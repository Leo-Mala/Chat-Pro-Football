#!/usr/bin/env python3
"""Supplemental trusted contract for risk-based Phase 10.9 certification.

This contract is executed from an audited trusted revision, never from the candidate checkout.
It binds the lightweight certification gates and rejects indirect scope-variable overrides.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


class RiskContractError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RiskContractError(message)


def scalar(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value


def job_block(source: str, job: str) -> str:
    match = re.search(
        rf"(?ms)^  {re.escape(job)}:\n(?P<body>.*?)(?=^  [A-Za-z0-9_-]+:\n|\Z)",
        source,
    )
    require(match is not None, f"Required job missing: {job}")
    return match.group(0)


def step_block(job_source: str, step_name: str) -> str:
    match = re.search(
        rf"(?ms)^      - name: {re.escape(step_name)}\n(?P<body>.*?)(?=^      - (?:name:|uses:)|\Z)",
        job_source,
    )
    require(match is not None, f"Required step missing: {step_name}")
    block = match.group(0)
    require(not re.search(r"(?m)^        if:\s*", block), f"Required step became conditional: {step_name}")
    return block


def run_body(step_source: str, step_name: str) -> str:
    match = re.search(r"(?ms)^        run:\s*[|>]\-?\n(?P<body>.*)$", step_source)
    require(match is not None, f"Required run body missing: {step_name}")
    lines: list[str] = []
    for line in match.group("body").splitlines():
        if not line.strip():
            lines.append("")
            continue
        indent = len(line) - len(line.lstrip(" "))
        require(indent >= 10, f"Malformed run indentation in {step_name}: {line}")
        lines.append(line[10:])
    return "\n".join(lines)


def logical_commands(run: str) -> list[str]:
    commands: list[str] = []
    pending = ""
    heredoc_end: str | None = None
    for raw in run.splitlines():
        stripped = raw.strip()
        if heredoc_end is not None:
            if stripped == heredoc_end:
                heredoc_end = None
            continue
        if not stripped or stripped.startswith("#"):
            continue
        pending = f"{pending} {stripped}".strip() if pending else stripped
        if pending.endswith("\\"):
            pending = pending[:-1].rstrip()
            continue
        heredoc = re.search(r"<<-?\s*['\"]?([A-Za-z0-9_]+)['\"]?", pending)
        commands.append(pending)
        pending = ""
        if heredoc is not None:
            heredoc_end = heredoc.group(1)
    if pending:
        commands.append(pending)
    return commands


def audit_scope_run(run: str) -> int:
    commands = logical_commands(run)
    required = {
        "mode=$(sed -n 's/^mode=//p' \"$scope_file\")",
        "light=$(sed -n 's/^light=//p' \"$scope_file\")",
        "mode=full",
        "light=false",
        'test -n "$mode"',
        'test -n "$light"',
        'echo "light=$light" >> "$GITHUB_OUTPUT"',
        'if [ "$mode" = full ]; then',
        'echo "Resolved certification mode: $mode"',
    }
    for marker in required:
        require(marker in commands, f"Required scope command missing: {marker}")

    allowed_references = set(required)
    allowed_references.add(
        "printf 'mode=full\\nlight=false\\nfull=true\\nreason=classifier differs from trusted base; forcing full certification\\n' > \"$scope_file\""
    )
    for command in commands:
        if not re.search(r"\b(?:mode|light)\b", command):
            continue
        require(command in allowed_references, f"Untrusted scope variable reference/mutation: {command}")

    forbidden_mutators = re.compile(
        r"^(?:export|readonly|declare|typeset|local|unset|read|readarray|mapfile|eval|source|\.)\b"
    )
    for command in commands:
        require(not forbidden_mutators.search(command), f"Forbidden scope shell mutator: {command}")
        require(not re.search(r"\bprintf\s+(?:[^;]*\s)?-v\s+(?:mode|light)\b", command),
                f"Forbidden indirect scope assignment: {command}")
        require(not re.search(r"\$\{(?:mode|light)(?::[-+=?])", command),
                f"Forbidden parameter-expansion scope assignment: {command}")
    return len(required)


def audit_light_build(run: str) -> int:
    commands = logical_commands(run)
    markers = (
        "set -euo pipefail",
        "./gradlew assembleDebug assembleRelease bundleRelease --stacktrace",
        'test -n "$debug_apk"',
        'test -n "$release_apk"',
        'test -n "$release_aab"',
        'test -x "$apksigner"',
        '"$apksigner" verify --verbose --print-certs "$release_apk"',
        'jarsigner -verify -strict -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASSWORD" -certs "$release_aab"',
    )
    for marker in markers:
        require(marker in commands, f"Mandatory lightweight build/signature gate missing: {marker}")
    for command in commands:
        require("|| true" not in command and "set +e" not in command,
                f"Lightweight build gate no longer fails closed: {command}")
    return len(markers)


def audit_light_revalidation(run: str) -> int:
    commands = logical_commands(run)
    markers = (
        "set -euo pipefail",
        "classifier_path='.github/scripts/ci_scope.py'",
        'trusted_classifier="$RUNNER_TEMP/phase109_trusted_ci_scope.py"',
        'git diff --quiet "$BASE_SHA"...HEAD -- "$classifier_path"',
        'git show "$BASE_SHA:$classifier_path" > "$trusted_classifier"',
        'test -s "$trusted_classifier"',
        'python3 "$trusted_classifier" self-test',
        "grep -q '^mode=light ' \"$RUNNER_TEMP/light-scope.txt\"",
    )
    for marker in markers:
        require(marker in commands, f"Mandatory lightweight revalidation gate missing: {marker}")
    require(any(command.startswith('python3 "$trusted_classifier" classify ') for command in commands),
            "Trusted classifier is not executed by lightweight revalidation")
    require(not any("python3 .github/scripts/ci_scope.py" in command for command in commands),
            "Candidate classifier is executable in lightweight revalidation")
    return len(markers) + 2


def audit_startup_smoke(run: str) -> int:
    commands = logical_commands(run)
    required = (
        "set -euo pipefail",
        "./gradlew testReleaseUnitTest --tests com.example.StartupSmokeTest --stacktrace",
    )
    for marker in required:
        require(marker in commands, f"Mandatory lightweight startup gate missing: {marker}")
    return len(required)


def audit_final_gate(source: str) -> int:
    job = job_block(source, "required-certification")
    require(
        "needs: [policy-scope, light-validation, jvm-build, stress, ui-golden, performance, instrumented]" in job,
        "Final gate no longer depends on lightweight validation",
    )
    step = step_block(job, "Fail closed unless every required component certified this HEAD and audited base")
    run = run_body(step, "Fail closed unless every required component certified this HEAD and audited base")
    commands = logical_commands(run)
    required = (
        "if [ '${{ needs.policy-scope.outputs.light }}' = true ]; then",
        "test '${{ needs.light-validation.result }}' = success",
        "test '${{ needs.light-validation.result }}' = skipped",
    )
    for marker in required:
        require(marker in commands, f"Final lightweight result binding missing: {marker}")
    return len(required) + 1


def verify(workflow: Path) -> dict[str, int]:
    source = workflow.read_text(encoding="utf-8")
    policy = job_block(source, "policy-scope")
    scope_step = step_block(policy, "Resolve mandatory certification scope")
    scope_markers = audit_scope_run(run_body(scope_step, "Resolve mandatory certification scope"))

    light = job_block(source, "light-validation")
    require("if: needs.policy-scope.outputs.light == 'true'" in light,
            "Lightweight job is not bound to trusted light scope")
    revalidation_step = step_block(light, "Revalidate lightweight scope fail closed")
    build_step = step_block(light, "Build lightweight Debug and Release APK/AAB")
    smoke_step = step_block(light, "Lightweight Release startup smoke")
    revalidation_markers = audit_light_revalidation(run_body(revalidation_step, revalidation_step.splitlines()[0]))
    build_markers = audit_light_build(run_body(build_step, build_step.splitlines()[0]))
    smoke_markers = audit_startup_smoke(run_body(smoke_step, smoke_step.splitlines()[0]))
    final_markers = audit_final_gate(source)
    return {
        "scopeMarkers": scope_markers,
        "lightRevalidationMarkers": revalidation_markers,
        "lightBuildMarkers": build_markers,
        "lightStartupMarkers": smoke_markers,
        "finalGateMarkers": final_markers,
    }


def expect_rejected(callable_obj, label: str) -> None:
    try:
        callable_obj()
    except RiskContractError:
        return
    raise RiskContractError(f"Negative self-test unexpectedly accepted: {label}")


def self_test() -> None:
    scope = """set -euo pipefail
mode=$(sed -n 's/^mode=//p' "$scope_file")
light=$(sed -n 's/^light=//p' "$scope_file")
test -n "$mode"
test -n "$light"
mode=full
light=false
printf 'mode=full\\nlight=false\\nfull=true\\nreason=classifier differs from trusted base; forcing full certification\\n' > "$scope_file"
echo "light=$light" >> "$GITHUB_OUTPUT"
if [ "$mode" = full ]; then
echo "Resolved certification mode: $mode"
"""
    audit_scope_run(scope)
    expect_rejected(lambda: audit_scope_run(scope + "\nexport mode=none"), "export mode override")
    expect_rejected(lambda: audit_scope_run(scope + "\nprintf -v light true"), "printf -v light override")

    build = """set -euo pipefail
./gradlew assembleDebug assembleRelease bundleRelease --stacktrace
test -n "$debug_apk"
test -n "$release_apk"
test -n "$release_aab"
test -x "$apksigner"
"$apksigner" verify --verbose --print-certs "$release_apk"
jarsigner -verify -strict -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASSWORD" -certs "$release_aab"
"""
    audit_light_build(build)
    expect_rejected(lambda: audit_light_build(build.replace("jarsigner -verify -strict", "echo jarsigner -verify -strict")),
                    "missing Release AAB signature verification")


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    verify_parser = sub.add_parser("verify")
    verify_parser.add_argument("--workflow", required=True)
    sub.add_parser("self-test")
    args = parser.parse_args()
    try:
        if args.command == "self-test":
            self_test()
            print(json.dumps({"status": "PASS", "negativeCases": 3}, sort_keys=True))
        else:
            result = verify(Path(args.workflow))
            print(json.dumps({"status": "PASS", **result}, indent=2, sort_keys=True))
        return 0
    except (RiskContractError, OSError) as exc:
        print(f"PHASE10.9 RISK CONTRACT FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
