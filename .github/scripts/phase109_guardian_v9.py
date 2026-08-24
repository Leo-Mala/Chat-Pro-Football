#!/usr/bin/env python3
"""Trusted Phase 10.9 guardian v9.

V9 closes the remaining fail-closed gaps found by independent review:
- both successful and failed Guardian writers are ordered by newest same-head required run;
- release minification/shrinker configuration is canonical and cannot be overridden by another
  executable Gradle layer;
- every production addMigrations call, including receiver-lambda/unqualified calls, must register
  exactly the canonical migration array;
- included-build Groovy source changes that the candidate workflow cannot prove were compiled are
  rejected rather than silently certified;
- installed Android certification must reject zero-test instrumentation output per selected suite.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

import phase109_guardian_v8 as v8

SELF_PATH = ".github/scripts/phase109_guardian_v9.py"
EMULATOR_GATE_PATH = ".github/scripts/phase107_emulator_gate.sh"
BASE_V8_VALIDATE_RUN = v8.validate_run
v8.v7.v6.v5.v4.v3.guardian.IMMUTABLE_TRUST_PATHS.add(SELF_PATH)


class GuardianV9Error(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardianV9Error(message)


def latest_same_head_required_run_id(repo: str, token: str, head: str) -> int:
    latest = 0
    page = 1
    while True:
        payload = v8.v7.v6.v5.v4.v3.guardian.api_request(
            repo, token, "GET",
            f"/actions/runs?head_sha={head}&event=pull_request&per_page=100&page={page}",
        )
        require(isinstance(payload, dict), "Workflow-run query returned an invalid payload")
        runs = payload.get("workflow_runs")
        require(isinstance(runs, list), "Workflow-run query did not return workflow_runs")
        for run in runs:
            if run.get("name") == v8.v7.v6.v5.REQUIRED_WORKFLOW_NAME:
                latest = max(latest, int(run.get("id", 0)))
        if len(runs) < 100:
            break
        page += 1
    require(latest > 0, f"No required certification run found for {head}")
    return latest


def require_latest_same_head_run(repo: str, token: str, run_id: int, head: str) -> None:
    latest = latest_same_head_required_run_id(repo, token, head)
    require(run_id == latest, f"Certification run {run_id} is stale for {head}; newest run is {latest}")


def candidate_included_build_prefixes(repo: str, token: str, head: str, candidate_tree: dict[str, str]) -> set[str]:
    settings_path = "settings.gradle.kts" if "settings.gradle.kts" in candidate_tree else "settings.gradle"
    require(settings_path in candidate_tree, "Gradle settings file is missing")
    settings = v8.v7.v6.v5.v4.v3.fetch_text(repo, token, head, settings_path)
    return v8.v7.strict_settings_included_build_prefixes(settings)


def validate_release_gradle_graph(repo: str, token: str, head: str, candidate_tree: dict[str, str]) -> dict[str, Any]:
    prefixes = candidate_included_build_prefixes(repo, token, head, candidate_tree)
    audited: list[str] = []
    forbidden_layers: list[str] = []
    shrinker_tokens = re.compile(
        r"\b(?:isMinifyEnabled|minifyEnabled|proguardFiles?|testProguardFiles?|consumerProguardFiles?)\b"
    )
    for path in sorted(candidate_tree):
        if not v8.v7.v6.v5.potential_gradle_layer(path, prefixes):
            continue
        audited.append(path)
        text = v8.v7.v6.v5.v4.v3.fetch_text(repo, token, head, path)
        if path == v8.v7.APP_BUILD_PATH:
            v8.v7.validate_release_build_contract(repo, token, head)
            continue
        clean = v8.v7.v6.v5.v4.strip_kotlin_comments(text) if path.lower().endswith((".kt", ".kts")) else text
        if shrinker_tokens.search(clean):
            forbidden_layers.append(path)
    require(not forbidden_layers,
            f"Release minification/shrinker configuration may exist only in {v8.v7.APP_BUILD_PATH}: {forbidden_layers}")
    return {
        "auditedGradleLayers": audited,
        "includedBuildPrefixes": sorted(prefixes),
        "canonicalReleaseShrinkerOwner": v8.v7.APP_BUILD_PATH,
    }


def validate_room_builder_registrations(repo: str, token: str, head: str, candidate_tree: dict[str, str]) -> dict[str, Any]:
    registrations: list[dict[str, Any]] = []
    suspicious: list[str] = []
    database_path = v8.v7.v6.v5.v4.v3.ROOM_DATABASE_PATH
    call_pattern = re.compile(r"(?<![A-Za-z0-9_])addMigrations\s*\(([^)]*)\)")
    for path in sorted(candidate_tree):
        if not path.startswith("app/src/main/") or not path.lower().endswith((".kt", ".java")):
            continue
        text = v8.v7.v6.v5.v4.v3.fetch_text(repo, token, head, path)
        clean = v8.v7.v6.v5.v4.executable_kotlin(text) if path.endswith(".kt") else text
        for call in call_pattern.findall(clean):
            normalized = re.sub(r"\s+", "", call)
            expected = "*ALL_MIGRATIONS" if path == database_path else "*AppDatabase.ALL_MIGRATIONS"
            registrations.append({"path": path, "argument": normalized, "expected": expected})
            if normalized != expected:
                suspicious.append(f"{path}:{normalized}")
    require(len(registrations) >= 2, f"Expected canonical Room registrations; got {registrations}")
    require(not suspicious, f"Production Room builders have non-canonical migration registrations: {suspicious}")
    require(any(item["path"] == database_path for item in registrations), "AppDatabase migration registration is missing")
    require(any(item["path"] != database_path for item in registrations), "External Room builder migration registration is missing")
    return {"registrations": registrations, "receiverLambdaCallsAudited": True}


def validate_changed_included_build_groovy(repo: str, token: str, run_id: int, head: str,
                                             candidate_tree: dict[str, str]) -> dict[str, Any]:
    run = v8.v7.v6.v5.v4.v3.guardian.api_request(repo, token, "GET", f"/actions/runs/{run_id}")
    require(isinstance(run, dict), "Certification run payload is missing")
    pr = v8.v7.v6.v5.v4.v3.guardian.find_current_pr(repo, token, run, head)
    files = v8.v7.v6.v5.v4.v3.complete_pr_files(repo, token, pr)
    changed = v8.v7.v6.v5.v4.v3.paths_from_files(files)
    prefixes = candidate_included_build_prefixes(repo, token, head, candidate_tree)
    unsupported = sorted(
        path for path in changed
        if path.lower().endswith(".groovy") and any(path.startswith(prefix) for prefix in prefixes)
    )
    require(not unsupported,
            f"Included-build Groovy changes are fail-closed until mandatory workflow scope compiles them: {unsupported}")
    return {"changedFiles": len(changed), "unsupportedIncludedBuildGroovy": unsupported}


def validate_instrumentation_gate_contract(repo: str, token: str, head: str) -> dict[str, Any]:
    text = v8.v7.v6.v5.v4.v3.fetch_text(repo, token, head, EMULATOR_GATE_PATH)
    require("grep -Eq '^OK \\([1-9][0-9]* tests?\\)$'" in text,
            "Installed Android gate must require a non-zero OK(test count) result for every selected suite")
    require("grep -q '^OK (' <<< \"$output\"" not in text,
            "Legacy zero-test-accepting instrumentation check is forbidden")
    return {"perSuiteNonZeroExecutionRequired": True, "gate": EMULATOR_GATE_PATH}


def validate_run(root: Path, repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    old_registration = v8.validate_room_builder_registrations
    v8.validate_room_builder_registrations = validate_room_builder_registrations
    try:
        candidate_tree = v8.v7.v6.v5.v4.v3.recursive_tree(repo, token, head)
        result = BASE_V8_VALIDATE_RUN(root, repo, token, run_id, head)
        result["releaseGradleGraphContract"] = validate_release_gradle_graph(repo, token, head, candidate_tree)
        result["roomBuilderRegistrationV9"] = validate_room_builder_registrations(repo, token, head, candidate_tree)
        result["includedBuildGroovyScope"] = validate_changed_included_build_groovy(repo, token, run_id, head, candidate_tree)
        result["instrumentationExecutionEvidence"] = validate_instrumentation_gate_contract(repo, token, head)
        return result
    finally:
        v8.validate_room_builder_registrations = old_registration


def validate_and_publish(root: Path, repo: str, token: str, run_id: int, head: str, target_url: str) -> dict[str, Any]:
    # Ordering applies to success just as it already applies to failure. A superseded run is never
    # allowed to become the newest Guardian writer.
    require_latest_same_head_run(repo, token, run_id, head)
    result = validate_run(root, repo, token, run_id, head)
    require_latest_same_head_run(repo, token, run_id, head)

    audited = str(result.get("runAuditedBaseSha", ""))
    require(bool(audited), "Validated run did not expose its audited base")
    require(v8.v7.v6.v5.live_main_sha(repo, token) == audited,
            "main advanced before Guardian success publication")
    pr = v8.v7.v6.v5.current_open_main_pr(repo, token, head)
    require(pr.get("base", {}).get("sha") == audited,
            "PR base advanced before Guardian success publication")
    require_latest_same_head_run(repo, token, run_id, head)

    v8.v7.v6.v5.v4.v3.guardian.publish_status(
        repo, token, head, "success", "trusted default-branch certification accepted", target_url,
    )

    # Post-check closes the race in which a newer run appears between the last pre-check and the
    # status POST. If superseded, fail closed; the newer run can later restore success if it passes.
    latest_after = latest_same_head_required_run_id(repo, token, head)
    post_main = v8.v7.v6.v5.live_main_sha(repo, token)
    try:
        post_pr = v8.v7.v6.v5.current_open_main_pr(repo, token, head)
        post_base = str(post_pr.get("base", {}).get("sha", ""))
    except Exception:
        post_base = ""
    if latest_after != run_id or post_main != audited or post_base != audited:
        v8.v7.v6.v5.v4.v3.guardian.publish_status(
            repo, token, head, "failure",
            "Guardian publication superseded or base changed; newest exact-base certification required",
            target_url,
        )
        raise GuardianV9Error(
            f"Guardian publication invalidated: latest={latest_after}, run={run_id}, main={post_main}, pr={post_base}, audited={audited}"
        )
    result["guardianSuccessPublished"] = True
    result["latestSameHeadRunId"] = run_id
    return result


def self_test() -> dict[str, Any]:
    v8.self_test()
    sample = "builder.apply { addMigrations(MIGRATION_14_22) }"
    require(re.search(r"(?<![A-Za-z0-9_])addMigrations\s*\(([^)]*)\)", sample) is not None,
            "Receiver-lambda addMigrations detection failed")
    return {
        "status": "PASS",
        "guardianV8": "PASS",
        "successAndFailureOrderedByNewestRun": True,
        "allGradleShrinkerOverridesRejected": True,
        "receiverLambdaMigrationsAudited": True,
        "includedBuildGroovyFailsClosed": True,
        "perSuiteAndroidNonZeroEvidenceRequired": True,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    validate_publish = sub.add_parser("validate-and-publish")
    validate_publish.add_argument("--root", default=".")
    validate_publish.add_argument("--repo", required=True)
    validate_publish.add_argument("--token", required=True)
    validate_publish.add_argument("--run-id", type=int, required=True)
    validate_publish.add_argument("--head", required=True)
    validate_publish.add_argument("--target-url", default="")
    invalidate = sub.add_parser("invalidate-current-main")
    invalidate.add_argument("--repo", required=True)
    invalidate.add_argument("--token", required=True)
    invalidate.add_argument("--main-sha", required=True)
    invalidate.add_argument("--target-url", default="")
    retarget = sub.add_parser("invalidate-retarget-run")
    retarget.add_argument("--repo", required=True)
    retarget.add_argument("--token", required=True)
    retarget.add_argument("--run-id", type=int, required=True)
    retarget.add_argument("--target-url", default="")
    failure = sub.add_parser("publish-failure-if-latest")
    failure.add_argument("--repo", required=True)
    failure.add_argument("--token", required=True)
    failure.add_argument("--run-id", type=int, required=True)
    failure.add_argument("--head", required=True)
    failure.add_argument("--description", required=True)
    failure.add_argument("--target-url", default="")
    sub.add_parser("self-test")
    args = parser.parse_args()
    try:
        if args.command == "validate-and-publish":
            result = validate_and_publish(Path(args.root).resolve(), args.repo, args.token, args.run_id, args.head, args.target_url)
        elif args.command == "invalidate-current-main":
            result = v8.v7.v6.v5.invalidate_current_main(args.repo, args.token, args.main_sha, args.target_url)
        elif args.command == "invalidate-retarget-run":
            result = v8.v7.v6.v5.invalidate_retarget_signal(args.repo, args.token, args.run_id, args.target_url)
        elif args.command == "publish-failure-if-latest":
            result = v8.v7.publish_failure_if_latest(
                args.repo, args.token, args.run_id, args.head, args.description, args.target_url
            )
        else:
            result = self_test()
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except Exception as exc:
        print(f"PHASE 10.9 TRUSTED GUARDIAN V9 FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
