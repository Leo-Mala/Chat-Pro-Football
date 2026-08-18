# European Bulk Data Importer

Offline build-time pipeline for the Pro Football 2026/27 European factual seed.

## Safety contract

`RAW -> NORMALIZED -> VALIDATED -> CANONICAL JSON`

The Android app never consumes raw source responses. The canonical writer uses an allow-list and drops provider IDs, photos, logos, kit images, statistics and provider ratings. Numeric `teamId` and `playerId` are not imported: Python only mirrors the stable identity rules during validation, while Android resolves the authoritative `StableTeamIdentityRegistry` / `StableRealPlayerIdentity`.

Local HTTP cache lives in `tools/europe_importer/.cache/` and must never be committed.

## 2026/27 scope

`config/associations_2026_27.json` defines the same 20 UEFA associations modeled in the app. The first production factual pilot covers all 20 Premier League clubs with an open-data collector. It uses English Wikipedia only to discover current-squad player links, Wikidata for structured facts and explicit official Premier League/club sources for small verified overrides. See `OPEN_DATA_PILOT.md` for the source and validation contract.

The checked-in Premier League assets are sharded by club and are accepted by production career creation only while the manifest is `validationStatus=VALIDATED` and every shard is `datasetKind=FACTUAL`.

## Runtime activation

`GenerateCalendarUseCase.generateSeasonFixtures` is the new-career checkpoint that prepares a one-shot `EuropeanNewSaveSeedCoordinator` plan. During the initial Room transaction, `GameRepository.saveTeams` applies factual club city/stadium metadata and `savePlayers` atomically consumes the planner output for `Player` plus `PlayerLoan`. The pending plan is removed after one consumption and also cleared in the outer transaction `finally`, so save loading, roster repair, regens, academies and uncovered countries/divisions keep their existing procedural behavior.

If a factual club resolves to a stable identity that is not present in the current global team list, or if a verified loan cannot materialize both owner and borrower, the seed fails closed instead of silently creating inconsistent data.

## Free Premier League factual pilot

```bash
python3 -m unittest discover -s tools/europe_importer/tests -t .
python3 -m tools.europe_importer.real_pilot
```

No API key is required. The runner produces the 20 canonical club shards, `dataset_manifest.json`, `open_data_audit.json` and `pilot_summary.json` in its configured output directory.

## Optional paid providers

The importer still retains API-Football and Sportmonks adapters for future source comparisons. Live credentials are read only from environment variables (`API_FOOTBALL_KEY`, `SPORTMONKS_API_TOKEN`) and must never be committed. Paid-provider imports remain subject to their own terms/coverage gates and are not required for the current Premier League open-data pilot.
