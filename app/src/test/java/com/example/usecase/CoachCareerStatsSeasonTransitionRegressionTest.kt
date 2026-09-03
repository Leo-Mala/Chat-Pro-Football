package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.GameCalendar
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class CoachCareerStatsSeasonTransitionRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var useCase: SeasonTransitionUseCase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        useCase = SeasonTransitionUseCase(
            repository,
            GenerateCalendarUseCase(repository),
            DatabaseIntegrityUseCase(repository)
        )
    }

    @After fun tearDown() = db.close()

    @Test fun `rollover accumulates only controlled club completed fixtures exactly once`() = runTest {
        repository.saveTeams(listOf(
            Team(id = 1L, name = "User", city = "U", state = "MG", division = 1, isPlayerControlled = true),
            Team(id = 2L, name = "CPU A", city = "A", state = "MG", division = 1),
            Team(id = 3L, name = "CPU B", city = "B", state = "MG", division = 1)
        ))
        val stale = GameSave(
            currentSeason = 2026,
            currentWeek = GameCalendar.WEEKS_PER_SEASON,
            playerTeamId = 1L,
            careerMatches = 10,
            careerWins = 4,
            careerDraws = 3,
            careerLosses = 3,
            careerGoalsScored = 12,
            careerGoalsConceded = 11
        )
        repository.saveGameSave(stale)
        repository.saveFixtures(listOf(
            Fixture(season = 2026, week = 1, homeTeamId = 1L, awayTeamId = 2L, homeScore = 2, awayScore = 0, competitionType = "SERIE_A", isPlayed = true),
            Fixture(season = 2026, week = 2, homeTeamId = 2L, awayTeamId = 1L, homeScore = 1, awayScore = 1, competitionType = "SERIE_A", isPlayed = true),
            Fixture(season = 2026, week = 3, homeTeamId = 1L, awayTeamId = 3L, homeScore = 0, awayScore = 3, competitionType = "CUP", isPlayed = true),
            Fixture(season = 2026, week = 4, homeTeamId = 2L, awayTeamId = 3L, homeScore = 5, awayScore = 4, competitionType = "SERIE_A", isPlayed = true),
            Fixture(season = 2026, week = 5, homeTeamId = 1L, awayTeamId = 2L, competitionType = "SERIE_A", isPlayed = false)
        ))

        val first = useCase.advanceToNextSeason(stale)
        assertEquals(13, first.careerMatches)
        assertEquals(5, first.careerWins)
        assertEquals(4, first.careerDraws)
        assertEquals(4, first.careerLosses)
        assertEquals(15, first.careerGoalsScored)
        assertEquals(15, first.careerGoalsConceded)

        val retry = useCase.advanceToNextSeason(stale)
        assertEquals(first.careerMatches, retry.careerMatches)
        assertEquals(first.careerWins, retry.careerWins)
        assertEquals(first.careerDraws, retry.careerDraws)
        assertEquals(first.careerLosses, retry.careerLosses)
        assertEquals(first.careerGoalsScored, retry.careerGoalsScored)
        assertEquals(first.careerGoalsConceded, retry.careerGoalsConceded)
    }
}
