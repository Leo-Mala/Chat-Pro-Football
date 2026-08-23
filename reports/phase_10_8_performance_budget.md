# Phase 10.8 — Full-scale season rollover performance budget

These budgets were fixed from the measured legacy baseline **before any production optimization**.
They are not adjusted from candidate results.

Baseline source: `reports/phase_10_8_baseline.json` (`0704e801d8367aec53e2be59cf090cf87e65aaca`).

## Existing budgets that remain unchanged

- Phase 10.1 initial persistence: **20,289 ms**
- Phase 10.1 full reload: **47,181 ms**
- Phase 10.1 monthly evolution: **65,553 ms**
- Phase 10.1 peak heap: **762,995,562 bytes**

No previous budget may be relaxed by Phase 10.8.

## New Phase 10.8 candidate budgets

| Metric | Required ceiling / invariant | Rationale fixed before optimization |
| --- | ---: | --- |
| Full season rollover, normal CI | **20,000 ms** | Legacy measured 20,605 ms. Candidate must improve rather than merely fit a wider timeout; 20 s is also slightly stricter than the existing 20,289 ms 60k initial-persistence ceiling. |
| Full season rollover, controlled single logical CPU | **60,000 ms** | Three-times normal budget, matching the existing Phase 10.1 conservative slow-run multiplier. This is a deterministic constrained CI profile, not a claim about physical-device timing. |
| Relevant queries during rollover | **25,000** | Legacy measured 153,810. A correct bulk path still needs standings, fixture and replacement writes, but no per-player read/update loop. |
| Per-player active-loan lookups | **0** | `getActiveLoanForPlayer()` N+1 is a material pattern at full scale and must be eliminated from the rollover path. |
| Full-entity Player `@Update` statements | **0** | Legacy emitted 57,925 full-row Player updates; seasonal age/reset must use an action-set update without altering unrelated player facts. |
| Team update statements | **500** | Only clubs whose division actually changes may be persisted; rewriting all 2,524 clubs is not acceptable. The ceiling leaves substantial format variability without allowing the legacy all-club pattern. |
| Peak observed heap | **350,000,000 bytes** | Legacy peak was 273,555,432 bytes. This allows ~28% runner/runtime headroom while remaining much stricter than the unchanged Phase 10.1 762,995,562-byte ceiling. |
| Peak WAL | **85,000,000 bytes** | Legacy peak was 75,651,472 bytes. Row pages may still need rewriting for age/reset, so the budget controls growth without pretending set-based SQL eliminates page writes. |
| WAL after explicit TRUNCATE checkpoint | **1,048,576 bytes** | Completed rollover must be checkpointable back to a small WAL; the benchmark records pre-checkpoint peak separately. |
| Post-rollover reopen + full-player reload | **47,181 ms** | Reuses, without relaxing, the existing Phase 10.1 full-reload ceiling. |
| Longest rollover transaction | **20,000 ms** | The transition remains atomic; transaction duration is bounded by the same full-rollover ceiling. |
| Room schema | **V22** | No schema/index change is justified by the measured bottlenecks. |

## Absolute correctness gates

The candidate fails regardless of timing if any of these change:

- datasetPlayers = validatedPlayers = processedPlayers = importedPlayers = **18,405**
- notImported = **0**
- persistedPlayersIncludingFallback = **60,885**
- clubs = **2,524** for the production universe materialized by this harness
- datasetLoanPlayers = **1,325**
- resolvedLoans = **816**
- rejectedLoans = **509**
- borrowerNotFound = **448**
- ownerNotFound = **60**
- ambiguousLoans = **1**
- duplicatePlayerIds = **0**
- duplicateTeamIds = **0**
- overallMutated = potentialMutated = attributesMutated = **0**

The candidate must also pass save/reopen, slot isolation, Room schema, migration/save-safety,
20/100-season stress, competition regressions, Debug/Release builds, Release startup, UI goldens,
and the Phase 10.7 installed Android matrix on the exact candidate head.
