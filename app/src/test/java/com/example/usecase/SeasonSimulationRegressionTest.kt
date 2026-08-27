package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class SeasonSimulationRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var simulateWeek: SimulateWeekUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        simulateWeek = SimulateWeekUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `reprocessing same week does not resimulate fixtures or duplicate CPU scorer goals`() = runTest {
        repository.saveTeams(
            listOf(
                Team(id = 1L, name = "A", city = "A", state = "AA", division = 1, rating = 72),
                Team(id = 2L, name = "B", city = "B", state = "BB", division = 1, rating = 68),
                Team(id = 3L, name = "C", city = "C", state = "CC", division = 1, rating = 74),
                Team(id = 4L, name = "D", city = "D", state = "DD", division = 1, rating = 66)
            )
        )
        repository.savePlayers(
            (1L..4L).flatMap { teamId ->
                listOf(
                    Player(
                        id = teamId * 100 + 1,
                        teamId = teamId,
                        name = "ATA $teamId",
                        age = 25,
                        position = "ATA",
                        force = 75
                    ),
                    Player(
                        id = teamId * 100 + 2,
                        teamId = teamId,
                        name = "MEI $teamId",
                        age = 25,
                        position = "MEI",
                        force = 70
                    )
                )
            }
        )
        repository.saveFixtures(
            listOf(
                Fixture(id = 101L, season = 2026, week = 4, homeTeamId = 1L, awayTeamId = 2L, competitionType = "SERIE_A"),
                Fixture(id = 102L, season = 2026, week = 4, homeTeamId = 3L, awayTeamId = 4L, competitionType = "SERIE_A")
            )
        )

        val initialPlayers = repository.getAllPlayers().associateBy { it.id }
        val firstPass = simulateWeek.simulateCpuMatchesForWeek(2026, 4)
        assertEquals(2, firstPass.size)
        val afterFirst = repository.getFixturesForWeek(2026, 4).associateBy { it.id }
        assertTrue(afterFirst.values.all { it.isPlayed })

        val playersAfterFirst = repository.getAllPlayers()
        val expectedGoals = afterFirst.values.sumOf { (it.homeScore ?: 0) + (it.awayScore ?: 0) }
        val seasonGoalDelta = playersAfterFirst.sumOf { player ->
            player.gols - (initialPlayers[player.id]?.gols ?: 0)
        }
        val careerGoalDelta = playersAfterFirst.sumOf { player ->
            player.careerGoals - (initialPlayers[player.id]?.careerGoals ?: 0)
        }
        assertEquals(expectedGoals, seasonGoalDelta)
        assertEquals(expectedGoals, careerGoalDelta)

        val goalsBeforeSecondPass = playersAfterFirst.associate { it.id to Pair(it.gols, it.careerGoals) }
        val secondPass = simulateWeek.simulateCpuMatchesForWeek(2026, 4)
        assertTrue(secondPass.isEmpty())
        val afterSecond = repository.getFixturesForWeek(2026, 4).associateBy { it.id }
        val playersAfterSecond = repository.getAllPlayers()

        assertEquals(afterFirst[101L]?.homeScore, afterSecond[101L]?.homeScore)
        assertEquals(afterFirst[101L]?.awayScore, afterSecond[101L]?.awayScore)
        assertEquals(afterFirst[102L]?.homeScore, afterSecond[102L]?.homeScore)
        assertEquals(afterFirst[102L]?.awayScore, afterSecond[102L]?.awayScore)
        playersAfterSecond.forEach { player ->
            assertEquals(goalsBeforeSecondPass[player.id], Pair(player.gols, player.careerGoals))
        }
    }
}
