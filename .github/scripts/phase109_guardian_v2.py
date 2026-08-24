#!/usr/bin/env python3
"""Corrected entry point for the trusted Phase 10.9 guardian.

The original guardian implementation remains as the shared trusted library. This entry point
hardens collection pagination, preserves both sides of PR renames, audits candidate workflows so
no untrusted workflow can forge the Guardian status, binds approval to the exact base recorded by
the completed certification run and provenance artifact, accepts only pull_request certification
for a merge-trust status, and invalidates every open main PR whenever main advances.
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
    i = 0
    while i < len(lines):
        line = lines[i]
        match = re.match(r"^(\s*)permissions\s*:\s*(.*?)\s*$", line)
        if not match:
            i += 1
            continue
        indent = len(match.group(1))
        rest = match.group(2).strip()
        if rest:
            if rest.startswith("{"):
                declarations.append((indent, parse_flow_permission_map(rest)))
            else:
                declarations.append((indent, scalar(rest).lower()))
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
            require(bool(key) and bool(item_value), f"Malformed block-style permissions entry: {entry}")
            require(key not in mapping, f"Duplicate permission key: {key}")
            mapping[key] = item_value
            i += 1
        declarations.append((indent, mapping))
    return declarations


def permission_writes(declaration: str | dict[str, str]) -> list[str]:
    if isinstance(declaration, str):
        return ["write-all"] if declaration == "write-all" else []
    return sorted(key for key, value in declaration.items() if value == "write")


def validate_read_only_declaration(declaration: str | dict[str, str], path: str) -> None:
    if isinstance(declaration, str):
        require(declaration in {"read-all"}, f"Changed workflow has unsafe permissions scalar in {path}: {declaration}")
        return
    unsafe = {key: value for key, value in declaration.items() if value not in {"read", "none"}}
    require(not unsafe, f"Changed workflow requests non-read-only permissions in {path}: {unsafe}")


def references_non_github_secret(text: str) -> bool:
    for expression in re.findall(r"\$\{\{(.*?)\}\}", text, flags=re.DOTALL):
        if not re.search(r"\bsecrets\b", expression):
            continue
        compact = re.sub(r"\s+", "", expression)
        allowed = {
            "secrets.GITHUB_TOKEN",
            "secrets['GITHUB_TOKEN']",
            'secrets["GITHUB_TOKEN"]',
        }
        refs = re.findall(r"secrets(?:\.[A-Za-z_][A-Za-z0-9_]*|\[['\"][^'\"]+['\"]\])", compact)
        if not refs or any(ref not in allowed for ref in refs):
            return True
    return False


def validate_candidate_legacy_workflows(repo: str, token: str, head: str, workflows: list[str]) -> int:
    """Validate candidate copies of every legacy workflow the trusted contract depends on."""
    marker_count = 0
    for path, rules in guardian.contract.BASE_RUN_RULES.items():
        require(path in workflows, f"Candidate deleted contract dependency workflow: {path}")
        source = fetch_candidate_text(repo, token, head, path)
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


def audit_candidate_workflow_privileges(repo: str, token: str, head: str, changed_paths: set[str]) -> dict[str, Any]:
    workflows = candidate_workflow_paths(repo, token, head)
    audited = 0
    changed_audited = 0
    for path in workflows:
        text = fetch_candidate_text(repo, token, head, path)
        declarations = parse_permission_declarations(text)

        if path != TRUSTED_GUARDIAN_WORKFLOW:
            writes = [item for _, declaration in declarations for item in permission_writes(declaration)]
            require("write-all" not in writes, f"Untrusted workflow requests write-all: {path}")
            require("statuses" not in writes, f"Untrusted workflow can forge commit statuses: {path}")
            require("checks" not in writes, f"Untrusted workflow can forge check runs: {path}")
        audited += 1

        if path in changed_paths and path != TRUSTED_GUARDIAN_WORKFLOW:
            top_level = [declaration for indent, declaration in declarations if indent == 0]
            require(len(top_level) == 1, f"Changed workflow must declare exactly one top-level permissions block: {path}")
            for _, declaration in declarations:
                validate_read_only_declaration(declaration, path)
            require(not references_non_github_secret(text), f"Changed workflow references a repository/environment secret: {path}")
            changed_audited += 1

    require(TRUSTED_GUARDIAN_WORKFLOW in workflows, "Trusted Guardian workflow is missing from candidate tree")
    legacy_markers = validate_candidate_legacy_workflows(repo, token, head, workflows)
    return {
        "workflowCount": len(workflows),
        "audited": audited,
        "changedAudited": changed_audited,
        "candidateLegacyExecutableMarkers": legacy_markers,
    }


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
            "User-Agent": "phase109-trusted-guardian",
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
            raise GuardianV2Error(f"Artifact download failed: HTTP {exc.code}: {body[:500]}") from exc
    require(bool(location), "Artifact download did not provide a signed redirect")
    signed = urllib.request.Request(str(location), headers={"User-Agent": "phase109-trusted-guardian"})
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
    require(
        provenance.get("baseSha") == audited_base_sha,
        f"Provenance artifact baseSha mismatch: artifact={provenance.get('baseSha')} run={audited_base_sha}",
    )
    return provenance


def validate_run(root: Path, repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    run = guardian.api_request(repo, token, "GET", f"/actions/runs/{run_id}")
    require(run.get("event") == "pull_request", f"Trusted guardian accepts only pull_request certification, got: {run.get('event')}")
    audited_base_sha = run_audited_base(run, head)

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
    finally:
        guardian.paged = original_paged
        guardian.changed_paths = original_changed_paths

    require(
        result.get("baseSha") == audited_base_sha,
        f"Live PR base differs from completed run base: live={result.get('baseSha')} run={audited_base_sha}",
    )
    require(
        result.get("liveMainSha") == audited_base_sha,
        f"main advanced after certification: live={result.get('liveMainSha')} run={audited_base_sha}",
    )
    provenance = read_provenance_artifact(repo, token, run_id, head, audited_base_sha)
    result["runAuditedBaseSha"] = audited_base_sha
    result["provenanceBaseSha"] = provenance.get("baseSha")
    result["candidateWorkflowPrivilegeAudit"] = privilege_audit
    return result


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
