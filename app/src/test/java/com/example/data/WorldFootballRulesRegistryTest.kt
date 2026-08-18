package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WorldFootballRulesRegistryTest {

    @Test
    fun `known countries map to typed confederations`() {
        assertEquals(FootballConfederation.CONMEBOL, CountryFootballRulesRegistry.confederationFor("Brasil"))
        assertEquals(FootballConfederation.CONMEBOL, CountryFootballRulesRegistry.confederationFor("Argentina"))
        assertEquals(FootballConfederation.UEFA, CountryFootballRulesRegistry.confederationFor("Inglaterra"))
        assertEquals(FootballConfederation.UEFA, CountryFootballRulesRegistry.confederationFor("Espanha"))
        assertEquals(FootballConfederation.CONCACAF, CountryFootballRulesRegistry.confederationFor("México"))
        assertEquals(FootballConfederation.AFC, CountryFootballRulesRegistry.confederationFor("Japão"))
        assertEquals(FootballConfederation.CAF, CountryFootballRulesRegistry.confederationFor("Egito"))
        assertEquals(FootballConfederation.OFC, CountryFootballRulesRegistry.confederationFor("Oceania"))
    }

    @Test
    fun `all persisted normal countries are explicit and agree with legacy metadata`() {
        val globalCountries = GlobalFootballSystem.countries.map { it.name }.toSet()
        assertEquals(globalCountries, CountryFootballRulesRegistry.knownCanonicalCountries)

        GlobalFootballSystem.countries.forEach { country ->
            val typed = CountryFootballRulesRegistry.confederationFor(country.name)
            assertNotNull("Missing typed rule for ${country.name}", typed)
            assertEquals(country.confederation, typed?.code)
        }
    }

    @Test
    fun `unknown and Mundial never become CONMEBOL`() {
        assertNull(CountryFootballRulesRegistry.resolve("País Inexistente"))
        assertNull(CountryFootballRulesRegistry.confederationFor("País Inexistente"))
        assertEquals("UNKNOWN", GlobalFootballSystem.getConfederationForCountry("País Inexistente"))
        assertFalse(GlobalFootballSystem.getConfederationForCountry("País Inexistente") == "CONMEBOL")

        val mundial = CountryFootballRulesRegistry.resolve("Mundial")
        assertNotNull(mundial)
        assertEquals(CountryRuleKind.VIRTUAL_WORLD, mundial?.kind)
        assertNull(mundial?.confederation)
        assertFalse(mundial?.domesticCompetitionsAllowed ?: true)
        assertEquals("UNKNOWN", GlobalFootballSystem.getConfederationForCountry("Mundial"))
        assertFalse(GlobalFootballSystem.getConfederationForCountry("Mundial") == "CONMEBOL")
    }

    @Test
    fun `unknown and Mundial receive no continental route`() {
        assertNull(GlobalFootballSystem.getContinentalTournamentsForCountryOrNull("País Inexistente"))
        assertNull(GlobalFootballSystem.getContinentalTournamentsForCountryOrNull("Mundial"))
        assertFalse(CountryFootballRulesRegistry.isContinentalCompetitionEligible("País Inexistente"))
        assertFalse(CountryFootballRulesRegistry.isContinentalCompetitionEligible("Mundial"))
    }

    @Test
    fun `unknown and Mundial cannot accidentally generate continental fixtures`() {
        fun teams(country: String): List<Team> = (1L..16L).map { id ->
            Team(
                id = id,
                name = "$country Clube $id",
                city = "Cidade $id",
                state = "ST",
                country = country,
                division = 1,
                rating = 80 - id.toInt()
            )
        }

        val unknownTeams = teams("País Inexistente")
        val unknownFixtures = CupCompetitionSystem.generateSeasonOpeningFixtures(
            season = 2026,
            teams = unknownTeams,
            userTeamId = 1L,
            userCountry = "País Inexistente"
        )
        assertTrue(unknownFixtures.none { it.competitionType.startsWith("CONTINENTAL_") })
        assertTrue(unknownFixtures.none { it.competitionType == "COPA" })

        val worldTeams = teams("Mundial")
        val worldFixtures = CupCompetitionSystem.generateSeasonOpeningFixtures(
            season = 2026,
            teams = worldTeams,
            userTeamId = 1L,
            userCountry = "Mundial"
        )
        assertTrue(worldFixtures.none { it.competitionType.startsWith("CONTINENTAL_") })
        assertTrue(worldFixtures.none { it.competitionType == "COPA" })
    }

    @Test
    fun `all six confederations are typed and have catalog identity`() {
        assertEquals(
            setOf(
                FootballConfederation.UEFA,
                FootballConfederation.CONMEBOL,
                FootballConfederation.CONCACAF,
                FootballConfederation.CAF,
                FootballConfederation.AFC,
                FootballConfederation.OFC
            ),
            FootballConfederation.entries.toSet()
        )
        FootballConfederation.entries.forEach { confederation ->
            assertTrue(
                "Missing competition catalog for ${confederation.code}",
                CompetitionRulesRegistry.continentalCatalogFor(confederation).isNotEmpty()
            )
        }
    }

    @Test
    fun `legacy country aliases are explicit and deterministic`() {
        val cases = mapOf(
            "Estados Unidos / México" to "CONCACAF",
            "América Central" to "CONCACAF",
            "África" to "CAF",
            "Ásia" to "AFC",
            "Oceania" to "OFC",
            "África / Ásia / Oceania" to "MIXED"
        )

        cases.forEach { (country, expected) ->
            val first = GlobalFootballSystem.getConfederationForCountry(country)
            val second = GlobalFootballSystem.getConfederationForCountry("  $country  ")
            assertEquals(expected, first)
            assertEquals(first, second)
        }
    }

    @Test
    fun `legacy competition codes and aliases resolve without changing persisted codes`() {
        val expected = mapOf(
            "SERIE_A" to CompetitionIdentity.DOMESTIC_LEAGUE_1,
            "DIV_1" to CompetitionIdentity.DOMESTIC_LEAGUE_1,
            "SERIE_B" to CompetitionIdentity.DOMESTIC_LEAGUE_2,
            "DIV_2" to CompetitionIdentity.DOMESTIC_LEAGUE_2,
            "SERIE_C" to CompetitionIdentity.DOMESTIC_LEAGUE_3,
            "DIV_3" to CompetitionIdentity.DOMESTIC_LEAGUE_3,
            "SERIE_D" to CompetitionIdentity.DOMESTIC_LEAGUE_4,
            "DIV_4" to CompetitionIdentity.DOMESTIC_LEAGUE_4,
            "COPA" to CompetitionIdentity.NATIONAL_CUP,
            "CUP" to CompetitionIdentity.NATIONAL_CUP,
            "CONTINENTAL_T1" to CompetitionIdentity.LEGACY_CONTINENTAL_T1,
            "CONTINENTAL_T2" to CompetitionIdentity.LEGACY_CONTINENTAL_T2,
            "CONTINENTAL_T3" to CompetitionIdentity.LEGACY_CONTINENTAL_T3,
            "WORLD_CUP" to CompetitionIdentity.SUPER_MUNDIAL
        )

        expected.forEach { (code, identity) ->
            assertEquals(identity, CompetitionRulesRegistry.resolve(code)?.identity)
        }
        assertEquals(
            CompetitionIdentity.LEGACY_CONTINENTAL_T1,
            CompetitionRulesRegistry.resolve("CONTINENTAL_T1_GP_A")?.identity
        )
        assertEquals(
            CompetitionIdentity.SUPER_MUNDIAL,
            CompetitionRulesRegistry.resolve("WORLD_CUP_GP_H")?.identity
        )
    }

    @Test
    fun `unknown CompetitionType no longer silently becomes Serie A`() {
        assertNull(CompetitionType.fromCodeOrNull("NOT_A_REAL_COMPETITION"))
        try {
            CompetitionType.fromCode("NOT_A_REAL_COMPETITION")
            fail("Unknown competition code must fail explicitly")
        } catch (_: IllegalArgumentException) {
            // Expected: explicit failure replaces the historical SERIE_A fallback.
        }

        assertEquals(CompetitionType.SERIE_A, CompetitionType.fromCode("SERIE_A"))
        assertEquals(CompetitionType.SERIE_A, CompetitionType.fromCode("DIV_1"))
        assertEquals(CompetitionType.SERIE_D, CompetitionType.fromCode("DIV_4"))
    }

    @Test
    fun `structural rule resolution is deterministic`() {
        repeat(20) {
            assertEquals(
                FootballConfederation.CONMEBOL,
                CountryFootballRulesRegistry.confederationFor("Brasil")
            )
            assertEquals(
                CompetitionIdentity.LEGACY_CONTINENTAL_T1,
                CompetitionRulesRegistry.resolve("CONTINENTAL_T1")?.identity
            )
        }
    }

    @Test
    fun `registry competition code remains valid for fixture validator`() {
        val code = requireNotNull(CompetitionRulesRegistry.resolve("CONTINENTAL_T1")).code
        val fixture = Fixture(
            season = 2026,
            week = 1,
            matchSlot = MatchSlot.MIDWEEK,
            homeTeamId = 10L,
            awayTeamId = 20L,
            competitionType = code
        )
        FixtureScheduleValidator.requireValid(listOf(fixture))
    }

    @Test
    fun `Super Mundial cadence remains unchanged`() {
        assertTrue(SuperMundialEditionPolicy.isEditionSeason(2025))
        assertTrue(SuperMundialEditionPolicy.isEditionSeason(2029))
        assertTrue(SuperMundialEditionPolicy.isEditionSeason(2033))
        assertTrue(SuperMundialEditionPolicy.isEditionSeason(2037))
        assertFalse(SuperMundialEditionPolicy.isEditionSeason(2026))
        assertFalse(SuperMundialEditionPolicy.isEditionSeason(2030))
        assertEquals(42, SuperMundialSystem.GROUP_WEEK_1)
        assertEquals(48, SuperMundialSystem.FINAL_WEEK)
        assertEquals(48, GameCalendar.WEEKS_PER_SEASON)
    }

    @Test
    fun `CONMEBOL remains dedicated while UEFA real rules remain explicitly pending`() {
        assertEquals(
            ConfederationEngineKind.DEDICATED_CONMEBOL,
            CompetitionRulesRegistry.engineForConfederation(FootballConfederation.CONMEBOL)
        )
        assertTrue(CompetitionRulesRegistry.hasRealDedicatedRules(FootballConfederation.CONMEBOL))

        assertEquals(
            ConfederationEngineKind.LEGACY_GENERIC,
            CompetitionRulesRegistry.engineForConfederation(FootballConfederation.UEFA)
        )
        assertFalse(CompetitionRulesRegistry.hasRealDedicatedRules(FootballConfederation.UEFA))
        assertTrue(
            CompetitionRulesRegistry.continentalCatalogFor(FootballConfederation.UEFA)
                .all { it.implementationStatus == CompetitionImplementationStatus.REAL_RULES_NOT_IMPLEMENTED }
        )
    }

    @Test
    fun `qualification source models preserve origin independently from Team rating`() {
        val slot = QualificationSlot(
            source = QualificationSource.LeaguePosition(1),
            destinationCompetition = CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE,
            ordinal = 1
        )
        assertEquals(QualificationSource.LeaguePosition(1), slot.source)
        assertEquals(CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE, slot.destinationCompetition)
    }

    @Test
    fun `catalog has unique canonical competition codes`() {
        val codes = CompetitionRulesRegistry.allCanonicalCodes
        assertEquals(codes.size, codes.map { it.uppercase() }.toSet().size)
        assertEquals(
            CompetitionRulesRegistry.catalogDefinitions.map { it.code }.toSet(),
            GlobalFootballSystem.competitions.map { it.code }.toSet()
        )
    }
}
