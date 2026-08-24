# Pro Football 3.0.0 — Technical Changelog

## Phase 11.1 — Release de Produção e Entrega Oficial

- Bumped Android `versionCode` from 30 to 31 while retaining `versionName=3.0.0`.
- Added an explicit fail-closed Gradle gate for production signing.
- Kept production keystore/password material external to the repository.
- Added defensive ignore rules for keystore files and local `google-services.json`.
- Added exact-SHA Phase 11.1 release engineering automation.
- Added post-Guardian release-readiness packaging from the exact certified main SHA.
- Added production tag publication guarded by exact Required Certification + Trusted Guardian evidence.
- Added signing-certificate SHA-256 identity verification.
- Added APK/AAB SHA-256 checksums and release provenance generation.
- Added R8 `mapping.txt`, Room V22 schema, resolved dependency report, test evidence, and signing report to
  delivery bundles.
- Added deterministic CycloneDX 1.5 component SBOM generation from the resolved Release runtime dependency
  report.
- Added immutable GitHub Release publication using `gh release create --verify-tag`.
- Added release notes, release policy, and Android/Room-aware rollback documentation.
- Explicitly retained R8 minification and left resource shrinking disabled to avoid a new post-certification
  resource-removal variable.
- Did not modify gameplay, players, ratings, attributes, clubs, competitions, loans, FC26 assets, or Room
  schema/migration behavior.
