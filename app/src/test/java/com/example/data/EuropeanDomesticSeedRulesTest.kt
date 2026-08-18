package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EuropeanDomesticSeedRulesTest {

    @Test
    fun `factual top flight overrides stale legacy first division size only`() {
        assertEquals(
            listOf(18, 16, 18),
            EuropeanDomesticSeedRules.resolveDivisionSizes("Bélgica", listOf(16, 16, 18))
        )
        assertEquals(
            listOf(18, 20, 38),
            EuropeanDomesticSeedRules.resolveDivisionSizes("Turquia", listOf(19, 20, 38))
        )
        assertEquals(
            listOf(14, 16, 18),
            EuropeanDomesticSeedRules.resolveDivisionSizes("Sérvia", listOf(16, 16, 18))
        )
    }

    @Test
    fun `all modeled UEFA associations expose complete factual top flight names`() {
        val modeledUefaCountries = CountryFootballRulesRegistry.knownCanonicalCountries
            .filter { CountryFootballRulesRegistry.confederationFor(it) == FootballConfederation.UEFA }

        assertEquals(20, modeledUefaCountries.size)
        modeledUefaCountries.forEach { country ->
            val baseline = requireNotNull(EuropeanDomesticBaseline2026_27.forCountry(country))
            val clubs = requireNotNull(EuropeanDomesticSeedRules.topFlightClubNames(country))
            assertEquals(baseline.topDivisionClubCount, clubs.size)
            assertTrue(EuropeanDomesticSeedRules.hasCompleteTopFlight(country))
        }
    }

    @Test
    fun `non UEFA or unknown country keeps legacy sizes unchanged`() {
        val brazil = listOf(20, 20, 20, 96, 15)
        val unknown = listOf(20, 20)

        assertEquals(brazil, EuropeanDomesticSeedRules.resolveDivisionSizes("Brasil", brazil))
        assertEquals(unknown, EuropeanDomesticSeedRules.resolveDivisionSizes("País Inexistente", unknown))
    }
}
