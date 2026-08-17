package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ContinentalQualificationRulesTest {

    @Test
    fun previousSeasonPositionsOverridePersistentRatingsWithoutMutatingTeams() {
        val championA = team(1, "Campeão A", "Brasil", rating = 55)
        val runnerA = team(2, "Vice A", "Brasil", rating = 99)
        val promotedA = team(3, "Promovido A", "Brasil", rating = 96)
        val championB = team(4, "Campeão B", "Argentina", rating = 60)
        val runnerB = team(5, "Vice B", "Argentina", rating = 97)
        val fallback = team(6, "Sem Histórico", "Uruguai", rating = 84)

        val standings = listOf(
            standing(championA, 1),
            standing(runnerA, 2),
            standing(championB, 1),
            standing(runnerB, 2)
        )

        val adjusted = ContinentalQualificationRules.applyPreviousSeasonStandings(
            teams = listOf(championA, runnerA, promotedA, championB, runnerB, fallback),
            standings = standings
        ).associateBy { it.id }

        assertEquals(100, adjusted.getValue(championA.id).rating)
        assertEquals(99, adjusted.getValue(runnerA.id).rating)
        assertEquals(1, adjusted.getValue(promotedA.id).rating)
        assertEquals(100, adjusted.getValue(championB.id).rating)
        assertEquals(99, adjusted.getValue(runnerB.id).rating)
        assertEquals(84, adjusted.getValue(fallback.id).rating)

        // A transformação é transitória; o objeto persistido/original mantém seu rating real.
        assertEquals(55, championA.rating)
        assertEquals(99, runnerA.rating)
    }

    @Test
    fun emptyHistoryKeepsFirstSeasonFallbackUntouched() {
        val teams = listOf(
            team(10, "A", "Brasil", 80),
            team(11, "B", "Brasil", 70)
        )

        assertEquals(
            teams,
            ContinentalQualificationRules.applyPreviousSeasonStandings(teams, emptyList())
        )
    }

    private fun team(
        id: Long,
        name: String,
        country: String,
        rating: Int
    ) = Team(
        id = id,
        name = name,
        city = name,
        state = "XX",
        country = country,
        division = 1,
        rating = rating
    )

    private fun standing(team: Team, position: Int) = GlobalLeagueStanding(
        season = 2026,
        country = team.country,
        division = 1,
        teamId = team.id,
        position = position,
        points = 80 - position,
        played = 38,
        wins = 20,
        draws = 10,
        losses = 8,
        goalsFor = 60,
        goalsAgainst = 40,
        goalDifference = 20
    )
}
