# Project execution rules

## Standing authorization for phase completion and merge

For development phases explicitly requested by the repository owner, the following is a standing project rule:

> Execute esta fase até o final e, se todos os testes estiverem verdes, o PR estiver APTO e o head auditado não tiver mudado, está autorizado a fazer o merge automaticamente.

This authorization remains in force for future phases until the repository owner explicitly revokes or changes it.

### Required conditions for automatic merge

Automatic merge is authorized only when all of the following are true:

1. The requested phase is fully implemented within its approved scope.
2. All required tests and CI checks for that phase are green.
3. The final technical audit classifies the PR as **APTO PARA MERGE**.
4. The PR head SHA is exactly the same SHA that was audited and validated.
5. No unexpected base-branch movement, merge conflict, scope violation, or material unresolved regression is present.
6. Temporary diagnostics, temporary workflows, and experimental artifacts that are not intended for the final implementation have been removed.

### Stop conditions

Do **not** merge automatically when any of these conditions is true:

- required CI or tests are failing, cancelled, incomplete, or were bypassed;
- the final audit classifies the PR as **NÃO APTO PARA MERGE**;
- the PR head changed after the final audit;
- the base branch changed in a way that invalidates the audited result;
- there is a merge conflict;
- the implementation exceeded the user-approved scope;
- a test was ignored, removed, weakened, retried as a substitute for a real fix, or a timeout was increased only to hide a regression.

When a stop condition occurs, preserve the branch/PR and report the concrete blocker instead of merging.

### Merge behavior

When every required condition is satisfied, do not request a second merge confirmation. Perform the normal merge, then verify and report:

- PR final state;
- audited head SHA;
- merge commit SHA;
- previous main SHA;
- new main SHA;
- final CI/test status.

This rule authorizes the merge step; it does not authorize bypassing repository protections, CI gates, scope constraints, or explicit freeze instructions.

## Risk-based CI certification policy

Pull-request validation is proportional to the technical risk of the exact diff. The classifier is fail-closed: a mixed, unknown, production-code, persistence, data, build-logic, test, tooling or CI-policy change is always promoted to **full certification**.

### Lightweight PR certification

A PR may use lightweight certification only when its diff is restricted to low-risk presentation/release-wiring changes recognized by `.github/scripts/ci_scope.py`, such as launcher artwork/resources, strings, colors, dimensions, styles/themes, safe launcher/label/theme Manifest wiring, and `versionCode`/`versionName` changes.

Lightweight certification must still validate the exact PR head and must include:

- Debug APK build;
- Release APK and AAB build;
- APK/AAB signature/structure verification using CI validation signing;
- Release startup smoke;
- fail-closed reclassification of the exact diff before success.

Long-horizon season stress, full FC26 materialization/audits, full-scale rollover performance, Room/save migration suites, UI golden matrix and the full Android emulator matrix are not mandatory for a genuinely lightweight PR.

### Full PR certification

Full certification remains mandatory when the diff touches or may affect gameplay, production Kotlin/Java code, saves, Room/database/migrations, calendar/season rollover, player/club/competition data, FC26 assets/tooling, tests, Gradle/build logic, CI/security policy, performance-sensitive runtime paths, or any path that the classifier does not explicitly recognize as low risk.

The existing Required Certification gates remain authoritative for full PRs, including Core, Migration/Save Recovery, FC26, 20/100-season stress, UI golden/accessibility, full-scale rollover performance and installed Android certification.

### Main and production release

Every push/merge to `main` is force-classified as **full certification**, regardless of how small the originating PR was. Production release/signing remains a separate protected operation and must only use a fully certified exact `main` SHA.

Standalone heavy workflows remain available as manual diagnostics, but they must not duplicate the automatic PR matrix when the Required Certification already covers the same gate.
