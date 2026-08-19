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

The dedicated CI regenerates the after-metrics from the real FC26 asset and writes `reports/fc26_factual_target_materialization_report.json`. No target gain is hardcoded as a passing result beyond requiring that Phase 9.11A coverage is not reduced and all previously missing verified stable targets become materialized.
