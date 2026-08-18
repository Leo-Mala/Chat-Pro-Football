from __future__ import annotations

import copy
import json
from datetime import date
from pathlib import Path
from typing import Any

from .open_data_postprocess import _select_current_sport_country
from .wikimedia_open_data import ENWIKI_API, SECTION_PRIORITIES

_EXCLUDED_NESTED_TOKENS = (
    "out on loan",
    "on loan",
    "b team",
    "under-21",
    "under 21",
    "academy",
    "reserves",
    "reserve team",
)


def _page_links(parsed: dict[str, Any]) -> set[str]:
    return {
        str(row["title"])
        for row in ((parsed.get("parse") or {}).get("links") or [])
        if int(row.get("ns", -1)) == 0 and row.get("title")
    }


def _excluded_nested_heading(line: Any) -> bool:
    value = str(line or "").strip().casefold()
    return any(token in value for token in _EXCLUDED_NESTED_TOKENS)


def install_current_squad_only_discovery(provider: Any) -> None:
    """Discover the active first-team body while excluding explicit non-active subsections.

    ``prop=links`` expands MediaWiki squad templates, which is required for many club pages. Parent
    sections may also include nested headings. We keep active nested headings (for example position
    groups) and subtract only clearly non-active headings such as ``Out on loan``, ``B Team`` or
    academy/reserve sections. This avoids both historical loan contamination and false empty squads.
    """
    client = provider.client

    def current_squad_links(title: str) -> tuple[str, list[str]]:
        sections = client.get(ENWIKI_API, {"action": "parse", "page": title, "prop": "sections"})
        rows = ((sections.get("parse") or {}).get("sections") or [])
        chosen = None
        chosen_pos = -1
        priorities = tuple(SECTION_PRIORITIES) + ("first team",)
        for wanted in priorities:
            for index, row in enumerate(rows):
                if str(row.get("line") or "").strip().casefold() == wanted:
                    chosen = row
                    chosen_pos = index
                    break
            if chosen:
                break
        if chosen is None:
            raise RuntimeError(f"No current/first-team squad section found on {title}")

        section_index = str(chosen["index"])
        try:
            chosen_level = int(chosen.get("level") or chosen.get("toclevel") or 0)
        except (TypeError, ValueError):
            chosen_level = 0

        excluded_indices: list[str] = []
        for row in rows[chosen_pos + 1 :]:
            try:
                level = int(row.get("level") or row.get("toclevel") or 0)
            except (TypeError, ValueError):
                level = 0
            if chosen_level and level and level <= chosen_level:
                break
            if row.get("index") is not None and _excluded_nested_heading(row.get("line")):
                excluded_indices.append(str(row["index"]))

        parent = client.get(
            ENWIKI_API,
            {"action": "parse", "page": title, "section": section_index, "prop": "links"},
        )
        links = _page_links(parent)

        for nested_index in excluded_indices:
            nested = client.get(
                ENWIKI_API,
                {"action": "parse", "page": title, "section": nested_index, "prop": "links"},
            )
            links.difference_update(_page_links(nested))

        return str(chosen.get("line") or ""), sorted(links)

    client.current_squad_links = current_squad_links


def install_p1532_discovery_bridge(provider: Any, overrides_path: Path) -> None:
    """Let P1532-only players pass preliminary discovery without weakening final nationality rules."""
    overrides = json.loads(overrides_path.read_text(encoding="utf-8"))
    as_of_iso = str(overrides.get("verifiedAsOfIso") or "").strip()
    try:
        date.fromisoformat(as_of_iso)
    except ValueError as exc:
        raise RuntimeError(f"verifiedAsOfIso must be YYYY-MM-DD: {as_of_iso!r}") from exc

    client = provider.client
    original_entities = client.entities
    bridged_qids: set[str] = set()

    def entities(qids: list[str]) -> dict[str, dict[str, Any]]:
        source = original_entities(qids)
        result: dict[str, dict[str, Any]] = {}
        for qid, original_entity in source.items():
            entity = original_entity
            claims = entity.get("claims") or {}
            has_non_deprecated_p27 = any(
                claim.get("rank") != "deprecated"
                and isinstance(
                    ((claim.get("mainsnak") or {}).get("datavalue") or {}).get("value"),
                    dict,
                )
                and (((claim.get("mainsnak") or {}).get("datavalue") or {}).get("value") or {}).get("id")
                for claim in claims.get("P27", [])
            )
            if not has_non_deprecated_p27:
                selected, status = _select_current_sport_country(entity, as_of_iso)
                if selected and status in {"PREFERRED", "NORMAL"}:
                    entity = copy.deepcopy(entity)
                    entity.setdefault("claims", {})["P27"] = [{
                        "rank": "normal",
                        "mainsnak": {"datavalue": {"value": {"id": selected}}},
                    }]
                    bridged_qids.add(str(qid))
            result[str(qid)] = entity
        return result

    client.entities = entities
    provider.p1532_discovery_bridged_qids = bridged_qids
