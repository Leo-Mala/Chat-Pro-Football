#!/usr/bin/env python3
"""Trusted executable contract for Phase 10.9 risk-based certification.

The mature pre-hardening contract is loaded from an immutable ancestor and remains the
compatibility/security foundation. This module re-exports its public API for every Guardian
revision, then adds fail-closed checks for indirect scope overrides, workflow-envelope bindings,
and all mandatory LIGHT gates.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import re
import shlex
import subprocess
import sys
import tempfile
from pathlib import Path
from types import ModuleType

LEGACY_CONTRACT_COMMIT = "ebd15a1ead5acbb83fcf32ad8bffbce644d826f8"
CONTRACT_PATH = ".github/scripts/phase109_trusted_contract.py"
REQUIRED_WORKFLOW = ".github/workflows/phase109-required-certification.yml"
REQUIRED_WORKFLOW_NAME = "Phase 10.9 Required Certification"
FORBIDDEN_SHELL_PRIMITIVES = {
    "alias", "unalias", "function", "eval", "source", "trap", "shopt", "export",
    "readonly", "declare", "typeset", "local", "unset", "read", "readarray", "mapfile",
    "exec", "exit", "return", "break", "continue",
}
EXPECTED_POLICY_OUTPUTS = {
    "light": "${{ steps.scope.outputs.light }}",
    "jvm": "${{ steps.scope.outputs.jvm }}",
    "stress": "${{ steps.scope.outputs.stress }}",
    "release": "${{ steps.scope.outputs.release }}",
    "ui": "${{ steps.scope.outputs.ui }}",
    "performance": "${{ steps.scope.outputs.performance }}",
    "instrumented": "${{ steps.scope.outputs.instrumented }}",
    "base_sha": "${{ steps.scope.outputs.base_sha }}",
}


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


def _indent(line: str) -> int:
    return len(line) - len(line.lstrip(" "))


def _job_block(text: str, job: str) -> list[str]:
    lines = text.splitlines()
    starts = [
        index for index, line in enumerate(lines)
        if _indent(line) == 2 and line.strip() == f"{job}:"
    ]
    hard_require(len(starts) == 1, f"Expected exactly one job block: {job}")
    start = starts[0]
    end = len(lines)
    for index in range(start + 1, len(lines)):
        if lines[index].strip() and _indent(lines[index]) <= 2:
            end = index
            break
    return lines[start:end]


def _step_block(text: str, job: str, step_name: str) -> list[str]:
    block = _job_block(text, job)
    starts = [
        index for index, line in enumerate(block)
        if _indent(line) == 6 and line.strip() == f"- name: {step_name}"
    ]
    hard_require(len(starts) == 1, f"Expected exactly one named step {job}/{step_name}")
    start = starts[0]
    end = len(block)
    for index in range(start + 1, len(block)):
        if block[index].strip() and _indent(block[index]) == 6 and block[index].strip().startswith("- "):
            end = index
            break
    return block[start:end]


def _require_step_shell_bash(text: str, job: str, step_name: str) -> None:
    block = _step_block(text, job, step_name)
    shells = [
        line.strip().split(":", 1)[1].strip()
        for line in block
        if _indent(line) == 8 and line.strip().startswith("shell:")
    ]
    hard_require(shells == ["bash"], f"Mandatory step shell must be exactly bash: {job}/{step_name}: {shells}")


def _job_section_mapping(text: str, job: str, section: str) -> dict[str, str]:
    block = _job_block(text, job)
    headers = [
        index for index, line in enumerate(block)
        if _indent(line) == 4 and line.strip() == f"{section}:"
    ]
    hard_require(len(headers) == 1, f"Expected exactly one {job}/{section} mapping")
    start = headers[0]
    mapping: dict[str, str] = {}
    for line in block[start + 1:]:
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        indent = _indent(line)
        if indent <= 4:
            break
        hard_require(indent == 6 and ":" in stripped, f"Unexpected nested structure in {job}/{section}: {stripped}")
        key, value = stripped.split(":", 1)
        key = key.strip()
        value = value.strip()
        hard_require(key and key not in mapping, f"Duplicate/invalid key in {job}/{section}: {key}")
        mapping[key] = value
    return mapping


def _top_level_block(text: str, key: str) -> list[str]:
    lines = text.splitlines()
    starts = [
        index for index, line in enumerate(lines)
        if _indent(line) == 0 and line.strip() == f"{key}:"
    ]
    hard_require(len(starts) == 1, f"Expected exactly one top-level {key} block")
    start = starts[0]
    end = len(lines)
    for index in range(start + 1, len(lines)):
        if lines[index].strip() and _indent(lines[index]) == 0:
            end = index
            break
    return lines[start + 1:end]


def _validate_workflow_identity_and_triggers(text: str) -> int:
    names = []
    for line in text.splitlines():
        if _indent(line) == 0 and line.strip().startswith("name:"):
            names.append(_LEGACY.scalar(line.strip().split(":", 1)[1]))
    hard_require(names == [REQUIRED_WORKFLOW_NAME], f"Required workflow name changed or duplicated: {names}")

    block = _top_level_block(text, "on")
    events: dict[str, list[str]] = {}
    current: str | None = None
    for line in block:
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        indent = _indent(line)
        if indent == 2 and stripped.endswith(":"):
            current = stripped[:-1]
            hard_require(current not in events, f"Duplicate workflow trigger: {current}")
            events[current] = []
            continue
        hard_require(current is not None and indent == 4, f"Unexpected workflow-trigger structure: {stripped}")
        events[current].append(stripped)

    hard_require(
        set(events) == {"pull_request", "push", "workflow_dispatch"},
        f"Required workflow trigger set changed: {sorted(events)}",
    )
    hard_require(
        events["pull_request"] == [
            "branches: [ main ]",
            "types: [opened, synchronize, reopened, ready_for_review]",
        ],
        f"Pull-request trigger changed: {events['pull_request']}",
    )
    hard_require(
        events["push"] == ["branches: [ main ]"],
        f"Exact-main push trigger must remain unfiltered: {events['push']}",
    )
    hard_require(not events["workflow_dispatch"], "workflow_dispatch must remain unfiltered")
    return 4


def _validate_policy_scope_outputs(text: str) -> int:
    outputs = _job_section_mapping(text, "policy-scope", "outputs")
    hard_require(outputs == EXPECTED_POLICY_OUTPUTS, f"policy-scope outputs changed: {outputs}")
    return len(EXPECTED_POLICY_OUTPUTS)


def _shell_tokens(command: str) -> list[str]:
    try:
        lexer = shlex.shlex(command, posix=True, punctuation_chars=";&|()")
        lexer.whitespace_split = True
        lexer.commenters = ""
        return list(lexer)
    except ValueError as exc:
        raise HardeningError(f"Cannot lex mandatory shell command: {command}: {exc}") from exc


def _reject_forbidden_shell_primitives(step, label: str) -> None:
    for command in _commands(step):
        tokens = _shell_tokens(command)
        forbidden = sorted({token for token in tokens if token in FORBIDDEN_SHELL_PRIMITIVES})
        hard_require(not forbidden, f"Forbidden shell primitive in {label}: {forbidden}: {command}")


def _validate_required_bash_shells(steps, text: str) -> int:
    pairs = {(rule.job, rule.step) for rule in getattr(_LEGACY, "CANDIDATE_RUN_RULES", ())}
    pairs.update({
        ("policy-scope", "Verify exact candidate HEAD"),
        ("policy-scope", "Resolve mandatory certification scope"),
        ("light-validation", "Verify exact candidate HEAD"),
        ("light-validation", "Revalidate lightweight scope fail closed"),
        ("light-validation", "Setup lightweight Android validation signing"),
        ("light-validation", "Build lightweight Debug and Release APK/AAB"),
        ("light-validation", "Lightweight Release startup smoke"),
        ("required-certification", "Fail closed unless every required component certified this HEAD and audited base"),
    })
    for job, step_name in sorted(pairs):
        _LEGACY.step_for(steps, job, step_name)
        _require_step_shell_bash(text, job, step_name)
    return len(pairs)


def _reject_light_shell_bypass(step, label: str) -> None:
    commands = _commands(step)
    hard_require(not _LEGACY.control_commands(step), f"Unexpected shell control flow in {label}")
    _reject_forbidden_shell_primitives(step, label)
    forbidden_start = re.compile(
        r"^(?:alias|unalias|function|eval|source|trap|shopt|export|readonly|declare|typeset|local|unset|read|readarray|mapfile|exec|exit|return|break|continue)\b"
    )
    for command in commands:
        stripped = command.strip()
        hard_require(not forbidden_start.search(stripped), f"Forbidden shell mutation/termination in {label}: {stripped}")
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
    _reject_forbidden_shell_primitives(step, "certification scope selection")
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
    allowed_assignments = {
        "legacy_source_anchor=\"r'^app/src/'\"",
        "classifier_path='.github/scripts/ci_scope.py'",
        "base='${{ github.event.pull_request.base.sha }}'",
        'scope_file="$RUNNER_TEMP/phase109-scope.txt"',
        'trusted_classifier="$RUNNER_TEMP/phase109_trusted_ci_scope.py"',
        "mode=$(sed -n 's/^mode=//p' \"$scope_file\")",
        "light=$(sed -n 's/^light=//p' \"$scope_file\")",
        "mode=full",
        "light=false",
        'base="$(git rev-parse HEAD^1)"',
    }
    forbidden_mutator = re.compile(
        r"^(?:export|readonly|declare|typeset|local|unset|read|readarray|mapfile|eval|source|exec|exit|return|break|continue|\.)\b"
    )
    assignment = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(?:\+)?=")
    compound_scope_assignment = re.compile(r"[;&|()]\s*(?:mode|light)\s*=")
    for command in commands:
        stripped = command.strip()
        hard_require(not forbidden_mutator.search(stripped), f"Forbidden certification-scope mutator/termination: {stripped}")
        hard_require(not re.search(r"\bprintf\s+(?:[^;]*\s)?-v\b", stripped),
                     f"Indirect certification-scope assignment: {stripped}")
        hard_require(not compound_scope_assignment.search(stripped),
                     f"Compound certification-scope assignment forbidden: {stripped}")
        if assignment.search(stripped):
            hard_require(stripped in allowed_assignments,
                         f"Unexpected certification-scope assignment: {stripped}")
        if re.search(r"\b(?:mode|light)\b", stripped):
            hard_require(stripped in allowed_scope_references,
                         f"Unexpected certification-scope reference/mutation: {stripped}")
    return len(allowed_scope_references) + len(allowed_assignments)


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
    _reject_forbidden_shell_primitives(gate, "required certification final gate")
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

    trigger_markers = _validate_workflow_identity_and_triggers(text)
    policy_output_markers = _validate_policy_scope_outputs(text)
    required_shell_markers = _validate_required_bash_shells(steps, text)

    scope = _LEGACY.step_for(steps, "policy-scope", "Resolve mandatory certification scope")
    scope_markers = _validate_scope_override_resistance(scope)

    revalidate = _LEGACY.step_for(steps, "light-validation", "Revalidate lightweight scope fail closed")
    revalidation_markers = _LEGACY.validate_trusted_light_revalidation(revalidate)
    _reject_light_shell_bypass(revalidate, "LIGHT trusted-scope revalidation")

    build = _LEGACY.step_for(steps, "light-validation", "Build lightweight Debug and Release APK/AAB")
    startup = _LEGACY.step_for(steps, "light-validation", "Lightweight Release startup smoke")

    for rule in getattr(_LEGACY, "CANDIDATE_RUN_RULES", ()):
        candidate_step = _LEGACY.step_for(steps, rule.job, rule.step)
        _reject_forbidden_shell_primitives(candidate_step, f"required step {rule.job}/{rule.step}")

    return {
        "workflowEnvelopeMarkers": trigger_markers,
        "policyScopeOutputMarkers": policy_output_markers,
        "requiredBashShellMarkers": required_shell_markers,
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
        shell: bash
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
    for injected in (
        "export mode=none",
        "export light=false",
        "printf -v mode none",
        "${mode:=none}",
        "x=mo\n          x+=de\n          printf -v \"$x\" none",
        "exec true",
        "true; exit 0",
    ):
        bad = scope_fixture.replace('echo "Resolved certification mode: $mode"', f'{injected}\n          echo "Resolved certification mode: $mode"')
        bad_step = _LEGACY.step_for(_LEGACY.parse_steps(bad), "policy-scope", "Resolve mandatory certification scope")
        _expect_rejected(lambda step=bad_step: _validate_scope_override_resistance(step), injected)

    build_fixture = """jobs:
  light-validation:
    steps:
      - name: Build lightweight Debug and Release APK/AAB
        shell: bash
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
    _require_step_shell_bash(build_fixture, "light-validation", "Build lightweight Debug and Release APK/AAB")

    missing_signature = build_fixture.replace(
        '          jarsigner -verify -strict -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASSWORD" -certs "$release_aab"\n', ""
    )
    bad_build = _LEGACY.step_for(_LEGACY.parse_steps(missing_signature), "light-validation", "Build lightweight Debug and Release APK/AAB")
    _expect_rejected(lambda: _validate_light_build(bad_build), "deleted Release AAB signature verification")

    early_exit = build_fixture.replace(
        "          ./gradlew assembleDebug assembleRelease bundleRelease --stacktrace\n",
        "          exit 0\n          ./gradlew assembleDebug assembleRelease bundleRelease --stacktrace\n",
    )
    early_exit_build = _LEGACY.step_for(_LEGACY.parse_steps(early_exit), "light-validation", "Build lightweight Debug and Release APK/AAB")
    _expect_rejected(lambda: _validate_light_build(early_exit_build), "early exit before mandatory LIGHT build")

    early_exec = build_fixture.replace(
        "          ./gradlew assembleDebug assembleRelease bundleRelease --stacktrace\n",
        "          exec true\n          ./gradlew assembleDebug assembleRelease bundleRelease --stacktrace\n",
    )
    early_exec_build = _LEGACY.step_for(_LEGACY.parse_steps(early_exec), "light-validation", "Build lightweight Debug and Release APK/AAB")
    _expect_rejected(lambda: _validate_light_build(early_exec_build), "exec termination before mandatory LIGHT build")

    compound_exit = build_fixture.replace(
        "          ./gradlew assembleDebug assembleRelease bundleRelease --stacktrace\n",
        "          true; exit 0\n          ./gradlew assembleDebug assembleRelease bundleRelease --stacktrace\n",
    )
    compound_exit_build = _LEGACY.step_for(_LEGACY.parse_steps(compound_exit), "light-validation", "Build lightweight Debug and Release APK/AAB")
    _expect_rejected(lambda: _validate_light_build(compound_exit_build), "compound exit before mandatory LIGHT build")

    shell_bypass = build_fixture.replace("        shell: bash\n", "        shell: bash -c true {0}\n")
    _expect_rejected(
        lambda: _require_step_shell_bash(shell_bypass, "light-validation", "Build lightweight Debug and Release APK/AAB"),
        "mandatory LIGHT shell bypass",
    )

    envelope_fixture = """name: Phase 10.9 Required Certification

on:
  pull_request:
    branches: [ main ]
    types: [opened, synchronize, reopened, ready_for_review]
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  policy-scope:
    outputs:
      light: ${{ steps.scope.outputs.light }}
      jvm: ${{ steps.scope.outputs.jvm }}
      stress: ${{ steps.scope.outputs.stress }}
      release: ${{ steps.scope.outputs.release }}
      ui: ${{ steps.scope.outputs.ui }}
      performance: ${{ steps.scope.outputs.performance }}
      instrumented: ${{ steps.scope.outputs.instrumented }}
      base_sha: ${{ steps.scope.outputs.base_sha }}
    steps:
      - name: Resolve mandatory certification scope
        shell: bash
        run: |
          set -euo pipefail
"""
    _validate_workflow_identity_and_triggers(envelope_fixture)
    _validate_policy_scope_outputs(envelope_fixture)

    missing_push = envelope_fixture.replace("  push:\n    branches: [ main ]\n", "")
    _expect_rejected(lambda: _validate_workflow_identity_and_triggers(missing_push), "missing exact-main push trigger")

    filtered_push = envelope_fixture.replace("  push:\n    branches: [ main ]\n", "  push:\n    branches: [ main ]\n    paths: [ 'docs/**' ]\n")
    _expect_rejected(lambda: _validate_workflow_identity_and_triggers(filtered_push), "filtered exact-main push trigger")

    bad_output = envelope_fixture.replace(
        "      jvm: ${{ steps.scope.outputs.jvm }}\n",
        "      jvm: ${{ steps.scope.outputs.nonexistent }}\n",
    )
    _expect_rejected(lambda: _validate_policy_scope_outputs(bad_output), "unbound policy-scope output")

    renamed = envelope_fixture.replace("name: Phase 10.9 Required Certification", "name: Candidate Controlled Certification")
    _expect_rejected(lambda: _validate_workflow_identity_and_triggers(renamed), "required workflow rename")


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
                "newNegativeCases": 15,
                "guardianApiCompatibility": hasattr(sys.modules[__name__], "BASE_RUN_RULES"),
                "lightweightGatesStructurallyBound": True,
                "indirectScopeOverridesRejected": True,
                "earlyTerminationRejected": True,
                "compoundTerminationRejected": True,
                "requiredShellsPinned": True,
                "policyOutputsBound": True,
                "exactMainTriggerBound": True,
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
