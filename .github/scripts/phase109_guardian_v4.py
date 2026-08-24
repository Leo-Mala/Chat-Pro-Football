#!/usr/bin/env python3
"""Trusted Phase 10.9 guardian v4 hardening layer.

V4 keeps the v3 exact-head/base checks and adds four fail-closed guarantees:
1) Room versions are parsed only from executable Kotlin, never comments or string literals;
2) immutable Room history is bound to the exact Gradle schema/test-asset directory;
3) main-push invalidation waits for every earlier validator so stale success can never win last;
4) a PR base retarget back to main is invalidated immediately by the trusted workflow.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
from pathlib import Path
from typing import Any

import phase109_guardian_v3 as v3

SELF_PATH = ".github/scripts/phase109_guardian_v4.py"
BUILD_GRADLE_PATH = "app/build.gradle.kts"
GUARDIAN_WORKFLOW_FILE = "phase109-trusted-guardian.yml"
ACTIVE_RUN_STATES = {"queued", "in_progress", "waiting", "pending", "requested"}

v3.guardian.IMMUTABLE_TRUST_PATHS.add(SELF_PATH)


class GuardianV4Error(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardianV4Error(message)


def strip_kotlin_comments(text: str) -> str:
    """Remove Kotlin/KTS comments while preserving string/char literals and line structure."""
    out: list[str] = []
    i = 0
    state = "code"
    block_depth = 0
    while i < len(text):
        if state == "code":
            if text.startswith("//", i):
                out.extend("  ")
                i += 2
                state = "line_comment"
            elif text.startswith("/*", i):
                out.extend("  ")
                i += 2
                block_depth = 1
                state = "block_comment"
            elif text.startswith('"""', i):
                out.extend('"""')
                i += 3
                state = "triple_string"
            elif text[i] == '"':
                out.append(text[i])
                i += 1
                state = "string"
            elif text[i] == "'":
                out.append(text[i])
                i += 1
                state = "char"
            else:
                out.append(text[i])
                i += 1
        elif state == "line_comment":
            if text[i] == "\n":
                out.append("\n")
                state = "code"
            else:
                out.append(" ")
            i += 1
        elif state == "block_comment":
            if text.startswith("/*", i):
                out.extend("  ")
                i += 2
                block_depth += 1
            elif text.startswith("*/", i):
                out.extend("  ")
                i += 2
                block_depth -= 1
                if block_depth == 0:
                    state = "code"
            else:
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
        elif state == "triple_string":
            if text.startswith('"""', i):
                out.extend('"""')
                i += 3
                state = "code"
            else:
                out.append(text[i])
                i += 1
        elif state in {"string", "char"}:
            quote = '"' if state == "string" else "'"
            out.append(text[i])
            if text[i] == "\\" and i + 1 < len(text):
                out.append(text[i + 1])
                i += 2
            elif text[i] == quote:
                i += 1
                state = "code"
            else:
                i += 1
    require(state not in {"block_comment"}, "Unterminated Kotlin block comment")
    return "".join(out)


def executable_kotlin(text: str) -> str:
    """Blank comments and literal contents so regexes can only match executable source."""
    clean = strip_kotlin_comments(text)
    out: list[str] = []
    i = 0
    state = "code"
    while i < len(clean):
        if state == "code":
            if clean.startswith('"""', i):
                out.extend("   ")
                i += 3
                state = "triple_string"
            elif clean[i] == '"':
                out.append(" ")
                i += 1
                state = "string"
            elif clean[i] == "'":
                out.append(" ")
                i += 1
                state = "char"
            else:
                out.append(clean[i])
                i += 1
        elif state == "triple_string":
            if clean.startswith('"""', i):
                out.extend("   ")
                i += 3
                state = "code"
            else:
                out.append("\n" if clean[i] == "\n" else " ")
                i += 1
        elif state in {"string", "char"}:
            quote = '"' if state == "string" else "'"
            if clean[i] == "\\" and i + 1 < len(clean):
                out.extend("  ")
                i += 2
            elif clean[i] == quote:
                out.append(" ")
                i += 1
                state = "code"
            else:
                out.append("\n" if clean[i] == "\n" else " ")
                i += 1
    return "".join(out)


def parse_room_versions(text: str) -> tuple[int, int]:
    code = executable_kotlin(text)
    constants = re.findall(
        r"\bconst\s+val\s+APP_DATABASE_SCHEMA_VERSION(?:\s*:\s*[A-Za-z0-9_.<>?]+)?\s*=\s*(\d+)\b",
        code,
    )
    annotations = re.findall(r"@Database\s*\([\s\S]*?\bversion\s*=\s*(\d+)\b", code)
    require(len(constants) == 1, f"Expected one executable APP_DATABASE_SCHEMA_VERSION declaration, got {constants}")
    require(len(annotations) == 1, f"Expected one executable @Database version declaration, got {annotations}")
    current = int(constants[0])
    annotation = int(annotations[0])
    require(current == annotation, f"Room version mismatch: constant={current}, annotation={annotation}")
    return current, annotation


def validate_schema_binding_text(text: str) -> dict[str, str]:
    clean = strip_kotlin_comments(text)
    schema_mentions = clean.count("room.schemaLocation")
    schema_calls = re.findall(
        r"arg\s*\(\s*\"room\.schemaLocation\"\s*,\s*\"([^\"]+)\"\s*\)",
        clean,
    )
    require(schema_mentions == 1 and schema_calls == ["$projectDir/schemas"],
            f"Room schemaLocation must be uniquely bound to $projectDir/schemas, got {schema_calls}")

    assets_mentions = len(re.findall(r"\bassets\s*\.\s*srcDir\s*\(", clean))
    asset_calls = re.findall(
        r"getByName\s*\(\s*\"androidTest\"\s*\)\s*\.\s*assets\s*\.\s*srcDir\s*\(\s*\"([^\"]+)\"\s*\)",
        clean,
    )
    require(assets_mentions == 1 and asset_calls == ["$projectDir/schemas"],
            f"androidTest Room assets must be uniquely bound to $projectDir/schemas, got {asset_calls}")
    return {"schemaLocation": schema_calls[0], "androidTestAssets": asset_calls[0]}


def validate_schema_binding(repo: str, token: str, ref: str) -> dict[str, str]:
    return validate_schema_binding_text(v3.fetch_text(repo, token, ref, BUILD_GRADLE_PATH))


def validate_room_history(
    repo: str,
    token: str,
    base_sha: str,
    head: str,
    base_tree: dict[str, str],
    candidate_tree: dict[str, str],
) -> dict[str, Any]:
    base_version, _ = parse_room_versions(v3.fetch_text(repo, token, base_sha, v3.ROOM_DATABASE_PATH))
    candidate_version, _ = parse_room_versions(v3.fetch_text(repo, token, head, v3.ROOM_DATABASE_PATH))
    require(candidate_version >= base_version,
            f"Room schema version decreased: base={base_version}, candidate={candidate_version}")

    base_binding = validate_schema_binding(repo, token, base_sha)
    candidate_binding = validate_schema_binding(repo, token, head)
    require(candidate_binding == base_binding,
            f"Room schema/test-asset binding changed: base={base_binding}, candidate={candidate_binding}")

    historical = 0
    pattern = re.compile(rf"^{re.escape(v3.ROOM_SCHEMA_PREFIX)}(\d+)\.json$")
    for path, base_blob in base_tree.items():
        match = pattern.match(path)
        if not match:
            continue
        version = int(match.group(1))
        if version > base_version:
            continue
        require(path in candidate_tree, f"Historical Room schema fixture deleted: {path}")
        require(candidate_tree[path] == base_blob, f"Historical Room schema fixture modified: {path}")
        historical += 1
    require(historical > 0, "No historical Room schema fixtures found in trusted base")
    current_path = f"{v3.ROOM_SCHEMA_PREFIX}{candidate_version}.json"
    require(current_path in candidate_tree, f"Candidate current Room schema fixture missing: {current_path}")
    return {
        "baseVersion": base_version,
        "candidateVersion": candidate_version,
        "immutableHistoricalSchemas": historical,
        "schemaBinding": candidate_binding,
    }


def validator_blockers(repo: str, token: str, current_run_id: int) -> list[dict[str, Any]]:
    payload = v3.guardian.api_request(
        repo,
        token,
        "GET",
        f"/actions/workflows/{GUARDIAN_WORKFLOW_FILE}/runs?per_page=100",
    )
    require(isinstance(payload, dict), "Guardian workflow runs payload is missing")
    runs = payload.get("workflow_runs")
    require(isinstance(runs, list), "Guardian workflow_runs collection is missing")
    return [
        {"id": int(run.get("id", 0)), "status": str(run.get("status", "")), "event": str(run.get("event", ""))}
        for run in runs
        if int(run.get("id", 0)) > 0
        and int(run.get("id", 0)) < current_run_id
        and run.get("event") == "workflow_run"
        and run.get("status") in ACTIVE_RUN_STATES
    ]


def wait_prior_validators(repo: str, token: str, current_run_id: int, timeout_seconds: int) -> dict[str, Any]:
    require(current_run_id > 0, "Current Guardian run id is missing")
    require(30 <= timeout_seconds <= 840, "Validator wait timeout must be between 30 and 840 seconds")
    deadline = time.monotonic() + timeout_seconds
    observed: set[int] = set()
    while True:
        blockers = validator_blockers(repo, token, current_run_id)
        observed.update(item["id"] for item in blockers)
        if not blockers:
            return {"status": "PASS", "priorValidatorsObserved": sorted(observed)}
        if time.monotonic() >= deadline:
            raise GuardianV4Error(f"Timed out waiting for prior validators: {blockers}")
        time.sleep(5)


def validate_run(root: Path, repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    original = v3.validate_room_history
    v3.validate_room_history = validate_room_history
    try:
        result = v3.validate_run(root, repo, token, run_id, head)
    finally:
        v3.validate_room_history = original
    # Re-read live main and PR immediately before the workflow is allowed to enter its success step.
    live_main = v3.guardian.api_request(repo, token, "GET", "/git/ref/heads/main")
    live_main_sha = str(live_main.get("object", {}).get("sha", "")) if isinstance(live_main, dict) else ""
    audited = str(result.get("runAuditedBaseSha", ""))
    require(live_main_sha == audited, f"main advanced during trusted validation: {live_main_sha} != {audited}")
    return result


def self_test() -> dict[str, Any]:
    spoof = """
// const val APP_DATABASE_SCHEMA_VERSION = 22
const val APP_DATABASE_SCHEMA_VERSION = 21
// @Database(version = 22)
@Database(entities = [], version = 21)
val text = \"APP_DATABASE_SCHEMA_VERSION = 99 @Database(version = 99)\"
"""
    require(parse_room_versions(spoof) == (21, 21), "Comment/string Room spoof was not rejected by parsing")
    binding = """
ksp {
  // arg(\"room.schemaLocation\", \"$projectDir/evil\")
  arg(\"room.schemaLocation\", \"$projectDir/schemas\")
}
android {
  sourceSets {
    getByName(\"androidTest\").assets.srcDir(\"$projectDir/schemas\")
  }
}
"""
    validate_schema_binding_text(binding)
    try:
        validate_schema_binding_text(binding + '\nksp { arg("room.schemaLocation", "$projectDir/evil") }\n')
    except GuardianV4Error:
        pass
    else:
        raise GuardianV4Error("Alternate Room schema directory negative test did not fail")
    return {
        "status": "PASS",
        "executableRoomParsing": True,
        "commentSpoofRejected": True,
        "schemaDirectoryBound": True,
        "priorValidatorOrdering": "enforced-by-runtime-wait",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)

    validate = sub.add_parser("validate-run")
    validate.add_argument("--root", default=".")
    validate.add_argument("--repo", required=True)
    validate.add_argument("--token", required=True)
    validate.add_argument("--run-id", type=int, required=True)
    validate.add_argument("--head", required=True)

    wait = sub.add_parser("wait-prior-validators")
    wait.add_argument("--repo", required=True)
    wait.add_argument("--token", required=True)
    wait.add_argument("--current-run-id", type=int, required=True)
    wait.add_argument("--timeout-seconds", type=int, default=780)

    invalidate = sub.add_parser("invalidate-open-prs")
    invalidate.add_argument("--repo", required=True)
    invalidate.add_argument("--token", required=True)
    invalidate.add_argument("--main-sha", required=True)
    invalidate.add_argument("--target-url", default="")

    status = sub.add_parser("publish-status")
    status.add_argument("--repo", required=True)
    status.add_argument("--token", required=True)
    status.add_argument("--sha", required=True)
    status.add_argument("--state", choices=("error", "failure", "pending", "success"), required=True)
    status.add_argument("--description", required=True)
    status.add_argument("--target-url", default="")

    sub.add_parser("self-test")
    args = parser.parse_args()
    try:
        if args.command == "validate-run":
            result = validate_run(Path(args.root).resolve(), args.repo, args.token, args.run_id, args.head)
        elif args.command == "wait-prior-validators":
            result = wait_prior_validators(args.repo, args.token, args.current_run_id, args.timeout_seconds)
        elif args.command == "invalidate-open-prs":
            result = v3.invalidate_all(args.repo, args.token, args.main_sha, args.target_url)
        elif args.command == "publish-status":
            v3.guardian.publish_status(args.repo, args.token, args.sha, args.state, args.description, args.target_url)
            result = {"status": "PASS", "sha": args.sha, "state": args.state, "context": v3.guardian.GUARDIAN_CONTEXT}
        else:
            result = self_test()
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except Exception as exc:
        print(f"PHASE 10.9 TRUSTED GUARDIAN V4 FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
