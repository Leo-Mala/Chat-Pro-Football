#!/usr/bin/env python3
"""Final fail-closed hardening for Phase 10.9 risk-based certification.

The fully audited contract from the immediately preceding candidate is loaded from an immutable
commit and remains the compatibility/security foundation. This layer closes the remaining
control-flow and worktree-substitution gaps without weakening any earlier invariant.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import shlex
import subprocess
import sys
import tempfile
from pathlib import Path
from types import ModuleType

PREVIOUS_CONTRACT_COMMIT = "15759f2bc8cec4b0bcfc4d681f145c82c6da4d98"
CONTRACT_PATH = ".github/scripts/phase109_trusted_contract.py"
REQUIRED_WORKFLOW = ".github/workflows/phase109-required-certification.yml"


class FinalHardeningError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise FinalHardeningError(message)


def _repo_root() -> Path:
    candidates = [Path.cwd().resolve()]
    try:
        candidates.append(Path(__file__).resolve().parents[2])
    except IndexError:
        pass
    for candidate in candidates:
        probe = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            cwd=candidate,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        if probe.returncode == 0:
            return Path(probe.stdout.strip()).resolve()
    raise FinalHardeningError("Cannot resolve repository root for frozen trusted contract")


def _git_show(root: Path, spec: str) -> str:
    proc = subprocess.run(
        ["git", "show", spec],
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    require(proc.returncode == 0, f"Cannot materialize previous trusted contract: {proc.stderr.strip()}")
    return proc.stdout


def _load_previous_contract() -> ModuleType:
    root = _repo_root()
    source = _git_show(root, f"{PREVIOUS_CONTRACT_COMMIT}:{CONTRACT_PATH}")
    require(source.startswith("#!/usr/bin/env python3"), "Previous trusted contract is malformed")
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".py", delete=False) as handle:
        handle.write(source)
        path = Path(handle.name)
    module_name = "_phase109_trusted_contract_frozen_15759"
    try:
        spec = importlib.util.spec_from_file_location(module_name, path)
        require(spec is not None and spec.loader is not None, "Cannot load previous trusted contract module")
        module = importlib.util.module_from_spec(spec)
        sys.modules[module_name] = module
        spec.loader.exec_module(module)
        return module
    finally:
        path.unlink(missing_ok=True)


_PREV = _load_previous_contract()
_LEGACY = _PREV._LEGACY

# Preserve the complete public API expected by every Guardian revision.
for _name in dir(_PREV):
    if not _name.startswith("_") and _name not in globals():
        globals()[_name] = getattr(_PREV, _name)


def _commands(step) -> list[str]:
    return _LEGACY.logical_commands(step.run)


def _tokens(command: str) -> list[str]:
    try:
        lexer = shlex.shlex(command, posix=True, punctuation_chars=";&|()")
        lexer.whitespace_split = True
        lexer.commenters = ""
        return list(lexer)
    except ValueError as exc:
        raise FinalHardeningError(f"Cannot lex trusted shell command: {command}: {exc}") from exc


def _reject_fail_open_shell_mode(step, label: str) -> int:
    checked = 0
    for command in _commands(step):
        tokens = _tokens(command)
        for index, token in enumerate(tokens[:-1]):
            if token == "set" and tokens[index + 1].startswith("+"):
                raise FinalHardeningError(f"Fail-open shell mode in {label}: {command}")
        checked += 1
    return checked


def _require_exact_commands(step, expected: tuple[str, ...], label: str) -> int:
    actual = tuple(_commands(step))
    require(actual == expected, f"Mandatory command sequence changed in {label}: {actual}")
    return len(expected)


def _validate_light_job_sequence(steps) -> int:
    expected = [
        ("Checkout exact candidate HEAD", "actions/checkout"),
        ("Verify exact candidate HEAD", ""),
        ("Revalidate lightweight scope fail closed", ""),
        ("Set up JDK 17", "actions/setup-java"),
        ("Setup Gradle", "gradle/actions/setup-gradle"),
        ("Setup lightweight Android validation signing", ""),
        ("Build lightweight Debug and Release APK/AAB", ""),
        ("Lightweight Release startup smoke", ""),
        ("Upload lightweight exact-head artifacts", "actions/upload-artifact"),
    ]
    actual = []
    for step in [item for item in steps if item.job == "light-validation"]:
        uses_family = step.uses.split("@", 1)[0] if step.uses else ""
        actual.append((step.name, uses_family))
    require(actual == expected, f"LIGHT job step sequence changed: {actual}")
    return len(expected)


def _validate_light_exact_execution(steps) -> int:
    markers = _validate_light_job_sequence(steps)

    verify = _LEGACY.step_for(steps, "light-validation", "Verify exact candidate HEAD")
    markers += _require_exact_commands(
        verify,
        (
            "set -euo pipefail",
            'test "$(git rev-parse HEAD)" = "$AUDIT_HEAD_SHA"',
            'git cat-file -e "$BASE_SHA^{commit}"',
            'git merge-base --is-ancestor "$BASE_SHA" HEAD',
        ),
        "LIGHT exact-HEAD verification",
    )

    revalidate = _LEGACY.step_for(steps, "light-validation", "Revalidate lightweight scope fail closed")
    markers += _require_exact_commands(
        revalidate,
        (
            "set -euo pipefail",
            "classifier_path='.github/scripts/ci_scope.py'",
            'trusted_classifier="$RUNNER_TEMP/phase109_trusted_ci_scope.py"',
            'git diff --quiet "$BASE_SHA"...HEAD -- "$classifier_path"',
            'git show "$BASE_SHA:$classifier_path" > "$trusted_classifier"',
            'test -s "$trusted_classifier"',
            'python3 "$trusted_classifier" self-test',
            'python3 "$trusted_classifier" classify --base "$BASE_SHA" --head HEAD > "$RUNNER_TEMP/light-scope.txt"',
            'grep -q \'^mode=light \' "$RUNNER_TEMP/light-scope.txt"',
        ),
        "LIGHT trusted-scope revalidation",
    )

    signing = _LEGACY.step_for(steps, "light-validation", "Setup lightweight Android validation signing")
    markers += _require_exact_commands(
        signing,
        (
            "set -euo pipefail",
            "cat > app/google-services.json <<'JSON'",
            "mkdir -p ~/.android",
            "keytool -genkeypair -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=CI,C=BR' >/dev/null 2>&1",
            'ks="$RUNNER_TEMP/light-validation-release.jks"',
            'keytool -genkeypair -keystore "$ks" -storepass ci-validation-only -alias ci-validation -keypass ci-validation-only -keyalg RSA -keysize 2048 -validity 30 -dname \'CN=Pro Football Lightweight CI,O=CI,C=BR\' >/dev/null 2>&1',
            'echo "KEYSTORE_PATH=$ks" >> "$GITHUB_ENV"',
            'echo "STORE_PASSWORD=ci-validation-only" >> "$GITHUB_ENV"',
            'echo "KEY_ALIAS=ci-validation" >> "$GITHUB_ENV"',
            'echo "KEY_PASSWORD=ci-validation-only" >> "$GITHUB_ENV"',
        ),
        "LIGHT validation-signing setup",
    )

    build = _LEGACY.step_for(steps, "light-validation", "Build lightweight Debug and Release APK/AAB")
    markers += _require_exact_commands(
        build,
        (
            "set -euo pipefail",
            "chmod +x gradlew",
            "./gradlew assembleDebug assembleRelease bundleRelease --stacktrace",
            "debug_apk=$(find app/build/outputs/apk/debug -maxdepth 1 -name '*.apk' -print -quit)",
            "release_apk=$(find app/build/outputs/apk/release -maxdepth 1 -name '*.apk' -print -quit)",
            "release_aab=$(find app/build/outputs/bundle/release -maxdepth 1 -name '*.aab' -print -quit)",
            'test -n "$debug_apk"',
            'test -n "$release_apk"',
            'test -n "$release_aab"',
            'apksigner=$(find "$ANDROID_HOME/build-tools" -type f -name apksigner | sort -V | tail -n 1)',
            'test -x "$apksigner"',
            '"$apksigner" verify --verbose --print-certs "$release_apk"',
            'jarsigner -verify -strict -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASSWORD" -certs "$release_aab"',
        ),
        "LIGHT build/signature gate",
    )

    startup = _LEGACY.step_for(steps, "light-validation", "Lightweight Release startup smoke")
    markers += _require_exact_commands(
        startup,
        (
            "set -euo pipefail",
            "./gradlew testReleaseUnitTest --tests com.example.StartupSmokeTest --stacktrace",
        ),
        "LIGHT startup gate",
    )
    return markers


def _validate_scope_fail_closed(steps) -> int:
    scope = _LEGACY.step_for(steps, "policy-scope", "Resolve mandatory certification scope")
    return _reject_fail_open_shell_mode(scope, "certification scope selection")


def _validate_final_light_branch(steps) -> int:
    gate = _LEGACY.step_for(
        steps,
        "required-certification",
        "Fail closed unless every required component certified this HEAD and audited base",
    )
    _reject_fail_open_shell_mode(gate, "required certification final gate")
    _PREV._reject_forbidden_shell_primitives(gate, "required certification final gate")
    commands = _commands(gate)
    controls = _LEGACY.control_commands(gate)
    expected_controls = [
        'if [ "${{ github.event_name }}" = "pull_request" ]; then',
        "else",
        "fi",
        "if [ '${{ needs.policy-scope.outputs.light }}' = true ]; then",
        "else",
        "fi",
    ]
    require(controls == expected_controls, f"Final gate control flow changed: {controls}")

    light_if = "if [ '${{ needs.policy-scope.outputs.light }}' = true ]; then"
    expected_branch = [
        light_if,
        "test '${{ needs.light-validation.result }}' = success",
        "else",
        "test '${{ needs.light-validation.result }}' = skipped",
        "fi",
    ]
    try:
        index = commands.index(light_if)
    except ValueError as exc:
        raise FinalHardeningError("Final LIGHT branch missing") from exc
    require(
        commands[index:index + len(expected_branch)] == expected_branch,
        f"Final LIGHT-result checks are not on the unconditional audited path: {commands[index:index + len(expected_branch)]}",
    )
    return len(expected_branch) + len(expected_controls)


def _final_hardening(workflow_path: Path) -> dict[str, int]:
    text = workflow_path.read_text(encoding="utf-8")
    steps = _LEGACY.parse_steps(text)
    return {
        "scopeFailClosedMarkers": _validate_scope_fail_closed(steps),
        "lightExactExecutionMarkers": _validate_light_exact_execution(steps),
        "finalLightBranchMarkers": _validate_final_light_branch(steps),
    }


def validate_base_workflows(root: Path, base_sha: str) -> int:
    return _PREV.validate_base_workflows(root, base_sha)


def validate_candidate_workflow(root: Path, workflow_path: Path) -> dict[str, int]:
    result = dict(_PREV.validate_candidate_workflow(root, workflow_path))
    result.update(_final_hardening(workflow_path))
    return result


def verify(root: Path, base_sha: str, workflow: Path) -> dict[str, object]:
    result = dict(_PREV.verify(root, base_sha, workflow))
    result.update(_final_hardening(workflow))
    result["previousTrustedContract"] = PREVIOUS_CONTRACT_COMMIT
    return result


def _expect_rejected(callable_obj, label: str) -> None:
    try:
        callable_obj()
    except (FinalHardeningError, _PREV.HardeningError, _LEGACY.ContractError):
        return
    raise FinalHardeningError(f"Negative self-test unexpectedly accepted: {label}")


def _self_test_final_hardening() -> None:
    scope_fixture = """jobs:
  policy-scope:
    steps:
      - name: Resolve mandatory certification scope
        shell: bash
        run: |
          set -euo pipefail
          set +e
"""
    scope_steps = _LEGACY.parse_steps(scope_fixture)
    _expect_rejected(lambda: _validate_scope_fail_closed(scope_steps), "standalone set +e in scope selection")

    light_sequence_fixture = """jobs:
  light-validation:
    steps:
      - name: Checkout exact candidate HEAD
        uses: actions/checkout@aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
      - name: Verify exact candidate HEAD
        shell: bash
        run: |
          set -euo pipefail
      - name: Revalidate lightweight scope fail closed
        shell: bash
        run: |
          set -euo pipefail
      - name: Set up JDK 17
        uses: actions/setup-java@bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@cccccccccccccccccccccccccccccccccccccccc
      - name: Setup lightweight Android validation signing
        shell: bash
        run: |
          set -euo pipefail
      - name: Build lightweight Debug and Release APK/AAB
        shell: bash
        run: |
          set -euo pipefail
      - name: Lightweight Release startup smoke
        shell: bash
        run: |
          set -euo pipefail
      - name: Upload lightweight exact-head artifacts
        uses: actions/upload-artifact@dddddddddddddddddddddddddddddddddddddddd
"""
    sequence_steps = _LEGACY.parse_steps(light_sequence_fixture)
    _validate_light_job_sequence(sequence_steps)
    mutated = light_sequence_fixture.replace(
        "      - name: Revalidate lightweight scope fail closed\n",
        "      - name: Replace candidate worktree\n"
        "        shell: bash\n"
        "        run: |\n"
        "          git restore --source=\"$BASE_SHA\" --staged --worktree app\n"
        "      - name: Revalidate lightweight scope fail closed\n",
    )
    _expect_rejected(
        lambda: _validate_light_job_sequence(_LEGACY.parse_steps(mutated)),
        "intervening worktree replacement step",
    )

    final_fixture = """jobs:
  required-certification:
    steps:
      - name: Fail closed unless every required component certified this HEAD and audited base
        shell: bash
        run: |
          set -euo pipefail
          if [ "${{ github.event_name }}" = "pull_request" ]; then
            test -n "$BASE_SHA"
          else
            test -n "$AUDIT_HEAD_SHA"
          fi
          if [ '${{ needs.policy-scope.outputs.light }}' = true ]; then
            test '${{ needs.light-validation.result }}' = success
          else
            test '${{ needs.light-validation.result }}' = skipped
          fi
"""
    final_steps = _LEGACY.parse_steps(final_fixture)
    _validate_final_light_branch(final_steps)
    wrapped = final_fixture.replace(
        "          if [ '${{ needs.policy-scope.outputs.light }}' = true ]; then\n",
        "          if false; then\n"
        "          if [ '${{ needs.policy-scope.outputs.light }}' = true ]; then\n",
    ).replace(
        "          fi\n",
        "          fi\n          fi\n",
        1,
    )
    _expect_rejected(
        lambda: _validate_final_light_branch(_LEGACY.parse_steps(wrapped)),
        "conditional wrapper around final LIGHT checks",
    )


def self_test() -> None:
    _PREV.self_test()
    _self_test_final_hardening()


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
                "previousTrustedContract": PREVIOUS_CONTRACT_COMMIT,
                "failOpenScopeRejected": True,
                "lightStepSequenceBound": True,
                "lightRunBodiesBound": True,
                "finalLightBranchUnconditional": True,
            }, sort_keys=True))
        else:
            root = Path(args.root).resolve()
            workflow = Path(args.workflow)
            if not workflow.is_absolute():
                workflow = root / workflow
            print(json.dumps({"status": "PASS", **verify(root, args.base_sha, workflow)}, indent=2, sort_keys=True))
        return 0
    except (FinalHardeningError, _PREV.HardeningError, _LEGACY.ContractError, OSError, subprocess.SubprocessError) as exc:
        print(f"PHASE10.9 TRUSTED CONTRACT FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
