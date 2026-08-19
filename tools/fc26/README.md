# FC26 world player dataset pipeline

This directory turns a user-supplied FC26 CSV snapshot into the compact offline asset consumed by new Pro Football saves.

## Source contract

The pipeline expects the `FC26_20250921.csv` column family used by the 2025-09-19 snapshot. The source CSV itself is intentionally **not** checked into the repository; provide it locally when regenerating the asset.

## Generate

```bash
python3 tools/fc26/import_fc26.py FC26_20250921.csv \
  --output app/src/main/assets/football/fc26/fc26_players_2025-09-19.tsv.bin \
  --manifest app/src/main/assets/football/fc26/fc26_manifest.json \
  --report reports/fc26_import_report.json
```

The `.bin` file contains a deterministic gzip-compressed TSV payload. The neutral extension avoids special/inconsistent asset handling while the manifest records the exact file and SHA-256.

The importer uses only Python's standard library. It validates the expected 18,405 rows, unique source player IDs, required source columns, 1..99 ratings and all 35 mapped Pro Football attributes.

## Validate checked-in asset

```bash
python3 tools/fc26/validate_fc26.py \
  app/src/main/assets/football/fc26/fc26_players_2025-09-19.tsv.bin \
  app/src/main/assets/football/fc26/fc26_manifest.json
```

## Runtime rules

- `FC26 overall` becomes `Player.force` exactly.
- `FC26 potential` becomes `Player.potential` exactly.
- Detailed source attributes feed `Atributos`; deterministic formulas are used only when Pro Football has no 1:1 source field.
- Source `player_id` is metadata. Room identity uses the existing stable real-player namespace (canonical name + DOB), independent of club.
- Club matching is conservative and never fuzzy-guesses ambiguous targets.
- Unmatched Pro Football clubs keep the existing procedural roster fallback.
- FC26 free agents use canonical `teamId = null`.
- `club_loaned_from` is retained as metadata/reporting only. Because the snapshot has no reliable loan duration, no `PlayerLoan` row is fabricated.
- The source monetary fields are EUR and are converted centrally to the game's BRL values using the manifest's snapshot reference rate.
- Existing saves are never reimported; the asset participates only in new-save seeding.
- Calendar generation only registers a lazy seed request; the 18,405-player asset is read when the initial new-save `saveTeams -> savePlayers` persistence sequence begins. Season transitions do not reimport FC26.

## CI bootstrap

If the binary asset has not yet been materialized in a branch, `bootstrap_fc26_asset.py` can use the exact pinned mirror at commit `63bab09ab065fca67fbbd8616b58384f984e41b3`. It accepts the snapshot only when both the expected 10,576,203-byte size and SHA-256 `4399cb2bcc2a14a2872e76a118f8f4bf64d7954503949c75751a14f33863e3b2` match. This bootstrap is build-time only; the Android game never accesses the mirror at runtime.
