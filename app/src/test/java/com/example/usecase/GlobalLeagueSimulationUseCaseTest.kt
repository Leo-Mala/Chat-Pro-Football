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
            assertEquals(6, row.played)
            assertEquals(row.played, row.wins + row.draws + row.losses)
            assertEquals(row.points, row.wins * 3 + row.draws)
            assertEquals(row.goalDifference, row.goalsFor - row.goalsAgainst)
        }

        // Invariantes que só uma liga baseada em confrontos reais consegue garantir.
        assertEquals(first.sumOf { it.wins }, first.sumOf { it.losses })
        assertEquals(first.sumOf { it.goalsFor }, first.sumOf { it.goalsAgainst })
        assertEquals(0, first.sumOf { it.draws } % 2)
    }

    @Test
    fun detailedCountryUsesCompleteActualFixturesInsteadOfSyntheticRatingOrder() {
        val strongestByRating = team(10, "Rating FC", "Brasil", 99)
        val actualChampion = team(11, "Resultado FC", "Brasil", 70)
        val third = team(12, "Terceiro", "Brasil", 80)
        val teams = listOf(strongestByRating, actualChampion, third)

        // 3 clubes em turno + returno = 6 partidas. O campeão real tem rating menor,
        // provando que o snapshot detalhado vem dos resultados e não do rating.
        val fixtures = listOf(
            playedFixture(1, actualChampion.id, strongestByRating.id, 2, 0),
            playedFixture(2, actualChampion.id, third.id, 1, 0),
            playedFixture(3, strongestByRating.id, third.id, 3, 0),
            playedFixture(4, strongestByRating.id, actualChampion.id, 0, 1),
            playedFixture(5, third.id, actualChampion.id, 0, 2),
            playedFixture(6, third.id, strongestByRating.id, 1, 1)
        )

        val standings = useCase.buildSeasonStandings(
            season = 2026,
            teams = teams,
            detailedFixtures = fixtures,
            detailedCountry = "Brasil"
        )

        assertEquals(actualChampion.id, standings.first().teamId)
        assertEquals(12, standings.first().points)
        assertEquals(4, standings.first().wins)
        assertEquals(4, standings.first().played)
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

        val standings = useCase.buildSeasonStandings(
            season = 2026,
            teams = teams,
            detailedFixtures = incomplete,
            detailedCountry = "Brasil"
        )

        // O fallback compacto para 3 clubes é turno + returno: 4 jogos por clube.
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
        val safeFallback = useCase.buildSeasonStandings(
            season = 2026,
            teams = teams,
            detailedFixtures = emptyList(),
            detailedCountry = "Brasil"
        )

        // A quantidade total é a correta (6), mas B x C aparece duas vezes e C x B não existe.
        val malformed = listOf(
            playedFixture(30, 30, 31, 7, 0),
            playedFixture(31, 31, 30, 7, 0),
            playedFixture(32, 30, 32, 7, 0),
            playedFixture(33, 32, 30, 7, 0),
            playedFixture(34, 31, 32, 7, 0),
            playedFixture(35, 31, 32, 7, 0)
        )

        val result = useCase.buildSeasonStandings(
            season = 2026,
            teams = teams,
            detailedFixtures = malformed,
            detailedCountry = "Brasil"
        )

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
