#!/usr/bin/env python3
"""Trusted Phase 10.9 guardian v12.

V12 preserves V11 and additionally resolves Kotlin typealiases that ultimately refer to
androidx.room.Room, including chained aliases. This closes the remaining lexical builder gap
without weakening any V11 checks.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

import phase109_guardian_v11 as v11

SELF_PATH = ".github/scripts/phase109_guardian_v12.py"
BASE_V11_VALIDATE_RUN = v11.validate_run
v11.v10.v9.v8.v7.v6.v5.v4.v3.guardian.IMMUTABLE_TRUST_PATHS.add(SELF_PATH)


class GuardianV12Error(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardianV12Error(message)


def kotlin_room_symbols(clean_source: str) -> set[str]:
    symbols = set(v11.kotlin_room_symbols(clean_source))
    aliases = [
        (match.group(1), match.group(2))
        for match in re.finditer(
            r"(?m)^\s*typealias\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([A-Za-z_][A-Za-z0-9_.]*)\s*;?\s*$",
            clean_source,
        )
    ]
    changed = True
    while changed:
        changed = False
        for alias, target in aliases:
            if target == "androidx.room.Room" or target in symbols:
                if alias not in symbols:
                    symbols.add(alias)
                    changed = True
    return symbols


def validate_run(root: Path, repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    old_symbols = v11.kotlin_room_symbols
    v11.kotlin_room_symbols = kotlin_room_symbols
    try:
        result = BASE_V11_VALIDATE_RUN(root, repo, token, run_id, head)
        result["productionRoomBuilderContractV12"] = {
            "kotlinRoomTypealiasesResolved": True,
        }
        return result
    finally:
        v11.kotlin_room_symbols = old_symbols


def validate_and_publish(root: Path, repo: str, token: str, run_id: int, head: str,
                         target_url: str) -> dict[str, Any]:
    old_validate = v11.validate_run
    old_symbols = v11.kotlin_room_symbols
    v11.validate_run = validate_run
    v11.kotlin_room_symbols = kotlin_room_symbols
    try:
        return v11.validate_and_publish(root, repo, token, run_id, head, target_url)
    finally:
        v11.validate_run = old_validate
        v11.kotlin_room_symbols = old_symbols


def parser_self_test() -> None:
    source = """
        typealias DirectRoom = androidx.room.Room
        typealias ChainedRoom = DirectRoom
        val first = DirectRoom.databaseBuilder(ctx, Db::class.java, "a")
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        val second = ChainedRoom.databaseBuilder(ctx, Db::class.java, "b")
            .build()
    """
    clean = v11.v10.v9.v8.v7.v6.v5.v4.executable_kotlin(source)
    symbols = kotlin_room_symbols(clean)
    require("DirectRoom" in symbols and "ChainedRoom" in symbols,
            f"Room typealias resolution failed: {symbols}")
    chains = v11.find_builder_chains(clean, symbols)
    require(len(chains) == 2, f"Room typealias builders were not both detected: {len(chains)}")
    require(v11.normalized_migration_calls(chains[0]) == ["*AppDatabase.ALL_MIGRATIONS"],
            "Canonical migration on typealias builder was not parsed")
    require(v11.normalized_migration_calls(chains[1]) == [],
            "Unregistered typealias builder was not exposed")


def self_test() -> dict[str, Any]:
    old_symbols = v11.kotlin_room_symbols
    v11.kotlin_room_symbols = kotlin_room_symbols
    try:
        v11.self_test()
        parser_self_test()
    finally:
        v11.kotlin_room_symbols = old_symbols
    return {
        "status": "PASS",
        "guardianV11": "PASS",
        "kotlinRoomTypealiasesResolved": True,
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
            result = v11.v10.v9.v8.v7.v6.v5.invalidate_current_main(
                args.repo, args.token, args.main_sha, args.target_url
            )
        elif args.command == "invalidate-retarget-run":
            result = v11.v10.v9.v8.v7.v6.v5.invalidate_retarget_signal(
                args.repo, args.token, args.run_id, args.target_url
            )
        elif args.command == "publish-failure-if-latest":
            result = v11.v10.v9.v8.v7.publish_failure_if_latest(
                args.repo, args.token, args.run_id, args.head, args.description, args.target_url
            )
        else:
            result = self_test()
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except Exception as exc:
        print(f"PHASE 10.9 TRUSTED GUARDIAN V12 FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
