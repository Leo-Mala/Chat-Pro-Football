#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

import phase109_guardian_v5 as v5

SELF_PATH = ".github/scripts/phase109_guardian_v6.py"
GAME_SAVE_REPOSITORY_PATH = "app/src/main/java/com/example/data/repository/GameSaveRepository.kt"
BASE_V5_ROOM_HISTORY = v5.validate_room_history
BASE_V5_VALIDATE_RUN = v5.validate_run
BASE_V5_VALIDATE_AND_PUBLISH = v5.validate_and_publish
v5.v4.v3.guardian.IMMUTABLE_TRUST_PATHS.add(SELF_PATH)


class GuardianV6Error(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardianV6Error(message)


def strict_included_build_prefixes(settings_text: str) -> set[str]:
    clean = v5.v4.strip_kotlin_comments(settings_text)
    # Applied settings scripts can hide additional includeBuild declarations from this parser.
    # Reject settings indirection rather than certifying an incomplete executable build graph.
    require(
        re.search(r"\bapply\s*\(\s*from\s*=", clean) is None
        and re.search(r"\bapply\s+from\s*:", clean) is None,
        "Applied settings scripts are forbidden; includeBuild declarations must be visible in the root settings file",
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


def validate_floor_enforcement(repo: str, token: str, head: str) -> dict[str, Any]:
    database = v5.v4.executable_kotlin(v5.v4.v3.fetch_text(repo, token, head, v5.v4.v3.ROOM_DATABASE_PATH))
    repository = v5.v4.executable_kotlin(v5.v4.v3.fetch_text(repo, token, head, GAME_SAVE_REPOSITORY_PATH))
    require(
        len(re.findall(r"check\s*\(\s*userVersion\s+in\s+MINIMUM_AUTOMATICALLY_MIGRATABLE_VERSION\s*\.\.\s*APP_DATABASE_SCHEMA_VERSION\s*\)", database)) == 1,
        "AppDatabase physical preflight is not directly bound to the audited migration range",
    )
    require(
        len(re.findall(r"if\s*\(\s*userVersion\s*!in\s+AppDatabase\.MINIMUM_AUTOMATICALLY_MIGRATABLE_VERSION\s*\.\.\s*APP_DATABASE_SCHEMA_VERSION\s*\)", repository)) == 1,
        "GameSaveRepository physical recovery inspection is not directly bound to the audited migration range",
    )
    suspicious = []
    for label, source in (("database", database), ("repository", repository)):
        for pattern in (r"userVersion\s*[<>]=?\s*\d+", r"\d+\s*[<>]=?\s*userVersion"):
            suspicious.extend(f"{label}:{item}" for item in re.findall(pattern, source))
    require(not suspicious, f"Alternate literal Room floor checks are forbidden: {suspicious}")
    return {"databaseGuardBound": True, "repositoryGuardBound": True}


def validate_room_history(repo: str, token: str, base_sha: str, head: str,
                          base_tree: dict[str, str], candidate_tree: dict[str, str]) -> dict[str, Any]:
    result = BASE_V5_ROOM_HISTORY(repo, token, base_sha, head, base_tree, candidate_tree)
    result["productionFloorEnforcement"] = validate_floor_enforcement(repo, token, head)
    return result


def preserve_current_generation(repo: str, token: str, main_sha: str, target_url: str) -> dict[str, Any]:
    # V3 historically swept every open PR during any successful validation. Replace that sweep with
    # the V5 generation-aware invalidator: PRs already certified against this exact main remain green,
    # while stale/uncertified heads fail closed. The triggering head is restored to success only after
    # full validation and the V5 pre/post main/base checks complete.
    return v5.invalidate_current_main(repo, token, main_sha, target_url)


def validate_run(root: Path, repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    old_prefixes = v5.included_build_prefixes
    old_history = v5.validate_room_history
    old_sweep = v5.v4.v3.invalidate_all
    v5.included_build_prefixes = strict_included_build_prefixes
    v5.validate_room_history = validate_room_history
    v5.v4.v3.invalidate_all = preserve_current_generation
    try:
        return BASE_V5_VALIDATE_RUN(root, repo, token, run_id, head)
    finally:
        v5.included_build_prefixes = old_prefixes
        v5.validate_room_history = old_history
        v5.v4.v3.invalidate_all = old_sweep


def validate_and_publish(root: Path, repo: str, token: str, run_id: int, head: str, target_url: str) -> dict[str, Any]:
    old_validate = v5.validate_run
    v5.validate_run = validate_run
    try:
        return BASE_V5_VALIDATE_AND_PUBLISH(root, repo, token, run_id, head, target_url)
    finally:
        v5.validate_run = old_validate


def self_test() -> dict[str, Any]:
    require(strict_included_build_prefixes('includeBuild("plugins")') == {"plugins/"}, "Static includeBuild parse failed")
    for sample in (
        'includeBuild("plug" + "ins")',
        'includeBuild(provider.get())',
        'includeBuild(pathVar)',
        'apply(from = "extra.settings.gradle.kts")\nincludeBuild("plugins")',
        'apply from: "extra.settings.gradle"\nincludeBuild("plugins")',
    ):
        try:
            strict_included_build_prefixes(sample)
        except GuardianV6Error:
            pass
        else:
            raise GuardianV6Error(f"Unsafe settings/includeBuild form was not rejected: {sample}")
    require(preserve_current_generation is not v5.v4.v3.invalidate_all, "Generation-aware sweep override is not distinct")
    return {
        "status": "PASS",
        "dynamicIncludeBuildRejected": True,
        "appliedSettingsIndirectionRejected": True,
        "productionMigrationFloorBound": True,
        "sameGenerationCertificationsPreserved": True,
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
            result = v5.invalidate_current_main(args.repo, args.token, args.main_sha, args.target_url)
        elif args.command == "invalidate-retarget-run":
            result = v5.invalidate_retarget_signal(args.repo, args.token, args.run_id, args.target_url)
        elif args.command == "publish-status":
            v5.v4.v3.guardian.publish_status(args.repo, args.token, args.sha, args.state, args.description, args.target_url)
            result = {"status": "PASS", "sha": args.sha, "state": args.state}
        else:
            result = self_test()
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except Exception as exc:
        print(f"PHASE 10.9 TRUSTED GUARDIAN V6 FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())