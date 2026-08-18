from __future__ import annotations
import argparse
import json
from pathlib import Path

from .fixture_builder import build_premier_league_api_fixture
from .identity import StableTeamIdentityContract
from .pipeline import run_pipeline
from .providers import ApiFootballProvider, FixtureProvider, JsonCache, ProviderRequest, SportmonksProvider

HERE = Path(__file__).resolve().parent

def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Build validated canonical European factual datasets.")
    p.add_argument("--provider", choices=("fixture","api-football","sportmonks"), required=True)
    p.add_argument("--country", default="Inglaterra")
    p.add_argument("--league", default="Premier League")
    p.add_argument("--season-label", default="2026/27")
    p.add_argument("--provider-league-id", type=int)
    p.add_argument("--provider-season-id", type=int)
    p.add_argument("--fixture", type=Path)
    p.add_argument("--output-dir", type=Path, required=True)
    p.add_argument("--filename", default="premier_league.json")
    p.add_argument("--dataset-kind", choices=("FACTUAL","FIXTURE"), default="FACTUAL")
    p.add_argument("--terms-reviewed", action="store_true", help="Required before any live provider request.")
    p.add_argument("--no-transfers", action="store_true")
    p.add_argument("--generated-at")
    return p

def main(argv=None) -> int:
    args = parser().parse_args(argv)
    if args.provider != "fixture" and not args.terms_reviewed:
        raise SystemExit("Live imports require --terms-reviewed after verifying current provider terms/licensing.")
    cache = JsonCache(HERE / ".cache")
    if args.provider == "fixture":
        provider = (
            FixtureProvider(args.fixture)
            if args.fixture is not None
            else FixtureProvider(payload=build_premier_league_api_fixture())
        )
    elif args.provider == "api-football":
        provider = ApiFootballProvider(cache)
    else:
        provider = SportmonksProvider(cache)
    request = ProviderRequest(
        country=args.country,
        league=args.league,
        season_label=args.season_label,
        provider_league_id=args.provider_league_id,
        provider_season_id=args.provider_season_id,
        fetch_transfers=not args.no_transfers,
    )
    contract = StableTeamIdentityContract.from_json(HERE / "config" / "stable_team_identity_premier_league.json")
    result = run_pipeline(
        provider, request, contract,
        generated_at=args.generated_at,
        dataset_kind=args.dataset_kind,
        output_dir=args.output_dir,
        filename=args.filename,
    )
    print(json.dumps(result.manifest, ensure_ascii=False, indent=2))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
