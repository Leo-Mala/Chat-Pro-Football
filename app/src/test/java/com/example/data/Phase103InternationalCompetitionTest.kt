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
            standing(2026, englandSecond, 1, 90),
            standing(2026, englandFirst, 2, 85)
        )

        val fields = UefaQualificationRules.selectLeaguePhaseFields(candidates, standings)
        assertEquals(36, fields.championsLeague.size)
        assertEquals(36, fields.europaLeague.size)
        assertEquals(36, fields.conferenceLeague.size)
        assertEquals(108, fields.all.map { it.team.id }.toSet().size)

        val championsIds = fields.championsLeague.map { it.team.id }
        assertTrue(championsIds.indexOf(englandSecond.id) < championsIds.indexOf(englandFirst.id))
        assertTrue(fields.all.none { qualified ->
            qualified.team.rating != candidates.first { it.id == qualified.team.id }.rating
        })
    }

    @Test
    fun `UEFA season rollover follows changed domestic results`() {
        val candidates = uefaCandidates(120)
        val a = candidates.first { it.id == 1L }
        val b = candidates.first { it.id == 21L }
        val first = UefaQualificationRules.selectLeaguePhaseFields(
            candidates,
            listOf(standing(2026, a, 1, 90), standing(2026, b, 2, 80))
        )
        val second = UefaQualificationRules.selectLeaguePhaseFields(
            candidates,
            listOf(standing(2027, b, 1, 92), standing(2027, a, 2, 82))
        )
        assertTrue(first.championsLeague.map { it.team.id }.indexOf(a.id) < first.championsLeague.map { it.team.id }.indexOf(b.id))
        assertTrue(second.championsLeague.map { it.team.id }.indexOf(b.id) < second.championsLeague.map { it.team.id }.indexOf(a.id))
    }

    @Test
    fun `world qualification uses only national clubs and legacy API never auto promotes user team`() {
        val teams = worldUniverse()
        val brazil = teams.filter { it.country == "Brasil" }.sortedBy { it.id }
        val userOutsideSportingCut = brazil.last()

        val participants = SuperMundialSystem.selectParticipants(
            season = 2029,
            allTeams = teams,
            userTeamId = userOutsideSportingCut.id
        )
        val typedField = requireNotNull(SuperMundialQualificationRules.selectField(2029, teams))

        assertEquals(32, participants.size)
        assertEquals(32, participants.map { it.id }.toSet().size)
        assertTrue(participants.all { selected -> teams.any { it.id == selected.id } })
        assertTrue(participants.all { CountryFootballRulesRegistry.isContinentalCompetitionEligible(it.country) })
        assertTrue("Dataset atual deve declarar o fallback do slot OFC ausente", typedField.usedOfcDataGapFallback)
        assertFalse("Clube do usuário não ganha vaga por ser controlado", participants.any { it.id == userOutsideSportingCut.id })
    }

    @Test
    fun `world qualification fails closed when a supported confederation cannot fill its quota`() {
        val incomplete = worldUniverse().filterNot {
            CountryFootballRulesRegistry.isContinentalCompetitionEligible(it.country) &&
                CountryFootballRulesRegistry.confederationFor(it.country) == FootballConfederation.CAF
        }
        assertEquals(null, SuperMundialQualificationRules.selectField(2029, incomplete, worldStandings(incomplete)))
        assertTrue(SuperMundialSystem.generateGroupStageFixtures(2029, incomplete, worldStandings(incomplete)).isEmpty())
    }

    @Test
    fun `world sporting snapshot decides which club receives an association slot`() {
        val teams = worldUniverse()
        val brazil = teams.filter { it.country == "Brasil" }.sortedBy { it.id }
        val baseline = worldStandings(teams).filterNot { it.teamId in brazil.map(Team::id) }

        val first = requireNotNull(
            SuperMundialQualificationRules.selectField(
                2029,
                teams,
                baseline + listOf(standing(2028, brazil[0], 1, 88), standing(2028, brazil[1], 2, 84))
            )
        )
        val second = requireNotNull(
            SuperMundialQualificationRules.selectField(
                2029,
                teams,
                baseline + listOf(standing(2028, brazil[1], 1, 91), standing(2028, brazil[0], 2, 80))
            )
        )

        assertTrue(first.teams.any { it.id == brazil[0].id })
        assertFalse(first.teams.any { it.id == brazil[1].id })
        assertTrue(second.teams.any { it.id == brazil[1].id })
        assertFalse(second.teams.any { it.id == brazil[0].id })
    }

    @Test
    fun `world draw is deterministic and respects confederation diversity`() {
        val teams = worldUniverse()
        val field = requireNotNull(SuperMundialQualificationRules.selectField(2029, teams, worldStandings(teams)))
        val first = WorldClubDrawEngine.drawGroups(2029, field.teams)
        val second = WorldClubDrawEngine.drawGroups(2029, field.teams.reversed())

        assertEquals(first.map { it.map(Team::id) }, second.map { it.map(Team::id) })
        assertEquals(8, first.size)
        assertTrue(first.all { it.size == 4 })
        first.forEach { group ->
            assertEquals(4, group.map { it.id }.toSet().size)
            assertEquals(
                4,
                group.map { requireNotNull(CountryFootballRulesRegistry.resolve(it.country)).canonicalCountry }.toSet().size
            )
            FootballConfederation.values().forEach { confederation ->
                val count = group.count { CountryFootballRulesRegistry.confederationFor(it.country) == confederation }
                val limit = if (confederation == FootballConfederation.UEFA) 2 else 1
                assertTrue("Confederação $confederation excedeu limite no grupo", count <= limit)
            }
        }

        val differentSeason = WorldClubDrawEngine.drawGroups(2033, field.teams)
        assertNotEquals(first.map { it.map(Team::id) }, differentSeason.map { it.map(Team::id) })
    }

    @Test
    fun `world group phase has 48 unique fixtures and three matches per club`() {
        val teams = worldUniverse()
        val fixtures = SuperMundialSystem.generateGroupStageFixtures(2029, teams, worldStandings(teams))
        assertEquals(48, fixtures.size)
        val ids = fixtures.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.toSet()
        assertEquals(32, ids.size)
        ids.forEach { id -> assertEquals(3, fixtures.count { it.homeTeamId == id || it.awayTeamId == id }) }
        assertTrue(fixtures.none { it.homeTeamId == it.awayTeamId })
        assertEquals(48, fixtures.map { Triple(it.week, it.homeTeamId, it.awayTeamId) }.toSet().size)
        FixtureScheduleValidator.requireValid(fixtures)
    }

    @Test
    fun `world cycle runs only every four seasons`() {
        val teams = worldUniverse()
        val standings = worldStandings(teams)
        assertEquals(48, SuperMundialSystem.generateGroupStageFixtures(2029, teams, standings).size)
        (2030..2032).forEach { assertTrue(SuperMundialSystem.generateGroupStageFixtures(it, teams, standings).isEmpty()) }
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
            "Inglaterra", "Espanha", "Itália", "Alemanha", "França", "Portugal",
            "Países Baixos", "Bélgica", "Turquia", "Escócia", "Áustria", "Suíça",
            "Dinamarca", "Noruega", "Suécia", "Polônia", "Tchéquia", "Croácia", "Sérvia", "Grécia"
        )
        return (0 until count).map { index ->
            val country = countries[index % countries.size]
            Team(index + 1L, "$country Clube ${index + 1}", "Cidade", country.take(2), country, 1, rating = 70 + (index % 20))
        }
    }

    private fun worldUniverse(): List<Team> {
        val countries = buildList {
            addAll(listOf("Inglaterra", "Espanha", "Itália", "Alemanha", "França", "Portugal", "Países Baixos", "Bélgica", "Turquia", "Escócia", "Áustria", "Suíça", "Dinamarca"))
            addAll(listOf("Brasil", "Brasil", "Argentina", "Colômbia", "Chile", "Uruguai", "Paraguai", "Venezuela"))
            addAll(listOf("Japão", "Coreia do Sul", "Arábia Saudita", "Emirados Árabes Unidos", "Catar"))
            addAll(listOf("Egito", "Marrocos", "Tunísia", "África do Sul", "África"))
            addAll(listOf("México", "Estados Unidos / Canadá", "Costa Rica", "Guatemala", "Honduras"))
            addAll(listOf("Oceania", "Oceania"))
        }
        return countries.mapIndexed { index, country ->
            Team(
                id = 50_000L + index,
                name = "World QA ${index + 1}",
                city = "Cidade ${index + 1}",
                state = country.take(2),
                country = country,
                division = 1,
                rating = 90 - (index % 30)
            )
        }
    }

    private fun worldStandings(teams: List<Team>): List<GlobalLeagueStanding> =
        teams.groupBy { it.country }.flatMap { (_, associationTeams) ->
            associationTeams.sortedBy { it.id }.mapIndexed { index, team -> standing(2028, team, index + 1, 90 - index) }
        }
}
