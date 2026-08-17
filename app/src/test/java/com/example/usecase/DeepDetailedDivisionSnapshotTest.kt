package com.example.usecase

import com.example.data.Fixture
import com.example.data.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepDetailedDivisionSnapshotTest {

    @Test
    fun fifthDivisionUsesRealLegacySerieDFixturesInsteadOfSyntheticFallback() {
        val ratingFavorite = team(501, "Favorito E", rating = 99)
        val actualChampion = team(502, "Campeão E", rating = 40)
        val fixtures = listOf(
            playedFixture(1, actualChampion.id, ratingFavorite.id, 2, 0),
            playedFixture(2, ratingFavorite.id, actualChampion.id, 0, 1)
        )

        val rows = GlobalLeagueSimulationUseCase().buildSeasonStandings(
            season = 2026,
            teams = listOf(ratingFavorite, actualChampion),
            detailedFixtures = fixtures,
            detailedCountry = "Brasil"
        )

        assertEquals(2, rows.size)
        assertTrue(rows.all { it.division == 5 })
        assertEquals(actualChampion.id, rows.first().teamId)
        assertEquals(6, rows.first().points)
        assertEquals(2, rows.first().played)
    }

    private fun team(id: Long, name: String, rating: Int) = Team(
        id = id,
        name = name,
        city = name,
        state = "MG",
        country = "Brasil",
        division = 5,
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
        competitionType = "SERIE_D",
        isPlayed = true
    )
}
