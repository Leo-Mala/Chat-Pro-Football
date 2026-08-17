package com.example.usecase

import com.example.data.GameRepository
import com.example.data.LeagueSeasonFormat
import com.example.data.Team
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLeagueCalendarTest {

    private val useCase = GenerateCalendarUseCase(mockk<GameRepository>(relaxed = true))

    @Test
    fun twentyClubLeagueKeepsHomeAndAwayFormat() {
        val teams = teams(count = 20, country = "Brasil")

        val fixtures = useCase.generateSeasonFixtures(
            season = 2026,
            teams = teams,
            userTeamId = teams.first().id,
            userCountry = "Brasil"
        ).filter { it.competitionType == "SERIE_A" }

        assertEquals(2, LeagueSeasonFormat.legsForDetailedLeague(20))
        assertEquals(380, fixtures.size)
        assertEquals(38, fixtures.maxOf { it.week })

        val directedPairs = fixtures
            .groupingBy { it.homeTeamId to it.awayTeamId }
            .eachCount()
        assertEquals(20 * 19, directedPairs.size)
        assertTrue(directedPairs.values.all { it == 1 })
    }

    @Test
    fun thirtyClubLeagueUsesSingleRoundRobinInsideFortyWeekSeason() {
        val teams = teams(count = 30, country = "Argentina")

        val fixtures = useCase.generateSeasonFixtures(
            season = 2026,
            teams = teams,
            userTeamId = teams.first().id,
            userCountry = "Argentina"
        ).filter { it.competitionType == "SERIE_A" }

        assertEquals(1, LeagueSeasonFormat.legsForDetailedLeague(30))
        assertEquals(435, fixtures.size)
        assertEquals(29, fixtures.maxOf { it.week })
        assertTrue(fixtures.all { it.week <= 40 })

        val unorderedPairs = fixtures
            .groupingBy { minOf(it.homeTeamId, it.awayTeamId) to maxOf(it.homeTeamId, it.awayTeamId) }
            .eachCount()
        assertEquals(30 * 29 / 2, unorderedPairs.size)
        assertTrue(unorderedPairs.values.all { it == 1 })
    }

    @Test
    fun fiftySevenClubLeagueUsesThreeGroupsOfNineteenWithByes() {
        val teams = teams(count = 57, country = "México", division = 3)

        val fixtures = useCase.generateSeasonFixtures(
            season = 2026,
            teams = teams,
            userTeamId = teams.first().id,
            userCountry = "México"
        ).filter { it.competitionType == "SERIE_C" }

        assertEquals(1_026, fixtures.size)
        assertEquals(38, fixtures.maxOf { it.week })
        assertTrue(fixtures.all { it.week in 1..40 })
        assertTrue(fixtures.none { it.homeTeamId <= 0L || it.awayTeamId <= 0L })
        assertTrue(
            LeagueSeasonFormat.hasExpectedDetailedPairings(
                teamIds = teams.map { it.id }.toSet(),
                fixtures = fixtures
            )
        )

        val appearances = fixtures
            .flatMap { listOf(it.homeTeamId, it.awayTeamId) }
            .groupingBy { it }
            .eachCount()
        assertEquals(57, appearances.size)
        assertTrue(appearances.values.all { it == 36 })
    }

    @Test
    fun sixtyClubLeagueUsesThreeBalancedGroupsOfTwenty() {
        val teams = teams(count = 60, country = "Brasil", division = 4)

        val fixtures = useCase.generateSeasonFixtures(
            season = 2026,
            teams = teams,
            userTeamId = teams.first().id,
            userCountry = "Brasil"
        ).filter { it.competitionType == "SERIE_D" }

        assertEquals(1_140, fixtures.size)
        assertEquals(38, fixtures.maxOf { it.week })
        assertTrue(fixtures.all { it.week in 1..40 })
        assertTrue(
            LeagueSeasonFormat.hasExpectedDetailedPairings(
                teamIds = teams.map { it.id }.toSet(),
                fixtures = fixtures
            )
        )
        assertTrue(fixtures.groupingBy { it.homeTeamId }.eachCount().values.all { it == 19 })
    }

    @Test
    fun ninetySixClubLeagueUsesSixBalancedGroupsOfSixteen() {
        val teams = teams(count = 96, country = "Brasil", division = 4)

        val fixtures = useCase.generateSeasonFixtures(
            season = 2026,
            teams = teams,
            userTeamId = teams.first().id,
            userCountry = "Brasil"
        ).filter { it.competitionType == "SERIE_D" }

        assertEquals(1_440, fixtures.size)
        assertEquals(30, fixtures.maxOf { it.week })
        assertTrue(fixtures.all { it.week in 1..40 })
        assertTrue(
            LeagueSeasonFormat.hasExpectedDetailedPairings(
                teamIds = teams.map { it.id }.toSet(),
                fixtures = fixtures
            )
        )
        assertTrue(fixtures.groupingBy { it.homeTeamId }.eachCount().values.all { it == 15 })
    }

    @Test
    fun irregularGiantDivisionRemainsExplicitlyOnFallbackWithoutImpossibleLeagueFixtures() {
        val teams = teams(count = 41, country = "Teste")

        val leagueFixtures = useCase.generateSeasonFixtures(
            season = 2026,
            teams = teams,
            userTeamId = teams.first().id,
            userCountry = "Teste"
        ).filter { it.competitionType == "SERIE_A" }

        assertFalse(LeagueSeasonFormat.supportsDetailedFormat(41))
        assertTrue(leagueFixtures.isEmpty())
    }

    private fun teams(
        count: Int,
        country: String,
        division: Int = 1
    ): List<Team> =
        (1L..count.toLong()).map { id ->
            Team(
                id = id,
                name = "$country Clube $id",
                city = "Cidade $id",
                state = "XX",
                country = country,
                division = division,
                rating = 90 - (id % 20).toInt()
            )
        }
}
