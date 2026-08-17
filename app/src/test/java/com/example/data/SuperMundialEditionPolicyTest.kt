package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperMundialEditionPolicyTest {

    @Test
    fun `official cycle starts in 2025 and repeats every four years`() {
        assertFalse(SuperMundialSystem.isSuperMundialSeason(2024))
        assertTrue(SuperMundialSystem.isSuperMundialSeason(2025))
        assertFalse(SuperMundialSystem.isSuperMundialSeason(2026))
        assertFalse(SuperMundialSystem.isSuperMundialSeason(2027))
        assertFalse(SuperMundialSystem.isSuperMundialSeason(2028))
        assertTrue(SuperMundialSystem.isSuperMundialSeason(2029))
        assertFalse(SuperMundialSystem.isSuperMundialSeason(2030))
        assertFalse(SuperMundialSystem.isSuperMundialSeason(2031))
        assertFalse(SuperMundialSystem.isSuperMundialSeason(2032))
        assertTrue(SuperMundialSystem.isSuperMundialSeason(2033))
        assertFalse(SuperMundialSystem.isSuperMundialSeason(2034))
        assertTrue(SuperMundialSystem.isSuperMundialSeason(2037))

        for (season in 1900..2300) {
            val expected = season >= 2025 && (season - 2025) % 4 == 0
            assertEquals("Periodicidade incorreta na temporada $season", expected, SuperMundialSystem.isSuperMundialSeason(season))
        }
    }

    @Test
    fun `host rotation is deterministic and exhausts eligible countries before repeating`() {
        val teams = hostUniverse()
        val firstPass = SuperMundialEditionPolicy.editionsThrough(2041, teams)
        val secondPass = SuperMundialEditionPolicy.editionsThrough(2041, teams.shuffled())

        assertEquals(firstPass, secondPass)
        assertEquals(listOf(2025, 2029, 2033, 2037, 2041), firstPass.map { it.season })

        val firstFourHosts = firstPass.take(4).map { it.hostCountry }
        assertEquals(4, firstFourHosts.toSet().size)
        firstPass.zipWithNext().forEach { (previous, next) ->
            assertNotEquals("Edições consecutivas não podem repetir sede", previous.hostCountry, next.hostCountry)
        }

        // Quatro países: a quinta edição pode reutilizar a primeira sede, mas nunca a anterior.
        assertEquals(firstPass.first().hostCountry, firstPass[4].hostCountry)
        assertNotEquals(firstPass[3].hostCountry, firstPass[4].hostCountry)
    }

    @Test
    fun `host club belongs to host country and is the deterministic strongest eligible club`() {
        val teams = hostUniverse()
        val edition = requireNotNull(SuperMundialEditionPolicy.editionForSeason(2029, teams))
        val hostClubs = teams.filter { it.country == edition.hostCountry }
        val expectedHost = hostClubs.sortedWith(compareByDescending<Team> { it.rating }.thenBy { it.id }).first()

        assertEquals(expectedHost.id, edition.hostTeamId)
        assertEquals(expectedHost.name, edition.hostTeamName)
        assertEquals(edition.hostCountry, expectedHost.country)
        assertEquals(edition, SuperMundialEditionPolicy.editionForSeason(2029, teams))
    }

    @Test
    fun `real persisted clubs fill the 32 team field before any virtual fallback`() {
        val teams = buildList {
            val countries = listOf("Argentina", "Brasil", "Espanha", "Inglaterra")
            var id = 1L
            countries.forEachIndexed { countryIndex, country ->
                repeat(10) { clubIndex ->
                    add(
                        Team(
                            id = id++,
                            name = "Clube $country $clubIndex",
                            city = "Cidade $clubIndex",
                            state = country.take(2),
                            country = country,
                            division = 1,
                            rating = 70 + countryIndex * 5 + clubIndex
                        )
                    )
                }
            }
        }

        val participants = SuperMundialSystem.selectParticipants(
            season = 2029,
            allTeams = teams,
            userTeamId = teams.first().id
        )
        val host = requireNotNull(SuperMundialEditionPolicy.hostTeamForSeason(2029, teams))

        assertEquals(32, participants.size)
        assertEquals(32, participants.map { it.id }.toSet().size)
        assertTrue(participants.any { it.id == host.id })
        assertTrue("Com 40 clubes reais, nenhum participante precisa ser virtual", participants.all { p -> teams.any { it.id == p.id } })

        val fixtures = SuperMundialSystem.generateGroupStageFixtures(2029, teams, teams.first().id)
        assertEquals(48, fixtures.size)
        assertEquals(32, fixtures.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.toSet().size)
        assertTrue(fixtures.all { it.matchSlot == MatchSlot.MIDWEEK })
        assertEquals(setOf(42, 43, 44), fixtures.map { it.week }.toSet())
    }

    @Test
    fun `non edition seasons never create participants or fixtures`() {
        val teams = hostUniverse()
        for (season in listOf(2026, 2027, 2028, 2030)) {
            assertTrue(SuperMundialSystem.selectParticipants(season, teams, teams.first().id).isEmpty())
            assertTrue(SuperMundialSystem.generateGroupStageFixtures(season, teams, teams.first().id).isEmpty())
        }
    }

    private fun hostUniverse(): List<Team> = listOf(
        Team(1L, "Argentina A", "Buenos Aires", "AR", "Argentina", 1, rating = 81),
        Team(2L, "Argentina B", "Córdoba", "AR", "Argentina", 1, rating = 78),
        Team(3L, "Brasil A", "São Paulo", "SP", "Brasil", 1, rating = 84),
        Team(4L, "Brasil B", "Belo Horizonte", "MG", "Brasil", 1, rating = 90),
        Team(5L, "Espanha A", "Madrid", "ES", "Espanha", 1, rating = 88),
        Team(6L, "Espanha B", "Barcelona", "ES", "Espanha", 1, rating = 86),
        Team(7L, "Inglaterra A", "London", "EN", "Inglaterra", 1, rating = 91),
        Team(8L, "Inglaterra B", "Manchester", "EN", "Inglaterra", 1, rating = 89)
    )
}
