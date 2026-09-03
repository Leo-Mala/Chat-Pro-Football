from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_exact(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"expected source block not found in {path}: {old[:220]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


benchmark = ROOT / "app/src/test/java/com/example/usecase/MonthlyCommitPerformanceBenchmarkTest.kt"
replace_exact(
    benchmark,
    '''import com.example.data.getAllMonthlyEvolutionInputSnapshots\n''',
    '''import com.example.data.validateMonthlyEvolutionUniverseCommitment\n'''
)
replace_exact(
    benchmark,
    '''        startedAtNs = System.nanoTime()
        val currentInputs = repository.getAllMonthlyEvolutionInputSnapshots()
        val tSnapshotReadMillis = elapsedMillis(startedAtNs)

        startedAtNs = System.nanoTime()
        val expectedById = plan.expectedInputs.associateBy { it.id }
        assertEquals(plan.expectedPlayerCount, currentInputs.size)
        assertEquals(plan.expectedPlayerCount, expectedById.size)
        var inputMismatchCount = 0
        var teamMoveCount = 0
        for ((playerId, expected) in expectedById) {
            val current = currentInputs.getValue(playerId)
            if (!expected.sameEvolutionStateIgnoringTeam(current)) inputMismatchCount++
            if (expected.teamId != current.teamId) teamMoveCount++
        }
        val tSnapshotCompareMillis = elapsedMillis(startedAtNs)
        assertEquals(0, inputMismatchCount)
        assertEquals(0, teamMoveCount)
''',
    '''        val commitment = requireNotNull(plan.expectedUniverseCommitment) {
            "Production monthly plan must retain the compact universe commitment."
        }
        assertTrue(plan.expectedInputs.isEmpty())
        assertEquals(plan.expectedPlayerCount, commitment.size)

        startedAtNs = System.nanoTime()
        val validation = repository.validateMonthlyEvolutionUniverseCommitment(
            expected = commitment,
            expectedTrainingCenterLevels = plan.expectedTrainingCenterLevels,
            currentTrainingCenterLevels = teamsById.mapValues { it.value.trainingCenterLevel },
            allowRosterCorrections = true
        )
        val tCommitmentValidationMillis = elapsedMillis(startedAtNs)
        assertTrue(validation.valid)
        assertTrue(validation.correctionIds.isEmpty())
        assertEquals(plan.expectedPlayerCount, validation.currentPlayerCount)
'''
)
replace_exact(
    benchmark,
    '''                "T_SNAPSHOT_READ=$tSnapshotReadMillis " +
                "T_SNAPSHOT_COMPARE=$tSnapshotCompareMillis " +''',
    '''                "T_COMMITMENT_VALIDATE=$tCommitmentValidationMillis " +'''
)
replace_exact(
    benchmark,
    '''                "SNAPSHOT_ROWS_COUNT=${currentInputs.size} " +''',
    '''                "COMMITMENT_ROWS_COUNT=${commitment.size} " +'''
)

print("monthly commit benchmark v3 compact validation patch prepared")
