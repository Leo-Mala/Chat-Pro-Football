# Pro Football 3.0.1 — Rollback Procedure

## Principle

Android store rollback is a **forward version operation**, not a `versionCode` downgrade.

Pro Football 3.0.1 uses `versionCode=32`. If this release must be rolled back or replaced, do not try to
publish 3.0.0/vc31 as an update. Create a new build with a `versionCode` strictly greater than 32 from a
deliberately selected known-good source, certify that exact SHA, sign it with the controlled production
key, and publish it under a new immutable release tag.

## Source and artifact identification

Use `provenance.json` and `SHA256SUMS.txt` for every released build to identify the release tag, exact
commit and tree SHA, versionName/versionCode, Required Certification run, Trusted Guardian run, signing
certificate SHA-256, and APK/AAB SHA-256. Do not select a rollback artifact only by filename.

## Safe rollback steps

1. Identify the last known-good source and exact commit from release provenance.
2. Review database compatibility before selecting that source.
3. Create a new fix/rollback branch; do not move or overwrite an existing immutable tag.
4. Set a new `versionCode` greater than 32 and a new release identity as required by policy.
5. Run the full Required Certification and Trusted Guardian on the exact candidate SHA.
6. Build APK/AAB from that exact certified source and verify the controlled signing certificate.
7. Generate new provenance, SBOM, checksums, mapping and test evidence.
8. Publish only as a new immutable release after all protected release gates succeed.

## Room and save constraints

The current database schema is V22. This procedure does not authorize a schema downgrade,
`fallbackToDestructiveMigration`, destructive save handling, or any bypass of migration/recovery gates.
A rollback/fix build must preserve compatible handling of existing V22 user data and be recertified before
distribution.

## Store rollout mitigation

When appropriate, halt or reduce an external store rollout while a forward-fix build is prepared. Store
operations are outside this repository; no Phase 11.1 workflow may weaken repository certification or
production-signing controls to accelerate such an operation.
