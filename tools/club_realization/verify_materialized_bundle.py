#!/usr/bin/env python3
"""Fail-closed verifier for the materialized real-club bundle.

The APK gate must not trust file counts alone. This verifier cross-checks the frozen
1,907-slot baseline, generated Kotlin replacement data, bundled PNG/SVG assets and
the SHA-256 manifest. An incomplete bundle is reported as not ready; a superficially
complete but inconsistent bundle raises an error and fails CI.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

EXPECTED_REPLACEMENTS = 1907
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
SUPPORTED_CREST_EXTENSIONS = {".png", ".svg"}

REPLACEMENT_RE = re.compile(
    r"BrasfootRealClubIdentity\.Replacement\("
    r"legacyTeamId = (?P<id>\d+)L, country = \"(?P<country>(?:\\.|[^\"])*)\", "
    r"division = (?P<division>\d+), legacySlotName = \"(?P<slot>(?:\\.|[^\"])*)\", "
    r"realClubName = \"(?P<club>(?:\\.|[^\"])*)\", crestFileName = \"(?P<crest>(?:\\.|[^\"])*)\"\),"
)


@dataclass(frozen=True)
class Replacement:
    legacy_team_id: int
    country: str
    division: int
    legacy_slot_name: str
    real_club_name: str
    crest_file_name: str


def _unescape_kotlin(value: str) -> str:
    return value.replace("\\\"", '"').replace("\\$", "$").replace("\\\\", "\\")


def read_baseline(path: Path) -> dict[int, tuple[str, int, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    result: dict[int, tuple[str, int, str]] = {}
    for row in rows:
        team_id = int(row["legacyTeamId"])
        value = (row["country"].strip(), int(row["division"]), row["legacySlotName"].strip())
        if team_id in result:
            raise ValueError(f"baseline duplicate legacyTeamId: {team_id}")
        result[team_id] = value
    if len(result) != EXPECTED_REPLACEMENTS:
        raise ValueError(f"baseline count {len(result)} != {EXPECTED_REPLACEMENTS}")
    return result


def read_replacements(path: Path) -> list[Replacement]:
    text = path.read_text(encoding="utf-8")
    return [
        Replacement(
            legacy_team_id=int(match.group("id")),
            country=_unescape_kotlin(match.group("country")),
            division=int(match.group("division")),
            legacy_slot_name=_unescape_kotlin(match.group("slot")),
            real_club_name=_unescape_kotlin(match.group("club")),
            crest_file_name=_unescape_kotlin(match.group("crest")),
        )
        for match in REPLACEMENT_RE.finditer(text)
    ]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_asset(path: Path) -> None:
    extension = path.suffix.casefold()
    data = path.read_bytes()
    if extension == ".png":
        if not data.startswith(PNG_SIGNATURE):
            raise ValueError(f"invalid PNG signature: {path.name}")
        return
    if extension == ".svg":
        folded = data.lower()
        for forbidden in (b"<!doctype", b"<!entity", b"<script", b"javascript:"):
            if forbidden in folded:
                raise ValueError(f"unsafe SVG content in {path.name}")
        try:
            root = ET.fromstring(data)
        except ET.ParseError as exc:
            raise ValueError(f"invalid SVG XML: {path.name}") from exc
        if root.tag.rsplit("}", 1)[-1].casefold() != "svg":
            raise ValueError(f"invalid SVG root: {path.name}")
        if not root.get("viewBox") and not (root.get("width") and root.get("height")):
            raise ValueError(f"SVG without viewBox or dimensions: {path.name}")
        return
    raise ValueError(f"unsupported crest extension: {path.name}")


def read_manifest(path: Path) -> dict[int, dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    required = {"legacyTeamId", "country", "division", "realClubName", "crestFileName", "sha256"}
    if rows and not required.issubset(rows[0]):
        raise ValueError(f"digest manifest missing columns: {sorted(required - set(rows[0]))}")
    result: dict[int, dict[str, str]] = {}
    for row in rows:
        team_id = int(row["legacyTeamId"])
        if team_id in result:
            raise ValueError(f"digest manifest duplicate legacyTeamId: {team_id}")
        result[team_id] = {key: row[key].strip() for key in required}
    return result


def crest_assets(crest_dir: Path) -> list[Path]:
    if not crest_dir.is_dir():
        return []
    return sorted(
        path for path in crest_dir.iterdir()
        if path.is_file() and path.suffix.casefold() in SUPPORTED_CREST_EXTENSIONS
    )


def verify_complete_bundle(
    baseline_path: Path,
    kotlin_path: Path,
    crest_dir: Path,
    manifest_path: Path,
) -> dict[str, str]:
    baseline = read_baseline(baseline_path)
    replacements = read_replacements(kotlin_path)
    manifest = read_manifest(manifest_path)
    assets = crest_assets(crest_dir)
    unsupported_files = sorted(
        path.name for path in crest_dir.iterdir()
        if path.is_file() and path.suffix.casefold() not in SUPPORTED_CREST_EXTENSIONS
    )

    if unsupported_files:
        raise ValueError(f"unsupported files in crest bundle: {unsupported_files[:20]}")
    if len(replacements) != EXPECTED_REPLACEMENTS:
        raise ValueError(f"replacement count {len(replacements)} != {EXPECTED_REPLACEMENTS}")
    if len(manifest) != EXPECTED_REPLACEMENTS:
        raise ValueError(f"digest count {len(manifest)} != {EXPECTED_REPLACEMENTS}")
    if len(assets) != EXPECTED_REPLACEMENTS:
        raise ValueError(f"crest count {len(assets)} != {EXPECTED_REPLACEMENTS}")

    ids = [row.legacy_team_id for row in replacements]
    if len(ids) != len(set(ids)):
        raise ValueError("duplicate legacyTeamId in generated Kotlin")
    if set(ids) != set(baseline):
        raise ValueError("generated Kotlin does not cover exactly the frozen 1,907-slot baseline")
    if set(manifest) != set(baseline):
        raise ValueError("digest manifest does not cover exactly the frozen 1,907-slot baseline")

    real_keys: set[tuple[str, str]] = set()
    crest_names: set[str] = set()
    actual_asset_names = {path.name.casefold(): path for path in assets}
    if len(actual_asset_names) != len(assets):
        raise ValueError("case-insensitive duplicate crest names in asset bundle")

    for row in replacements:
        baseline_value = baseline[row.legacy_team_id]
        if (row.country, row.division, row.legacy_slot_name) != baseline_value:
            raise ValueError(f"baseline slot mismatch for legacyTeamId {row.legacy_team_id}")

        real_key = (row.country.casefold(), row.real_club_name.casefold())
        if real_key in real_keys:
            raise ValueError(f"duplicate real club: {row.country} / {row.real_club_name}")
        real_keys.add(real_key)

        crest_key = row.crest_file_name.casefold()
        if crest_key in crest_names:
            raise ValueError(f"crest reused by more than one club: {row.crest_file_name}")
        crest_names.add(crest_key)
        asset = actual_asset_names.get(crest_key)
        if asset is None:
            raise ValueError(f"missing crest asset: {row.crest_file_name}")
        validate_asset(asset)

        digest_row = manifest[row.legacy_team_id]
        expected_fields = {
            "country": row.country,
            "division": str(row.division),
            "realClubName": row.real_club_name,
            "crestFileName": row.crest_file_name,
        }
        for field, expected in expected_fields.items():
            if digest_row[field] != expected:
                raise ValueError(f"manifest {field} mismatch for legacyTeamId {row.legacy_team_id}")
        declared_digest = digest_row["sha256"].lower()
        if not re.fullmatch(r"[0-9a-f]{64}", declared_digest):
            raise ValueError(f"invalid sha256 syntax for legacyTeamId {row.legacy_team_id}")
        actual_digest = sha256(asset)
        if declared_digest != actual_digest:
            raise ValueError(f"sha256 mismatch for {row.crest_file_name}")

    if crest_names != set(actual_asset_names):
        raise ValueError("asset directory contains crests not referenced by generated replacements")

    return {
        "ready": "true",
        "reason": "complete-and-verified",
        "replacement_rows": str(len(replacements)),
        "crest_count": str(len(assets)),
        "digest_rows": str(len(manifest)),
    }


def probe(args: argparse.Namespace) -> dict[str, str]:
    kotlin_exists = args.kotlin_file.is_file()
    manifest_exists = args.digest_file.is_file()
    crest_count = len(crest_assets(args.crest_dir))

    # Missing/incomplete materialization is an expected development state: defer APK without failing CI.
    if not kotlin_exists or not manifest_exists or crest_count != EXPECTED_REPLACEMENTS:
        replacement_rows = len(read_replacements(args.kotlin_file)) if kotlin_exists else 0
        digest_rows = len(read_manifest(args.digest_file)) if manifest_exists else 0
        reason_parts = []
        if not kotlin_exists:
            reason_parts.append("replacement-data-missing")
        elif replacement_rows != EXPECTED_REPLACEMENTS:
            reason_parts.append(f"replacement-count-{replacement_rows}-of-{EXPECTED_REPLACEMENTS}")
        if crest_count != EXPECTED_REPLACEMENTS:
            reason_parts.append(f"crest-count-{crest_count}-of-{EXPECTED_REPLACEMENTS}")
        if not manifest_exists:
            reason_parts.append("digest-manifest-missing")
        elif digest_rows != EXPECTED_REPLACEMENTS:
            reason_parts.append(f"digest-count-{digest_rows}-of-{EXPECTED_REPLACEMENTS}")
        return {
            "ready": "false",
            "reason": "+".join(reason_parts) or "incomplete",
            "replacement_rows": str(replacement_rows),
            "crest_count": str(crest_count),
            "digest_rows": str(digest_rows),
        }

    return verify_complete_bundle(args.baseline, args.kotlin_file, args.crest_dir, args.digest_file)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, default=Path("docs/club-realization/generated-filler-slots.csv"))
    parser.add_argument("--kotlin-file", type=Path, default=Path("app/src/main/java/com/example/data/BrasfootRealClubReplacementData.kt"))
    parser.add_argument("--crest-dir", type=Path, default=Path("app/src/main/assets/club_crests"))
    parser.add_argument("--digest-file", type=Path, default=Path("docs/club-realization/crest-sha256.csv"))
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()

    result = probe(args)
    for key, value in result.items():
        print(f"{key}={value}")
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as handle:
            for key, value in result.items():
                handle.write(f"{key}={value}\n")


if __name__ == "__main__":
    main()
