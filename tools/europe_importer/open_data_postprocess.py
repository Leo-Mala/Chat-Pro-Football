from __future__ import annotations

import json
from datetime import date
from pathlib import Path
from typing import Any

IDENTITY_NAME_PREFIX = "identity-name-v1:"


def _claim_item_ids(entity: dict[str, Any], prop: str) -> list[str]:
    result: list[str] = []
    for claim in (entity.get("claims") or {}).get(prop, []):
        if claim.get("rank") == "deprecated":
            continue
        value = ((claim.get("mainsnak") or {}).get("datavalue") or {}).get("value")
        if isinstance(value, dict) and value.get("id"):
            result.append(str(value["id"]))
    return result


def _english_label(entity: dict[str, Any], fallback: str) -> str:
    return str(((entity.get("labels") or {}).get("en") or {}).get("value") or fallback)


def _qid_from_transient_id(value: Any) -> str:
    return f"Q{int(value)}"


def _time_to_iso(value: Any) -> str | None:
    if not isinstance(value, dict):
        return None
    raw = str(value.get("time") or "")
    precision = int(value.get("precision") or 0)
    if precision < 11 or len(raw) < 11:
        return None
    candidate = raw[1:11] if raw.startswith("+") else raw[:10]
    try:
        date.fromisoformat(candidate)
    except ValueError:
        return None
    return candidate


def _qualifier_dates(statement: dict[str, Any], prop: str) -> list[str]:
    result: list[str] = []
    for snak in (statement.get("qualifiers") or {}).get(prop, []):
        parsed = _time_to_iso((snak.get("datavalue") or {}).get("value"))
        if parsed:
            result.append(parsed)
    return result


def _statement_active_as_of(statement: dict[str, Any], as_of_iso: str) -> bool:
    if statement.get("rank") == "deprecated":
        return False
    starts = _qualifier_dates(statement, "P580")
    ends = _qualifier_dates(statement, "P582")
    if starts and max(starts) > as_of_iso:
        return False
    if ends and min(ends) < as_of_iso:
        return False
    return True


def _statement_item_id(statement: dict[str, Any]) -> str | None:
    value = ((statement.get("mainsnak") or {}).get("datavalue") or {}).get("value")
    if isinstance(value, dict) and value.get("id"):
        return str(value["id"])
    return None


def _select_current_sport_country(entity: dict[str, Any], as_of_iso: str) -> tuple[str | None, str]:
    active: list[tuple[str, str]] = []
    for statement in (entity.get("claims") or {}).get("P1532", []):
        qid = _statement_item_id(statement)
        if not qid or not _statement_active_as_of(statement, as_of_iso):
            continue
        rank = "preferred" if statement.get("rank") == "preferred" else "normal"
        active.append((rank, qid))

    if not active:
        return None, "NONE"
    preferred = sorted({qid for rank, qid in active if rank == "preferred"})
    if len(preferred) == 1:
        return preferred[0], "PREFERRED"
    if len(preferred) > 1:
        return None, "AMBIGUOUS"
    normal = sorted({qid for _, qid in active})
    if len(normal) == 1:
        return normal[0], "NORMAL"
    return None, "AMBIGUOUS"


def install_verified_squad_discovery_overrides(provider: Any, overrides_path: Path) -> None:
    """Augment volatile Wikipedia squad discovery with explicitly sourced memberships.

    This does not inject player facts. It only adds an officially verified player page to the
    discovery set; occupation, birth date, nationality and position still have to resolve through
    the normal structured-data validation path.
    """
    overrides = json.loads(overrides_path.read_text(encoding="utf-8"))
    rows = overrides.get("squadMemberships", []) or []
    if not rows:
        return

    by_club_page: dict[str, list[str]] = {}
    for row in rows:
        club_page = str(row.get("clubWikipediaPage") or "").strip()
        player_page = str(row.get("wikipediaTitle") or "").strip()
        full_name = str(row.get("fullName") or "").strip()
        source = str(row.get("source") or "").strip()
        if not all((club_page, player_page, full_name, source)):
            raise RuntimeError(f"Incomplete verified squad membership override: {row}")
        by_club_page.setdefault(club_page, []).append(player_page)

    original = provider.client.current_squad_links

    def current_squad_links(title: str) -> tuple[str, list[str]]:
        section, links = original(title)
        forced = by_club_page.get(title, [])
        return section, sorted(set(links).union(forced))

    provider.client.current_squad_links = current_squad_links


def _team_id_by_name(raw: dict[str, Any]) -> dict[str, int]:
    return {
        str((row.get("team") or {}).get("name")): int((row.get("team") or {})["id"])
        for row in (raw.get("teamsResponse") or {}).get("response") or []
        if (row.get("team") or {}).get("name") and (row.get("team") or {}).get("id") is not None
    }


def _row_matches_team(player_row: dict[str, Any], team_id: int) -> bool:
    return any(
        int((stat.get("team") or {}).get("id")) == team_id
        for stat in player_row.get("statistics") or []
        if (stat.get("team") or {}).get("id") is not None
    )


def _apply_verified_squad_exclusions(
    raw: dict[str, Any], audit: dict[str, Any], overrides: dict[str, Any]
) -> None:
    team_ids = _team_id_by_name(raw)
    player_rows = (raw.get("playersResponse") or {}).get("response") or []
    for exclusion in overrides.get("squadExclusions", []) or []:
        club = str(exclusion.get("club") or "").strip()
        full_name = str(exclusion.get("fullName") or "").strip()
        birth_date = str(exclusion.get("birthDateIso") or "").strip()
        source = str(exclusion.get("source") or "").strip()
        reason = str(exclusion.get("reason") or "").strip()
        if not all((club, full_name, birth_date, source, reason)):
            raise RuntimeError(f"Incomplete verified squad exclusion: {exclusion}")
        if club not in team_ids:
            raise RuntimeError(f"Verified squad exclusion club not collected: {club}")
        team_id = team_ids[club]
        matches = [
            row for row in player_rows
            if str((row.get("player") or {}).get("name") or "").strip() == full_name
            and str(((row.get("player") or {}).get("birth") or {}).get("date") or "").strip() == birth_date
            and _row_matches_team(row, team_id)
        ]
        if len(matches) != 1:
            raise RuntimeError(
                f"Verified squad exclusion must resolve exactly once: {club}/{full_name}/{birth_date} "
                f"(matches={len(matches)})"
            )
        player_rows.remove(matches[0])
        audit.setdefault("verifiedOverridesUsed", []).append({
            "kind": "squadExclusion",
            "player": full_name,
            "birthDateIso": birth_date,
            "club": club,
            "reason": reason,
            "source": source,
        })


def _prepare_verified_player_names(
    raw: dict[str, Any], audit: dict[str, Any], overrides: dict[str, Any]
) -> None:
    player_rows = (raw.get("playersResponse") or {}).get("response") or []
    for correction in overrides.get("playerNames", []) or []:
        current_name = str(correction.get("currentName") or "").strip()
        official_name = str(correction.get("officialName") or "").strip()
        source = str(correction.get("source") or "").strip()
        if not all((current_name, official_name, source)) or current_name == official_name:
            raise RuntimeError(f"Incomplete/invalid verified player name override: {correction}")
        matches = [
            row for row in player_rows
            if str((row.get("player") or {}).get("name") or "").strip() in {current_name, official_name}
        ]
        if len(matches) != 1:
            raise RuntimeError(
                f"Verified player name correction must resolve exactly once: "
                f"{current_name} -> {official_name} (matches={len(matches)})"
            )
        # Freeze the pre-correction spelling for pipeline identity validation. The canonical writer
        # applies the official display spelling afterwards and encodes the old identity spelling in
        # the existing disambiguator field so Android and Python preserve the same stable playerId.
        matches[0]["player"]["name"] = current_name
        audit.setdefault("verifiedOverridesUsed", []).append({
            "kind": "playerName",
            "identityName": current_name,
            "officialName": official_name,
            "source": source,
        })


def _apply_sport_nationalities(
    provider: Any,
    raw: dict[str, Any],
    audit: dict[str, Any],
    overrides: dict[str, Any],
) -> None:
    rows = (raw.get("playersResponse") or {}).get("response") or []
    qids = sorted({
        _qid_from_transient_id((row.get("player") or {}).get("id"))
        for row in rows
        if (row.get("player") or {}).get("id") is not None
    })
    entities = provider.client.entities(qids)
    as_of_iso = str(overrides.get("verifiedAsOfIso") or "").strip()
    try:
        date.fromisoformat(as_of_iso)
    except ValueError as exc:
        raise RuntimeError(f"verifiedAsOfIso must be YYYY-MM-DD: {as_of_iso!r}") from exc

    official_overrides = {
        str(item.get("fullName") or "").strip(): {
            "nationality": str(item.get("nationality") or "").strip(),
            "source": str(item.get("source") or "").strip(),
        }
        for item in overrides.get("nationalities", []) or []
    }
    for name, item in official_overrides.items():
        if not name or not item["nationality"] or not item["source"]:
            raise RuntimeError(f"Incomplete nationality override for {name!r}")

    country_qids: set[str] = set()
    selection: dict[str, tuple[str | None, str]] = {}
    for qid, entity in entities.items():
        chosen, status = _select_current_sport_country(entity, as_of_iso)
        selection[qid] = (chosen, status)
        if chosen:
            country_qids.add(chosen)

    country_entities = provider.client.entities(sorted(country_qids)) if country_qids else {}
    country_labels = {qid: _english_label(entity, qid) for qid, entity in country_entities.items()}

    sport_country_used = 0
    nationality_changed = 0
    fallback_citizenship = 0
    official_override_used = 0
    ambiguous: list[str] = []

    for row in rows:
        player = row.get("player") or {}
        provider_id = player.get("id")
        if provider_id is None:
            continue
        full_name = str(player.get("name") or "").strip()
        qid = _qid_from_transient_id(provider_id)
        country_qid, status = selection.get(qid, (None, "NONE"))
        override = official_overrides.get(full_name)

        if status == "AMBIGUOUS" and override is None:
            ambiguous.append(full_name or qid)
            continue

        if override is not None:
            label = override["nationality"]
            official_override_used += 1
            audit.setdefault("verifiedOverridesUsed", []).append({
                "kind": "nationality",
                "player": full_name,
                "value": label,
                "source": override["source"],
            })
        elif country_qid:
            label = country_labels.get(country_qid, country_qid)
            sport_country_used += 1
        else:
            fallback_citizenship += 1
            continue

        if label and label != player.get("nationality"):
            player["nationality"] = label
            nationality_changed += 1

    if ambiguous:
        audit.setdefault("warnings", []).append({
            "kind": "ambiguousSportNationality",
            "players": sorted(ambiguous),
            "asOfIso": as_of_iso,
        })
        raise RuntimeError(
            "Ambiguous active P1532 statements without official override: " + ", ".join(sorted(ambiguous))
        )

    audit["sportNationality"] = {
        "property": "P1532",
        "asOfIso": as_of_iso,
        "playersUsingSportCountry": sport_country_used,
        "playersUsingOfficialOverride": official_override_used,
        "playersChangedFromCitizenship": nationality_changed,
        "playersUsingCitizenshipFallback": fallback_citizenship,
        "ambiguousSportCountryPlayers": 0,
        "fallbackProperty": "P27",
    }


def _audit_verified_squad_memberships(
    raw: dict[str, Any], audit: dict[str, Any], overrides: dict[str, Any]
) -> None:
    team_ids = _team_id_by_name(raw)
    player_rows = (raw.get("playersResponse") or {}).get("response") or []

    for row in overrides.get("squadMemberships", []) or []:
        full_name = str(row.get("fullName") or "").strip()
        club = str(row.get("club") or "").strip()
        source = str(row.get("source") or "").strip()
        if club not in team_ids:
            raise RuntimeError(f"Verified squad membership club not collected: {club}")
        team_id = team_ids[club]
        matches = [
            player_row for player_row in player_rows
            if str((player_row.get("player") or {}).get("name") or "").strip() == full_name
            and _row_matches_team(player_row, team_id)
        ]
        if len(matches) != 1:
            raise RuntimeError(
                f"Verified squad membership must resolve exactly once: {club}/{full_name} "
                f"(matches={len(matches)})"
            )
        audit.setdefault("verifiedOverridesUsed", []).append({
            "kind": "squadMembership",
            "player": full_name,
            "club": club,
            "source": source,
        })


def _apply_verified_loans(raw: dict[str, Any], audit: dict[str, Any], overrides: dict[str, Any]) -> None:
    team_ids = _team_id_by_name(raw)
    players_by_name: dict[str, list[dict[str, Any]]] = {}
    for row in (raw.get("playersResponse") or {}).get("response") or []:
        name = str((row.get("player") or {}).get("name") or "").strip()
        if name:
            players_by_name.setdefault(name, []).append(row)

    verified_transfers: list[dict[str, Any]] = []
    verified_names: set[str] = set()
    for loan in overrides.get("loans", []) or []:
        full_name = str(loan.get("fullName") or "").strip()
        owner_name = str(loan.get("ownerClub") or "").strip()
        borrower_name = str(loan.get("borrowerClub") or "").strip()
        verified_as = str(loan.get("verifiedAsOfIso") or "").strip()
        source = str(loan.get("source") or "").strip()
        if not all((full_name, owner_name, borrower_name, verified_as, source)):
            raise RuntimeError(f"Incomplete verified loan override: {loan}")
        if owner_name == borrower_name:
            raise RuntimeError(f"Verified loan owner=borrower for {full_name}")
        if owner_name not in team_ids or borrower_name not in team_ids:
            raise RuntimeError(
                f"Verified loan endpoint missing from Premier League identity map: "
                f"{full_name} ({owner_name} -> {borrower_name})"
            )
        matches = players_by_name.get(full_name, [])
        if len(matches) != 1:
            raise RuntimeError(f"Verified loan player must resolve exactly once: {full_name} (matches={len(matches)})")
        player_row = matches[0]
        player_id = (player_row.get("player") or {}).get("id")
        if player_id is None:
            raise RuntimeError(f"Verified loan player has no transient source id: {full_name}")
        borrower_id = team_ids[borrower_name]
        active_team_ids = {
            int((stat.get("team") or {}).get("id"))
            for stat in player_row.get("statistics") or []
            if (stat.get("team") or {}).get("id") is not None
        }
        if borrower_id not in active_team_ids:
            raise RuntimeError(
                f"Verified loan borrower does not match current squad discovery for {full_name}: "
                f"expected {borrower_name}"
            )
        verified_transfers.append({
            "player": {"id": int(player_id), "name": full_name},
            "transfers": [{
                "date": verified_as,
                "type": "Loan",
                "teams": {
                    "out": {"id": team_ids[owner_name], "name": owner_name},
                    "in": {"id": borrower_id, "name": borrower_name},
                },
            }],
        })
        verified_names.add(full_name)
        audit.setdefault("verifiedOverridesUsed", []).append({
            "kind": "loan",
            "player": full_name,
            "ownerClub": owner_name,
            "borrowerClub": borrower_name,
            "verifiedAsOfIso": verified_as,
            "source": source,
        })

    for candidate in audit.get("loanCandidates", []) or []:
        if candidate.get("player") in verified_names:
            candidate["status"] = "VERIFIED_MATERIALIZED"
    raw["transfersResponse"] = {"response": verified_transfers}
    audit["verifiedLoanCount"] = len(verified_transfers)


def apply_canonical_name_overrides(dataset: dict[str, Any], overrides_path: Path) -> list[dict[str, str]]:
    """Apply verified display-name corrections while preserving the already validated stable ID."""
    overrides = json.loads(overrides_path.read_text(encoding="utf-8"))
    applied: list[dict[str, str]] = []
    players: list[dict[str, Any]] = []
    for league in dataset.get("leagues", []) or []:
        for club in league.get("clubs", []) or []:
            players.extend(club.get("players", []) or [])
    for loan in dataset.get("loans", []) or []:
        player = loan.get("player")
        if isinstance(player, dict):
            players.append(player)

    for correction in overrides.get("playerNames", []) or []:
        current_name = str(correction.get("currentName") or "").strip()
        official_name = str(correction.get("officialName") or "").strip()
        source = str(correction.get("source") or "").strip()
        matches = [p for p in players if str(p.get("fullName") or "").strip() == current_name]
        if len(matches) != 1:
            raise RuntimeError(
                f"Canonical player name correction must resolve exactly once: "
                f"{current_name} -> {official_name} (matches={len(matches)})"
            )
        player = matches[0]
        if str(player.get("identityDisambiguator") or "").strip():
            raise RuntimeError(f"Cannot combine name-identity alias with existing disambiguator: {current_name}")
        player["fullName"] = official_name
        player["identityDisambiguator"] = IDENTITY_NAME_PREFIX + current_name
        applied.append({"identityName": current_name, "officialName": official_name, "source": source})
    return applied


def apply_verified_open_data_facts(provider: Any, raw: dict[str, Any], overrides_path: Path) -> dict[str, Any]:
    overrides = json.loads(overrides_path.read_text(encoding="utf-8"))
    audit = provider.last_audit
    _apply_verified_squad_exclusions(raw, audit, overrides)
    _prepare_verified_player_names(raw, audit, overrides)
    _audit_verified_squad_memberships(raw, audit, overrides)
    _apply_sport_nationalities(provider, raw, audit, overrides)
    _apply_verified_loans(raw, audit, overrides)
    provider.last_audit = audit
    raw["openDataAudit"] = audit
    return raw
