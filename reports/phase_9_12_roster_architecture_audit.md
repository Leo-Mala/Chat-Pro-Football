# Phase 9.12 — Post-Bulk Roster Architecture Audit

## Baseline

- repository: `Leo-Mala/Chat-Pro-Football`
- immutable base: `main@c970583436edc28c184039146f6607895cb3a0f5`
- base already contains merged PR #41 and the complete FC26 bulk import
- FC26 snapshot players: **18,405 / 18,405 imported**
- source clubs: 662
- target teams: 2,524
- Room: **V21**

This phase does not revert the bulk-import decision, does not reintroduce club-by-club research and does not use API-Football.

## Verified post-PR #41 state

| Metric | Value |
|---|---:|
| FC26 players imported | 18,405 |
| FC26 players not imported | 0 |
| FC26 clubs MATCHED | 400 |
| FC26 clubs UNMATCHED | 260 |
| FC26 clubs AMBIGUOUS | 2 |
| FC26 players with mapped club | 11,155 |
| true FC26 free agents | 89 |
| FC26 unresolved/unassigned players | 7,161 |
| fallback rosters | 2,124 |
| procedural fallback players | 63,720 |
| total persisted players | 82,125 |
| duplicate player IDs | 0 |
| duplicate Team IDs | 0 |
| overall/potential/attributes mutated | 0 / 0 / 0 |

The planner creates one 30-player procedural roster for every target Team without a safely MATCHED FC26 source club:

`2,524 target teams - 400 mapped teams = 2,124 fallback rosters`.

## Why club matching alone cannot solve the scalability problem

Only 262 FC26 source clubs remain unresolved (`260 UNMATCHED + 2 AMBIGUOUS`). Even perfect safe resolution of all 262 could directly replace at most 262 existing fallback rosters:

`262 × 30 = 7,860 procedural players`.

The lower bound after perfect FC26 club resolution would still be:

- 1,862 fallback target teams;
- 55,860 procedural players;
- at least 74,265 persisted players before any other architecture change.

Therefore **87.7% of the current procedural fallback population cannot be removed merely by resolving FC26 club identities**. The primary problem is weekly player lifecycle architecture, not matching.

## Performance baseline

Post-bulk diagnostics before the PR #41 cache optimization measured approximately:

- CPU contract renewal: **27.317 s**
- weekly contract processing: **34.631 s**
- CPU squad integrity: **53.198 s**
- combined: **115.146 s**

PR #41 reduced the measured combined path to approximately **46.748 s**, with CPU squad integrity falling to approximately **116 ms** by reusing the weekly player snapshot and removing an N×players lookup. The remaining dominant work is renewal plus the canonical contract tick.

## Exact weekly dependency map

The weekly path is orchestrated by `GameViewModel.processWeekEndEconomicAndEvolution()` in this order:

1. CPU contract renewal;
2. canonical player contract tick;
3. CPU squad integrity;
4. transfer offer generation;
5. monthly evolution every four weeks;
6. competition progression;
7. season transition at week 48.

| Stage | Frequency | Baseline player access | Classification | Decision |
|---|---|---|---|---|
| CPU contract renewal | weekly | `getAllPlayers()` | replaceable, but snapshot currently feeds fast integrity pass | retain for Strategy-A slice 1; reassess after measurement |
| canonical contract tick | weekly | `getAllPlayers()` + Kotlin copy + chunked Room `@Update` for every active contract | **replaceable now** | SQL action sets |
| CPU squad integrity | weekly | uses renewal snapshot after PR #41 | measured fast (~116 ms) | preserve in slice 1 |
| transfer offers | weekly | user roster + teams | already targeted | preserve |
| monthly evolution | every 4 weeks | global `getAllPlayers()` | intentionally global gameplay rule | retain initially |
| season aging/retirement | once/season | global `getAllPlayers()` | semantically global | retain |
| database integrity repair | transition/explicit | global validation/repair | safety path, not weekly steady-state | retain |
| match simulation/stats | per match | team/ID targeted reads | already targeted | preserve |

## Deterministic and data-safety invariants

Any implementation must preserve:

- all 18,405 FC26 stable player IDs;
- FC26 `overall -> force`, `potential`, positions and complete `Atributos`;
- `StableRealPlayerIdentity` and stable Team identities;
- true free agents distinct from `UNASSIGNED_SOURCE_CLUB`;
- loan owner/borrower semantics;
- contract expiration semantics;
- deterministic CPU squad decisions for identical persisted state;
- save-slot isolation and close/reopen behavior;
- eFootball-only behavior;
- Room V21 unless a later proven requirement makes migration unavoidable.

## Strategy decision

### Selected: Strategy A — query/action-set optimization first

Keeping the procedural rosters persisted while eliminating unnecessary full-table materialization has much lower semantic risk than lazy roster materialization and already has evidence of large payoff from PR #41.

### Deferred: Strategy B — lazy procedural roster materialization

Do not implement unless Strategy A still leaves unacceptable latency. Lazy materialization changes transfer visibility, contracts, evolution, retirement, loans, deterministic IDs and save/reload timing and therefore has substantially higher correctness risk.

## Phase 9.12B — Strategy-A slice 1

The first production slice replaces `ProcessTransfersUseCase.processWeeklyContractsAndLoans()` with an atomic Room transaction containing three ordered SQL action sets:

1. expire loaned players whose contract starts the tick at exactly 1 week, preserving borrower and owner identity while zeroing contract/salary/starter state;
2. expire non-loaned players at exactly 1 week into canonical free-agent state (`teamId=null`, `originalTeamId=null`, zero salary, non-starter);
3. decrement only contracts that started the tick above 1 week.

**Ordering is part of the contract.** Expiration must happen before the `> 1` decrement. Otherwise a 2-week contract would become 1 and then expire in the same weekly tick.

This implementation:

- removes Kotlin materialization of the entire player table from the contract tick;
- removes tens of thousands of entity copies and chunked `@Update` calls;
- adds no entity or column;
- changes no FC26 mapper or source metadata;
- changes no team identity;
- changes no eFootball-only behavior;
- keeps Room at V21.

A focused regression test locks the pre-existing semantics for:

- a 2-week non-loan contract;
- a 1-week non-loan expiration;
- a 1-week loaned-player expiration;
- an already-zero contract.

## Measurement gate after slice 1

Run the real FC26 career path with the 82,125-player persisted universe and measure the same three blocks:

- CPU renewal;
- contract tick;
- CPU integrity.

Decision rule:

- if the contract block collapses and total weekly latency has sufficient margin, keep the existing persisted-roster architecture and do **not** implement lazy materialization;
- if CPU renewal remains the dominant unacceptable block, implement Strategy-A slice 2 using expiring-contract queries + roster summaries/actionable-team reads while preserving the integrity semantics;
- only consider Strategy B if both Strategy-A slices are insufficient.

## Final validation plan

Before merge eligibility, Phase 9.12 must pass:

- Debug build;
- focused ProcessTransfers regressions;
- focused CpuSquadManagement regressions;
- `CareerFunctionalFlowTest`;
- full non-stress suite;
- FC26 full-seed validation: 18,405/18,405, zero duplicate IDs and zero rating/attribute mutation;
- Room V21 verification;
- save-slot isolation and close/reopen coverage;
- migration compatibility checks;
- stress 20 seasons;
- stress 100 seasons;
- final diff audit with all temporary diagnostics/workflows removed.

No PR in this phase may be merged without explicit user authorization.
