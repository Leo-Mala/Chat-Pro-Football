#!/usr/bin/env python3
"""Trusted Phase 10.9 guardian v3.

Runs only from the trusted default branch. Candidate revisions are consumed only as API data.
Correctness is deliberately conservative: normal PRs cannot alter workflow definitions or the
trusted Phase 10.9 kernel, historical Room schema fixtures cannot be rewritten, Room versions
cannot move backwards, PR file enumeration must be complete, and every successful validation
first invalidates all open PR Guardian statuses so a lost main-push invalidator cannot leave a
stale green context behind.
"""
from __future__ import annotations

import argparse
import base64
import io
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path
from typing import Any

import phase109_guardian as guardian

SELF_PATH = ".github/scripts/phase109_guardian_v3.py"
WORKFLOW_PREFIX = ".github/workflows/"
ROOM_DATABASE_PATH = "app/src/main/java/com/example/data/database.kt"
ROOM_SCHEMA_PREFIX = "app/schemas/com.example.data.AppDatabase/"
TRUSTED_SCRIPT_PREFIX = ".github/scripts/phase109_"
TRUSTED_EXTRA_PATHS = {
    ".github/scripts/phase107_emulator_gate.sh",
}
MAX_PR_FILES = 3000

guardian.IMMUTABLE_TRUST_PATHS.add(SELF_PATH)
guardian.IMMUTABLE_TRUST_PATHS.update(guardian.contract.BASE_RUN_RULES.keys())


class GuardianV3Error(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardianV3Error(message)


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


def complete_pr_files(repo: str, token: str, pr: dict[str, Any]) -> list[dict[str, Any]]:
    declared = pr.get("changed_files")
    require(isinstance(declared, int) and declared >= 0, "PR changed_files count is unavailable")
    require(declared <= MAX_PR_FILES, f"PR has {declared} changed files; GitHub file API is only trustworthy through {MAX_PR_FILES}")
    pr_number = int(pr.get("number", 0))
    require(pr_number > 0, "PR number is missing")
    files = paged(repo, token, f"/pulls/{pr_number}/files")
    require(len(files) == declared, f"PR files response incomplete: declared={declared}, returned={len(files)}")
    return files


def paths_from_files(files: list[dict[str, Any]]) -> set[str]:
    paths: set[str] = set()
    for item in files:
        filename = str(item.get("filename", ""))
        previous = str(item.get("previous_filename", ""))
        if filename:
            paths.add(filename)
        if previous:
            paths.add(previous)
    return paths


def fetch_text(repo: str, token: str, ref: str, path: str) -> str:
    encoded = urllib.parse.quote(path, safe="/")
    item = guardian.api_request(repo, token, "GET", f"/contents/{encoded}?ref={ref}")
    require(isinstance(item, dict) and item.get("type") == "file", f"File is missing at {ref}: {path}")
    require(item.get("encoding") == "base64", f"Unexpected contents encoding for {path}")
    try:
        return base64.b64decode(str(item.get("content", ""))).decode("utf-8")
    except Exception as exc:
        raise GuardianV3Error(f"Cannot decode UTF-8 file {path} at {ref}: {exc}") from exc


def recursive_tree(repo: str, token: str, ref: str) -> dict[str, str]:
    payload = guardian.api_request(repo, token, "GET", f"/git/trees/{ref}?recursive=1")
    require(isinstance(payload, dict), f"Tree payload missing for {ref}")
    require(not payload.get("truncated", False), f"Recursive tree is truncated for {ref}")
    entries = payload.get("tree")
    require(isinstance(entries, list), f"Recursive tree entries missing for {ref}")
    result: dict[str, str] = {}
    for item in entries:
        if item.get("type") != "blob":
            continue
        path = str(item.get("path", ""))
        sha = str(item.get("sha", ""))
        if path and sha:
            result[path] = sha
    return result


def workflow_blobs(tree: dict[str, str]) -> dict[str, str]:
    return {
        path: sha for path, sha in tree.items()
        if path.startswith(WORKFLOW_PREFIX) and path.lower().endswith((".yml", ".yaml"))
    }


def validate_workflow_immutability(base_tree: dict[str, str], candidate_tree: dict[str, str]) -> int:
    base = workflow_blobs(base_tree)
    candidate = workflow_blobs(candidate_tree)
    require(candidate == base, "Normal PRs may not add, delete, rename, or modify trusted workflow definitions")
    return len(candidate)


def validate_trusted_kernel_paths(paths: set[str]) -> None:
    forbidden = sorted(
        path for path in paths
        if path.startswith(TRUSTED_SCRIPT_PREFIX) or path in TRUSTED_EXTRA_PATHS
    )
    require(not forbidden, f"Normal PR attempted to modify trusted CI kernel paths: {forbidden}")


def without_yaml_comments(text: str) -> str:
    cleaned: list[str] = []
    for line in text.splitlines():
        quote: str | None = None
        escaped = False
        kept: list[str] = []
        for char in line:
            if escaped:
                kept.append(char)
                escaped = False
                continue
            if char == "\\" and quote == '"':
                kept.append(char)
                escaped = True
                continue
            if char in {"'", '"'}:
                if quote is None:
                    quote = char
                elif quote == char:
                    quote = None
                kept.append(char)
                continue
            if char == "#" and quote is None:
                break
            kept.append(char)
        cleaned.append("".join(kept))
    return "\n".join(cleaned)


def scalar(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        value = value[1:-1]
    return value.strip()


def parse_flow_permission_map(value: str) -> dict[str, str]:
    body = value.strip()
    require(body.startswith("{") and body.endswith("}"), f"Malformed flow-style permissions map: {value}")
    body = body[1:-1].strip()
    if not body:
        return {}
    result: dict[str, str] = {}
    for part in body.split(","):
        require(":" in part, f"Malformed flow-style permission entry: {part}")
        key, item_value = part.split(":", 1)
        key = scalar(key).lower()
        item_value = scalar(item_value).lower()
        require(bool(key) and bool(item_value), f"Malformed flow-style permission entry: {part}")
        require(key not in result, f"Duplicate permission key: {key}")
        result[key] = item_value
    return result


def parse_permission_declarations(text: str) -> list[tuple[int, str | dict[str, str]]]:
    clean = without_yaml_comments(text)
    lines = clean.splitlines()
    declarations: list[tuple[int, str | dict[str, str]]] = []
    key_pattern = r"(?:permissions|'permissions'|\"permissions\")"
    i = 0
    while i < len(lines):
        line = lines[i]
        match = re.match(rf"^(\s*){key_pattern}\s*:\s*(.*?)\s*$", line)
        if not match:
            i += 1
            continue
        indent = len(match.group(1))
        rest = match.group(2).strip()
        if rest:
            declarations.append((indent, parse_flow_permission_map(rest) if rest.startswith("{") else scalar(rest).lower()))
            i += 1
            continue
        mapping: dict[str, str] = {}
        i += 1
        while i < len(lines):
            child = lines[i]
            if not child.strip():
                i += 1
                continue
            child_indent = len(child) - len(child.lstrip(" "))
            if child_indent <= indent:
                break
            entry = child.strip()
            require(":" in entry, f"Malformed block-style permissions entry: {entry}")
            key, item_value = entry.split(":", 1)
            key = scalar(key).lower()
            item_value = scalar(item_value).lower()
            require(bool(key) and bool(item_value), f"Malformed block-style permission entry: {entry}")
            require(key not in mapping, f"Duplicate permission key: {key}")
            mapping[key] = item_value
            i += 1
        declarations.append((indent, mapping))
    return declarations


def audit_trusted_workflow_permissions(repo: str, token: str, head: str, tree: dict[str, str]) -> int:
    count = 0
    for path in sorted(workflow_blobs(tree)):
        text = fetch_text(repo, token, head, path)
        declarations = parse_permission_declarations(text)
        for _, declaration in declarations:
            if isinstance(declaration, str):
                require(declaration != "write-all", f"Workflow requests write-all: {path}")
                continue
            writes = {key for key, value in declaration.items() if value == "write"}
            if path != ".github/workflows/phase109-trusted-guardian.yml":
                require("statuses" not in writes and "checks" not in writes, f"Non-Guardian workflow can forge status/check context: {path}")
        count += 1
    return count


def parse_room_version(text: str) -> int:
    match = re.search(r"\bAPP_DATABASE_SCHEMA_VERSION\s*=\s*(\d+)\b", text)
    require(match is not None, "APP_DATABASE_SCHEMA_VERSION is missing")
    return int(match.group(1))


def validate_room_history(repo: str, token: str, base_sha: str, head: str, base_tree: dict[str, str], candidate_tree: dict[str, str]) -> dict[str, Any]:
    base_version = parse_room_version(fetch_text(repo, token, base_sha, ROOM_DATABASE_PATH))
    candidate_version = parse_room_version(fetch_text(repo, token, head, ROOM_DATABASE_PATH))
    require(candidate_version >= base_version, f"Room schema version decreased: base={base_version}, candidate={candidate_version}")

    historical = 0
    pattern = re.compile(rf"^{re.escape(ROOM_SCHEMA_PREFIX)}(\d+)\.json$")
    for path, base_blob in base_tree.items():
        match = pattern.match(path)
        if not match:
            continue
        version = int(match.group(1))
        if version > base_version:
            continue
        require(path in candidate_tree, f"Historical Room schema fixture deleted: {path}")
        require(candidate_tree[path] == base_blob, f"Historical Room schema fixture modified: {path}")
        historical += 1
    require(historical > 0, "No historical Room schema fixtures found in trusted base")
    return {"baseVersion": base_version, "candidateVersion": candidate_version, "immutableHistoricalSchemas": historical}


def validate_candidate_legacy_workflows(repo: str, token: str, head: str, workflows: dict[str, str]) -> int:
    marker_count = 0
    for path, rules in guardian.contract.BASE_RUN_RULES.items():
        require(path in workflows, f"Candidate deleted contract dependency workflow: {path}")
        source = fetch_text(repo, token, head, path)
        steps = guardian.contract.parse_steps(source)
        commands = [cmd for step in steps for cmd in guardian.contract.logical_commands(step.run)]
        for executable, markers in rules:
            for marker in markers:
                require(
                    any(marker in cmd and guardian.contract.command_is_executable(cmd, executable) for cmd in commands),
                    f"Candidate legacy workflow no longer proves executable command in {path}: {marker}",
                )
                marker_count += 1
    return marker_count


def run_audited_base(run: dict[str, Any], head: str) -> str:
    matches = [
        item for item in run.get("pull_requests", [])
        if item.get("head", {}).get("sha") == head and item.get("base", {}).get("ref") == "main"
    ]
    require(len(matches) == 1, f"Expected exactly one run-bound PR snapshot for {head}; got {len(matches)}")
    base_sha = str(matches[0].get("base", {}).get("sha", ""))
    require(bool(base_sha), "Completed certification run has no audited base SHA")
    return base_sha


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # type: ignore[override]
        return None


def download_artifact_zip(repo: str, token: str, artifact_id: int) -> bytes:
    api_url = f"https://api.github.com/repos/{repo}/actions/artifacts/{artifact_id}/zip"
    request = urllib.request.Request(
        api_url,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "phase109-trusted-guardian-v3",
        },
    )
    opener = urllib.request.build_opener(NoRedirect)
    location: str | None = None
    try:
        with opener.open(request, timeout=30) as response:
            if 200 <= response.status < 300:
                return response.read()
    except urllib.error.HTTPError as exc:
        if exc.code in {301, 302, 303, 307, 308}:
            location = exc.headers.get("Location")
        else:
            body = exc.read().decode("utf-8", errors="replace")
            raise GuardianV3Error(f"Artifact download failed: HTTP {exc.code}: {body[:500]}") from exc
    require(bool(location), "Artifact download did not provide a signed redirect")
    signed = urllib.request.Request(str(location), headers={"User-Agent": "phase109-trusted-guardian-v3"})
    with urllib.request.urlopen(signed, timeout=60) as response:
        require(200 <= response.status < 300, f"Signed artifact download failed: HTTP {response.status}")
        return response.read()


def read_provenance_artifact(repo: str, token: str, run_id: int, head: str, audited_base_sha: str) -> dict[str, Any]:
    artifacts = paged(repo, token, f"/actions/runs/{run_id}/artifacts", "artifacts")
    final_name = f"phase-10-9-required-certification-{head}"
    matches = [
        artifact for artifact in artifacts
        if artifact.get("name") == final_name
        and not artifact.get("expired", False)
        and int(artifact.get("size_in_bytes", 0)) > 0
    ]
    require(len(matches) == 1, f"Expected one live provenance artifact: {final_name}")
    artifact_id = int(matches[0].get("id", 0))
    require(artifact_id > 0, "Provenance artifact id is missing")
    archive = download_artifact_zip(repo, token, artifact_id)
    with zipfile.ZipFile(io.BytesIO(archive)) as bundle:
        names = [name for name in bundle.namelist() if Path(name).name == "phase109-required-certification.json"]
        require(len(names) == 1, f"Expected one provenance JSON in artifact; got {names}")
        provenance = json.loads(bundle.read(names[0]).decode("utf-8"))
    require(provenance.get("status") == "CERTIFIED", "Provenance artifact is not CERTIFIED")
    require(provenance.get("auditHead") == head, "Provenance artifact auditHead does not match run HEAD")
    require(provenance.get("baseSha") == audited_base_sha, "Provenance artifact baseSha does not match run-audited base")
    return provenance


def invalidate_all(repo: str, token: str, main_sha: str, target_url: str) -> dict[str, Any]:
    pulls = paged(repo, token, "/pulls?state=open&base=main")
    invalidated: list[dict[str, Any]] = []
    for pr in pulls:
        head_sha = str(pr.get("head", {}).get("sha", ""))
        if not head_sha:
            continue
        guardian.publish_status(
            repo,
            token,
            head_sha,
            "failure",
            f"main/trust sweep at {main_sha[:12]}; exact-head/base re-certification required",
            target_url,
        )
        invalidated.append({"pr": pr.get("number"), "head": head_sha, "draft": bool(pr.get("draft", False))})
    return {"status": "PASS", "mainSha": main_sha, "invalidated": invalidated}


def validate_run(root: Path, repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    run = guardian.api_request(repo, token, "GET", f"/actions/runs/{run_id}")
    require(run.get("event") == "pull_request", f"Trusted Guardian accepts only pull_request certification, got: {run.get('event')}")
    require(run.get("head_sha") == head, "workflow_run head does not match requested candidate HEAD")
    audited_base_sha = run_audited_base(run, head)

    pr = guardian.find_current_pr(repo, token, run, head)
    files = complete_pr_files(repo, token, pr)
    paths = paths_from_files(files)
    validate_trusted_kernel_paths(paths)

    base_tree = recursive_tree(repo, token, audited_base_sha)
    candidate_tree = recursive_tree(repo, token, head)
    workflow_count = validate_workflow_immutability(base_tree, candidate_tree)
    permission_audit = audit_trusted_workflow_permissions(repo, token, head, candidate_tree)
    legacy_markers = validate_candidate_legacy_workflows(repo, token, head, workflow_blobs(candidate_tree))
    room_audit = validate_room_history(repo, token, audited_base_sha, head, base_tree, candidate_tree)

    original_paged = guardian.paged
    original_changed_paths = guardian.changed_paths
    guardian.paged = lambda repo_arg, token_arg, path_arg: (
        paged(repo_arg, token_arg, path_arg, "jobs")
        if path_arg.endswith("/jobs") else
        paged(repo_arg, token_arg, path_arg, "artifacts")
        if path_arg.endswith("/artifacts") else
        paged(repo_arg, token_arg, path_arg)
    )
    guardian.changed_paths = lambda repo_arg, token_arg, pr_number: set(paths)
    try:
        result = guardian.validate_triggered_run(root, repo, token, run_id, head)
    finally:
        guardian.paged = original_paged
        guardian.changed_paths = original_changed_paths

    require(result.get("baseSha") == audited_base_sha, "Live PR base differs from completed run base")
    require(result.get("liveMainSha") == audited_base_sha, "main advanced after completed certification")
    provenance = read_provenance_artifact(repo, token, run_id, head, audited_base_sha)

    # Correctness does not depend on the push invalidator surviving GitHub concurrency queues.
    # Every successful validation first fails the Guardian context on every open PR (including
    # drafts). The workflow then publishes success only for the single HEAD validated here.
    sweep = invalidate_all(repo, token, audited_base_sha, str(run.get("html_url", "")))

    result["runAuditedBaseSha"] = audited_base_sha
    result["provenanceBaseSha"] = provenance.get("baseSha")
    result["workflowImmutability"] = {"trustedWorkflowCount": workflow_count, "permissionAudited": permission_audit}
    result["candidateLegacyExecutableMarkers"] = legacy_markers
    result["roomHistory"] = room_audit
    result["staleStatusSweep"] = sweep
    result["completePrFiles"] = len(files)
    return result


def self_test() -> dict[str, Any]:
    quoted = parse_permission_declarations('"permissions": {statuses: write, contents: read}\n')
    require(quoted == [(0, {"statuses": "write", "contents": "read"})], "quoted permissions key was not parsed")
    block = parse_permission_declarations("permissions:\n  contents: read\n  statuses: none\n")
    require(block == [(0, {"contents": "read", "statuses": "none"})], "block permissions were not parsed")
    require(parse_room_version("const val APP_DATABASE_SCHEMA_VERSION = 22") == 22, "Room version parser failed")
    base = {".github/workflows/a.yml": "1"}
    validate_workflow_immutability(base, dict(base))
    try:
        validate_workflow_immutability(base, {".github/workflows/a.yml": "2"})
    except GuardianV3Error:
        pass
    else:
        raise GuardianV3Error("workflow mutation negative test did not fail")
    return {"status": "PASS", "negativeWorkflowMutationRejected": True, "quotedPermissionsParsed": True, "roomVersionParsed": True}


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
    sub.add_parser("self-test")
    args = parser.parse_args()
    try:
        if args.command == "validate-run":
            result = validate_run(Path(args.root).resolve(), args.repo, args.token, args.run_id, args.head)
        elif args.command == "invalidate-open-prs":
            result = invalidate_all(args.repo, args.token, args.main_sha, args.target_url)
        elif args.command == "publish-status":
            guardian.publish_status(args.repo, args.token, args.sha, args.state, args.description, args.target_url)
            result = {"status": "PASS", "sha": args.sha, "state": args.state, "context": guardian.GUARDIAN_CONTEXT}
        else:
            result = self_test()
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except Exception as exc:
        print(f"PHASE 10.9 TRUSTED GUARDIAN V3 FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
