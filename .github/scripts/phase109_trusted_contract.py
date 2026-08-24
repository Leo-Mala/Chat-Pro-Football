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
        "jvm-build",
        "Run European bulk importer tests",
        ("-m unittest discover -s tools/europe_importer/tests -t .",),
        ("python3",),
    ),
    RunRule(
        "jvm-build",
        "Release Variant Startup Smoke",
        ("testReleaseUnitTest", "--tests com.example.StartupSmokeTest", "--stacktrace"),
        ("./gradlew",),
    ),
    RunRule(
        "jvm-build",
        "Core Regression",
        ("testDebugUnitTest", "-PexcludeStressTests=true", "--stacktrace"),
        ("./gradlew",),
    ),
    RunRule(
        "jvm-build",
        "Explicit Migration and Save Recovery certification",
        (
            "com.example.migrations.MigrationSafetyTest",
            "com.example.migrations.MigrationCompatibilityTest",
            "com.example.SaveSlotIsolationTest",
            "com.example.GamePreferencesRestoreSafetyTest",
            "com.example.BackupRestoreRoundTripTest",
            "com.example.Phase106*",
            "-PexcludeStressTests=true",
        ),
        ("./gradlew",),
    ),
    RunRule(
        "jvm-build",
        "Materialize exact-head FC26 and 60k reports without cache",
        ("com.example.data.Fc26FullSeedIntegrationTest", "com.example.data.GlobalMainAuditPerformanceStressTest", "--no-build-cache", "--rerun-tasks"),
        ("./gradlew",),
    ),
    RunRule(
        "jvm-build",
        "Validate FC26 exact invariants, provenance and Room migration policy",
        ("git diff --exit-code -- app/schemas", "phase109_policy.py validate-fc26", "phase109_policy.py validate-room"),
        ("git", "python3"),
    ),
    RunRule(
        "stress",
        "Execute stress gates",
        ("com.example.TwentySeasonStressTest", "com.example.OneHundredSeasonMatchByMatchStressTest"),
        ("./gradlew",),
    ),
    RunRule(
        "ui-golden",
        "Render and compare critical UI",
        ("com.example.MainMenuScreenshotTest", "com.example.SavesScreenshotTest", "com.example.Phase105CriticalUiGoldenTest", "com.example.Phase105AccessibilityAndResilienceTest", "-Proborazzi.test.record=true", "--no-build-cache", "--rerun-tasks"),
        ("./gradlew",),
    ),
    RunRule(
        "performance",
        "Measure full-scale exact-head rollover",
        ("com.example.data.Phase108FullScaleSeasonRolloverPerformanceStressTest", "--no-build-cache", "--rerun-tasks", "phase109_policy.py validate-performance"),
        ("./gradlew", "taskset", "python3"),
    ),
    RunRule(
        "instrumented",
        "Build target and instrumentation APKs",
        ("assembleDebug assembleDebugAndroidTest", "assembleRelease assembleReleaseAndroidTest bundleRelease"),
        ("./gradlew",),
    ),
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
    result = subprocess.run(
        ["git", *args], cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False
    )
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
            in_jobs = True
            current_job = None
            in_steps = False
            current = None
            i += 1
            continue
        if in_jobs and indent == 2 and re.fullmatch(r"[A-Za-z0-9_-]+:", stripped):
            current_job = stripped[:-1]
            in_steps = False
            current = None
            i += 1
            continue
        if current_job and indent == 4 and stripped == "steps:":
            in_steps = True
            current = None
            i += 1
            continue
        if in_steps and indent == 6 and stripped.startswith("- "):
            current = Step(job=current_job or "")
            steps.append(current)
            inline = stripped[2:]
            if ":" in inline:
                key, value = inline.split(":", 1)
                if key == "name":
                    current.name = scalar(value)
                elif key == "uses":
                    current.uses = scalar(value)
            i += 1
            continue
        if current and indent == 8 and ":" in stripped:
            key, value = stripped.split(":", 1)
            if key == "name":
                current.name = scalar(value)
            elif key == "uses":
                current.uses = scalar(value)
            elif key == "if":
                current.if_expr = scalar(value)
            elif key == "run" and value.strip() in {"|", ">", "|-", ">-"}:
                block: list[str] = []
                i += 1
                while i < len(lines):
                    child = lines[i]
                    child_indent = len(child) - len(child.lstrip(" "))
                    if child.strip() and child_indent <= 8:
                        break
                    if not child.strip():
                        block.append("")
                    else:
                        block.append(child[10:] if child.startswith(" " * 10) else child.lstrip())
                    i += 1
                current.run = "\n".join(block)
                continue
            elif key == "with" and value.strip() == "":
                i += 1
                while i < len(lines):
                    child = lines[i]
                    child_indent = len(child) - len(child.lstrip(" "))
                    if child.strip() and child_indent <= 8:
                        break
                    if child.strip() and child_indent == 10 and ":" in child.strip():
                        ckey, cvalue = child.strip().split(":", 1)
                        current.with_values[ckey] = scalar(cvalue)
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
            if stripped == heredoc_end:
                heredoc_end = None
            continue
        if not stripped or stripped.startswith("#"):
            continue
        heredoc = re.search(r"<<-?\s*['\"]?([A-Za-z0-9_]+)['\"]?", stripped)
        if heredoc and re.match(r"^(?:cat|tee)\b", stripped):
            heredoc_end = heredoc.group(1)
            continue
        pending = f"{pending} {stripped}".strip() if pending else stripped
        if pending.endswith("\\"):
            pending = pending[:-1].rstrip()
            continue
        commands.append(pending)
        pending = ""
    if pending:
        commands.append(pending)
    return commands


def command_is_fail_closed(command: str) -> bool:
    stripped = command.strip()
    if not stripped:
        return False
    # Required certification commands must execute unconditionally and propagate failure. Reject
    # control-flow/short-circuit constructs that could make a textual marker non-executable or turn
    # its failure into success. Also reject shell substitutions in required-marker commands because
    # they can synthesize or rewrite executable text dynamically.
    if "||" in stripped or "&&" in stripped or "$(" in stripped or "`" in stripped:
        return False
    if re.search(r"(?:^|[;|&]\s*)(?:if|then|elif|else|fi|while|until|for|case|esac|select|true|false|!)\b", stripped):
        return False
    if re.search(r"(?:^|[;]\s*)set\s+\+e\b", stripped):
        return False
    return True


def command_is_executable(command: str, token: str) -> bool:
    stripped = command.strip()
    forbidden = ("echo ", "printf ", "cat ", "true ", "false ", ": ", "export ", "readonly ")
    if stripped.startswith(forbidden) or not command_is_fail_closed(stripped):
        return False
    if token == "./gradlew":
        return "./gradlew " in f" {stripped} "
    if token == "taskset":
        return re.search(r"(?:^|[;|&]\s*)taskset\b", stripped) is not None
    if token == "python3":
        return re.search(r"(?:^|[;|&]\s*)python3\b", stripped) is not None
    if token == "git":
        return re.search(r"(?:^|[;|&]\s*)git\b", stripped) is not None
    return token in stripped


def step_for(steps: Iterable[Step], job: str, name: str) -> Step:
    matches = [step for step in steps if step.job == job and step.name == name]
    require(len(matches) == 1, f"Expected exactly one step {job}/{name}; got {len(matches)}")
    step = matches[0]
    require(step.if_expr.strip().lower() not in {"false", "${{ false }}", "0"}, f"Required step is disabled: {job}/{name}")
    return step


def validate_run_rule(step: Step, rule: RunRule) -> int:
    require(bool(step.run.strip()), f"Required run step has no executable body: {rule.job}/{rule.step}")
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
                require(
                    any(marker in cmd and command_is_executable(cmd, executable) for cmd in commands),
                    f"Trusted base no longer proves executable command in {path}: {marker}",
                )
                count += 1
    return count


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
    return {"runMarkers": run_markers, "usesMarkers": uses_markers, "steps": len(steps)}


def verify(root: Path, base_sha: str, workflow: Path) -> dict[str, object]:
    candidate_sha = git_text(root, "rev-parse", "HEAD^{commit}").strip()
    resolved_base = git_text(root, "rev-parse", "--verify", f"{base_sha}^{{commit}}").strip()
    require(resolved_base != candidate_sha, "Trusted base must be outside candidate HEAD")
    base_count = validate_base_workflows(root, resolved_base)
    candidate = validate_candidate_workflow(root, workflow)
    return {
        "trustedBaseSha": resolved_base,
        "candidateSha": candidate_sha,
        "trustedBaseExecutableMarkers": base_count,
        **candidate,
    }


def self_test() -> None:
    fixture = """jobs:\n  jvm-build:\n    steps:\n      - name: Core Regression\n        run: |\n          set -euo pipefail\n          ./gradlew testDebugUnitTest -PexcludeStressTests=true --stacktrace\n"""
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
            bad_steps = parse_steps(bad)
            validate_run_rule(step_for(bad_steps, rule.job, rule.step), rule)
        except ContractError:
            continue
        raise ContractError(f"Structural negative self-test accepted disabled/non-fail-closed command: {replacement}")


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
            print(json.dumps({"status": "PASS", "negativeCases": 6, "pinnedEmulatorRunner": PINNED_EMULATOR_RUNNER}, sort_keys=True))
        else:
            root = Path(args.root).resolve()
            result = verify(root, args.base_sha, root / args.workflow)
            print(json.dumps({"status": "PASS", **result}, indent=2, sort_keys=True))
        return 0
    except (ContractError, OSError, subprocess.SubprocessError) as exc:
        print(f"PHASE10.9 TRUSTED CONTRACT FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    import sys
    raise SystemExit(main())
