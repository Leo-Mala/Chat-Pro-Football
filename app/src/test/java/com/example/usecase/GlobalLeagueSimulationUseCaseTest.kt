package com.example.usecase

import com.example.data.Fixture
import com.example.data.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalLeagueSimulationUseCaseTest {

    private val useCase = GlobalLeagueSimulationUseCase()

    @Test
    fun compactCpuSimulationIsDeterministicAndInternallyConsistent() {
        val teams = listOf(
            team(1, "River", "Argentina", 88),
            team(2, "Boca", "Argentina", 86),
            team(3, "Racing", "Argentina", 79),
            team(4, "Velez", "Argentina", 76),
            team(5, "Clube B", "Argentina", 65, division = 2)
        )

        val first = useCase.buildSeasonStandings(
            season = 2026,
            teams = teams,
            detailedFixtures = emptyList(),
            detailedCountry = "Brasil"
        )
        val second = useCase.buildSeasonStandings(
            season = 2026,
            teams = teams,
            detailedFixtures = emptyList(),
            detailedCountry = "Brasil"
        )

        assertEquals(first, second)
        assertEquals(4, first.size)
        assertEquals(listOf(1, 2, 3, 4), first.map { it.position })
        assertFalse(first.any { it.teamId == 5L })

        first.forEach { row ->
            assertEquals(row.played, row.wins + row.draws + row.losses)
            assertEquals(row.points, row.wins * 3 + row.draws)
            assertEquals(row.goalDifference, row.goalsFor - row.goalsAgainst)
            assertTrue(row.played > 0)
        }
    }

    @Test
    fun detailedCountryUsesActualPlayedFixturesInsteadOfSyntheticRatingOrder() {
        val strongestByRating = team(10, "Rating FC", "Brasil", 99)
        val actualChampion = team(11, "Resultado FC", "Brasil", 70)
        val third = team(12, "Terceiro", "Brasil", 80)
        val teams = listOf(strongestByRating, actualChampion, third)

        val fixtures = listOf(
            playedFixture(1, actualChampion.id, strongestByRating.id, 2, 0),
            playedFixture(2, actualChampion.id, third.id, 1, 0),
            playedFixture(3, strongestByRating.id, third.id, 3, 0)
        )

        val standings = useCase.buildSeasonStandings(
            season = 2026,
            teams = teams,
            detailedFixtures = fixtures,
            detailedCountry = "Brasil"
        )

        assertEquals(actualChampion.id, standings.first().teamId)
        assertEquals(6, standings.first().points)
        assertEquals(2, standings.first().wins)
        assertEquals(2, standings.first().played)
    }

    private fun team(
        id: Long,
        name: String,
        country: String,
        rating: Int,
        division: Int = 1
    ) = Team(
        id = id,
        name = name,
        city = name,
        state = "XX",
        country = country,
        division = division,
        rating = rating
    )

    private fun playedFixture(
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
