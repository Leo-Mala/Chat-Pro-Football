from __future__ import annotations

import copy
import json
import re
from datetime import date
from pathlib import Path
from typing import Any

from .open_data_postprocess import _select_current_sport_country
from .wikimedia_open_data import ENWIKI_API, SECTION_PRIORITIES

_NESTED_HEADING_RE = re.compile(r"(?m)^={3,}\s*.*?\s*={3,}\s*$")
_WIKILINK_RE = re.compile(r"\[\[([^\[\]|#]+)")


def install_current_squad_only_discovery(provider: Any) -> None:
    """Keep only the selected current/first-team section body, excluding nested subsections.

    MediaWiki's `prop=links` for a section can include links from nested subsections such as
    "Out on loan". For a factual active-squad snapshot that is unsafe: a loaned-out player can be
    rediscovered as active. This wrapper reads the selected section wikitext and stops at the first
    nested heading before extracting links. Player eligibility is still decided later by Wikidata
    occupation/fact validation.
    """
    client = provider.client

    def current_squad_links(title: str) -> tuple[str, list[str]]:
        sections = client.get(ENWIKI_API, {"action": "parse", "page": title, "prop": "sections"})
        rows = ((sections.get("parse") or {}).get("sections") or [])
        chosen = None
        for wanted in SECTION_PRIORITIES:
            chosen = next(
                (row for row in rows if str(row.get("line") or "").strip().casefold() == wanted),
                None,
            )
            if chosen:
                break
        if chosen is None:
            raise RuntimeError(f"No current/first-team squad section found on {title}")

        section_index = str(chosen["index"])
        parsed = client.get(
            ENWIKI_API,
            {"action": "parse", "page": title, "section": section_index, "prop": "wikitext"},
        )
        wikitext = (parsed.get("parse") or {}).get("wikitext") or ""
        if isinstance(wikitext, dict):
            wikitext = wikitext.get("*") or ""
        text = str(wikitext)
        first_nested = _NESTED_HEADING_RE.search(text)
        if first_nested:
            text = text[: first_nested.start()]

        links: list[str] = []
        for raw_target in _WIKILINK_RE.findall(text):
            target = raw_target.strip().replace("_", " ")
            if not target or ":" in target:
                continue
            links.append(target)
        return str(chosen.get("line") or ""), sorted(set(links))

    client.current_squad_links = current_squad_links


def install_p1532_discovery_bridge(provider: Any, overrides_path: Path) -> None:
    """Let P1532-only players pass preliminary discovery without weakening final nationality rules.

    The legacy collector historically required P27 before the post-processing stage. For a player
    whose only structured nationality is a current, unambiguous P1532, that caused an early false
    rejection. This wrapper mirrors the selected P1532 into a transient P27 claim *only in the
    in-memory discovery response*. The final nationality is still re-resolved from original P1532
    semantics (preferred/deprecated/time qualifiers/ambiguity) and official overrides later.
    Nothing synthetic is persisted to canonical data.
    """
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
