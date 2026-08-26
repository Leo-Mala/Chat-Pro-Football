# Pro Football 3.0.1 — Technical Changelog

## Hotfix 3.0.1 and Phase 11.1 alignment

- PR #66 intentionally advanced Android `versionCode` from 31 to 32 and `versionName` from 3.0.0 to 3.0.1.
- The application ID and production signing identity were preserved across that hotfix.
- The already-certified 3.0.1 hotfix includes the launcher artwork/wiring and the runtime corrections that
  were part of PR #66; this release-engineering correction does not alter those runtime changes.
- Updated the Phase 11.1 fail-closed project policy to require the certified `versionCode=32` and
  `versionName=3.0.1` instead of the obsolete 3.0.0/vc31 contract.
- Updated validation and production artifact names to `Pro-Football-3.0.1-vc32`.
- Updated Phase 11.1 release packaging to consume the dedicated 3.0.1 release notes, technical changelog,
  and rollback procedure while preserving the historical 3.0.0 documents unchanged.
- Required Certification, Trusted Guardian, exact-SHA checks, production signing verification, R8,
  Room V22 delivery evidence, provenance, SBOM, and checksum gates remain enabled and fail-closed.
- No gameplay, players, ratings, clubs, competitions, FC26 factual data, Room schema/migrations, saves,
  or game-engine behavior is modified by this Phase 11.1 correction.
