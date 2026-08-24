# Pro Football 3.0.0 — Release Engineering

## Certified source baseline

Phase 11.1 starts from the immutable Phase 10.10 merge:

- main baseline: `d58819cf105122f869582fd79cfac21430cb9b48`
- baseline tree: `3b49658fc05be5beb858fdf7dd0808294064dff6`
- certified predecessor base: `ca9e3c14cf5e0ab6fabc61bbde822e6cbf15368c`
- Phase 10.9 Required Certification run: `32779896862` — success
- target release version: `versionName=3.0.0`, `versionCode=31`
- target immutable tag: `v3.0.0`

Phase 11.1 changes release engineering only. It does not authorize gameplay, club, competition, player,
rating, attribute, loan, or FC26 factual-data changes.

## Versioning policy

`versionCode` is strictly monotonic. Every Android store submission must use a value greater than every
previously submitted build, even when a rollback or rebuild is needed. `versionName` is the human-facing
semantic release identifier.

The release tag is `v<versionName>` and must point to the exact commit that passed the Required
Certification and Trusted Guardian. A tag is never used to rebuild a different commit, and release assets
are never reused across SHAs.

For 3.0.0 the repository moves from versionCode 30 to 31. Repeated store builds must increment versionCode
again; they must not overwrite or republish versionCode 31.

## Release classifications

### PRODUCTION_READY_VALIDATION_SIGNED

The post-merge readiness workflow builds APK/AAB from the exact certified main SHA using an ephemeral CI
certificate. This proves packaging, R8, manifest, AAB/APK generation, checksums, provenance, dependency
resolution, SBOM generation, and signing mechanics. It is **not** an official store binary.

### PRODUCTION_SIGNED

This classification is allowed only when the production workflow receives the controlled external signing
material, verifies the expected certificate SHA-256 fingerprint, rebuilds from the exact certified tag SHA,
and publishes the GitHub Release from those newly generated assets.

No ephemeral, debug, or validation certificate is ever classified as production.

## Required production signing secrets

The repository intentionally contains no production keystore or signing password. Configure these GitHub
Actions secrets before creating the production tag:

- `PRODUCTION_KEYSTORE_BASE64` — base64 encoding of the controlled Android upload/production keystore.
- `PRODUCTION_STORE_PASSWORD` — keystore password.
- `PRODUCTION_KEY_ALIAS` — exact production/upload key alias.
- `PRODUCTION_KEY_PASSWORD` — private-key password.
- `PRODUCTION_SIGNING_CERT_SHA256` — expected SHA-256 fingerprint of the signing certificate.

The workflow materializes the keystore only under the runner temporary directory, with mode 0600, and
removes it at the end. Passwords and keystore bytes are never committed or intentionally printed.

The expected certificate fingerprint is mandatory. A credential set that signs successfully with a
different certificate fails closed.

## Workflow

`.github/workflows/phase111-release-engineering.yml` has two independent paths.

### Post-merge readiness

`Phase 10.9 Required Certification` → `Phase 10.9 Trusted Guardian` → Phase 11.1 readiness bundle.

The readiness job:

1. checks out the exact Guardian `head_sha`;
2. proves that SHA remains on `main`;
3. queries GitHub Actions and requires successful Required Certification **and** Trusted Guardian on the
   same exact SHA;
4. builds Debug, Release APK, and Release AAB;
5. executes the Release startup smoke;
6. validates the R8 mapping output and Room V22 schema;
7. creates a resolved dependency report and CycloneDX SBOM;
8. verifies the validation signature;
9. creates SHA-256 checksums and provenance;
10. uploads `phase-11-1-delivery-<SHA>` as a GitHub Actions artifact.

This artifact is explicitly validation-signed and not for store distribution.

### Production tag

A push of `v3.0.0` runs the production path. It refuses publication unless:

- the tag resolves to a commit on main;
- the tag name exactly matches `versionName`;
- the exact commit has a successful main-push Required Certification;
- the exact commit has a successful Trusted Guardian;
- all five production signing secrets are available;
- Gradle's `requireProductionSigning` gate is satisfied;
- APK and AAB signatures verify;
- the APK certificate SHA-256 equals `PRODUCTION_SIGNING_CERT_SHA256`;
- the GitHub Release for that tag does not already exist.

Only then does the workflow create the GitHub Release with `--verify-tag`.

## Release outputs

Both readiness and production builds materialize, as applicable:

- Release APK;
- Release AAB;
- R8 `mapping.txt`;
- `provenance.json`;
- `SHA256SUMS.txt`;
- signing report;
- Room V22 exported schema;
- resolved release dependency report;
- CycloneDX 1.5 SBOM;
- exact-head test/certification evidence;
- technical changelog;
- rollback guide;
- release notes.

The production GitHub Release is built again from the tagged commit. No artifact from the PR or an older
commit is reused.

## Build and platform identity

The 3.0.0 release contract keeps:

- applicationId: `com.aistudio.brasfutretro.djuxzt`
- compileSdk: 35
- targetSdk: 35
- minSdk: 24
- Room schema: V22
- Room automatic migration floor: V14
- R8/minification: enabled
- resource shrinking: explicitly disabled for 3.0.0 to avoid introducing a new resource-removal variable
  after final functional certification

The provenance file records the exact commit/tree SHA, version, SDK levels, Gradle, AGP, Kotlin, JDK,
workflow run, certification run, Guardian run, certificate fingerprint, and SHA-256 of every delivery
artifact.

## Reproducibility model

The release is source-reproducible and traceable: immutable Git input, pinned Gradle wrapper checksum,
pinned CI actions, explicit toolchain versions, exact-head certification, artifact hashing, and complete
provenance are recorded.

Android archives are not claimed to be bit-for-bit reproducible across arbitrary runner environments.
The authoritative binary is the binary whose SHA-256 is in the release provenance and checksums generated
by the exact release workflow run.

## Google Play

Phase 11.1 does not assume access to Google Play Console. The production workflow prepares a Play-ready AAB
when official signing material is present, but it does not claim Play upload, review, or publication.

If Play App Signing is used, `PRODUCTION_KEYSTORE_BASE64` is normally the controlled **upload key** used to
sign the AAB submitted to Google Play. Google retains and uses the app-signing key according to the Play
App Signing configuration.

## Security rules

- Never commit keystores, signing passwords, tokens, or real API credentials.
- Never replace production signing with debug or ephemeral signing.
- Never print signing passwords or keystore bytes.
- Never publish a release for a SHA without exact Required Certification and Guardian success.
- Never overwrite a GitHub Release or reuse assets from another SHA.
- Never lower versionCode for a store rollback.
- Never use `fallbackToDestructiveMigration`.
- Never bypass or weaken the Required Certification gate.
