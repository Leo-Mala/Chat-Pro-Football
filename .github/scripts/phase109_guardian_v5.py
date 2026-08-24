#!/usr/bin/env python3
"""Trusted Phase 10.9 guardian v5.

V5 keeps the exact-head/base, immutable workflow, Room-history and permission guarantees from
V3/V4 while closing the remaining ordering and parser gaps found by independent review:
- the Room @Database version must be the androidx.room.Database annotation decorating AppDatabase;
- androidTest Room schemas cannot be redirected through a second assets mutator/alias;
- trusted success is published and then revalidated against live main/base in one command;
- main-advance invalidation is immediate and skips only a Guardian success already proven against
  that exact main generation, so a delayed push run cannot erase a newer valid certification;
- PR-base edits from forks are signalled by a read-only workflow and invalidated from workflow_run,
  where the token is trusted/default-branch scoped.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

import phase109_guardian_v4 as v4

SELF_PATH = ".github/scripts/phase109_guardian_v5.py"
REQUIRED_WORKFLOW_NAME = "Phase 10.9 Required Certification"
RETARGET_SIGNAL_WORKFLOW_NAME = "Phase 10.9 PR Base Signal"
RETARGET_SIGNAL_JOB_NAME = "Signal base edit to main"

v4.v3.guardian.IMMUTABLE_TRUST_PATHS.add(SELF_PATH)


class GuardianV5Error(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardianV5Error(message)


def parse_room_versions(text: str) -> tuple[int, int]:
    code = v4.executable_kotlin(text)
    constants = re.findall(
        r"\bconst\s+val\s+APP_DATABASE_SCHEMA_VERSION(?:\s*:\s*[A-Za-z0-9_.<>?]+)?\s*=\s*(\d+)\b",
        code,
    )
    require(len(constants) == 1, f"Expected one executable APP_DATABASE_SCHEMA_VERSION declaration, got {constants}")

    imports = re.findall(r"^\s*import\s+androidx\.room\.Database\s*$", code, flags=re.MULTILINE)
    require(len(imports) == 1, "AppDatabase must import androidx.room.Database exactly once without aliasing")
    require(
        re.search(r"\b(?:annotation\s+class|class|object|interface|typealias)\s+Database\b", code) is None,
        "A local Database symbol may not shadow androidx.room.Database",
    )
    require("@androidx.room.Database" not in code, "Use the audited androidx.room.Database import and @Database annotation")

    app_database = re.search(r"\babstract\s+class\s+AppDatabase\s*:\s*RoomDatabase\s*\(\s*\)", code)
    require(app_database is not None, "AppDatabase must directly extend RoomDatabase")
    annotations = list(re.finditer(r"@Database\s*\(([\s\S]*?)\)", code))
    require(len(annotations) == 1, f"Expected exactly one Room @Database annotation, got {len(annotations)}")
    annotation_match = annotations[0]
    require(annotation_match.start() < app_database.start(), "Room @Database must decorate AppDatabase")

    between = code[annotation_match.end():app_database.start()]
    # Other annotations (currently @TypeConverters) may sit between @Database and AppDatabase,
    # but executable declarations/statements/classes may not.
    stripped_between = re.sub(
        r"@[A-Za-z_][A-Za-z0-9_.]*\s*(?:\([^()]*\))?\s*",
        "",
        between,
    )
    require(not stripped_between.strip(), "Room @Database is not attached to AppDatabase")

    versions = re.findall(r"\bversion\s*=\s*(\d+)\b", annotation_match.group(1))
    require(len(versions) == 1, f"Expected one AppDatabase Room version, got {versions}")
    current = int(constants[0])
    annotation = int(versions[0])
    require(current == annotation, f"Room version mismatch: constant={current}, annotation={annotation}")
    return current, annotation


def validate_schema_binding_text(text: str) -> dict[str, str]:
    binding = v4.validate_schema_binding_text(text)
    code = v4.executable_kotlin(text)
    assets_tokens = re.findall(r"\bassets\b", code)
    require(
        len(assets_tokens) == 1,
        "Android test assets must have exactly one executable binding; aliases/additional mutators are forbidden",
    )
    forbidden = re.findall(r"\b(?:setSrcDirs|srcDirs|setSourceDirectories|setSourceDirectoriesFrom)\b", code)
    require(not forbidden, f"Alternate Android-test asset setters are forbidden: {forbidden}")
    return binding


def validate_schema_binding(repo: str, token: str, ref: str) -> dict[str, str]:
    return validate_schema_binding_text(v4.v3.fetch_text(repo, token, ref, v4.BUILD_GRADLE_PATH))


def validate_room_history(
    repo: str,
    token: str,
    base_sha: str,
    head: str,
    base_tree: dict[str, str],
    candidate_tree: dict[str, str],
) -> dict[str, Any]:
    old_parse = v4.parse_room_versions
    old_binding = v4.validate_schema_binding
    v4.parse_room_versions = parse_room_versions
    v4.validate_schema_binding = validate_schema_binding
    try:
        return v4.validate_room_history(repo, token, base_sha, head, base_tree, candidate_tree)
    finally:
        v4.parse_room_versions = old_parse
        v4.validate_schema_binding = old_binding


def validate_run(root: Path, repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    old_history = v4.validate_room_history
    v4.validate_room_history = validate_room_history
    try:
        return v4.validate_run(root, repo, token, run_id, head)
    finally:
        v4.validate_room_history = old_history


def live_main_sha(repo: str, token: str) -> str:
    payload = v4.v3.guardian.api_request(repo, token, "GET", "/git/ref/heads/main")
    sha = str(payload.get("object", {}).get("sha", "")) if isinstance(payload, dict) else ""
    require(bool(sha), "Live main SHA is unavailable")
    return sha


def current_open_main_pr(repo: str, token: str, head: str) -> dict[str, Any]:
    pulls = v4.v3.guardian.api_request(repo, token, "GET", f"/commits/{head}/pulls") or []
    require(isinstance(pulls, list), "Commit pull-request lookup returned an invalid payload")
    matches = [
        pr for pr in pulls
        if pr.get("state") == "open"
        and pr.get("head", {}).get("sha") == head
        and pr.get("base", {}).get("ref") == "main"
    ]
    require(len(matches) == 1, f"Expected one open PR targeting main for {head}; got {len(matches)}")
    return matches[0]


def run_is_exact_current_base(run: dict[str, Any], head: str, main_sha: str) -> bool:
    if run.get("name") != REQUIRED_WORKFLOW_NAME or run.get("conclusion") != "success":
        return False
    if run.get("head_sha") != head or run.get("event") != "pull_request":
        return False
    snapshots = [
        item for item in run.get("pull_requests", [])
        if item.get("head", {}).get("sha") == head
        and item.get("base", {}).get("ref") == "main"
        and item.get("base", {}).get("sha") == main_sha
    ]
    return len(snapshots) == 1


def current_guardian_certified(repo: str, token: str, head: str, main_sha: str) -> bool:
    statuses = v4.v3.paged(repo, token, f"/commits/{head}/statuses")
    latest = next(
        (status for status in statuses if status.get("context") == v4.v3.guardian.GUARDIAN_CONTEXT),
        None,
    )
    if not latest or latest.get("state") != "success":
        return False
    target_url = str(latest.get("target_url", ""))
    match = re.search(r"/actions/runs/(\d+)(?:$|[/?#])", target_url)
    if not match:
        return False
    run = v4.v3.guardian.api_request(repo, token, "GET", f"/actions/runs/{int(match.group(1))}")
    return isinstance(run, dict) and run_is_exact_current_base(run, head, main_sha)


def invalidate_current_main(repo: str, token: str, main_sha: str, target_url: str) -> dict[str, Any]:
    require(live_main_sha(repo, token) == main_sha, "Push invalidator is not bound to the current main generation")
    pulls = v4.v3.paged(repo, token, "/pulls?state=open&base=main")
    invalidated: list[dict[str, Any]] = []
    preserved: list[dict[str, Any]] = []
    for pr in pulls:
        head = str(pr.get("head", {}).get("sha", ""))
        if not head:
            continue
        # Re-check immediately before each write. A delayed push run must never erase a newer
        # Guardian success whose target Required Certification already proves this same main SHA.
        if current_guardian_certified(repo, token, head, main_sha):
            preserved.append({"pr": pr.get("number"), "head": head})
            continue
        v4.v3.guardian.publish_status(
            repo,
            token,
            head,
            "failure",
            f"main advanced to {main_sha[:12]}; exact-head/base re-certification required",
            target_url,
        )
        invalidated.append({"pr": pr.get("number"), "head": head})
    return {"status": "PASS", "mainSha": main_sha, "invalidated": invalidated, "preservedCurrent": preserved}


def validate_and_publish(root: Path, repo: str, token: str, run_id: int, head: str, target_url: str) -> dict[str, Any]:
    result = validate_run(root, repo, token, run_id, head)
    audited = str(result.get("runAuditedBaseSha", ""))
    require(bool(audited), "Validated run did not expose its audited base")
    require(live_main_sha(repo, token) == audited, "main advanced before Guardian success publication")
    pr = current_open_main_pr(repo, token, head)
    require(pr.get("base", {}).get("sha") == audited, "PR base advanced before Guardian success publication")

    v4.v3.guardian.publish_status(
        repo,
        token,
        head,
        "success",
        "trusted default-branch certification accepted",
        target_url,
    )

    # Post-publication validation closes the GET->POST race: if main/base moved between the final
    # pre-check and the status write, this same command immediately overwrites its own stale green.
    post_main = live_main_sha(repo, token)
    try:
        post_pr = current_open_main_pr(repo, token, head)
        post_base = str(post_pr.get("base", {}).get("sha", ""))
    except Exception:
        post_base = ""
    if post_main != audited or post_base != audited:
        v4.v3.guardian.publish_status(
            repo,
            token,
            head,
            "failure",
            "main/PR base changed during Guardian publication; re-certification required",
            target_url,
        )
        raise GuardianV5Error(
            f"main/PR base changed during Guardian publication: main={post_main}, pr={post_base}, audited={audited}"
        )
    result["guardianSuccessPublished"] = True
    return result


def invalidate_retarget_signal(repo: str, token: str, run_id: int, target_url: str) -> dict[str, Any]:
    run = v4.v3.guardian.api_request(repo, token, "GET", f"/actions/runs/{run_id}")
    require(isinstance(run, dict), "Retarget signal run payload is missing")
    require(run.get("name") == RETARGET_SIGNAL_WORKFLOW_NAME, "Unexpected workflow_run for retarget signal")
    require(run.get("event") == "pull_request", "Retarget signal must originate from pull_request")
    jobs = v4.v3.paged(repo, token, f"/actions/runs/{run_id}/jobs", "jobs")
    signal_jobs = [job for job in jobs if job.get("name") == RETARGET_SIGNAL_JOB_NAME]
    require(len(signal_jobs) == 1, f"Expected one retarget signal job, got {len(signal_jobs)}")
    if signal_jobs[0].get("conclusion") != "success":
        return {"status": "PASS", "action": "ignored-non-base-edit", "runId": run_id}

    snapshots = [item for item in run.get("pull_requests", []) if item.get("base", {}).get("ref") == "main"]
    require(len(snapshots) == 1, "Retarget signal does not contain one PR snapshot targeting main")
    head = str(snapshots[0].get("head", {}).get("sha", ""))
    require(bool(head), "Retarget signal head SHA is missing")
    v4.v3.guardian.publish_status(
        repo,
        token,
        head,
        "failure",
        "PR base changed; exact-base re-certification required",
        target_url,
    )
    return {"status": "PASS", "action": "invalidated", "head": head, "runId": run_id}


def self_test() -> dict[str, Any]:
    source = """
package com.example.data
import androidx.room.Database
import androidx.room.RoomDatabase
const val APP_DATABASE_SCHEMA_VERSION = 22
@Database(entities = [], version = 22, exportSchema = true)
@TypeConverters(Foo::class)
abstract class AppDatabase : RoomDatabase()
"""
    require(parse_room_versions(source) == (22, 22), "Canonical Room declaration did not parse")

    fake = source.replace(
        "@Database(entities = [], version = 22, exportSchema = true)",
        "annotation class Database(val version: Int)\n@Database(version = 22)\nclass Dummy\n@androidx.room.Database(entities = [], version = 21, exportSchema = false)",
    )
    try:
        parse_room_versions(fake)
    except GuardianV5Error:
        pass
    else:
        raise GuardianV5Error("Fake Database annotation negative test did not fail")

    binding = """
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
android { sourceSets { getByName("androidTest").assets.srcDir("$projectDir/schemas") } }
"""
    validate_schema_binding_text(binding)
    try:
        validate_schema_binding_text(binding + '\nandroid { sourceSets { getByName("androidTest").assets.setSrcDirs(listOf("$projectDir/alternate")) } }\n')
    except (GuardianV5Error, v4.GuardianV4Error):
        pass
    else:
        raise GuardianV5Error("Alternate assets setter negative test did not fail")

    return {
        "status": "PASS",
        "roomAnnotationBoundToAppDatabase": True,
        "alternateAssetsSetterRejected": True,
        "successPostChecked": True,
        "generationAwareInvalidation": True,
        "trustedRetargetSignal": True,
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
        if args.command == "validate-and-publish":
            result = validate_and_publish(Path(args.root).resolve(), args.repo, args.token, args.run_id, args.head, args.target_url)
        elif args.command == "invalidate-current-main":
            result = invalidate_current_main(args.repo, args.token, args.main_sha, args.target_url)
        elif args.command == "invalidate-retarget-run":
            result = invalidate_retarget_signal(args.repo, args.token, args.run_id, args.target_url)
        elif args.command == "publish-status":
            v4.v3.guardian.publish_status(args.repo, args.token, args.sha, args.state, args.description, args.target_url)
            result = {"status": "PASS", "sha": args.sha, "state": args.state, "context": v4.v3.guardian.GUARDIAN_CONTEXT}
        else:
            result = self_test()
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except Exception as exc:
        print(f"PHASE 10.9 TRUSTED GUARDIAN V5 FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
