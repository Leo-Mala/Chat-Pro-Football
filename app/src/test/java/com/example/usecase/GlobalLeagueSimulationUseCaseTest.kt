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
    fun compactCpuSimulationIsDeterministicAndLeagueWideConsistent() {
        val teams = listOf(
            team(1, "River", "Argentina", 88),
            team(2, "Boca", "Argentina", 86),
            team(3, "Racing", "Argentina", 79),
            team(4, "Velez", "Argentina", 76),
            team(5, "Clube B", "Argentina", 65, division = 2)
        )

        val first = useCase.buildSeasonStandings(2026, teams, emptyList(), "Brasil")
        val second = useCase.buildSeasonStandings(2026, teams, emptyList(), "Brasil")

        assertEquals(first, second)
        assertEquals(5, first.size)

        val topDivision = first.filter { it.division == 1 }
        assertEquals(4, topDivision.size)
        assertEquals(listOf(1, 2, 3, 4), topDivision.map { it.position })
        assertFalse(topDivision.any { it.teamId == 5L })

        topDivision.forEach { row ->
            assertEquals(6, row.played)
            assertEquals(row.played, row.wins + row.draws + row.losses)
            assertEquals(row.points, row.wins * 3 + row.draws)
            assertEquals(row.goalDifference, row.goalsFor - row.goalsAgainst)
        }

        assertEquals(topDivision.sumOf { it.wins }, topDivision.sumOf { it.losses })
        assertEquals(topDivision.sumOf { it.goalsFor }, topDivision.sumOf { it.goalsAgainst })
        assertEquals(0, topDivision.sumOf { it.draws } % 2)

        val secondDivision = first.single { it.division == 2 }
        assertEquals(5L, secondDivision.teamId)
        assertEquals(1, secondDivision.position)
        assertEquals(0, secondDivision.played)
    }

    @Test
    fun largeCompactDivisionUsesSingleRoundRobinToBoundCpuCost() {
        val teams = (1L..24L).map { id ->
            team(id, "Div2 $id", "Argentina", 50 + (id % 30).toInt(), division = 2)
        }

        val standings = useCase.buildSeasonStandings(2026, teams, emptyList(), "Brasil")

        assertEquals(24, standings.size)
        assertTrue(standings.all { it.division == 2 })
        assertTrue(standings.all { it.played == 23 })
        assertEquals(standings.sumOf { it.wins }, standings.sumOf { it.losses })
        assertEquals(standings.sumOf { it.goalsFor }, standings.sumOf { it.goalsAgainst })
    }

    @Test
    fun detailedCountryUsesCompleteActualFixturesInsteadOfSyntheticRatingOrder() {
        val strongestByRating = team(10, "Rating FC", "Brasil", 99)
        val actualChampion = team(11, "Resultado FC", "Brasil", 70)
        val third = team(12, "Terceiro", "Brasil", 80)
        val teams = listOf(strongestByRating, actualChampion, third)

        val fixtures = listOf(
            playedFixture(1, actualChampion.id, strongestByRating.id, 2, 0),
            playedFixture(2, actualChampion.id, third.id, 1, 0),
            playedFixture(3, strongestByRating.id, third.id, 3, 0),
            playedFixture(4, strongestByRating.id, actualChampion.id, 0, 1),
            playedFixture(5, third.id, actualChampion.id, 0, 2),
            playedFixture(6, third.id, strongestByRating.id, 1, 1)
        )

        val standings = useCase.buildSeasonStandings(2026, teams, fixtures, "Brasil")

        assertEquals(actualChampion.id, standings.first().teamId)
        assertEquals(12, standings.first().points)
        assertEquals(4, standings.first().wins)
        assertEquals(4, standings.first().played)
    }

    @Test
    fun detailedLowerDivisionAlsoUsesItsRealCompletedResults() {
        val favorite = team(40, "Favorito B", "Brasil", 99, division = 2)
        val champion = team(41, "Campeao B", "Brasil", 60, division = 2)
        val teams = listOf(favorite, champion)
        val fixtures = listOf(
            playedFixture(40, champion.id, favorite.id, 1, 0, competitionType = "SERIE_B"),
            playedFixture(41, favorite.id, champion.id, 0, 2, competitionType = "SERIE_B")
        )

        val standings = useCase.buildSeasonStandings(2026, teams, fixtures, "Brasil")

        assertEquals(2, standings.size)
        assertTrue(standings.all { it.division == 2 })
        assertEquals(champion.id, standings.first().teamId)
        assertEquals(6, standings.first().points)
    }

    @Test
    fun thirtyTeamDetailedLeagueUsesCompleteSingleRoundRobinResults() {
        val teams = (1L..30L).map { id ->
            team(
                id = id,
                name = "Argentina $id",
                country = "Argentina",
                rating = if (id == 30L) 1 else 99
            )
        }

        var fixtureId = 1000L
        val fixtures = buildList {
            for (i in 0 until teams.lastIndex) {
                for (j in i + 1 until teams.size) {
                    val home = teams[i]
                    val away = teams[j]
                    val awayIsLowRatedChampion = away.id == 30L
                    add(
                        playedFixture(
                            id = fixtureId++,
                            homeId = home.id,
                            awayId = away.id,
                            homeScore = if (awayIsLowRatedChampion) 0 else 0,
                            awayScore = if (awayIsLowRatedChampion) 1 else 0,
                            countrySeasonWeek = ((fixtureId - 1001L) % 29L).toInt() + 1
                        )
                    )
                }
            }
        }

        val standings = useCase.buildSeasonStandings(
            season = 2026,
            teams = teams,
            detailedFixtures = fixtures,
            detailedCountry = "Argentina"
        )

        assertEquals(435, fixtures.size)
        assertEquals(30L, standings.first().teamId)
        assertEquals(87, standings.first().points)
        assertEquals(29, standings.first().wins)
        assertEquals(29, standings.first().played)
    }

    @Test
    fun incompleteDetailedLeagueFallsBackInsteadOfPersistingPartialTruth() {
        val teams = listOf(
            team(20, "A", "Brasil", 80),
            team(21, "B", "Brasil", 75),
            team(22, "C", "Brasil", 70)
        )
        val incomplete = listOf(
            playedFixture(20, 20, 21, 1, 0),
            playedFixture(21, 20, 22, 1, 0),
            playedFixture(22, 21, 22, 1, 0)
        )

        val standings = useCase.buildSeasonStandings(2026, teams, incomplete, "Brasil")

        assertTrue(standings.all { it.played == 4 })
        assertEquals(standings.sumOf { it.wins }, standings.sumOf { it.losses })
    }

    @Test
    fun countCorrectButDuplicatedPairingsFallBackToSafeSimulation() {
        val teams = listOf(
            team(30, "A", "Brasil", 91),
            team(31, "B", "Brasil", 82),
            team(32, "C", "Brasil", 73)
        )
        val safeFallback = useCase.buildSeasonStandings(2026, teams, emptyList(), "Brasil")

        val malformed = listOf(
            playedFixture(30, 30, 31, 7, 0),
            playedFixture(31, 31, 30, 7, 0),
            playedFixture(32, 30, 32, 7, 0),
            playedFixture(33, 32, 30, 7, 0),
            playedFixture(34, 31, 32, 7, 0),
            playedFixture(35, 31, 32, 7, 0)
        )

        val result = useCase.buildSeasonStandings(2026, teams, malformed, "Brasil")
        assertEquals(safeFallback, result)
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
        awayScore: Int,
        countrySeasonWeek: Int = id.toInt(),
        competitionType: String = "SERIE_A"
    ) = Fixture(
        id = id,
        season = 2026,
        week = countrySeasonWeek,
        homeTeamId = homeId,
        awayTeamId = awayId,
        homeScore = homeScore,
        awayScore = awayScore,
        competitionType = competitionType,
        isPlayed = true
    )
}
