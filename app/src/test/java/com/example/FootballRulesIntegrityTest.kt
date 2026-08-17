package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.GameCalendar
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.LeagueHierarchyLoader
import com.example.data.Team
import com.example.usecase.DatabaseIntegrityUseCase
import com.example.usecase.GenerateCalendarUseCase
import com.example.usecase.SeasonTransitionUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FootballRulesIntegrityTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var calendar: GenerateCalendarUseCase
    private lateinit var transition: SeasonTransitionUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        calendar = GenerateCalendarUseCase(repository)
        transition = SeasonTransitionUseCase(
            repository,
            calendar,
            DatabaseIntegrityUseCase(repository)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `adjacent division rules are balanced and hierarchy drives movement count`() {
        for (country in LeagueHierarchyLoader.supportedCountries) {
            val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry(country)
            assertTrue(
                "Promotion/relegation boundary must preserve division sizes for $country",
                hierarchy.hasBalancedAdjacentMovementRules()
            )
        }

        val brazil = LeagueHierarchyLoader.getHierarchyForCountry("Brasil")
        assertEquals(listOf(1, 2, 3, 4, 5), brazil.divisions.map { it.divisionLevel })
        assertEquals(4, brazil.movementSpotsBetween(1, 2))
        assertEquals(4, brazil.movementSpotsBetween(2, 3))
        assertEquals(4, brazil.movementSpotsBetween(3, 4))
        assertEquals(4, brazil.movementSpotsBetween(4, 5))

        val england = LeagueHierarchyLoader.getHierarchyForCountry("Inglaterra")
        assertEquals(listOf(1, 2, 3), england.divisions.map { it.divisionLevel })
        assertEquals(2, england.movementSpotsBetween(1, 2))
        assertEquals(2, england.movementSpotsBetween(2, 3))
        assertEquals(0, england.movementSpotsBetween(3, 4))
    }

    @Test
    fun `season transition uses real results for user country and compact standings for CPU country`() = runBlocking {
        val englandA = teams("Inglaterra", division = 1, firstId = 100L, count = 4)
        val englandB = teams("Inglaterra", division = 2, firstId = 200L, count = 4)
        val spainA = teams("Espanha", division = 1, firstId = 300L, count = 4)
        val spainB = teams("Espanha", division = 2, firstId = 400L, count = 4)
        val allTeams = englandA + englandB + spainA + spainB
        repository.saveTeams(allTeams)

        val season = 2026
        val englandFixtures = (
            calendar.generateRoundRobinFixtures(season, englandA, "SERIE_A") +
                calendar.generateRoundRobinFixtures(season, englandB, "SERIE_B")
            ).mapIndexed { index, fixture -> completedFixture(fixture, index) }
        repository.saveFixtures(englandFixtures)

        val save = GameSave(
            coachName = "Rules QA",
            currentSeason = season,
            currentWeek = GameCalendar.WEEKS_PER_SEASON,
            playerTeamId = englandA.first().id,
            bankBalance = 10_000_000L
        )
        repository.saveGameSave(save)

        val originalEnglandDivisions = (englandA + englandB).associate { it.id to it.division }
        val originalSpainDivisions = (spainA + spainB).associate { it.id to it.division }

        transition.advanceToNextSeason(save)

        val afterTeams = repository.getAllTeams().associateBy { it.id }
        val englandChanged = originalEnglandDivisions.count { (id, oldDivision) ->
            afterTeams.getValue(id).division != oldDivision
        }
        val spainChanged = originalSpainDivisions.count { (id, oldDivision) ->
            afterTeams.getValue(id).division != oldDivision
        }

        assertEquals(4, englandChanged)
        assertEquals(4, spainChanged)
        assertEquals(4, afterTeams.values.count { it.country == "Inglaterra" && it.division == 1 })
        assertEquals(4, afterTeams.values.count { it.country == "Inglaterra" && it.division == 2 })
        assertEquals(4, afterTeams.values.count { it.country == "Espanha" && it.division == 1 })
        assertEquals(4, afterTeams.values.count { it.country == "Espanha" && it.division == 2 })

        val snapshot = repository.getGlobalStandingsForSeason(season)
        assertEquals(4, snapshot.count { it.country == "Espanha" && it.division == 1 })
        assertEquals(4, snapshot.count { it.country == "Espanha" && it.division == 2 })

        val nextFixtures = repository.getFixturesForSeason(2027)
        val leagueFixtures = nextFixtures.filter {
            it.competitionType in setOf("SERIE_A", "SERIE_B", "SERIE_C", "SERIE_D")
        }
        assertTrue(leagueFixtures.isNotEmpty())
        val englishIds = afterTeams.values.filter { it.country == "Inglaterra" }.map { it.id }.toSet()
        assertTrue(
            leagueFixtures.all { it.homeTeamId in englishIds && it.awayTeamId in englishIds }
        )
    }

    @Test
    fun `transition into eligible year regenerates complete Super Mundial group stage`() = runBlocking {
        val brazil = teams("Brasil", division = 1, firstId = 1_000L, count = 4)
        val england = teams("Inglaterra", division = 1, firstId = 2_000L, count = 4)
        repository.saveTeams(brazil + england)

        val save = GameSave(
            coachName = "Mundial QA",
            currentSeason = 2028,
            currentWeek = GameCalendar.WEEKS_PER_SEASON,
            playerTeamId = brazil.first().id,
            bankBalance = 10_000_000L
        )
        repository.saveGameSave(save)

        transition.advanceToNextSeason(save)

        val fixtures2029 = repository.getFixturesForSeason(2029)
        val worldGroups = fixtures2029.filter { it.competitionType.startsWith("WORLD_CUP_GP_") }
        assertEquals(48, worldGroups.size)

        val participants = worldGroups
            .flatMap { listOf(it.homeTeamId, it.awayTeamId) }
            .groupingBy { it }
            .eachCount()
        assertEquals(32, participants.size)
        assertTrue(participants.values.all { it == 3 })
        assertEquals(3, participants[brazil.first().id])

        val leagueFixtures = fixtures2029.filter {
            it.competitionType in setOf("SERIE_A", "SERIE_B", "SERIE_C", "SERIE_D")
        }
        val brazilIds = brazil.map { it.id }.toSet()
        assertTrue(leagueFixtures.isNotEmpty())
        assertTrue(
            leagueFixtures.all { it.homeTeamId in brazilIds && it.awayTeamId in brazilIds }
        )
    }

    private fun teams(
        country: String,
        division: Int,
        firstId: Long,
        count: Int
    ): List<Team> {
        return (0 until count).map { index ->
            Team(
                id = firstId + index,
                name = "$country D$division Clube ${index + 1}",
                city = "Cidade ${index + 1}",
                state = "ST",
                country = country,
                division = division,
                rating = 60 + index
            )
        }
    }

    private fun completedFixture(fixture: Fixture, index: Int): Fixture {
        val homeWins = index % 3 != 0
        return fixture.copy(
            homeScore = if (homeWins) 2 else 0,
            awayScore = if (homeWins) 0 else 1,
            isPlayed = true
        )
    }
}
