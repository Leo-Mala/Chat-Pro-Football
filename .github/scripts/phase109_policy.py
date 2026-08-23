#!/usr/bin/env python3
"""Phase 10.9 fail-closed CI/CD policy and provenance validators.

This script intentionally uses only the Python standard library so the policy gate does not
need an extra package installation before it can decide whether repository validation is safe.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path
from typing import Any

FC26_EXPECTED = {
    "datasetPlayers": 18405,
    "validatedPlayers": 18405,
    "duplicatePlayerIds": 0,
    "duplicateTeamIds": 0,
    "overallMutated": 0,
    "potentialMutated": 0,
    "attributesMutated": 0,
    "resolvedLoans": 816,
    "rejectedLoans": 509,
    "borrowerNotFound": 448,
    "ownerNotFound": 60,
    "ambiguousLoans": 1,
}

PERF_LIMITS = {
    "queries": 25000,
    "teamUpdates": 500,
    "heap": 350_000_000,
    "wal": 85_000_000,
    "walAfterTruncate": 1_048_576,
}


def fail(message: str) -> None:
    raise ValueError(message)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def parse_database_versions(source: str) -> tuple[int, int]:
    const_match = re.search(r"APP_DATABASE_SCHEMA_VERSION\s*=\s*(\d+)", source)
    annotation_match = re.search(r"@Database\([\s\S]*?\bversion\s*=\s*(\d+)", source)
    require(const_match is not None, "APP_DATABASE_SCHEMA_VERSION not found")
    require(annotation_match is not None, "Room @Database version not found")
    return int(const_match.group(1)), int(annotation_match.group(1))


def validate_room_values(current: int, annotation: int) -> None:
    require(current == annotation, f"Room version mismatch: constant={current}, annotation={annotation}")
    require(current >= 2, f"Room schema version is unexpectedly low: {current}")


def validate_room_repository(root: Path) -> dict[str, Any]:
    database_path = root / "app/src/main/java/com/example/data/database.kt"
    source = database_path.read_text(encoding="utf-8")
    current, annotation = parse_database_versions(source)
    validate_room_values(current, annotation)
    previous = current - 1

    current_schema = root / f"app/schemas/com.example.data.AppDatabase/{current}.json"
    previous_schema = root / f"app/schemas/com.example.data.AppDatabase/{previous}.json"
    migration_file = root / f"app/src/main/java/com/example/data/migrations/Migration_{previous}_{current}.kt"
    migration_symbol = f"MIGRATION_{previous}_{current}"

    require(current_schema.is_file(), f"Missing current Room schema: {current_schema}")
    require(previous_schema.is_file(), f"Missing previous Room schema: {previous_schema}")
    require(migration_file.is_file(), f"Missing previous-to-current migration: {migration_file}")
    require(migration_symbol in source, f"{migration_symbol} is not registered by AppDatabase")
    require("ALL_MIGRATIONS" in source, "AppDatabase.ALL_MIGRATIONS is missing")

    destructive = []
    for path in (root / "app/src/main").rglob("*.kt"):
        text = path.read_text(encoding="utf-8", errors="replace")
        if "fallbackToDestructiveMigration" in text:
            destructive.append(str(path.relative_to(root)))
    require(not destructive, f"Destructive Room fallback is forbidden: {destructive}")

    return {
        "currentSchemaVersion": current,
        "previousSchemaVersion": previous,
        "migrationSymbol": migration_symbol,
        "currentSchema": str(current_schema.relative_to(root)),
        "previousSchema": str(previous_schema.relative_to(root)),
    }


def report_value(report: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        if key in report:
            return report[key]
    return None


def validate_fc26_report(report: dict[str, Any], expected_head: str | None = None) -> None:
    for key, expected in FC26_EXPECTED.items():
        actual = report_value(report, key)
        require(actual == expected, f"FC26 {key}: expected {expected}, got {actual}")

    persisted = report_value(report, "persistedPlayers", "persistedPlayersIncludingFallback")
    require(persisted == 60885, f"FC26 persisted players: expected 60885, got {persisted}")
    loan_players = report_value(report, "loanPlayers", "datasetLoanPlayers")
    require(loan_players == 1325, f"FC26 loan players: expected 1325, got {loan_players}")

    processed = report_value(report, "processedPlayers")
    imported = report_value(report, "importedPlayers")
    not_imported = report_value(report, "notImported")
    if processed is not None:
        require(processed == 18405, f"FC26 processedPlayers: expected 18405, got {processed}")
    if imported is not None:
        require(imported == 18405, f"FC26 importedPlayers: expected 18405, got {imported}")
    if not_imported is not None:
        require(not_imported == 0, f"FC26 notImported: expected 0, got {not_imported}")

    if expected_head:
        actual_head = report.get("auditHead")
        require(actual_head == expected_head, f"FC26 report head mismatch: {actual_head} != {expected_head}")


def validate_performance_report(report: dict[str, Any], expected_head: str | None = None) -> None:
    if expected_head:
        require(report.get("auditHead") == expected_head, "Performance report SHA does not match audited HEAD")
    queries = report.get("queries", {})
    memory = report.get("memory", {})
    sqlite = report.get("sqlite", {})
    timing = report.get("timingMillis", {})
    profile = report.get("profile", "normal")
    rollover_limit = 60_000 if profile == "constrained" else 20_000

    require(timing.get("seasonRolloverTotal", rollover_limit + 1) <= rollover_limit,
            f"Rollover exceeded {profile} budget")
    require(queries.get("total", PERF_LIMITS["queries"] + 1) <= PERF_LIMITS["queries"], "Query budget exceeded")
    require(queries.get("activeLoanLookupPerPlayer") == 0, "Active-loan N+1 returned")
    require(queries.get("fullEntityPlayerUpdates") == 0, "Full-entity Player update loop returned")
    require(queries.get("teamUpdateStatements", PERF_LIMITS["teamUpdates"] + 1) <= PERF_LIMITS["teamUpdates"],
            "Team update budget exceeded")
    require(memory.get("heapPeakObservedBytes", PERF_LIMITS["heap"] + 1) <= PERF_LIMITS["heap"], "Heap budget exceeded")
    require(sqlite.get("walPeakObservedBytes", PERF_LIMITS["wal"] + 1) <= PERF_LIMITS["wal"], "WAL budget exceeded")
    require(sqlite.get("walAfterTruncateCheckpointBytes", PERF_LIMITS["walAfterTruncate"] + 1)
            <= PERF_LIMITS["walAfterTruncate"], "Post-TRUNCATE WAL budget exceeded")


def validate_artifacts(expected_head: str, artifacts: list[dict[str, Any]]) -> None:
    require(bool(artifacts), "No certification artifacts were supplied")
    for artifact in artifacts:
        name = str(artifact.get("name", ""))
        size = int(artifact.get("size", 0))
        require(expected_head in name, f"Artifact is not pinned to audited HEAD: {name}")
        require(size > 0, f"Artifact is empty: {name}")


def evaluate_required_results(scopes: dict[str, bool], results: dict[str, str]) -> None:
    mapping = {
        "jvm": "jvm",
        "stress": "stress",
        "release": "release",
        "ui": "ui",
        "performance": "performance",
        "instrumented": "instrumented",
    }
    for scope, result_key in mapping.items():
        required = scopes.get(scope, False)
        result = results.get(result_key, "missing")
        if required:
            require(result == "success", f"Required component {result_key} did not succeed: {result}")
        else:
            require(result in {"success", "skipped"}, f"Optional component {result_key} ended unexpectedly: {result}")


def reject_case(label: str, action) -> None:
    try:
        action()
    except ValueError:
        return
    fail(f"Negative fail-closed self-test unexpectedly passed: {label}")


def self_test() -> None:
    all_scopes = {key: True for key in ("jvm", "stress", "release", "ui", "performance", "instrumented")}
    all_success = {key: "success" for key in all_scopes}
    evaluate_required_results(all_scopes, all_success)

    reject_case("mandatory test failure", lambda: evaluate_required_results(all_scopes, {**all_success, "jvm": "failure"}))
    reject_case("missing artifact", lambda: validate_artifacts("a" * 40, []))
    reject_case("artifact SHA mismatch", lambda: validate_artifacts("a" * 40, [{"name": "artifact-badsha", "size": 1}]))

    good_fc26 = dict(FC26_EXPECTED)
    good_fc26.update({"persistedPlayers": 60885, "loanPlayers": 1325, "auditHead": "a" * 40})
    validate_fc26_report(good_fc26, "a" * 40)
    reject_case("FC26 divergence", lambda: validate_fc26_report({**good_fc26, "datasetPlayers": 18404}, "a" * 40))
    reject_case("Room schema mismatch", lambda: validate_room_values(22, 21))

    good_perf = {
        "auditHead": "a" * 40,
        "profile": "normal",
        "timingMillis": {"seasonRolloverTotal": 1000},
        "queries": {"total": 1000, "activeLoanLookupPerPlayer": 0, "fullEntityPlayerUpdates": 0, "teamUpdateStatements": 100},
        "memory": {"heapPeakObservedBytes": 100_000_000},
        "sqlite": {"walPeakObservedBytes": 20_000_000, "walAfterTruncateCheckpointBytes": 0},
    }
    validate_performance_report(good_perf, "a" * 40)
    reject_case("performance budget", lambda: validate_performance_report({**good_perf, "queries": {**good_perf["queries"], "total": 25001}}, "a" * 40))
    reject_case("save recovery failure", lambda: evaluate_required_results(all_scopes, {**all_success, "jvm": "failure"}))
    reject_case("Release build failure", lambda: evaluate_required_results(all_scopes, {**all_success, "release": "failure"}))
    reject_case("Android instrumented failure", lambda: evaluate_required_results(all_scopes, {**all_success, "instrumented": "failure"}))
    print("Phase 10.9 negative fail-closed scenarios: PASS (9/9 rejected as expected)")


def audit_repository(root: Path) -> dict[str, Any]:
    workflows = sorted((root / ".github/workflows").glob("*.yml"))
    require(workflows, "No GitHub Actions workflows found")
    bad_pull_target = []
    for path in workflows:
        text = path.read_text(encoding="utf-8", errors="replace")
        if "pull_request_target" in text:
            bad_pull_target.append(path.name)
    require(not bad_pull_target, f"pull_request_target is forbidden for this repository CI: {bad_pull_target}")

    required_path = root / ".github/workflows/phase109-required-certification.yml"
    require(required_path.is_file(), "Permanent Phase 10.9 required certification workflow is missing")
    required = required_path.read_text(encoding="utf-8")
    require("name: Phase 10.9 Required Certification" in required, "Stable required workflow name is missing")
    require("contents: read" in required, "Required certification must use read-only repository contents")
    require("pull_request:" in required, "Required certification must run for pull requests")
    require("Required Certification Gate" in required, "Stable final certification job is missing")
    require("AUDIT_HEAD_SHA" in required and "git rev-parse HEAD" in required,
            "Required certification does not prove exact HEAD checkout")
    require("continue-on-error" not in required, "Required certification may not mask failures")

    room = validate_room_repository(root)
    return {
        "workflowCount": len(workflows),
        "pullRequestTarget": False,
        "requiredWorkflow": str(required_path.relative_to(root)),
        "room": room,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    audit = sub.add_parser("audit")
    audit.add_argument("--root", default=".")
    sub.add_parser("self-test")
    fc26 = sub.add_parser("validate-fc26")
    fc26.add_argument("path")
    fc26.add_argument("--head", default=None)
    perf = sub.add_parser("validate-performance")
    perf.add_argument("path")
    perf.add_argument("--head", default=None)
    room = sub.add_parser("validate-room")
    room.add_argument("--root", default=".")
    gate = sub.add_parser("gate")
    gate.add_argument("--provenance", default=None)

    args = parser.parse_args()
    try:
        if args.command == "audit":
            result = audit_repository(Path(args.root).resolve())
            print(json.dumps(result, indent=2, sort_keys=True))
        elif args.command == "self-test":
            self_test()
        elif args.command == "validate-fc26":
            validate_fc26_report(json.loads(Path(args.path).read_text()), args.head)
            print(f"FC26 invariants validated: {args.path}")
        elif args.command == "validate-performance":
            validate_performance_report(json.loads(Path(args.path).read_text()), args.head)
            print(f"Phase 10.8 performance budgets validated: {args.path}")
        elif args.command == "validate-room":
            print(json.dumps(validate_room_repository(Path(args.root).resolve()), indent=2, sort_keys=True))
        elif args.command == "gate":
            scopes = {key: os.environ.get(f"SCOPE_{key.upper()}", "false") == "true" for key in
                      ("jvm", "stress", "release", "ui", "performance", "instrumented")}
            results = {key: os.environ.get(f"RESULT_{key.upper()}", "missing") for key in scopes}
            evaluate_required_results(scopes, results)
            provenance = {
                "auditHead": os.environ.get("AUDIT_HEAD_SHA"),
                "baseSha": os.environ.get("BASE_SHA"),
                "scopes": scopes,
                "results": results,
                "status": "CERTIFIED",
            }
            if args.provenance:
                output = Path(args.provenance)
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_text(json.dumps(provenance, indent=2, sort_keys=True) + "\n")
            print(json.dumps(provenance, indent=2, sort_keys=True))
        return 0
    except (ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"PHASE 10.9 POLICY FAILURE: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
