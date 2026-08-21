package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase103ReviewRegressionTest {

    @Test
    fun `relegated club cannot consume a UEFA slot from its old first division snapshot`() {
        val base = uefaCandidates(120)
        val relegated = base.first { it.id == 1L }.copy(division = 2)
        val candidates = base.map { if (it.id == relegated.id) relegated else it }
        val fields = UefaQualificationRules.selectLeaguePhaseFields(
            candidates,
            listOf(standing(2026, relegated, 1, 100))
        )

        assertEquals(108, fields.all.size)
        assertFalse(fields.all.any { it.team.id == relegated.id })
    }

    @Test
    fun `UEFA draw separates associations and caps repeated opponent associations after sporting reorder`() {
        val candidates = uefaCandidates(120)
        val grouped = candidates.groupBy { canonicalAssociation(it.country) }
        val snapshot = grouped.entries.flatMapIndexed { associationIndex, (_, associationTeams) ->
            associationTeams.sortedBy { it.id }.mapIndexed { teamIndex, team ->
                val points = when (teamIndex) {
                    0 -> 500 - associationIndex
                    1 -> when (associationIndex) {
                        0 -> 497 // England runner-up becomes the fourth slot-2 candidate.
                        1 -> 500
                        2 -> 499
                        3 -> 498
                        else -> 450 - associationIndex
                    }
                    else -> 300 - teamIndex * 10 - associationIndex
                }
                standing(2026, team, teamIndex + 1, points)
            }
        }

        val fields = UefaQualificationRules.selectLeaguePhaseFields(candidates, snapshot)
        assertEquals(36, fields.championsLeague.size)
        val englishOrdinals = fields.championsLeague
            .filter { canonicalAssociation(it.team.country) == canonicalAssociation("Inglaterra") }
            .map { it.slot.ordinal }
            .sorted()
        assertEquals(listOf(1, 24), englishOrdinals)

        val fixtures = UefaCompetitionSystem.generateOpeningFixtures(2027, fields)
            .filter { it.competitionType == UefaCompetitionSystem.CHAMPIONS_LEAGUE }
        assertEquals(144, fixtures.size)
        val byId = candidates.associateBy { it.id }
        assertTrue(
            fixtures.all { fixture ->
                canonicalAssociation(byId.getValue(fixture.homeTeamId).country) !=
                    canonicalAssociation(byId.getValue(fixture.awayTeamId).country)
            }
        )

        fields.championsLeague.forEach { qualified ->
            val opponentAssociations = fixtures.mapNotNull { fixture ->
                when (qualified.team.id) {
                    fixture.homeTeamId -> canonicalAssociation(byId.getValue(fixture.awayTeamId).country)
                    fixture.awayTeamId -> canonicalAssociation(byId.getValue(fixture.homeTeamId).country)
                    else -> null
                }
            }
            assertEquals(8, opponentAssociations.size)
            assertTrue(
                opponentAssociations.groupingBy { it }.eachCount().values.all { count -> count <= 2 }
            )
        }
    }

    @Test
    fun `world selector ignores legacy aggregate regions`() {
        val teams = worldUniverse() + listOf(
            Team(199_001L, "Legacy Africa", "Legacy", "AF", "África", 1, rating = 99),
            Team(199_002L, "Legacy Oceania", "Legacy", "OC", "Oceania", 1, rating = 99)
        )
        val field = requireNotNull(SuperMundialQualificationRules.selectField(2029, teams, standings(teams)))

        assertTrue(field.usedOfcDataGapFallback)
        assertTrue(field.teams.none { it.id in setOf(199_001L, 199_002L) })
        assertTrue(field.teams.all { CountryFootballRulesRegistry.isContinentalCompetitionEligible(it.country) })
    }

    @Test
    fun `world host eligibility ignores unknown and legacy countries consistently`() {
        val teams = worldUniverse() + listOf(
            Team(198_001L, "Unknown Host Trap", "Unknown", "ZZ", "ZZZ Unknown", 1, rating = 100),
            Team(198_002L, "Legacy Host Trap", "Legacy", "AF", "África", 1, rating = 100)
        )
        val eligible = SuperMundialQualificationRules.eligibleRealTeams(teams)
        val field = requireNotNull(SuperMundialQualificationRules.selectField(2029, teams, standings(teams)))
        val resolvedHost = requireNotNull(SuperMundialEditionPolicy.hostTeamForSeason(2029, eligible))

        assertEquals(resolvedHost.id, field.host.id)
        assertTrue(field.host.id !in setOf(198_001L, 198_002L))
        assertTrue(CountryFootballRulesRegistry.isContinentalCompetitionEligible(field.host.country))
    }

    @Test
    fun `world draw compares canonical association aliases`() {
        val teams = drawFieldWithAssociationAlias()
        val groups = WorldClubDrawEngine.drawGroups(2029, teams)

        assertEquals(8, groups.size)
        groups.forEach { group ->
            val associations = group.map {
                requireNotNull(CountryFootballRulesRegistry.resolve(it.country)).canonicalCountry
            }
            assertEquals(associations.size, associations.toSet().size)
        }
    }

    @Test
    fun `impossible world draw fails closed instead of throwing`() {
        val countries = listOf(
            "Inglaterra", "Espanha", "Itália", "Alemanha", "França", "Portugal",
            "Países Baixos", "Bélgica", "Turquia", "Escócia", "Áustria", "Suíça"
        )
        val teams = buildList {
            repeat(9) { index ->
                add(Team(300_000L + index, "England $index", "London", "EN", "Inglaterra", 1))
            }
            repeat(23) { index ->
                val country = countries[1 + (index % (countries.size - 1))]
                add(Team(301_000L + index, "Other $index", "City", country.take(2), country, 1))
            }
        }

        assertTrue(WorldClubDrawEngine.drawGroups(2029, teams).isEmpty())
    }

    @Test
    fun `world quota order follows sporting result instead of country alphabet`() {
        val teams = worldUniverse()
        val conmebol = teams.filter {
            CountryFootballRulesRegistry.confederationFor(it.country) == FootballConfederation.CONMEBOL
        }
        val venezuela = conmebol.single { it.country == "Venezuela" }
        val argentina = conmebol.single { it.country == "Argentina" }
        val snapshot = standings(teams)
            .filterNot { it.teamId in conmebol.map(Team::id) } +
            conmebol.map { team ->
                val points = when (team.id) {
                    venezuela.id -> 100
                    argentina.id -> 1
                    else -> 80 - (team.id % 10L).toInt()
                }
                standing(2032, team, 1, points)
            }

        val field = requireNotNull(SuperMundialQualificationRules.selectField(2033, teams, snapshot))
        val selectedConmebol = field.teams.filter {
            CountryFootballRulesRegistry.confederationFor(it.country) == FootballConfederation.CONMEBOL
        }

        assertTrue(selectedConmebol.any { it.id == venezuela.id })
        assertFalse(selectedConmebol.any { it.id == argentina.id })
    }

    private fun uefaCandidates(count: Int): List<Team> {
        val countries = listOf(
            "Inglaterra", "Espanha", "Itália", "Alemanha", "França", "Portugal",
            "Países Baixos", "Bélgica", "Turquia", "Escócia", "Áustria", "Suíça",
            "Dinamarca", "Noruega", "Suécia", "Polônia", "Tchéquia", "Croácia", "Sérvia", "Grécia"
        )
        return (0 until count).map { index ->
            val country = countries[index % countries.size]
            Team(index + 1L, "UEFA review ${index + 1}", "City", country.take(2), country, 1)
        }
    }

    private fun worldUniverse(): List<Team> {
        val countries = buildList {
            addAll(listOf(
                "Inglaterra", "Espanha", "Itália", "Alemanha", "França", "Portugal",
                "Países Baixos", "Bélgica", "Turquia", "Escócia", "Áustria", "Suíça", "Dinamarca"
            ))
            addAll(listOf(
                "Brasil", "Argentina", "Colômbia", "Chile", "Uruguai", "Paraguai", "Equador", "Peru", "Bolívia", "Venezuela"
            ))
            addAll(listOf("Japão", "Coreia do Sul", "Arábia Saudita", "Emirados Árabes Unidos", "Catar"))
            addAll(listOf("Egito", "Egito", "Marrocos", "Tunísia", "África do Sul"))
            addAll(listOf("México", "Estados Unidos / Canadá", "Costa Rica", "Guatemala", "Honduras"))
        }
        return countries.mapIndexed { index, country ->
            Team(
                id = 200_000L + index,
                name = "World review ${index + 1}",
                city = "City",
                state = country.take(2),
                country = country,
                division = 1,
                rating = 70
            )
        }
    }

    private fun drawFieldWithAssociationAlias(): List<Team> {
        val countries = buildList {
            addAll(listOf(
                "Inglaterra", "Espanha", "Itália", "Alemanha", "França", "Portugal",
                "Países Baixos", "Bélgica", "Turquia", "Escócia", "Áustria", "Suíça",
                "Dinamarca", "Noruega"
            ))
            addAll(listOf("Brasil", "Argentina", "Colômbia", "Chile", "Uruguai", "Paraguai"))
            addAll(listOf("Japão", "Coreia do Sul", "Arábia Saudita", "Emirados Árabes Unidos"))
            addAll(listOf("Egito", "Marrocos", "Tunísia", "África do Sul"))
            addAll(listOf("Estados Unidos / Canadá", "Estados Unidos / México", "México", "Costa Rica"))
        }
        return countries.mapIndexed { index, country ->
            Team(400_000L + index, "Draw review ${index + 1}", "City", country.take(2), country, 1)
        }
    }

    private fun standings(teams: List<Team>): List<GlobalLeagueStanding> =
        teams
            .filter { CountryFootballRulesRegistry.isContinentalCompetitionEligible(it.country) }
            .groupBy { requireNotNull(CountryFootballRulesRegistry.resolve(it.country)).canonicalCountry }
            .flatMap { (_, associationTeams) ->
                associationTeams.sortedBy { it.id }.mapIndexed { index, team ->
                    standing(2028, team, index + 1, 90 - index)
                }
            }

    private fun canonicalAssociation(country: String): String =
        requireNotNull(CountryFootballRulesRegistry.resolve(country)).canonicalCountry

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
