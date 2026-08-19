# Phase 9.11A1 — Factual Club Target Materialization

Base: `main@222e4e154466c8e2e977a78f6ffc34d1eb595001`

## Objective

Materialize the already-verified 2026/27 European top-flight club identities into the new-save `DefaultData` universe so FC26 can resolve reserved stable targets without fuzzy matching.

## Safety contract

- FC26 remains the factual player source.
- No FC26 overall, potential or player attributes are changed.
- No eFootball-only player is imported.
- No Room migration is introduced; Room remains V21.
- Stable IDs come from `StableTeamIdentityRegistry`.
- Canonical top-flight names/division membership come from `EuropeanDomesticBaseline2026_27`.
- Fully sourced explicit templates keep their explicit city/stadium/gameplay metadata.
- Identity-only targets may reuse deterministic internal slot metadata, but that metadata is explicitly NOT promoted to factual provenance.
- Similarity/fuzzy scoring cannot materialize or match a club.
- Lower-division non-stable ID allocation preserves the pre-materialization catalog ordering.

## Baseline inherited from Phase 9.11A

- FC26 players: 18,405
- FC26 clubs: 662
- target teams: 2,544
- matched clubs: 160
- unmatched clubs: 492
- ambiguous clubs: 10
- imported FC26 players: 4,583
- skipped FC26 players: 13,822
- players with mapped club: 4,494
- fallback rosters required: 2,384
- stable factual targets missing: 130 clubs
- FC26 players blocked by those missing stable targets: 3,597

## Validated result

The dedicated Phase 9.11A1 CI regenerated the result from the real FC26 asset and the final head passed the full Android gate.

- verified countries: 20
- factual top-flight clubs materialized: 320
- target teams after materialization: 2,524
- matched FC26 clubs: 296
- unmatched clubs: 364
- ambiguous clubs: 2
- imported FC26 players: 8,355
- skipped FC26 players: 10,050
- players with mapped club: 8,266
- fallback rosters required: 2,228
- stable factual targets missing: 0
- stable targets present but unresolved: 0
- gained matches: 137
- gained players: 3,798
- lost matches: 1
- lost players: 26
- net matched-club gain: +136
- net imported-player gain: +3,772
- non-stable ID redirects: 0

The only lost match is FC26 source club `68` / `FC Metz` (26 players). Its former procedural target `Metz FC` is outside the verified 2026/27 top-flight baseline, so this phase deliberately does not invent a lower-tier factual placement. It remains explicit scope for a future lower-tier factual materialization phase.

All 30 redirects detected among previously matched clubs now resolve to stable factual identities; no non-stable club ID is silently redirected.

The detailed deterministic diagnostics remain CI artifacts (`fc26_factual_target_materialization_report.json`, club mapping, unmatched candidates and missing-target report) rather than being permanently versioned as large generated files.

## Final gate

Final validated head before this documentation-only marker: `70b74286e85a48c200e2fd4fc35ac92362df4c53`.

- `FC26 Factual Club Target Materialization #11`: SUCCESS
- `Android CI Build #534`: SUCCESS
- Debug APK: SUCCESS
- core regression / migration-save safety: SUCCESS
- 20/100-season stress: SUCCESS
- Room schema V21: SUCCESS
- Room migration: none

This documentation-only marker does not change runtime, matching, IDs, datasets, tests, Room schema, or application behavior. The PR must return to Draft and must not be merged without fresh explicit user authorization.
