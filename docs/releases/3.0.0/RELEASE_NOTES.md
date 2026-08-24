# Pro Football 3.0.0 — Release Notes

Pro Football 3.0.0 is the production-delivery release built from the certified post-Phase-10.10 codebase.

This release does not introduce new gameplay or alter the certified FC26 factual dataset. Its Phase 11.1
scope is release engineering: Android versioning, fail-closed production signing, exact-SHA release
provenance, APK/AAB delivery packaging, checksums, R8 mapping retention, Room schema delivery, dependency
reporting, SBOM generation, rollback documentation, and an immutable GitHub Release path.

## Android package

- applicationId: `com.aistudio.brasfutretro.djuxzt`
- versionName: `3.0.0`
- versionCode: `31`
- minSdk: `24`
- targetSdk: `35`
- compileSdk: `35`
- Room schema: `V22`
- R8: enabled

## Data integrity

The FC26 certified factual data remains unchanged by Phase 11.1. The production release pipeline is
required to use the same exact commit that passes the repository's Required Certification and Trusted
Guardian.

## Distribution status

An Actions readiness bundle signed with the ephemeral validation certificate is not a store release.
The official GitHub Release is created only when the controlled production/upload key is provided and its
certificate SHA-256 fingerprint matches the configured expected fingerprint.

Google Play upload/review is an external step and is not claimed by the repository workflow.
