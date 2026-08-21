package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase103InternationalCompetitionTest {

    @Test
    fun `UEFA qualification consumes previous domestic positions and remains exclusive`() {
        val candidates = uefaCandidates(120)
        val englandFirst = candidates.first { it.id == 1L }
        val englandSecond = candidates.first { it.id == 21L }
        val standings = listOf(
            standing(2026, englandSecond, position = 1, points = 90),
            standing(2026, englandFirst, position = 2, points = 85)
        )

        val fields = UefaQualificationRules.selectLeaguePhaseFields(candidates, standings)
        assertEquals(36, fields.championsLeague.size)
        assertEquals(36, fields.europaLeague.size)
        assertEquals(36, fields.conferenceLeague.size)
        assertEquals(108, fields.all.map { it.team.id }.toSet().size)

        val championsIds = fields.championsLeague.map { it.team.id }
        assertTrue(championsIds.indexOf(englandSecond.id) < championsIds.indexOf(englandFirst.id))
        assertTrue(fields.all.none { it.team.rating != candidates.first { c -> c.id == it.team.id }.rating })
    }

    @Test
    fun `world qualification uses only real persisted clubs and never auto promotes user team`() {
        val teams = worldUniverse()
        val user = teams.last()
        val field = requireNotNull(
            SuperMundialQualificationRules.selectField(
                season = 2029,
                allTeams = teams,
                previousSeasonStandings = worldStandings(teams)
            )
        )

        assertEquals(32, field.teams.size)
        assertEquals(32, field.teams.map { it.id }.toSet().size)
        assertTrue(field.teams.all { selected -> teams.any { it.id == selected.id } })
        assertFalse("Clube do usuário não ganha vaga por ser controlado", field.teams.any { it.id == user.id })
        assertTrue(field.usedSportingSnapshot)
    }

    @Test
    fun `world qualification fails closed when a confederation cannot fill its quota`() {
        val incomplete = worldUniverse().filterNot {
            CountryFootballRulesRegistry.confederationFor(it.country) == FootballConfederation.OFC
        }
        assertEquals(
            null,
            SuperMundialQualificationRules.selectField(2029, incomplete, worldStandings(incomplete))
        )
        assertTrue(
            SuperMundialSystem.generateGroupStageFixtures(2029, incomplete, worldStandings(incomplete)).isEmpty()
        )
    }

    @Test
    fun `world sporting snapshot decides which club receives an association slot`() {
        val teams = worldUniverse()
        val brazil = teams.filter { it.country == "Brasil" }.sortedBy { it.id }
        assertEquals(2, brazil.size)

        val firstSnapshot = worldStandings(teams) + listOf(
            standing(2028, brazil[0], 1, 88),
            standing(2028, brazil[1], 2, 84)
        )
        val secondSnapshot = worldStandings(teams).filterNot { it.teamId in brazil.map(Team::id) } + listOf(
            standing(2028, brazil[1], 1, 91),
            standing(2028, brazil[0], 2, 80)
        )

        val first = requireNotNull(SuperMundialQualificationRules.selectField(2029, teams, firstSnapshot))
        val second = requireNotNull(SuperMundialQualificationRules.selectField(2029, teams, secondSnapshot))

        assertTrue(first.teams.any { it.id == brazil[0].id })
        assertFalse(first.teams.any { it.id == brazil[1].id })
        assertTrue(second.teams.any { it.id == brazil[1].id })
        assertFalse(second.teams.any { it.id == brazil[0].id })
    }

    @Test
    fun `world draw is deterministic and respects confederation diversity`() {
        val teams = worldUniverse()
        val field = requireNotNull(
            SuperMundialQualificationRules.selectField(2029, teams, worldStandings(teams))
        )
        val first = WorldClubDrawEngine.drawGroups(2029, field.teams)
        val second = WorldClubDrawEngine.drawGroups(2029, field.teams.reversed())

        assertEquals(first.map { it.map(Team::id) }, second.map { it.map(Team::id) })
        assertEquals(8, first.size)
        assertTrue(first.all { it.size == 4 })
        first.forEach { group ->
            assertEquals(4, group.map { it.id }.toSet().size)
            assertEquals(4, group.map { it.country }.toSet().size)
            FootballConfederation.values().forEach { confederation ->
                val count = group.count {
                    CountryFootballRulesRegistry.confederationFor(it.country) == confederation
                }
                val limit = if (confederation == FootballConfederation.UEFA) 2 else 1
                assertTrue("Confederação $confederation excedeu limite no grupo", count <= limit)
            }
        }

        val differentSeason = WorldClubDrawEngine.drawGroups(2033, field.teams)
        assertNotEquals(first.map { it.map(Team::id) }, differentSeason.map { it.map(Team::id) })
    }

    @Test
    fun `world league phase creates 48 unique fixtures and three matches per club`() {
        val teams = worldUniverse()
        val fixtures = SuperMundialSystem.generateGroupStageFixtures(
            season = 2029,
            allTeams = teams,
            previousSeasonStandings = worldStandings(teams)
        )
        assertEquals(48, fixtures.size)
        val ids = fixtures.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.toSet()
        assertEquals(32, ids.size)
        ids.forEach { id ->
            assertEquals(3, fixtures.count { it.homeTeamId == id || it.awayTeamId == id })
        }
        assertTrue(fixtures.none { it.homeTeamId == it.awayTeamId })
        assertEquals(48, fixtures.map { Triple(it.week, it.homeTeamId, it.awayTeamId) }.toSet().size)
        FixtureScheduleValidator.requireValid(fixtures)
    }

    @Test
    fun `world cycle runs only every four seasons`() {
        val teams = worldUniverse()
        val standings = worldStandings(teams)
        assertEquals(48, SuperMundialSystem.generateGroupStageFixtures(2029, teams, standings).size)
        assertTrue(SuperMundialSystem.generateGroupStageFixtures(2030, teams, standings).isEmpty())
        assertTrue(SuperMundialSystem.generateGroupStageFixtures(2031, teams, standings).isEmpty())
        assertTrue(SuperMundialSystem.generateGroupStageFixtures(2032, teams, standings).isEmpty())
        assertEquals(48, SuperMundialSystem.generateGroupStageFixtures(2033, teams, standings).size)
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

    private fun uefaCandidates(count: Int): List<Team> {
        val countries = listOf(
            "Inglaterra", "Espanha", "Itália", "Alemanha", "França",
            "Portugal", "Países Baixos", "Bélgica", "Turquia", "Escócia",
            "Áustria", "Suíça", "Dinamarca", "Noruega", "Suécia",
            "Polônia", "Tchéquia", "Croácia", "Sérvia", "Grécia"
        )
        return (0 until count).map { index ->
            val country = countries[index % countries.size]
            Team(
                id = index + 1L,
                name = "$country Clube ${index + 1}",
                city = "Cidade",
                state = country.take(2),
                country = country,
                division = 1,
                rating = 70 + (index % 20)
            )
        }
    }

    private fun worldUniverse(): List<Team> {
        val countries = buildList {
            addAll(listOf(
                "Inglaterra", "Espanha", "Itália", "Alemanha", "França", "Portugal",
                "Países Baixos", "Bélgica", "Turquia", "Escócia", "Áustria", "Suíça", "Dinamarca"
            ))
            addAll(listOf("Brasil", "Brasil", "Argentina", "Colômbia", "Chile", "Uruguai", "Paraguai"))
            addAll(listOf("Japão", "Coreia do Sul", "Arábia Saudita", "Emirados Árabes Unidos", "Catar"))
            addAll(listOf("Egito", "Marrocos", "Tunísia", "África do Sul", "África"))
            addAll(listOf("México", "Estados Unidos / Canadá", "Costa Rica", "Guatemala", "Honduras"))
            addAll(listOf("Oceania", "Oceania"))
            // Clube extra deliberadamente fraco e sem vaga garantida; usado como "user" no teste.
            add("Venezuela")
        }
        return countries.mapIndexed { index, country ->
            Team(
                id = 50_000L + index,
                name = "World QA ${index + 1}",
                city = "Cidade ${index + 1}",
                state = country.take(2),
                country = country,
                division = 1,
                rating = 90 - (index % 30),
                isPlayerControlled = index == countries.lastIndex
            )
        }
    }

    private fun worldStandings(teams: List<Team>): List<GlobalLeagueStanding> =
        teams.groupBy { it.country }.flatMap { (_, associationTeams) ->
            associationTeams.sortedBy { it.id }.mapIndexed { index, team ->
                standing(2028, team, index + 1, 90 - index)
            }
        }
}
