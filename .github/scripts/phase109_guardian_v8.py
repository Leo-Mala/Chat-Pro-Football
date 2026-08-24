#!/usr/bin/env python3
"""Trusted Phase 10.9 guardian v8.

V8 preserves V7 and corrects the canonical Room registration contract: AppDatabase's own
companion registers `*ALL_MIGRATIONS`, while external production builders must register
`*AppDatabase.ALL_MIGRATIONS`. Any additional or alternate migration registration remains
fail-closed.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

import phase109_guardian_v7 as v7

SELF_PATH = ".github/scripts/phase109_guardian_v8.py"
BASE_V7_VALIDATE_RUN = v7.validate_run
BASE_V7_VALIDATE_AND_PUBLISH = v7.validate_and_publish
v7.v6.v5.v4.v3.guardian.IMMUTABLE_TRUST_PATHS.add(SELF_PATH)


class GuardianV8Error(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardianV8Error(message)


def validate_room_builder_registrations(repo: str, token: str, head: str, candidate_tree: dict[str, str]) -> dict[str, Any]:
    registrations: list[dict[str, Any]] = []
    suspicious: list[str] = []
    database_path = v7.v6.v5.v4.v3.ROOM_DATABASE_PATH
    for path in sorted(candidate_tree):
        if not path.startswith("app/src/main/") or not path.lower().endswith((".kt", ".java")):
            continue
        text = v7.v6.v5.v4.v3.fetch_text(repo, token, head, path)
        clean = v7.v6.v5.v4.executable_kotlin(text) if path.endswith(".kt") else text
        for call in re.findall(r"\.addMigrations\s*\(([^)]*)\)", clean):
            normalized = re.sub(r"\s+", "", call)
            expected = "*ALL_MIGRATIONS" if path == database_path else "*AppDatabase.ALL_MIGRATIONS"
            registrations.append({"path": path, "argument": normalized, "expected": expected})
            if normalized != expected:
                suspicious.append(f"{path}:{normalized}")
    require(len(registrations) >= 2, f"Expected canonical Room registrations in AppDatabase and slot factory; got {registrations}")
    require(not suspicious, f"Production Room builders have non-canonical migration registrations: {suspicious}")
    require(any(item["path"] == database_path for item in registrations), "AppDatabase canonical migration registration is missing")
    require(any(item["path"] != database_path for item in registrations), "External production Room builder registration is missing")
    return {"registrations": registrations, "onlyCanonicalMigrationArray": True}


def validate_run(root: Path, repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    old_registration = v7.validate_room_builder_registrations
    v7.validate_room_builder_registrations = validate_room_builder_registrations
    try:
        return BASE_V7_VALIDATE_RUN(root, repo, token, run_id, head)
    finally:
        v7.validate_room_builder_registrations = old_registration


def validate_and_publish(root: Path, repo: str, token: str, run_id: int, head: str, target_url: str) -> dict[str, Any]:
    old_validate = v7.validate_run
    v7.validate_run = validate_run
    try:
        return BASE_V7_VALIDATE_AND_PUBLISH(root, repo, token, run_id, head, target_url)
    finally:
        v7.validate_run = old_validate


def self_test() -> dict[str, Any]:
    v7.self_test()
    return {
        "status": "PASS",
        "guardianV7": "PASS",
        "appDatabaseOwnRegistration": "*ALL_MIGRATIONS",
        "externalBuilderRegistration": "*AppDatabase.ALL_MIGRATIONS",
        "alternateRegistrationsRejected": True,
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
            result = v7.v6.v5.invalidate_current_main(args.repo, args.token, args.main_sha, args.target_url)
        elif args.command == "invalidate-retarget-run":
            result = v7.v6.v5.invalidate_retarget_signal(args.repo, args.token, args.run_id, args.target_url)
        elif args.command == "publish-failure-if-latest":
            result = v7.publish_failure_if_latest(args.repo, args.token, args.run_id, args.head, args.description, args.target_url)
        else:
            result = self_test()
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except Exception as exc:
        print(f"PHASE 10.9 TRUSTED GUARDIAN V8 FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
