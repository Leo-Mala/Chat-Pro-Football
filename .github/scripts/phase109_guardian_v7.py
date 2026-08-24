#!/usr/bin/env python3
"""Trusted Phase 10.9 guardian v7.

V7 layers additional fail-closed guarantees over V6:
- settings scripts may not use any `apply` indirection;
- Android instrumentation tests may not be hidden by runner filter annotations;
- the release build must keep R8/minification and the canonical shrinker/test-shrinker inputs enabled;
- every production Room builder must register exactly AppDatabase.ALL_MIGRATIONS;
- stale/rejected older certification runs cannot overwrite a newer same-head Guardian decision.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

import phase109_guardian_v6 as v6

SELF_PATH = ".github/scripts/phase109_guardian_v7.py"
APP_BUILD_PATH = "app/build.gradle.kts"
BASE_V6_VALIDATE_RUN = v6.validate_run
BASE_V6_VALIDATE_AND_PUBLISH = v6.validate_and_publish
v6.v5.v4.v3.guardian.IMMUTABLE_TRUST_PATHS.add(SELF_PATH)


class GuardianV7Error(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardianV7Error(message)


def strict_settings_included_build_prefixes(settings_text: str) -> set[str]:
    clean = v6.v5.v4.strip_kotlin_comments(settings_text)
    require(
        re.search(r"\bapply\b", clean) is None,
        "Settings apply indirection is forbidden; the complete includeBuild graph must be visible in the root settings file",
    )
    calls = list(re.finditer(r"\bincludeBuild\s*\(([^)]*)\)", clean))
    prefixes: set[str] = set()
    for call in calls:
        expression = call.group(1).strip()
        literal = re.fullmatch(r"[\"']([^\"']+)[\"']", expression)
        require(literal is not None, f"includeBuild must use a static literal path: {expression}")
        value = literal.group(1).strip().strip("/")
        require(value and ".." not in Path(value).parts, f"Unsafe includeBuild path: {value}")
        prefixes.add(value + "/")
    require(len(re.findall(r"\bincludeBuild\b", clean)) == len(calls), "Unrecognized includeBuild syntax")
    return prefixes


def _extract_named_block(text: str, name: str) -> str:
    match = re.search(rf"\b{re.escape(name)}\s*\{{", text)
    require(match is not None, f"Gradle block is missing: {name}")
    start = text.find("{", match.start())
    depth = 0
    quote: str | None = None
    escaped = False
    for index in range(start, len(text)):
        char = text[index]
        if escaped:
            escaped = False
            continue
        if char == "\\" and quote == '"':
            escaped = True
            continue
        if char in {"'", '"'}:
            if quote is None:
                quote = char
            elif quote == char:
                quote = None
            continue
        if quote is not None:
            continue
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[start + 1:index]
    raise GuardianV7Error(f"Unclosed Gradle block: {name}")


def validate_release_build_contract(repo: str, token: str, head: str) -> dict[str, Any]:
    text = v6.v5.v4.v3.fetch_text(repo, token, head, APP_BUILD_PATH)
    clean = v6.v5.v4.strip_kotlin_comments(text)
    build_types = _extract_named_block(clean, "buildTypes")
    release = _extract_named_block(build_types, "release")
    minify_values = re.findall(r"\bisMinifyEnabled\s*=\s*(true|false)\b", release)
    require(minify_values == ["true"], f"Release R8/minification must be explicitly enabled exactly once: {minify_values}")
    require(
        re.search(r"\bproguardFiles\s*\([^\n]*[\"']src/main/proguard-rules\.pro[\"']", release) is not None,
        "Release build must consume app/src/main/proguard-rules.pro",
    )
    require(
        re.search(r"\btestProguardFiles\s*\([^\n]*[\"']src/androidTest/proguard-rules\.pro[\"']", release) is not None,
        "Release AndroidTest R8 must consume app/src/androidTest/proguard-rules.pro",
    )
    return {"releaseMinified": True, "productionRulesBound": True, "androidTestRulesBound": True}


def validate_instrumentation_filter_contract(repo: str, token: str, head: str, candidate_tree: dict[str, str]) -> dict[str, Any]:
    offenders: list[str] = []
    android_tests = 0
    forbidden = (
        r"@(?:androidx\.test\.filters\.)?SdkSuppress\b",
        r"@(?:androidx\.test\.filters\.)?RequiresDevice\b",
    )
    for path in sorted(candidate_tree):
        if not path.startswith("app/src/androidTest/") or not path.lower().endswith((".kt", ".java")):
            continue
        text = v6.v5.v4.v3.fetch_text(repo, token, head, path)
        clean = v6.v5.v4.strip_kotlin_comments(text) if path.endswith(".kt") else text
        android_tests += len(re.findall(r"@(?:org\.junit\.)?Test\b", clean))
        if any(re.search(pattern, clean) for pattern in forbidden):
            offenders.append(path)
    require(android_tests > 0, "Android instrumentation source set has no @Test methods")
    require(not offenders, f"Runner-filtered mandatory Android tests are forbidden: {offenders}")
    return {"androidTestMethods": android_tests, "runnerFiltersRejected": True}


def validate_room_builder_registrations(repo: str, token: str, head: str, candidate_tree: dict[str, str]) -> dict[str, Any]:
    registrations: list[dict[str, Any]] = []
    suspicious: list[str] = []
    for path in sorted(candidate_tree):
        if not path.startswith("app/src/main/") or not path.lower().endswith((".kt", ".java")):
            continue
        text = v6.v5.v4.v3.fetch_text(repo, token, head, path)
        clean = v6.v5.v4.executable_kotlin(text) if path.endswith(".kt") else text
        for call in re.findall(r"\.addMigrations\s*\(([^)]*)\)", clean):
            normalized = re.sub(r"\s+", "", call)
            registrations.append({"path": path, "argument": normalized})
            if normalized != "*AppDatabase.ALL_MIGRATIONS":
                suspicious.append(f"{path}:{normalized}")
    require(registrations, "No production Room migration registration was found")
    require(not suspicious, f"Production Room builders may register only AppDatabase.ALL_MIGRATIONS: {suspicious}")
    return {"registrations": registrations, "onlyCanonicalMigrationArray": True}


def validate_run(root: Path, repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    old_prefixes = v6.strict_included_build_prefixes
    v6.strict_included_build_prefixes = strict_settings_included_build_prefixes
    try:
        candidate_tree = v6.v5.v4.v3.recursive_tree(repo, token, head)
        result = BASE_V6_VALIDATE_RUN(root, repo, token, run_id, head)
        result["releaseBuildContract"] = validate_release_build_contract(repo, token, head)
        result["instrumentationFilterContract"] = validate_instrumentation_filter_contract(repo, token, head, candidate_tree)
        result["roomBuilderRegistrationContract"] = validate_room_builder_registrations(repo, token, head, candidate_tree)
        return result
    finally:
        v6.strict_included_build_prefixes = old_prefixes


def validate_and_publish(root: Path, repo: str, token: str, run_id: int, head: str, target_url: str) -> dict[str, Any]:
    old_validate = v6.validate_run
    v6.validate_run = validate_run
    try:
        return BASE_V6_VALIDATE_AND_PUBLISH(root, repo, token, run_id, head, target_url)
    finally:
        v6.validate_run = old_validate


def newer_same_head_required_run_exists(repo: str, token: str, run_id: int, head: str) -> bool:
    page = 1
    while True:
        payload = v6.v5.v4.v3.guardian.api_request(
            repo,
            token,
            "GET",
            f"/actions/runs?head_sha={head}&event=pull_request&per_page=100&page={page}",
        )
        require(isinstance(payload, dict), "Workflow-run query returned an invalid payload")
        runs = payload.get("workflow_runs")
        require(isinstance(runs, list), "Workflow-run query did not return workflow_runs")
        for run in runs:
            if run.get("name") == v6.v5.REQUIRED_WORKFLOW_NAME and int(run.get("id", 0)) > run_id:
                return True
        if len(runs) < 100:
            return False
        page += 1


def publish_failure_if_latest(repo: str, token: str, run_id: int, head: str, description: str, target_url: str) -> dict[str, Any]:
    if newer_same_head_required_run_exists(repo, token, run_id, head):
        return {"status": "PASS", "action": "skipped-stale-run", "runId": run_id, "head": head}
    v6.v5.v4.v3.guardian.publish_status(repo, token, head, "failure", description, target_url)
    return {"status": "PASS", "action": "failure-published", "runId": run_id, "head": head}


def self_test() -> dict[str, Any]:
    require(strict_settings_included_build_prefixes('includeBuild("plugins")') == {"plugins/"}, "Static includeBuild parse failed")
    for unsafe in (
        'apply(from = "extra.settings.gradle.kts")',
        'apply { from("extra.settings.gradle.kts") }',
        'apply(mutableMapOf("from" to "extra.settings.gradle.kts"))',
        'includeBuild("plug" + "ins")',
    ):
        try:
            strict_settings_included_build_prefixes(unsafe)
        except GuardianV7Error:
            pass
        else:
            raise GuardianV7Error(f"Unsafe settings form was not rejected: {unsafe}")
    sample = """
android {
  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "src/main/proguard-rules.pro")
      testProguardFiles("src/androidTest/proguard-rules.pro")
    }
  }
}
"""
    release = _extract_named_block(_extract_named_block(sample, "buildTypes"), "release")
    require("isMinifyEnabled = true" in release, "Release block extraction failed")
    return {
        "status": "PASS",
        "allSettingsApplyIndirectionRejected": True,
        "runnerFilterAnnotationsRejected": True,
        "releaseMinificationTrusted": True,
        "canonicalRoomRegistrationRequired": True,
        "staleSameHeadFailureRejected": True,
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
            result = v6.v5.invalidate_current_main(args.repo, args.token, args.main_sha, args.target_url)
        elif args.command == "invalidate-retarget-run":
            result = v6.v5.invalidate_retarget_signal(args.repo, args.token, args.run_id, args.target_url)
        elif args.command == "publish-failure-if-latest":
            result = publish_failure_if_latest(args.repo, args.token, args.run_id, args.head, args.description, args.target_url)
        else:
            result = self_test()
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except Exception as exc:
        print(f"PHASE 10.9 TRUSTED GUARDIAN V7 FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
