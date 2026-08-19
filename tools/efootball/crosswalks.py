"""Crosswalks inferred only from secure FC26<->PlayersDB matches."""
from __future__ import annotations

from collections import Counter, defaultdict
from typing import Any, Iterable

EXPECTED_POSITION_FAMILIES: dict[int, set[str]] = {
    0: {"GK"},
    1: {"CB"},
    2: {"LB", "LWB"},
    3: {"RB", "RWB"},
    4: {"CDM", "CM", "CB"},
    5: {"CM", "CDM"},
    6: {"LM", "LW"},
    7: {"RM", "RW"},
    8: {"CAM", "CM"},
    9: {"LW", "LM"},
    10: {"RW", "RM"},
    11: {"ST", "CF", "CAM"},
    12: {"ST", "CF"},
}


def _primary_fc_position(fc_record: dict[str, Any]) -> str:
    return str(fc_record["player_positions"]).split(",", 1)[0].strip()


def derive_position_crosswalk(
    secure_pairs: Iterable[tuple[str, str]],
    fc_by_id: dict[str, dict[str, Any]],
    players_by_id: dict[str, dict[str, Any]],
) -> dict[str, Any]:
    distributions: dict[int, Counter[str]] = defaultdict(Counter)
    for fc_id, konami_id in secure_pairs:
        player = players_by_id[konami_id]
        code = player.get("registeredPosition")
        if code is None:
            continue
        distributions[int(code)][_primary_fc_position(fc_by_id[fc_id])] += 1

    output: dict[str, Any] = {}
    for code in sorted(distributions):
        counts = distributions[code]
        total = sum(counts.values())
        dominant, dominant_count = counts.most_common(1)[0]
        expected = EXPECTED_POSITION_FAMILIES.get(code, set())
        validated = dominant in expected and dominant_count >= 5
        output[str(code)] = {
            "sampleCount": total,
            "dominantFcPosition": dominant,
            "dominantCount": dominant_count,
            "dominantPurity": round(dominant_count / total, 6),
            "distribution": dict(sorted(counts.items())),
            "compatibleFcPositions": sorted(expected) if validated else [],
            "validated": validated,
        }
    return output


def validate_position_crosswalk(crosswalk: dict[str, Any]) -> None:
    missing = [str(code) for code in EXPECTED_POSITION_FAMILIES if str(code) not in crosswalk]
    invalid = [code for code, data in crosswalk.items() if not data["validated"]]
    if missing or invalid:
        raise ValueError(f"Position crosswalk failed validation: missing={missing}, invalid={invalid}")


def position_compatible(fc_record: dict[str, Any], player: dict[str, Any], crosswalk: dict[str, Any]) -> bool:
    code = player.get("registeredPosition")
    if code is None:
        return False
    accepted = set(crosswalk.get(str(int(code)), {}).get("compatibleFcPositions", []))
    if not accepted:
        return False
    fc_positions = {part.strip() for part in str(fc_record["player_positions"]).split(",") if part.strip()}
    return bool(fc_positions & accepted)


def derive_nationality_crosswalk(
    secure_pairs: Iterable[tuple[str, str]],
    fc_by_id: dict[str, dict[str, Any]],
    players_by_id: dict[str, dict[str, Any]],
    min_samples: int = 5,
    min_purity: float = 0.95,
) -> dict[str, Any]:
    counts: dict[str, Counter[str]] = defaultdict(Counter)
    for fc_id, konami_id in secure_pairs:
        nationalities = players_by_id[konami_id].get("nationalities") or []
        if not nationalities:
            continue
        code = str(nationalities[0])
        counts[code][str(fc_by_id[fc_id]["nationality_name"])] += 1

    output: dict[str, Any] = {}
    for code in sorted(counts, key=lambda value: int(value)):
        distribution = counts[code]
        total = sum(distribution.values())
        dominant, dominant_count = distribution.most_common(1)[0]
        purity = dominant_count / total
        output[code] = {
            "sampleCount": total,
            "dominantNationality": dominant,
            "dominantCount": dominant_count,
            "purity": round(purity, 6),
            "autoAccepted": total >= min_samples and purity >= min_purity,
            "distribution": dict(sorted(distribution.items())),
        }
    return output
