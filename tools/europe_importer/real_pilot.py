from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any

from .audited_discovery import (
    install_current_squad_only_discovery,
    install_p1532_discovery_bridge,
)
from .audited_exclusions import apply_verified_squad_exclusions, without_squad_exclusions
from .identity import StableTeamIdentityContract
from .open_data_postprocess import (
    apply_canonical_name_overrides,
    apply_verified_open_data_facts,
)
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
        return (
            value
            .replace("provider://fixture/", "provider://wikimedia-open-data/")
            .replace("provider://api-football/", "provider://wikimedia-open-data/")
        )
    return value


def _apply_verified_loan_provenance(dataset: dict[str, Any], overrides_path: Path) -> None:
    overrides = json.loads(overrides_path.read_text(encoding="utf-8"))
    source_by_name = {
        str(row.get("fullName") or "").strip(): str(row.get("source") or "").strip()
        for row in overrides.get("loans", []) or []
        if row.get("fullName") and row.get("source")
    }
    for loan in dataset.get("loans", []) or []:
        player_name = str((loan.get("player") or {}).get("fullName") or "").strip()
        source = source_by_name.get(player_name)
        if source:
            loan["sourceRefs"] = [source]


def main() -> int:
    output_dir = Path(
        os.environ.get("PREMIER_LEAGUE_PILOT_OUTPUT", "build/premier-league-real-pilot")
    )
    output_dir.mkdir(parents=True, exist_ok=True)
    for path in output_dir.glob("*.json"):
        path.unlink()

    contract_path = HERE / "config" / "stable_team_identity_premier_league.json"
    overrides_path = HERE / "config" / "open_data_verified_overrides_2026_27.json"
    overrides = json.loads(overrides_path.read_text(encoding="utf-8"))
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
    # Order matters. First constrain collaborative discovery to the active squad body; then the
    # audited bridge handles P1532-only facts plus explicitly verified membership links/labels.
    install_current_squad_only_discovery(provider)
    install_p1532_discovery_bridge(provider, overrides_path)

    summary_path = output_dir / "pilot_summary.json"
    audit_path = output_dir / "open_data_audit.json"

    try:
        raw = provider.collect(request)
        provider.last_audit["p1532DiscoveryBridgeCount"] = len(
            getattr(provider, "p1532_discovery_bridged_qids", set())
        )
        provider.last_audit["verifiedSquadLabelFallbackCount"] = len(
            getattr(provider, "verified_squad_label_fallback_qids", set())
        )

        # Exclusions are applied before the legacy postprocessor and are idempotent: if the safer
        # discovery path already removed a false association, the rule becomes an audited no-op.
        apply_verified_squad_exclusions(raw, provider.last_audit, overrides)
        with without_squad_exclusions(overrides_path) as runtime_overrides_path:
            raw = apply_verified_open_data_facts(provider, raw, runtime_overrides_path)

        audit_path.write_text(
            json.dumps(provider.last_audit, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

        raw["provider"] = "fixture"
        contract = StableTeamIdentityContract.from_json(contract_path)
        result = run_pipeline(
            FixtureProvider(payload=raw),
            request,
            contract,
            dataset_kind="FACTUAL",
            output_dir=None,
        )
        canonical_dataset = _rewrite_open_data_provenance(result.dataset)
        canonical_dataset["provider"] = "wikimedia-open-data"
        _apply_verified_loan_provenance(canonical_dataset, overrides_path)
        name_corrections = apply_canonical_name_overrides(canonical_dataset, overrides_path)
        provider.last_audit["canonicalNameCorrections"] = name_corrections
        audit_path.write_text(
            json.dumps(provider.last_audit, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        manifest = write_sharded_dataset(canonical_dataset, output_dir)

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

        verified_loan_count = int(provider.last_audit.get("verifiedLoanCount") or 0)
        if int(manifest.get("loanCount", 0)) != verified_loan_count:
            raise RuntimeError(
                f"Canonical loanCount={manifest.get('loanCount')} differs from verified open-data loans={verified_loan_count}"
            )

        summary = {
            "provider": "wikimedia-open-data",
            "seasonLabel": "2026/27",
            "verifiedAsOfIso": str(overrides.get("verifiedAsOfIso") or ""),
            "clubCount": manifest.get("clubCount"),
            "playerCount": manifest.get("playerCount"),
            "loanCount": manifest.get("loanCount"),
            "loanCandidatesDetected": len(provider.last_audit.get("loanCandidates") or []),
            "verifiedLoanCount": verified_loan_count,
            "canonicalNameCorrectionCount": len(name_corrections),
            "p1532DiscoveryBridgeCount": provider.last_audit.get("p1532DiscoveryBridgeCount", 0),
            "verifiedSquadLabelFallbackCount": provider.last_audit.get("verifiedSquadLabelFallbackCount", 0),
            "validationStatus": manifest.get("validationStatus"),
            "datasetFiles": manifest.get("datasetFiles"),
            "sourcePolicy": {
                "squadDiscovery": "English Wikipedia active first-team body plus explicit official membership overrides",
                "structuredFacts": "Wikidata CC0",
                "sportNationality": "Current/ranked Wikidata P1532; official override for unresolved ambiguity; P27 only when P1532 is absent",
                "verifiedOverrides": "Official club/league sources",
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
                    "verifiedAsOfIso": str(overrides.get("verifiedAsOfIso") or ""),
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
