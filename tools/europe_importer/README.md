# European Bulk Data Importer

Offline build-time pipeline for the Pro Football 2026/27 European factual seed.

## Safety contract

`RAW -> NORMALIZED -> VALIDATED -> CANONICAL JSON`

The Android app never consumes provider responses. The canonical writer uses an allow-list and
drops provider IDs, photos, logos, kit images, statistics and provider ratings. Numeric `teamId` and
`playerId` are not imported: Python only mirrors the stable identity rules during validation, while
Android resolves the authoritative `StableTeamIdentityRegistry` / `StableRealPlayerIdentity`.

Live credentials are read only from environment variables:

- `API_FOOTBALL_KEY`
- `SPORTMONKS_API_TOKEN`

`--terms-reviewed` is mandatory for live calls. Provider terms and competition/rightsholder
permissions must be reviewed before producing a `FACTUAL` dataset.

Local HTTP cache lives in `tools/europe_importer/.cache/` and must never be committed.

## 2026/27 scope

`config/associations_2026_27.json` defines the same 20 UEFA associations modeled in the app.
The first executable pilot is the full 20-club Premier League fixture. Its canonical output is
checked into Android assets as `FIXTURE_ONLY`, so it is available to tests but cannot seed a real
save.

## Commands

Run importer tests:

```bash
python3 -m unittest discover -s tools/europe_importer/tests -t .
```

Rebuild the deterministic Premier League fixture asset:

```bash
python3 -m tools.europe_importer.cli \
  --provider fixture \
  --dataset-kind FIXTURE \
  --generated-at 2026-08-18T14:00:00Z \
  --output-dir app/src/main/assets/football/europe/2026_27 \
  --filename premier_league.fixture.json
```

A real API-Football import uses the same pipeline, but requires a key and explicit terms gate:

```bash
API_FOOTBALL_KEY=... python3 -m tools.europe_importer.cli \
  --provider api-football --provider-league-id 39 --terms-reviewed \
  --output-dir app/src/main/assets/football/europe/2026_27
```

Sportmonks uses `SPORTMONKS_API_TOKEN` and `--provider-season-id`. Provider selectors are metadata
only; they are never persisted as Pro Football identities.
