from __future__ import annotations

import json
from pathlib import Path
from typing import Any

ASSOCIATION_FOOTBALL_PLAYER_QID = "Q937857"


def _claim_item_ids(entity: dict[str, Any], prop: str) -> list[str]:
    result: list[str] = []
    for claim in (entity.get("claims") or {}).get(prop, []):
        if claim.get("rank") == "deprecated":
            continue
        value = ((claim.get("mainsnak") or {}).get("datavalue") or {}).get("value")
        if isinstance(value, dict) and value.get("id"):
            result.append(str(value["id"]))
    return result


def _claim_time(entity: dict[str, Any], prop: str) -> str | None:
    for claim in (entity.get("claims") or {}).get(prop, []):
        if claim.get("rank") == "deprecated":
            continue
        value = ((claim.get("mainsnak") or {}).get("datavalue") or {}).get("value")
        if not isinstance(value, dict):
            continue
        raw = str(value.get("time") or "")
        precision = int(value.get("precision") or 0)
        if precision >= 11 and len(raw) >= 11:
            return raw[1:11] if raw.startswith("+") else raw[:10]
    return None


def _position_label(entity: dict[str, Any], provider: Any, override: dict[str, str] | None) -> str | None:
    if override:
        return override["position"]
    description = str(((entity.get("descriptions") or {}).get("en") or {}).get("value") or "")
    mapped = provider_position(description)
    if mapped:
        return mapped
    position_qids = _claim_item_ids(entity, "P413")
    if not position_qids:
        return None
    position_entities = provider.client.entities(position_qids[:1])
    label = str((((position_entities.get(position_qids[0]) or {}).get("labels") or {}).get("en") or {}).get("value") or "")
    return provider_position(label)


def provider_position(value: str) -> str | None:
    text = value.strip().casefold().replace("_", " ")
    if "goalkeeper" in text or text == "keeper":
        return "Goalkeeper"
    if any(token in text for token in (
        "defender", "centre-back", "center-back", "full-back", "fullback",
        "wing-back", "wingback", "left-back", "right-back", "sweeper", "stopper",
    )):
        return "Defender"
    if any(token in text for token in (
        "midfielder", "midfield", "wing half", "wing-half", "half-back", "half back", "playmaker",
    )):
        return "Midfielder"
    if any(token in text for token in ("forward", "striker", "winger", "attacker")):
        return "Forward"
    return None


def materialize_missing_verified_memberships(
    provider: Any,
    raw: dict[str, Any],
    overrides_path: Path,
) -> None:
    """Recover only explicitly verified squad memberships rejected/missed by discovery.

    The override supplies only the current club association and Wikipedia title. Every player fact
    still comes from Wikidata and must satisfy the same minimum contract: association-football
    occupation, exact DOB, at least P27 or P1532 nationality metadata, and a supported position
    (structured, description-derived, or separately source-verified position override).
    QIDs remain transient and are never written to canonical output.
    """
    overrides = json.loads(overrides_path.read_text(encoding="utf-8"))
    memberships = overrides.get("squadMemberships", []) or []
    if not memberships:
        return

    team_ids = {
        str((row.get("team") or {}).get("name")): int((row.get("team") or {})["id"])
        for row in (raw.get("teamsResponse") or {}).get("response") or []
        if (row.get("team") or {}).get("name") and (row.get("team") or {}).get("id") is not None
    }
    position_overrides = {
        str(row.get("fullName") or "").strip(): {
            "position": str(row.get("position") or "").strip(),
            "source": str(row.get("source") or "").strip(),
        }
        for row in overrides.get("positions", []) or []
    }
    nationality_overrides = {
        str(row.get("fullName") or "").strip(): str(row.get("nationality") or "").strip()
        for row in overrides.get("nationalities", []) or []
    }
    player_rows = (raw.get("playersResponse") or {}).get("response") or []
    audit = raw.get("openDataAudit") or provider.last_audit

    for membership in memberships:
        club = str(membership.get("club") or "").strip()
        full_name = str(membership.get("fullName") or "").strip()
        wikipedia_title = str(membership.get("wikipediaTitle") or "").strip()
        source = str(membership.get("source") or "").strip()
        if not all((club, full_name, wikipedia_title, source)):
            raise RuntimeError(f"Incomplete verified squad membership: {membership}")
        if club not in team_ids:
            raise RuntimeError(f"Verified membership club not collected: {club}")
        team_id = team_ids[club]

        existing = [
            row for row in player_rows
            if str((row.get("player") or {}).get("name") or "").strip() == full_name
            and any(
                int((stat.get("team") or {}).get("id")) == team_id
                for stat in row.get("statistics") or []
                if (stat.get("team") or {}).get("id") is not None
            )
        ]
        if len(existing) == 1:
            continue
        if len(existing) > 1:
            raise RuntimeError(f"Verified membership already duplicated: {club}/{full_name}")

        title_qids = provider.client.qids_for_titles([wikipedia_title])
        qids = sorted(set(title_qids.values()))
        if len(qids) != 1:
            raise RuntimeError(
                f"Verified membership title must resolve one Wikidata entity: {wikipedia_title} (qids={qids})"
            )
        qid = qids[0]
        entity = (provider.client.entities([qid]) or {}).get(qid) or {}
        if ASSOCIATION_FOOTBALL_PLAYER_QID not in _claim_item_ids(entity, "P106"):
            raise RuntimeError(f"Verified membership entity is not an association-football player: {full_name}")

        label = str(((entity.get("labels") or {}).get("en") or {}).get("value") or "").strip()
        if label.casefold() != full_name.casefold():
            raise RuntimeError(
                f"Verified membership identity mismatch: config={full_name!r}, Wikidata={label!r}"
            )
        birth = _claim_time(entity, "P569")
        citizenship_qids = _claim_item_ids(entity, "P27")
        sport_country_qids = _claim_item_ids(entity, "P1532")
        if not birth:
            raise RuntimeError(f"Verified membership has no exact birth date: {full_name}")
        if not citizenship_qids and not sport_country_qids and full_name not in nationality_overrides:
            raise RuntimeError(f"Verified membership has no P27/P1532 nationality facts: {full_name}")

        nationality_qid = (citizenship_qids or sport_country_qids or [None])[0]
        nationality = nationality_overrides.get(full_name, "")
        if not nationality and nationality_qid:
            country_entity = (provider.client.entities([nationality_qid]) or {}).get(nationality_qid) or {}
            nationality = str(((country_entity.get("labels") or {}).get("en") or {}).get("value") or "").strip()
        if not nationality:
            raise RuntimeError(f"Verified membership nationality could not be labeled: {full_name}")

        position = _position_label(entity, provider, position_overrides.get(full_name))
        if not position:
            raise RuntimeError(f"Verified membership has no supported position: {full_name}")

        player_rows.append({
            "player": {
                "id": int(qid[1:]),
                "name": label,
                "birth": {"date": birth},
                "nationality": nationality,
            },
            "statistics": [{
                "team": {"id": team_id},
                "games": {"position": position, "number": None},
            }],
        })
        audit.setdefault("verifiedOverridesUsed", []).append({
            "kind": "squadMembershipMaterialized",
            "player": full_name,
            "club": club,
            "source": source,
        })

    provider.last_audit = audit
    raw["openDataAudit"] = audit
