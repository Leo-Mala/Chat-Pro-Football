#!/usr/bin/env python3
"""Trusted Phase 10.9 guardian v12.

V12 preserves V11 and additionally:
- resolves Kotlin Room typealiases across the complete candidate source tree, including chained aliases;
- resolves local/import-aliased names that refer to those cross-file Room typealiases;
- audits Room builders in every tracked module source set, not only the app module;
- keeps variant/unit/instrumentation test source sets excluded from production checks;
- validates the full Required Certification run emitted by a push to main and publishes trusted
  status only when that exact main SHA, its first parent, all mandatory jobs and provenance agree.
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
V3 = v11.v10.v9.v8.v7.v6.v5.v4.v3
V5 = v11.v10.v9.v8.v7.v6.v5
CORE = V3.guardian
MAIN_REQUIRED_JOBS = {
    "Policy, exact HEAD and scope",
    "Build, Core, Migration, Save Recovery and FC26",
    "20 and 100 Season Stress",
    "UI Golden Regression and Accessibility",
    "Full-scale Rollover (normal)",
    "Full-scale Rollover (constrained)",
    "Android api24-startup",
    "Android api30-startup",
    "Android api35-debug",
    "Android api35-release",
    "Required Certification Gate",
}
CORE.IMMUTABLE_TRUST_PATHS.add(SELF_PATH)


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
            if (target in resolved or target.rsplit(".", 1)[-1] in resolved) and alias not in resolved:
                resolved.add(alias)
                changed = True
    return resolved


def imported_room_typealias_names(clean_source: str, global_aliases: set[str]) -> set[str]:
    """Resolve names visible in this file for globally known Room typealiases.

    Kotlin imports may rename a cross-file typealias, e.g. `import pkg.SaveRoom as LocalRoom`.
    The builder audit must recognize LocalRoom rather than only the declaration name SaveRoom.
    """
    visible: set[str] = set()
    for match in re.finditer(
        r"(?m)^\s*import\s+([A-Za-z_][A-Za-z0-9_.]*)(?:\s+as\s+([A-Za-z_][A-Za-z0-9_]*))?\s*;?\s*$",
        clean_source,
    ):
        target, local_name = match.group(1), match.group(2)
        declared_name = target.rsplit(".", 1)[-1]
        if declared_name in global_aliases:
            visible.add(local_name or declared_name)
    return visible


def kotlin_room_symbols_with_aliases(clean_source: str, global_aliases: set[str]) -> set[str]:
    return (
        set(BASE_V11_KOTLIN_ROOM_SYMBOLS(clean_source))
        | global_aliases
        | imported_room_typealias_names(clean_source, global_aliases)
    )


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
    fetch_text = V3.fetch_text
    for path in sorted(candidate_tree):
        if is_production_android_source(path) and path.endswith(".kt"):
            sources.append(executable_kotlin(fetch_text(repo, token, head, path)))
    return sources


def validate_run(root: Path, repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    candidate_tree = V3.recursive_tree(repo, token, head)
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
            "importAliasedRoomTypealiasesResolved": True,
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


def validate_main_required_run(repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    """Validate exact-main evidence without executing candidate code with the Guardian token."""
    run = CORE.api_request(repo, token, "GET", f"/actions/runs/{run_id}")
    require(isinstance(run, dict), "Required main workflow run payload is missing")
    require(run.get("name") == "Phase 10.9 Required Certification",
            f"Unexpected main certification workflow: {run.get('name')}")
    require(run.get("path") == CORE.REQUIRED_WORKFLOW_PATH,
            f"Unexpected main certification workflow path: {run.get('path')}")
    require(run.get("event") == "push", f"Main certification must be push-triggered: {run.get('event')}")
    require(run.get("head_sha") == head, "Main certification run HEAD does not match requested SHA")
    require(run.get("conclusion") == "success", f"Main certification run is not successful: {run.get('conclusion')}")
    require(V5.live_main_sha(repo, token) == head, "main advanced before trusted main validation")

    commit = CORE.api_request(repo, token, "GET", f"/commits/{head}")
    require(isinstance(commit, dict), "Main commit metadata is missing")
    parents = commit.get("parents", [])
    require(isinstance(parents, list) and len(parents) >= 1, "Certified main commit has no first parent")
    audited_base = str(parents[0].get("sha", ""))
    require(bool(audited_base), "Certified main first-parent SHA is missing")

    jobs = V3.paged(repo, token, f"/actions/runs/{run_id}/jobs", "jobs")
    job_map = {str(job.get("name", "")): str(job.get("conclusion", "")) for job in jobs}
    missing = sorted(name for name in MAIN_REQUIRED_JOBS if name not in job_map)
    require(not missing, f"Main certification is missing mandatory jobs: {missing}")
    failed = sorted(name for name in MAIN_REQUIRED_JOBS if job_map.get(name) != "success")
    require(not failed, f"Main certification mandatory jobs are not successful: {failed}")

    provenance = V3.read_provenance_artifact(repo, token, run_id, head, audited_base)
    scopes = provenance.get("scopes")
    results = provenance.get("results")
    require(isinstance(scopes, dict), "Main provenance scopes are missing")
    require(isinstance(results, dict), "Main provenance results are missing")
    for key in ("jvm", "stress", "release", "ui", "performance", "instrumented"):
        require(scopes.get(key) is True, f"Main provenance did not force mandatory scope: {key}")
        require(results.get(key) == "success", f"Main provenance result is not successful: {key}={results.get(key)}")

    return {
        "status": "PASS",
        "mainHeadSha": head,
        "auditedBaseSha": audited_base,
        "requiredRunId": run_id,
        "mandatoryJobs": sorted(MAIN_REQUIRED_JOBS),
        "provenanceStatus": provenance.get("status"),
        "allScopesForced": True,
    }


def validate_main_and_publish(repo: str, token: str, run_id: int, head: str,
                              target_url: str) -> dict[str, Any]:
    result = validate_main_required_run(repo, token, run_id, head)
    require(V5.live_main_sha(repo, token) == head, "main advanced before trusted main success publication")
    CORE.publish_status(
        repo,
        token,
        head,
        "success",
        "trusted exact-main recertification accepted",
        target_url,
    )
    post_main = V5.live_main_sha(repo, token)
    if post_main != head:
        CORE.publish_status(
            repo,
            token,
            head,
            "failure",
            "main advanced during trusted exact-main publication; recertification required",
            target_url,
        )
        raise GuardianV12Error(f"main advanced during publication: expected={head}, live={post_main}")
    result["guardianMainSuccessPublished"] = True
    return result


def parser_self_test() -> None:
    first = """
        package pkg
        import androidx.room.Room
        typealias DirectRoom = Room
    """
    second = """
        package pkg
        typealias ChainedRoom = DirectRoom
        val first = DirectRoom.databaseBuilder(ctx, Db::class.java, "a")
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        val second = ChainedRoom.databaseBuilder(ctx, Db::class.java, "b")
            .build()
    """
    third = """
        package consumer
        import pkg.ChainedRoom as LocalRoom
        val third = LocalRoom.databaseBuilder(ctx, Db::class.java, "c")
            .build()
    """
    executable_kotlin = v11.v10.v9.v8.v7.v6.v5.v4.executable_kotlin
    clean_first = executable_kotlin(first)
    clean_second = executable_kotlin(second)
    clean_third = executable_kotlin(third)
    aliases = resolve_global_room_typealiases((clean_first, clean_second, clean_third))
    require(aliases == {"DirectRoom", "ChainedRoom"}, f"Global Room typealias resolution failed: {aliases}")
    symbols = kotlin_room_symbols_with_aliases(clean_second, aliases)
    chains = v11.find_builder_chains(clean_second, symbols)
    require(len(chains) == 2, f"Room typealias builders were not both detected: {len(chains)}")
    require(v11.normalized_migration_calls(chains[0]) == ["*AppDatabase.ALL_MIGRATIONS"],
            "Canonical migration on typealias builder was not parsed")
    require(v11.normalized_migration_calls(chains[1]) == [],
            "Unregistered typealias builder was not exposed")
    imported_symbols = kotlin_room_symbols_with_aliases(clean_third, aliases)
    require("LocalRoom" in imported_symbols, "Imported alias of cross-file Room typealias was not resolved")
    require(len(v11.find_builder_chains(clean_third, imported_symbols)) == 1,
            "Builder through imported Room typealias alias was not detected")
    require(is_production_android_source("feature/src/main/java/x/Db.kt"),
            "Feature module main source must be audited")
    require(is_production_android_source("features/foo/src/release/java/x/Db.java"),
            "Nested release module source must be audited")
    require(not is_production_android_source("feature/src/testRelease/java/x/Db.kt"),
            "Variant unit-test source must not be production")
    require(not is_production_android_source("feature/src/androidTestDemo/java/x/Db.kt"),
            "Variant instrumentation source must not be production")
    require(len(MAIN_REQUIRED_JOBS) == 11 and "Required Certification Gate" in MAIN_REQUIRED_JOBS,
            "Exact-main mandatory job contract is incomplete")


def self_test() -> dict[str, Any]:
    v11.self_test()
    parser_self_test()
    return {
        "status": "PASS",
        "guardianV11": "PASS",
        "kotlinRoomTypealiasesResolvedAcrossTree": True,
        "importAliasedRoomTypealiasesResolved": True,
        "allTrackedModuleSourceSetsAudited": True,
        "exactMainRequiredJobContract": sorted(MAIN_REQUIRED_JOBS),
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
    validate_main = sub.add_parser("validate-main-and-publish")
    validate_main.add_argument("--repo", required=True)
    validate_main.add_argument("--token", required=True)
    validate_main.add_argument("--run-id", type=int, required=True)
    validate_main.add_argument("--head", required=True)
    validate_main.add_argument("--target-url", default="")
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
        elif args.command == "validate-main-and-publish":
            result = validate_main_and_publish(
                args.repo, args.token, args.run_id, args.head, args.target_url
            )
        elif args.command == "invalidate-current-main":
            result = V5.invalidate_current_main(
                args.repo, args.token, args.main_sha, args.target_url
            )
        elif args.command == "invalidate-retarget-run":
            result = V5.invalidate_retarget_signal(
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
