package com.example.usecase

import com.example.data.DefaultData
import com.example.data.GlobalFootballSystem
import com.example.data.LeagueSeasonFormat
import com.example.data.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalWorldSimulationScaleTest {

    @Test
    fun fullWorldDatasetProducesOneConsistentStandingPerClub() {
        val teams = DefaultData.countriesMap.flatMap { (country, data) ->
            data.teams.map { template ->
                Team(
                    id = GlobalFootballSystem.getGlobalId(country, template.name),
                    name = template.name,
                    city = template.city,
                    state = template.state,
                    country = country,
                    division = template.division,
                    rating = template.rating,
                    stadiumName = template.stadium
                )
            }
        }

        assertTrue("A regressão precisa exercitar o banco mundial completo", teams.size > 2_000)
        assertEquals(teams.size, teams.map { it.id }.toSet().size)

        val useCase = GlobalLeagueSimulationUseCase()
        val first = useCase.buildSeasonStandings(
            season = 2026,
            teams = teams,
            detailedFixtures = emptyList(),
            detailedCountry = "__CPU_ONLY__"
        )
        val second = useCase.buildSeasonStandings(
            season = 2026,
            teams = teams,
            detailedFixtures = emptyList(),
            detailedCountry = "__CPU_ONLY__"
        )

        assertEquals("A simulação mundial deve ser determinística", first, second)
        assertEquals("Cada clube deve gerar exatamente uma linha de classificação", teams.size, first.size)

        val rowsByLeague = first.groupBy { it.country to it.division }
        val teamsByLeague = teams.groupBy { it.country to it.division }
        assertEquals(teamsByLeague.keys, rowsByLeague.keys)
        assertTrue("O dataset precisa cobrir muitos campeonatos", rowsByLeague.size > 100)

        for ((league, leagueTeams) in teamsByLeague) {
            val rows = rowsByLeague.getValue(league).sortedBy { it.position }
            assertEquals(leagueTeams.size, rows.size)
            assertEquals((1..leagueTeams.size).toList(), rows.map { it.position })
            assertEquals(leagueTeams.map { it.id }.toSet(), rows.map { it.teamId }.toSet())

            val legs = LeagueSeasonFormat.legsForCompactSimulation(leagueTeams.size)
            val expectedPlayed = if (leagueTeams.size < 2) 0 else (leagueTeams.size - 1) * legs
            rows.forEach { row ->
                assertEquals(expectedPlayed, row.played)
                assertEquals(row.played, row.wins + row.draws + row.losses)
                assertEquals(row.points, row.wins * 3 + row.draws)
                assertEquals(row.goalDifference, row.goalsFor - row.goalsAgainst)
            }

            assertEquals(rows.sumOf { it.wins }, rows.sumOf { it.losses })
            assertEquals(rows.sumOf { it.goalsFor }, rows.sumOf { it.goalsAgainst })
            assertEquals(0, rows.sumOf { it.draws } % 2)
        }
    }
}
