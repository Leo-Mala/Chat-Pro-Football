package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.MatchSlot
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
class SimulateWeekUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var useCase: SimulateWeekUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = GameRepository(db)
        useCase = SimulateWeekUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun calculateCpuMatchScore_generates_valid_score() {
        val (homeScore, awayScore) = useCase.calculateCpuMatchScore(homeRating = 70, awayRating = 65)
        assertTrue(homeScore >= 0)
        assertTrue(awayScore >= 0)
    }

    @Test
    fun simulateCpuMatchesForWeek_updates_unplayed_fixtures() = runTest {
        val f1 = Fixture(id = 1L, season = 2026, week = 1, homeTeamId = 1L, awayTeamId = 2L, competitionType = "SERIE_A", isPlayed = false)
        val f2 = Fixture(id = 2L, season = 2026, week = 1, homeTeamId = 3L, awayTeamId = 4L, competitionType = "SERIE_A", isPlayed = false)
        repository.saveFixtures(listOf(f1, f2))

        repository.saveTeams(defaultTeams())

        val played = useCase.simulateCpuMatchesForWeek(season = 2026, week = 1)

        assertEquals(2, played.size)
        assertTrue(played[0].isPlayed)
        assertNotNull(played[0].homeScore)
        assertNotNull(played[0].awayScore)
    }

    @Test
    fun simulateCpuMatchesForWeek_excludes_every_fixture_of_user_team() = runTest {
        val userTeamId = 1L
        repository.saveTeams(defaultTeams())
        repository.saveFixtures(
            listOf(
                Fixture(
                    id = 10L,
                    season = 2026,
                    week = 31,
                    homeTeamId = userTeamId,
                    awayTeamId = 2L,
                    competitionType = "SERIE_A"
                ),
                Fixture(
                    id = 11L,
                    season = 2026,
                    week = 31,
                    homeTeamId = 3L,
                    awayTeamId = userTeamId,
                    competitionType = "COPA",
                    matchSlot = MatchSlot.MIDWEEK
                ),
                Fixture(
                    id = 12L,
                    season = 2026,
                    week = 31,
                    homeTeamId = 3L,
                    awayTeamId = 4L,
                    competitionType = "SERIE_A"
                )
            )
        )

        val simulated = useCase.simulateCpuMatchesForWeek(
            season = 2026,
            week = 31,
            excludedTeamId = userTeamId
        )

        assertEquals(1, simulated.size)
        assertEquals(12L, simulated.single().id)

        val refreshed = repository.getFixturesForWeek(2026, 31)
        val userFixtures = refreshed.filter {
            it.homeTeamId == userTeamId || it.awayTeamId == userTeamId
        }
        assertEquals(2, userFixtures.size)
        assertTrue(userFixtures.all { !it.isPlayed })
        assertTrue(refreshed.single { it.id == 12L }.isPlayed)
    }

    @Test
    fun weekQuery_orders_midweek_user_fixture_before_weekend_one() = runTest {
        repository.saveFixtures(
            listOf(
                Fixture(
                    id = 20L,
                    season = 2026,
                    week = 31,
                    homeTeamId = 1L,
                    awayTeamId = 2L,
                    competitionType = "SERIE_A",
                    homeScore = 2,
                    awayScore = 1,
                    isPlayed = true
                ),
                Fixture(
                    id = 21L,
                    season = 2026,
                    week = 31,
                    homeTeamId = 1L,
                    awayTeamId = 3L,
                    competitionType = "COPA",
                    isPlayed = false,
                    matchSlot = MatchSlot.MIDWEEK
                )
            )
        )

        val fixtures = repository.getFixturesForWeek(2026, 31)

        assertEquals(21L, fixtures.first().id)
        assertEquals(MatchSlot.MIDWEEK, fixtures.first().matchSlot)
        assertFalse(fixtures.first().isPlayed)
        assertTrue(fixtures.last().isPlayed)
    }

    private fun defaultTeams(): List<Team> = listOf(
        Team(id = 1L, name = "Time 1", city = "A", state = "SP", country = "Brasil", division = 1, rating = 70),
        Team(id = 2L, name = "Time 2", city = "B", state = "SP", country = "Brasil", division = 1, rating = 65),
        Team(id = 3L, name = "Time 3", city = "C", state = "SP", country = "Brasil", division = 1, rating = 80),
        Team(id = 4L, name = "Time 4", city = "D", state = "SP", country = "Brasil", division = 1, rating = 60)
    )
}
