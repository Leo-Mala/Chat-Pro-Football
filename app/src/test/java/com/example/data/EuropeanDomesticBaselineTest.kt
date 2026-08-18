package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EuropeanDomesticBaselineTest {

    @Test
    fun `baseline covers exactly the twenty UEFA associations currently modeled`() {
        val modeledUefaCountries = CountryFootballRulesRegistry.knownCanonicalCountries
            .filter { CountryFootballRulesRegistry.confederationFor(it) == FootballConfederation.UEFA }
            .toSet()
        val baselineCountries = EuropeanDomesticBaseline2026_27.associations.map { it.country }.toSet()

        assertEquals(20, modeledUefaCountries.size)
        assertEquals(modeledUefaCountries, baselineCountries)
    }

    @Test
    fun `verified top flights have complete unique 2026 27 club lists`() {
        val expectedCounts = linkedMapOf(
            "Inglaterra" to 20,
            "Espanha" to 20,
            "Itália" to 20,
            "Alemanha" to 18,
            "França" to 18,
            "Países Baixos" to 18,
            "Bélgica" to 18,
            "Turquia" to 18,
            "Escócia" to 12,
            "Áustria" to 12,
            "Suíça" to 12,
            "Dinamarca" to 12,
            "Noruega" to 16,
            "Polônia" to 18,
            "Croácia" to 10,
            "Sérvia" to 14
        )

        assertEquals(expectedCounts.keys, EuropeanDomesticBaseline2026_27.verifiedTopFlightCountries)
        expectedCounts.forEach { (country, expectedCount) ->
            val baseline = EuropeanDomesticBaseline2026_27.forCountry(country)
            assertNotNull(baseline)
            requireNotNull(baseline)
            assertEquals(EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT, baseline.coverage)
            assertEquals(expectedCount, baseline.topDivisionClubCount)
            assertEquals(expectedCount, baseline.verifiedTopFlightClubs.size)
            assertEquals(expectedCount, baseline.verifiedTopFlightClubs.distinct().size)
        }
    }

    @Test
    fun `only four UEFA associations remain structure only`() {
        val remaining = EuropeanDomesticBaseline2026_27.associations
            .filter { it.coverage == EuropeanDomesticCoverage.STRUCTURE_ONLY }
            .map { it.country }
            .toSet()

        assertEquals(setOf("Portugal", "Suécia", "Tchéquia", "Grécia"), remaining)
    }

    @Test
    fun `England and Spain default data use exactly the verified top flight sets`() {
        listOf("Inglaterra", "Espanha").forEach { country ->
            val baseline = requireNotNull(EuropeanDomesticBaseline2026_27.forCountry(country))
            val seededTopFlight = DefaultData.getTeamsForCountry(country)
                .filter { it.division == 1 }
                .map { StableTeamIdentityRegistry.canonicalNameFor(country, it.name) ?: it.name }

            assertEquals(baseline.topDivisionClubCount, seededTopFlight.size)
            assertEquals(baseline.verifiedTopFlightClubs.toSet(), seededTopFlight.toSet())
        }
    }

    @Test
    fun `promoted and relegated clubs preserve identity instead of list position`() {
        assertEquals(27L, GlobalFootballSystem.getGlobalId("Inglaterra", "Coventry City"))
        assertEquals(29L, GlobalFootballSystem.getGlobalId("Inglaterra", "Hull City"))
        assertEquals(24L, GlobalFootballSystem.getGlobalId("Inglaterra", "Ipswich Town"))
        assertEquals(9L, GlobalFootballSystem.getGlobalId("Inglaterra", "West Ham United"))

        assertEquals(206L, GlobalFootballSystem.getGlobalId("Espanha", "Athletic Club"))
        assertEquals(228L, GlobalFootballSystem.getGlobalId("Espanha", "Elche CF"))
        assertEquals(244L, GlobalFootballSystem.getGlobalId("Espanha", "Málaga CF"))
        assertEquals(243L, GlobalFootballSystem.getGlobalId("Espanha", "RC Deportivo"))
        assertEquals(204L, GlobalFootballSystem.getGlobalId("Espanha", "Girona FC"))
    }

    @Test
    fun `stable identities reverse materialize current division without changing team id`() {
        val coventry = requireNotNull(GlobalFootballSystem.getTeamByGlobalId(27L))
        val westHam = requireNotNull(GlobalFootballSystem.getTeamByGlobalId(9L))
        val elche = requireNotNull(GlobalFootballSystem.getTeamByGlobalId(228L))
        val girona = requireNotNull(GlobalFootballSystem.getTeamByGlobalId(204L))

        assertEquals("Coventry City", coventry.name)
        assertEquals(1, coventry.division)
        assertEquals(27L, coventry.id)

        assertEquals("West Ham United", westHam.name)
        assertEquals(2, westHam.division)
        assertEquals(9L, westHam.id)

        assertEquals("Elche CF", elche.name)
        assertEquals(1, elche.division)
        assertEquals(228L, elche.id)

        assertEquals("Girona FC", girona.name)
        assertEquals(2, girona.division)
        assertEquals(204L, girona.id)
    }

    @Test
    fun `stable registry ids and aliases are collision free`() {
        val ids = StableTeamIdentityRegistry.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.all { it in 1 until GlobalFootballSystem.VIRTUAL_TEAM_ID_FLOOR })

        assertEquals(2L, StableTeamIdentityRegistry.idFor("Inglaterra", "Arsenal"))
        assertEquals(202L, StableTeamIdentityRegistry.idFor("Espanha", "Barcelona"))
        assertEquals(206L, StableTeamIdentityRegistry.idFor("Espanha", "Athletic Bilbao"))
        assertFalse(StableTeamIdentityRegistry.isStableRealClub("Brasil", "Flamengo"))
    }

    @Test
    fun `all England and Spain seeded templates resolve to unique reversible ids`() {
        listOf("Inglaterra", "Espanha").forEach { country ->
            val templates = DefaultData.getTeamsForCountry(country)
            val resolved = templates.map { template ->
                val id = GlobalFootballSystem.getGlobalId(country, template.name)
                id to template.name
            }

            assertEquals(
                "$country possui colisão de teamId após aplicar registry estável",
                resolved.size,
                resolved.map { it.first }.distinct().size
            )

            resolved.forEach { (id, expectedName) ->
                val materialized = requireNotNull(GlobalFootballSystem.getTeamByGlobalId(id))
                assertEquals(expectedName, materialized.name)
                assertEquals(country, materialized.country)
            }
        }
    }
}
