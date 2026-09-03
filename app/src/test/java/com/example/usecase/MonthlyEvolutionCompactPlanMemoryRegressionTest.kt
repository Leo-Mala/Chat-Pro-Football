package com.example.usecase

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
        assertTrue("stable compact commitment must validate and commit", useCase.commitMonthlyEvolution(plan))
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
