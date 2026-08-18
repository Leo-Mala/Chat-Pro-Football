from __future__ import annotations
import copy
import json
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .identity import StableTeamIdentityContract, stable_player_id
from .providers import DataProvider, ProviderRequest

POSITION_MAP = {
    "goalkeeper":"GOL", "keeper":"GOL", "gk":"GOL",
    "centre-back":"ZAG", "center-back":"ZAG", "defender":"ZAG", "defence":"ZAG",
    "left-back":"LAT", "right-back":"LAT", "full-back":"LAT", "wing-back":"LAT",
    "defensive midfield":"VOL", "holding midfielder":"VOL",
    "midfielder":"MEI", "midfield":"MEI", "attacking midfield":"MEI",
    "forward":"ATA", "attacker":"ATA", "striker":"ATA", "winger":"ATA",
}

class ValidationError(ValueError):
    pass

@dataclass(frozen=True)
class PipelineResult:
    dataset: dict[str, Any]
    manifest: dict[str, Any]
    validation_errors: tuple[str, ...]

def normalize_position(raw: str | None) -> str:
    value = (raw or "").strip().casefold()
    if value in POSITION_MAP:
        return POSITION_MAP[value]
    for key, normalized in POSITION_MAP.items():
        if key in value:
            return normalized
    raise ValidationError(f"unsupported position: {raw!r}")

def _api_player(entry: dict[str, Any], team_by_provider: dict[int, dict[str, Any]]) -> tuple[int, dict[str, Any]]:
    player = entry.get("player") or {}
    statistics = entry.get("statistics") or []
    stat = statistics[0] if statistics else {}
    team_id = ((stat.get("team") or {}).get("id"))
    if team_id is None or int(team_id) not in team_by_provider:
        raise ValidationError(f"player without known club: {player.get('name')}")
    games = stat.get("games") or {}
    birth = player.get("birth") or {}
    return int(team_id), {
        "providerPlayerId": player.get("id"),
        "fullName": player.get("name"),
        "birthDateIso": birth.get("date"),
        "nationality": player.get("nationality"),
        "position": normalize_position(games.get("position") or player.get("position")),
        "shirtNumber": games.get("number"),
        "identityDisambiguator":"",
    }

def normalize_api_football(raw: dict[str, Any], request: ProviderRequest) -> dict[str, Any]:
    teams: list[dict[str, Any]] = []
    team_by_provider: dict[int, dict[str, Any]] = {}
    for entry in (raw.get("teamsResponse") or {}).get("response", []):
        team = entry.get("team") or {}
        venue = entry.get("venue") or {}
        provider_id = int(team["id"])
        normalized = {
            "providerTeamId":provider_id,
            "name":team.get("name"),
            "country":request.country,
            "city":venue.get("city") or "",
            "stadium":venue.get("name") or "",
            "players":[],
        }
        teams.append(normalized)
        team_by_provider[provider_id] = normalized

    player_by_provider: dict[int, dict[str, Any]] = {}
    for entry in (raw.get("playersResponse") or {}).get("response", []):
        team_id, player = _api_player(entry, team_by_provider)
        if player["providerPlayerId"] is not None:
            player_by_provider[int(player["providerPlayerId"])] = player
        team_by_provider[team_id]["players"].append(player)

    loans: list[dict[str, Any]] = []
    seen_transfer_keys: set[tuple] = set()
    for item in (raw.get("transfersResponse") or {}).get("response", []):
        player_ref = item.get("player") or {}
        provider_player_id = player_ref.get("id")
        player = player_by_provider.get(int(provider_player_id)) if provider_player_id is not None else None
        if player is None:
            continue
        for transfer in item.get("transfers") or []:
            transfer_type = str(transfer.get("type") or "").casefold()
            if "loan" not in transfer_type:
                continue
            tteams = transfer.get("teams") or {}
            owner = (tteams.get("out") or {}).get("id")
            borrower = (tteams.get("in") or {}).get("id")
            if owner is None or borrower is None:
                continue
            owner, borrower = int(owner), int(borrower)
            if owner not in team_by_provider or borrower not in team_by_provider:
                continue
            key = (int(provider_player_id), owner, borrower, transfer.get("date"))
            if key in seen_transfer_keys:
                continue
            seen_transfer_keys.add(key)
            # A loan is materialized separately by the Android planner. Remove the borrower-roster
            # copy so the same factual identity can never be seeded twice.
            for club in teams:
                club["players"] = [p for p in club["players"] if p.get("providerPlayerId") != provider_player_id]
            loans.append({
                "player":copy.deepcopy(player),
                "ownerProviderTeamId":owner,
                "borrowerProviderTeamId":borrower,
                "season":2026,
                "startWeek":1,
                "durationWeeks":48,
                "verifiedAsOfIso":transfer.get("date") or "2026-08-18",
            })
    return {
        "provider":"api-football",
        "country":request.country,
        "league":request.league,
        "domesticSeasonLabel":request.season_label,
        "teams":teams,
        "loans":loans,
    }

def _sportmonks_position(squad: dict[str, Any]) -> str:
    player = squad.get("player") or {}
    # API v3 exposes position information through the player/details relationship. We only whitelist
    # textual position metadata and deliberately ignore statistics/ratings.
    candidates = [
        (squad.get("position") or {}).get("name") if isinstance(squad.get("position"), dict) else None,
        (player.get("position") or {}).get("name") if isinstance(player.get("position"), dict) else None,
        player.get("position_name"),
    ]
    for candidate in candidates:
        if candidate:
            return normalize_position(candidate)
    raise ValidationError(f"Sportmonks squad entry lacks supported position for {player.get('name')}")

def normalize_sportmonks(raw: dict[str, Any], request: ProviderRequest) -> dict[str, Any]:
    teams: list[dict[str, Any]] = []
    team_by_provider: dict[int, dict[str, Any]] = {}
    player_by_provider: dict[int, dict[str, Any]] = {}
    for item in (raw.get("teamsResponse") or {}).get("data", []):
        venue = item.get("venue") or {}
        pid = int(item["id"])
        club = {
            "providerTeamId":pid,
            "name":item.get("name"),
            "country":request.country,
            "city":venue.get("city_name") or venue.get("city") or "",
            "stadium":venue.get("name") or "",
            "players":[],
        }
        teams.append(club)
        team_by_provider[pid] = club
        squad_doc = (raw.get("squadsByTeam") or {}).get(str(pid), {})
        for squad in squad_doc.get("data", []):
            player = squad.get("player") or {}
            pp_id = player.get("id") or squad.get("player_id")
            normalized = {
                "providerPlayerId":pp_id,
                "fullName":player.get("name") or player.get("display_name"),
                "birthDateIso":player.get("date_of_birth"),
                "nationality":(player.get("nationality") or {}).get("name") if isinstance(player.get("nationality"), dict) else player.get("nationality_name") or "",
                "position":_sportmonks_position(squad),
                "shirtNumber":squad.get("jersey_number"),
                "identityDisambiguator":"",
            }
            club["players"].append(normalized)
            if pp_id is not None:
                player_by_provider[int(pp_id)] = normalized

    loans: list[dict[str, Any]] = []
    seen = set()
    # Sportmonks transfer type is included by live adapter when available. Only explicit loan types
    # are promoted into PlayerLoan; ordinary transfers remain provenance, not a runtime transfer event.
    for _, doc in (raw.get("transfersByTeam") or {}).items():
        for tr in doc.get("data", []):
            tr_type = tr.get("type") or {}
            type_name = tr_type.get("name") if isinstance(tr_type, dict) else str(tr_type or "")
            if "loan" not in str(type_name).casefold():
                continue
            pp_id = tr.get("player_id")
            owner, borrower = tr.get("from_team_id"), tr.get("to_team_id")
            if pp_id is None or owner is None or borrower is None:
                continue
            pp_id, owner, borrower = int(pp_id), int(owner), int(borrower)
            if pp_id not in player_by_provider or owner not in team_by_provider or borrower not in team_by_provider:
                continue
            key = (pp_id, owner, borrower, tr.get("date"))
            if key in seen:
                continue
            seen.add(key)
            for club in teams:
                club["players"] = [p for p in club["players"] if p.get("providerPlayerId") != pp_id]
            loans.append({
                "player":copy.deepcopy(player_by_provider[pp_id]),
                "ownerProviderTeamId":owner,
                "borrowerProviderTeamId":borrower,
                "season":2026,
                "startWeek":1,
                "durationWeeks":48,
                "verifiedAsOfIso":tr.get("date") or "2026-08-18",
            })
    return {
        "provider":"sportmonks",
        "country":request.country,
        "league":request.league,
        "domesticSeasonLabel":request.season_label,
        "teams":teams,
        "loans":loans,
    }

def normalize(raw: dict[str, Any], request: ProviderRequest) -> dict[str, Any]:
    provider = raw.get("provider")
    if provider in ("fixture", "api-football"):
        # Fixtures intentionally use API-Football-shaped raw responses to exercise the real adapter.
        return normalize_api_football(raw, request)
    if provider == "sportmonks":
        return normalize_sportmonks(raw, request)
    raise ValidationError(f"unsupported provider payload: {provider!r}")

def _required(value: Any, label: str, errors: list[str]) -> None:
    if value is None or (isinstance(value, str) and not value.strip()):
        errors.append(f"missing {label}")

def validate(normalized: dict[str, Any], team_contract: StableTeamIdentityContract) -> list[str]:
    errors: list[str] = []
    clubs = normalized["teams"]
    club_keys: set[tuple[str,str]] = set()
    stable_team_ids: set[int] = set()
    player_keys: dict[tuple[str,str,str], str] = {}
    stable_player_ids: set[int] = set()
    provider_team_map: dict[int, dict[str, Any]] = {}

    for club in clubs:
        _required(club.get("country"), f"country for club {club.get('name')}", errors)
        _required(club.get("name"), "club name", errors)
        key = (str(club.get("country") or "").casefold(), str(club.get("name") or "").casefold())
        if key in club_keys:
            errors.append(f"duplicate club: {club.get('country')}/{club.get('name')}")
        club_keys.add(key)
        try:
            team_id = team_contract.id_for(club["country"], club["name"])
            if team_id in stable_team_ids:
                errors.append(f"duplicate teamId resolved by StableTeamIdentityRegistry contract: {team_id}")
            stable_team_ids.add(team_id)
        except Exception as exc:
            errors.append(str(exc))
        provider_team_id = club.get("providerTeamId")
        if provider_team_id is not None:
            provider_team_map[int(provider_team_id)] = club

        positions: list[str] = []
        shirts: set[int] = set()
        for player in club.get("players", []):
            for field in ("fullName","birthDateIso","nationality","position"):
                _required(player.get(field), f"{field} for player in {club.get('name')}", errors)
            positions.append(str(player.get("position")))
            shirt = player.get("shirtNumber")
            if shirt is not None:
                if int(shirt) in shirts:
                    errors.append(f"duplicate shirt number in {club.get('name')}: {shirt}")
                shirts.add(int(shirt))
            pkey = (str(player.get("fullName") or "").casefold(), str(player.get("birthDateIso") or ""), str(player.get("identityDisambiguator") or "").casefold())
            location = f"{club.get('country')}/{club.get('name')}"
            if pkey in player_keys:
                errors.append(f"player simultaneously in two clubs or duplicated: {player.get('fullName')} ({player_keys[pkey]} and {location})")
            player_keys[pkey] = location
            try:
                pid = stable_player_id(player["fullName"], player["birthDateIso"], player.get("identityDisambiguator") or "")
                if pid in stable_player_ids:
                    errors.append(f"duplicate playerId resolved by StableRealPlayerIdentity: {pid}")
                stable_player_ids.add(pid)
            except Exception as exc:
                errors.append(f"invalid player identity {player.get('fullName')}: {exc}")

        goalkeepers = positions.count("GOL")
        defenders = positions.count("ZAG") + positions.count("LAT")
        midfielders = positions.count("VOL") + positions.count("MEI")
        attackers = positions.count("ATA")
        if goalkeepers < 2:
            errors.append(f"squad without goalkeepers or below minimum: {club.get('name')} ({goalkeepers}/2)")
        if defenders < 6:
            errors.append(f"squad without defenders or below minimum: {club.get('name')} ({defenders}/6)")
        if midfielders < 5:
            errors.append(f"squad without midfielders or below minimum: {club.get('name')} ({midfielders}/5)")
        if attackers < 3:
            errors.append(f"squad without attackers or below minimum: {club.get('name')} ({attackers}/3)")
        if len(positions) < 18:
            errors.append(f"squad below gameplay-ready size: {club.get('name')} ({len(positions)}/18)")

    for loan in normalized.get("loans", []):
        owner = int(loan.get("ownerProviderTeamId", -1))
        borrower = int(loan.get("borrowerProviderTeamId", -1))
        player = loan.get("player") or {}
        if owner == borrower:
            errors.append(f"inconsistent loan owner=borrower for {player.get('fullName')}")
        if owner not in provider_team_map or borrower not in provider_team_map:
            errors.append(f"inconsistent loan with unknown club for {player.get('fullName')}")
            continue
        pkey = (str(player.get("fullName") or "").casefold(), str(player.get("birthDateIso") or ""), str(player.get("identityDisambiguator") or "").casefold())
        if pkey in player_keys:
            errors.append(f"loan player is also active in a club snapshot: {player.get('fullName')}")
        try:
            pid = stable_player_id(player["fullName"], player["birthDateIso"], player.get("identityDisambiguator") or "")
            if pid in stable_player_ids:
                errors.append(f"duplicate playerId resolved by StableRealPlayerIdentity: {pid}")
            stable_player_ids.add(pid)
        except Exception as exc:
            errors.append(f"invalid loan player identity {player.get('fullName')}: {exc}")

    return errors

def canonicalize(normalized: dict[str, Any], generated_at: str, dataset_kind: str) -> dict[str, Any]:
    provider = normalized["provider"]
    source_ref = (
        f"fixture://{provider}/{normalized['league'].replace(' ', '-').lower()}"
        if dataset_kind == "FIXTURE"
        else f"provider://{provider}/{normalized['country']}/{normalized['league']}/2026_27"
    )
    clubs = []
    team_by_provider = {}
    for club in normalized["teams"]:
        team_by_provider[int(club["providerTeamId"])] = club
        players = []
        for p in club["players"]:
            players.append({
                "fullName":p["fullName"],
                "birthDateIso":p["birthDateIso"],
                "nationality":p["nationality"],
                "position":p["position"],
                "shirtNumber":p.get("shirtNumber"),
                "identityDisambiguator":p.get("identityDisambiguator") or "",
            })
        clubs.append({
            "name":club["name"],
            "city":club["city"],
            "stadium":club["stadium"],
            "players":players,
        })
    loans = []
    for loan in normalized.get("loans", []):
        p = loan["player"]
        owner = team_by_provider[int(loan["ownerProviderTeamId"])]
        borrower = team_by_provider[int(loan["borrowerProviderTeamId"])]
        loans.append({
            "player":{
                "fullName":p["fullName"],
                "birthDateIso":p["birthDateIso"],
                "nationality":p["nationality"],
                "position":p["position"],
                "shirtNumber":p.get("shirtNumber"),
                "identityDisambiguator":p.get("identityDisambiguator") or "",
            },
            "ownerCountry":owner["country"],
            "ownerClubName":owner["name"],
            "borrowerCountry":borrower["country"],
            "borrowerClubName":borrower["name"],
            "season":int(loan["season"]),
            "startWeek":int(loan["startWeek"]),
            "durationWeeks":int(loan["durationWeeks"]),
            "verifiedAsOfIso":loan["verifiedAsOfIso"],
            "sourceRefs":[source_ref],
        })
    return {
        "schemaVersion":1,
        "datasetKind":dataset_kind,
        "provider":provider,
        "season":"2026/27",
        "generatedAt":generated_at,
        "leagues":[{
            "country":normalized["country"],
            "name":normalized["league"],
            "domesticSeasonLabel":normalized["domesticSeasonLabel"],
            "verifiedAsOfIso":"2026-08-18",
            "sourceRefs":[source_ref],
            "clubs":clubs,
        }],
        "loans":loans,
    }

def build_manifest(dataset: dict[str, Any], validation_status: str, dataset_files: list[str]) -> dict[str, Any]:
    countries = sorted({league["country"] for league in dataset["leagues"]})
    leagues = sorted({league["name"] for league in dataset["leagues"]})
    clubs = [club for league in dataset["leagues"] for club in league["clubs"]]
    player_count = sum(len(c["players"]) for c in clubs) + len(dataset.get("loans", []))
    return {
        "provider":dataset["provider"],
        "season":dataset["season"],
        "generatedAt":dataset["generatedAt"],
        "countries":countries,
        "leagues":leagues,
        "clubCount":len(clubs),
        "playerCount":player_count,
        "loanCount":len(dataset.get("loans", [])),
        "validationStatus":validation_status,
        "datasetFiles":dataset_files,
    }

def run_pipeline(
    provider: DataProvider,
    request: ProviderRequest,
    team_contract: StableTeamIdentityContract,
    *,
    generated_at: str | None = None,
    dataset_kind: str = "FACTUAL",
    output_dir: Path | None = None,
    filename: str = "premier_league.json",
) -> PipelineResult:
    raw = provider.collect(request)
    normalized = normalize(raw, request)
    errors = validate(normalized, team_contract)
    if errors:
        raise ValidationError("\n".join(errors))
    generated_at = generated_at or datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00","Z")
    dataset = canonicalize(normalized, generated_at, dataset_kind)
    status = "FIXTURE_ONLY" if dataset_kind == "FIXTURE" else "VALIDATED"
    manifest = build_manifest(dataset, status, [filename])
    if output_dir is not None:
        output_dir.mkdir(parents=True, exist_ok=True)
        (output_dir / filename).write_text(json.dumps(dataset, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
        (output_dir / "dataset_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return PipelineResult(dataset, manifest, tuple(errors))
