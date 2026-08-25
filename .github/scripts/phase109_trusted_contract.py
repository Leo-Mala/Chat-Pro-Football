#!/usr/bin/env python3
"""Fail-closed trusted contract for Phase 10.9 risk-based certification.

The mature pre-hardening contract is frozen at an immutable ancestor and is always executed
first. This wrapper adds independent structural checks for certification-scope integrity and
for every mandatory lightweight build/signature/startup gate.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

LEGACY_CONTRACT_COMMIT = "ebd15a1ead5acbb83fcf32ad8bffbce644d826f8"
CONTRACT_PATH = ".github/scripts/phase109_trusted_contract.py"
REQUIRED_WORKFLOW = ".github/workflows/phase109-required-certification.yml"


class ContractError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ContractError(message)


def git_text(root: Path, *args: str) -> str:
    proc = subprocess.run(
        ["git", *args], cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False
    )
    require(proc.returncode == 0, f"git {' '.join(args)} failed: {proc.stderr.strip()}")
    return proc.stdout


def run_legacy(root: Path, argv: list[str]) -> None:
    source = git_text(root, "show", f"{LEGACY_CONTRACT_COMMIT}:{CONTRACT_PATH}")
    require(source.startswith("#!/usr/bin/env python3"), "Frozen legacy trusted contract could not be materialized")
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".py", delete=False) as handle:
        handle.write(source)
        legacy_path = Path(handle.name)
    try:
        proc = subprocess.run(
            [sys.executable, str(legacy_path), *argv],
            cwd=root,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        if proc.stdout:
            print(proc.stdout, end="")
        if proc.stderr:
            print(proc.stderr, end="", file=sys.stderr)
        require(proc.returncode == 0, f"Frozen legacy trusted contract rejected candidate (exit={proc.returncode})")
    finally:
        legacy_path.unlink(missing_ok=True)


def job_block(source: str, job: str) -> str:
    match = re.search(
        rf"(?ms)^  {re.escape(job)}:\n(?P<body>.*?)(?=^  [A-Za-z0-9_-]+:\n|\Z)", source
    )
    require(match is not None, f"Required job missing: {job}")
    return match.group(0)


def step_block(job_source: str, step_name: str) -> str:
    match = re.search(
        rf"(?ms)^      - name: {re.escape(step_name)}\n(?P<body>.*?)(?=^      - |\Z)", job_source
    )
    require(match is not None, f"Required step missing: {step_name}")
    block = match.group(0)
    require(not re.search(r"(?m)^        if:\s*", block), f"Required step became conditional: {step_name}")
    require("continue-on-error:" not in block, f"Required step may not continue on error: {step_name}")
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


def reject_shell_bypass(commands: list[str], label: str, *, allow_controls: set[str] | None = None) -> None:
    allow_controls = allow_controls or set()
    control_re = re.compile(r"^(?:if\b|elif\b|else\b|fi\b|while\b|until\b|for\b|case\b|esac\b|select\b)")
    mutation_re = re.compile(
        r"^(?:alias|unalias|function|eval|source|trap|shopt|export|readonly|declare|typeset|local|unset|read|readarray|mapfile)\b"
    )
    for command in commands:
        stripped = command.strip()
        if control_re.search(stripped):
            require(stripped in allow_controls, f"Unexpected shell control flow in {label}: {stripped}")
        require(not mutation_re.search(stripped), f"Forbidden shell mutation in {label}: {stripped}")
        require(not re.search(r"^(?:PATH|BASH_ENV|SHELLOPTS)\s*=", stripped),
                f"Shell execution environment mutation forbidden in {label}: {stripped}")
        require(not re.search(r"^[A-Za-z_][A-Za-z0-9_]*\s*\(\)\s*\{", stripped),
                f"Shell function definition forbidden in {label}: {stripped}")
        require("|| true" not in stripped and not re.search(r"(?:^|;)\s*set\s+\+e\b", stripped),
                f"Fail-open command forbidden in {label}: {stripped}")
        require(not re.search(r"\bprintf\s+(?:[^;]*\s)?-v\b", stripped),
                f"Indirect shell assignment forbidden in {label}: {stripped}")


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

    allowed_scope_references = set(required)
    allowed_scope_references.add(
        "printf 'mode=full\\nlight=false\\nfull=true\\nreason=classifier differs from trusted base; forcing full certification\\n' > \"$scope_file\""
    )
    for command in commands:
        if re.search(r"\b(?:mode|light)\b", command):
            require(command in allowed_scope_references,
                    f"Indirect or unexpected certification-scope reference/mutation: {command}")

    allowed_controls = {
        'if [ "${{ github.event_name }}" = "pull_request" ]; then',
        'if git cat-file -e "$base:$classifier_path" 2>/dev/null && git diff --quiet "$base"...HEAD -- "$classifier_path"; then',
        "else",
        "fi",
        'if [ "$mode" = full ]; then',
        "for key in jvm stress release ui performance instrumented; do",
    }
    reject_shell_bypass(commands, "certification scope selection", allow_controls=allowed_controls)
    return len(required) + 2


def audit_light_revalidation(run: str) -> int:
    commands = logical_commands(run)
    required = (
        "set -euo pipefail",
        "classifier_path='.github/scripts/ci_scope.py'",
        'trusted_classifier="$RUNNER_TEMP/phase109_trusted_ci_scope.py"',
        'git diff --quiet "$BASE_SHA"...HEAD -- "$classifier_path"',
        'git show "$BASE_SHA:$classifier_path" > "$trusted_classifier"',
        'test -s "$trusted_classifier"',
        'python3 "$trusted_classifier" self-test',
        "grep -q '^mode=light ' \"$RUNNER_TEMP/light-scope.txt\"",
    )
    for marker in required:
        require(marker in commands, f"Mandatory lightweight revalidation gate missing: {marker}")
    require(any(command.startswith('python3 "$trusted_classifier" classify ') for command in commands),
            "Trusted classifier is not executed by lightweight revalidation")
    require(not any("python3 .github/scripts/ci_scope.py" in command for command in commands),
            "Candidate classifier is executable in lightweight revalidation")
    reject_shell_bypass(commands, "lightweight revalidation")
    return len(required) + 2


def audit_light_build(run: str) -> int:
    commands = logical_commands(run)
    required = (
        "set -euo pipefail",
        "./gradlew assembleDebug assembleRelease bundleRelease --stacktrace",
        'debug_apk=$(find app/build/outputs/apk/debug -maxdepth 1 -name \'*.apk\' -print -quit)',
        'release_apk=$(find app/build/outputs/apk/release -maxdepth 1 -name \'*.apk\' -print -quit)',
        'release_aab=$(find app/build/outputs/bundle/release -maxdepth 1 -name \'*.aab\' -print -quit)',
        'test -n "$debug_apk"',
        'test -n "$release_apk"',
        'test -n "$release_aab"',
        'apksigner=$(find "$ANDROID_HOME/build-tools" -type f -name apksigner | sort -V | tail -n 1)',
        'test -x "$apksigner"',
        '"$apksigner" verify --verbose --print-certs "$release_apk"',
        'jarsigner -verify -strict -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASSWORD" -certs "$release_aab"',
    )
    for marker in required:
        require(marker in commands, f"Mandatory lightweight build/signature gate missing: {marker}")
    reject_shell_bypass(commands, "lightweight Debug/Release build and signature verification")
    return len(required)


def audit_light_startup(run: str) -> int:
    commands = logical_commands(run)
    required = (
        "set -euo pipefail",
        "./gradlew testReleaseUnitTest --tests com.example.StartupSmokeTest --stacktrace",
    )
    for marker in required:
        require(marker in commands, f"Mandatory lightweight Release startup gate missing: {marker}")
    reject_shell_bypass(commands, "lightweight Release startup smoke")
    return len(required)


def audit_final_gate(source: str) -> int:
    job = job_block(source, "required-certification")
    require("continue-on-error:" not in job, "Final Required Certification Gate may not continue on error")
    require(
        "needs: [policy-scope, light-validation, jvm-build, stress, ui-golden, performance, instrumented]" in job,
        "Final gate no longer depends on lightweight validation",
    )
    step_name = "Fail closed unless every required component certified this HEAD and audited base"
    step = step_block(job, step_name)
    commands = logical_commands(run_body(step, step_name))
    required = (
        "if [ '${{ needs.policy-scope.outputs.light }}' = true ]; then",
        "test '${{ needs.light-validation.result }}' = success",
        "test '${{ needs.light-validation.result }}' = skipped",
    )
    for marker in required:
        require(marker in commands, f"Final lightweight result binding missing: {marker}")
    return len(required) + 1


def hardening_verify(workflow: Path) -> dict[str, int]:
    source = workflow.read_text(encoding="utf-8")
    policy = job_block(source, "policy-scope")
    scope_name = "Resolve mandatory certification scope"
    scope = step_block(policy, scope_name)
    scope_markers = audit_scope_run(run_body(scope, scope_name))

    light = job_block(source, "light-validation")
    require("continue-on-error:" not in light, "Lightweight validation may not continue on error")
    require("if: needs.policy-scope.outputs.light == 'true'" in light,
            "Lightweight job is not bound to trusted light scope")

    revalidate_name = "Revalidate lightweight scope fail closed"
    build_name = "Build lightweight Debug and Release APK/AAB"
    startup_name = "Lightweight Release startup smoke"
    revalidate = step_block(light, revalidate_name)
    build = step_block(light, build_name)
    startup = step_block(light, startup_name)

    return {
        "scopeHardeningMarkers": scope_markers,
        "lightRevalidationMarkers": audit_light_revalidation(run_body(revalidate, revalidate_name)),
        "lightBuildSignatureMarkers": audit_light_build(run_body(build, build_name)),
        "lightStartupMarkers": audit_light_startup(run_body(startup, startup_name)),
        "lightFinalGateMarkers": audit_final_gate(source),
    }


def expect_rejected(callable_obj, label: str) -> None:
    try:
        callable_obj()
    except ContractError:
        return
    raise ContractError(f"Negative self-test unexpectedly accepted: {label}")


def hardening_self_test() -> None:
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
for key in jvm stress release ui performance instrumented; do
else
fi
echo "Resolved certification mode: $mode"
"""
    audit_scope_run(scope)
    expect_rejected(lambda: audit_scope_run(scope + "\nexport mode=none"), "export mode override")
    expect_rejected(lambda: audit_scope_run(scope + "\nprintf -v light true"), "printf -v light override")
    expect_rejected(lambda: audit_scope_run(scope + "\n${mode:=none}"), "parameter-expansion mode override")

    build = """set -euo pipefail
./gradlew assembleDebug assembleRelease bundleRelease --stacktrace
debug_apk=$(find app/build/outputs/apk/debug -maxdepth 1 -name '*.apk' -print -quit)
release_apk=$(find app/build/outputs/apk/release -maxdepth 1 -name '*.apk' -print -quit)
release_aab=$(find app/build/outputs/bundle/release -maxdepth 1 -name '*.aab' -print -quit)
test -n "$debug_apk"
test -n "$release_apk"
test -n "$release_aab"
apksigner=$(find "$ANDROID_HOME/build-tools" -type f -name apksigner | sort -V | tail -n 1)
test -x "$apksigner"
"$apksigner" verify --verbose --print-certs "$release_apk"
jarsigner -verify -strict -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASSWORD" -certs "$release_aab"
"""
    audit_light_build(build)
    expect_rejected(
        lambda: audit_light_build(build.replace(
            'jarsigner -verify -strict -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASSWORD" -certs "$release_aab"',
            'echo jarsigner -verify -strict -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASSWORD" -certs "$release_aab"',
        )),
        "removed Release AAB signature verification",
    )
    expect_rejected(
        lambda: audit_light_build(build.replace(
            "./gradlew assembleDebug assembleRelease bundleRelease --stacktrace",
            "if false; then\n./gradlew assembleDebug assembleRelease bundleRelease --stacktrace\nfi",
        )),
        "conditionalized lightweight build",
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    verify_parser = sub.add_parser("verify")
    verify_parser.add_argument("--root", default=".")
    verify_parser.add_argument("--base-sha", required=True)
    verify_parser.add_argument("--workflow", default=REQUIRED_WORKFLOW)
    sub.add_parser("self-test")
    args = parser.parse_args()

    try:
        root = Path(getattr(args, "root", ".")).resolve()
        if args.command == "self-test":
            run_legacy(Path.cwd(), ["self-test"])
            hardening_self_test()
            print(json.dumps({
                "status": "PASS",
                "frozenLegacyContract": LEGACY_CONTRACT_COMMIT,
                "newNegativeCases": 5,
                "lightweightGatesStructurallyBound": True,
                "indirectScopeOverridesRejected": True,
            }, sort_keys=True))
        else:
            workflow = Path(args.workflow)
            if not workflow.is_absolute():
                workflow = root / workflow
            run_legacy(root, [
                "verify",
                "--root", str(root),
                "--base-sha", args.base_sha,
                "--workflow", str(workflow),
            ])
            result = hardening_verify(workflow)
            print(json.dumps({
                "status": "PASS",
                "frozenLegacyContract": LEGACY_CONTRACT_COMMIT,
                **result,
            }, indent=2, sort_keys=True))
        return 0
    except (ContractError, OSError, subprocess.SubprocessError) as exc:
        print(f"PHASE10.9 TRUSTED CONTRACT FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
