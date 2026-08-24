#!/usr/bin/env python3
"""Corrected entry point for the trusted Phase 10.9 guardian.

The original guardian implementation remains as the shared trusted library. This entry point
hardens collection pagination, preserves both sides of PR renames, audits candidate workflows so
no untrusted workflow can forge the Guardian status, accepts only pull_request certification for
a merge-trust status, and invalidates every open main PR whenever main advances.
"""
from __future__ import annotations

import argparse
import base64
import json
import re
import sys
import urllib.parse
from pathlib import Path
from typing import Any

import phase109_guardian as guardian

SELF_PATH = ".github/scripts/phase109_guardian_v2.py"
TRUSTED_GUARDIAN_WORKFLOW = ".github/workflows/phase109-trusted-guardian.yml"
guardian.IMMUTABLE_TRUST_PATHS.add(SELF_PATH)


class GuardianV2Error(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardianV2Error(message)


def paged(repo: str, token: str, path: str, collection_key: str | None = None) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    page = 1
    separator = "&" if "?" in path else "?"
    while True:
        payload = guardian.api_request(repo, token, "GET", f"{path}{separator}per_page=100&page={page}")
        if collection_key is None:
            require(isinstance(payload, list), f"Expected list from paged endpoint: {path}")
            chunk = payload
        else:
            require(isinstance(payload, dict), f"Expected object from paged endpoint: {path}")
            chunk = payload.get(collection_key)
            require(isinstance(chunk, list), f"Expected collection '{collection_key}' from: {path}")
        result.extend(chunk)
        if len(chunk) < 100:
            return result
        page += 1


def pr_files(repo: str, token: str, pr_number: int) -> list[dict[str, Any]]:
    return paged(repo, token, f"/pulls/{pr_number}/files")


def changed_paths_with_previous(repo: str, token: str, pr_number: int) -> set[str]:
    """Return every current and previous path so rename-away cannot evade trust-kernel checks."""
    paths: set[str] = set()
    for item in pr_files(repo, token, pr_number):
        filename = str(item.get("filename", ""))
        previous = str(item.get("previous_filename", ""))
        if filename:
            paths.add(filename)
        if previous:
            paths.add(previous)
    return paths


def candidate_workflow_paths(repo: str, token: str, head: str) -> list[str]:
    tree = guardian.api_request(repo, token, "GET", f"/git/trees/{head}?recursive=1")
    require(isinstance(tree, dict) and not tree.get("truncated", False), "Candidate workflow tree is missing or truncated")
    entries = tree.get("tree")
    require(isinstance(entries, list), "Candidate workflow tree has no entries")
    return sorted(
        str(item.get("path"))
        for item in entries
        if item.get("type") == "blob"
        and str(item.get("path", "")).startswith(".github/workflows/")
        and str(item.get("path", "")).lower().endswith((".yml", ".yaml"))
    )


def fetch_candidate_text(repo: str, token: str, head: str, path: str) -> str:
    encoded = urllib.parse.quote(path, safe="/")
    item = guardian.api_request(repo, token, "GET", f"/contents/{encoded}?ref={head}")
    require(isinstance(item, dict) and item.get("type") == "file", f"Candidate file is missing: {path}")
    require(item.get("encoding") == "base64", f"Unexpected contents encoding for {path}")
    return base64.b64decode(str(item.get("content", ""))).decode("utf-8")


def without_yaml_comments(text: str) -> str:
    cleaned: list[str] = []
    for line in text.splitlines():
        # Permission declarations and secret expressions are YAML, never expected after a shell '#'
        # in a permission key. Removing trailing comments avoids accepting commented-out safeguards.
        cleaned.append(line.split("#", 1)[0])
    return "\n".join(cleaned)


def audit_candidate_workflow_privileges(
    repo: str,
    token: str,
    head: str,
    changed_paths: set[str],
) -> dict[str, Any]:
    workflows = candidate_workflow_paths(repo, token, head)
    audited = 0
    changed_audited = 0
    for path in workflows:
        text = fetch_candidate_text(repo, token, head, path)
        clean = without_yaml_comments(text)
        normalized = clean.replace("'", "").replace('"', "")
        if path != TRUSTED_GUARDIAN_WORKFLOW:
            require(
                re.search(r"(?mi)^\s*permissions\s*:\s*write-all\s*$", normalized) is None,
                f"Untrusted workflow requests write-all: {path}",
            )
            require(
                re.search(r"(?mi)^\s*statuses\s*:\s*write\s*$", normalized) is None,
                f"Untrusted workflow can forge commit statuses: {path}",
            )
            require(
                re.search(r"(?mi)^\s*checks\s*:\s*write\s*$", normalized) is None,
                f"Untrusted workflow can forge check runs: {path}",
            )
        audited += 1

        if path in changed_paths and path != TRUSTED_GUARDIAN_WORKFLOW:
            # Any workflow a PR changes must stop inheriting repository-wide token defaults.
            require(
                re.search(r"(?m)^permissions\s*:", clean) is not None,
                f"Changed workflow must declare top-level permissions explicitly: {path}",
            )
            # PR-authored workflow changes are read-only. A future privileged workflow change must
            # go through the separately governed trusted-kernel/admin path, never self-certify.
            write_scope = re.search(r"(?mi)^\s*[A-Za-z0-9_-]+\s*:\s*write\s*$", normalized)
            require(write_scope is None, f"Changed workflow requests a write permission: {path}: {write_scope.group(0).strip() if write_scope else ''}")
            require(
                re.search(r"\$\{\{\s*secrets\.(?!GITHUB_TOKEN\b)", clean) is None,
                f"Changed workflow references a repository/environment secret: {path}",
            )
            changed_audited += 1

    require(TRUSTED_GUARDIAN_WORKFLOW in workflows, "Trusted Guardian workflow is missing from candidate tree")
    return {"workflowCount": len(workflows), "audited": audited, "changedAudited": changed_audited}


def validate_run(root: Path, repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    run = guardian.api_request(repo, token, "GET", f"/actions/runs/{run_id}")
    require(
        run.get("event") == "pull_request",
        f"Trusted guardian accepts only pull_request certification, got: {run.get('event')}",
    )

    original_paged = guardian.paged
    original_changed_paths = guardian.changed_paths
    guardian.paged = lambda repo_arg, token_arg, path_arg: (
        paged(repo_arg, token_arg, path_arg, "jobs")
        if path_arg.endswith("/jobs") else
        paged(repo_arg, token_arg, path_arg, "artifacts")
        if path_arg.endswith("/artifacts") else
        paged(repo_arg, token_arg, path_arg)
    )
    guardian.changed_paths = changed_paths_with_previous
    try:
        pr = guardian.find_current_pr(repo, token, run, head)
        paths = changed_paths_with_previous(repo, token, int(pr["number"]))
        privilege_audit = audit_candidate_workflow_privileges(repo, token, head, paths)
        result = guardian.validate_triggered_run(root, repo, token, run_id, head)
        result["candidateWorkflowPrivilegeAudit"] = privilege_audit
        return result
    finally:
        guardian.paged = original_paged
        guardian.changed_paths = original_changed_paths


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
