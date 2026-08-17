package com.example.usecase

import com.example.data.GameRepository
import com.example.data.Team
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupedDetailedLeagueSnapshotTest {

    private val calendar = GenerateCalendarUseCase(mockk<GameRepository>(relaxed = true))

    @Test
    fun ninetySixClubGroupedLeagueUsesRealResultsInsteadOfCompactRatingFallback() {
        val teams = (1L..96L).map { id ->
            Team(
                id = id,
                name = "Clube $id",
                city = "Cidade $id",
                state = "XX",
                country = "Brasil",
                division = 4,
                rating = if (id == 96L) 1 else 70 + (id % 20).toInt()
            )
        }
        val championId = 96L

        val detailedFixtures = calendar.generateSeasonFixtures(
            season = 2026,
            teams = teams,
            userTeamId = championId,
            userCountry = "Brasil"
        )
            .filter { it.competitionType == "SERIE_D" }
            .map { fixture ->
                when (championId) {
                    fixture.homeTeamId -> fixture.copy(
                        homeScore = 3,
                        awayScore = 0,
                        isPlayed = true
                    )
                    fixture.awayTeamId -> fixture.copy(
                        homeScore = 0,
                        awayScore = 3,
                        isPlayed = true
                    )
                    else -> fixture.copy(
                        homeScore = 0,
                        awayScore = 0,
                        isPlayed = true
                    )
                }
            }

        val standings = GlobalLeagueSimulationUseCase().buildSeasonStandings(
            season = 2026,
            teams = teams,
            detailedFixtures = detailedFixtures,
            detailedCountry = "Brasil"
        )

        val champion = standings.single { it.teamId == championId }
        assertEquals(1, champion.position)
        assertEquals(90, champion.points)
        assertEquals(30, champion.played)
        assertEquals(30, champion.wins)
        assertTrue(standings.all { it.played == 30 })
    }
}
