from __future__ import annotations

import json
import tempfile
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator


def _team_ids(raw: dict[str, Any]) -> dict[str, int]:
    return {
        str((row.get("team") or {}).get("name")): int((row.get("team") or {})["id"])
        for row in (raw.get("teamsResponse") or {}).get("response") or []
        if (row.get("team") or {}).get("name") and (row.get("team") or {}).get("id") is not None
    }


def _matches_team(row: dict[str, Any], team_id: int) -> bool:
    return any(
        int((stat.get("team") or {}).get("id")) == team_id
        for stat in row.get("statistics") or []
        if (stat.get("team") or {}).get("id") is not None
    )


def apply_verified_squad_exclusions(
    raw: dict[str, Any],
    audit: dict[str, Any],
    overrides: dict[str, Any],
) -> None:
    """Remove only a specifically verified false club membership.

    Exclusions are intentionally idempotent. If upstream discovery no longer contains the false
    association, the rule is recorded as NOT_PRESENT and does nothing. If a same-name player exists
    but the optional birth date does not match, or if multiple same-name rows are ambiguous, the
    operation fails closed instead of deleting the wrong person.
    """
    team_ids = _team_ids(raw)
    response = raw.setdefault("playersResponse", {}).setdefault("response", [])
    if not isinstance(response, list):
        raise RuntimeError("playersResponse.response must be a list")

    for exclusion in overrides.get("squadExclusions", []) or []:
        club = str(exclusion.get("club") or "").strip()
        full_name = str(exclusion.get("fullName") or "").strip()
        birth_date = str(exclusion.get("birthDateIso") or "").strip()
        source = str(exclusion.get("source") or "").strip()
        reason = str(exclusion.get("reason") or "").strip()
        if not all((club, full_name, source, reason)):
            raise RuntimeError(f"Incomplete verified squad exclusion: {exclusion}")
        if club not in team_ids:
            raise RuntimeError(f"Verified squad exclusion club not collected: {club}")
        team_id = team_ids[club]

        same_name_in_club = [
            row for row in response
            if str((row.get("player") or {}).get("name") or "").strip() == full_name
            and _matches_team(row, team_id)
        ]
        if not same_name_in_club:
            audit.setdefault("verifiedOverridesUsed", []).append({
                "kind": "squadExclusion",
                "status": "NOT_PRESENT",
                "player": full_name,
                "birthDateIso": birth_date or None,
                "club": club,
                "reason": reason,
                "source": source,
            })
            continue

        if birth_date:
            exact = [
                row for row in same_name_in_club
                if str(((row.get("player") or {}).get("birth") or {}).get("date") or "").strip() == birth_date
            ]
            if not exact:
                found_births = sorted({
                    str(((row.get("player") or {}).get("birth") or {}).get("date") or "")
                    for row in same_name_in_club
                })
                raise RuntimeError(
                    f"Verified squad exclusion identity mismatch for {club}/{full_name}: "
                    f"expected birthDateIso={birth_date}, found={found_births}"
                )
            matches = exact
        else:
            matches = same_name_in_club

        if len(matches) != 1:
            raise RuntimeError(
                f"Verified squad exclusion is ambiguous: {club}/{full_name} "
                f"(matches={len(matches)}); add birthDateIso or stronger identity evidence"
            )

        response.remove(matches[0])
        audit.setdefault("verifiedOverridesUsed", []).append({
            "kind": "squadExclusion",
            "status": "REMOVED",
            "player": full_name,
            "birthDateIso": birth_date or None,
            "club": club,
            "reason": reason,
            "source": source,
        })


@contextmanager
def without_squad_exclusions(overrides_path: Path) -> Iterator[Path]:
    """Yield a temporary config after exclusions have already been applied safely above."""
    doc = json.loads(overrides_path.read_text(encoding="utf-8"))
    doc["squadExclusions"] = []
    with tempfile.TemporaryDirectory(prefix="europe-overrides-") as tmp:
        path = Path(tmp) / overrides_path.name
        path.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        yield path
