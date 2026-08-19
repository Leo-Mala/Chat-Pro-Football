# Phase 9.11A0 validation note

Local full-dataset validation against the pinned FC26 snapshot and user-supplied PlayersDB 2026-08-19 exports completed before PR creation.

- Python unit tests: 9 passed.
- Full PlayersDB rows processed: 103,559.
- eFootball 2026 rows classified: 35,956.
- Secure FC26↔Konami matches: 9,234.
- Determinism: two complete runs produced identical SHA-256 payloads for all seven deterministic JSON reports.
- Full-run elapsed time: ~12 seconds per run in the validation environment.
- Peak RSS: ~507 MB in the validation environment.
- Room/runtime Android code: unchanged; database remains V21.

The earlier audit value 1,519 for no-DOB probable matches represented candidate relationships. The implemented report counts unique PlayersDB records (`1,514`) and separately preserves the `1,519` candidate-pair count. Five PlayersDB records have more than one plausible FC26 candidate and are not double-counted.

Raw PlayersDB exports are not committed or bundled in the APK while redistribution permission remains unresolved.
