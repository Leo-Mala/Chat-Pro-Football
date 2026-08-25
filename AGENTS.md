# Project execution policy

This file defines the standing execution, certification, and merge rules for this repository. Apply the rules in this order: **explicit current user instruction → approved phase scope/freeze → repository protections and trusted CI → fastest safe execution**. A lower-priority rule never overrides a higher-priority one.

## 1. Core operating rule: fastest safe path

Always choose the **fastest safe path** that reaches the requested result without weakening correctness, security, repository protections, data integrity, or required certification.

- Stay strictly inside the approved phase/PR scope. Do not add speculative refactors, optional hardening, unrelated cleanup, or extra deliverables to an active phase.
- Before changing a candidate HEAD that is already being certified, consolidate all currently knowable P0/P1/P2 blockers and batch compatible fixes into one change whenever safe.
- Prefer fewer commits, fewer CI runs, fewer external calls, and lower wall-clock time when alternatives are equally safe and compliant.
- Do not restart, cancel, duplicate, or rerun successful validation unnecessarily. Reuse evidence only when repository policy allows it and its exact HEAD/base binding is still valid.
- Prefer the authoritative Required Certification over equivalent standalone heavy workflows. Standalone heavy workflows are diagnostics when the Required Certification already covers the same gate.
- Use LIGHT certification whenever the trusted fail-closed classifier permits it. Do not promote a genuinely lightweight change to FULL without a concrete technical or policy reason.
- If certification is near completion, defer optional work to a follow-up instead of invalidating the HEAD. Change the HEAD only for a real correctness, security, scope, review, P0/P1/P2, or gate requirement.
- Diagnose the root cause before rerunning a failed job. Rerun only a demonstrated transient/infrastructure failure; never use reruns, ignored tests, weakened assertions, or larger timeouts to hide a deterministic defect.
- Preserve concurrency/cancellation rules that eliminate obsolete runs and avoid duplicate automatic heavy matrices.

**Fastest never means bypassing** branch/ruleset protection, Required Certification, Trusted Guardian, exact-head/base validation, required reviews/tests, security controls, data-integrity rules, or explicit user constraints.

## 2. Standing authorization for phase completion and merge

For development phases explicitly requested by the repository owner, the standing authorization is:

> Execute esta fase até o final e, se todos os testes estiverem verdes, o PR estiver APTO e o head auditado não tiver mudado, está autorizado a fazer o merge automaticamente.

This authorization remains valid until the repository owner explicitly changes or revokes it. Do not ask for a second merge confirmation when all merge conditions below are satisfied.

### Merge conditions — all required

1. The requested phase is complete and remains within its approved scope.
2. Every required test and CI check for the exact candidate HEAD is green.
3. The final technical audit classifies the PR as **APTO PARA MERGE**.
4. The PR HEAD is exactly the SHA that was audited and certified.
5. The audited base is still valid; no material base movement, merge conflict, scope violation, or unresolved regression exists.
6. All blocking review findings are resolved and no required review remains outstanding.
7. Temporary diagnostics, temporary workflows, and experimental artifacts not intended for the final implementation are removed.

### Stop conditions — never merge

Do **not** merge when any of the following is true:

- required CI/tests are failing, cancelled, incomplete, stale, or bypassed;
- the final audit is **NÃO APTO PARA MERGE**;
- the PR HEAD changed after audit/certification;
- base movement invalidated the audited result;
- a merge conflict or material unresolved regression exists;
- implementation exceeded the approved scope or violated an explicit freeze;
- a blocking review finding remains unresolved;
- a test/gate was removed, ignored, weakened, retried as a substitute for a fix, or given a larger timeout merely to hide a regression.

When a stop condition occurs, preserve the branch/PR, fix the root cause when it is within scope, and report the concrete blocker instead of bypassing it.

### Merge behavior

When every merge condition is satisfied, perform the normal protected merge and then verify/report:

- PR final state;
- audited HEAD SHA;
- previous `main` SHA;
- merge commit SHA;
- new `main` SHA;
- final required CI/Guardian status.

Standing authorization permits the merge step only; it never permits bypassing repository protections, trusted CI, scope constraints, or explicit freeze instructions.

## 3. Exact HEAD/base and trusted-CI discipline

- Certification evidence is valid only for the exact candidate HEAD and audited base it was produced for.
- Any HEAD change invalidates prior candidate certification unless a repository mechanism explicitly proves otherwise.
- Material base movement requires re-evaluation before merge; never assume old evidence still applies.
- Changes to trusted CI/security policy must remain fail-closed and use the repository's authorized trusted-evolution mechanism. A candidate must never self-authorize a trust-boundary change.
- Trusted Guardian and Required Certification must validate the exact relevant SHA whenever repository policy requires them.
- Never force-push or bypass required rulesets/protections to make a candidate mergeable.

## 4. Risk-based CI certification

Pull-request validation is proportional to the technical risk of the **exact diff**. Classification is fail-closed: mixed, unknown, or unrecognized changes are FULL.

### LIGHT certification

LIGHT is allowed only when `.github/scripts/ci_scope.py` recognizes the exact diff as low risk, such as approved launcher artwork/resources, strings, colors, dimensions, styles/themes, safe launcher/label/theme Manifest wiring, and literal `versionCode`/`versionName` changes.

LIGHT must still validate the exact PR HEAD with all of these gates:

- Debug APK build;
- Release APK and AAB build;
- APK/AAB signature and structure verification using CI validation signing;
- Release startup smoke;
- fail-closed reclassification of the exact diff before success.

A genuinely LIGHT PR does **not** require long-horizon season stress, full FC26 materialization/audits, full-scale rollover performance, Room/save migration suites, UI golden matrix, or the full Android emulator matrix.

### FULL certification

FULL is mandatory when the diff touches or may affect any of the following:

- gameplay or production Kotlin/Java runtime code;
- saves, Room/database/migrations, calendar, season rollover, or persistence;
- player, club, competition, FC26 data/assets/tooling, or other factual/runtime data;
- tests, Gradle/build logic, tooling, CI/security policy, or `AGENTS.md`;
- performance-sensitive runtime paths;
- mixed, unknown, ambiguous, or otherwise unrecognized paths.

The authoritative FULL Required Certification includes the applicable Core, Migration/Save Recovery, FC26, 20/100-season stress, UI golden/accessibility, full-scale rollover performance, and installed Android gates.

## 5. `main` and production release

- Every push/merge to `main` is force-classified as **FULL**, regardless of the originating PR classification.
- Production release/signing is a separate protected operation and must use an exact, fully certified `main` SHA.
- Production credentials must only be consumed by a release path that verifies the required certification/Guardian evidence for the exact release SHA before signing or publishing.
- Standalone heavy workflows may remain available for manual diagnostics, but they must not duplicate the automatic PR matrix when Required Certification already owns the same gate.

## 6. Completion standard

A phase is complete only when implementation, required validation, blocking review resolution, final audit, protected merge (when authorized), and post-merge verification required by the phase are complete. Do not report completion while a required gate is still pending or while the exact merged `main` has not received any mandatory post-merge certification.
