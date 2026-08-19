"""A-E classification for eFootball 2026 records after matching."""
from __future__ import annotations

from collections import Counter, defaultdict
from datetime import date
from typing import Any

from .match_engine import unresolved_candidate
from .name_normalization import is_robust_name, normalize_name

REFERENCE_DATE = date(2026, 8, 19)


def age_consistency(player: dict[str, Any]) -> dict[str, Any]:
    birthdate = player.get("birthdate")
    supplied_age = player.get("age")
    if not birthdate or supplied_age is None:
        return {"status": "UNKNOWN", "derivedAge": None, "difference": None}
    try:
        year, month, day = (int(part) for part in str(birthdate).split("-"))
        derived = REFERENCE_DATE.year - year - ((REFERENCE_DATE.month, REFERENCE_DATE.day) < (month, day))
    except (TypeError, ValueError):
        return {"status": "INVALID_BIRTHDATE", "derivedAge": None, "difference": None}
    difference = abs(derived - int(supplied_age))
    if difference <= 1:
        status = "NORMAL"
    elif difference <= 4:
        status = "SUSPICIOUS"
    else:
        status = "STRONGLY_SUSPICIOUS"
    return {"status": status, "derivedAge": derived, "difference": difference}


def _identity_name(player: dict[str, Any]) -> bool:
    if is_robust_name(player.get("fullName")):
        return True
    return any(is_robust_name(value) for value in (player.get("playerName") or []))


def _core_identity_fields(player: dict[str, Any]) -> bool:
    return (
        _identity_name(player)
        and bool(player.get("nationalities"))
        and player.get("registeredPosition") is not None
        and bool(player.get("positions"))
        and player.get("height") is not None
        and player.get("weight") is not None
        and player.get("strongFoot") is not None
    )


def classification_reason(player: dict[str, Any], classification: str, age: dict[str, Any]) -> list[str]:
    if classification == "D":
        if player.get("is_system"):
            return ["SYSTEM_PLAYER"]
        return ["STRONGLY_SUSPICIOUS_AGE"]
    if classification == "E":
        return ["POSSIBLE_UNRESOLVED_FC26_OVERLAP"]
    reasons = ["NO_ACCEPTED_FC26_MATCH"]
    if classification == "C1":
        reasons.extend(["BIRTHDATE_PRESENT", "CORE_IDENTITY_COMPLETE"])
    elif classification == "C2":
        reasons.extend(["BIRTHDATE_MISSING", "CORE_IDENTITY_COMPLETE"])
    else:
        reasons.append("CORE_IDENTITY_INCOMPLETE")
    if age["status"] == "SUSPICIOUS":
        reasons.append("SUSPICIOUS_AGE")
    return reasons


def classify(
    all_efootball_2026: list[dict[str, Any]],
    non_system_players: list[dict[str, Any]],
    secure_player_ids: set[str],
    probable_player_ids: set[str],
    fc_records: list[dict[str, Any]],
) -> tuple[dict[str, str], dict[str, Any]]:
    fc_by_dob: dict[str, list[dict[str, Any]]] = defaultdict(list)
    fc_name_index: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for fc in fc_records:
        fc_by_dob[str(fc["dob"])].append(fc)
        for name in {normalize_name(fc["long_name"]), normalize_name(fc["short_name"])}:
            if name:
                fc_name_index[name].append(fc)

    groups: dict[str, str] = {}
    ages: dict[str, dict[str, Any]] = {}

    for player in all_efootball_2026:
        konami_id = str(player["konamiID"])
        age = age_consistency(player)
        ages[konami_id] = age
        if player.get("is_system"):
            groups[konami_id] = "D"

    for player in non_system_players:
        konami_id = str(player["konamiID"])
        if konami_id in secure_player_ids:
            groups[konami_id] = "A"
            continue
        if konami_id in probable_player_ids:
            groups[konami_id] = "B"
            continue
        age = ages[konami_id]
        if age["status"] == "STRONGLY_SUSPICIOUS":
            groups[konami_id] = "D"
            continue
        if unresolved_candidate(fc_records, player, fc_by_dob, fc_name_index):
            groups[konami_id] = "E"
            continue
        if _core_identity_fields(player):
            groups[konami_id] = "C1" if player.get("birthdate") else "C2"
        else:
            groups[konami_id] = "C3"

    if len(groups) != len(all_efootball_2026):
        raise AssertionError(f"Classification did not cover every eFootball 2026 record: {len(groups)} != {len(all_efootball_2026)}")

    counts = Counter(groups.values())
    age_counts = Counter(age["status"] for age in ages.values())
    return groups, {
        "counts": {key: counts.get(key, 0) for key in ["A", "B", "C1", "C2", "C3", "D", "E"]},
        "ageStatusCounts": dict(sorted(age_counts.items())),
        "ages": ages,
    }
