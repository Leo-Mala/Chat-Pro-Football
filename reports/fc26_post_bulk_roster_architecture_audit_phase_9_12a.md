# Phase 9.12A — FC26 Post-Bulk Roster Architecture Audit

## Baseline

- repository: `Leo-Mala/Chat-Pro-Football`
- base branch: `main`
- immutable base SHA: `c970583436edc28c184039146f6607895cb3a0f5`
- base contains merged PR #41 (`feat: import all FC26 players in bulk`)
- FC26 snapshot: `2025-09-19`
- source dataset players: `18,405`
- source clubs: `662`
- Room: `V21`

This phase starts from the post-bulk-import state. It does **not** revert the decision to import every FC26 player, does not reintroduce club-by-club research, and does not use API-Football.

## Verified post-PR #41 state

The final PR #41 artifact (`FC26 Bulk Player Import #19`, reports artifact `9393549795`) records:

| Metric | Value |
|---|---:|
| FC26 players imported | 18,405 |
| FC26 players not imported | 0 |
| Pro Football target teams | 2,524 |
| FC26 clubs MATCHED | 400 |
| FC26 clubs UNMATCHED | 260 |
| FC26 clubs AMBIGUOUS | 2 |
| FC26 players with mapped club | 11,155 |
| true FC26 free agents | 89 |
| FC26 unassigned from unresolved clubs | 7,161 |
| fallback rosters required | 2,124 |
| fallback players (`2,124 × 30`) | 63,720 |
| total persisted players | 82,125 |
| duplicate player IDs | 0 |
| duplicate Team IDs | 0 |
| overall/potential/attributes mutated | 0 / 0 / 0 |

The current planner creates one procedural roster for every target `Team` that does not have a `MATCHED` FC26 source club. Therefore:

`2,524 target teams - 400 mapped target teams = 2,124 fallback rosters`.

## Critical finding: club matching alone cannot remove most fallback players

There are only `262` unresolved source clubs (`260 UNMATCHED + 2 AMBIGUOUS`). Even in the theoretical best case where every unresolved FC26 club is later associated safely to one distinct existing target team, at most `262` current fallback rosters can be replaced directly by those real FC26 squads.

Maximum direct fallback reduction from resolving every remaining FC26 club:

`262 clubs × 30 procedural players = 7,860 procedural players`.

The lower bound that would still remain after perfect resolution of all current FC26 source clubs is therefore:

- fallback target teams: `2,124 - 262 = 1,862`
- procedural players: `1,862 × 30 = 55,860`
- total persisted players, before any other architecture change: `82,125 - 7,860 = 74,265`

This means **87.7% of the current procedural fallback population cannot be removed merely by resolving the remaining FC26 club identities**, because the FC26 snapshot contains only 662 source clubs while the Pro Football universe contains 2,524 target teams.

Therefore the next scalability problem is primarily a **roster architecture / weekly-processing problem**, not just a club-matching problem.

## Unresolved FC26 population

The PR #41 unresolved artifact contains exactly `262 clubs / 7,161 players`.

Materialization classification:

| Classification | Clubs | Players |
|---|---:|---:|
| `NO_STABLE_IDENTITY` | 216 | 5,927 |
| `UNKNOWN_COUNTRY_CONTEXT` | 46 | 1,234 |
| **Total** | **262** | **7,161** |

The only two `AMBIGUOUS` clubs remain:

- Caracas FC — 28 players
- CF Montréal — 25 players

No fuzzy/review-only candidate may be promoted automatically in this phase.

## Performance context

The post-bulk career diagnostic measured `82,125` persisted players and showed that three weekly blocks originally consumed approximately:

- CPU contract renewal: `27.317 s`
- weekly contract processing: `34.631 s`
- CPU squad integrity: `53.198 s`
- combined measured hot path: `115.146 s`

PR #41 already reduced the measured combined path to approximately `46.748 s`, with CPU squad integrity reduced to approximately `116 ms` by reusing the weekly player snapshot and removing an in-memory N×players lookup.

The remaining cost is therefore concentrated mainly in operations that still load/process very large portions of the `82,125` player table during weekly contract/lifecycle work.

## Phase 9.12A objective

Determine the safest architecture for supporting the complete 2,524-team world without requiring expensive full-table player processing every week, while preserving all existing gameplay contracts.

The audit must answer:

1. Which weekly/career systems truly require every procedural fallback player to be persisted at all times?
2. Which systems need only roster counts/strength rather than full `Player` rows?
3. Which systems require full players only for the user club, transfer-market-visible clubs, loan endpoints, or clubs participating in active detailed competitions?
4. Which current operations scan all players but could instead query only actionable subsets (contract expiring, active loan, injury/card lifecycle, transfer candidate, evolution cohort, etc.)?
5. Whether lazy materialization of procedural rosters can preserve deterministic IDs and save/reload semantics without a Room schema change.
6. Whether keeping all procedural rosters persisted but removing global weekly scans provides enough performance margin, avoiding a larger data-model change.
7. What exact measurable benefit comes from resolving the 262 remaining FC26 source clubs separately from roster-architecture optimization.

## Safety constraints

Phase 9.12A is an audit/gate first. During this audit:

- do not remove any of the 18,405 FC26 players;
- do not change FC26 `overall`, `potential`, position or `Atributos`;
- preserve `StableRealPlayerIdentity`;
- preserve stable team identities;
- keep true dataset free agents distinct from `UNASSIGNED_SOURCE_CLUB` players;
- do not auto-map unresolved clubs using fuzzy similarity;
- do not research clubs one by one;
- do not use API-Football;
- do not change eFootball-only behavior;
- keep Room V21 unless a later implementation phase proves a migration is unavoidable;
- do not touch PR #34 or PR #27;
- do not merge this phase without a dedicated final validation and explicit authorization.

## Candidate implementation strategies to evaluate

### Strategy A — Query/action-set optimization first

Keep the 63,720 procedural fallback players persisted, but eliminate weekly full-table processing where only a small actionable subset is needed.

Potential benefits:
- minimal semantic risk;
- no roster lifecycle redesign;
- likely no Room migration;
- save/reload and transfer behavior remain structurally unchanged.

This is the preferred first implementation direction if profiling confirms sufficient gain.

### Strategy B — Lazy procedural roster materialization

Persist full procedural players only when a team becomes gameplay-relevant, while retaining deterministic ability to reconstruct the same roster.

Potential benefit:
- largest database/player-count reduction.

Risks to audit before implementation:
- transfer market visibility;
- CPU contracts and squad management;
- loans and ownership;
- player evolution/history;
- season transitions and retirement;
- deterministic identity after save/reload;
- materialization timing affecting simulation results.

This strategy must not be implemented until those invariants are proven.

### Strategy C — Smaller procedural rosters

Reducing the current fixed `30` players per fallback team would lower persistence directly, but may silently change squad-depth, transfer, injury, contract and CPU-management behavior. It is **not** considered safe without first proving minimum-roster requirements across the game engine.

### Strategy D — Resolve remaining FC26 clubs

Still useful and desirable, but mathematically insufficient as the main solution: even perfect resolution removes at most 7,860 of the 63,720 procedural players.

It should remain a separate data-quality stream after the architecture decision, not be confused with the core scalability fix.

## Audit exit gate

Before Phase 9.12B may change production behavior, 9.12A must produce:

- exact read/write dependency map for weekly player lifecycle systems;
- exact set of full-table player reads in the weekly path;
- measured per-stage timings on a fresh FC26 career;
- classification of each full-table scan as required vs replaceable;
- deterministic roster/materialization invariants;
- explicit recommendation between Strategy A, B, or a staged A→B path;
- projected persisted-player count and weekly-time impact;
- regression plan covering career flow, transfers, contracts, loans, evolution, retirement, save/reload, 20 seasons and 100 seasons;
- confirmation that FC26 ratings/attributes and Room safety remain intact.

## Initial recommendation

Start with **Strategy A: query/action-set optimization** because PR #41 already demonstrated that eliminating one unnecessary 82k-player reread reduced CPU squad-integrity work from ~53.2 seconds to ~0.116 seconds without changing persisted data or Room schema.

Only if targeted query optimization still leaves unacceptable weekly latency should Phase 9.12B consider lazy procedural roster materialization.
