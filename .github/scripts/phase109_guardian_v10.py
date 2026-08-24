#!/usr/bin/env python3
"""Trusted Phase 10.9 guardian v10.

V10 preserves V9 and closes the production Room-builder coverage gaps:
- every non-test Android source set is audited, including release-only/debug/variant sources;
- every source file that creates a Room database builder must also register exactly the canonical
  AppDatabase migration array;
- every addMigrations call in production code is audited, not only the two historical main-source
  registrations.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

import phase109_guardian_v9 as v9

SELF_PATH = ".github/scripts/phase109_guardian_v10.py"
BASE_V9_VALIDATE_RUN = v9.validate_run
v9.v8.v7.v6.v5.v4.v3.guardian.IMMUTABLE_TRUST_PATHS.add(SELF_PATH)


class GuardianV10Error(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardianV10Error(message)


def is_production_android_source(path: str) -> bool:
    if not path.startswith("app/src/") or not path.lower().endswith((".kt", ".java")):
        return False
    parts = path.split("/")
    if len(parts) < 4:
        return False
    source_set = parts[2].lower()
    return source_set not in {"test", "androidtest", "testfixtures"}


def canonical_migration_argument(path: str, database_path: str) -> str:
    return "*ALL_MIGRATIONS" if path == database_path else "*AppDatabase.ALL_MIGRATIONS"


def validate_every_production_room_builder(repo: str, token: str, head: str,
                                            candidate_tree: dict[str, str]) -> dict[str, Any]:
    database_path = v9.v8.v7.v6.v5.v4.v3.ROOM_DATABASE_PATH
    add_pattern = re.compile(r"(?<![A-Za-z0-9_])addMigrations\s*\(([^)]*)\)")
    builder_pattern = re.compile(r"\b(?:androidx\.room\.)?Room\s*\.\s*databaseBuilder\s*\(")
    builders: list[dict[str, Any]] = []
    registrations: list[dict[str, Any]] = []
    suspicious: list[str] = []

    for path in sorted(candidate_tree):
        if not is_production_android_source(path):
            continue
        text = v9.v8.v7.v6.v5.v4.v3.fetch_text(repo, token, head, path)
        clean = v9.v8.v7.v6.v5.v4.executable_kotlin(text) if path.endswith(".kt") else text
        expected = canonical_migration_argument(path, database_path)
        calls = [re.sub(r"\s+", "", arg) for arg in add_pattern.findall(clean)]
        for call in calls:
            registrations.append({"path": path, "argument": call, "expected": expected})
            if call != expected:
                suspicious.append(f"{path}:{call}")

        builder_count = len(builder_pattern.findall(clean))
        if builder_count:
            # Fail closed per source file: a production builder must be visibly chained to the
            # canonical migration registration in the same audited executable source unit. This
            # prevents dead canonical registrations elsewhere from satisfying a global count.
            require(calls, f"Production Room builder has no migration registration: {path}")
            require(all(call == expected for call in calls),
                    f"Production Room builder has non-canonical migration registration: {path}:{calls}")
            builders.append({"path": path, "builderCalls": builder_count,
                             "canonicalRegistrations": len(calls), "expected": expected})

    require(builders, "No production Room.databaseBuilder call was found")
    require(registrations, "No production addMigrations registration was found")
    require(not suspicious, f"Non-canonical production addMigrations calls found: {suspicious}")
    require(any(item["path"] == database_path for item in registrations),
            "AppDatabase canonical migration registration is missing")
    require(any(item["path"] != database_path for item in registrations),
            "External production Room migration registration is missing")
    return {
        "productionBuilders": builders,
        "registrations": registrations,
        "allAndroidProductionSourceSetsAudited": True,
        "everyBuilderBoundToCanonicalMigrations": True,
    }


def validate_run(root: Path, repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    old_v9_registration = v9.validate_room_builder_registrations
    # V10 supersedes V9's main-only registration audit with the stronger all-source-set contract.
    v9.validate_room_builder_registrations = lambda repo_arg, token_arg, head_arg, tree_arg: (
        validate_every_production_room_builder(repo_arg, token_arg, head_arg, tree_arg)
    )
    try:
        candidate_tree = v9.v8.v7.v6.v5.v4.v3.recursive_tree(repo, token, head)
        result = BASE_V9_VALIDATE_RUN(root, repo, token, run_id, head)
        result["productionRoomBuilderContractV10"] = validate_every_production_room_builder(
            repo, token, head, candidate_tree
        )
        return result
    finally:
        v9.validate_room_builder_registrations = old_v9_registration


def validate_and_publish(root: Path, repo: str, token: str, run_id: int, head: str,
                         target_url: str) -> dict[str, Any]:
    # Keep V9 newest-run ordering while substituting V10 validation.
    v9.require_latest_same_head_run(repo, token, run_id, head)
    result = validate_run(root, repo, token, run_id, head)
    v9.require_latest_same_head_run(repo, token, run_id, head)
    audited = str(result.get("runAuditedBaseSha", ""))
    require(bool(audited), "Validated run did not expose its audited base")
    require(v9.v8.v7.v6.v5.live_main_sha(repo, token) == audited,
            "main advanced before Guardian success publication")
    pr = v9.v8.v7.v6.v5.current_open_main_pr(repo, token, head)
    require(pr.get("base", {}).get("sha") == audited,
            "PR base advanced before Guardian success publication")
    v9.require_latest_same_head_run(repo, token, run_id, head)
    v9.v8.v7.v6.v5.v4.v3.guardian.publish_status(
        repo, token, head, "success", "trusted default-branch certification accepted", target_url,
    )
    latest_after = v9.latest_same_head_required_run_id(repo, token, head)
    post_main = v9.v8.v7.v6.v5.live_main_sha(repo, token)
    try:
        post_pr = v9.v8.v7.v6.v5.current_open_main_pr(repo, token, head)
        post_base = str(post_pr.get("base", {}).get("sha", ""))
    except Exception:
        post_base = ""
    if latest_after != run_id or post_main != audited or post_base != audited:
        v9.v8.v7.v6.v5.v4.v3.guardian.publish_status(
            repo, token, head, "failure",
            "Guardian publication superseded or base changed; newest exact-base certification required",
            target_url,
        )
        raise GuardianV10Error(
            f"Guardian publication invalidated: latest={latest_after}, run={run_id}, main={post_main}, pr={post_base}, audited={audited}"
        )
    result["guardianSuccessPublished"] = True
    result["latestSameHeadRunId"] = run_id
    return result


def self_test() -> dict[str, Any]:
    v9.self_test()
    require(is_production_android_source("app/src/release/java/com/example/Foo.kt"),
            "release source set must be production-audited")
    require(is_production_android_source("app/src/debug/java/com/example/Foo.kt"),
            "debug source set must be production-audited")
    require(not is_production_android_source("app/src/androidTest/java/com/example/Foo.kt"),
            "androidTest must not be classified as production")
    require(not is_production_android_source("app/src/test/java/com/example/Foo.kt"),
            "test must not be classified as production")
    return {
        "status": "PASS",
        "guardianV9": "PASS",
        "releaseAndVariantSourcesAudited": True,
        "everyProductionRoomBuilderRequiresCanonicalRegistration": True,
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
            result = validate_and_publish(Path(args.root).resolve(), args.repo, args.token,
                                          args.run_id, args.head, args.target_url)
        elif args.command == "invalidate-current-main":
            result = v9.v8.v7.v6.v5.invalidate_current_main(args.repo, args.token,
                                                             args.main_sha, args.target_url)
        elif args.command == "invalidate-retarget-run":
            result = v9.v8.v7.v6.v5.invalidate_retarget_signal(args.repo, args.token,
                                                                args.run_id, args.target_url)
        elif args.command == "publish-failure-if-latest":
            result = v9.v8.v7.publish_failure_if_latest(
                args.repo, args.token, args.run_id, args.head, args.description, args.target_url
            )
        else:
            result = self_test()
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except Exception as exc:
        print(f"PHASE 10.9 TRUSTED GUARDIAN V10 FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
