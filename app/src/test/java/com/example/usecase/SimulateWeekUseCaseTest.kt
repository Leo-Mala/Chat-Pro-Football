package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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

        val team1 = Team(id = 1L, name = "Time 1", city = "A", state = "SP", division = 1, rating = 70)
        val team2 = Team(id = 2L, name = "Time 2", city = "B", state = "SP", division = 1, rating = 65)
        val team3 = Team(id = 3L, name = "Time 3", city = "C", state = "SP", division = 1, rating = 80)
        val team4 = Team(id = 4L, name = "Time 4", city = "D", state = "SP", division = 1, rating = 60)
        repository.saveTeams(listOf(team1, team2, team3, team4))

        val played = useCase.simulateCpuMatchesForWeek(season = 2026, week = 1)

        assertEquals(2, played.size)
        assertTrue(played[0].isPlayed)
        assertNotNull(played[0].homeScore)
        assertNotNull(played[0].awayScore)
    }
}
