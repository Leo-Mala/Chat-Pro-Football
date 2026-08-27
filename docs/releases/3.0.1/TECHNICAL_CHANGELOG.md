# Pro Football 3.0.1 — Technical Changelog

## Release identity and protected delivery

- Android identity is `versionCode=32` and `versionName=3.0.1`; `applicationId` is unchanged.
- Validation and production artifact names remain `Pro-Football-3.0.1-vc32`.
- Required Certification, Trusted Guardian, exact-SHA checks, production signing verification, R8,
  Room V22 delivery evidence, provenance, SBOM, checksums and immutable-tag validation remain enabled
  and fail-closed.
- Historical 3.0.0 release documents remain unchanged.

## Runtime stabilization

### Editor and reactive state

- Removed the main-menu wait on editor seed bootstrap; the Editor route is entered immediately and owns
  asynchronous preparation.
- Serialized editor preparation per `GameViewModel` to prevent duplicate pre-career seed materialization.
- Player editor save waits for the Room commit before closing and prevents simultaneous duplicate saves.
- Recalculates affected source/destination club strength in the same transaction, including the engine's
  empty-roster result.
- Uses the canonical player-attribute overall calculation so edited attributes and displayed strength stay
  synchronized without leave/re-enter refreshes.

### Career creation performance

- Replaced repeated 100-row Room persistence loops for teams, players and fixtures with DAO bulk writes.
- Indexed FC26 club matching and removed avoidable repeated lookup work.
- Added single-flight cache for validated FC26 dataset loading and asynchronous application prewarm.
- Memoized deterministic FC26 player `stableId` derivation, avoiding repeated Unicode normalization,
  regex/date parsing during sorting and mapping without changing any generated ID.
- Added phase timing for database bootstrap, roster materialization, club setup, competition/calendar,
  persistence and total duration, plus persistence sub-breakdown.
- Performance telemetry now reports the roster seed actually consumed by Room rather than a procedural
  fallback list that FC26 may replace.
- Installed Release evidence on the optimized full-career flow: total `20.606 s` versus approximately
  `168.1 s` before optimization; `teamSeedAndPersistence=10.483 s`, `playerPersistence=2.865 s`,
  `fixturePersistence=222 ms`, `saveRowPersistence=15 ms`, `databaseBootstrap=131 ms`,
  `rosterMaterialization=1.798 s`, `clubSetup=3.180 s`, and `competitionCalendar=88 ms`.

### Monthly evolution

- Added compact production planning that preserves processing order and RNG calls while retaining heavy
  `PlayerEvolutionResult` objects only for players with persisted deltas.
- Monthly counters are reset with set-based SQL; changed players receive column-scoped writes only.
- Prepared plans remain fail-closed against stale save/player/team state and do not restore unrelated
  contract, salary, ownership, fitness or transfer columns.
- Added a 60,000-player regression proving no-op monthly evolution does not retain the full world as heavy
  result objects.

### Transfers

- Purchase callbacks return to UI only after the transfer transaction completes, keeping market visibility
  aligned with persisted ownership.
- Accepted purchase confirmation is idempotent when the UI still holds the pre-transfer player snapshot:
  the already-persisted player/save are returned without a second debit, installment or transaction row.
- Added persistence/reopen and no-double-charge regression coverage.

### Match statistics and top scorers

- Seasonal leaderboards use `Player.gols`; historical `careerGoals` remains cumulative and is preserved
  across season rollover.
- Detailed user fixtures increment both seasonal and cumulative goals and season/career appearances.
- CPU-vs-CPU simulation attributes exactly the committed fixture goals to deterministic scorers using the
  same position/force weighting model as detailed matches, with one global roster read instead of N+1.
- CPU score plus scorer updates are committed atomically and repeated week simulation cannot duplicate
  player totals.
- Played fixture scores are immutable against stale concurrent finalization; same-score knockout penalty
  metadata can still be completed. The UI rereads the committed fixture after simulation before displaying
  the result.

## Data and schema integrity

- No FC26 factual asset, factual player rating, factual club definition or competition definition is
  changed by these runtime fixes.
- Room remains schema V22 and no destructive migration is introduced.
- No CI gate was disabled, weakened or replaced by a timeout increase.
- No direct push to `main` or force push is part of this change set.
