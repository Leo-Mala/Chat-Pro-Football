package com.example.data

import com.example.usecase.GlobalLeagueSimulationUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldFootballRulesHardeningTest {

    @Test
    fun `legacy CompetitionType recognizes every code the game still produces`() {
        val expected = mapOf(
            "SERIE_A" to CompetitionType.SERIE_A,
            "DIV_1" to CompetitionType.SERIE_A,
            "SERIE_B" to CompetitionType.SERIE_B,
            "DIV_2" to CompetitionType.SERIE_B,
            "SERIE_C" to CompetitionType.SERIE_C,
            "DIV_3" to CompetitionType.SERIE_C,
            "SERIE_D" to CompetitionType.SERIE_D,
            "DIV_4" to CompetitionType.SERIE_D,
            "CUP" to CompetitionType.CUP,
            "COPA" to CompetitionType.CUP,
            "STATE" to CompetitionType.STATE,
            "ESTADUAL" to CompetitionType.STATE,
            "CONTINENTAL_T1" to CompetitionType.CONTINENTAL_T1,
            "LIBERTADORES" to CompetitionType.CONTINENTAL_T1,
            "CONTINENTAL_T2" to CompetitionType.CONTINENTAL_T2,
            "SULAMERICANA" to CompetitionType.CONTINENTAL_T2,
            "CONTINENTAL_T3" to CompetitionType.CONTINENTAL_T3,
            "WORLD_CUP" to CompetitionType.WORLD_CUP,
            "WORLD" to CompetitionType.WORLD_CUP
        )

        expected.forEach { (code, competitionType) ->
            assertEquals(competitionType, CompetitionType.fromCodeOrNull(code))
            assertEquals(competitionType, CompetitionType.fromCode(code))
        }

        assertNull(CompetitionType.fromCodeOrNull("NOT_A_REAL_COMPETITION"))
        assertThrows(IllegalArgumentException::class.java) {
            CompetitionType.fromCode("NOT_A_REAL_COMPETITION")
        }
    }

    @Test
    fun `dedicated CONMEBOL registry metadata follows the dedicated engine calendar`() {
        val libertadores = CompetitionRulesRegistry.continentalCatalogFor(FootballConfederation.CONMEBOL)
            .single { it.identity == CompetitionIdentity.CONMEBOL_LIBERTADORES }
        val sudamericana = CompetitionRulesRegistry.continentalCatalogFor(FootballConfederation.CONMEBOL)
            .single { it.identity == CompetitionIdentity.CONMEBOL_SUDAMERICANA }

        listOf(libertadores, sudamericana).forEach { competition ->
            assertEquals(ConmebolCompetitionSystem.GROUP_WEEKS.first(), competition.startWeek)
            assertEquals(ConmebolCompetitionSystem.FINAL_WEEK, competition.endWeek)
            assertFalse(competition.startWeek == 33 && competition.endWeek == 36)
        }
    }

    @Test
    fun `typed continental catalog preserves missing tiers instead of cloning tier one`() {
        val expectedCounts = mapOf(
            FootballConfederation.UEFA to 3,
            FootballConfederation.CONMEBOL to 2,
            FootballConfederation.CONCACAF to 3,
            FootballConfederation.CAF to 2,
            FootballConfederation.AFC to 3,
            FootballConfederation.OFC to 1
        )

        expectedCounts.forEach { (confederation, expectedCount) ->
            val set = CompetitionRulesRegistry.continentalCompetitionSet(confederation)
            assertEquals(expectedCount, set.all.size)
            assertEquals(expectedCount, set.all.map { it.code }.toSet().size)
            assertEquals(expectedCount, CompetitionRulesRegistry.continentalCatalogFor(confederation).size)
        }

        val ofc = CompetitionRulesRegistry.continentalCompetitionSet(FootballConfederation.OFC)
        assertEquals("OFC_CL", ofc.tier1?.code)
        assertNull(ofc.tier2)
        assertNull(ofc.tier3)
        assertNull(CompetitionRulesRegistry.continentalCatalogCodesOrNull(FootballConfederation.OFC))
        assertThrows(IllegalArgumentException::class.java) {
            CompetitionRulesRegistry.continentalCatalogCodes(FootballConfederation.OFC)
        }

        val conmebol = CompetitionRulesRegistry.continentalCompetitionSet(FootballConfederation.CONMEBOL)
        assertEquals("CONMEBOL_CL", conmebol.tier1?.code)
        assertEquals("CONMEBOL_CS", conmebol.tier2?.code)
        assertNull(conmebol.tier3)
        assertNull(CompetitionRulesRegistry.continentalCatalogCodesOrNull(FootballConfederation.CONMEBOL))

        val uefaLegacyAdapter = requireNotNull(
            CompetitionRulesRegistry.continentalCatalogCodesOrNull(FootballConfederation.UEFA)
        )
        assertEquals(Triple("UEFA_CL", "UEFA_EL", "UEFA_ECL"), uefaLegacyAdapter)
    }

    @Test
    fun `only national associations are eligible for new domestic and continental rules`() {
        listOf("Brasil", "Inglaterra", "Japão").forEach { country ->
            val rules = requireNotNull(CountryFootballRulesRegistry.resolve(country))
            assertEquals(CountryRuleKind.NATIONAL_ASSOCIATION, rules.kind)
            assertTrue(CountryFootballRulesRegistry.isDomesticCompetitionEligible(country))
            assertTrue(CountryFootballRulesRegistry.isContinentalCompetitionEligible(country))
        }

        val legacyAggregates = listOf(
            "América Central",
            "África",
            "Ásia",
            "Oceania",
            "África / Ásia / Oceania"
        )
        legacyAggregates.forEach { country ->
            val rules = requireNotNull(CountryFootballRulesRegistry.resolve(country))
            assertEquals(CountryRuleKind.LEGACY_AGGREGATE, rules.kind)
            assertFalse(CountryFootballRulesRegistry.isDomesticCompetitionEligible(country))
            assertFalse(CountryFootballRulesRegistry.isContinentalCompetitionEligible(country))
        }

        assertFalse(CountryFootballRulesRegistry.isDomesticCompetitionEligible("Mundial"))
        assertFalse(CountryFootballRulesRegistry.isContinentalCompetitionEligible("Mundial"))
        assertFalse(CountryFootballRulesRegistry.isDomesticCompetitionEligible("País Inexistente"))
        assertFalse(CountryFootballRulesRegistry.isContinentalCompetitionEligible("País Inexistente"))
    }

    @Test
    fun `legacy aliases remain resolvable without becoming synthetic associations`() {
        val usaMexico = requireNotNull(CountryFootballRulesRegistry.resolve("Estados Unidos / México"))
        assertEquals("Estados Unidos / Canadá", usaMexico.canonicalCountry)
        assertEquals(CountryRuleKind.NATIONAL_ASSOCIATION, usaMexico.kind)
        assertEquals(FootballConfederation.CONCACAF, usaMexico.confederation)
        assertTrue(CountryFootballRulesRegistry.isDomesticCompetitionEligible("Estados Unidos / México"))
        assertTrue(CountryFootballRulesRegistry.isContinentalCompetitionEligible("Estados Unidos / México"))

        assertEquals("CONCACAF", GlobalFootballSystem.getConfederationForCountry("América Central"))
        assertEquals("CAF", GlobalFootballSystem.getConfederationForCountry("África"))
        assertEquals("AFC", GlobalFootballSystem.getConfederationForCountry("Ásia"))
        assertEquals("OFC", GlobalFootballSystem.getConfederationForCountry("Oceania"))
        assertEquals("MIXED", GlobalFootballSystem.getConfederationForCountry("África / Ásia / Oceania"))
    }

    @Test
    fun `new typed country API preserves continental absence that Triple cannot express`() {
        val brasil = requireNotNull(
            GlobalFootballSystem.getContinentalCompetitionSetForCountryOrNull("Brasil")
        )
        assertEquals(2, brasil.all.size)
        assertEquals("CONMEBOL_CL", brasil.tier1?.code)
        assertEquals("CONMEBOL_CS", brasil.tier2?.code)
        assertNull(brasil.tier3)

        val england = requireNotNull(
            GlobalFootballSystem.getContinentalCompetitionSetForCountryOrNull("Inglaterra")
        )
        assertEquals(3, england.all.size)

        assertNull(GlobalFootballSystem.getContinentalCompetitionSetForCountryOrNull("Oceania"))
        assertNull(GlobalFootballSystem.getContinentalCompetitionSetForCountryOrNull("Mundial"))
        assertNull(GlobalFootballSystem.getContinentalCompetitionSetForCountryOrNull("País Inexistente"))

        @Suppress("DEPRECATION")
        assertNull(GlobalFootballSystem.getContinentalTournamentsForCountryOrNull("Brasil"))
        @Suppress("DEPRECATION")
        assertNull(GlobalFootballSystem.getContinentalTournamentsForCountryOrNull("Oceania"))
    }

    @Test
    fun `global domestic standings exclude virtual unknown and aggregate identities`() {
        fun pair(country: String, firstId: Long): List<Team> = listOf(
            team(firstId, "$country A", country, 80),
            team(firstId + 1, "$country B", country, 70)
        )

        val teams =
            pair("Brasil", 1L) +
                pair("Inglaterra", 10L) +
                pair("Japão", 20L) +
                pair("Mundial", 30L) +
                pair("País Inexistente", 40L) +
                pair("África", 50L)

        val standings = GlobalLeagueSimulationUseCase().buildSeasonStandings(
            season = 2026,
            teams = teams,
            detailedFixtures = emptyList(),
            detailedCountry = "Brasil"
        )

        assertEquals(setOf("Brasil", "Inglaterra", "Japão"), standings.map { it.country }.toSet())
        assertEquals(6, standings.size)
        assertTrue(standings.none { it.country == "Mundial" })
        assertTrue(standings.none { it.country == "País Inexistente" })
        assertTrue(standings.none { it.country == "África" })
    }

    @Test
    fun `UEFA catalog stays pending and is not naively treated as global knockout`() {
        assertEquals(
            ConfederationEngineKind.DEDICATED_CONMEBOL,
            CompetitionRulesRegistry.engineForConfederation(FootballConfederation.CONMEBOL)
        )
        assertEquals(
            ConfederationEngineKind.LEGACY_GENERIC,
            CompetitionRulesRegistry.engineForConfederation(FootballConfederation.UEFA)
        )
        assertTrue(
            CompetitionRulesRegistry.continentalCatalogFor(FootballConfederation.UEFA)
                .all { it.implementationStatus == CompetitionImplementationStatus.REAL_RULES_NOT_IMPLEMENTED }
        )

        assertFalse(CompetitionRules.isKnockoutCompetition("UEFA_CL"))
        assertFalse(CompetitionRules.isKnockoutCompetition("UEFA_EL"))
        assertFalse(CompetitionRules.isKnockoutCompetition("UEFA_ECL"))
        assertTrue(CompetitionRules.isKnockoutCompetition("CONTINENTAL_T3"))
    }

    @Test
    fun `Super Mundial cadence remains unchanged`() {
        assertEquals(42, SuperMundialSystem.GROUP_WEEK_1)
        assertEquals(48, SuperMundialSystem.FINAL_WEEK)
        assertEquals(48, GameCalendar.WEEKS_PER_SEASON)
        assertTrue(SuperMundialEditionPolicy.isEditionSeason(2025))
        assertTrue(SuperMundialEditionPolicy.isEditionSeason(2029))
        assertFalse(SuperMundialEditionPolicy.isEditionSeason(2026))
    }

    private fun team(
        id: Long,
        name: String,
        country: String,
        rating: Int,
        division: Int = 1
    ) = Team(
        id = id,
        name = name,
        city = name,
        state = "ST",
        country = country,
        division = division,
        rating = rating
    )
}
