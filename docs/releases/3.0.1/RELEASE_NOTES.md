# Pro Football 3.0.1 — Release Notes

Pro Football 3.0.1 is the hotfix and runtime-stabilization release that follows the immutable 3.0.0 release.

The Android package keeps `applicationId=com.aistudio.brasfutretro.djuxzt` and advances the release
identity to `versionName=3.0.1` and `versionCode=32`.

## Android package

- applicationId: `com.aistudio.brasfutretro.djuxzt`
- versionName: `3.0.1`
- versionCode: `32`
- minSdk: `24`
- targetSdk: `35`
- compileSdk: `35`
- Room schema: `V22`
- R8: enabled

## Runtime fixes

- Editor Técnico opens its own route immediately instead of waiting for pre-career roster preparation or
  temporarily showing team selection. Re-entry no longer depends on a stale menu-local navigation lock.
- Player edits commit through Room before the dialog closes. Player overall/attributes and affected club
  strength are recalculated from the committed state, including the defined empty-roster rating.
- New-career persistence uses Room bulk operations instead of 100-row transaction loops. FC26 validation
  is cached/prewarmed once per process and deterministic FC26 player identity is memoized instead of
  repeating Unicode/regex normalization during roster ordering.
- Installed Release diagnostic evidence reduced real career creation from about `168.1 s` to `20.606 s`
  on the same full-career flow. In the optimized run, FC26 team seed/persistence was `10.483 s`, player
  persistence `2.865 s`, calendar generation `88 ms`, and database bootstrap `131 ms`.
- Monthly player evolution still processes the full world in the same deterministic order/RNG sequence,
  but no longer retains tens of thousands of heavy no-op result objects. Monthly counter maintenance is
  set-based and only changed evolution columns are written back.
- Successful market purchases now refresh from the persisted ownership source. Repeated confirmation of
  an already-committed accepted offer is idempotent and cannot create a second debit/transaction.
- Seasonal top-scorer screens use the resettable `gols` field rather than historical `careerGoals`.
  CPU-vs-CPU fixtures now attribute exactly the persisted match goals to deterministic player scorers.
- Match finalization is idempotent: an already-played fixture cannot have its committed score replaced by
  a stale concurrent result, while same-score knockout penalty metadata may still be completed.

## Scope and integrity

The runtime fixes do **not** modify FC26 factual assets, player identities/ratings as factual content,
club definitions, competition definitions, or use factual-data changes as a workaround. Room remains V22;
there is no destructive migration, direct push to `main`, force push, or CI gate reduction in this release.

The production path remains fail-closed: publication requires the exact `main` commit with successful
Required Certification and Trusted Guardian evidence. Controlled production signing material is verified
by certificate SHA-256 before the official artifacts are published.

## Distribution status

The Actions readiness bundle uses an ephemeral validation certificate and is not a store release. The
official GitHub Release is created only by the protected production workflow after the immutable release
tag, exact certified SHA, and production signing identity have all been verified.
