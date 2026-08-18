from __future__ import annotations
import hashlib
import json
import os
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any

@dataclass(frozen=True)
class ProviderRequest:
    country: str
    league: str
    season_label: str
    provider_league_id: int | None = None
    provider_season_id: int | None = None
    api_season: int = 2026
    fetch_transfers: bool = True

class JsonCache:
    def __init__(self, root: Path):
        self.root = root

    def get_or_fetch(self, provider: str, cache_key: str, fetcher) -> dict[str, Any]:
        digest = hashlib.sha256(cache_key.encode("utf-8")).hexdigest()
        path = self.root / provider / f"{digest}.json"
        if path.exists():
            return json.loads(path.read_text(encoding="utf-8"))
        payload = fetcher()
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(payload, ensure_ascii=False, sort_keys=True), encoding="utf-8")
        return payload

class DataProvider:
    name: str
    def collect(self, request: ProviderRequest) -> dict[str, Any]:
        raise NotImplementedError

class FixtureProvider(DataProvider):
    def __init__(self, path: Path | None = None, payload: dict[str, Any] | None = None):
        if path is None and payload is None:
            raise ValueError("FixtureProvider requires path or payload")
        self.name = "fixture"
        self.path = path
        self.payload = payload

    def collect(self, request: ProviderRequest) -> dict[str, Any]:
        if self.payload is not None:
            return json.loads(json.dumps(self.payload))
        assert self.path is not None
        return json.loads(self.path.read_text(encoding="utf-8"))

class ApiFootballProvider(DataProvider):
    name = "api-football"
    BASE_URL = "https://v3.football.api-sports.io"

    def __init__(self, cache: JsonCache, api_key: str | None = None):
        self.cache = cache
        self.api_key = api_key or os.environ.get("API_FOOTBALL_KEY")
        if not self.api_key:
            raise RuntimeError("API_FOOTBALL_KEY is required for live API-Football imports")

    def _get(self, endpoint: str, params: dict[str, Any]) -> dict[str, Any]:
        clean = {k: v for k, v in params.items() if v is not None}
        cache_key = endpoint + "?" + urllib.parse.urlencode(sorted(clean.items()))
        def fetch():
            url = f"{self.BASE_URL}/{endpoint}?{urllib.parse.urlencode(clean)}"
            req = urllib.request.Request(url, headers={"x-apisports-key": self.api_key, "Accept":"application/json"})
            with urllib.request.urlopen(req, timeout=30) as response:
                return json.loads(response.read().decode("utf-8"))
        return self.cache.get_or_fetch(self.name, cache_key, fetch)

    def collect(self, request: ProviderRequest) -> dict[str, Any]:
        if request.provider_league_id is None:
            raise ValueError("API-Football requires provider_league_id")
        teams = self._get("teams", {"league": request.provider_league_id, "season": request.api_season})
        player_entries: list[dict[str, Any]] = []
        page = 1
        while True:
            payload = self._get("players", {
                "league": request.provider_league_id,
                "season": request.api_season,
                "page": page,
            })
            player_entries.extend(payload.get("response", []))
            paging = payload.get("paging") or {}
            if int(paging.get("current", page)) >= int(paging.get("total", page)):
                break
            page += 1
        transfers: list[dict[str, Any]] = []
        if request.fetch_transfers:
            for item in teams.get("response", []):
                provider_team_id = (item.get("team") or {}).get("id")
                if provider_team_id is not None:
                    team_transfers = self._get("transfers", {"team": provider_team_id})
                    transfers.extend(team_transfers.get("response", []))
        return {
            "provider":"api-football",
            "teamsResponse": teams,
            "playersResponse":{"response":player_entries},
            "transfersResponse":{"response":transfers},
        }

class SportmonksProvider(DataProvider):
    name = "sportmonks"
    BASE_URL = "https://api.sportmonks.com/v3/football"

    def __init__(self, cache: JsonCache, api_token: str | None = None):
        self.cache = cache
        self.api_token = api_token or os.environ.get("SPORTMONKS_API_TOKEN")
        if not self.api_token:
            raise RuntimeError("SPORTMONKS_API_TOKEN is required for live Sportmonks imports")

    def _get(self, path: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        params = dict(params or {})
        cache_key = path + "?" + urllib.parse.urlencode(sorted(params.items()))
        def fetch():
            query = dict(params)
            query["api_token"] = self.api_token
            url = f"{self.BASE_URL}/{path}?{urllib.parse.urlencode(query)}"
            req = urllib.request.Request(url, headers={"Accept":"application/json"})
            with urllib.request.urlopen(req, timeout=30) as response:
                return json.loads(response.read().decode("utf-8"))
        return self.cache.get_or_fetch(self.name, cache_key, fetch)

    def collect(self, request: ProviderRequest) -> dict[str, Any]:
        if request.provider_season_id is None:
            raise ValueError("Sportmonks requires provider_season_id")
        teams = self._get(f"teams/seasons/{request.provider_season_id}", {"include":"venue;country"})
        squads: dict[str, Any] = {}
        transfers: dict[str, Any] = {}
        for team in teams.get("data", []):
            team_id = team["id"]
            squads[str(team_id)] = self._get(
                f"squads/seasons/{request.provider_season_id}/teams/{team_id}",
                {"include":"player;details.type"},
            )
            if request.fetch_transfers:
                transfers[str(team_id)] = self._get(
                    f"transfers/teams/{team_id}",
                    {"include":"player;type;fromTeam;toTeam"},
                )
        return {
            "provider":"sportmonks",
            "teamsResponse":teams,
            "squadsByTeam":squads,
            "transfersByTeam":transfers,
        }
