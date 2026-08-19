# FC26 + eFootball identity reconciliation (Phase 9.11A0)

This package reconciles FC26 `player_id` values with PlayersDB/eFootball `konamiID` values **offline**. It does not mutate the Android runtime, Room schema, FC26 ratings, potentials, attributes, clubs, contracts, or saves.

## Source policy

- **FC26 remains the factual primary source.**
- PlayersDB is an identity complement only in this phase.
- The raw PlayersDB CSV/JSONL is intentionally **not committed**. Redistribution/bundling permission is unresolved, and the public export lacks professional club, league, FIFA ID, overall, potential and detailed eFootball attributes.
- `starRating` is never converted into FC26 `overall`; missing club/rating data is never invented.
- C1/C2/C3 records are reports only and are never imported into Room.

## PlayersDB format

The supplied JSONL is not object-per-line JSONL. Line 1 is the 29-column header array and every following line is a value array. `playersdb_reader.py` validates that contract explicitly. CSV and JSONL can be checked for semantic equivalence after type and whitespace normalization.

## Reconcile

```bash
python3 tools/efootball/reconcile_playersdb.py \
  --fc26 /path/to/FC26_20250921.csv \
  --playersdb-jsonl /path/to/PlayersDB_20260819.jsonl \
  --playersdb-csv /path/to/PlayersDB_20260819.csv \
  --output-dir reports \
  --verify-determinism
```

Generated deterministic reports:

- `fc26_efootball_identity_report.json`
- `fc26_efootball_secure_matches.json`
- `fc26_efootball_probable_matches.json`
- `efootball_only_candidates.json`
- `efootball_rejected_records.json`
- `efootball_nationality_crosswalk.json`
- `efootball_position_crosswalk.json`

Runtime timings and memory are isolated in `fc26_efootball_performance.json` because they are intentionally volatile and must not trigger report-commit loops.

## Match hierarchy

- **A1:** exact normalized `fullName` + exact DOB, 1:1.
- **A2:** exact primary PlayersDB `playerName` alias + exact DOB, 1:1.
- **A3:** exact order-insensitive token signature + exact DOB + height within 2 cm + exact preferred foot, 1:1. Only two narrow audited Korean romanization equivalences (`seong/sung`, `jeong/jung`) are normalized; this is not fuzzy matching.
- **B1:** same DOB + conservative name similarity + corroborating height/foot/position. All candidates are retained; none is auto-promoted to A.
- **B2:** DOB absent + exact primary-name identity + corroborating height/foot/position. Group B counts unique PlayersDB records, while candidate-pair count is reported separately.

The position families used in B are enabled only after the secure A set validates the observed PlayersDB registered-position codes against FC26 primary positions.

## Classification

Every eFootball 2026 record is partitioned exactly once into A, B, C1, C2, C3, D or E. System players and strongly suspicious age records are D; unresolved possible FC26 overlaps are E; the remaining eFootball-only records are split by identity completeness.

## Tests

```bash
python3 -m unittest discover -s tools/efootball/tests -t .
```

CI uses synthetic fixtures only, so the raw PlayersDB export is not required or redistributed by GitHub Actions.
