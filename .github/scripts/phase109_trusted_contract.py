#!/usr/bin/env python3
"""Trusted executable contract for Phase 10.9 risk-based certification.

The mature pre-hardening contract is loaded from an immutable ancestor and remains the
compatibility/security foundation. This module re-exports its public API for every Guardian
revision, then adds fail-closed checks for indirect scope overrides and all mandatory LIGHT gates.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path
from types import ModuleType

LEGACY_CONTRACT_COMMIT = "ebd15a1ead5acbb83fcf32ad8bffbce644d826f8"
CONTRACT_PATH = ".github/scripts/phase109_trusted_contract.py"
REQUIRED_WORKFLOW = ".github/workflows/phase109-required-certification.yml"


class HardeningError(ValueError):
    pass


def hard_require(condition: bool, message: str) -> None:
    if not condition:
        raise HardeningError(message)


def _repo_root() -> Path:
    candidates = [Path.cwd().resolve()]
    try:
        candidates.append(Path(__file__).resolve().parents[2])
    except IndexError:
        pass
    for candidate in candidates:
        probe = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"], cwd=candidate, text=True,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
        )
        if probe.returncode == 0:
            return Path(probe.stdout.strip()).resolve()
    raise HardeningError("Cannot resolve repository root for frozen trusted contract")


def _git_show(root: Path, spec: str) -> str:
    proc = subprocess.run(
        ["git", "show", spec], cwd=root, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
    )
    hard_require(proc.returncode == 0, f"Cannot materialize frozen trusted contract: {proc.stderr.strip()}")
    return proc.stdout


def _load_legacy_contract() -> ModuleType:
    root = _repo_root()
    source = _git_show(root, f"{LEGACY_CONTRACT_COMMIT}:{CONTRACT_PATH}")
    hard_require(source.startswith("#!/usr/bin/env python3"), "Frozen trusted contract is malformed")
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".py", delete=False) as handle:
        handle.write(source)
        path = Path(handle.name)
    module_name = "_phase109_trusted_contract_frozen_ebd15"
    try:
        spec = importlib.util.spec_from_file_location(module_name, path)
        hard_require(spec is not None and spec.loader is not None, "Cannot load frozen trusted contract module")
        module = importlib.util.module_from_spec(spec)
        sys.modules[module_name] = module
        spec.loader.exec_module(module)
        return module
    finally:
        path.unlink(missing_ok=True)


_LEGACY = _load_legacy_contract()

# Preserve the complete public module API expected by phase109_guardian.py and all historical
# Guardian revisions (including BASE_RUN_RULES, parsers, rule types and validation helpers).
for _name in dir(_LEGACY):
    if not _name.startswith("_") and _name not in globals():
        globals()[_name] = getattr(_LEGACY, _name)


def _commands(step) -> list[str]:
    return _LEGACY.logical_commands(step.run)


def _reject_light_shell_bypass(step, label: str) -> None:
    commands = _commands(step)
    hard_require(not _LEGACY.control_commands(step), f"Unexpected shell control flow in {label}")
    forbidden_start = re.compile(
        r"^(?:alias|unalias|function|eval|source|trap|shopt|export|readonly|declare|typeset|local|unset|read|readarray|mapfile)\b"
    )
    for command in commands:
        stripped = command.strip()
        hard_require(not forbidden_start.search(stripped), f"Forbidden shell mutation in {label}: {stripped}")
        hard_require(not re.search(r"^(?:PATH|BASH_ENV|SHELLOPTS)\s*=", stripped),
                     f"Execution-environment mutation in {label}: {stripped}")
        hard_require(not re.search(r"^[A-Za-z_][A-Za-z0-9_]*\s*\(\)\s*\{", stripped),
                     f"Shell function definition in {label}: {stripped}")
        hard_require("|| true" not in stripped and not re.search(r"(?:^|;)\s*set\s+\+e\b", stripped),
                     f"Fail-open command in {label}: {stripped}")
        hard_require(not re.search(r"\bprintf\s+(?:[^;]*\s)?-v\b", stripped),
                     f"Indirect shell assignment in {label}: {stripped}")


def _validate_scope_override_resistance(step) -> int:
    # First preserve every exact structural invariant from the frozen trusted contract.
    _LEGACY.validate_trusted_scope_selection(step)
    commands = _commands(step)
    allowed_scope_references = {
        "mode=$(sed -n 's/^mode=//p' \"$scope_file\")",
        "light=$(sed -n 's/^light=//p' \"$scope_file\")",
        "mode=full",
        "light=false",
        'test -n "$mode"',
        'test -n "$light"',
        'echo "light=$light" >> "$GITHUB_OUTPUT"',
        'if [ "$mode" = full ]; then',
        'echo "Resolved certification mode: $mode"',
        "printf 'mode=full\\nlight=false\\nfull=true\\nreason=classifier differs from trusted base; forcing full certification\\n' > \"$scope_file\"",
    }
    forbidden_mutator = re.compile(
        r"^(?:export|readonly|declare|typeset|local|unset|read|readarray|mapfile|eval|source|\.)\b"
    )
    for command in commands:
        stripped = command.strip()
        hard_require(not forbidden_mutator.search(stripped), f"Forbidden certification-scope mutator: {stripped}")
        hard_require(not re.search(r"\bprintf\s+(?:[^;]*\s)?-v\s+(?:mode|light)\b", stripped),
                     f"Indirect certification-scope assignment: {stripped}")
        if re.search(r"\b(?:mode|light)\b", stripped):
            hard_require(stripped in allowed_scope_references,
                         f"Unexpected certification-scope reference/mutation: {stripped}")
    return len(allowed_scope_references)


def _validate_light_build(step) -> int:
    commands = _commands(step)
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
        hard_require(marker in commands, f"Mandatory LIGHT build/signature gate missing: {marker}")
    _reject_light_shell_bypass(step, "LIGHT Debug/Release build and signature verification")
    return len(required)


def _validate_light_startup(step) -> int:
    commands = _commands(step)
    required = (
        "set -euo pipefail",
        "./gradlew testReleaseUnitTest --tests com.example.StartupSmokeTest --stacktrace",
    )
    for marker in required:
        hard_require(marker in commands, f"Mandatory LIGHT startup gate missing: {marker}")
    _reject_light_shell_bypass(step, "LIGHT Release startup smoke")
    return len(required)


def _validate_light_final_binding(steps, text: str) -> int:
    hard_require("continue-on-error" not in text, "Required certification workflow may not continue on error")
    hard_require("needs: [policy-scope, light-validation, jvm-build, stress, ui-golden, performance, instrumented]" in text,
                 "Final gate no longer depends on LIGHT validation")
    gate = _LEGACY.step_for(
        steps, "required-certification", "Fail closed unless every required component certified this HEAD and audited base"
    )
    commands = _commands(gate)
    required = (
        "if [ '${{ needs.policy-scope.outputs.light }}' = true ]; then",
        "test '${{ needs.light-validation.result }}' = success",
        "test '${{ needs.light-validation.result }}' = skipped",
    )
    for marker in required:
        hard_require(marker in commands, f"Final LIGHT-result binding missing: {marker}")
    return len(required)


def _hardening_validate_candidate_workflow(workflow_path: Path) -> dict[str, int]:
    text = workflow_path.read_text(encoding="utf-8")
    steps = _LEGACY.parse_steps(text)

    scope = _LEGACY.step_for(steps, "policy-scope", "Resolve mandatory certification scope")
    scope_markers = _validate_scope_override_resistance(scope)

    revalidate = _LEGACY.step_for(steps, "light-validation", "Revalidate lightweight scope fail closed")
    revalidation_markers = _LEGACY.validate_trusted_light_revalidation(revalidate)
    _reject_light_shell_bypass(revalidate, "LIGHT trusted-scope revalidation")

    build = _LEGACY.step_for(steps, "light-validation", "Build lightweight Debug and Release APK/AAB")
    startup = _LEGACY.step_for(steps, "light-validation", "Lightweight Release startup smoke")

    return {
        "scopeOverrideResistanceMarkers": scope_markers,
        "lightRevalidationMarkers": revalidation_markers,
        "lightBuildSignatureMarkers": _validate_light_build(build),
        "lightStartupMarkers": _validate_light_startup(startup),
        "lightFinalBindingMarkers": _validate_light_final_binding(steps, text),
    }


def validate_base_workflows(root: Path, base_sha: str) -> int:
    return _LEGACY.validate_base_workflows(root, base_sha)


def validate_candidate_workflow(root: Path, workflow_path: Path) -> dict[str, int]:
    result = dict(_LEGACY.validate_candidate_workflow(root, workflow_path))
    result.update(_hardening_validate_candidate_workflow(workflow_path))
    return result


def verify(root: Path, base_sha: str, workflow: Path) -> dict[str, object]:
    result = dict(_LEGACY.verify(root, base_sha, workflow))
    result.update(_hardening_validate_candidate_workflow(workflow))
    result["frozenLegacyContract"] = LEGACY_CONTRACT_COMMIT
    return result


def _expect_rejected(callable_obj, label: str) -> None:
    try:
        callable_obj()
    except (HardeningError, _LEGACY.ContractError):
        return
    raise HardeningError(f"Negative self-test unexpectedly accepted: {label}")


def _hardening_self_test() -> None:
    scope_fixture = """jobs:
  policy-scope:
    steps:
      - name: Resolve mandatory certification scope
        run: |
          set -euo pipefail
          classifier_path='.github/scripts/ci_scope.py'
          if [ "${{ github.event_name }}" = "pull_request" ]; then
            base='${{ github.event.pull_request.base.sha }}'
            scope_file="$RUNNER_TEMP/phase109-scope.txt"
            if git cat-file -e "$base:$classifier_path" 2>/dev/null && git diff --quiet "$base"...HEAD -- "$classifier_path"; then
              trusted_classifier="$RUNNER_TEMP/phase109_trusted_ci_scope.py"
              git show "$base:$classifier_path" > "$trusted_classifier"
              test -s "$trusted_classifier"
              python3 "$trusted_classifier" self-test
              python3 "$trusted_classifier" classify --base "$base" --head HEAD
              mode=$(sed -n 's/^mode=//p' "$scope_file")
              light=$(sed -n 's/^light=//p' "$scope_file")
            else
              mode=full
              light=false
              printf 'mode=full\\nlight=false\\nfull=true\\nreason=classifier differs from trusted base; forcing full certification\\n' > "$scope_file"
            fi
          else
            mode=full
            light=false
          fi
          echo "base_sha=$base" >> "$GITHUB_OUTPUT"
          echo "light=$light" >> "$GITHUB_OUTPUT"
          if [ "$mode" = full ]; then
            for key in jvm stress release ui performance instrumented; do
              echo "$key=true" >> "$GITHUB_OUTPUT"
            done
          else
            for key in jvm stress release ui performance instrumented; do
              echo "$key=false" >> "$GITHUB_OUTPUT"
            done
          fi
          echo "Resolved certification mode: $mode"
"""
    scope = _LEGACY.step_for(_LEGACY.parse_steps(scope_fixture), "policy-scope", "Resolve mandatory certification scope")
    _validate_scope_override_resistance(scope)
    for injected in ("export mode=none", "export light=false", "printf -v mode none", "${mode:=none}"):
        bad = scope_fixture.replace('echo "Resolved certification mode: $mode"', f'{injected}\n          echo "Resolved certification mode: $mode"')
        bad_step = _LEGACY.step_for(_LEGACY.parse_steps(bad), "policy-scope", "Resolve mandatory certification scope")
        _expect_rejected(lambda step=bad_step: _validate_scope_override_resistance(step), injected)

    build_fixture = """jobs:
  light-validation:
    steps:
      - name: Build lightweight Debug and Release APK/AAB
        run: |
          set -euo pipefail
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
    build = _LEGACY.step_for(_LEGACY.parse_steps(build_fixture), "light-validation", "Build lightweight Debug and Release APK/AAB")
    _validate_light_build(build)
    missing_signature = build_fixture.replace(
        '          jarsigner -verify -strict -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASSWORD" -certs "$release_aab"\n', ""
    )
    bad_build = _LEGACY.step_for(_LEGACY.parse_steps(missing_signature), "light-validation", "Build lightweight Debug and Release APK/AAB")
    _expect_rejected(lambda: _validate_light_build(bad_build), "deleted Release AAB signature verification")


def self_test() -> None:
    _LEGACY.self_test()
    _hardening_self_test()


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
        if args.command == "self-test":
            self_test()
            print(json.dumps({
                "status": "PASS",
                "frozenLegacyContract": LEGACY_CONTRACT_COMMIT,
                "newNegativeCases": 5,
                "guardianApiCompatibility": hasattr(sys.modules[__name__], "BASE_RUN_RULES"),
                "lightweightGatesStructurallyBound": True,
                "indirectScopeOverridesRejected": True,
            }, sort_keys=True))
        else:
            root = Path(args.root).resolve()
            workflow = Path(args.workflow)
            if not workflow.is_absolute():
                workflow = root / workflow
            print(json.dumps({"status": "PASS", **verify(root, args.base_sha, workflow)}, indent=2, sort_keys=True))
        return 0
    except (HardeningError, _LEGACY.ContractError, OSError, subprocess.SubprocessError) as exc:
        print(f"PHASE10.9 TRUSTED CONTRACT FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
