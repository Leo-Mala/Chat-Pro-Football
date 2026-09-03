from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_exact(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"expected source block not found in {path}: {old[:180]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


maintenance = ROOT / "app/src/main/java/com/example/data/MonthlyEvolutionMaintenanceQueries.kt"
marker = '''/**
 * Applies only the four columns owned by monthly evolution. This prevents a prepared plan from
'''
validation = '''data class MonthlyEvolutionRosterValidation(
    val valid: Boolean,
    val correctionIds: Set<Long>,
    val currentPlayerCount: Int
)

/**
 * Full-universe weekly-close validation without materializing a second current-input HashMap.
 * Every persisted evolution input is still checked. New rows are targeted for recalculation;
 * removed or mutated expected rows invalidate the plan fail-closed.
 */
internal fun GameRepository.validateMonthlyEvolutionRosterInputs(
    expectedInputs: List<MonthlyEvolutionInputSnapshot>,
    expectedTrainingCenterLevels: Map<Long, Int>,
    currentTrainingCenterLevels: Map<Long, Int>
): MonthlyEvolutionRosterValidation {
    if (expectedInputs.isEmpty()) {
        val count = getMonthlyEvolutionPlayerCount()
        return MonthlyEvolutionRosterValidation(count == 0, emptySet(), count)
    }

    val expectedById = HashMap<Long, MonthlyEvolutionInputSnapshot>(hashMapCapacityForSize(expectedInputs.size))
    for (expected in expectedInputs) expectedById[expected.id] = expected

    val corrections = linkedSetOf<Long>()
    val database = db.openHelper.writableDatabase
    var lastSeenId: Long? = null
    var currentCount = 0
    var matchedExpectedCount = 0
    var valid = true

    while (true) {
        val query = if (lastSeenId == null) {
            """
            SELECT id, teamId, age, position, force, potential, minutosJogados, mediaNotas,
                   focoTreino, atributosJson, atributos
            FROM players
            ORDER BY id ASC
            LIMIT $MONTHLY_VALIDATION_SCAN_BATCH_SIZE
            """.trimIndent()
        } else {
            """
            SELECT id, teamId, age, position, force, potential, minutosJogados, mediaNotas,
                   focoTreino, atributosJson, atributos
            FROM players
            WHERE id > ?
            ORDER BY id ASC
            LIMIT $MONTHLY_VALIDATION_SCAN_BATCH_SIZE
            """.trimIndent()
        }
        val args = lastSeenId?.let { arrayOf<Any>(it) } ?: emptyArray()
        var rowsInBatch = 0
        var batchLastId: Long? = null

        database.query(query, args).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val teamIdIndex = cursor.getColumnIndexOrThrow("teamId")
            val ageIndex = cursor.getColumnIndexOrThrow("age")
            val positionIndex = cursor.getColumnIndexOrThrow("position")
            val forceIndex = cursor.getColumnIndexOrThrow("force")
            val potentialIndex = cursor.getColumnIndexOrThrow("potential")
            val minutesIndex = cursor.getColumnIndexOrThrow("minutosJogados")
            val ratingIndex = cursor.getColumnIndexOrThrow("mediaNotas")
            val focusIndex = cursor.getColumnIndexOrThrow("focoTreino")
            val jsonIndex = cursor.getColumnIndexOrThrow("atributosJson")
            val attributesIndex = cursor.getColumnIndexOrThrow("atributos")

            while (cursor.moveToNext()) {
                val current = MonthlyEvolutionInputSnapshot(
                    id = cursor.getLong(idIndex),
                    teamId = if (cursor.isNull(teamIdIndex)) null else cursor.getLong(teamIdIndex),
                    age = cursor.getInt(ageIndex),
                    position = cursor.getString(positionIndex),
                    force = cursor.getInt(forceIndex),
                    potential = cursor.getInt(potentialIndex),
                    minutosJogados = cursor.getInt(minutesIndex),
                    mediaNotas = cursor.getDouble(ratingIndex),
                    focoTreino = if (cursor.isNull(focusIndex)) null else cursor.getString(focusIndex),
                    atributosJson = if (cursor.isNull(jsonIndex)) null else cursor.getString(jsonIndex),
                    atributosStorage = cursor.getString(attributesIndex)
                )
                currentCount++
                rowsInBatch++
                batchLastId = current.id

                val expected = expectedById[current.id]
                if (expected == null) {
                    corrections.add(current.id)
                    continue
                }
                matchedExpectedCount++
                if (!expected.sameEvolutionStateIgnoringTeam(current)) {
                    valid = false
                    continue
                }
                if (expected.teamId != current.teamId) {
                    val oldLevel = expected.teamId?.let { expectedTrainingCenterLevels[it] } ?: 1
                    val newLevel = current.teamId?.let { currentTrainingCenterLevels[it] } ?: 1
                    if (oldLevel != newLevel) corrections.add(current.id)
                }
            }
        }

        if (rowsInBatch == 0) break
        check(lastSeenId == null || requireNotNull(batchLastId) > lastSeenId!!) {
            "Monthly evolution validation keyset did not advance after player id $lastSeenId."
        }
        lastSeenId = batchLastId
        if (rowsInBatch < MONTHLY_VALIDATION_SCAN_BATCH_SIZE) break
    }

    if (matchedExpectedCount != expectedById.size) valid = false
    return MonthlyEvolutionRosterValidation(valid, corrections, currentCount)
}

'''
replace_exact(maintenance, marker, validation + marker)

player_evo = ROOT / "app/src/main/java/com/example/usecase/PlayerEvolutionUseCase.kt"
replace_exact(player_evo, "import com.example.data.getAllMonthlyEvolutionInputSnapshots\n", "")
replace_exact(
    player_evo,
    "import com.example.data.toMonthlyEvolutionPlayerState\n",
    "import com.example.data.toMonthlyEvolutionPlayerState\nimport com.example.data.validateMonthlyEvolutionRosterInputs\n"
)
replace_exact(
    player_evo,
    '''            } else {
                val currentInputs = repository.getAllMonthlyEvolutionInputSnapshots()
                val expectedById = plan.expectedInputs.associateBy { it.id }

                if (expectedById.keys.any { it !in currentInputs }) return@withTransaction false

                val teams = currentTeamsById ?: repository.getAllTeams().associateBy { it.id }.also {
                    currentTeamsById = it
                }
                val corrections = linkedSetOf<Long>()

                for ((playerId, expected) in expectedById) {
                    val current = currentInputs.getValue(playerId)
                    if (!expected.sameEvolutionStateIgnoringTeam(current)) {
                        return@withTransaction false
                    }
                    if (expected.teamId != current.teamId) {
                        val oldLevel = expected.teamId?.let { plan.expectedTrainingCenterLevels[it] } ?: 1
                        val newLevel = current.teamId?.let { teams[it]?.trainingCenterLevel } ?: 1
                        if (oldLevel != newLevel) corrections.add(playerId)
                    }
                }

                for (playerId in currentInputs.keys) {
                    if (playerId !in expectedById) corrections.add(playerId)
                }
                correctionIds = corrections
            }
''',
    '''            } else {
                val teams = currentTeamsById ?: repository.getAllTeams().associateBy { it.id }.also {
                    currentTeamsById = it
                }
                val validation = repository.validateMonthlyEvolutionRosterInputs(
                    expectedInputs = plan.expectedInputs,
                    expectedTrainingCenterLevels = plan.expectedTrainingCenterLevels,
                    currentTrainingCenterLevels = teams.mapValues { it.value.trainingCenterLevel }
                )
                if (!validation.valid) return@withTransaction false
                correctionIds = validation.correctionIds
            }
'''
)

write_text(ROOT / "app/src/test/java/com/example/data/MonthlyEvolutionStreamingValidationRegressionTest.kt", '''package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class MonthlyEvolutionStreamingValidationRegressionTest {
    @Test fun `streaming validation matches stable world and rejects mutated input`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = GameRepository(db)
            repository.saveTeams(listOf(
                Team(id = 1L, name = "A", city = "A", state = "AA", division = 1, trainingCenterLevel = 1),
                Team(id = 2L, name = "B", city = "B", state = "BB", division = 1, trainingCenterLevel = 1)
            ))
            repository.savePlayers((1L..2500L).map { id ->
                Player(id = id, teamId = if (id % 2L == 0L) 1L else 2L, name = "P$id",
                    age = 20 + (id % 10).toInt(), position = "MEI", force = 60 + (id % 30).toInt())
            })
            val expected = repository.getAllMonthlyEvolutionInputSnapshots().values.toList()
            val levels = mapOf(1L to 1, 2L to 1)
            val stable = repository.validateMonthlyEvolutionRosterInputs(expected, levels, levels)
            assertTrue(stable.valid)
            assertTrue(stable.correctionIds.isEmpty())
            assertEquals(2500, stable.currentPlayerCount)

            repository.updatePlayers(listOf(repository.getPlayer(100L)!!.copy(force = 99)))
            assertFalse(repository.validateMonthlyEvolutionRosterInputs(expected, levels, levels).valid)
        } finally { db.close() }
    }

    @Test fun `new weekly player is a targeted correction`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = GameRepository(db)
            repository.savePlayers(listOf(Player(id = 1L, teamId = null, name = "P1", age = 22, position = "MEI", force = 70)))
            val expected = repository.getAllMonthlyEvolutionInputSnapshots().values.toList()
            repository.savePlayers(listOf(Player(id = 2L, teamId = null, name = "P2", age = 18, position = "ATA", force = 55)))
            val validation = repository.validateMonthlyEvolutionRosterInputs(expected, emptyMap(), emptyMap())
            assertTrue(validation.valid)
            assertEquals(setOf(2L), validation.correctionIds)
        } finally { db.close() }
    }
}
''')

print("monthly streaming validation prepared")
