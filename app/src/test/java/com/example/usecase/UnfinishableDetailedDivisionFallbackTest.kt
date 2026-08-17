package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameCalendar
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.GlobalLeagueStanding
import com.example.data.LeagueSeasonFormat
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
class UnfinishableDetailedDivisionFallbackTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var calendarUseCase: GenerateCalendarUseCase
    private lateinit var transitionUseCase: SeasonTransitionUseCase
    private val globalSimulation = GlobalLeagueSimulationUseCase()

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        calendarUseCase = GenerateCalendarUseCase(repository)
        transitionUseCase = SeasonTransitionUseCase(
            repository = repository,
            generateCalendarUseCase = calendarUseCase,
            databaseIntegrityUseCase = DatabaseIntegrityUseCase(repository),
            globalLeagueSimulationUseCase = globalSimulation
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun unfinishableDetailedDivisionUsesSnapshotWhileFinishableNeighborUsesRealResults() = runTest {
        val user = team(1L, "Brasil User", division = 1, rating = 95)
        val giantDivision = (100L..140L).mapIndexed { index, id ->
            team(id, "D4 ${index + 1}", division = 4, rating = 90 - (index % 25))
        }
        val lowerDivision = (200L..203L).mapIndexed { index, id ->
            team(id, "D5 ${index + 1}", division = 5, rating = 70 - index)
        }
        val allTeams = listOf(user) + giantDivision + lowerDivision
        repository.saveTeams(allTeams)

        assertFalse(
            "41 clubes não cabem em um turno dentro das 40 semanas",
            LeagueSeasonFormat.fitsCurrentSeason(giantDivision.size)
        )
        assertTrue(LeagueSeasonFormat.fitsCurrentSeason(lowerDivision.size))

        val lowerFixtures = calendarUseCase.generateRoundRobinFixtures(
            season = 2026,
            teams = lowerDivision,
            competitionType = "SERIE_D",
            startWeek = 1,
            legs = 2
        ).mapIndexed { index, fixture ->
            val homeWins = fixture.homeTeamId < fixture.awayTeamId
            fixture.copy(
                id = 10_000L + index,
                isPlayed = true,
                homeScore = if (homeWins) 2 else 0,
                awayScore = if (homeWins) 0 else 2
            )
        }
        repository.saveFixtures(lowerFixtures)

        val save = GameSave(
            id = 1,
            currentSeason = 2026,
            currentWeek = GameCalendar.WEEKS_PER_SEASON,
            playerTeamId = user.id
        )
        repository.saveGameSave(save)

        val expectedSnapshot = globalSimulation.buildSeasonStandings(
            season = 2026,
            teams = allTeams,
            detailedFixtures = lowerFixtures,
            detailedCountry = "Brasil"
        )
        val expectedRelegated = expectedSnapshot
            .rowsFor("Brasil", 4)
            .takeLast(4)
            .map { it.teamId }
            .toSet()
        val expectedPromoted = expectedSnapshot
            .rowsFor("Brasil", 5)
            .take(4)
            .map { it.teamId }
            .toSet()

        transitionUseCase.advanceToNextSeason(save)

        val updated = repository.getAllTeams().associateBy { it.id }
        expectedRelegated.forEach { id -> assertEquals(5, updated.getValue(id).division) }
        expectedPromoted.forEach { id -> assertEquals(4, updated.getValue(id).division) }
        giantDivision.filterNot { it.id in expectedRelegated }
            .forEach { assertEquals(4, updated.getValue(it.id).division) }
        assertEquals(1, updated.getValue(user.id).division)

        val persisted = repository.getGlobalStandingsForSeason(2026)
        assertEquals(41, persisted.count { it.country == "Brasil" && it.division == 4 })
        assertEquals(4, persisted.count { it.country == "Brasil" && it.division == 5 })
        assertEquals(
            "A divisão 5 deve vir dos 12 jogos detalhados reais",
            6,
            persisted.first { it.country == "Brasil" && it.division == 5 }.played
        )
    }

    private fun List<GlobalLeagueStanding>.rowsFor(
        country: String,
        division: Int
    ): List<GlobalLeagueStanding> = filter {
        it.country == country && it.division == division
    }.sortedBy { it.position }

    private fun team(
        id: Long,
        name: String,
        division: Int,
        rating: Int
    ) = Team(
        id = id,
        name = name,
        city = name,
        state = "BR",
        country = "Brasil",
        division = division,
        rating = rating
    )
}
