#!/usr/bin/env python3
"""Trusted default-branch guardian for Phase 10.9 certification.

This script must run only from the default branch through `workflow_run`/`push`. It never checks
out or executes candidate code. Candidate workflow YAML is fetched as inert data and inspected by
the trusted contract that lives beside this file on main.
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

import phase109_trusted_contract as contract

REQUIRED_WORKFLOW_PATH = ".github/workflows/phase109-required-certification.yml"
GUARDIAN_CONTEXT = "Phase 10.9 Trusted Guardian"
IMMUTABLE_TRUST_PATHS = {
    REQUIRED_WORKFLOW_PATH,
    ".github/workflows/phase109-trusted-guardian.yml",
    ".github/scripts/phase109_guardian.py",
    ".github/scripts/phase109_trusted_contract.py",
    ".github/scripts/phase109_policy.py",
    ".github/scripts/phase107_emulator_gate.sh",
}


class GuardianError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardianError(message)


def api_request(repo: str, token: str, method: str, path: str, payload: dict[str, Any] | None = None) -> Any:
    url = f"https://api.github.com/repos/{repo}{path}"
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "phase109-trusted-guardian",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise GuardianError(f"GitHub API {method} {path} failed: HTTP {exc.code}: {body[:500]}") from exc


def paged(repo: str, token: str, path: str) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    page = 1
    separator = "&" if "?" in path else "?"
    while True:
        chunk = api_request(repo, token, "GET", f"{path}{separator}per_page=100&page={page}")
        require(isinstance(chunk, list), f"Expected list from paged endpoint: {path}")
        result.extend(chunk)
        if len(chunk) < 100:
            return result
        page += 1


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args], cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False
    )
    require(result.returncode == 0, f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout.strip()


def find_current_pr(repo: str, token: str, run: dict[str, Any], head_sha: str) -> dict[str, Any]:
    numbers = [item.get("number") for item in run.get("pull_requests", []) if item.get("number")]
    candidates: list[dict[str, Any]] = []
    for number in numbers:
        candidates.append(api_request(repo, token, "GET", f"/pulls/{number}"))
    if not candidates:
        candidates = api_request(repo, token, "GET", f"/commits/{head_sha}/pulls") or []
    matches = [
        pr for pr in candidates
        if pr.get("state") == "open"
        and pr.get("base", {}).get("ref") == "main"
        and pr.get("head", {}).get("sha") == head_sha
    ]
    require(len(matches) == 1, f"Expected exactly one open main PR for {head_sha}; got {len(matches)}")
    return matches[0]


def changed_paths(repo: str, token: str, pr_number: int) -> set[str]:
    files = paged(repo, token, f"/pulls/{pr_number}/files")
    return {str(item.get("filename", "")) for item in files if item.get("filename")}


def fetch_candidate_workflow(repo: str, token: str, head_sha: str) -> str:
    encoded_path = urllib.parse.quote(REQUIRED_WORKFLOW_PATH, safe="/")
    item = api_request(repo, token, "GET", f"/contents/{encoded_path}?ref={head_sha}")
    require(item.get("type") == "file", "Candidate required certification workflow is missing")
    require(item.get("encoding") == "base64", "Unexpected GitHub contents encoding")
    return base64.b64decode(item.get("content", "")).decode("utf-8")


def validate_triggered_run(root: Path, repo: str, token: str, run_id: int, expected_head: str) -> dict[str, Any]:
    run = api_request(repo, token, "GET", f"/actions/runs/{run_id}")
    require(run.get("name") == "Phase 10.9 Required Certification", f"Unexpected triggering workflow: {run.get('name')}")
    require(run.get("path") == REQUIRED_WORKFLOW_PATH, f"Unexpected triggering workflow path: {run.get('path')}")
    require(run.get("head_sha") == expected_head, "workflow_run head SHA does not match guardian target")
    require(run.get("event") in {"pull_request", "workflow_dispatch"}, f"Unexpected triggering event: {run.get('event')}")
    require(run.get("conclusion") == "success", f"Required certification run did not succeed: {run.get('conclusion')}")

    pr = find_current_pr(repo, token, run, expected_head)
    require(not pr.get("draft", False), "Guardian will not certify a draft PR")
    pr_number = int(pr["number"])
    base_sha = str(pr.get("base", {}).get("sha", ""))
    require(bool(base_sha), "PR base SHA is missing")

    live_main = api_request(repo, token, "GET", "/git/ref/heads/main")
    live_main_sha = str(live_main.get("object", {}).get("sha", ""))
    require(live_main_sha == base_sha, f"PR base is stale: audited={base_sha}, liveMain={live_main_sha}")

    paths = changed_paths(repo, token, pr_number)
    changed_trust = sorted(paths & IMMUTABLE_TRUST_PATHS)
    require(not changed_trust, f"Trusted CI kernel is immutable after Phase 10.9 bootstrap: {changed_trust}")

    candidate_workflow = fetch_candidate_workflow(repo, token, expected_head)
    require("continue-on-error" not in candidate_workflow, "Candidate required workflow contains continue-on-error")
    require("pull_request_target" not in candidate_workflow, "Candidate required workflow contains pull_request_target")

    git(root, "fetch", "--no-tags", "origin", base_sha)
    contract.validate_base_workflows(root, base_sha)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".yml", delete=False) as temp:
        temp.write(candidate_workflow)
        temp_path = Path(temp.name)
    try:
        structural = contract.validate_candidate_workflow(root, temp_path)
    finally:
        temp_path.unlink(missing_ok=True)

    jobs = paged(repo, token, f"/actions/runs/{run_id}/jobs")
    job_map = {str(job.get("name")): str(job.get("conclusion")) for job in jobs}
    require(job_map.get("Required Certification Gate") == "success", "Required Certification Gate is not successful")
    require(job_map.get("Policy, exact HEAD and scope") == "success", "Policy/exact-HEAD gate is not successful")

    artifacts = paged(repo, token, f"/actions/runs/{run_id}/artifacts")
    final_name = f"phase-10-9-required-certification-{expected_head}"
    final_artifacts = [a for a in artifacts if a.get("name") == final_name and int(a.get("size_in_bytes", 0)) > 0]
    require(len(final_artifacts) == 1, f"Expected one immutable final provenance artifact: {final_name}")

    return {
        "status": "PASS",
        "pr": pr_number,
        "candidateHead": expected_head,
        "baseSha": base_sha,
        "liveMainSha": live_main_sha,
        "requiredRunId": run_id,
        "changedFiles": len(paths),
        "structuralContract": structural,
        "provenanceArtifact": final_name,
    }


def publish_status(repo: str, token: str, sha: str, state: str, description: str, target_url: str = "") -> None:
    payload: dict[str, Any] = {
        "state": state,
        "context": GUARDIAN_CONTEXT,
        "description": description[:140],
    }
    if target_url:
        payload["target_url"] = target_url
    api_request(repo, token, "POST", f"/statuses/{sha}", payload)


def invalidate_open_prs(repo: str, token: str, main_sha: str, target_url: str = "") -> dict[str, Any]:
    pulls = paged(repo, token, "/pulls?state=open&base=main")
    invalidated: list[dict[str, Any]] = []
    for pr in pulls:
        if pr.get("draft", False):
            continue
        head_sha = str(pr.get("head", {}).get("sha", ""))
        base_sha = str(pr.get("base", {}).get("sha", ""))
        if not head_sha or base_sha == main_sha:
            continue
        publish_status(
            repo,
            token,
            head_sha,
            "failure",
            f"main advanced to {main_sha[:12]}; exact-head/base re-certification required",
            target_url,
        )
        invalidated.append({"pr": pr.get("number"), "head": head_sha, "oldBase": base_sha})
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
            result = validate_triggered_run(Path(args.root).resolve(), args.repo, args.token, args.run_id, args.head)
        elif args.command == "invalidate-open-prs":
            result = invalidate_open_prs(args.repo, args.token, args.main_sha, args.target_url)
        else:
            publish_status(args.repo, args.token, args.sha, args.state, args.description, args.target_url)
            result = {"status": "PASS", "sha": args.sha, "state": args.state, "context": GUARDIAN_CONTEXT}
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except (GuardianError, contract.ContractError, OSError, subprocess.SubprocessError, ValueError) as exc:
        print(f"PHASE 10.9 TRUSTED GUARDIAN FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
