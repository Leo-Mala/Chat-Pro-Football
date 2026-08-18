# European Bulk Data Importer

Offline build-time pipeline for the Pro Football 2026/27 European factual seed.

## Safety contract

`RAW -> NORMALIZED -> VALIDATED -> CANONICAL JSON`

The Android app never consumes provider responses. The canonical writer uses an allow-list and drops provider IDs, photos, logos, kit images, statistics and provider ratings. Numeric `teamId` and `playerId` are not imported: Python only mirrors the stable identity rules during validation, while Android resolves the authoritative `StableTeamIdentityRegistry` / `StableRealPlayerIdentity`.

Live credentials are read only from environment variables: `API_FOOTBALL_KEY` and `SPORTMONKS_API_TOKEN`. `--terms-reviewed` is mandatory for live calls. Provider terms and competition/rightsholder permissions must be reviewed before producing a `FACTUAL` dataset. Local HTTP cache lives in `tools/europe_importer/.cache/` and must never be committed.

## 2026/27 scope

`config/associations_2026_27.json` defines the same 20 UEFA associations modeled in the app. The first executable pilot exercises all 20 Premier League clubs in Python. Android keeps a small representative Premier League canonical fixture (including both sides of a loan) as `FIXTURE_ONLY`; it exists only for loader/planner tests and cannot seed a real save. Live `FACTUAL` generation can write the full league, optionally sharded by club for reviewable versioned diffs.

## Runtime activation

The app only exposes a dataset to career creation when `dataset_manifest.json` reports `validationStatus=VALIDATED` and every canonical shard is `datasetKind=FACTUAL`. `FIXTURE_ONLY` is therefore test data, never production seed data.

`GenerateCalendarUseCase.generateSeasonFixtures` is the new-career checkpoint that prepares a one-shot `EuropeanNewSaveSeedCoordinator` plan. During the initial Room transaction, `GameRepository.saveTeams` applies factual club city/stadium metadata and `savePlayers` atomically consumes the planner output for `Player` plus `PlayerLoan`. The pending plan is removed after one consumption and also cleared in the outer transaction `finally`, so save loading, roster repair, regens, academies and uncovered countries/divisions keep their existing procedural behavior.

If a future factual club resolves to a stable identity that is not present in the current global team list, or if a loan cannot materialize both owner and borrower, the seed fails closed instead of silently creating inconsistent data.

## Commands

```bash
python3 -m unittest discover -s tools/europe_importer/tests -t .
python3 -m tools.europe_importer.cli \
  --provider fixture --dataset-kind FIXTURE \
  --generated-at 2026-08-18T14:00:00Z \
  --output-dir app/src/main/assets/football/europe/2026_27 \
  --shard-by-club
```

A real API-Football import uses the same pipeline but requires a key and explicit terms gate:

```bash
API_FOOTBALL_KEY=... python3 -m tools.europe_importer.cli \
  --provider api-football --provider-league-id 39 --terms-reviewed \
  --output-dir app/src/main/assets/football/europe/2026_27 --shard-by-club
```

Sportmonks uses `SPORTMONKS_API_TOKEN` and `--provider-season-id`. Provider selectors are metadata only; they are never persisted as Pro Football identities.
