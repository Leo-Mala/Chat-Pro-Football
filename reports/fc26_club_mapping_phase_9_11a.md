# Phase 9.11A — FC26 Club Mapping Expansion

Base: `main@80a372f820e81115255b7951f96125c38e0ea0cb`

## Safety contract

- FC26 remains the factual player source.
- `club_team_id` is never assumed to equal Pro Football `Team.id`.
- Automatic matches are limited to audited source-ID mappings, materialized stable identities, and the pre-existing conservative exact/core rules.
- Similarity scores are report-only and can never create a `MATCHED` result.
- Missing stable targets remain unresolved instead of being mapped to procedural city clubs.
- No FC26 overall, potential, attributes, player identity or Room schema is changed by this phase.

## Baseline before expansion

- FC26 players: 18,405
- FC26 clubs: 662
- Target teams: 2,544
- Matched clubs: 139
- Unmatched clubs: 511
- Ambiguous clubs: 12
- FC26 players imported: 3,979
- FC26 players skipped: 14,426
- Target teams requiring procedural fallback roster: 2,405

The diagnostic checkpoint will regenerate these metrics after the first audited mappings and will separately report stable factual clubs that are not physically materialized in the current `DefaultData` universe.
