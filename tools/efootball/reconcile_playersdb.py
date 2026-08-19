#!/usr/bin/env python3
"""FC26 + PlayersDB/eFootball identity reconciliation CLI.

This tool never mutates FC26 ratings/attributes and never creates Android/Room
players. It produces offline identity reports only.
"""
from __future__ import annotations

import argparse
import gc
import hashlib
import json
import resource
import shutil
import sys
import tempfile
import time
from pathlib import Path
from typing import Any

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
    from tools.efootball.classification import age_consistency, classify, classification_reason
    from tools.efootball.crosswalks import derive_nationality_crosswalk, derive_position_crosswalk, validate_position_crosswalk
    from tools.efootball.fc26_reader import read_fc26
    from tools.efootball.match_engine import probable_matches, quality_metrics, secure_matches
    from tools.efootball.playersdb_reader import EXPECTED_FIELDS, compare_csv_jsonl_paths, iter_jsonl
else:
    from .classification import age_consistency, classify, classification_reason
    from .crosswalks import derive_nationality_crosswalk, derive_position_crosswalk, validate_position_crosswalk
    from .fc26_reader import read_fc26
    from .match_engine import probable_matches, quality_metrics, secure_matches
    from .playersdb_reader import EXPECTED_FIELDS, compare_csv_jsonl_paths, iter_jsonl

DATASET_VERSION = "2026-08-19"
FC26_EXPECTED_SHA256 = "4399cb2bcc2a14a2872e76a118f8f4bf64d7954503949c75751a14f33863e3b2"
REPORT_FILENAMES = {
    "summary": "fc26_efootball_identity_report.json",
    "secure": "fc26_efootball_secure_matches.json",
    "probable": "fc26_efootball_probable_matches.json",
    "candidates": "efootball_only_candidates.json",
    "rejected": "efootball_rejected_records.json",
    "nationality": "efootball_nationality_crosswalk.json",
    "position": "efootball_position_crosswalk.json",
    "performance": "fc26_efootball_performance.json",
    "markdown": "fc26_efootball_identity_summary.md",
}


def sha256_file(path: str | Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _stable_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def _write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(_stable_json(value), encoding="utf-8")


def _secure_detail(match: dict[str, Any], fc: dict[str, Any], player: dict[str, Any]) -> dict[str, Any]:
    return {
        "fc26PlayerId": match["fc26PlayerId"],
        "konamiId": match["konamiId"],
        "fc26Name": fc["long_name"],
        "efootballName": player.get("fullName") or ((player.get("playerName") or [None])[0]),
        "birthdate": player.get("birthdate"),
        "fc26Nationality": fc["nationality_name"],
        "efootballNationalityCode": (player.get("nationalities") or [None])[0],
        "fc26Positions": [part.strip() for part in fc["player_positions"].split(",") if part.strip()],
        "efootballRegisteredPosition": player.get("registeredPosition"),
        "efootballPositions": player.get("positions"),
        "fc26Height": int(fc["height_cm"]),
        "efootballHeight": player.get("height"),
        "fc26Weight": int(fc["weight_kg"]),
        "efootballWeight": player.get("weight"),
        "fc26PreferredFoot": fc["preferred_foot"],
        "efootballStrongFoot": player.get("strongFoot"),
        "matchLevel": match["matchLevel"],
        "matchReasons": match["matchReasons"],
        "confidence": match["confidence"],
    }


def _candidate_detail(player: dict[str, Any], classification: str) -> dict[str, Any]:
    age = age_consistency(player)
    return {
        "konamiID": str(player["konamiID"]),
        "playerName": player.get("playerName"),
        "fullName": player.get("fullName"),
        "birthdate": player.get("birthdate"),
        "age": player.get("age"),
        "nationalities": player.get("nationalities"),
        "registeredPosition": player.get("registeredPosition"),
        "positions": player.get("positions"),
        "height": player.get("height"),
        "weight": player.get("weight"),
        "strongFoot": player.get("strongFoot"),
        "starRating": player.get("starRating"),
        "youthClub": player.get("youthClub"),
        "update_at": player.get("update_at"),
        "game_versions": player.get("game_versions"),
        "real_face": player.get("real_face"),
        "base_konami_id": player.get("base_konami_id"),
        "classification": classification,
        "classificationReasons": classification_reason(player, classification, age),
        "ageConsistency": age,
    }


def _percent(numerator: int, denominator: int) -> float:
    return round(100.0 * numerator / denominator, 4) if denominator else 0.0


def build_reconciliation(
    fc26_path: str | Path,
    playersdb_jsonl_path: str | Path,
    playersdb_csv_path: str | Path | None = None,
) -> tuple[dict[str, Any], dict[str, Any]]:
    total_started = time.perf_counter()
    read_started = time.perf_counter()
    fc_records = read_fc26(fc26_path)
    playersdb_total = 0
    base_konami_present = 0
    seen_konami_ids: set[str] = set()
    duplicate_konami_ids: list[str] = []
    efootball_all: list[dict[str, Any]] = []
    for record in iter_jsonl(playersdb_jsonl_path):
        playersdb_total += 1
        konami_id = str(record["konamiID"])
        if konami_id in seen_konami_ids:
            duplicate_konami_ids.append(konami_id)
        seen_konami_ids.add(konami_id)
        if record.get("base_konami_id") not in {None, ""}:
            base_konami_present += 1
        if "eFootball 2026" in (record.get("game_versions") or []):
            efootball_all.append(record)
    if duplicate_konami_ids:
        duplicate_konami_ids = sorted(set(duplicate_konami_ids), key=int)
        raise ValueError(f"Duplicate PlayersDB konamiID values: {duplicate_konami_ids[:20]}")

    csv_equivalent: bool | None = None
    csv_mismatch_count: int | None = None
    if playersdb_csv_path:
        csv_equivalent, csv_mismatch_count, compared_rows = compare_csv_jsonl_paths(playersdb_csv_path, playersdb_jsonl_path)
        if compared_rows != playersdb_total:
            raise ValueError(f"CSV/JSONL row-count mismatch: compared={compared_rows}, jsonl={playersdb_total}")
    read_seconds = time.perf_counter() - read_started

    efootball_non_system = [record for record in efootball_all if not record.get("is_system")]
    match_started = time.perf_counter()
    secure_rows, matched_fc_ids, matched_player_ids = secure_matches(fc_records, efootball_non_system)
    fc_by_id = {str(record["player_id"]): record for record in fc_records}
    player_by_id = {str(record["konamiID"]): record for record in efootball_non_system}
    secure_pairs = {(row["fc26PlayerId"], row["konamiId"]) for row in secure_rows}
    position_crosswalk = derive_position_crosswalk(secure_pairs, fc_by_id, player_by_id)
    validate_position_crosswalk(position_crosswalk)
    nationality_crosswalk = derive_nationality_crosswalk(secure_pairs, fc_by_id, player_by_id)
    probable_rows, probable_player_ids, probable_metrics = probable_matches(
        fc_records, efootball_non_system, matched_fc_ids, matched_player_ids, position_crosswalk
    )
    match_seconds = time.perf_counter() - match_started

    classify_started = time.perf_counter()
    groups, classification_data = classify(
        efootball_all, efootball_non_system, matched_player_ids, probable_player_ids, fc_records
    )
    classify_seconds = time.perf_counter() - classify_started
    if sum(classification_data["counts"].values()) != len(efootball_all):
        raise AssertionError("A-E classification total does not equal eFootball 2026 total")
    if len({row["fc26PlayerId"] for row in secure_rows}) != len(secure_rows):
        raise AssertionError("Secure matches contain duplicate FC26 IDs")
    if len({row["konamiId"] for row in secure_rows}) != len(secure_rows):
        raise AssertionError("Secure matches contain duplicate Konami IDs")

    quality = quality_metrics(secure_rows, fc_by_id, player_by_id, nationality_crosswalk, position_crosswalk)
    secure_detail = [
        _secure_detail(row, fc_by_id[row["fc26PlayerId"]], player_by_id[row["konamiId"]])
        for row in sorted(secure_rows, key=lambda item: (item["matchLevel"], int(item["fc26PlayerId"]), int(item["konamiId"])))
    ]
    probable_detail = sorted(probable_rows, key=lambda item: int(item["konamiId"]))
    candidates: list[dict[str, Any]] = []
    rejected: list[dict[str, Any]] = []
    player_all_by_id = {str(record["konamiID"]): record for record in efootball_all}
    for konami_id in sorted(groups, key=int):
        group = groups[konami_id]
        if group in {"C1", "C2", "C3"}:
            candidates.append(_candidate_detail(player_all_by_id[konami_id], group))
        elif group == "D":
            rejected.append(_candidate_detail(player_all_by_id[konami_id], group))

    secure_count = classification_data["counts"]["A"]
    probable_count = classification_data["counts"]["B"]
    auto_nationalities = sum(1 for value in nationality_crosswalk.values() if value["autoAccepted"])
    age_counts = classification_data["ageStatusCounts"]
    fc_sha = sha256_file(fc26_path)
    json_sha = sha256_file(playersdb_jsonl_path)
    csv_sha = sha256_file(playersdb_csv_path) if playersdb_csv_path else None

    summary = {
        "schemaVersion": 1,
        "phase": "9.11A0",
        "policy": {
            "fc26RemainsPrimary": True,
            "playersDbUsage": "IDENTITY_COMPLEMENT_ONLY",
            "importsEfootballOnlyPlayers": False,
            "mutatesFc26RatingsPotentialAttributesOrClubs": False,
            "redistributionPermission": "UNRESOLVED_DO_NOT_BUNDLE_RAW_PLAYERSDB",
        },
        "sourceMetadata": {
            "fc26": {"sourceFile": Path(fc26_path).name, "sourceVersion": "2025-09-19", "sha256": fc_sha},
            "playersDbJsonl": {"sourceFile": Path(playersdb_jsonl_path).name, "sourceVersion": DATASET_VERSION, "sha256": json_sha},
            "playersDbCsv": ({"sourceFile": Path(playersdb_csv_path).name, "sourceVersion": DATASET_VERSION, "sha256": csv_sha} if playersdb_csv_path else None),
        },
        "fc26": {
            "playerCount": len(fc_records),
            "uniquePlayerIds": len({row["player_id"] for row in fc_records}),
            "sourceSha256MatchesExpected": fc_sha == FC26_EXPECTED_SHA256,
        },
        "playersDb": {
            "totalRecords": playersdb_total,
            "fields": EXPECTED_FIELDS,
            "fieldCount": len(EXPECTED_FIELDS),
            "jsonlCsvEquivalent": csv_equivalent,
            "semanticMismatchCount": csv_mismatch_count,
            "uniqueKonamiIds": len(seen_konami_ids),
            "duplicateKonamiIds": duplicate_konami_ids,
            "baseKonamiIdPopulated": base_konami_present,
            "baseKonamiIdStatus": "BASE_KONAMI_ID_NOT_AVAILABLE" if base_konami_present == 0 else "AVAILABLE",
        },
        "efootball2026": {
            "total": len(efootball_all),
            "system": sum(bool(row.get("is_system")) for row in efootball_all),
            "nonSystem": len(efootball_non_system),
        },
        "classification": {
            "groupA": classification_data["counts"]["A"],
            "groupB": classification_data["counts"]["B"],
            "groupC1": classification_data["counts"]["C1"],
            "groupC2": classification_data["counts"]["C2"],
            "groupC3": classification_data["counts"]["C3"],
            "groupD": classification_data["counts"]["D"],
            "groupE": classification_data["counts"]["E"],
            "sum": sum(classification_data["counts"].values()),
        },
        "matching": {
            "secureByLevel": dict(sorted(__import__("collections").Counter(row["matchLevel"] for row in secure_rows).items())),
            "probableByLevel": dict(sorted(__import__("collections").Counter(row["matchLevel"] for row in probable_rows).items())),
            "secureCoverageFc26Percent": _percent(secure_count, len(fc_records)),
            "secureAndProbableCoverageFc26Percent": _percent(secure_count + probable_count, len(fc_records)),
            "efootballNonSystemCoveredBySecureFc26Percent": _percent(secure_count, len(efootball_non_system)),
            "probableMetrics": probable_metrics,
            "baselineCorrection": {
                "previousB2Value1519WasCandidatePairCount": probable_metrics["b2CandidatePairs"] == 1519,
                "correctB2UniqueRecordCount": probable_metrics["b2Records"],
                "reason": "Group counts represent unique PlayersDB people; candidate pairs are reported separately when one record has multiple FC26 candidates.",
            },
        },
        "quality": quality,
        "crosswalks": {
            "nationalityCodesInferred": len(nationality_crosswalk),
            "nationalitiesAutoAccepted": auto_nationalities,
            "positionsMapped": len(position_crosswalk),
            "allPositionCodesValidated": all(item["validated"] for item in position_crosswalk.values()),
        },
        "anomalies": {
            "ageDifference0to1": age_counts.get("NORMAL", 0),
            "ageDifference2to4": age_counts.get("SUSPICIOUS", 0),
            "ageDifference5Plus": age_counts.get("STRONGLY_SUSPICIOUS", 0),
            "ageUnknown": age_counts.get("UNKNOWN", 0),
            "duplicateKonamiIds": len(duplicate_konami_ids),
            "duplicateSecureFc26Ids": len(secure_rows) - len({row["fc26PlayerId"] for row in secure_rows}),
            "duplicateSecureKonamiIds": len(secure_rows) - len({row["konamiId"] for row in secure_rows}),
            "probableRecordsWithMultipleCandidates": sum(len(row["candidates"]) > 1 for row in probable_rows),
        },
        "deterministicReports": [
            REPORT_FILENAMES["summary"], REPORT_FILENAMES["secure"], REPORT_FILENAMES["probable"],
            REPORT_FILENAMES["candidates"], REPORT_FILENAMES["rejected"], REPORT_FILENAMES["nationality"], REPORT_FILENAMES["position"],
        ],
        "volatilePerformanceReport": REPORT_FILENAMES["performance"],
    }

    peak_rss_kib = int(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss)
    performance = {
        "schemaVersion": 1,
        "volatile": True,
        "readSeconds": round(read_seconds, 6),
        "matchingSeconds": round(match_seconds, 6),
        "classificationSeconds": round(classify_seconds, 6),
        "totalSeconds": round(time.perf_counter() - total_started, 6),
        "peakResidentSetKiB": peak_rss_kib,
        "memoryMeasurement": "resource.getrusage(RUSAGE_SELF).ru_maxrss (Linux KiB)",
    }
    reports = {
        "summary": summary,
        "secure": {"schemaVersion": 1, "count": len(secure_detail), "matches": secure_detail},
        "probable": {"schemaVersion": 1, "count": len(probable_detail), "records": probable_detail},
        "candidates": {"schemaVersion": 1, "count": len(candidates), "records": candidates},
        "rejected": {"schemaVersion": 1, "count": len(rejected), "records": rejected},
        "nationality": {"schemaVersion": 1, "crosswalk": nationality_crosswalk},
        "position": {"schemaVersion": 1, "crosswalk": position_crosswalk},
    }
    return reports, performance


def write_reports(output_dir: str | Path, reports: dict[str, Any], performance: dict[str, Any]) -> dict[str, str]:
    output = Path(output_dir)
    output.mkdir(parents=True, exist_ok=True)
    hashes: dict[str, str] = {}
    for key in ["summary", "secure", "probable", "candidates", "rejected", "nationality", "position"]:
        path = output / REPORT_FILENAMES[key]
        _write_json(path, reports[key])
        hashes[path.name] = sha256_file(path)
    _write_json(output / REPORT_FILENAMES["performance"], performance)

    summary = reports["summary"]
    counts = summary["classification"]
    markdown = f"""# FC26 + eFootball identity reconciliation — Phase 9.11A0

- FC26 remains the factual primary source; no rating, potential, attribute or club is changed.
- PlayersDB is used only for offline identity reconciliation.
- Raw PlayersDB files and per-player derived reports are not intended for APK bundling while redistribution permission is unresolved.
- FC26 players: **{summary['fc26']['playerCount']:,}**
- PlayersDB records: **{summary['playersDb']['totalRecords']:,}**
- eFootball 2026: **{summary['efootball2026']['total']:,}** total / **{summary['efootball2026']['nonSystem']:,}** non-system
- A secure: **{counts['groupA']:,}**
- B probable (unique PlayersDB records): **{counts['groupB']:,}**
- C1 strong eFootball-only identity: **{counts['groupC1']:,}**
- C2 reasonable identity: **{counts['groupC2']:,}**
- C3 insufficient identity: **{counts['groupC3']:,}**
- D reject: **{counts['groupD']:,}**
- E indeterminate: **{counts['groupE']:,}**

## Baseline correction

The earlier audit value `1,519` for the no-DOB probable bucket represented **candidate relationships**, not unique PlayersDB records. The pipeline reports both values separately. This prevents one person with multiple plausible FC26 candidates from inflating Group B.

## Redistribution boundary

This repository should not bundle the raw PlayersDB export or eFootball-only players into the APK until redistribution permission and the missing professional-club/rating data are resolved.
"""
    (output / REPORT_FILENAMES["markdown"]).write_text(markdown, encoding="utf-8")
    hashes[REPORT_FILENAMES["markdown"]] = sha256_file(output / REPORT_FILENAMES["markdown"])
    return hashes


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fc26", required=True, help="FC26_20250921.csv")
    parser.add_argument("--playersdb-jsonl", required=True, help="PlayersDB_20260819.jsonl")
    parser.add_argument("--playersdb-csv", help="Optional PlayersDB CSV for semantic-equivalence validation")
    parser.add_argument("--output-dir", default="reports", help="Report output directory")
    parser.add_argument("--verify-determinism", action="store_true", help="Run reconciliation twice and compare deterministic report payloads")
    args = parser.parse_args(argv)

    reports, performance = build_reconciliation(args.fc26, args.playersdb_jsonl, args.playersdb_csv)
    summary_for_stdout = reports["summary"]
    hashes = write_reports(args.output_dir, reports, performance)
    deterministic_names = [REPORT_FILENAMES[key] for key in ["summary", "secure", "probable", "candidates", "rejected", "nationality", "position", "markdown"]]
    expected_hashes = {name: hashes[name] for name in deterministic_names}

    if args.verify_determinism:
        del reports
        gc.collect()
        second, second_performance = build_reconciliation(args.fc26, args.playersdb_jsonl, args.playersdb_csv)
        temp_dir = Path(tempfile.mkdtemp(prefix="efootball-determinism-"))
        try:
            second_hashes = write_reports(temp_dir, second, second_performance)
            actual = {name: second_hashes[name] for name in deterministic_names}
            if expected_hashes != actual:
                raise SystemExit(f"Determinism check failed: {expected_hashes} != {actual}")
        finally:
            shutil.rmtree(temp_dir, ignore_errors=True)

    print(_stable_json({"summary": summary_for_stdout, "reportSha256": hashes, "determinismVerified": bool(args.verify_determinism)}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
