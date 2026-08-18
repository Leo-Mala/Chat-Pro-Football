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
    """Discover the active first-team body while excluding explicit non-active subsections."""
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


def _resolve_exact_qid(client: Any, wikipedia_title: str, context: str) -> str:
    resolved = client.qids_for_titles([wikipedia_title])
    resolved_qids = sorted(set(resolved.values()))
    if len(resolved_qids) != 1:
        raise RuntimeError(
            f"{context} page must resolve exactly one QID: {wikipedia_title} "
            f"(resolved={resolved_qids})"
        )
    return resolved_qids[0]


def install_p1532_discovery_bridge(provider: Any, overrides_path: Path) -> None:
    """Bridge only safe transient discovery facts and explicitly verified facts.

    The bridge is fail-closed and QID-bound. It may:
    * mirror a current, unambiguous P1532 into a transient P27 for the legacy preliminary gate;
    * add official squad membership links and fill a missing English label for that exact QID;
    * fill a missing P569 birth date only when an official ``birthDates`` override resolves to one QID.

    These values exist only in the in-memory discovery envelope. They never become provider IDs and
    do not bypass occupation, position, club identity or canonical validation.
    """
    overrides = json.loads(overrides_path.read_text(encoding="utf-8"))
    as_of_iso = str(overrides.get("verifiedAsOfIso") or "").strip()
    try:
        date.fromisoformat(as_of_iso)
    except ValueError as exc:
        raise RuntimeError(f"verifiedAsOfIso must be YYYY-MM-DD: {as_of_iso!r}") from exc

    client = provider.client
    verified_name_by_qid: dict[str, dict[str, str]] = {}
    verified_pages_by_club: dict[str, list[str]] = {}
    for row in overrides.get("squadMemberships", []) or []:
        club_page = str(row.get("clubWikipediaPage") or "").strip()
        player_page = str(row.get("wikipediaTitle") or "").strip()
        full_name = str(row.get("fullName") or "").strip()
        source = str(row.get("source") or "").strip()
        if not all((club_page, player_page, full_name, source)):
            raise RuntimeError(f"Incomplete verified squad membership bridge: {row}")
        qid = _resolve_exact_qid(client, player_page, "Verified squad membership")
        existing = verified_name_by_qid.get(qid)
        if existing and existing["fullName"] != full_name:
            raise RuntimeError(
                f"Conflicting verified squad names for {qid}: {existing['fullName']} vs {full_name}"
            )
        verified_name_by_qid[qid] = {"fullName": full_name, "source": source}
        verified_pages_by_club.setdefault(club_page, []).append(player_page)

    verified_birth_by_qid: dict[str, dict[str, str]] = {}
    for row in overrides.get("birthDates", []) or []:
        player_page = str(row.get("wikipediaTitle") or "").strip()
        full_name = str(row.get("fullName") or "").strip()
        birth_date_iso = str(row.get("birthDateIso") or "").strip()
        source = str(row.get("source") or "").strip()
        if not all((player_page, full_name, birth_date_iso, source)):
            raise RuntimeError(f"Incomplete verified birth-date bridge: {row}")
        try:
            date.fromisoformat(birth_date_iso)
        except ValueError as exc:
            raise RuntimeError(f"Invalid birthDateIso for {full_name}: {birth_date_iso!r}") from exc
        qid = _resolve_exact_qid(client, player_page, "Verified birth date")
        existing = verified_birth_by_qid.get(qid)
        if existing and existing["birthDateIso"] != birth_date_iso:
            raise RuntimeError(
                f"Conflicting verified birth dates for {qid}: "
                f"{existing['birthDateIso']} vs {birth_date_iso}"
            )
        verified_birth_by_qid[qid] = {
            "fullName": full_name,
            "birthDateIso": birth_date_iso,
            "source": source,
        }

    original_entities = client.entities
    original_current_squad_links = client.current_squad_links
    bridged_qids: set[str] = set()
    label_fallback_qids: set[str] = set()
    birth_fallback_qids: set[str] = set()

    def entities(qids: list[str]) -> dict[str, dict[str, Any]]:
        source = original_entities(qids)
        result: dict[str, dict[str, Any]] = {}
        for qid, original_entity in source.items():
            entity = original_entity
            claims = entity.get("claims") or {}
            has_non_deprecated_p27 = any(
                claim.get("rank") != "deprecated"
                and isinstance(
                    ((claim.get("mainsnak") or {}).get("datavalue") or {}).get("value"), dict
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

            verified_name = verified_name_by_qid.get(str(qid))
            current_label = str(((entity.get("labels") or {}).get("en") or {}).get("value") or "").strip()
            if verified_name and not current_label:
                if entity is original_entity:
                    entity = copy.deepcopy(entity)
                entity.setdefault("labels", {})["en"] = {
                    "language": "en",
                    "value": verified_name["fullName"],
                }
                label_fallback_qids.add(str(qid))

            verified_birth = verified_birth_by_qid.get(str(qid))
            has_birth = any(
                claim.get("rank") != "deprecated"
                and isinstance(((claim.get("mainsnak") or {}).get("datavalue") or {}).get("value"), dict)
                and (((claim.get("mainsnak") or {}).get("datavalue") or {}).get("value") or {}).get("time")
                for claim in (entity.get("claims") or {}).get("P569", [])
            )
            if verified_birth and not has_birth:
                if entity is original_entity:
                    entity = copy.deepcopy(entity)
                birth_date_iso = verified_birth["birthDateIso"]
                entity.setdefault("claims", {})["P569"] = [{
                    "rank": "normal",
                    "mainsnak": {
                        "datavalue": {
                            "value": {
                                "time": f"+{birth_date_iso}T00:00:00Z",
                                "precision": 11,
                            }
                        }
                    },
                }]
                birth_fallback_qids.add(str(qid))

            result[str(qid)] = entity
        return result

    def current_squad_links(title: str) -> tuple[str, list[str]]:
        section, links = original_current_squad_links(title)
        forced = verified_pages_by_club.get(title, [])
        return section, sorted(set(links).union(forced))

    client.entities = entities
    client.current_squad_links = current_squad_links
    provider.p1532_discovery_bridged_qids = bridged_qids
    provider.verified_squad_label_fallback_qids = label_fallback_qids
    provider.verified_birth_date_fallback_qids = birth_fallback_qids
    provider.verified_squad_label_sources_by_qid = verified_name_by_qid
    provider.verified_birth_date_sources_by_qid = verified_birth_by_qid
