package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldCompetitionSeasonRolloverTest {

    @Test
    fun `future world editions can change participants from sporting snapshots`() {
        val teams = worldUniverse()
        val brazil = teams.filter { it.country == "Brasil" }.sortedBy { it.id }
        val common = standings(teams).filterNot { it.teamId in brazil.map(Team::id) }

        val field2029 = requireNotNull(
            SuperMundialQualificationRules.selectField(
                season = 2029,
                allTeams = teams,
                previousSeasonStandings = common + listOf(
                    standing(2028, brazil[0], 1, 92),
                    standing(2028, brazil[1], 2, 80)
                )
            )
        )
        val field2033 = requireNotNull(
            SuperMundialQualificationRules.selectField(
                season = 2033,
                allTeams = teams,
                previousSeasonStandings = common + listOf(
                    standing(2032, brazil[1], 1, 94),
                    standing(2032, brazil[0], 2, 81)
                )
            )
        )

        assertTrue(field2029.teams.any { it.id == brazil[0].id })
        assertFalse(field2029.teams.any { it.id == brazil[1].id })
        assertTrue(field2033.teams.any { it.id == brazil[1].id })
        assertFalse(field2033.teams.any { it.id == brazil[0].id })
    }

    private fun worldUniverse(): List<Team> {
        val countries = buildList {
            addAll(listOf(
                "Inglaterra", "Espanha", "Itália", "Alemanha", "França", "Portugal",
                "Países Baixos", "Bélgica", "Turquia", "Escócia", "Áustria", "Suíça", "Dinamarca"
            ))
            addAll(listOf("Brasil", "Brasil", "Argentina", "Colômbia", "Chile", "Uruguai", "Paraguai", "Venezuela"))
            addAll(listOf("Japão", "Coreia do Sul", "Arábia Saudita", "Emirados Árabes Unidos", "Catar"))
            addAll(listOf("Egito", "Marrocos", "Tunísia", "África do Sul", "África"))
            addAll(listOf("México", "Estados Unidos / Canadá", "Costa Rica", "Guatemala", "Honduras"))
            addAll(listOf("Oceania", "Oceania"))
        }
        return countries.mapIndexed { index, country ->
            Team(
                id = 90_000L + index,
                name = "World rollover ${index + 1}",
                city = "Cidade",
                state = country.take(2),
                country = country,
                division = 1,
                rating = 75
            )
        }
    }

    private fun standings(teams: List<Team>): List<GlobalLeagueStanding> =
        teams.groupBy { it.country }.flatMap { (_, associationTeams) ->
            associationTeams.sortedBy { it.id }.mapIndexed { index, team ->
                standing(2028, team, index + 1, 90 - index)
            }
        }

    private fun standing(season: Int, team: Team, position: Int, points: Int) =
        GlobalLeagueStanding(
            season = season,
            country = team.country,
            division = 1,
            teamId = team.id,
            position = position,
            points = points,
            played = 38,
            wins = 0,
            draws = 0,
            losses = 0,
            goalsFor = 0,
            goalsAgainst = 0,
            goalDifference = 0
        )
}
