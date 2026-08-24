#!/usr/bin/env python3
"""Trusted Phase 10.9 guardian v11.

V11 closes the remaining Room-builder audit gaps found by independent review:
- resolves Kotlin aliases of androidx.room.Room;
- strips Java comments/string/char literals before lexical auditing;
- associates canonical addMigrations registration with each individual databaseBuilder chain,
  rather than merely with its containing file;
- continues to reject every non-canonical production addMigrations call.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

import phase109_guardian_v10 as v10

SELF_PATH = ".github/scripts/phase109_guardian_v11.py"
BASE_V10_VALIDATE_RUN = v10.validate_run
v10.v9.v8.v7.v6.v5.v4.v3.guardian.IMMUTABLE_TRUST_PATHS.add(SELF_PATH)


class GuardianV11Error(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardianV11Error(message)


def strip_java_comments_and_literals(source: str) -> str:
    out: list[str] = []
    i = 0
    n = len(source)
    state = "code"
    while i < n:
        ch = source[i]
        nxt = source[i + 1] if i + 1 < n else ""
        if state == "code":
            if ch == "/" and nxt == "/":
                out.extend("  ")
                i += 2
                state = "line_comment"
                continue
            if ch == "/" and nxt == "*":
                out.extend("  ")
                i += 2
                state = "block_comment"
                continue
            if ch == '"':
                out.append(" ")
                i += 1
                state = "string"
                continue
            if ch == "'":
                out.append(" ")
                i += 1
                state = "char"
                continue
            out.append(ch)
            i += 1
            continue
        if state == "line_comment":
            if ch == "\n":
                out.append("\n")
                state = "code"
            else:
                out.append(" ")
            i += 1
            continue
        if state == "block_comment":
            if ch == "*" and nxt == "/":
                out.extend("  ")
                i += 2
                state = "code"
            else:
                out.append("\n" if ch == "\n" else " ")
                i += 1
            continue
        if state in {"string", "char"}:
            quote = '"' if state == "string" else "'"
            if ch == "\\":
                out.append(" ")
                if i + 1 < n:
                    out.append("\n" if source[i + 1] == "\n" else " ")
                    i += 2
                else:
                    i += 1
                continue
            if ch == quote:
                out.append(" ")
                i += 1
                state = "code"
            else:
                out.append("\n" if ch == "\n" else " ")
                i += 1
            continue
    return "".join(out)


def kotlin_room_symbols(clean_source: str) -> set[str]:
    symbols: set[str] = set()
    if re.search(r"(?m)^\s*import\s+androidx\.room\.Room\s*$", clean_source):
        symbols.add("Room")
    for match in re.finditer(r"(?m)^\s*import\s+androidx\.room\.Room\s+as\s+([A-Za-z_][A-Za-z0-9_]*)\s*$", clean_source):
        symbols.add(match.group(1))
    # Fully-qualified use does not require an import.
    symbols.add("androidx.room.Room")
    return symbols


def java_room_symbols(clean_source: str) -> set[str]:
    symbols = {"androidx.room.Room"}
    if re.search(r"(?m)^\s*import\s+androidx\.room\.Room\s*;", clean_source):
        symbols.add("Room")
    return symbols


def builder_start_pattern(symbols: set[str]) -> re.Pattern[str]:
    alternatives = "|".join(sorted((re.escape(symbol) for symbol in symbols), key=len, reverse=True))
    require(bool(alternatives), "No Room symbols available for builder audit")
    return re.compile(rf"(?<![A-Za-z0-9_.])(?:{alternatives})\s*\.\s*databaseBuilder\s*\(")


def find_builder_chains(clean_source: str, symbols: set[str]) -> list[str]:
    pattern = builder_start_pattern(symbols)
    matches = list(pattern.finditer(clean_source))
    chains: list[str] = []
    build_pattern = re.compile(r"\.\s*build\s*\(\s*\)")
    for index, match in enumerate(matches):
        boundary = matches[index + 1].start() if index + 1 < len(matches) else len(clean_source)
        region = clean_source[match.start():boundary]
        build = build_pattern.search(region)
        require(build is not None, f"Room.databaseBuilder expression has no visible build() terminal near offset {match.start()}")
        chains.append(region[:build.end()])
    return chains


def normalized_migration_calls(text: str) -> list[str]:
    add_pattern = re.compile(r"(?<![A-Za-z0-9_])addMigrations\s*\(([^)]*)\)")
    return [re.sub(r"\s+", "", arg) for arg in add_pattern.findall(text)]


def validate_every_production_room_builder(repo: str, token: str, head: str,
                                            candidate_tree: dict[str, str]) -> dict[str, Any]:
    database_path = v10.v9.v8.v7.v6.v5.v4.v3.ROOM_DATABASE_PATH
    builders: list[dict[str, Any]] = []
    registrations: list[dict[str, Any]] = []
    suspicious: list[str] = []

    for path in sorted(candidate_tree):
        if not v10.is_production_android_source(path):
            continue
        source = v10.v9.v8.v7.v6.v5.v4.v3.fetch_text(repo, token, head, path)
        if path.endswith(".kt"):
            clean = v10.v9.v8.v7.v6.v5.v4.executable_kotlin(source)
            symbols = kotlin_room_symbols(clean)
        else:
            clean = strip_java_comments_and_literals(source)
            symbols = java_room_symbols(clean)

        all_calls = normalized_migration_calls(clean)
        expected = v10.canonical_migration_argument(path, database_path)
        for call in all_calls:
            registrations.append({"path": path, "argument": call, "expected": expected})
            if call != expected:
                suspicious.append(f"{path}:{call}")

        chains = find_builder_chains(clean, symbols)
        for chain_index, chain in enumerate(chains, start=1):
            chain_calls = normalized_migration_calls(chain)
            require(len(chain_calls) == 1,
                    f"Production Room builder #{chain_index} in {path} must have exactly one migration registration; got {chain_calls}")
            require(chain_calls[0] == expected,
                    f"Production Room builder #{chain_index} in {path} has non-canonical migration registration: {chain_calls[0]}")
            builders.append({
                "path": path,
                "builderIndex": chain_index,
                "canonicalRegistration": chain_calls[0],
                "expected": expected,
            })

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
        "everyBuilderExpressionBoundToCanonicalMigrations": True,
        "kotlinRoomAliasesResolved": True,
        "javaCommentsAndLiteralsStripped": True,
    }


def validate_run(root: Path, repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    old_v10 = v10.validate_every_production_room_builder
    v10.validate_every_production_room_builder = validate_every_production_room_builder
    try:
        candidate_tree = v10.v9.v8.v7.v6.v5.v4.v3.recursive_tree(repo, token, head)
        result = BASE_V10_VALIDATE_RUN(root, repo, token, run_id, head)
        result["productionRoomBuilderContractV11"] = validate_every_production_room_builder(
            repo, token, head, candidate_tree
        )
        return result
    finally:
        v10.validate_every_production_room_builder = old_v10


def validate_and_publish(root: Path, repo: str, token: str, run_id: int, head: str,
                         target_url: str) -> dict[str, Any]:
    v10.v9.require_latest_same_head_run(repo, token, run_id, head)
    result = validate_run(root, repo, token, run_id, head)
    v10.v9.require_latest_same_head_run(repo, token, run_id, head)
    audited = str(result.get("runAuditedBaseSha", ""))
    require(bool(audited), "Validated run did not expose its audited base")
    require(v10.v9.v8.v7.v6.v5.live_main_sha(repo, token) == audited,
            "main advanced before Guardian success publication")
    pr = v10.v9.v8.v7.v6.v5.current_open_main_pr(repo, token, head)
    require(pr.get("base", {}).get("sha") == audited,
            "PR base advanced before Guardian success publication")
    v10.v9.require_latest_same_head_run(repo, token, run_id, head)
    v10.v9.v8.v7.v6.v5.v4.v3.guardian.publish_status(
        repo, token, head, "success", "trusted default-branch certification accepted", target_url,
    )
    latest_after = v10.v9.latest_same_head_required_run_id(repo, token, head)
    post_main = v10.v9.v8.v7.v6.v5.live_main_sha(repo, token)
    try:
        post_pr = v10.v9.v8.v7.v6.v5.current_open_main_pr(repo, token, head)
        post_base = str(post_pr.get("base", {}).get("sha", ""))
    except Exception:
        post_base = ""
    if latest_after != run_id or post_main != audited or post_base != audited:
        v10.v9.v8.v7.v6.v5.v4.v3.guardian.publish_status(
            repo, token, head, "failure",
            "Guardian publication superseded or base changed; newest exact-base certification required",
            target_url,
        )
        raise GuardianV11Error(
            f"Guardian publication invalidated: latest={latest_after}, run={run_id}, main={post_main}, pr={post_base}, audited={audited}"
        )
    result["guardianSuccessPublished"] = True
    result["latestSameHeadRunId"] = run_id
    return result


def parser_self_test() -> None:
    kotlin = """
        import androidx.room.Room as SaveRoom
        val a = SaveRoom.databaseBuilder(ctx, Db::class.java, \"a\")
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        val b = SaveRoom.databaseBuilder(ctx, Db::class.java, \"b\")
            .build()
    """
    clean = v10.v9.v8.v7.v6.v5.v4.executable_kotlin(kotlin)
    chains = find_builder_chains(clean, kotlin_room_symbols(clean))
    require(len(chains) == 2, f"Alias builder detection failed: {len(chains)}")
    require(normalized_migration_calls(chains[0]) == ["*AppDatabase.ALL_MIGRATIONS"], "First builder migration parse failed")
    require(normalized_migration_calls(chains[1]) == [], "Second builder should be detected as unregistered")

    java = '''
      import androidx.room.Room;
      // addMigrations(*AppDatabase.ALL_MIGRATIONS)
      String x = "addMigrations(*AppDatabase.ALL_MIGRATIONS)";
      Room.databaseBuilder(ctx, Db.class, "x").build();
    '''
    clean_java = strip_java_comments_and_literals(java)
    require(normalized_migration_calls(clean_java) == [], "Java comment/literal stripping failed")
    require(len(find_builder_chains(clean_java, java_room_symbols(clean_java))) == 1, "Java builder detection failed")


def self_test() -> dict[str, Any]:
    v10.self_test()
    parser_self_test()
    return {
        "status": "PASS",
        "guardianV10": "PASS",
        "builderExpressionBinding": True,
        "kotlinRoomAliasesResolved": True,
        "javaCommentsAndLiteralsStripped": True,
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
            result = v10.v9.v8.v7.v6.v5.invalidate_current_main(args.repo, args.token,
                                                                 args.main_sha, args.target_url)
        elif args.command == "invalidate-retarget-run":
            result = v10.v9.v8.v7.v6.v5.invalidate_retarget_signal(args.repo, args.token,
                                                                    args.run_id, args.target_url)
        elif args.command == "publish-failure-if-latest":
            result = v10.v9.v8.v7.publish_failure_if_latest(
                args.repo, args.token, args.run_id, args.head, args.description, args.target_url
            )
        else:
            result = self_test()
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except Exception as exc:
        print(f"PHASE 10.9 TRUSTED GUARDIAN V11 FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
