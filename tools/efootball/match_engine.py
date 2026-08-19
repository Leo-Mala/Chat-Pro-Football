"""Conservative FC26 <-> eFootball identity matching."""
from __future__ import annotations

from collections import Counter, defaultdict
from difflib import SequenceMatcher
from typing import Any, Iterable

from .crosswalks import EXPECTED_POSITION_FAMILIES, position_compatible
from .name_normalization import normalize_name, token_signature

DOB_PROBABLE_SIMILARITY = 0.8679245283
UNRESOLVED_SIMILARITY = 0.80


def _one_to_one(edges: Iterable[tuple[str, str]]) -> set[tuple[str, str]]:
    edges = list(edges)
    fc_degree = Counter(fc_id for fc_id, _ in edges)
    konami_degree = Counter(konami_id for _, konami_id in edges)
    return {
        (fc_id, konami_id)
        for fc_id, konami_id in edges
        if fc_degree[fc_id] == 1 and konami_degree[konami_id] == 1
    }


def _foot_matches(fc: dict[str, Any], player: dict[str, Any]) -> bool:
    strong = player.get("strongFoot")
    if strong is None:
        return False
    return (fc["preferred_foot"] == "Right" and int(strong) == 0) or (
        fc["preferred_foot"] == "Left" and int(strong) == 1
    )


def _height_within(fc: dict[str, Any], player: dict[str, Any], maximum: int = 2) -> bool:
    height = player.get("height")
    if height is None:
        return False
    return abs(int(fc["height_cm"]) - int(height)) <= maximum


def _player_name_aliases(player: dict[str, Any]) -> set[str]:
    return {normalize_name(value) for value in (player.get("playerName") or []) if normalize_name(value)}


def _all_primary_names(player: dict[str, Any]) -> list[str]:
    names: list[str] = []
    if player.get("fullName"):
        names.append(str(player["fullName"]))
    names.extend(str(value) for value in (player.get("playerName") or []) if value)
    return names


def _best_name_similarity(fc: dict[str, Any], player: dict[str, Any]) -> float:
    fc_name = normalize_name(fc["long_name"])
    return max(
        (SequenceMatcher(None, fc_name, normalize_name(name)).ratio() for name in _all_primary_names(player)),
        default=0.0,
    )


def secure_matches(
    fc_records: list[dict[str, Any]], players: list[dict[str, Any]]
) -> tuple[list[dict[str, Any]], set[str], set[str]]:
    """Build secure A1/A2/A3 matches, enforcing 1:1 at every level."""
    fc_by_dob: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for fc in fc_records:
        fc_by_dob[str(fc["dob"])].append(fc)

    matched_fc: set[str] = set()
    matched_players: set[str] = set()
    rows: list[dict[str, Any]] = []

    edges: list[tuple[str, str]] = []
    for player in players:
        dob = player.get("birthdate")
        full_name = player.get("fullName")
        if not dob or not full_name:
            continue
        normalized = normalize_name(full_name)
        for fc in fc_by_dob.get(str(dob), []):
            if normalized == normalize_name(fc["long_name"]):
                edges.append((str(fc["player_id"]), str(player["konamiID"])))
    a1 = _one_to_one(edges)
    for fc_id, konami_id in sorted(a1, key=lambda pair: (int(pair[0]), int(pair[1]))):
        rows.append({"fc26PlayerId": fc_id, "konamiId": konami_id, "matchLevel": "A1", "matchReasons": ["DOB_EXACT", "FULL_NAME_EXACT"], "confidence": "SECURE"})
    matched_fc.update(fc_id for fc_id, _ in a1)
    matched_players.update(konami_id for _, konami_id in a1)

    edges = []
    for player in players:
        konami_id = str(player["konamiID"])
        dob = player.get("birthdate")
        if konami_id in matched_players or not dob:
            continue
        aliases = _player_name_aliases(player)
        for fc in fc_by_dob.get(str(dob), []):
            fc_id = str(fc["player_id"])
            if fc_id in matched_fc:
                continue
            if aliases & {normalize_name(fc["long_name"]), normalize_name(fc["short_name"])}:
                edges.append((fc_id, konami_id))
    a2 = _one_to_one(edges)
    for fc_id, konami_id in sorted(a2, key=lambda pair: (int(pair[0]), int(pair[1]))):
        rows.append({"fc26PlayerId": fc_id, "konamiId": konami_id, "matchLevel": "A2", "matchReasons": ["DOB_EXACT", "PLAYER_NAME_ALIAS_EXACT"], "confidence": "SECURE"})
    matched_fc.update(fc_id for fc_id, _ in a2)
    matched_players.update(konami_id for _, konami_id in a2)

    edges = []
    for player in players:
        konami_id = str(player["konamiID"])
        dob = player.get("birthdate")
        if konami_id in matched_players or not dob:
            continue
        signatures = {
            signature
            for name in _all_primary_names(player)
            if len(signature := token_signature(name)) >= 2
        }
        if not signatures:
            continue
        for fc in fc_by_dob.get(str(dob), []):
            fc_id = str(fc["player_id"])
            if fc_id in matched_fc:
                continue
            if token_signature(fc["long_name"]) not in signatures:
                continue
            if not _height_within(fc, player, 2) or not _foot_matches(fc, player):
                continue
            edges.append((fc_id, konami_id))
    a3 = _one_to_one(edges)
    for fc_id, konami_id in sorted(a3, key=lambda pair: (int(pair[0]), int(pair[1]))):
        rows.append({"fc26PlayerId": fc_id, "konamiId": konami_id, "matchLevel": "A3", "matchReasons": ["DOB_EXACT", "TOKEN_SIGNATURE_EXACT", "HEIGHT_WITHIN_2CM", "PREFERRED_FOOT_EXACT"], "confidence": "SECURE"})
    matched_fc.update(fc_id for fc_id, _ in a3)
    matched_players.update(konami_id for _, konami_id in a3)

    return rows, matched_fc, matched_players


def probable_matches(
    fc_records: list[dict[str, Any]],
    players: list[dict[str, Any]],
    matched_fc: set[str],
    matched_players: set[str],
    position_crosswalk: dict[str, Any],
) -> tuple[list[dict[str, Any]], set[str], dict[str, int]]:
    """Return Group B by unique PlayersDB record, preserving all candidates."""
    fc_by_dob: dict[str, list[dict[str, Any]]] = defaultdict(list)
    fc_name_index: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for fc in fc_records:
        fc_by_dob[str(fc["dob"])].append(fc)
        for name in {normalize_name(fc["long_name"]), normalize_name(fc["short_name"])}:
            if name:
                fc_name_index[name].append(fc)

    by_player: dict[str, list[dict[str, Any]]] = defaultdict(list)
    b1_ids: set[str] = set()
    b2_ids: set[str] = set()
    b2_edges = 0

    for player in players:
        konami_id = str(player["konamiID"])
        dob = player.get("birthdate")
        if konami_id in matched_players or not dob:
            continue
        candidates: list[dict[str, Any]] = []
        for fc in fc_by_dob.get(str(dob), []):
            fc_id = str(fc["player_id"])
            if fc_id in matched_fc:
                continue
            if not _height_within(fc, player, 2) or not _foot_matches(fc, player):
                continue
            if not position_compatible(fc, player, position_crosswalk):
                continue
            similarity = _best_name_similarity(fc, player)
            if similarity >= DOB_PROBABLE_SIMILARITY:
                candidates.append({"fc26PlayerId": fc_id, "fc26Name": fc["long_name"], "nameSimilarity": round(similarity, 6)})
        if candidates:
            b1_ids.add(konami_id)
            by_player[konami_id].extend(sorted(candidates, key=lambda row: (-row["nameSimilarity"], int(row["fc26PlayerId"]))))

    for player in players:
        konami_id = str(player["konamiID"])
        if konami_id in matched_players or konami_id in b1_ids or player.get("birthdate"):
            continue
        names: set[str] = set()
        if player.get("fullName"):
            names.add(normalize_name(player["fullName"]))
        names.update(_player_name_aliases(player))
        candidates_by_fc: dict[str, dict[str, Any]] = {}
        for name in sorted(names):
            for fc in fc_name_index.get(name, []):
                fc_id = str(fc["player_id"])
                if fc_id in matched_fc:
                    continue
                if not _height_within(fc, player, 2) or not _foot_matches(fc, player):
                    continue
                if not position_compatible(fc, player, position_crosswalk):
                    continue
                candidates_by_fc[fc_id] = {"fc26PlayerId": fc_id, "fc26Name": fc["long_name"], "matchedNormalizedName": name}
        if candidates_by_fc:
            b2_ids.add(konami_id)
            b2_edges += len(candidates_by_fc)
            by_player[konami_id].extend(candidates_by_fc[fc_id] for fc_id in sorted(candidates_by_fc, key=int))

    output: list[dict[str, Any]] = []
    player_by_id = {str(player["konamiID"]): player for player in players}
    for konami_id in sorted(set(by_player), key=int):
        player = player_by_id[konami_id]
        level = "B1" if konami_id in b1_ids else "B2"
        missing = [] if level == "B1" else ["BIRTHDATE"]
        output.append({
            "konamiId": konami_id,
            "efootballName": player.get("fullName") or ((player.get("playerName") or [None])[0]),
            "birthdate": player.get("birthdate"),
            "matchLevel": level,
            "confidence": "PROBABLE",
            "matchReasons": ["DOB_EXACT", "NAME_SIMILAR", "HEIGHT_WITHIN_2CM", "PREFERRED_FOOT_EXACT", "POSITION_COMPATIBLE"] if level == "B1" else ["PRIMARY_NAME_EXACT", "HEIGHT_WITHIN_2CM", "PREFERRED_FOOT_EXACT", "POSITION_COMPATIBLE"],
            "missingEvidence": missing,
            "conflicts": ["MULTIPLE_FC26_CANDIDATES"] if len(by_player[konami_id]) > 1 else [],
            "candidates": by_player[konami_id],
        })

    metrics = {
        "b1Records": len(b1_ids),
        "b2Records": len(b2_ids),
        "b2CandidatePairs": b2_edges,
        "probableRecords": len(output),
        "probableCandidatePairs": sum(len(item["candidates"]) for item in output),
    }
    return output, set(by_player), metrics


def unresolved_candidate(
    fc_records: list[dict[str, Any]],
    player: dict[str, Any],
    fc_by_dob: dict[str, list[dict[str, Any]]],
    fc_name_index: dict[str, list[dict[str, Any]]],
) -> bool:
    """Conservative signal that a remaining record may still overlap FC26."""
    dob = player.get("birthdate")
    if dob:
        return any(_best_name_similarity(fc, player) >= UNRESOLVED_SIMILARITY for fc in fc_by_dob.get(str(dob), []))
    names: set[str] = set()
    if player.get("fullName"):
        names.add(normalize_name(player["fullName"]))
    names.update(_player_name_aliases(player))
    return any(fc_name_index.get(name) for name in names)


def quality_metrics(
    secure_rows: list[dict[str, Any]],
    fc_by_id: dict[str, dict[str, Any]],
    players_by_id: dict[str, dict[str, Any]],
    nationality_crosswalk: dict[str, Any],
    position_crosswalk: dict[str, Any],
) -> dict[str, Any]:
    total = len(secure_rows)
    counters = Counter()
    nat_total = nat_match = 0
    pos_total = pos_match = 0
    for match in secure_rows:
        fc = fc_by_id[match["fc26PlayerId"]]
        player = players_by_id[match["konamiId"]]
        if _foot_matches(fc, player):
            counters["foot"] += 1
        if player.get("height") is not None:
            diff = abs(int(fc["height_cm"]) - int(player["height"]))
            counters["heightMeasured"] += 1
            if diff == 0: counters["heightExact"] += 1
            if diff <= 1: counters["height1"] += 1
            if diff <= 2: counters["height2"] += 1
        if player.get("weight") is not None:
            diff = abs(int(fc["weight_kg"]) - int(player["weight"]))
            counters["weightMeasured"] += 1
            if diff == 0: counters["weightExact"] += 1
            if diff <= 1: counters["weight1"] += 1
            if diff <= 2: counters["weight2"] += 1
        nationalities = player.get("nationalities") or []
        if nationalities:
            cross = nationality_crosswalk.get(str(nationalities[0]))
            if cross and cross["autoAccepted"]:
                nat_total += 1
                if cross["dominantNationality"] == fc["nationality_name"]:
                    nat_match += 1
        positions_vector = player.get("positions") or []
        if positions_vector:
            pos_total += 1
            active_codes = {index for index, value in enumerate(positions_vector) if value and int(value) > 0}
            compatible_positions: set[str] = set()
            for code in active_codes:
                compatible_positions.update(EXPECTED_POSITION_FAMILIES.get(code, set()))
            fc_positions = {part.strip() for part in str(fc["player_positions"]).split(",") if part.strip()}
            if fc_positions & compatible_positions:
                pos_match += 1

    def pct(value: int, denominator: int) -> float:
        return round(100.0 * value / denominator, 4) if denominator else 0.0

    return {
        "preferredFootAgreementPercent": pct(counters["foot"], total),
        "heightExactPercent": pct(counters["heightExact"], counters["heightMeasured"]),
        "heightWithin1CmPercent": pct(counters["height1"], counters["heightMeasured"]),
        "heightWithin2CmPercent": pct(counters["height2"], counters["heightMeasured"]),
        "weightExactPercent": pct(counters["weightExact"], counters["weightMeasured"]),
        "weightWithin1KgPercent": pct(counters["weight1"], counters["weightMeasured"]),
        "weightWithin2KgPercent": pct(counters["weight2"], counters["weightMeasured"]),
        "nationalityAgreementPercent": pct(nat_match, nat_total),
        "nationalityAgreementSampleCount": nat_total,
        "positionCompatibilityPercent": pct(pos_match, pos_total),
    }
