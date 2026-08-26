# Pro Football 3.0.1 — Release Notes

Pro Football 3.0.1 is the certified hotfix release that follows the immutable 3.0.0 release.

The Android package keeps `applicationId=com.aistudio.brasfutretro.djuxzt` and advances the certified
release identity to `versionName=3.0.1` and `versionCode=32`. The 3.0.1 hotfix was introduced by PR #66;
this Phase 11.1 correction only aligns release-engineering validation, artifact naming, and release
documentation with that already-certified project version.

## Android package

- applicationId: `com.aistudio.brasfutretro.djuxzt`
- versionName: `3.0.1`
- versionCode: `32`
- minSdk: `24`
- targetSdk: `35`
- compileSdk: `35`
- Room schema: `V22`
- R8: enabled

## Scope and integrity

This release-engineering correction does not modify gameplay, players, ratings, clubs, competitions,
FC26 factual data, Room schema/migrations, save behavior, or the game engine. It does not downgrade or
reuse `versionCode=31`, and it leaves the historical 3.0.0 release documentation unchanged.

The production path remains fail-closed: it must use the exact `main` commit that has successful Required
Certification and Trusted Guardian evidence, and controlled production signing material is verified by
certificate SHA-256 before publication.

## Distribution status

The Actions readiness bundle is signed only with the ephemeral validation certificate and is not a store
release. An official GitHub Release can be created only by the protected production workflow after the
immutable release tag, exact certified SHA, and production signing identity have all been verified.
