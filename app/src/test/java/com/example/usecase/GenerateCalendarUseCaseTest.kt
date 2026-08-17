package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
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
class GenerateCalendarUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var useCase: GenerateCalendarUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = GameRepository(db)
        useCase = GenerateCalendarUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun generateRoundRobinFixtures_creates_double_round_robin_for_even_teams() {
        val teams = (1L..4L).map { id ->
            Team(id = id, name = "Time $id", city = "Sp", state = "SP", division = 1)
        }

        val fixtures = useCase.generateRoundRobinFixtures(season = 2026, teams = teams, competitionType = "SERIE_A", startWeek = 1)

        assertEquals(12, fixtures.size)
    }

    @Test
    fun generateRoundRobinFixtures_handles_odd_number_of_teams_gracefully() {
        val teams = (1L..5L).map { id ->
            Team(id = id, name = "Time $id", city = "Sp", state = "SP", division = 1)
        }

        val fixtures = useCase.generateRoundRobinFixtures(season = 2026, teams = teams, competitionType = "SERIE_A", startWeek = 1)

        assertEquals(20, fixtures.size)
        assertTrue(fixtures.none { it.homeTeamId == -1L || it.awayTeamId == -1L })
    }

    @Test
    fun saveCalendarFixtures_populates_fixtures_in_repository() = runTest {
        val teams = (1L..4L).map { id ->
            Team(id = id, name = "Time $id", city = "Sp", state = "SP", division = 1)
        }
        repository.saveTeams(teams)
        val fixtures = useCase.generateRoundRobinFixtures(season = 2026, teams = teams, competitionType = "SERIE_A", startWeek = 1)

        useCase.saveCalendarFixtures(fixtures)

        val fixturesInDb = repository.getAllFixtures()
        assertEquals(12, fixturesInDb.size)
    }
}
