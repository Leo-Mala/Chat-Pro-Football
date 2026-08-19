# Phase 9.11A acceptance gate

The branch is acceptable only if the final non-draft pull-request head passes all of the existing Android CI checkpoints:

- FC26 importer/asset validation
- Debug APK build
- core regression tests
- migration/save safety
- 20-season stress test
- 100-season match-by-match stress test
- exported Room schema validation (Room V21)

The dedicated FC26 Club Mapping Expansion workflow must also pass:

- conservative matcher regression tests
- explicit stable-ID mapping tests
- explicit TeamTemplate mapping tests
- full FC26 seed integration
- candidate-report policy validation
- deterministic diagnostic artifact generation

No merge is authorized by this document. Merge requires separate explicit user authorization after the checks pass.
