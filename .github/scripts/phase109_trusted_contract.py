#!/usr/bin/env python3
"""Trusted executable contract for Phase 10.9 certification.

This file is designed to be executed from the trusted base commit, not from the candidate
checkout. During the one-time Phase 10.9 bootstrap, the permanent workflow pins an immutable
commit that already contains this contract. After Phase 10.9 reaches main, every later PR must
execute the copy from its audited base SHA.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

BOOTSTRAP_BASE_SHA = "f9980ead5ffdb7c6504b714cde56e4e5f16d5fff"
REQUIRED_WORKFLOW = ".github/workflows/phase109-required-certification.yml"
CONTRACT_PATH = ".github/scripts/phase109_trusted_contract.py"
PINNED_EMULATOR_RUNNER = "reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d"
MANDATORY_TEST_SOURCE_ANCHOR = "236e40691ddd4dd4e3221fec4ef6e24f491bc26e"
AUDIT_HEAD_REF = "${{ env.AUDIT_HEAD_SHA }}"
EXACT_HEAD_ASSERTION = 'test "$(git rev-parse HEAD)" = "$AUDIT_HEAD_SHA"'
CERTIFICATION_CHECKOUT_JOBS = {
    "policy-scope",
    "jvm-build",
    "stress",
    "ui-golden",
    "performance",
    "instrumented",
    "required-certification",
}
PINNED_ACTIONS = {
    "actions/checkout": "d23441a48e516b6c34aea4fa41551a30e30af803",
    "actions/setup-java": "b6effb05e454b25005698d916606bdc6ffcbf961",
    "actions/upload-artifact": "ea165f8d65b6e75b540449e92b4886f43607fa02",
    "gradle/actions/setup-gradle": "9c971963bec38e04b3d30dcc455b5382be2fdbfb",
    "reactivecircus/android-emulator-runner": "a421e43855164a8197daf9d8d40fe71c6996bb0d",
}


@dataclass
class Step:
    job: str
    name: str = ""
    run: str = ""
    uses: str = ""
    if_expr: str = ""
    with_values: dict[str, str] = field(default_factory=dict)


@dataclass(frozen=True)
class RunRule:
    job: str
    step: str
    markers: tuple[str, ...]
    executable_tokens: tuple[str, ...]


@dataclass(frozen=True)
class UsesRule:
    job: str
    step: str
    uses: str
    with_markers: tuple[tuple[str, str], ...] = ()


BASE_RUN_RULES: dict[str, tuple[tuple[str, tuple[str, ...]], ...]] = {
    ".github/workflows/core-regression.yml": (
        ("./gradlew", ("testDebugUnitTest", "-PexcludeStressTests=true", "--stacktrace")),
    ),
    ".github/workflows/migration-safety.yml": (
        ("./gradlew", ("com.example.SaveSlotIsolationTest", "com.example.migrations.MigrationSafetyTest", "com.example.migrations.MigrationCompatibilityTest")),
    ),
    ".github/workflows/release-variant-smoke.yml": (
        ("./gradlew", ("testReleaseUnitTest", "com.example.StartupSmokeTest")),
    ),
    ".github/workflows/long-horizon-stress.yml": (
        ("./gradlew", ("com.example.TwentySeasonStressTest", "com.example.OneHundredSeasonMatchByMatchStressTest")),
    ),
    ".github/workflows/phase105-ui-golden-regression.yml": (
        ("./gradlew", ("com.example.MainMenuScreenshotTest", "com.example.SavesScreenshotTest", "com.example.Phase105CriticalUiGoldenTest", "com.example.Phase105AccessibilityAndResilienceTest")),
    ),
    ".github/workflows/phase108-full-scale-rollover-performance.yml": (
        ("./gradlew", ("com.example.data.Phase108FullScaleSeasonRolloverPerformanceStressTest", "--no-build-cache", "--rerun-tasks")),
    ),
    ".github/workflows/android.yml": (
        ("python3", ("-m unittest discover -s tools/europe_importer/tests -t .",)),
        ("python3", ("tools/fc26/validate_fc26.py",)),
        ("./gradlew", ("com.example.data.Fc26FullSeedIntegrationTest",)),
        ("./gradlew", ("com.example.data.GlobalMainAuditPerformanceStressTest",)),
    ),
}

CANDIDATE_RUN_RULES = (
    RunRule(
        "policy-scope",
        "Audit permanent CI policy, trusted executable contract and Room migration policy",
        (
            'git diff --exit-code "$base"...HEAD -- app/src/main/assets/football/fc26',
            f"git diff --no-renames --diff-filter=DM --exit-code {MANDATORY_TEST_SOURCE_ANCHOR}..HEAD -- app/src/test app/src/androidTest",
        ),
        ("git",),
    ),
    RunRule("jvm-build", "Run European bulk importer tests", ("-m unittest discover -s tools/europe_importer/tests -t .",), ("python3",)),
    RunRule("jvm-build", "Release Variant Startup Smoke", ("testReleaseUnitTest", "--tests com.example.StartupSmokeTest", "--stacktrace"), ("./gradlew",)),
    RunRule("jvm-build", "Core Regression", ("testDebugUnitTest", "-PexcludeStressTests=true", "--stacktrace"), ("./gradlew",)),
    RunRule(
        "jvm-build", "Explicit Migration and Save Recovery certification",
        ("com.example.migrations.MigrationSafetyTest", "com.example.migrations.MigrationCompatibilityTest", "com.example.SaveSlotIsolationTest", "com.example.GamePreferencesRestoreSafetyTest", "com.example.BackupRestoreRoundTripTest", "com.example.Phase106*", "-PexcludeStressTests=true"),
        ("./gradlew",),
    ),
    RunRule("jvm-build", "Materialize exact-head FC26 and 60k reports without cache", ("com.example.data.Fc26FullSeedIntegrationTest", "com.example.data.GlobalMainAuditPerformanceStressTest", "--no-build-cache", "--rerun-tasks"), ("./gradlew",)),
    RunRule("jvm-build", "Validate FC26 exact invariants, provenance and Room migration policy", ("git diff --exit-code -- app/schemas", "phase109_policy.py validate-fc26", "phase109_policy.py validate-room"), ("git", "python3")),
    RunRule("stress", "Execute stress gates", ("com.example.TwentySeasonStressTest", "com.example.OneHundredSeasonMatchByMatchStressTest"), ("./gradlew",)),
    RunRule("ui-golden", "Render and compare critical UI", ("com.example.MainMenuScreenshotTest", "com.example.SavesScreenshotTest", "com.example.Phase105CriticalUiGoldenTest", "com.example.Phase105AccessibilityAndResilienceTest", "-Proborazzi.test.record=true", "--no-build-cache", "--rerun-tasks"), ("./gradlew",)),
    RunRule("performance", "Measure full-scale exact-head rollover", ("com.example.data.Phase108FullScaleSeasonRolloverPerformanceStressTest", "--no-build-cache", "--rerun-tasks", "phase109_policy.py validate-performance"), ("./gradlew", "taskset", "python3")),
    RunRule("instrumented", "Build target and instrumentation APKs", ("assembleDebug assembleDebugAndroidTest", "assembleRelease assembleReleaseAndroidTest bundleRelease"), ("./gradlew",)),
)

CANDIDATE_USES_RULES = (
    UsesRule(
        "instrumented",
        "Execute installed Android certification",
        PINNED_EMULATOR_RUNNER,
        (("script", "bash .github/scripts/phase107_emulator_gate.sh"),),
    ),
)


class ContractError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ContractError(message)


def git_text(root: Path, *args: str) -> str:
    result = subprocess.run(["git", *args], cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    require(result.returncode == 0, f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout


def scalar(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value


def parse_steps(text: str) -> list[Step]:
    lines = text.splitlines()
    steps: list[Step] = []
    in_jobs = False
    current_job: str | None = None
    in_steps = False
    current: Step | None = None
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        indent = len(line) - len(line.lstrip(" "))
        if indent == 0 and stripped == "jobs:":
            in_jobs = True; current_job = None; in_steps = False; current = None; i += 1; continue
        if in_jobs and indent == 2 and re.fullmatch(r"[A-Za-z0-9_-]+:", stripped):
            current_job = stripped[:-1]; in_steps = False; current = None; i += 1; continue
        if current_job and indent == 4 and stripped == "steps:":
            in_steps = True; current = None; i += 1; continue
        if in_steps and indent == 6 and stripped.startswith("- "):
            current = Step(job=current_job or ""); steps.append(current)
            inline = stripped[2:]
            if ":" in inline:
                key, value = inline.split(":", 1)
                if key == "name": current.name = scalar(value)
                elif key == "uses": current.uses = scalar(value)
            i += 1; continue
        if current and indent == 8 and ":" in stripped:
            key, value = stripped.split(":", 1)
            if key == "name": current.name = scalar(value)
            elif key == "uses": current.uses = scalar(value)
            elif key == "if": current.if_expr = scalar(value)
            elif key == "run" and value.strip() in {"|", ">", "|-", ">-"}:
                block: list[str] = []; i += 1
                while i < len(lines):
                    child = lines[i]; child_indent = len(child) - len(child.lstrip(" "))
                    if child.strip() and child_indent <= 8: break
                    block.append("" if not child.strip() else (child[10:] if child.startswith(" " * 10) else child.lstrip())); i += 1
                current.run = "\n".join(block); continue
            elif key == "with" and value.strip() == "":
                i += 1
                while i < len(lines):
                    child = lines[i]; child_indent = len(child) - len(child.lstrip(" "))
                    if child.strip() and child_indent <= 8: break
                    if child.strip() and child_indent == 10 and ":" in child.strip():
                        ckey, cvalue = child.strip().split(":", 1); current.with_values[ckey] = scalar(cvalue)
                    i += 1
                continue
        i += 1
    return steps


def logical_commands(run: str) -> list[str]:
    commands: list[str] = []
    pending = ""
    heredoc_end: str | None = None
    for raw in run.splitlines():
        stripped = raw.strip()
        if heredoc_end is not None:
            if stripped == heredoc_end: heredoc_end = None
            continue
        if not stripped or stripped.startswith("#"): continue
        pending = f"{pending} {stripped}".strip() if pending else stripped
        if pending.endswith("\\"):
            pending = pending[:-1].rstrip(); continue
        heredoc = re.search(r"<<-?\s*['\"]?([A-Za-z0-9_]+)['\"]?", pending)
        commands.append(pending)
        pending = ""
        if heredoc is not None:
            heredoc_end = heredoc.group(1)
    if pending: commands.append(pending)
    return commands


def control_commands(step: Step) -> list[str]:
    pattern = re.compile(r"^(?:if\b|elif\b|else\b|fi\b|while\b|until\b|for\b|case\b|esac\b|select\b)")
    return [cmd.strip() for cmd in logical_commands(step.run) if pattern.search(cmd.strip())]


def validate_control_flow(step: Step, rule: RunRule) -> None:
    controls = control_commands(step)
    if not controls:
        return
    allowed: dict[tuple[str, str], list[str]] = {
        ("policy-scope", "Audit permanent CI policy, trusted executable contract and Room migration policy"): [
            'if [ "${{ github.event_name }}" = "pull_request" ]; then', "else", "fi",
            'if git cat-file -e "$base:$contract_path" 2>/dev/null; then', "else", "fi",
        ],
        ("ui-golden", "Render and compare critical UI"): [
            "for name in main_menu.png saves_empty.png saves_existing.png; do",
        ],
        ("performance", "Measure full-scale exact-head rollover"): [
            'if [ "$PHASE108_PROFILE" = constrained ]; then', "else", "fi"
        ],
        ("instrumented", "Build target and instrumentation APKs"): [
            "if [ '${{ matrix.mode }}' = release ]; then", "else", "fi"
        ],
    }
    expected = allowed.get((rule.job, rule.step))
    require(expected is not None, f"Unexpected shell control flow in required step {rule.job}/{rule.step}: {controls}")
    require(controls == expected, f"Required step control flow changed in {rule.job}/{rule.step}: {controls}")


def validate_shell_mutation(step: Step, rule: RunRule) -> None:
    for command in logical_commands(step.run):
        stripped = command.strip()
        require(not re.search(r"^(?:alias|unalias|function|eval|source|trap|shopt)\b", stripped),
                f"Shell mutation forbidden in required step {rule.job}/{rule.step}: {stripped}")
        require(not re.search(r"^(?:PATH|BASH_ENV|SHELLOPTS)\s*=", stripped),
                f"Shell execution environment mutation forbidden in {rule.job}/{rule.step}: {stripped}")
        require(not re.search(r"^[A-Za-z_][A-Za-z0-9_]*\s*\(\)\s*\{", stripped),
                f"Shell function definition forbidden in {rule.job}/{rule.step}: {stripped}")


def command_is_fail_closed(command: str) -> bool:
    stripped = command.strip()
    if not stripped: return False
    if "||" in stripped or "&&" in stripped or "$(" in stripped or "`" in stripped: return False
    if re.search(r"(?:^|[;|&]\s*)(?:true|false|!)\b", stripped): return False
    if re.search(r"(?:^|[;]\s*)set\s+\+e\b", stripped): return False
    return True


def command_is_executable(command: str, token: str) -> bool:
    stripped = command.strip()
    forbidden = ("echo ", "printf ", "cat ", "true ", "false ", ": ", "export ", "readonly ")
    if stripped.startswith(forbidden) or not command_is_fail_closed(stripped): return False
    if token == "./gradlew": return "./gradlew " in f" {stripped} "
    if token == "taskset": return re.search(r"(?:^|[;|&]\s*)taskset\b", stripped) is not None
    if token == "python3": return re.search(r"(?:^|[;|&]\s*)python3\b", stripped) is not None
    if token == "git": return re.search(r"(?:^|[;|&]\s*)git\b", stripped) is not None
    return token in stripped


def step_for(steps: Iterable[Step], job: str, name: str) -> Step:
    matches = [step for step in steps if step.job == job and step.name == name]
    require(len(matches) == 1, f"Expected exactly one step {job}/{name}; got {len(matches)}")
    step = matches[0]
    require(not step.if_expr.strip(), f"Required step must be unconditional: {job}/{name}; got if={step.if_expr}")
    return step


def validate_run_rule(step: Step, rule: RunRule) -> int:
    require(bool(step.run.strip()), f"Required run step has no executable body: {rule.job}/{rule.step}")
    validate_control_flow(step, rule)
    validate_shell_mutation(step, rule)
    commands = logical_commands(step.run)
    for marker in rule.markers:
        matching = [cmd for cmd in commands if marker in cmd and any(command_is_executable(cmd, token) for token in rule.executable_tokens)]
        require(matching, f"Required executable marker missing from {rule.job}/{rule.step}: {marker}")
    return len(rule.markers)


def validate_base_workflows(root: Path, base_sha: str) -> int:
    count = 0
    for path, rules in BASE_RUN_RULES.items():
        source = git_text(root, "show", f"{base_sha}:{path}")
        steps = parse_steps(source)
        commands = [cmd for step in steps for cmd in logical_commands(step.run)]
        for executable, markers in rules:
            for marker in markers:
                require(any(marker in cmd and command_is_executable(cmd, executable) for cmd in commands), f"Trusted base no longer proves executable command in {path}: {marker}")
                count += 1
    return count


def validate_action_pins(steps: Iterable[Step]) -> int:
    pinned = 0
    for step in steps:
        if not step.uses:
            continue
        require("@" in step.uses, f"Action reference has no immutable ref: {step.job}/{step.name or '<unnamed>'}: {step.uses}")
        action, ref = step.uses.rsplit("@", 1)
        expected = PINNED_ACTIONS.get(action)
        require(expected is not None, f"Unapproved external action in required certification: {step.uses}")
        require(ref == expected, f"Action must be pinned to trusted full SHA: {action}@{expected}; got {step.uses}")
        pinned += 1
    require(pinned > 0, "Required workflow contains no pinned external actions")
    return pinned


def validate_exact_head_checkouts(steps: list[Step]) -> int:
    checkout_action = f"actions/checkout@{PINNED_ACTIONS['actions/checkout']}"
    bound = 0
    for job in sorted(CERTIFICATION_CHECKOUT_JOBS):
        job_steps = [step for step in steps if step.job == job]
        checkouts = [(index, step) for index, step in enumerate(job_steps) if step.uses == checkout_action]
        require(len(checkouts) == 1, f"Expected exactly one pinned candidate checkout in {job}; got {len(checkouts)}")
        checkout_index, checkout = checkouts[0]
        require(not checkout.if_expr.strip(), f"Candidate checkout must be unconditional in {job}")
        require(checkout.with_values.get("ref", "") == AUDIT_HEAD_REF,
                f"Candidate checkout ref must equal {AUDIT_HEAD_REF} in {job}; got {checkout.with_values.get('ref', '')}")
        require(checkout_index + 1 < len(job_steps), f"Candidate checkout has no immediate HEAD assertion in {job}")
        assertion_step = job_steps[checkout_index + 1]
        require(not assertion_step.if_expr.strip(), f"Exact HEAD assertion must be unconditional in {job}")
        require(bool(assertion_step.run.strip()), f"Exact HEAD assertion must be a run step immediately after checkout in {job}")
        require(any(EXACT_HEAD_ASSERTION in command for command in logical_commands(assertion_step.run)),
                f"Immediate exact HEAD assertion missing after checkout in {job}")
        bound += 1
    return bound


def validate_candidate_workflow(root: Path, workflow_path: Path) -> dict[str, int]:
    text = workflow_path.read_text(encoding="utf-8")
    steps = parse_steps(text)
    run_markers = 0
    for rule in CANDIDATE_RUN_RULES:
        run_markers += validate_run_rule(step_for(steps, rule.job, rule.step), rule)
    uses_markers = 0
    for rule in CANDIDATE_USES_RULES:
        step = step_for(steps, rule.job, rule.step)
        require(step.uses == rule.uses, f"Required action changed in {rule.job}/{rule.step}: {step.uses}")
        for key, marker in rule.with_markers:
            actual = step.with_values.get(key, "")
            require(marker in actual, f"Required action input missing in {rule.job}/{rule.step}: {key}={marker}")
            uses_markers += 1
    action_pins = validate_action_pins(steps)
    exact_head_bindings = validate_exact_head_checkouts(steps)
    return {
        "runMarkers": run_markers,
        "usesMarkers": uses_markers,
        "actionPins": action_pins,
        "exactHeadCheckoutBindings": exact_head_bindings,
        "steps": len(steps),
    }


def verify(root: Path, base_sha: str, workflow: Path) -> dict[str, object]:
    candidate_sha = git_text(root, "rev-parse", "HEAD^{commit}").strip()
    resolved_base = git_text(root, "rev-parse", "--verify", f"{base_sha}^{{commit}}").strip()
    require(resolved_base != candidate_sha, "Trusted base must be outside candidate HEAD")
    require(subprocess.run(["git", "merge-base", "--is-ancestor", resolved_base, candidate_sha], cwd=root, check=False).returncode == 0,
            "Audited base must be an ancestor of the exact candidate HEAD")
    base_count = validate_base_workflows(root, resolved_base)
    candidate = validate_candidate_workflow(root, workflow)
    return {"trustedBaseSha": resolved_base, "candidateSha": candidate_sha, "trustedBaseExecutableMarkers": base_count, **candidate}


def self_test() -> None:
    fixture = """jobs:
  jvm-build:
    steps:
      - name: Core Regression
        run: |
          set -euo pipefail
          ./gradlew testDebugUnitTest -PexcludeStressTests=true --stacktrace
"""
    steps = parse_steps(fixture)
    rule = RunRule("jvm-build", "Core Regression", ("testDebugUnitTest",), ("./gradlew",))
    validate_run_rule(step_for(steps, rule.job, rule.step), rule)
    replacements = (
        "echo './gradlew testDebugUnitTest -PexcludeStressTests=true --stacktrace'",
        "# ./gradlew testDebugUnitTest -PexcludeStressTests=true --stacktrace",
        "false && ./gradlew testDebugUnitTest -PexcludeStressTests=true --stacktrace",
        "./gradlew testDebugUnitTest -PexcludeStressTests=true --stacktrace || true",
        "if false; then ./gradlew testDebugUnitTest -PexcludeStressTests=true --stacktrace; fi",
        "$(printf './gradlew') testDebugUnitTest -PexcludeStressTests=true --stacktrace",
    )
    for replacement in replacements:
        bad = fixture.replace("./gradlew testDebugUnitTest -PexcludeStressTests=true --stacktrace", replacement)
        try:
            validate_run_rule(step_for(parse_steps(bad), rule.job, rule.step), rule)
        except ContractError:
            continue
        raise ContractError(f"Structural negative self-test accepted disabled/non-fail-closed command: {replacement}")

    multiline_false = """jobs:
  jvm-build:
    steps:
      - name: Core Regression
        run: |
          set -euo pipefail
          if false; then
            ./gradlew testDebugUnitTest -PexcludeStressTests=true --stacktrace
          fi
"""
    try:
        validate_run_rule(step_for(parse_steps(multiline_false), rule.job, rule.step), rule)
    except ContractError:
        pass
    else:
        raise ContractError("Structural negative self-test accepted required command inside false multiline branch")

    conditional = fixture.replace(
        "      - name: Core Regression\n",
        "      - name: Core Regression\n        if: ${{ github.event_name == 'workflow_dispatch' }}\n",
    )
    try:
        validate_run_rule(step_for(parse_steps(conditional), rule.job, rule.step), rule)
    except ContractError:
        pass
    else:
        raise ContractError("Structural negative self-test accepted conditionally skipped mandatory step")

    good_actions = [
        Step(job="x", uses=f"actions/checkout@{PINNED_ACTIONS['actions/checkout']}"),
        Step(job="x", uses=f"gradle/actions/setup-gradle@{PINNED_ACTIONS['gradle/actions/setup-gradle']}"),
    ]
    require(validate_action_pins(good_actions) == 2, "Pinned action self-test failed")
    try:
        validate_action_pins([Step(job="x", uses="actions/checkout@v6")])
    except ContractError:
        pass
    else:
        raise ContractError("Mutable action tag negative self-test did not fail")

    checkout_steps: list[Step] = []
    for job in CERTIFICATION_CHECKOUT_JOBS:
        checkout_steps += [
            Step(
                job=job,
                uses=f"actions/checkout@{PINNED_ACTIONS['actions/checkout']}",
                with_values={"ref": AUDIT_HEAD_REF},
            ),
            Step(job=job, name="Verify exact candidate HEAD", run=f"set -euo pipefail\n{EXACT_HEAD_ASSERTION}"),
        ]
    require(validate_exact_head_checkouts(checkout_steps) == len(CERTIFICATION_CHECKOUT_JOBS),
            "Exact-head checkout positive self-test failed")
    bad_ref = [Step(**{**step.__dict__, "with_values": dict(step.with_values)}) for step in checkout_steps]
    first_checkout = next(step for step in bad_ref if step.uses.startswith("actions/checkout@"))
    first_checkout.with_values["ref"] = "${{ needs.policy-scope.outputs.base_sha }}"
    try:
        validate_exact_head_checkouts(bad_ref)
    except ContractError:
        pass
    else:
        raise ContractError("Exact-head checkout negative self-test accepted base ref")


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
            print(json.dumps({"status": "PASS", "negativeCases": 10, "pinnedEmulatorRunner": PINNED_EMULATOR_RUNNER, "mandatoryTestAnchor": MANDATORY_TEST_SOURCE_ANCHOR, "pinnedActions": PINNED_ACTIONS, "exactHeadCheckoutJobs": sorted(CERTIFICATION_CHECKOUT_JOBS)}, sort_keys=True))
        else:
            root = Path(args.root).resolve(); result = verify(root, args.base_sha, root / args.workflow)
            print(json.dumps({"status": "PASS", **result}, indent=2, sort_keys=True))
        return 0
    except (ContractError, OSError, subprocess.SubprocessError) as exc:
        print(f"PHASE10.9 TRUSTED CONTRACT FAILED: {exc}", file=sys.stderr); return 1


if __name__ == "__main__":
    import sys
    raise SystemExit(main())
