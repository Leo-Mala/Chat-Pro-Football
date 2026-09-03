from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_exact(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"expected source block not found in {path}: {old[:240]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


write_text(
    ROOT / "app/src/main/java/com/example/data/MonthlyEvolutionUniverseCommitment.kt",
    r'''package com.example.data

import java.security.MessageDigest

/**
 * Compact stale-plan proof for the production monthly path.
 *
 * The previous compact path retained two sizeable attribute strings for every world player until
 * the weekly close committed. On a ~75k-player career this kept a large object graph alive across
 * the most memory-sensitive part of season simulation. This representation keeps only primitive
 * ids/team ids plus a SHA-256 digest of every football input that influences monthly evolution.
 */
internal data class MonthlyEvolutionUniverseCommitment(
    val playerIds: LongArray,
    val teamIds: LongArray,
    val teamPresent: BooleanArray,
    val digest0: LongArray,
    val digest1: LongArray,
    val digest2: LongArray,
    val digest3: LongArray
) {
    init {
        val size = playerIds.size
        require(teamIds.size == size)
        require(teamPresent.size == size)
        require(digest0.size == size)
        require(digest1.size == size)
        require(digest2.size == size)
        require(digest3.size == size)
        for (index in 1 until size) {
            require(playerIds[index] > playerIds[index - 1]) {
                "Monthly evolution commitment player ids must be unique and sorted."
            }
        }
    }

    val size: Int get() = playerIds.size

    fun teamIdAt(index: Int): Long? = if (teamPresent[index]) teamIds[index] else null
}

private data class MonthlyEvolutionRowCommitment(
    val id: Long,
    val teamId: Long?,
    val digest0: Long,
    val digest1: Long,
    val digest2: Long,
    val digest3: Long
)

/** Builds the compact proof while the normal 4,096-player evolution batches stream past. */
internal class MonthlyEvolutionCommitmentBuilder(expectedSize: Int) {
    private val rows = ArrayList<MonthlyEvolutionRowCommitment>(expectedSize.coerceAtLeast(0))
    private val digest = MessageDigest.getInstance("SHA-256")
    private val digestOutput = ByteArray(32)

    fun add(player: Player) {
        updateMonthlyEvolutionStateDigest(
            digest = digest,
            id = player.id,
            age = player.age,
            position = player.position,
            force = player.force,
            potential = player.potential,
            minutosJogados = player.minutosJogados,
            mediaNotas = player.mediaNotas,
            focoTreino = player.focoTreino,
            atributosJson = player.atributosJson,
            atributosStorage = requireNotNull(AtributosConverter.atributosToJson(player.atributos))
        )
        finishDigest(digest, digestOutput)
        rows.add(
            MonthlyEvolutionRowCommitment(
                id = player.id,
                teamId = player.teamId,
                digest0 = readLong(digestOutput, 0),
                digest1 = readLong(digestOutput, 8),
                digest2 = readLong(digestOutput, 16),
                digest3 = readLong(digestOutput, 24)
            )
        )
    }

    fun build(): MonthlyEvolutionUniverseCommitment {
        rows.sortBy { it.id }
        val size = rows.size
        val playerIds = LongArray(size)
        val teamIds = LongArray(size)
        val teamPresent = BooleanArray(size)
        val digest0 = LongArray(size)
        val digest1 = LongArray(size)
        val digest2 = LongArray(size)
        val digest3 = LongArray(size)

        for (index in rows.indices) {
            val row = rows[index]
            if (index > 0) {
                check(row.id > playerIds[index - 1]) {
                    "Duplicate player id ${row.id} in monthly evolution commitment."
                }
            }
            playerIds[index] = row.id
            if (row.teamId != null) {
                teamPresent[index] = true
                teamIds[index] = row.teamId
            }
            digest0[index] = row.digest0
            digest1[index] = row.digest1
            digest2[index] = row.digest2
            digest3[index] = row.digest3
        }

        return MonthlyEvolutionUniverseCommitment(
            playerIds = playerIds,
            teamIds = teamIds,
            teamPresent = teamPresent,
            digest0 = digest0,
            digest1 = digest1,
            digest2 = digest2,
            digest3 = digest3
        )
    }
}

/**
 * Validates every persisted player against a compact proof without reconstructing the old
 * 75k-entry snapshot list or a HashMap copy. New weekly rows remain targeted corrections; missing
 * rows or changed football inputs fail closed. Team moves preserve the existing training-center
 * rule: same effective level is safe, different level is recalculated under the transaction.
 */
internal fun GameRepository.validateMonthlyEvolutionUniverseCommitment(
    expected: MonthlyEvolutionUniverseCommitment,
    expectedTrainingCenterLevels: Map<Long, Int>,
    currentTrainingCenterLevels: Map<Long, Int>,
    allowRosterCorrections: Boolean
): MonthlyEvolutionRosterValidation {
    if (expected.size == 0) {
        val count = getMonthlyEvolutionPlayerCount()
        return MonthlyEvolutionRosterValidation(
            valid = count == 0,
            correctionIds = emptySet(),
            currentPlayerCount = count
        )
    }

    val corrections = linkedSetOf<Long>()
    val database = db.openHelper.writableDatabase
    val digest = MessageDigest.getInstance("SHA-256")
    val digestOutput = ByteArray(32)
    var expectedIndex = 0
    var lastSeenId: Long? = null
    var currentCount = 0
    var valid = true

    while (true) {
        val query = if (lastSeenId == null) {
            """
            SELECT id, teamId, age, position, force, potential, minutosJogados, mediaNotas,
                   focoTreino, atributosJson, atributos
            FROM players
            ORDER BY id ASC
            LIMIT 1024
            """.trimIndent()
        } else {
            """
            SELECT id, teamId, age, position, force, potential, minutosJogados, mediaNotas,
                   focoTreino, atributosJson, atributos
            FROM players
            WHERE id > ?
            ORDER BY id ASC
            LIMIT 1024
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
                val currentId = cursor.getLong(idIndex)
                val currentTeamId = if (cursor.isNull(teamIdIndex)) null else cursor.getLong(teamIdIndex)
                currentCount++
                rowsInBatch++
                batchLastId = currentId

                while (expectedIndex < expected.size && expected.playerIds[expectedIndex] < currentId) {
                    valid = false // expected row disappeared
                    expectedIndex++
                }

                if (expectedIndex >= expected.size || currentId < expected.playerIds[expectedIndex]) {
                    if (allowRosterCorrections) corrections.add(currentId) else valid = false
                    continue
                }

                updateMonthlyEvolutionStateDigest(
                    digest = digest,
                    id = currentId,
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
                finishDigest(digest, digestOutput)
                if (expected.digest0[expectedIndex] != readLong(digestOutput, 0) ||
                    expected.digest1[expectedIndex] != readLong(digestOutput, 8) ||
                    expected.digest2[expectedIndex] != readLong(digestOutput, 16) ||
                    expected.digest3[expectedIndex] != readLong(digestOutput, 24)
                ) {
                    valid = false
                }

                val expectedTeamId = expected.teamIdAt(expectedIndex)
                if (expectedTeamId != currentTeamId) {
                    if (!allowRosterCorrections) {
                        valid = false
                    } else {
                        val oldLevel = expectedTeamId?.let { expectedTrainingCenterLevels[it] } ?: 1
                        val newLevel = currentTeamId?.let { currentTrainingCenterLevels[it] } ?: 1
                        if (oldLevel != newLevel) corrections.add(currentId)
                    }
                }
                expectedIndex++
            }
        }

        if (rowsInBatch == 0) break
        check(lastSeenId == null || requireNotNull(batchLastId) > lastSeenId!!) {
            "Monthly evolution compact validation keyset did not advance after player id $lastSeenId."
        }
        lastSeenId = batchLastId
        if (rowsInBatch < 1024) break
    }

    if (expectedIndex != expected.size) valid = false
    return MonthlyEvolutionRosterValidation(valid, corrections, currentCount)
}

private fun updateMonthlyEvolutionStateDigest(
    digest: MessageDigest,
    id: Long,
    age: Int,
    position: String,
    force: Int,
    potential: Int,
    minutosJogados: Int,
    mediaNotas: Double,
    focoTreino: String?,
    atributosJson: String?,
    atributosStorage: String
) {
    digest.reset()
    digest.updateLongValue(id)
    digest.updateIntValue(age)
    digest.updateStringValue(position)
    digest.updateIntValue(force)
    digest.updateIntValue(potential)
    digest.updateIntValue(minutosJogados)
    digest.updateLongValue(java.lang.Double.doubleToLongBits(mediaNotas))
    digest.updateNullableStringValue(focoTreino)
    digest.updateNullableStringValue(atributosJson)
    digest.updateStringValue(atributosStorage)
}

private fun finishDigest(digest: MessageDigest, output: ByteArray) {
    check(digest.digest(output, 0, output.size) == output.size) {
        "SHA-256 monthly evolution digest length mismatch."
    }
}

private fun MessageDigest.updateIntValue(value: Int) {
    update((value ushr 24).toByte())
    update((value ushr 16).toByte())
    update((value ushr 8).toByte())
    update(value.toByte())
}

private fun MessageDigest.updateLongValue(value: Long) {
    update((value ushr 56).toByte())
    update((value ushr 48).toByte())
    update((value ushr 40).toByte())
    update((value ushr 32).toByte())
    update((value ushr 24).toByte())
    update((value ushr 16).toByte())
    update((value ushr 8).toByte())
    update(value.toByte())
}

private fun MessageDigest.updateNullableStringValue(value: String?) {
    if (value == null) {
        update(0.toByte())
    } else {
        update(1.toByte())
        updateStringValue(value)
    }
}

private fun MessageDigest.updateStringValue(value: String) {
    updateIntValue(value.length)
    for (char in value) {
        val code = char.code
        update((code ushr 8).toByte())
        update(code.toByte())
    }
}

private fun readLong(bytes: ByteArray, offset: Int): Long {
    var value = 0L
    for (index in 0 until 8) {
        value = (value shl 8) or (bytes[offset + index].toLong() and 0xffL)
    }
    return value
}
'''
)

player_evolution = ROOT / "app/src/main/java/com/example/usecase/PlayerEvolutionUseCase.kt"
replace_exact(
    player_evolution,
    "import com.example.data.MonthlyEvolutionInputSnapshot\nimport com.example.data.MonthlyEvolutionPlayerState\n",
    "import com.example.data.MonthlyEvolutionCommitmentBuilder\nimport com.example.data.MonthlyEvolutionInputSnapshot\nimport com.example.data.MonthlyEvolutionPlayerState\nimport com.example.data.MonthlyEvolutionUniverseCommitment\n"
)
replace_exact(
    player_evolution,
    "import com.example.data.validateMonthlyEvolutionRosterInputs\n",
    "import com.example.data.validateMonthlyEvolutionRosterInputs\nimport com.example.data.validateMonthlyEvolutionUniverseCommitment\n"
)
replace_exact(
    player_evolution,
    '''    /** Lightweight snapshots of every evolution input, used only for stale-plan validation. */
    val expectedInputs: List<MonthlyEvolutionInputSnapshot> = emptyList(),
    /** Exact universe size at preparation; detects players inserted after a standalone plan. */
''',
    '''    /** Detailed/legacy stale-plan snapshots. Compact production plans leave this empty. */
    val expectedInputs: List<MonthlyEvolutionInputSnapshot> = emptyList(),
    /** Primitive-array SHA-256 proof retained by the compact production path. */
    val expectedUniverseCommitment: MonthlyEvolutionUniverseCommitment? = null,
    /** Exact universe size at preparation; detects players inserted after a standalone plan. */
'''
)
replace_exact(
    player_evolution,
    '''        val changedPlayers = if (retainDetailedResults) ArrayList<Player>() else null
        val changedPlayerStates = if (retainDetailedResults) null else ArrayList<MonthlyEvolutionPlayerState>()
        val historyLogs = ArrayList<HistoricoEvolucao>()
        val expectedInputs = ArrayList<MonthlyEvolutionInputSnapshot>(expectedPlayerCount)
        val referencedTeamIds = HashSet<Long>()
''',
    '''        val changedPlayers = if (retainDetailedResults) ArrayList<Player>() else null
        val changedPlayerStates = if (retainDetailedResults) null else ArrayList<MonthlyEvolutionPlayerState>()
        val historyLogs = ArrayList<HistoricoEvolucao>()
        val expectedInputs = if (retainDetailedResults) {
            ArrayList<MonthlyEvolutionInputSnapshot>(expectedPlayerCount)
        } else null
        val commitmentBuilder = if (retainDetailedResults) null else {
            MonthlyEvolutionCommitmentBuilder(expectedPlayerCount)
        }
        val referencedTeamIds = HashSet<Long>()
'''
)
replace_exact(
    player_evolution,
    '''            for (player in batch) {
                expectedInputs.add(player.toMonthlyEvolutionInputSnapshot())
                player.teamId?.let(referencedTeamIds::add)
            }
''',
    '''            for (player in batch) {
                if (detailed) {
                    expectedInputs!!.add(player.toMonthlyEvolutionInputSnapshot())
                } else {
                    commitmentBuilder!!.add(player)
                }
                player.teamId?.let(referencedTeamIds::add)
            }
'''
)
replace_exact(
    player_evolution,
    '''        check(expectedInputs.size == expectedPlayerCount) {
            "Monthly evolution expected $expectedPlayerCount inputs but captured ${expectedInputs.size}."
        }

        val expectedTrainingLevels = referencedTeamIds.associateWith { teamId ->
''',
    '''        val expectedUniverseCommitment = commitmentBuilder?.build()
        if (retainDetailedResults) {
            check(expectedInputs!!.size == expectedPlayerCount) {
                "Monthly evolution expected $expectedPlayerCount detailed inputs but captured ${expectedInputs.size}."
            }
        } else {
            check(expectedUniverseCommitment?.size == expectedPlayerCount) {
                "Monthly evolution compact commitment size mismatch."
            }
        }

        val expectedTrainingLevels = referencedTeamIds.associateWith { teamId ->
'''
)
replace_exact(
    player_evolution,
    '''            historyLogs = historyLogs,
            expectedInputs = expectedInputs,
            expectedPlayerCount = expectedPlayerCount,
''',
    '''            historyLogs = historyLogs,
            expectedInputs = expectedInputs ?: emptyList(),
            expectedUniverseCommitment = expectedUniverseCommitment,
            expectedPlayerCount = expectedPlayerCount,
'''
)
replace_exact(
    player_evolution,
    '''        var correctionIds: Set<Long> = emptySet()
        if (plan.expectedInputs.isNotEmpty()) {
            if (!allowWeeklyRosterCorrections) {
                if (plan.expectedPlayerCount > 0 &&
                    repository.getMonthlyEvolutionPlayerCount() != plan.expectedPlayerCount
                ) {
                    return@withTransaction false
                }
                val currentInputs = repository.getMonthlyEvolutionInputSnapshots(plan.expectedInputs.map { it.id })
                if (currentInputs.size != plan.expectedInputs.size ||
                    plan.expectedInputs.any { expected -> currentInputs[expected.id] != expected }
                ) {
                    return@withTransaction false
                }
            } else {
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
        }
''',
    '''        var correctionIds: Set<Long> = emptySet()
        val compactCommitment = plan.expectedUniverseCommitment
        if (compactCommitment != null) {
            val teams = currentTeamsById ?: repository.getAllTeams().associateBy { it.id }.also {
                currentTeamsById = it
            }
            val validation = repository.validateMonthlyEvolutionUniverseCommitment(
                expected = compactCommitment,
                expectedTrainingCenterLevels = plan.expectedTrainingCenterLevels,
                currentTrainingCenterLevels = teams.mapValues { it.value.trainingCenterLevel },
                allowRosterCorrections = allowWeeklyRosterCorrections
            )
            if (!validation.valid) return@withTransaction false
            correctionIds = validation.correctionIds
        } else if (plan.expectedInputs.isNotEmpty()) {
            if (!allowWeeklyRosterCorrections) {
                if (plan.expectedPlayerCount > 0 &&
                    repository.getMonthlyEvolutionPlayerCount() != plan.expectedPlayerCount
                ) {
                    return@withTransaction false
                }
                val currentInputs = repository.getMonthlyEvolutionInputSnapshots(plan.expectedInputs.map { it.id })
                if (currentInputs.size != plan.expectedInputs.size ||
                    plan.expectedInputs.any { expected -> currentInputs[expected.id] != expected }
                ) {
                    return@withTransaction false
                }
            } else {
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
        }
'''
)

write_text(
    ROOT / "app/src/test/java/com/example/usecase/MonthlyEvolutionCompactPlanMemoryRegressionTest.kt",
    r'''package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class MonthlyEvolutionCompactPlanMemoryRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var useCase: PlayerEvolutionUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        useCase = PlayerEvolutionUseCase(repository)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `season monthly plan retains primitive commitment instead of full world snapshots`() = runTest {
        val playerCount = 256
        val team = Team(
            id = 1L,
            name = "Heap FC",
            city = "BH",
            state = "MG",
            country = "Brasil",
            division = 1,
            rating = 75,
            trainingCenterLevel = 3
        )
        repository.saveTeams(listOf(team))
        val save = GameSave(currentSeason = 2026, currentWeek = 12, playerTeamId = team.id)
        repository.saveGameSave(save)
        repository.savePlayers(
            List(playerCount) { index ->
                Player(
                    id = index.toLong() + 1L,
                    teamId = team.id,
                    name = "Heap %04d".format(index),
                    age = 21,
                    position = if (index % 11 == 0) "GOL" else "ATA",
                    force = if (index == 0) 99 else 65,
                    potential = 99,
                    minutosJogados = 360,
                    mediaNotas = 8.0,
                    salary = 12_345L + index,
                    contractDurationWeeks = 77
                )
            }
        )

        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W12")
        val commitment = plan.expectedUniverseCommitment

        assertEquals(playerCount, plan.expectedPlayerCount)
        assertTrue("compact weekly plan must not retain full per-player input snapshots", plan.expectedInputs.isEmpty())
        assertNotNull(commitment)
        requireNotNull(commitment)
        assertEquals(playerCount, commitment.size)
        assertEquals(playerCount, commitment.playerIds.size)
        assertEquals(playerCount, commitment.teamIds.size)
        assertEquals(playerCount, commitment.digest0.size)
        assertTrue(commitment.playerIds.asList().zipWithNext().all { (a, b) -> a < b })
        assertTrue("compact weekly plan must not retain full PlayerEvolutionResult objects", plan.results.isEmpty())
        assertTrue("compact weekly plan must not retain full changed Player entities", plan.updatedPlayers.isEmpty())
        assertTrue("changed players still need compact persistence state", plan.updatedPlayerStates.isNotEmpty())
        assertEquals(plan.updatedPlayerStates.size, plan.updatedPlayerStates.map { it.id }.distinct().size)

        val sentinelBefore = requireNotNull(repository.getPlayer(1L))
        assertTrue(useCase.commitMonthlyEvolution(plan))
        val sentinelAfter = requireNotNull(repository.getPlayer(1L))

        assertEquals(99, sentinelAfter.force)
        assertEquals(0, sentinelAfter.minutosJogados)
        assertEquals(sentinelBefore.salary, sentinelAfter.salary)
        assertEquals(sentinelBefore.contractDurationWeeks, sentinelAfter.contractDurationWeeks)
        assertFalse(repository.getHistoricoPorJogador(1L).isEmpty())
    }

    @Test
    fun `detailed standalone path retains legacy result contract`() = runTest {
        val team = Team(id = 2L, name = "Detailed FC", city = "SP", state = "SP", division = 1, rating = 75)
        repository.saveTeams(listOf(team))
        val save = GameSave(currentSeason = 2026, currentWeek = 4, playerTeamId = team.id)
        repository.saveGameSave(save)
        repository.savePlayers(
            List(12) { index ->
                Player(
                    id = 1_000L + index,
                    teamId = team.id,
                    name = "Detailed $index",
                    age = 22,
                    position = "MEI",
                    force = 65,
                    potential = 95,
                    minutosJogados = 180,
                    mediaNotas = 7.8
                )
            }
        )

        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W4", retainDetailedResults = true)

        assertEquals(12, plan.results.size)
        assertEquals(12, plan.expectedInputs.size)
        assertTrue(plan.expectedUniverseCommitment == null)
        assertTrue(plan.updatedPlayers.isNotEmpty())
        assertEquals(
            plan.updatedPlayers.map { it.id },
            plan.updatedPlayerStates.map { it.id }
        )
    }
}
'''
)

write_text(
    ROOT / "app/src/test/java/com/example/usecase/MonthlyEvolutionCompactCommitmentRegressionTest.kt",
    r'''package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class MonthlyEvolutionCompactCommitmentRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var useCase: PlayerEvolutionUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        useCase = PlayerEvolutionUseCase(repository)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `compact commitment rejects changed football input before any monthly write`() = runTest {
        val team = Team(id = 41L, name = "Digest FC", city = "BH", state = "MG", division = 1, trainingCenterLevel = 2)
        repository.saveTeams(listOf(team))
        val save = GameSave(currentSeason = 2026, currentWeek = 8, playerTeamId = team.id)
        repository.saveGameSave(save)
        repository.savePlayers(listOf(
            Player(id = 401L, teamId = team.id, name = "Digest One", age = 22, position = "MEI", force = 70,
                potential = 90, minutosJogados = 180, mediaNotas = 7.5)
        ))

        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W8")
        val current = requireNotNull(repository.getPlayer(401L))
        repository.updatePlayers(listOf(current.copy(force = current.force + 1)))

        assertFalse(useCase.commitMonthlyEvolution(plan))
        val after = requireNotNull(repository.getPlayer(401L))
        assertEquals(180, after.minutosJogados)
        assertEquals(current.force + 1, after.force)
        assertTrue(repository.getHistoricoPorJogador(401L).isEmpty())
    }

    @Test
    fun `weekly compact commitment accepts new player as targeted correction`() = runTest {
        val team = Team(id = 51L, name = "Correction FC", city = "SP", state = "SP", division = 1, trainingCenterLevel = 2)
        repository.saveTeams(listOf(team))
        val save = GameSave(currentSeason = 2026, currentWeek = 12, playerTeamId = team.id)
        repository.saveGameSave(save)
        repository.savePlayers(listOf(
            Player(id = 501L, teamId = team.id, name = "Existing", age = 23, position = "ATA", force = 68,
                potential = 88, minutosJogados = 120, mediaNotas = 7.2)
        ))

        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W12")
        repository.savePlayers(listOf(
            Player(id = 502L, teamId = team.id, name = "Emergency", age = 18, position = "ATA", force = 55,
                potential = 85, minutosJogados = 0, mediaNotas = 0.0)
        ))

        assertTrue(useCase.commitMonthlyEvolution(plan, allowWeeklyRosterCorrections = true))
        assertEquals(0, requireNotNull(repository.getPlayer(501L)).minutosJogados)
        assertTrue(repository.getPlayer(502L) != null)
    }

    @Test
    fun `weekly same training level team move remains safe and preserves new team`() = runTest {
        val oldTeam = Team(id = 61L, name = "Old FC", city = "RJ", state = "RJ", division = 1, trainingCenterLevel = 3)
        val newTeam = Team(id = 62L, name = "New FC", city = "RJ", state = "RJ", division = 1, trainingCenterLevel = 3)
        repository.saveTeams(listOf(oldTeam, newTeam))
        val save = GameSave(currentSeason = 2026, currentWeek = 16, playerTeamId = oldTeam.id)
        repository.saveGameSave(save)
        repository.savePlayers(listOf(
            Player(id = 601L, teamId = oldTeam.id, name = "Moved", age = 24, position = "ZAG", force = 72,
                potential = 82, minutosJogados = 240, mediaNotas = 7.0)
        ))

        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W16")
        val moved = requireNotNull(repository.getPlayer(601L)).copy(teamId = newTeam.id)
        repository.updatePlayers(listOf(moved))

        assertTrue(useCase.commitMonthlyEvolution(plan, allowWeeklyRosterCorrections = true))
        assertEquals(newTeam.id, requireNotNull(repository.getPlayer(601L)).teamId)
    }
}
'''
)

print("monthly compact commitment patch prepared")
