"""Readers for the public PlayersDB CSV and header-array JSONL exports."""
from __future__ import annotations

import csv
import json
from pathlib import Path
from typing import Any, Iterable

EXPECTED_FIELDS = [
    "konamiID", "playerName", "shirtName", "fullName", "jpPlayerName",
    "cnPlayerName", "fakeName", "fakeShirtName", "fakeJpPlayerName",
    "fakeCnPlayerName", "nationalities", "age", "birthdate", "height",
    "weight", "strongFoot", "strongHand", "starRating", "registeredPosition",
    "positions", "youthClub", "update_at", "info_id", "added", "source",
    "game_versions", "real_face", "is_system", "base_konami_id",
]

_JSON_FIELDS = {
    "playerName", "shirtName", "jpPlayerName", "cnPlayerName", "fakeName", "fakeShirtName", "fakeJpPlayerName",
    "fakeCnPlayerName", "nationalities", "positions", "source", "game_versions",
    "real_face",
}
_INT_FIELDS = {"age", "height", "weight", "strongFoot", "strongHand", "starRating", "registeredPosition"}


class PlayersDbFormatError(ValueError):
    pass


def _validate_header(header: list[str]) -> None:
    if header != EXPECTED_FIELDS:
        raise PlayersDbFormatError(f"Unexpected PlayersDB fields: {header!r}")


def _record_from_values(values: list[Any], line_number: int) -> dict[str, Any]:
    if len(values) != len(EXPECTED_FIELDS):
        raise PlayersDbFormatError(
            f"PlayersDB row {line_number} has {len(values)} values; expected {len(EXPECTED_FIELDS)}"
        )
    return dict(zip(EXPECTED_FIELDS, values, strict=True))


def iter_jsonl(path: str | Path):
    """Yield PlayersDB records from its header-array JSONL without retaining all rows."""
    with Path(path).open("r", encoding="utf-8") as handle:
        try:
            header = json.loads(next(handle))
        except StopIteration as exc:
            raise PlayersDbFormatError("PlayersDB JSONL is empty") from exc
        if not isinstance(header, list):
            raise PlayersDbFormatError("PlayersDB JSONL first line must be a header array")
        _validate_header(header)
        for line_number, line in enumerate(handle, start=2):
            if not line.strip():
                continue
            values = json.loads(line)
            if not isinstance(values, list):
                raise PlayersDbFormatError(f"PlayersDB JSONL row {line_number} is not an array")
            yield _record_from_values(values, line_number)


def read_jsonl(path: str | Path) -> list[dict[str, Any]]:
    """Read all PlayersDB JSONL records; tests/small callers may prefer this helper."""
    return list(iter_jsonl(path))


def _parse_csv_value(field: str, raw: str) -> Any:
    value = raw.strip()
    if value == "":
        return None
    if field in _JSON_FIELDS:
        try:
            return json.loads(value)
        except json.JSONDecodeError as exc:
            raise PlayersDbFormatError(f"Invalid JSON value in CSV field {field}: {raw!r}") from exc
    if field in _INT_FIELDS:
        return int(value)
    if field == "is_system":
        lowered = value.lower()
        if lowered == "true":
            return True
        if lowered == "false":
            return False
        raise PlayersDbFormatError(f"Invalid boolean in is_system: {raw!r}")
    # konamiID is intentionally kept as a string to match the JSONL export.
    return value


def read_csv(path: str | Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with Path(path).open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames is None:
            raise PlayersDbFormatError("PlayersDB CSV has no header")
        _validate_header(reader.fieldnames)
        for row in reader:
            records.append({field: _parse_csv_value(field, row[field]) for field in EXPECTED_FIELDS})
    return records


def _canonical(value: Any) -> Any:
    if isinstance(value, str):
        return value.strip()
    if isinstance(value, list):
        return [_canonical(item) for item in value]
    if isinstance(value, dict):
        return {key: _canonical(value[key]) for key in sorted(value)}
    return value


def semantic_mismatches(
    csv_records: Iterable[dict[str, Any]], jsonl_records: Iterable[dict[str, Any]]
) -> list[dict[str, Any]]:
    """Return semantic CSV/JSONL differences after type/whitespace normalization."""
    csv_by_id = {str(record["konamiID"]): _canonical(record) for record in csv_records}
    json_by_id = {str(record["konamiID"]): _canonical(record) for record in jsonl_records}
    mismatches: list[dict[str, Any]] = []
    for konami_id in sorted(set(csv_by_id) | set(json_by_id), key=lambda value: int(value)):
        left = csv_by_id.get(konami_id)
        right = json_by_id.get(konami_id)
        if left != right:
            mismatches.append({"konamiId": konami_id, "csv": left, "jsonl": right})
    return mismatches


def validate_unique_konami_ids(records: Iterable[dict[str, Any]]) -> list[str]:
    seen: set[str] = set()
    duplicates: set[str] = set()
    for record in records:
        identifier = str(record["konamiID"])
        if identifier in seen:
            duplicates.add(identifier)
        seen.add(identifier)
    return sorted(duplicates, key=int)


def compare_csv_jsonl_paths(csv_path: str | Path, jsonl_path: str | Path) -> tuple[bool, int, int]:
    """Stream CSV and JSONL in lockstep; return equivalent, mismatch count, row count."""
    mismatch_count = 0
    row_count = 0
    with Path(csv_path).open("r", encoding="utf-8-sig", newline="") as csv_handle, Path(jsonl_path).open("r", encoding="utf-8") as json_handle:
        csv_reader = csv.DictReader(csv_handle)
        if csv_reader.fieldnames is None:
            raise PlayersDbFormatError("PlayersDB CSV has no header")
        _validate_header(csv_reader.fieldnames)
        try:
            json_header = json.loads(next(json_handle))
        except StopIteration as exc:
            raise PlayersDbFormatError("PlayersDB JSONL is empty") from exc
        _validate_header(json_header)

        csv_iter = iter(csv_reader)
        json_iter = iter(json_handle)
        while True:
            try:
                csv_row = next(csv_iter)
                csv_done = False
            except StopIteration:
                csv_done = True
                csv_row = None
            try:
                json_line = next(json_iter)
                json_done = False
            except StopIteration:
                json_done = True
                json_line = None
            if csv_done and json_done:
                break
            if csv_done != json_done:
                mismatch_count += 1
                if not csv_done:
                    row_count += 1
                if not json_done:
                    row_count += 1
                continue
            assert csv_row is not None and json_line is not None
            row_count += 1
            csv_record = {field: _parse_csv_value(field, csv_row[field]) for field in EXPECTED_FIELDS}
            values = json.loads(json_line)
            if not isinstance(values, list):
                raise PlayersDbFormatError(f"PlayersDB JSONL row {row_count + 1} is not an array")
            json_record = _record_from_values(values, row_count + 1)
            if _canonical(csv_record) != _canonical(json_record):
                mismatch_count += 1
    return mismatch_count == 0, mismatch_count, row_count
