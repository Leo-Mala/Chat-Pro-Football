# Pro Football 3.0.0 — Rollback Procedure

## Principle

Android store rollback is a **forward version operation**, not a versionCode downgrade.

The 3.0.0 release uses versionCode 31. Google Play and normal Android update rules do not allow publishing
an older or equal versionCode as an update. If 3.0.0 must be rolled back, create a new build with a
versionCode greater than 31 from a deliberately selected known-good source, certify that new exact SHA,
sign it with the controlled production/upload key, and publish it as a new release.

## Source and artifact identification

For every released build use `provenance.json` and `SHA256SUMS.txt` to identify:

- release tag;
- exact commit SHA;
- tree SHA;
- versionName/versionCode;
- Required Certification run;
- Trusted Guardian run;
- signing certificate SHA-256;
- APK/AAB SHA-256.

Do not select a rollback artifact only by filename.

## Safe rollback steps

1. Identify the last known-good release and its exact commit from that release's provenance.
2. Review database compatibility before selecting that source.
3. Create a new rollback/fix branch from an approved source; do not move an existing immutable release tag.
4. Set a new `versionCode` strictly greater than every submitted store build.
5. Keep or advance `versionName` according to the release policy; never reuse a tag for different bytes.
6. Run the full Required Certification and Trusted Guardian on the new exact head.
7. Build and sign new APK/AAB from that exact certified head.
8. Verify the production certificate fingerprint.
9. Generate new checksums/provenance and publish a new immutable release.
10. Use the newly signed AAB for any Google Play rollout.

## Room constraints

The current database schema is V22 and the supported automatic migration floor is V14.

The project certifies **forward migrations** through V22. It does not provide destructive fallback and it
does not certify schema downgrade migrations. Installing an APK built from source that expects an older
Room schema over a device already migrated to V22 can fail or be unsafe.

Therefore, a rollback must not simply reinstall an old APK over a V22 user database. Prefer a forward-fix
build that still understands V22. If a historical codebase is selected, first port/retain V22 compatibility
and recertify it.

Do not add `fallbackToDestructiveMigration` to make a rollback appear to work.

## Store rollout mitigation

When the issue permits, prefer halting or reducing the active store rollout while a forward-fix build is
prepared. Google Play operational actions are external to this repository and must be performed in the
authorized Play Console account.
