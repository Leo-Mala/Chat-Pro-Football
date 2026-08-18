from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
from pathlib import Path
from typing import Any

from .identity import StableTeamIdentityContract
from .pipeline import run_pipeline
from .providers import ApiFootballProvider, JsonCache, ProviderRequest
from .sharding import write_sharded_dataset

HERE = Path(__file__).resolve().parent


class RateLimitedApiFootballProvider(ApiFootballProvider):
    """API-Football provider wrapper suitable for the Free 10 req/min plan.

    The canonical importer remains provider-agnostic; this class only throttles
    live requests used by the one-off Premier League pilot and retries HTTP 429
    without ever printing the API key.
    """

    def __init__(self, cache: JsonCache, min_interval_seconds: float, max_retries: int = 3):
        super().__init__(cache)
        self.min_interval_seconds = max(0.0, min_interval_seconds)
        self.max_retries = max(0, max_retries)
        self._last_call_started_at = 0.0

    def _get(self, endpoint: str, params: dict[str, Any]) -> dict[str, Any]:
        attempt = 0
        while True:
            elapsed = time.monotonic() - self._last_call_started_at
            sleep_for = self.min_interval_seconds - elapsed
            if sleep_for > 0:
                time.sleep(sleep_for)
            self._last_call_started_at = time.monotonic()
            try:
                return super()._get(endpoint, params)
            except urllib.error.HTTPError as exc:
                if exc.code != 429 or attempt >= self.max_retries:
                    raise
                attempt += 1
                retry_after = exc.headers.get("Retry-After") if exc.headers else None
                try:
                    delay = float(retry_after) if retry_after else 65.0
                except ValueError:
                    delay = 65.0
                delay = max(delay, 65.0)
                print(
                    f"API-Football rate limit reached; retrying request after {delay:.0f}s "
                    f"(attempt {attempt}/{self.max_retries}).",
                    file=sys.stderr,
                )
                time.sleep(delay)


def _api_errors(payload: dict[str, Any]) -> list[str]:
    errors = payload.get("errors")
    if not errors:
        return []
    if isinstance(errors, dict):
        return [f"{key}: {value}" for key, value in errors.items()]
    if isinstance(errors, list):
        return [str(value) for value in errors]
    return [str(errors)]


def main() -> int:
    if not os.environ.get("API_FOOTBALL_KEY"):
        raise SystemExit("API_FOOTBALL_KEY repository secret is required")

    output_dir = Path(os.environ.get("PREMIER_LEAGUE_PILOT_OUTPUT", "build/premier-league-real-pilot"))
    output_dir.mkdir(parents=True, exist_ok=True)
    for path in output_dir.glob("*.json"):
        path.unlink()

    min_interval = float(os.environ.get("API_FOOTBALL_MIN_INTERVAL_SECONDS", "6.2"))
    provider = RateLimitedApiFootballProvider(JsonCache(HERE / ".cache"), min_interval_seconds=min_interval)

    # Preflight: fail clearly when the account/plan cannot access Premier League 2026.
    coverage = provider._get("leagues", {"id": 39, "season": 2026})
    errors = _api_errors(coverage)
    if errors:
        raise SystemExit("API-Football preflight failed: " + "; ".join(errors))
    responses = coverage.get("response") or []
    if not responses:
        raise SystemExit(
            "API-Football returned no Premier League season=2026 coverage for this subscription. "
            "Check the API-Football dashboard coverage/plan before retrying."
        )

    request = ProviderRequest(
        country="Inglaterra",
        league="Premier League",
        season_label="2026/27",
        provider_league_id=39,
        api_season=2026,
        fetch_transfers=True,
    )
    contract = StableTeamIdentityContract.from_json(HERE / "config" / "stable_team_identity_premier_league.json")
    result = run_pipeline(
        provider,
        request,
        contract,
        dataset_kind="FACTUAL",
        output_dir=None,
    )
    manifest = write_sharded_dataset(result.dataset, output_dir)

    if manifest.get("validationStatus") != "VALIDATED":
        raise SystemExit(f"Unexpected pilot validation status: {manifest.get('validationStatus')}")
    if int(manifest.get("clubCount", 0)) != 20:
        raise SystemExit(f"Premier League pilot must contain 20 clubs, found {manifest.get('clubCount')}")
    if int(manifest.get("playerCount", 0)) < 360:
        raise SystemExit(
            "Premier League pilot player coverage is suspiciously low: "
            f"{manifest.get('playerCount')} total identities"
        )

    summary = {
        "provider": "api-football",
        "providerLeagueId": 39,
        "apiSeason": 2026,
        "seasonLabel": "2026/27",
        "clubCount": manifest.get("clubCount"),
        "playerCount": manifest.get("playerCount"),
        "loanCount": manifest.get("loanCount"),
        "validationStatus": manifest.get("validationStatus"),
        "datasetFiles": manifest.get("datasetFiles"),
    }
    (output_dir / "pilot_summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
