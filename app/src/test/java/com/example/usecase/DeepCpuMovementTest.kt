package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Team
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeepCpuMovementTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun cpuBrazilMovesFourClubsAcrossFourthAndFifthDivisionBoundary() = runBlocking {
        val argentinaUserTeams = listOf(
            team(1, "ARG User", "Argentina", 1, 85),
            team(2, "ARG Rival", "Argentina", 1, 75)
        )
        val brazilFourth = (100L..107L).mapIndexed { index, id ->
            team(id, "BRA D ${index + 1}", "Brasil", 4, 75 - index)
        }
        val brazilFifth = (200L..207L).mapIndexed { index, id ->
            team(id, "BRA E ${index + 1}", "Brasil", 5, 60 - index)
        }
        val allTeams = argentinaUserTeams + brazilFourth + brazilFifth
        repository.saveTeams(allTeams)

        val save = GameSave(
            currentSeason = 2026,
            currentWeek = 40,
            playerTeamId = argentinaUserTeams.first().id
        )
        repository.saveGameSave(save)
        val userFixtures = listOf(
            fixture(1, argentinaUserTeams[0].id, argentinaUserTeams[1].id, 2, 0),
            fixture(2, argentinaUserTeams[1].id, argentinaUserTeams[0].id, 0, 1)
        )
        repository.saveFixtures(userFixtures)

        val expectedSnapshot = GlobalLeagueSimulationUseCase().buildSeasonStandings(
            season = 2026,
            teams = allTeams,
            detailedFixtures = userFixtures,
            detailedCountry = "Argentina"
        )
        val expectedRelegated = expectedSnapshot
            .filter { it.country == "Brasil" && it.division == 4 }
            .sortedBy { it.position }
            .takeLast(4)
            .map { it.teamId }
            .toSet()
        val expectedPromoted = expectedSnapshot
            .filter { it.country == "Brasil" && it.division == 5 }
            .sortedBy { it.position }
            .take(4)
            .map { it.teamId }
            .toSet()

        SeasonTransitionUseCase(
            repository = repository,
            generateCalendarUseCase = GenerateCalendarUseCase(repository),
            databaseIntegrityUseCase = DatabaseIntegrityUseCase(repository)
        ).advanceToNextSeason(save)

        val updated = repository.getAllTeams().associateBy { it.id }
        assertEquals(4, expectedRelegated.size)
        assertEquals(4, expectedPromoted.size)
        expectedRelegated.forEach { id -> assertEquals(5, updated.getValue(id).division) }
        expectedPromoted.forEach { id -> assertEquals(4, updated.getValue(id).division) }
        brazilFourth.filterNot { it.id in expectedRelegated }
            .forEach { assertEquals(4, updated.getValue(it.id).division) }
        brazilFifth.filterNot { it.id in expectedPromoted }
            .forEach { assertEquals(5, updated.getValue(it.id).division) }
    }

    private fun team(
        id: Long,
        name: String,
        country: String,
        division: Int,
        rating: Int
    ) = Team(
        id = id,
        name = name,
        city = name,
        state = "XX",
        country = country,
        division = division,
        rating = rating
    )

    private fun fixture(
        id: Long,
        homeId: Long,
        awayId: Long,
        homeScore: Int,
        awayScore: Int
    ) = Fixture(
        id = id,
        season = 2026,
        week = id.toInt(),
        homeTeamId = homeId,
        awayTeamId = awayId,
        homeScore = homeScore,
        awayScore = awayScore,
        competitionType = "SERIE_A",
        isPlayed = true
    )
}
