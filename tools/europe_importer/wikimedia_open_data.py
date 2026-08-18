from __future__ import annotations
import json
import time
import urllib.parse
import urllib.request
from datetime import date
from pathlib import Path
from typing import Any

from .providers import DataProvider, ProviderRequest

HERE = Path(__file__).resolve().parent
USER_AGENT = "Chat-Pro-Football/1.0 (open-data pilot; https://github.com/Leo-Mala/Chat-Pro-Football)"
ENWIKI_API = "https://en.wikipedia.org/w/api.php"
WIKIDATA_API = "https://www.wikidata.org/w/api.php"
SEASON_REFERENCE_DATE = date(2026, 8, 18)
ASSOCIATION_FOOTBALL_PLAYER_QID = "Q937857"
LOAN_QID = "Q2914547"
OVERRIDES_PATH = HERE / "config" / "open_data_verified_overrides_2026_27.json"

WIKIPEDIA_PAGES = {
    "Arsenal FC":"Arsenal F.C.",
    "Aston Villa":"Aston Villa F.C.",
    "AFC Bournemouth":"AFC Bournemouth",
    "Brentford FC":"Brentford F.C.",
    "Brighton & Hove Albion":"Brighton & Hove Albion F.C.",
    "Chelsea FC":"Chelsea F.C.",
    "Coventry City":"Coventry City F.C.",
    "Crystal Palace":"Crystal Palace F.C.",
    "Everton FC":"Everton F.C.",
    "Fulham FC":"Fulham F.C.",
    "Hull City":"Hull City A.F.C.",
    "Ipswich Town":"Ipswich Town F.C.",
    "Leeds United":"Leeds United F.C.",
    "Liverpool FC":"Liverpool F.C.",
    "Manchester City":"Manchester City F.C.",
    "Manchester United":"Manchester United F.C.",
    "Newcastle United":"Newcastle United F.C.",
    "Nottingham Forest":"Nottingham Forest F.C.",
    "Sunderland AFC":"Sunderland A.F.C.",
    "Tottenham Hotspur":"Tottenham Hotspur F.C.",
}

SECTION_PRIORITIES = (
    "current squad", "first-team squad", "first team squad", "first-team players", "players"
)

def _qid_int(qid: str) -> int:
    return int(qid[1:])

def _time_to_iso(value: dict[str, Any] | None) -> str | None:
    if not value:
        return None
    raw = str(value.get("time") or "")
    precision = int(value.get("precision") or 0)
    if precision < 11 or len(raw) < 11:
        return None
    return raw[1:11] if raw.startswith("+") else raw[:10]

def _qualifier_values(statement: dict[str, Any], prop: str) -> list[Any]:
    values = []
    for snak in (statement.get("qualifiers") or {}).get(prop, []):
        value = (snak.get("datavalue") or {}).get("value")
        if value is not None:
            values.append(value)
    return values

def _statement_active(statement: dict[str, Any]) -> bool:
    end_values = _qualifier_values(statement, "P582")
    if not end_values:
        return True
    end_iso = _time_to_iso(end_values[0]) if isinstance(end_values[0], dict) else None
    return end_iso is None or end_iso >= SEASON_REFERENCE_DATE.isoformat()

def _claim_item_ids(entity: dict[str, Any], prop: str) -> list[str]:
    result = []
    for claim in (entity.get("claims") or {}).get(prop, []):
        snak = claim.get("mainsnak") or {}
        value = (snak.get("datavalue") or {}).get("value")
        if isinstance(value, dict) and value.get("id"):
            result.append(str(value["id"]))
    return result

def _active_claim_item_ids(entity: dict[str, Any], prop: str) -> list[str]:
    rows = []
    for claim in (entity.get("claims") or {}).get(prop, []):
        if claim.get("rank") == "deprecated" or not _statement_active(claim):
            continue
        value = (((claim.get("mainsnak") or {}).get("datavalue") or {}).get("value") or {})
        if isinstance(value, dict) and value.get("id"):
            rows.append((0 if claim.get("rank") == "preferred" else 1, str(value["id"])))
    rows.sort()
    return [qid for _, qid in rows]

def _claim_time(entity: dict[str, Any], prop: str) -> str | None:
    for claim in (entity.get("claims") or {}).get(prop, []):
        value = ((claim.get("mainsnak") or {}).get("datavalue") or {}).get("value")
        if isinstance(value, dict):
            parsed = _time_to_iso(value)
            if parsed:
                return parsed
    return None

def _statement_for_team(entity: dict[str, Any], team_qid: str) -> dict[str, Any] | None:
    candidates = []
    for statement in (entity.get("claims") or {}).get("P54", []):
        value = (((statement.get("mainsnak") or {}).get("datavalue") or {}).get("value") or {})
        if value.get("id") != team_qid or not _statement_active(statement):
            continue
        candidates.append(statement)
    if not candidates:
        return None
    candidates.sort(key=lambda s: 0 if s.get("rank") == "preferred" else 1)
    return candidates[0]

def _shirt_number(entity: dict[str, Any], membership: dict[str, Any] | None) -> int | None:
    # P1618 as a top-level player statement is often historical and can collide
    # after transfers. Only trust a number qualified on the current P54 link.
    candidates = _qualifier_values(membership or {}, "P1618")
    for value in candidates:
        try:
            number = int(str(value))
            if 0 < number < 1000:
                return number
        except (TypeError, ValueError):
            pass
    return None

def _position_ids(entity: dict[str, Any], membership: dict[str, Any] | None) -> list[str]:
    result = []
    for value in _qualifier_values(membership or {}, "P413"):
        if isinstance(value, dict) and value.get("id"):
            result.append(str(value["id"]))
    for qid in _claim_item_ids(entity, "P413"):
        if qid not in result:
            result.append(qid)
    return result

def _position_to_provider_label(label: str) -> str | None:
    value = label.strip().casefold().replace("_", " ")
    if "goalkeeper" in value or value == "keeper":
        return "Goalkeeper"
    if any(token in value for token in (
        "defender", "centre-back", "center-back", "full-back", "fullback",
        "wing-back", "wingback", "left-back", "right-back", "sweeper", "stopper",
    )):
        return "Defender"
    if any(token in value for token in (
        "midfielder", "midfield", "wing half", "wing-half", "half-back", "half back", "playmaker",
    )):
        return "Midfielder"
    if any(token in value for token in (
        "forward", "striker", "winger", "attacker",
    )):
        return "Forward"
    return None

def _load_verified_overrides() -> tuple[dict[str, dict[str, str]], dict[str, dict[str, str]], str]:
    if not OVERRIDES_PATH.exists():
        return {}, {}, ""
    doc = json.loads(OVERRIDES_PATH.read_text(encoding="utf-8"))
    positions = {
        str(row["fullName"]): {
            "position": str(row["position"]),
            "source": str(row["source"]),
        }
        for row in doc.get("positions", [])
    }
    stadiums = {
        str(row["club"]): {
            "stadium": str(row["stadium"]),
            "source": str(row["source"]),
        }
        for row in doc.get("stadiums", [])
    }
    return positions, stadiums, str(doc.get("verifiedAsOfIso") or "")

class WikimediaClient:
    def __init__(self, cache_root: Path, min_interval_seconds: float = 0.2):
        self.cache_root = cache_root
        self.min_interval_seconds = max(0.0, min_interval_seconds)
        self._last_call = 0.0

    def get(self, endpoint: str, params: dict[str, Any]) -> dict[str, Any]:
        query = {**params, "format":"json", "formatversion":"2"}
        key = endpoint + "?" + urllib.parse.urlencode(sorted((k, str(v)) for k, v in query.items()))
        import hashlib
        digest = hashlib.sha256(key.encode()).hexdigest()
        path = self.cache_root / f"{digest}.json"
        if path.exists():
            return json.loads(path.read_text(encoding="utf-8"))
        elapsed = time.monotonic() - self._last_call
        if elapsed < self.min_interval_seconds:
            time.sleep(self.min_interval_seconds - elapsed)
        url = endpoint + "?" + urllib.parse.urlencode(query)
        req = urllib.request.Request(url, headers={"User-Agent":USER_AGENT, "Accept":"application/json"})
        self._last_call = time.monotonic()
        with urllib.request.urlopen(req, timeout=30) as response:
            payload = json.loads(response.read().decode("utf-8"))
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(payload, ensure_ascii=False, sort_keys=True), encoding="utf-8")
        return payload

    def page_qid(self, title: str) -> str:
        payload = self.get(ENWIKI_API, {"action":"query","prop":"pageprops","ppprop":"wikibase_item","titles":title})
        pages = (payload.get("query") or {}).get("pages") or []
        qid = (((pages[0] if pages else {}).get("pageprops") or {}).get("wikibase_item"))
        if not qid:
            raise RuntimeError(f"No Wikidata item for enwiki page {title!r}")
        return str(qid)

    def current_squad_links(self, title: str) -> tuple[str, list[str]]:
        sections = self.get(ENWIKI_API, {"action":"parse","page":title,"prop":"sections"})
        rows = ((sections.get("parse") or {}).get("sections") or [])
        chosen = None
        for wanted in SECTION_PRIORITIES:
            chosen = next((r for r in rows if str(r.get("line") or "").strip().casefold() == wanted), None)
            if chosen:
                break
        if chosen is None:
            raise RuntimeError(f"No current/first-team squad section found on {title}")
        section_index = str(chosen["index"])
        parsed = self.get(ENWIKI_API, {"action":"parse","page":title,"section":section_index,"prop":"links"})
        links = [
            str(row["title"]) for row in ((parsed.get("parse") or {}).get("links") or [])
            if int(row.get("ns", -1)) == 0 and row.get("title")
        ]
        return str(chosen.get("line") or ""), sorted(set(links))

    def qids_for_titles(self, titles: list[str]) -> dict[str, str]:
        result: dict[str, str] = {}
        for i in range(0, len(titles), 40):
            batch = titles[i:i+40]
            payload = self.get(
                ENWIKI_API,
                {"action":"query","prop":"pageprops","ppprop":"wikibase_item","redirects":"1","titles":"|".join(batch)}
            )
            for page in (payload.get("query") or {}).get("pages") or []:
                qid = ((page.get("pageprops") or {}).get("wikibase_item"))
                if qid and page.get("title"):
                    result[str(page["title"])] = str(qid)
        return result

    def entities(self, qids: list[str]) -> dict[str, dict[str, Any]]:
        result: dict[str, dict[str, Any]] = {}
        for i in range(0, len(qids), 40):
            batch = qids[i:i+40]
            if not batch:
                continue
            payload = self.get(
                WIKIDATA_API,
                {"action":"wbgetentities","ids":"|".join(batch),"props":"claims|labels|descriptions","languages":"en"}
            )
            for qid, entity in (payload.get("entities") or {}).items():
                if not entity.get("missing"):
                    result[str(qid)] = entity
        return result

def _labels(entities: dict[str, dict[str, Any]], qids: set[str], client: WikimediaClient) -> dict[str, str]:
    missing = sorted(qid for qid in qids if qid not in entities)
    if missing:
        entities.update(client.entities(missing))
    return {
        qid: str(((entity.get("labels") or {}).get("en") or {}).get("value") or qid)
        for qid, entity in entities.items()
    }

class WikimediaOpenDataProvider(DataProvider):
    name = "wikimedia-open-data"

    def __init__(self, cache_root: Path, team_names: list[str]):
        self.client = WikimediaClient(cache_root)
        self.team_names = team_names
        self.position_overrides, self.stadium_overrides, self.overrides_verified_as = _load_verified_overrides()
        self.last_audit: dict[str, Any] = {}

    def collect(self, request: ProviderRequest) -> dict[str, Any]:
        teams_response = []
        players_response = []
        audit = {
            "clubs":[],
            "warnings":[],
            "loanCandidates":[],
            "verifiedOverridesUsed":[],
            "verifiedOverridesAsOfIso": self.overrides_verified_as,
        }
        all_entities: dict[str, dict[str, Any]] = {}

        club_qids: dict[str, str] = {}
        club_pages: dict[str, str] = {}
        for team_name in self.team_names:
            page = WIKIPEDIA_PAGES.get(team_name)
            if not page:
                raise RuntimeError(f"No enwiki page mapping for {team_name}")
            club_pages[team_name] = page
            club_qids[team_name] = self.client.page_qid(page)
        all_entities.update(self.client.entities(list(club_qids.values())))

        for team_name in self.team_names:
            club_qid = club_qids[team_name]
            club_entity = all_entities[club_qid]
            section, links = self.client.current_squad_links(club_pages[team_name])
            title_qids = self.client.qids_for_titles(links)
            linked_qids = sorted(set(title_qids.values()))
            linked_entities = self.client.entities(linked_qids)
            all_entities.update(linked_entities)

            stadium_qids = _active_claim_item_ids(club_entity, "P115")
            city_qids = _active_claim_item_ids(club_entity, "P159") or _active_claim_item_ids(club_entity, "P131")
            related = set(stadium_qids + city_qids)
            labels = _labels(all_entities, related, self.client)
            stadium = labels.get(stadium_qids[0], "") if stadium_qids else ""
            city = labels.get(city_qids[0], "") if city_qids else ""
            stadium_override = self.stadium_overrides.get(team_name)
            if stadium_override:
                stadium = stadium_override["stadium"]
                audit["verifiedOverridesUsed"].append({
                    "kind":"stadium",
                    "club":team_name,
                    "value":stadium,
                    "source":stadium_override["source"],
                })

            transient_team_id = _qid_int(club_qid)
            candidates = []
            rejected = []
            metadata_qids: set[str] = set()

            for linked_qid in linked_qids:
                entity = linked_entities.get(linked_qid) or {}
                if ASSOCIATION_FOOTBALL_PLAYER_QID not in _claim_item_ids(entity, "P106"):
                    continue
                membership = _statement_for_team(entity, club_qid)
                birth = _claim_time(entity, "P569")
                nationality_ids = _claim_item_ids(entity, "P27")
                position_ids = _position_ids(entity, membership)
                description = str(((entity.get("descriptions") or {}).get("en") or {}).get("value") or "")
                description_position = _position_to_provider_label(description)
                metadata_qids.update(nationality_ids)
                metadata_qids.update(position_ids)
                label = str(((entity.get("labels") or {}).get("en") or {}).get("value") or "")
                verified_position = self.position_overrides.get(label)
                missing = []
                if not label: missing.append("name")
                if not birth: missing.append("birthDate")
                if not nationality_ids: missing.append("nationality")
                if not position_ids and description_position is None and verified_position is None:
                    missing.append("position")
                if missing:
                    rejected.append({"qid":linked_qid,"title":label or linked_qid,"missing":missing})
                    continue
                candidates.append((
                    linked_qid, entity, membership, label, birth,
                    nationality_ids, position_ids, description_position, verified_position,
                ))

            metadata_labels = _labels(all_entities, metadata_qids, self.client)
            accepted_count = 0
            p54_crosschecked = 0
            for (
                linked_qid, entity, membership, label, birth,
                nationality_ids, position_ids, description_position, verified_position,
            ) in candidates:
                nationality = metadata_labels.get(nationality_ids[0], nationality_ids[0])
                raw_position = metadata_labels.get(position_ids[0], position_ids[0]) if position_ids else ""
                position = (
                    verified_position["position"] if verified_position
                    else description_position or _position_to_provider_label(raw_position)
                )
                if position is None:
                    rejected.append({
                        "qid":linked_qid,
                        "title":label,
                        "missing":[f"supportedPosition({raw_position})"],
                    })
                    continue
                if verified_position:
                    audit["verifiedOverridesUsed"].append({
                        "kind":"position",
                        "player":label,
                        "club":team_name,
                        "value":position,
                        "source":verified_position["source"],
                    })
                accepted_count += 1
                if membership is not None:
                    p54_crosschecked += 1
                players_response.append({
                    "player":{
                        "id":_qid_int(linked_qid),
                        "name":label,
                        "birth":{"date":birth},
                        "nationality":nationality,
                    },
                    "statistics":[{
                        "team":{"id":transient_team_id},
                        "games":{"position":position,"number":_shirt_number(entity, membership)},
                    }],
                })

                if membership is not None:
                    acquisition_ids = []
                    for value in _qualifier_values(membership, "P1642"):
                        if isinstance(value, dict) and value.get("id"):
                            acquisition_ids.append(str(value["id"]))
                    if LOAN_QID in acquisition_ids:
                        audit["loanCandidates"].append({
                            "playerQid":linked_qid,
                            "player":label,
                            "borrowerClub":team_name,
                            "borrowerClubQid":club_qid,
                            "status":"DETECTED_NOT_MATERIALIZED",
                        })

            teams_response.append({
                "team":{"id":transient_team_id,"name":team_name,"country":"England"},
                "venue":{"name":stadium,"city":city},
            })
            audit["clubs"].append({
                "club":team_name,
                "clubQid":club_qid,
                "wikipediaPage":club_pages[team_name],
                "squadSection":section,
                "candidateLinks":len(links),
                "acceptedPlayers":accepted_count,
                "p54CrossCheckedPlayers":p54_crosschecked,
                "rejectedPlayers":rejected,
                "stadium":stadium,
                "city":city,
            })

        self.last_audit = audit
        return {
            "provider":self.name,
            "teamsResponse":{"response":teams_response},
            "playersResponse":{"response":players_response},
            "transfersResponse":{"response":[]},
            "externalTeamsById":{},
            "externalPlayersById":{},
            "openDataAudit":audit,
        }
