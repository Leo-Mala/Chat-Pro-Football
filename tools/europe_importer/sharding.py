from __future__ import annotations
import copy
import json
import re
from pathlib import Path
from typing import Any

from .pipeline import build_manifest


def _slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", value.casefold()).strip("-")


def shard_by_club(dataset: dict[str, Any]) -> list[tuple[str, dict[str, Any]]]:
    shards: list[tuple[str, dict[str, Any]]] = []
    loans_written = False
    for league in dataset["leagues"]:
        for club in league["clubs"]:
            shard = {
                "schemaVersion": dataset["schemaVersion"],
                "datasetKind": dataset["datasetKind"],
                "provider": dataset["provider"],
                "season": dataset["season"],
                "generatedAt": dataset["generatedAt"],
                "leagues": [{**copy.deepcopy(league), "clubs": [copy.deepcopy(club)]}],
                "loans": copy.deepcopy(dataset.get("loans", [])) if not loans_written else [],
            }
            loans_written = True
            name = f"{_slug(league['country'])}__{_slug(league['name'])}__{_slug(club['name'])}.json"
            shards.append((name, shard))
    return shards


def write_sharded_dataset(dataset: dict[str, Any], output_dir: Path) -> dict[str, Any]:
    shards = shard_by_club(dataset)
    status = "FIXTURE_ONLY" if dataset["datasetKind"] == "FIXTURE" else "VALIDATED"
    manifest = build_manifest(dataset, status, [name for name, _ in shards])
    output_dir.mkdir(parents=True, exist_ok=True)
    for name, payload in shards:
        (output_dir / name).write_text(
            json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n",
            encoding="utf-8",
        )
    (output_dir / "dataset_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return manifest
