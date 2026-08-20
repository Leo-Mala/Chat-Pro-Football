# Phase 9.13 — Final Weekly Performance

Baseline: `main@400b0faf6a0dbda8657743c6c7bc7fea5b9cff77`

## Current measured bottleneck

The Phase 9.12 audited 82,125-player career measured:

- CPU contract renewal: 21,508 ms
- canonical contract tick: 306 ms
- CPU squad integrity: 60 ms
- combined measured path: 21,874 ms

The remaining dominant weekly bottleneck is therefore CPU renewal. The current implementation still materializes all players and builds a full in-memory `teamId -> roster` map before looking at the small subset of contracts in the one-week renewal window.

## Strategy

Phase 9.13 will preserve gameplay semantics while replacing full-table weekly Player materialization with targeted SQL/action-set queries.

Planned implementation order:

1. Query lightweight roster aggregates by `teamId` (roster size and goalkeeper count) instead of loading full Player entities for healthy clubs.
2. Query only non-loan players actually inside the renewal window.
3. Preserve the existing deterministic retention order and mandatory renewals required to keep `MIN_SQUAD_SIZE` and a goalkeeper after the weekly tick.
4. After the contract tick, query roster-health aggregates again and load full rosters only for clubs that are actually underfilled, oversized, or without a goalkeeper.
5. Load operational free agents only when a club actually needs replenishment, preserving the exclusion of `UNASSIGNED_SOURCE_CLUB` FC26 players.
6. Validate active loans using only the player IDs referenced by active loan rows rather than a complete Player snapshot.
7. Preserve collision-safe generated IDs using a database `MAX(id)` query and deterministic monotonic allocation.

No Player entity/schema/index change is required by this plan; Room should remain V21.

## Non-negotiable invariants

- preserve all 18,405 FC26 players;
- overall mutations = 0;
- potential mutations = 0;
- Atributos mutations = 0;
- duplicate Player IDs = 0;
- duplicate Team IDs = 0;
- true dataset free agents remain distinguishable from `UNASSIGNED_SOURCE_CLUB`;
- no fuzzy club mapping;
- no club-by-club factual research;
- no API-Football;
- no eFootball-only behavior change;
- PR #34 stays frozen;
- PR #27 stays frozen until the later competition-rules phase explicitly re-audits it;
- no timeout inflation, ignored tests, weakened tests, or retry-as-fix.

## Validation gates

- focused CPU renewal semantics tests;
- underfilled/oversized/no-goalkeeper squad-integrity tests;
- deterministic CPU decision test;
- CareerFunctionalFlowTest;
- full non-stress suite;
- FC26 18,405-player full seed and immutability validation;
- save-slot isolation and migration safety;
- real 82,125-player performance artifact with before/after comparison;
- 20-season stress;
- 100-season stress;
- Room schema V21 verification;
- final diff audit.

## Exit gate

If all required checks are green, the PR is classified `APTO PARA MERGE`, the audited head has not changed, and the base has not moved in a way that invalidates the audit, the project `AGENTS.md` standing authorization permits automatic merge without a second user confirmation.

After merge, continue directly to Phase 9.14 rather than waiting for another authorization.
