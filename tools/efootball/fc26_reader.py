"""Minimal FC26 reader used by the offline identity reconciliation pipeline."""
from __future__ import annotations

import csv
from pathlib import Path
from typing import Any

REQUIRED_FIELDS = {
    "player_id", "short_name", "long_name", "dob", "nationality_name",
    "player_positions", "height_cm", "weight_kg", "preferred_foot",
    "overall", "potential", "club_team_id", "club_name", "league_id", "league_name",
}


def read_fc26(path: str | Path) -> list[dict[str, Any]]:
    with Path(path).open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        fields = set(reader.fieldnames or [])
        missing = sorted(REQUIRED_FIELDS - fields)
        if missing:
            raise ValueError(f"FC26 source missing required fields: {missing}")
        records = list(reader)
    identifiers = [row["player_id"] for row in records]
    if len(identifiers) != len(set(identifiers)):
        raise ValueError("FC26 player_id must be unique")
    return records
