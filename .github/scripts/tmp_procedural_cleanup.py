from pathlib import Path


def read(path):
    return Path(path).read_text()


def write(path, text):
    Path(path).write_text(text)


def replace_once(path, old, new):
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, got {count}: {old[:140]!r}")
    write(path, text.replace(old, new, 1))


# MainApplication no longer owns any factual-player runtime.
path = "app/src/main/java/com/example/MainApplication.kt"
text = read(path).replace("import com.example.data.EuropeanFactualAssetRuntime\n", "")
write(path, text)

# Two FC26-named files actually contain audited CLUB identity coverage, not player records.
# Preserve those club identities under neutral names so deleting the FC26 player feature does not
# silently delete clubs/divisions or change deterministic team IDs.
renames = {
    "app/src/main/java/com/example/data/Fc26RemainingClubCoverage2026_27.kt": (
        "app/src/main/java/com/example/data/AuditedLowerTierClubCoverage2026_27.kt",
        "Fc26RemainingClubCoverage2026_27",
        "AuditedLowerTierClubCoverage2026_27",
    ),
    "app/src/main/java/com/example/data/Fc26RemainingFactualBaselinesA3_2026_27.kt": (
        "app/src/main/java/com/example/data/AuditedFactualBaselinesA3_2026_27.kt",
        "Fc26RemainingFactualBaselinesA3_2026_27",
        "AuditedFactualBaselinesA3_2026_27",
    ),
}
for old_path, (new_path, old_symbol, new_symbol) in renames.items():
    source = read(old_path).replace(old_symbol, new_symbol)
    source = source.replace("FC26", "audited club source").replace("fc26", "auditedClubSource")
    write(new_path, source)

# Replace symbol references throughout Android source/tests before old FC26-named files are deleted.
for root in [Path("app/src/main/java"), Path("app/src/test/java")]:
    for p in root.rglob("*.kt"):
        text = p.read_text()
        updated = text.replace("Fc26RemainingClubCoverage2026_27", "AuditedLowerTierClubCoverage2026_27")
        updated = updated.replace("Fc26RemainingFactualBaselinesA3_2026_27", "AuditedFactualBaselinesA3_2026_27")
        if updated != text:
            p.write_text(updated)

# No FC26-only free-agent quarantine exists in procedural careers.
path = "app/src/main/java/com/example/usecase/CpuSquadManagementUseCase.kt"
text = read(path)
text = text.replace("import com.example.data.Fc26LoanPolicy\n", "")
text = text.replace("import com.example.data.isFc26UnassignedSourceClub\n", "")
text = text.replace(".filter { !it.isOnLoan && !it.isFc26UnassignedSourceClub() }", ".filter { !it.isOnLoan }")
text = text.replace(
    "val invalidTemporalState = loan.remainingWeeks <= 0 && !Fc26LoanPolicy.isUnknownEndSnapshotLoan(loan)",
    "val invalidTemporalState = loan.remainingWeeks <= 0"
)
write(path, text)

# Remove special open-ended snapshot-loan branch. Procedural gameplay loans always have normal
# durations and follow the regular branch below.
path = "app/src/main/java/com/example/usecase/FinanceUseCase.kt"
text = read(path).replace("import com.example.data.Fc26LoanPolicy\n", "")
start = text.find("            if (Fc26LoanPolicy.isUnknownEndSnapshotLoan(loan)) {")
if start >= 0:
    end_marker = "            if (loan.borrowerTeamId == currentSave.playerTeamId) {"
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit("FinanceUseCase regular-loan marker not found")
    text = text[:start] + text[end:]
write(path, text)

# Loan lifecycle comments are now generic gameplay semantics.
path = "app/src/main/java/com/example/usecase/LoanLifecycleUseCase.kt"
text = read(path)
text = text.replace(
    " * Owns explicit player-loan return transitions for both gameplay loans and FC26 snapshot loans.\n *\n * FC26 loans may have no trustworthy end date. Those rows remain ACTIVE until an explicit career\n * event closes them; this class never invents a duration. The return is save-slot scoped by\n",
    " * Owns explicit player-loan return transitions for gameplay loans. The return is save-slot scoped by\n"
)
write(path, text)

# Remove obsolete FC26-only tests that cannot describe the procedural-only product anymore.
obsolete_tests = [
    "app/src/test/java/com/example/data/EuropeanNewSaveSeedCoordinatorTest.kt",
    "app/src/test/java/com/example/data/GlobalMainAuditPerformanceStressTest.kt",
    "app/src/test/java/com/example/data/Phase912WeeklyLifecyclePerformanceTest.kt",
    "app/src/test/java/com/example/usecase/Phase104LoanSafetyRegressionTest.kt",
    "app/src/test/java/com/example/usecase/Phase104SafeLoanLifecycleTest.kt",
]
for file in obsolete_tests:
    p = Path(file)
    if p.exists():
        p.unlink()

# Phase 10.8 rollover remains useful, but its FC26-only fixture is obsolete. Remove the test file
# rather than keeping a certification that validates a deleted dataset.
p = Path("app/src/test/java/com/example/data/Phase108FullScaleSeasonRolloverPerformanceTest.kt")
if p.exists():
    p.unlink()

# Text/comments still exposed to the current product should stop naming the deleted player source.
comment_files = [
    "app/src/main/java/com/example/data/CareerCreationPerformance.kt",
    "app/src/main/java/com/example/data/PreCareerEditorOverrides.kt",
    "app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt",
]
for file in comment_files:
    text = read(file)
    text = text.replace("Fc26SeedPlanner/asset load", "external player-seed materialization")
    text = text.replace("substituído pelo FC26", "substituído por uma fonte factual")
    text = text.replace("FC26", "fonte factual removida")
    text = text.replace("fc26", "factual")
    write(file, text)

# The eFootball reconciliation package was exclusively an FC26-player auxiliary pipeline.
# It is not used by runtime/gameplay and should not survive a procedural-only player model.
for root in [Path("tools/fc26"), Path("tools/efootball")]:
    if root.exists():
        for p in sorted(root.rglob("*"), reverse=True):
            if p.is_file():
                p.unlink()
        for p in sorted(root.rglob("*"), reverse=True):
            if p.is_dir():
                try:
                    p.rmdir()
                except OSError:
                    pass
        try:
            root.rmdir()
        except OSError:
            pass

# FC26/eFootball-only workflow is obsolete with those tools removed.
p = Path(".github/workflows/efootball-identity.yml")
if p.exists():
    p.unlink()
