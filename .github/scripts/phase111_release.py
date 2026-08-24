#!/usr/bin/env python3
"""Phase 11.1 release-engineering helpers.

Standard-library only. Generates deterministic release metadata/SBOM from the exact checkout
and verifies the trusted Phase 10.9 certification evidence before a production release.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

REQUIRED_CERTIFICATION_NAME = "Phase 10.9 Required Certification"
TRUSTED_GUARDIAN_NAME = "Phase 10.9 Trusted Guardian"
EXPECTED_APPLICATION_ID = "com.aistudio.brasfutretro.djuxzt"
EXPECTED_VERSION_CODE = 31
EXPECTED_VERSION_NAME = "3.0.0"
EXPECTED_MIN_SDK = 24
EXPECTED_TARGET_SDK = 35
EXPECTED_COMPILE_SDK = 35


class ReleaseError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ReleaseError(message)


def git_text(root: Path, *args: str) -> str:
    completed = subprocess.run(
        ["git", *args],
        cwd=root,
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    require(completed.returncode == 0, f"git {' '.join(args)} failed: {completed.stderr.strip()}")
    return completed.stdout.strip()


def file_text(root: Path, relative: str) -> str:
    path = root / relative
    require(path.is_file(), f"Required file missing: {relative}")
    return path.read_text(encoding="utf-8")


def first_match(pattern: str, text: str, label: str) -> str:
    match = re.search(pattern, text, re.MULTILINE)
    require(match is not None, f"Could not resolve {label}")
    return match.group(1)


def project_metadata(root: Path) -> dict[str, Any]:
    app_gradle = file_text(root, "app/build.gradle.kts")
    versions = file_text(root, "gradle/libs.versions.toml")
    wrapper = file_text(root, "gradle/wrapper/gradle-wrapper.properties")

    version_code = int(first_match(r"\bversionCode\s*=\s*(\d+)", app_gradle, "versionCode"))
    version_name = first_match(r'\bversionName\s*=\s*"([^"]+)"', app_gradle, "versionName")
    application_id = first_match(r'\bapplicationId\s*=\s*"([^"]+)"', app_gradle, "applicationId")
    min_sdk = int(first_match(r"\bminSdk\s*=\s*(\d+)", app_gradle, "minSdk"))
    target_sdk = int(first_match(r"\btargetSdk\s*=\s*(\d+)", app_gradle, "targetSdk"))
    compile_sdk = int(first_match(r"\bcompileSdk\s*=\s*(\d+)", app_gradle, "compileSdk"))
    agp = first_match(r'(?m)^agp\s*=\s*"([^"]+)"', versions, "AGP version")
    kotlin = first_match(r'(?m)^kotlin\s*=\s*"([^"]+)"', versions, "Kotlin version")
    gradle = first_match(r"gradle-([0-9][0-9A-Za-z.\-]*)-bin\.zip", wrapper, "Gradle version")
    minified = bool(re.search(r"\bisMinifyEnabled\s*=\s*true", app_gradle))
    shrink_resources_match = re.search(r"\bisShrinkResources\s*=\s*(true|false)", app_gradle)
    shrink_resources = shrink_resources_match.group(1) == "true" if shrink_resources_match else False

    return {
        "applicationId": application_id,
        "versionCode": version_code,
        "versionName": version_name,
        "minSdk": min_sdk,
        "targetSdk": target_sdk,
        "compileSdk": compile_sdk,
        "gradle": gradle,
        "agp": agp,
        "kotlin": kotlin,
        "r8Minification": minified,
        "resourceShrinking": shrink_resources,
    }


def verify_project(root: Path) -> dict[str, Any]:
    metadata = project_metadata(root)
    expected = {
        "applicationId": EXPECTED_APPLICATION_ID,
        "versionCode": EXPECTED_VERSION_CODE,
        "versionName": EXPECTED_VERSION_NAME,
        "minSdk": EXPECTED_MIN_SDK,
        "targetSdk": EXPECTED_TARGET_SDK,
        "compileSdk": EXPECTED_COMPILE_SDK,
        "r8Minification": True,
        "resourceShrinking": False,
    }
    for key, value in expected.items():
        require(metadata.get(key) == value, f"{key}: expected {value!r}, got {metadata.get(key)!r}")

    gradle = file_text(root, "app/build.gradle.kts")
    require("requireProductionSigning" in gradle, "Fail-closed production-signing gate is missing")
    require('System.getenv("KEYSTORE_PATH")' in gradle, "Release keystore path is not environment-backed")
    require('System.getenv("STORE_PASSWORD")' in gradle, "Release store password is not environment-backed")
    require('System.getenv("KEY_ALIAS")' in gradle, "Release key alias is not environment-backed")
    require('System.getenv("KEY_PASSWORD")' in gradle, "Release key password is not environment-backed")
    require(
        "fallbackToDestructiveMigration" not in "\n".join(
            p.read_text(encoding="utf-8", errors="replace") for p in (root / "app/src/main").rglob("*.kt")
        ),
        "fallbackToDestructiveMigration is forbidden",
    )
    return metadata


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def normalize_fingerprint(value: str) -> str:
    return re.sub(r"[^0-9A-Fa-f]", "", value).upper()


def generate_sbom(root: Path, dependency_report: Path, output: Path) -> dict[str, Any]:
    metadata = project_metadata(root)
    require(dependency_report.is_file(), f"Dependency report missing: {dependency_report}")
    text = dependency_report.read_text(encoding="utf-8", errors="replace")
    pattern = re.compile(
        r"(?:\+---|\\---)\s+([^:\s]+):([^:\s]+):([^\s]+?)(?:\s+->\s+([^\s]+))?(?:\s|$)"
    )
    components: dict[tuple[str, str, str], dict[str, Any]] = {}
    for match in pattern.finditer(text):
        group, name, requested, resolved = match.groups()
        version = (resolved or requested).rstrip("(*)")
        if version in {"project", "unspecified"} or version.startswith("{"):
            continue
        key = (group, name, version)
        components[key] = {
            "type": "library",
            "group": group,
            "name": name,
            "version": version,
            "purl": f"pkg:maven/{urllib.parse.quote(group, safe='.')}/{urllib.parse.quote(name, safe='._-')}@{urllib.parse.quote(version, safe='._-+')}",
        }

    bom = {
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "version": 1,
        "metadata": {
            "component": {
                "type": "application",
                "name": "Pro Football",
                "version": metadata["versionName"],
                "properties": [
                    {"name": "android:applicationId", "value": metadata["applicationId"]},
                    {"name": "android:versionCode", "value": str(metadata["versionCode"])},
                ],
            }
        },
        "components": [components[key] for key in sorted(components)],
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(bom, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    require(bool(components), "SBOM parser found no resolved Maven components")
    return {"components": len(components), "sha256": sha256(output)}


def github_json(url: str, token: str) -> dict[str, Any]:
    request = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "phase-11-1-release-engineering",
        },
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def select_successful_run(
    runs: list[dict[str, Any]],
    name: str,
    sha: str,
    *,
    event: str | None = None,
    branch: str | None = None,
) -> dict[str, Any]:
    matches = []
    for run in runs:
        if run.get("name") != name:
            continue
        if run.get("head_sha") != sha or run.get("status") != "completed" or run.get("conclusion") != "success":
            continue
        if event is not None and run.get("event") != event:
            continue
        if branch is not None and run.get("head_branch") != branch:
            continue
        matches.append(run)
    require(matches, f"No successful {name!r} run found for exact SHA {sha}")
    matches.sort(key=lambda item: int(item.get("id", 0)), reverse=True)
    return matches[0]


def certification_evidence(sha: str, output: Path) -> dict[str, Any]:
    token = os.environ.get("GITHUB_TOKEN", "")
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    api_url = os.environ.get("GITHUB_API_URL", "https://api.github.com").rstrip("/")
    require(token, "GITHUB_TOKEN is required to verify certification evidence")
    require(repository and "/" in repository, "GITHUB_REPOSITORY is missing")
    query = urllib.parse.urlencode({"head_sha": sha, "status": "completed", "per_page": 100})
    payload = github_json(f"{api_url}/repos/{repository}/actions/runs?{query}", token)
    runs = payload.get("workflow_runs", [])
    require(isinstance(runs, list), "GitHub Actions runs response is malformed")

    required = select_successful_run(runs, REQUIRED_CERTIFICATION_NAME, sha, event="push", branch="main")
    guardian = select_successful_run(runs, TRUSTED_GUARDIAN_NAME, sha, event="workflow_run", branch="main")
    required_jobs_payload = github_json(f"{required['jobs_url']}?per_page=100", token)
    guardian_jobs_payload = github_json(f"{guardian['jobs_url']}?per_page=100", token)

    def summarize_jobs(payload: dict[str, Any]) -> list[dict[str, Any]]:
        jobs = payload.get("jobs", [])
        require(isinstance(jobs, list), "GitHub Actions jobs response is malformed")
        return [
            {
                "name": job.get("name"),
                "conclusion": job.get("conclusion"),
                "startedAt": job.get("started_at"),
                "completedAt": job.get("completed_at"),
            }
            for job in jobs
        ]

    evidence = {
        "auditHead": sha,
        "requiredCertification": {
            "name": required["name"],
            "runId": required["id"],
            "runNumber": required.get("run_number"),
            "event": required.get("event"),
            "conclusion": required.get("conclusion"),
            "htmlUrl": required.get("html_url"),
            "jobs": summarize_jobs(required_jobs_payload),
        },
        "trustedGuardian": {
            "name": guardian["name"],
            "runId": guardian["id"],
            "runNumber": guardian.get("run_number"),
            "event": guardian.get("event"),
            "conclusion": guardian.get("conclusion"),
            "htmlUrl": guardian.get("html_url"),
            "jobs": summarize_jobs(guardian_jobs_payload),
        },
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return evidence


def parse_artifacts(values: list[str]) -> dict[str, Path]:
    result: dict[str, Path] = {}
    for value in values:
        require("=" in value, f"Artifact must be NAME=PATH: {value}")
        name, raw_path = value.split("=", 1)
        name = name.strip()
        path = Path(raw_path)
        require(name and name not in result, f"Invalid or duplicate artifact name: {name!r}")
        require(path.is_file(), f"Artifact file missing: {path}")
        require(path.stat().st_size > 0, f"Artifact file is empty: {path}")
        result[name] = path
    require(result, "At least one artifact is required")
    return result


def manifest(
    root: Path,
    mode: str,
    output: Path,
    artifact_values: list[str],
    signing_cert_sha256: str,
    evidence_path: Path,
    release_ref: str,
    workflow_run_id: str,
    workflow_run_attempt: str,
    jdk_version: str,
) -> dict[str, Any]:
    require(mode in {"validation", "production"}, f"Unsupported mode: {mode}")
    metadata = verify_project(root)
    artifacts = parse_artifacts(artifact_values)
    evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
    commit_sha = git_text(root, "rev-parse", "HEAD^{commit}")
    tree_sha = git_text(root, "rev-parse", "HEAD^{tree}")
    commit_timestamp = git_text(root, "show", "-s", "--format=%cI", commit_sha)

    fingerprint = normalize_fingerprint(signing_cert_sha256)
    require(len(fingerprint) == 64, "Signing certificate SHA-256 fingerprint must contain 64 hex digits")

    artifact_entries = {}
    for name, path in sorted(artifacts.items()):
        artifact_entries[name] = {
            "fileName": path.name,
            "sizeBytes": path.stat().st_size,
            "sha256": sha256(path),
        }

    release = {
        "schemaVersion": 1,
        "product": "Pro Football",
        "classification": "PRODUCTION_SIGNED" if mode == "production" else "PRODUCTION_READY_VALIDATION_SIGNED",
        "storeDistributionStatus": "READY" if mode == "production" else "UNSIGNED_FOR_STORE_PRODUCTION_KEY",
        "commitSha": commit_sha,
        "treeSha": tree_sha,
        "sourceTimestamp": commit_timestamp,
        "releaseRef": release_ref,
        "version": metadata,
        "jdk": jdk_version,
        "workflow": {
            "runId": str(workflow_run_id),
            "runAttempt": str(workflow_run_attempt),
            "repository": os.environ.get("GITHUB_REPOSITORY", ""),
        },
        "certification": evidence,
        "signing": {
            "mode": "PRODUCTION_CONTROLLED" if mode == "production" else "EPHEMERAL_VALIDATION_ONLY",
            "certificateSha256": fingerprint,
        },
        "artifacts": artifact_entries,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(release, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return release


def self_test() -> None:
    require(normalize_fingerprint("aa:bb 01") == "AABB01", "Fingerprint normalization failed")
    fixture = "+--- a.b:lib:1.0\n|    +--- c.d:other:2.0 -> 2.1\n\\--- e.f:last:3.0\n"
    pattern = re.compile(
        r"(?:\+---|\\---)\s+([^:\s]+):([^:\s]+):([^\s]+?)(?:\s+->\s+([^\s]+))?(?:\s|$)"
    )
    found = [(m.group(1), m.group(2), m.group(4) or m.group(3)) for m in pattern.finditer(fixture)]
    require(
        found == [("a.b", "lib", "1.0"), ("c.d", "other", "2.1"), ("e.f", "last", "3.0")],
        f"Dependency parser self-test failed: {found}",
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("self-test")
    sub.add_parser("verify-project")

    evidence_parser = sub.add_parser("certification-evidence")
    evidence_parser.add_argument("--sha", required=True)
    evidence_parser.add_argument("--output", required=True)

    sbom_parser = sub.add_parser("sbom")
    sbom_parser.add_argument("--dependencies", required=True)
    sbom_parser.add_argument("--output", required=True)

    manifest_parser = sub.add_parser("manifest")
    manifest_parser.add_argument("--mode", required=True, choices=["validation", "production"])
    manifest_parser.add_argument("--output", required=True)
    manifest_parser.add_argument("--artifact", action="append", default=[])
    manifest_parser.add_argument("--signing-cert-sha256", required=True)
    manifest_parser.add_argument("--evidence", required=True)
    manifest_parser.add_argument("--release-ref", required=True)
    manifest_parser.add_argument("--workflow-run-id", required=True)
    manifest_parser.add_argument("--workflow-run-attempt", required=True)
    manifest_parser.add_argument("--jdk-version", required=True)

    args = parser.parse_args()
    root = Path(args.root).resolve()

    try:
        if args.command == "self-test":
            self_test()
            print(json.dumps({"status": "PASS"}, sort_keys=True))
        elif args.command == "verify-project":
            print(json.dumps({"status": "PASS", **verify_project(root)}, indent=2, sort_keys=True))
        elif args.command == "certification-evidence":
            result = certification_evidence(args.sha, Path(args.output))
            print(json.dumps({"status": "PASS", **result}, indent=2, sort_keys=True))
        elif args.command == "sbom":
            result = generate_sbom(root, Path(args.dependencies), Path(args.output))
            print(json.dumps({"status": "PASS", **result}, indent=2, sort_keys=True))
        elif args.command == "manifest":
            result = manifest(
                root=root,
                mode=args.mode,
                output=Path(args.output),
                artifact_values=args.artifact,
                signing_cert_sha256=args.signing_cert_sha256,
                evidence_path=Path(args.evidence),
                release_ref=args.release_ref,
                workflow_run_id=args.workflow_run_id,
                workflow_run_attempt=args.workflow_run_attempt,
                jdk_version=args.jdk_version,
            )
            print(json.dumps({"status": "PASS", "classification": result["classification"]}, sort_keys=True))
        else:
            raise ReleaseError(f"Unknown command: {args.command}")
        return 0
    except (ReleaseError, OSError, subprocess.SubprocessError, urllib.error.URLError, json.JSONDecodeError) as exc:
        print(f"PHASE 11.1 RELEASE ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
