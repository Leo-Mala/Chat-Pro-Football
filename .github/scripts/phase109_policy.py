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
import subprocess
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

# These markers are not invented by the Phase 10.9 candidate. Each one must exist in the
# named workflow at the trusted base commit *and* in the candidate permanent workflow. This
# prevents a PR from silently deleting a required test invocation while keeping a green job
# name. The trusted definitions are read with `git show <base-sha>:<path>` outside HEAD.
TRUSTED_WORKFLOW_MARKERS: dict[str, tuple[str, ...]] = {
    ".github/workflows/core-regression.yml": (
        "./gradlew testDebugUnitTest -PexcludeStressTests=true --stacktrace",
    ),
    ".github/workflows/migration-safety.yml": (
        "com.example.SaveSlotIsolationTest",
        "com.example.migrations.MigrationSafetyTest",
        "com.example.migrations.MigrationCompatibilityTest",
    ),
    ".github/workflows/release-variant-smoke.yml": (
        "com.example.StartupSmokeTest",
    ),
    ".github/workflows/long-horizon-stress.yml": (
        "com.example.TwentySeasonStressTest",
        "com.example.OneHundredSeasonMatchByMatchStressTest",
    ),
    ".github/workflows/phase105-ui-golden-regression.yml": (
        "com.example.MainMenuScreenshotTest",
        "com.example.SavesScreenshotTest",
        "com.example.Phase105CriticalUiGoldenTest",
        "com.example.Phase105AccessibilityAndResilienceTest",
    ),
    ".github/workflows/phase107-android-instrumented.yml": (
        ".github/scripts/phase107_emulator_gate.sh",
        "assembleDebug assembleDebugAndroidTest",
        "assembleRelease assembleReleaseAndroidTest bundleRelease",
    ),
    ".github/workflows/phase108-full-scale-rollover-performance.yml": (
        "com.example.data.Phase108FullScaleSeasonRolloverPerformanceStressTest",
        "--no-build-cache",
        "--rerun-tasks",
    ),
    ".github/workflows/android.yml": (
        "tools/fc26/validate_fc26.py",
        "com.example.data.Fc26FullSeedIntegrationTest",
        "com.example.data.GlobalMainAuditPerformanceStressTest",
    ),
}

CANDIDATE_ONLY_REQUIRED_MARKERS = (
    "com.example.GamePreferencesRestoreSafetyTest",
    "com.example.BackupRestoreRoundTripTest",
    "com.example.Phase106*",
    "git diff --exit-code -- app/schemas",
    "phase109_policy.py validate-fc26",
    "phase109_policy.py validate-room",
    "phase109_policy.py validate-performance",
)


def fail(message: str) -> None:
    raise ValueError(message)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def git_text(root: Path, *args: str) -> str:
    completed = subprocess.run(
        ["git", *args],
        cwd=root,
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    require(completed.returncode == 0, f"git {' '.join(args)} failed: {completed.stderr.strip()}")
    return completed.stdout.strip()


def resolve_trusted_base_sha(root: Path) -> str:
    explicit = os.environ.get("PHASE109_TRUSTED_BASE_SHA", "").strip()
    if explicit:
        return git_text(root, "rev-parse", "--verify", f"{explicit}^{{commit}}")

    base_ref = os.environ.get("GITHUB_BASE_REF", "").strip()
    if base_ref:
        # checkout@v6 with fetch-depth: 0 materializes the pull-request base. Prefer the
        # remote-tracking ref so the source is outside the candidate HEAD.
        for candidate in (f"refs/remotes/origin/{base_ref}", f"origin/{base_ref}", base_ref):
            completed = subprocess.run(
                ["git", "rev-parse", "--verify", f"{candidate}^{{commit}}"],
                cwd=root,
                check=False,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            if completed.returncode == 0:
                return completed.stdout.strip()
        fail(f"Could not resolve trusted pull-request base ref: {base_ref}")

    # workflow_dispatch has no GITHUB_BASE_REF. The parent is the conservative trusted
    # reference for a manually dispatched candidate commit.
    return git_text(root, "rev-parse", "--verify", "HEAD^{commit}^")


def trusted_file_text(root: Path, trusted_sha: str, path: str) -> str:
    return git_text(root, "show", f"{trusted_sha}:{path}")


def validate_required_marker_contract(required: str, trusted_sources: dict[str, str]) -> int:
    marker_count = 0
    for path, markers in TRUSTED_WORKFLOW_MARKERS.items():
        trusted = trusted_sources.get(path)
        require(trusted is not None, f"Trusted workflow source missing: {path}")
        for marker in markers:
            require(marker in trusted, f"Trusted base no longer proves required marker in {path}: {marker}")
            require(marker in required, f"Candidate required workflow removed trusted invocation: {marker}")
            marker_count += 1
    for marker in CANDIDATE_ONLY_REQUIRED_MARKERS:
        require(marker in required, f"Candidate required workflow removed Phase 10.9 safeguard: {marker}")
        marker_count += 1
    return marker_count


def validate_required_workflow_against_trusted_base(root: Path, required: str) -> dict[str, Any]:
    trusted_sha = resolve_trusted_base_sha(root)
    candidate_sha = git_text(root, "rev-parse", "HEAD^{commit}")
    require(trusted_sha != candidate_sha, "Trusted CI definition must be outside the candidate HEAD")
    trusted_sources = {
        path: trusted_file_text(root, trusted_sha, path)
        for path in TRUSTED_WORKFLOW_MARKERS
    }
    marker_count = validate_required_marker_contract(required, trusted_sources)
    return {
        "trustedBaseSha": trusted_sha,
        "candidateSha": candidate_sha,
        "trustedWorkflowCount": len(trusted_sources),
        "requiredMarkerCount": marker_count,
    }


def parse_database_versions(source: str) -> tuple[int, int]:
    const_match = re.search(r"APP_DATABASE_SCHEMA_VERSION\s*=\s*(\d+)", source)
    annotation_match = re.search(r"@Database\([\s\S]*?\bversion\s*=\s*(\d+)", source)
    require(const_match is not None, "APP_DATABASE_SCHEMA_VERSION not found")
    require(annotation_match is not None, "Room @Database version not found")
    return int(const_match.group(1)), int(annotation_match.group(1))


def parse_minimum_migratable_version(source: str) -> int:
    match = re.search(r"MINIMUM_AUTOMATICALLY_MIGRATABLE_VERSION\s*=\s*(\d+)", source)
    require(match is not None, "MINIMUM_AUTOMATICALLY_MIGRATABLE_VERSION not found")
    return int(match.group(1))


def validate_room_values(current: int, annotation: int) -> None:
    require(current == annotation, f"Room version mismatch: constant={current}, annotation={annotation}")
    require(current >= 2, f"Room schema version is unexpectedly low: {current}")


def validate_room_repository(root: Path) -> dict[str, Any]:
    database_path = root / "app/src/main/java/com/example/data/database.kt"
    source = database_path.read_text(encoding="utf-8")
    current, annotation = parse_database_versions(source)
    validate_room_values(current, annotation)
    minimum = parse_minimum_migratable_version(source)
    require(1 <= minimum < current, f"Invalid minimum migratable Room version: {minimum}")
    require("ALL_MIGRATIONS" in source, "AppDatabase.ALL_MIGRATIONS is missing")

    schema_dir = root / "app/schemas/com.example.data.AppDatabase"
    exported_versions = sorted(
        int(path.stem) for path in schema_dir.glob("*.json") if path.stem.isdigit()
    )
    require(exported_versions, "No exported Room schemas found")
    require(current in exported_versions, f"Missing current exported Room schema V{current}")
    require(current - 1 in exported_versions, f"Missing previous exported Room schema V{current - 1}")
    first_exported = exported_versions[0]
    require(first_exported >= minimum, "Exported Room schema history predates declared migration minimum unexpectedly")
    for version in range(first_exported, current + 1):
        require(version in exported_versions, f"Gap in exported Room schema history at V{version}")

    migration_symbols: list[str] = []
    for previous in range(minimum, current):
        target = previous + 1
        migration_file = root / f"app/src/main/java/com/example/data/migrations/Migration_{previous}_{target}.kt"
        migration_symbol = f"MIGRATION_{previous}_{target}"
        require(migration_file.is_file(), f"Missing migration edge: {migration_file}")
        require(migration_symbol in source, f"{migration_symbol} is not registered by AppDatabase")
        migration_symbols.append(migration_symbol)

    destructive: list[str] = []
    production_root = root / "app/src/main"
    for suffix in ("*.kt", "*.java"):
        for path in production_root.rglob(suffix):
            text = path.read_text(encoding="utf-8", errors="replace")
            if "fallbackToDestructiveMigration" in text:
                destructive.append(str(path.relative_to(root)))
    require(not destructive, f"Destructive Room fallback is forbidden: {sorted(set(destructive))}")

    return {
        "currentSchemaVersion": current,
        "minimumMigratableSchemaVersion": minimum,
        "firstExportedSchemaVersion": first_exported,
        "previousSchemaVersion": current - 1,
        "migrationChain": migration_symbols,
        "exportedSchemas": exported_versions,
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

    trusted = {
        path: "\n".join(markers)
        for path, markers in TRUSTED_WORKFLOW_MARKERS.items()
    }
    candidate = "\n".join(
        marker
        for markers in TRUSTED_WORKFLOW_MARKERS.values()
        for marker in markers
    ) + "\n" + "\n".join(CANDIDATE_ONLY_REQUIRED_MARKERS)
    validate_required_marker_contract(candidate, trusted)
    core_marker = TRUSTED_WORKFLOW_MARKERS[".github/workflows/core-regression.yml"][0]
    reject_case(
        "required workflow command removal",
        lambda: validate_required_marker_contract(candidate.replace(core_marker, "", 1), trusted),
    )
    print("Phase 10.9 negative fail-closed scenarios: PASS (10/10 rejected as expected)")


def audit_repository(root: Path) -> dict[str, Any]:
    workflow_root = root / ".github/workflows"
    workflows = sorted(set(workflow_root.glob("*.yml")) | set(workflow_root.glob("*.yaml")))
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
    require("git diff --no-renames --name-only" in required,
            "Scope classification must preserve both sides of production-code renames")
    require("r'^app/src/'" in required,
            "Required certification must conservatively include Android variant source sets")
    require("git diff --exit-code -- app/schemas" in required,
            "Required certification must reject generated Room schema drift")
    require("git ls-remote origin" in required,
            "Final certification must re-check the audited base branch before success")

    trusted_contract = validate_required_workflow_against_trusted_base(root, required)
    room = validate_room_repository(root)
    return {
        "workflowCount": len(workflows),
        "pullRequestTarget": False,
        "requiredWorkflow": str(required_path.relative_to(root)),
        "trustedWorkflowContract": trusted_contract,
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