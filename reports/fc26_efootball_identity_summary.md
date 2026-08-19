# FC26 + eFootball identity reconciliation — Phase 9.11A0

- FC26 remains the factual primary source; no rating, potential, attribute or club is changed.
- PlayersDB is used only for offline identity reconciliation.
- Raw PlayersDB files and per-player derived reports are not intended for APK bundling while redistribution permission is unresolved.
- FC26 players: **18,405**
- PlayersDB records: **103,559**
- eFootball 2026: **35,956** total / **35,553** non-system
- A secure: **9,234**
- B probable (unique PlayersDB records): **1,542**
- C1 strong eFootball-only identity: **4,281**
- C2 reasonable identity: **16,689**
- C3 insufficient identity: **2,067**
- D reject: **491**
- E indeterminate: **1,652**

## Baseline correction

The earlier audit value `1,519` for the no-DOB probable bucket represented **candidate relationships**, not unique PlayersDB records. The pipeline reports both values separately. This prevents one person with multiple plausible FC26 candidates from inflating Group B.

## Redistribution boundary

This repository should not bundle the raw PlayersDB export or eFootball-only players into the APK until redistribution permission and the missing professional-club/rating data are resolved.
