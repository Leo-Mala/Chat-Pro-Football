from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any

from .identity import StableTeamIdentityContract
from .pipeline import run_pipeline
from .providers import FixtureProvider, ProviderRequest
from .sharding import write_sharded_dataset
from .wikimedia_open_data import WikimediaOpenDataProvider

HERE = Path(__file__).resolve().parent


def _rewrite_open_data_provenance(value: Any) -> Any:
    if isinstance(value, dict):
        return {k: _rewrite_open_data_provenance(v) for k, v in value.items()}
    if isinstance(value, list):
        return [_rewrite_open_data_provenance(v) for v in value]
    if isinstance(value, str):
        return value.replace("provider://fixture/", "provider://wikimedia-open-data/")
    return value


def main() -> int:
    output_dir = Path(
        os.environ.get("PREMIER_LEAGUE_PILOT_OUTPUT", "build/premier-league-real-pilot")
    )
    output_dir.mkdir(parents=True, exist_ok=True)
    for path in output_dir.glob("*.json"):
        path.unlink()

    contract_path = HERE / "config" / "stable_team_identity_premier_league.json"
    contract_doc = json.loads(contract_path.read_text(encoding="utf-8"))
    team_names = [str(team["name"]) for team in contract_doc["teams"]]

    request = ProviderRequest(
        country="Inglaterra",
        league="Premier League",
        season_label="2026/27",
        api_season=2026,
        fetch_transfers=False,
    )
    provider = WikimediaOpenDataProvider(
        HERE / ".cache" / "wikimedia-open-data",
        team_names=team_names,
    )

    summary_path = output_dir / "pilot_summary.json"
    audit_path = output_dir / "open_data_audit.json"

    try:
        # Collection is intentionally separate from canonicalization so the
        # audit survives even if validation fails.
        raw = provider.collect(request)
        audit_path.write_text(
            json.dumps(provider.last_audit, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

        # The open-data collector emits the same transient raw shape as the
        # tested fixture normalizer. Provider/QIDs are stripped by canonicalize.
        raw["provider"] = "fixture"
        contract = StableTeamIdentityContract.from_json(contract_path)
        result = run_pipeline(
            FixtureProvider(payload=raw),
            request,
            contract,
            dataset_kind="FACTUAL",
            output_dir=None,
        )
        result.dataset["provider"] = "wikimedia-open-data"
        _rewrite_open_data_provenance(result.dataset)
        manifest = write_sharded_dataset(result.dataset, output_dir)

        if manifest.get("validationStatus") != "VALIDATED":
            raise RuntimeError(
                f"Unexpected pilot validation status: {manifest.get('validationStatus')}"
            )
        if int(manifest.get("clubCount", 0)) != 20:
            raise RuntimeError(
                f"Premier League pilot must contain 20 clubs, found {manifest.get('clubCount')}"
            )

        club_audit = provider.last_audit.get("clubs") or []
        low_coverage = [
            f"{row.get('club')}={row.get('acceptedPlayers')}"
            for row in club_audit
            if int(row.get("acceptedPlayers") or 0) < 18
        ]
        if low_coverage:
            raise RuntimeError(
                "Open-data squad coverage below gameplay-ready minimum: "
                + ", ".join(low_coverage)
            )

        summary = {
            "provider": "wikimedia-open-data",
            "seasonLabel": "2026/27",
            "clubCount": manifest.get("clubCount"),
            "playerCount": manifest.get("playerCount"),
            "loanCount": manifest.get("loanCount"),
            "loanCandidatesDetected": len(provider.last_audit.get("loanCandidates") or []),
            "validationStatus": manifest.get("validationStatus"),
            "datasetFiles": manifest.get("datasetFiles"),
            "sourcePolicy": {
                "squadDiscovery": "English Wikipedia current/first-team squad section",
                "structuredFacts": "Wikidata CC0",
                "providerIdsPersisted": False,
                "wikipediaTextPersisted": False,
            },
        }
        summary_path.write_text(
            json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(json.dumps(summary, ensure_ascii=False, indent=2))
        return 0
    except Exception as exc:
        if provider.last_audit and not audit_path.exists():
            audit_path.write_text(
                json.dumps(provider.last_audit, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )
        summary_path.write_text(
            json.dumps(
                {
                    "provider": "wikimedia-open-data",
                    "seasonLabel": "2026/27",
                    "validationStatus": "FAILED",
                    "error": str(exc),
                    "auditAvailable": audit_path.exists(),
                },
                ensure_ascii=False,
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        raise


if __name__ == "__main__":
    raise SystemExit(main())
