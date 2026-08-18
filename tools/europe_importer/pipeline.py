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

PROJECT_COUNTRY_ALIASES = {
    "england":"Inglaterra", "inglaterra":"Inglaterra",
    "spain":"Espanha", "espanha":"Espanha",
    "italy":"Itália", "italia":"Itália", "itália":"Itália",
    "germany":"Alemanha", "alemanha":"Alemanha",
    "france":"França", "franca":"França", "frança":"França",
    "portugal":"Portugal",
    "netherlands":"Países Baixos", "the netherlands":"Países Baixos", "paises baixos":"Países Baixos", "países baixos":"Países Baixos",
    "belgium":"Bélgica", "belgica":"Bélgica", "bélgica":"Bélgica",
    "turkey":"Turquia", "türkiye":"Turquia", "turkiye":"Turquia", "turquia":"Turquia",
    "scotland":"Escócia", "escocia":"Escócia", "escócia":"Escócia",
    "austria":"Áustria", "austria":"Áustria", "áustria":"Áustria",
    "switzerland":"Suíça", "suica":"Suíça", "suíça":"Suíça",
    "denmark":"Dinamarca", "dinamarca":"Dinamarca",
    "norway":"Noruega", "noruega":"Noruega",
    "sweden":"Suécia", "suecia":"Suécia", "suécia":"Suécia",
    "poland":"Polônia", "polonia":"Polônia", "polônia":"Polônia",
    "czech republic":"Tchéquia", "czechia":"Tchéquia", "tchequia":"Tchéquia", "tchéquia":"Tchéquia",
    "croatia":"Croácia", "croacia":"Croácia", "croácia":"Croácia",
    "serbia":"Sérvia", "servia":"Sérvia", "sérvia":"Sérvia",
    "greece":"Grécia", "grecia":"Grécia", "grécia":"Grécia",
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


def _project_country(raw: str | None) -> str:
    value = (raw or "").strip()
    if not value:
        raise ValidationError("external loan club without country")
    return PROJECT_COUNTRY_ALIASES.get(value.casefold(), value)


def _transfer_type_name(transfer: dict[str, Any]) -> str:
    value = transfer.get("type") or {}
    return str(value.get("name") if isinstance(value, dict) else value or "")


def _is_current_season_transfer(transfer: dict[str, Any], request: ProviderRequest) -> bool:
    return str(transfer.get("date") or "").startswith(str(request.api_season))


def _latest_transfer_by_player(rows: list[dict[str, Any]], request: ProviderRequest) -> list[tuple[int, dict[str, Any]]]:
    latest: dict[int, dict[str, Any]] = {}
    for row in rows:
        player_id = (row.get("player") or {}).get("id") or row.get("player_id")
        if player_id is None:
            continue
        for transfer in row.get("transfers") or [row]:
            if not _is_current_season_transfer(transfer, request):
                continue
            pid = int(player_id)
            previous = latest.get(pid)
            if previous is None or str(transfer.get("date") or "") > str(previous.get("date") or ""):
                latest[pid] = transfer
    return sorted(latest.items())


def _api_player(entry: dict[str, Any], preferred_team_id: int | None = None) -> tuple[int | None, dict[str, Any]]:
    player = entry.get("player") or {}
    statistics = entry.get("statistics") or []
    stat = next(
        (
            candidate for candidate in statistics
            if preferred_team_id is not None and int(((candidate.get("team") or {}).get("id") or -1)) == preferred_team_id
        ),
        statistics[0] if statistics else {},
    )
    team_id = ((stat.get("team") or {}).get("id"))
    games = stat.get("games") or {}
    birth = player.get("birth") or {}
    return (int(team_id) if team_id is not None else None), {
        "providerPlayerId": player.get("id"),
        "fullName": player.get("name"),
        "birthDateIso": birth.get("date"),
        "nationality": player.get("nationality"),
        "position": normalize_position(games.get("position") or player.get("position")),
        "shirtNumber": games.get("number"),
        "identityDisambiguator":"",
    }


def _api_external_team_refs(raw: dict[str, Any]) -> dict[int, dict[str, Any]]:
    result: dict[int, dict[str, Any]] = {}
    for key, document in (raw.get("externalTeamsById") or {}).items():
        entries = document.get("response") or []
        if not entries:
            continue
        item = entries[0]
        team = item.get("team") or {}
        provider_id = int(team.get("id") or key)
        result[provider_id] = {
            "providerTeamId": provider_id,
            "name": team.get("name"),
            "country": _project_country(team.get("country")),
        }
    return result


def _api_external_players(raw: dict[str, Any]) -> dict[int, dict[str, Any]]:
    result: dict[int, dict[str, Any]] = {}
    for key, document in (raw.get("externalPlayersById") or {}).items():
        entries = document.get("response") or []
        if not entries:
            continue
        _, player = _api_player(entries[0])
        provider_id = int(player.get("providerPlayerId") or key)
        result[provider_id] = player
    return result


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
        team_id, player = _api_player(entry)
        if team_id is None or team_id not in team_by_provider:
            raise ValidationError(f"player without known club: {player.get('fullName')}")
        if player["providerPlayerId"] is not None:
            player_by_provider[int(player["providerPlayerId"])] = player
        team_by_provider[team_id]["players"].append(player)

    external_teams = _api_external_team_refs(raw)
    external_players = _api_external_players(raw)
    all_team_refs = {
        **{provider_id: {"providerTeamId": provider_id, "name": club["name"], "country": club["country"]}
           for provider_id, club in team_by_provider.items()},
        **external_teams,
    }

    loans: list[dict[str, Any]] = []
    seen_transfer_keys: set[tuple] = set()
    transfer_rows = (raw.get("transfersResponse") or {}).get("response", [])
    for provider_player_id, transfer in _latest_transfer_by_player(transfer_rows, request):
        if "loan" not in _transfer_type_name(transfer).casefold():
            continue
        tteams = transfer.get("teams") or {}
        owner = (tteams.get("out") or {}).get("id")
        borrower = (tteams.get("in") or {}).get("id")
        if owner is None or borrower is None:
            raise ValidationError(f"current loan without both clubs for provider player {provider_player_id}")
        owner, borrower = int(owner), int(borrower)
        owner_ref = all_team_refs.get(owner)
        borrower_ref = all_team_refs.get(borrower)
        if owner_ref is None or borrower_ref is None:
            raise ValidationError(f"current loan endpoint metadata unavailable for provider player {provider_player_id}")
        player = player_by_provider.get(provider_player_id) or external_players.get(provider_player_id)
        if player is None:
            raise ValidationError(f"current loan player factual profile unavailable: {provider_player_id}")
        key = (provider_player_id, owner, borrower, transfer.get("date"))
        if key in seen_transfer_keys:
            continue
        seen_transfer_keys.add(key)
        for club in teams:
            club["players"] = [p for p in club["players"] if p.get("providerPlayerId") != provider_player_id]
        loans.append({
            "player":copy.deepcopy(player),
            "owner":copy.deepcopy(owner_ref),
            "borrower":copy.deepcopy(borrower_ref),
            "season":request.api_season,
            "startWeek":1,
            "durationWeeks":48,
            "verifiedAsOfIso":transfer.get("date") or f"{request.api_season}-01-01",
        })
    return {
        "provider":"api-football",
        "country":request.country,
        "league":request.league,
        "domesticSeasonLabel":request.season_label,
        "teams":teams,
        "loans":loans,
    }


def _sportmonks_position(source: dict[str, Any]) -> str:
    player = source.get("player") or source
    candidates = [
        (source.get("position") or {}).get("name") if isinstance(source.get("position"), dict) else None,
        (player.get("position") or {}).get("name") if isinstance(player.get("position"), dict) else None,
        player.get("position_name"),
    ]
    for candidate in candidates:
        if candidate:
            return normalize_position(candidate)
    raise ValidationError(f"Sportmonks player lacks supported position for {player.get('name')}")


def _sportmonks_player(source: dict[str, Any], provider_id: int | None = None) -> dict[str, Any]:
    player = source.get("player") or source
    nationality = player.get("nationality")
    return {
        "providerPlayerId": player.get("id") or source.get("player_id") or provider_id,
        "fullName": player.get("name") or player.get("display_name"),
        "birthDateIso": player.get("date_of_birth"),
        "nationality": nationality.get("name") if isinstance(nationality, dict) else player.get("nationality_name") or "",
        "position": _sportmonks_position(source),
        "shirtNumber": source.get("jersey_number"),
        "identityDisambiguator":"",
    }


def _sportmonks_external_team_refs(raw: dict[str, Any]) -> dict[int, dict[str, Any]]:
    result: dict[int, dict[str, Any]] = {}
    for key, document in (raw.get("externalTeamsById") or {}).items():
        item = document.get("data") or {}
        if not isinstance(item, dict) or not item:
            continue
        provider_id = int(item.get("id") or key)
        country = item.get("country") or {}
        result[provider_id] = {
            "providerTeamId":provider_id,
            "name":item.get("name"),
            "country":_project_country(country.get("name") if isinstance(country, dict) else item.get("country_name")),
        }
    return result


def _sportmonks_external_players(raw: dict[str, Any]) -> dict[int, dict[str, Any]]:
    result: dict[int, dict[str, Any]] = {}
    for key, document in (raw.get("externalPlayersById") or {}).items():
        item = document.get("data") or {}
        if isinstance(item, dict) and item:
            player = _sportmonks_player(item, int(key))
            result[int(player["providerPlayerId"])] = player
    return result


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
            player = _sportmonks_player(squad)
            club["players"].append(player)
            pp_id = player.get("providerPlayerId")
            if pp_id is not None:
                player_by_provider[int(pp_id)] = player

    external_teams = _sportmonks_external_team_refs(raw)
    external_players = _sportmonks_external_players(raw)
    all_team_refs = {
        **{provider_id: {"providerTeamId": provider_id, "name": club["name"], "country": club["country"]}
           for provider_id, club in team_by_provider.items()},
        **external_teams,
    }

    transfer_rows: list[dict[str, Any]] = []
    for document in (raw.get("transfersByTeam") or {}).values():
        transfer_rows.extend(document.get("data", []))
    loans: list[dict[str, Any]] = []
    seen: set[tuple] = set()
    for pp_id, transfer in _latest_transfer_by_player(transfer_rows, request):
        if "loan" not in _transfer_type_name(transfer).casefold():
            continue
        owner = transfer.get("from_team_id")
        borrower = transfer.get("to_team_id")
        if owner is None or borrower is None:
            raise ValidationError(f"current Sportmonks loan without both clubs for player {pp_id}")
        owner, borrower = int(owner), int(borrower)
        owner_ref = all_team_refs.get(owner)
        borrower_ref = all_team_refs.get(borrower)
        if owner_ref is None or borrower_ref is None:
            raise ValidationError(f"current Sportmonks loan endpoint metadata unavailable for player {pp_id}")
        player = player_by_provider.get(pp_id) or external_players.get(pp_id)
        if player is None:
            raise ValidationError(f"current Sportmonks loan player factual profile unavailable: {pp_id}")
        key = (pp_id, owner, borrower, transfer.get("date"))
        if key in seen:
            continue
        seen.add(key)
        for club in teams:
            club["players"] = [p for p in club["players"] if p.get("providerPlayerId") != pp_id]
        loans.append({
            "player":copy.deepcopy(player),
            "owner":copy.deepcopy(owner_ref),
            "borrower":copy.deepcopy(borrower_ref),
            "season":request.api_season,
            "startWeek":1,
            "durationWeeks":48,
            "verifiedAsOfIso":transfer.get("date") or f"{request.api_season}-01-01",
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
        player = loan.get("player") or {}
        owner = loan.get("owner") or {}
        borrower = loan.get("borrower") or {}
        for endpoint_label, endpoint in (("owner", owner), ("borrower", borrower)):
            _required(endpoint.get("country"), f"loan.{endpoint_label}.country for {player.get('fullName')}", errors)
            _required(endpoint.get("name"), f"loan.{endpoint_label}.name for {player.get('fullName')}", errors)
        owner_id = owner.get("providerTeamId")
        borrower_id = borrower.get("providerTeamId")
        owner_key = (str(owner.get("country") or "").casefold(), str(owner.get("name") or "").casefold())
        borrower_key = (str(borrower.get("country") or "").casefold(), str(borrower.get("name") or "").casefold())
        if (owner_id is not None and borrower_id is not None and int(owner_id) == int(borrower_id)) or owner_key == borrower_key:
            errors.append(f"inconsistent loan owner=borrower for {player.get('fullName')}")
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
    for club in normalized["teams"]:
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
        owner = loan["owner"]
        borrower = loan["borrower"]
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
