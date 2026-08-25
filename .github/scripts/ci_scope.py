#!/usr/bin/env python3
"""Risk-based CI scope classifier for Pro Football pull requests.

PRs are classified as:
- light: launcher/text/visual resource changes plus safe version/manifest wiring only;
- full: any production code, persistence, data, build logic, tooling, tests or CI policy change;
- none: documentation-only or otherwise non-runtime changes.

Pushes to main are deliberately forced to full by the calling workflow.
"""
from __future__ import annotations

import argparse
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


LIGHT_RESOURCE_RE = re.compile(
    r"^app/src/main/res/(?:drawable[^/]*/|mipmap[^/]*/|font[^/]*/).+"
)
LIGHT_VALUES_RE = re.compile(
    r"^app/src/main/res/values[^/]*/(?:strings|colors|dimens|styles|themes)\.xml$"
)
MANIFEST_PATH = "app/src/main/AndroidManifest.xml"
APP_BUILD_PATH = "app/build.gradle.kts"

# Non-runtime documentation may pass with policy-only validation. AGENTS.md is deliberately
# excluded because it defines CI and automatic-merge policy and therefore requires full certification.
NEUTRAL_RE = re.compile(
    r"^(?:README(?:\.[^/]*)?|docs/.*|CHANGELOG(?:\.[^/]*)?|LICENSE(?:\.[^/]*)?|.*\.md)$",
    re.IGNORECASE,
)

FULL_PREFIXES = (
    ".github/",
    "app/schemas/",
    "app/src/test/",
    "app/src/androidTest/",
    "app/src/main/assets/",
    "app/src/main/java/",
    "app/src/main/kotlin/",
    "tools/",
    "gradle/",
    "buildSrc/",
    "build-logic/",
    "reports/fc26_",
    "reports/phase_10_8_",
)
FULL_EXACT = {
    "AGENTS.md",
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "metadata.json",
}


@dataclass(frozen=True)
class Classification:
    mode: str
    light: bool
    full: bool
    reason: str
    changed: tuple[str, ...]


def git(root: Path, *args: str) -> str:
    proc = subprocess.run(
        ["git", *args], cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False
    )
    if proc.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed: {proc.stderr.strip()}")
    return proc.stdout


def changed_payload(root: Path, base: str, head: str, path: str) -> list[str]:
    text = git(root, "diff", "--no-ext-diff", "--unified=0", f"{base}...{head}", "--", path)
    payload: list[str] = []
    for line in text.splitlines():
        if line.startswith(("+++", "---", "@@", "diff --git ", "index ")):
            continue
        if line.startswith(("+", "-")):
            payload.append(line[1:].strip())
    return payload


def manifest_visual_only(lines: Iterable[str]) -> bool:
    seen = False
    # Require the complete changed XML payload line to be exactly one approved visual attribute.
    # Prefix-only matching is intentionally forbidden because a second non-visual attribute could
    # otherwise be placed on the same line and incorrectly receive lightweight certification.
    allowed = re.compile(
        r"^android:(?:icon|roundIcon|label|theme)\s*=\s*(?:\"[^\"\r\n]+\"|'[^'\r\n]+')$"
    )
    for line in lines:
        if not line:
            continue
        if re.fullmatch(r"<!--(?:.|\\s)*-->", line):
            continue
        seen = True
        if not allowed.fullmatch(line):
            return False
    return seen


def build_version_only(lines: Iterable[str]) -> bool:
    seen = False
    # Only literal version metadata assignments are lightweight. Kotlin permits semicolon-separated
    # statements, so prefix matching here would allow build logic to be smuggled onto a version line.
    version_code = re.compile(r"^versionCode\s*=\s*\d+$")
    version_name = re.compile(r'^versionName\s*=\s*"[^"\r\n]+"$')
    for line in lines:
        if not line:
            continue
        if line.startswith("//"):
            continue
        seen = True
        if not (version_code.fullmatch(line) or version_name.fullmatch(line)):
            return False
    return seen


def is_light_resource(path: str) -> bool:
    return bool(LIGHT_RESOURCE_RE.match(path) or LIGHT_VALUES_RE.match(path))


def classify_paths(
    changed: Iterable[str],
    *,
    manifest_lines: Iterable[str] | None = None,
    build_lines: Iterable[str] | None = None,
) -> Classification:
    paths = tuple(sorted({p.strip() for p in changed if p.strip()}))
    if not paths:
        return Classification("none", False, False, "no changed files", paths)

    saw_light = False
    for path in paths:
        if is_light_resource(path):
            saw_light = True
            continue
        if path == MANIFEST_PATH:
            if manifest_lines is not None and manifest_visual_only(manifest_lines):
                saw_light = True
                continue
            return Classification("full", False, True, "AndroidManifest contains non-visual change", paths)
        if path == APP_BUILD_PATH:
            if build_lines is not None and build_version_only(build_lines):
                saw_light = True
                continue
            return Classification("full", False, True, "app build logic changed beyond version metadata", paths)
        if path in FULL_EXACT or any(path.startswith(prefix) for prefix in FULL_PREFIXES):
            return Classification("full", False, True, f"runtime/high-risk path changed: {path}", paths)
        if path.startswith("app/"):
            return Classification("full", False, True, f"unclassified app path changed: {path}", paths)
        if path.startswith("reports/") and not path.endswith(".md"):
            return Classification("full", False, True, f"machine-audited report changed: {path}", paths)
        if NEUTRAL_RE.match(path):
            continue
        # Unknown files fail closed into full certification.
        return Classification("full", False, True, f"unknown path changed: {path}", paths)

    if saw_light:
        return Classification("light", True, False, "visual/text/launcher-only change", paths)
    return Classification("none", False, False, "documentation-only change", paths)


def classify_git(root: Path, base: str, head: str) -> Classification:
    changed = [
        line.strip()
        for line in git(root, "diff", "--no-renames", "--name-only", f"{base}...{head}").splitlines()
        if line.strip()
    ]
    manifest_lines = changed_payload(root, base, head, MANIFEST_PATH) if MANIFEST_PATH in changed else None
    build_lines = changed_payload(root, base, head, APP_BUILD_PATH) if APP_BUILD_PATH in changed else None
    return classify_paths(changed, manifest_lines=manifest_lines, build_lines=build_lines)


def write_github_output(path: Path, result: Classification) -> None:
    with path.open("a", encoding="utf-8") as out:
        out.write(f"mode={result.mode}\n")
        out.write(f"light={'true' if result.light else 'false'}\n")
        out.write(f"full={'true' if result.full else 'false'}\n")
        safe_reason = result.reason.replace("\n", " ")
        out.write(f"reason={safe_reason}\n")


def self_test() -> None:
    cases = [
        (
            "launcher",
            ["app/src/main/res/drawable-nodpi/ic_launcher.webp", "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml"],
            None,
            None,
            "light",
        ),
        ("strings", ["app/src/main/res/values/strings.xml"], None, None, "light"),
        (
            "manifest icon",
            [MANIFEST_PATH],
            ['android:icon="@mipmap/ic_launcher_301"', 'android:roundIcon="@mipmap/ic_launcher_301"'],
            None,
            "light",
        ),
        (
            "version bump",
            [APP_BUILD_PATH],
            None,
            ["versionCode = 32", 'versionName = "3.0.1"', "// release comment"],
            "light",
        ),
        (
            "icon release bundle",
            [
                "app/src/main/res/drawable-nodpi/ic_launcher.webp",
                MANIFEST_PATH,
                APP_BUILD_PATH,
            ],
            ['android:icon="@mipmap/ic_launcher_301"'],
            ["versionCode = 32", 'versionName = "3.0.1"'],
            "light",
        ),
        (
            "manifest permission",
            [MANIFEST_PATH],
            ['<uses-permission android:name="android.permission.CAMERA" />'],
            None,
            "full",
        ),
        (
            "manifest same-line smuggling",
            [MANIFEST_PATH],
            ['android:icon="@mipmap/x" android:allowBackup="false"'],
            None,
            "full",
        ),
        (
            "manifest comment-prefix smuggling",
            [MANIFEST_PATH],
            ['<!-- audit note --> <uses-permission android:name="android.permission.CAMERA" />', 'android:icon="@mipmap/x"'],
            None,
            "full",
        ),
        (
            "build plugin",
            [APP_BUILD_PATH],
            None,
            ['implementation("x:y:1")'],
            "full",
        ),
        (
            "version same-line smuggling",
            [APP_BUILD_PATH],
            None,
            ['versionName = "3.0.2"; minSdk = 35'],
            "full",
        ),
        ("game code", ["app/src/main/java/com/example/GameEngine.kt"], None, None, "full"),
        ("room", ["app/schemas/com.example.data.AppDatabase/22.json"], None, None, "full"),
        ("ci policy", [".github/workflows/android.yml"], None, None, "full"),
        ("agents policy", ["AGENTS.md"], None, None, "full"),
        (
            "mixed visual and code",
            ["app/src/main/res/drawable/logo.xml", "app/src/main/java/com/example/GameEngine.kt"],
            None,
            None,
            "full",
        ),
        ("docs", ["README.md", "docs/release.md"], None, None, "none"),
    ]
    for name, paths, manifest, build, expected in cases:
        actual = classify_paths(paths, manifest_lines=manifest, build_lines=build).mode
        if actual != expected:
            raise AssertionError(f"{name}: expected {expected}, got {actual}")
    print(f"risk-based CI scope self-test: PASS ({len(cases)}/{len(cases)})")


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    classify = sub.add_parser("classify")
    classify.add_argument("--base", required=True)
    classify.add_argument("--head", default="HEAD")
    classify.add_argument("--root", default=".")
    classify.add_argument("--github-output", default=None)
    classify.add_argument("--changed-output", default=None)
    sub.add_parser("self-test")
    args = parser.parse_args()

    if args.command == "self-test":
        self_test()
        return 0

    root = Path(args.root).resolve()
    result = classify_git(root, args.base, args.head)
    if args.github_output:
        write_github_output(Path(args.github_output), result)
    if args.changed_output:
        Path(args.changed_output).write_text("\n".join(result.changed) + ("\n" if result.changed else ""), encoding="utf-8")
    print(f"mode={result.mode} reason={result.reason}")
    for path in result.changed:
        print(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
