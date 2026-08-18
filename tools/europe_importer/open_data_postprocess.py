from __future__ import annotations

import json
from pathlib import Path
from typing import Any


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


def _apply_sport_nationalities(provider: Any, raw: dict[str, Any], audit: dict[str, Any]) -> None:
    rows = (raw.get("playersResponse") or {}).get("response") or []
    qids = sorted({
        _qid_from_transient_id((row.get("player") or {}).get("id"))
        for row in rows
        if (row.get("player") or {}).get("id") is not None
    })
    entities = provider.client.entities(qids)

    country_qids: set[str] = set()
    sport_country_by_player: dict[str, str] = {}
    for qid, entity in entities.items():
        sport_countries = _claim_item_ids(entity, "P1532")
        if sport_countries:
            sport_country_by_player[qid] = sport_countries[0]
            country_qids.add(sport_countries[0])

    country_entities = provider.client.entities(sorted(country_qids)) if country_qids else {}
    country_labels = {
        qid: _english_label(entity, qid)
        for qid, entity in country_entities.items()
    }

    changed = 0
    fallback_citizenship = 0
    for row in rows:
        player = row.get("player") or {}
        provider_id = player.get("id")
        if provider_id is None:
            continue
        qid = _qid_from_transient_id(provider_id)
        country_qid = sport_country_by_player.get(qid)
        if country_qid:
            label = country_labels.get(country_qid, country_qid)
            if label and label != player.get("nationality"):
                player["nationality"] = label
                changed += 1
        else:
            fallback_citizenship += 1

    audit["sportNationality"] = {
        "property": "P1532",
        "playersUsingSportCountry": changed,
        "playersUsingCitizenshipFallback": fallback_citizenship,
        "fallbackProperty": "P27",
    }


def _apply_verified_loans(
    raw: dict[str, Any],
    audit: dict[str, Any],
    overrides: dict[str, Any],
) -> None:
    team_id_by_name = {
        str((row.get("team") or {}).get("name")): int((row.get("team") or {})["id"])
        for row in (raw.get("teamsResponse") or {}).get("response") or []
        if (row.get("team") or {}).get("name") and (row.get("team") or {}).get("id") is not None
    }

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
        if owner_name not in team_id_by_name or borrower_name not in team_id_by_name:
            raise RuntimeError(
                f"Verified loan endpoint missing from Premier League identity map: "
                f"{full_name} ({owner_name} -> {borrower_name})"
            )

        matches = players_by_name.get(full_name, [])
        if len(matches) != 1:
            raise RuntimeError(
                f"Verified loan player must resolve exactly once: {full_name} (matches={len(matches)})"
            )
        player_row = matches[0]
        player_id = (player_row.get("player") or {}).get("id")
        if player_id is None:
            raise RuntimeError(f"Verified loan player has no transient source id: {full_name}")

        borrower_id = team_id_by_name[borrower_name]
        active_team_ids = {
            int(((stat.get("team") or {}).get("id")))
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
                    "out": {"id": team_id_by_name[owner_name], "name": owner_name},
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


def apply_verified_open_data_facts(
    provider: Any,
    raw: dict[str, Any],
    overrides_path: Path,
) -> dict[str, Any]:
    overrides = json.loads(overrides_path.read_text(encoding="utf-8"))
    audit = provider.last_audit
    _apply_sport_nationalities(provider, raw, audit)
    _apply_verified_loans(raw, audit, overrides)
    provider.last_audit = audit
    raw["openDataAudit"] = audit
    return raw
