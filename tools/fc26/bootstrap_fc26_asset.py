#!/usr/bin/env python3
"""Materialize the deterministic FC26 Android asset from the exact pinned source snapshot."""
from __future__ import annotations

import argparse
import hashlib
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

PINNED_SOURCE_URL = (
    "https://raw.githubusercontent.com/tanh1c/open-fm/"
    "63bab09ab065fca67fbbd8616b58384f984e41b3/"
    "src-engine/crates/ofm_core/src/generator/FC26_20250921.csv"
)
EXPECTED_SOURCE_SHA256 = "4399cb2bcc2a14a2872e76a118f8f4bf64d7954503949c75751a14f33863e3b2"
EXPECTED_SOURCE_SIZE = 10_576_203
REPO_ROOT = Path(__file__).resolve().parents[2]
IMPORTER = REPO_ROOT / "tools/fc26/import_fc26.py"
VALIDATOR = REPO_ROOT / "tools/fc26/validate_fc26.py"
# O conteúdo continua gzip. A extensão neutra evita tratamento especial/inconsistente pelo AssetManager/Robolectric.
DEFAULT_ASSET = REPO_ROOT / "app/src/main/assets/football/fc26/fc26_players_2025-09-19.tsv.bin"
DEFAULT_MANIFEST = REPO_ROOT / "app/src/main/assets/football/fc26/fc26_manifest.json"
DEFAULT_REPORT = REPO_ROOT / "reports/fc26_import_report.json"


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for block in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def valid_existing(asset: Path, manifest: Path) -> bool:
    if not asset.is_file() or not manifest.is_file():
        return False
    check = subprocess.run(
        [sys.executable, str(VALIDATOR), str(asset), str(manifest)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    return check.returncode == 0


def require_exact_source(path: Path) -> None:
    size = path.stat().st_size
    digest = sha256(path)
    if size != EXPECTED_SOURCE_SIZE or digest != EXPECTED_SOURCE_SHA256:
        raise SystemExit(
            "FC26 source snapshot mismatch: "
            f"size={size}/{EXPECTED_SOURCE_SIZE}, sha256={digest}/{EXPECTED_SOURCE_SHA256}"
        )


def materialize(source: Path, asset: Path, manifest: Path, report: Path) -> None:
    asset.parent.mkdir(parents=True, exist_ok=True)
    manifest.parent.mkdir(parents=True, exist_ok=True)
    report.parent.mkdir(parents=True, exist_ok=True)
    subprocess.check_call([
        sys.executable,
        str(IMPORTER),
        str(source),
        "--output", str(asset),
        "--manifest", str(manifest),
        "--report", str(report),
    ])
    subprocess.check_call([
        sys.executable,
        str(VALIDATOR),
        str(asset),
        str(manifest),
    ])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, help="Optional local FC26_20250921.csv; no network used when supplied.")
    parser.add_argument("--asset", type=Path, default=DEFAULT_ASSET)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    args = parser.parse_args()

    if valid_existing(args.asset, args.manifest):
        print(f"FC26 asset already valid: {args.asset}")
        return 0

    if args.source is not None:
        require_exact_source(args.source)
        materialize(args.source, args.asset, args.manifest, args.report)
        return 0

    with tempfile.TemporaryDirectory(prefix="fc26-") as tmp:
        source = Path(tmp) / "FC26_20250921.csv"
        print(f"Downloading pinned FC26 snapshot: {PINNED_SOURCE_URL}")
        urllib.request.urlretrieve(PINNED_SOURCE_URL, source)
        require_exact_source(source)
        materialize(source, args.asset, args.manifest, args.report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
