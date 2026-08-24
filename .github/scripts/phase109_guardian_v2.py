#!/usr/bin/env python3
"""Corrected entry point for the trusted Phase 10.9 guardian.

The original guardian implementation remains as the shared trusted library. This entry point
hardens collection pagination, accepts only pull_request certification for a merge-trust status,
and invalidates every open main PR whenever main advances.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

import phase109_guardian as guardian

SELF_PATH = ".github/scripts/phase109_guardian_v2.py"
guardian.IMMUTABLE_TRUST_PATHS.add(SELF_PATH)


def paged(repo: str, token: str, path: str, collection_key: str | None = None) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    page = 1
    separator = "&" if "?" in path else "?"
    while True:
        payload = guardian.api_request(repo, token, "GET", f"{path}{separator}per_page=100&page={page}")
        if collection_key is None:
            guardian.require(isinstance(payload, list), f"Expected list from paged endpoint: {path}")
            chunk = payload
        else:
            guardian.require(isinstance(payload, dict), f"Expected object from paged endpoint: {path}")
            chunk = payload.get(collection_key)
            guardian.require(isinstance(chunk, list), f"Expected collection '{collection_key}' from: {path}")
        result.extend(chunk)
        if len(chunk) < 100:
            return result
        page += 1


def validate_run(root: Path, repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    run = guardian.api_request(repo, token, "GET", f"/actions/runs/{run_id}")
    guardian.require(
        run.get("event") == "pull_request",
        f"Trusted guardian accepts only pull_request certification, got: {run.get('event')}",
    )

    original_paged = guardian.paged
    guardian.paged = lambda repo_arg, token_arg, path_arg: (
        paged(repo_arg, token_arg, path_arg, "jobs")
        if path_arg.endswith("/jobs") else
        paged(repo_arg, token_arg, path_arg, "artifacts")
        if path_arg.endswith("/artifacts") else
        paged(repo_arg, token_arg, path_arg)
    )
    try:
        return guardian.validate_triggered_run(root, repo, token, run_id, head)
    finally:
        guardian.paged = original_paged


def invalidate_all(repo: str, token: str, main_sha: str, target_url: str) -> dict[str, Any]:
    pulls = paged(repo, token, "/pulls?state=open&base=main")
    invalidated: list[dict[str, Any]] = []
    for pr in pulls:
        if pr.get("draft", False):
            continue
        head_sha = str(pr.get("head", {}).get("sha", ""))
        if not head_sha:
            continue
        guardian.publish_status(
            repo,
            token,
            head_sha,
            "failure",
            f"main advanced to {main_sha[:12]}; exact-head/base re-certification required",
            target_url,
        )
        invalidated.append({"pr": pr.get("number"), "head": head_sha})
    return {"status": "PASS", "mainSha": main_sha, "invalidated": invalidated}


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    validate = sub.add_parser("validate-run")
    validate.add_argument("--root", default=".")
    validate.add_argument("--repo", required=True)
    validate.add_argument("--token", required=True)
    validate.add_argument("--run-id", type=int, required=True)
    validate.add_argument("--head", required=True)
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
    args = parser.parse_args()
    try:
        if args.command == "validate-run":
            result = validate_run(Path(args.root).resolve(), args.repo, args.token, args.run_id, args.head)
        elif args.command == "invalidate-open-prs":
            result = invalidate_all(args.repo, args.token, args.main_sha, args.target_url)
        else:
            guardian.publish_status(args.repo, args.token, args.sha, args.state, args.description, args.target_url)
            result = {"status": "PASS", "sha": args.sha, "state": args.state, "context": guardian.GUARDIAN_CONTEXT}
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except Exception as exc:
        print(f"PHASE 10.9 TRUSTED GUARDIAN V2 FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
