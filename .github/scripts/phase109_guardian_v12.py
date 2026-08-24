#!/usr/bin/env python3
"""Trusted Phase 10.9 guardian v12.

V12 preserves V11 and additionally:
- resolves Kotlin Room typealiases across the complete candidate source tree, including chained aliases;
- audits Room builders in every tracked module source set, not only the app module;
- keeps variant/unit/instrumentation test source sets excluded from production checks.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any, Iterable

import phase109_guardian_v11 as v11

SELF_PATH = ".github/scripts/phase109_guardian_v12.py"
BASE_V11_VALIDATE_RUN = v11.validate_run
BASE_V11_KOTLIN_ROOM_SYMBOLS = v11.kotlin_room_symbols
BASE_V11_IS_PRODUCTION_ANDROID_SOURCE = v11.is_production_android_source
v11.v10.v9.v8.v7.v6.v5.v4.v3.guardian.IMMUTABLE_TRUST_PATHS.add(SELF_PATH)


class GuardianV12Error(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardianV12Error(message)


def typealias_pairs(clean_source: str) -> list[tuple[str, str]]:
    return [
        (match.group(1), match.group(2))
        for match in re.finditer(
            r"(?m)^\s*typealias\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([A-Za-z_][A-Za-z0-9_.]*)\s*;?\s*$",
            clean_source,
        )
    ]


def resolve_global_room_typealiases(clean_sources: Iterable[str]) -> set[str]:
    pairs: list[tuple[str, str]] = []
    resolved: set[str] = set()
    for clean in clean_sources:
        local_symbols = set(BASE_V11_KOTLIN_ROOM_SYMBOLS(clean))
        for alias, target in typealias_pairs(clean):
            pairs.append((alias, target))
            if target == "androidx.room.Room" or target in local_symbols:
                resolved.add(alias)
    changed = True
    while changed:
        changed = False
        for alias, target in pairs:
            if target in resolved and alias not in resolved:
                resolved.add(alias)
                changed = True
    return resolved


def kotlin_room_symbols_with_aliases(clean_source: str, global_aliases: set[str]) -> set[str]:
    return set(BASE_V11_KOTLIN_ROOM_SYMBOLS(clean_source)) | global_aliases


def is_production_android_source(path: str) -> bool:
    parts = Path(path).parts
    if not path.endswith((".kt", ".java")):
        return False
    src_positions = [index for index, part in enumerate(parts[:-2]) if part == "src"]
    if not src_positions:
        return False
    src_index = src_positions[-1]
    require(src_index + 1 < len(parts), f"Malformed source-set path: {path}")
    source_set = parts[src_index + 1]
    if v11.is_variant_test_source_set(source_set):
        return False
    return True


def candidate_kotlin_sources(repo: str, token: str, head: str,
                             candidate_tree: dict[str, str]) -> list[str]:
    sources: list[str] = []
    executable_kotlin = v11.v10.v9.v8.v7.v6.v5.v4.executable_kotlin
    fetch_text = v11.v10.v9.v8.v7.v6.v5.v4.v3.fetch_text
    for path in sorted(candidate_tree):
        if is_production_android_source(path) and path.endswith(".kt"):
            sources.append(executable_kotlin(fetch_text(repo, token, head, path)))
    return sources


def validate_run(root: Path, repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    candidate_tree = v11.v10.v9.v8.v7.v6.v5.v4.v3.recursive_tree(repo, token, head)
    global_aliases = resolve_global_room_typealiases(
        candidate_kotlin_sources(repo, token, head, candidate_tree)
    )
    old_symbols = v11.kotlin_room_symbols
    old_source_predicate = v11.is_production_android_source
    v11.kotlin_room_symbols = lambda clean: kotlin_room_symbols_with_aliases(clean, global_aliases)
    v11.is_production_android_source = is_production_android_source
    try:
        result = BASE_V11_VALIDATE_RUN(root, repo, token, run_id, head)
        result["productionRoomBuilderContractV12"] = {
            "kotlinRoomTypealiasesResolvedAcrossTree": True,
            "resolvedRoomTypealiases": sorted(global_aliases),
            "allTrackedModuleSourceSetsAudited": True,
        }
        return result
    finally:
        v11.kotlin_room_symbols = old_symbols
        v11.is_production_android_source = old_source_predicate


def validate_and_publish(root: Path, repo: str, token: str, run_id: int, head: str,
                         target_url: str) -> dict[str, Any]:
    old_validate = v11.validate_run
    v11.validate_run = validate_run
    try:
        return v11.validate_and_publish(root, repo, token, run_id, head, target_url)
    finally:
        v11.validate_run = old_validate


def parser_self_test() -> None:
    first = """
        import androidx.room.Room
        typealias DirectRoom = Room
    """
    second = """
        typealias ChainedRoom = DirectRoom
        val first = DirectRoom.databaseBuilder(ctx, Db::class.java, "a")
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        val second = ChainedRoom.databaseBuilder(ctx, Db::class.java, "b")
            .build()
    """
    executable_kotlin = v11.v10.v9.v8.v7.v6.v5.v4.executable_kotlin
    clean_first = executable_kotlin(first)
    clean_second = executable_kotlin(second)
    aliases = resolve_global_room_typealiases((clean_first, clean_second))
    require(aliases == {"DirectRoom", "ChainedRoom"}, f"Global Room typealias resolution failed: {aliases}")
    symbols = kotlin_room_symbols_with_aliases(clean_second, aliases)
    chains = v11.find_builder_chains(clean_second, symbols)
    require(len(chains) == 2, f"Room typealias builders were not both detected: {len(chains)}")
    require(v11.normalized_migration_calls(chains[0]) == ["*AppDatabase.ALL_MIGRATIONS"],
            "Canonical migration on typealias builder was not parsed")
    require(v11.normalized_migration_calls(chains[1]) == [],
            "Unregistered typealias builder was not exposed")
    require(is_production_android_source("feature/src/main/java/x/Db.kt"),
            "Feature module main source must be audited")
    require(is_production_android_source("features/foo/src/release/java/x/Db.java"),
            "Nested release module source must be audited")
    require(not is_production_android_source("feature/src/testRelease/java/x/Db.kt"),
            "Variant unit-test source must not be production")
    require(not is_production_android_source("feature/src/androidTestDemo/java/x/Db.kt"),
            "Variant instrumentation source must not be production")


def self_test() -> dict[str, Any]:
    # V11 must remain independently healthy; do not monkey-patch its symbol resolver during this call.
    v11.self_test()
    parser_self_test()
    return {
        "status": "PASS",
        "guardianV11": "PASS",
        "kotlinRoomTypealiasesResolvedAcrossTree": True,
        "allTrackedModuleSourceSetsAudited": True,
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
