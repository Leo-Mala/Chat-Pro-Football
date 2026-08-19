package com.example.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EuropeanFactualClubTargetMaterializerTest {

    @After
    fun restoreLegacyCatalog() {
        EuropeanFactualClubTargetMaterializer2026_27.resetForTests()
    }

    @Test
    fun `verified top flights materialize exact factual names counts and stable ids`() {
        val report = EuropeanFactualClubTargetMaterializer2026_27.installIntoDefaultData()

        assertEquals(20, report.countries)
        assertEquals(320, report.factualTopFlightClubs)

        val ids = mutableListOf<Long>()
        EuropeanDomesticBaseline2026_27.associations
            .filter { it.coverage == EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT }
            .forEach { baseline ->
                val actual = DefaultData.countriesMap.getValue(baseline.country).teams
                    .filter { it.division == 1 }
                assertEquals(baseline.topDivisionClubCount, actual.size)
                assertEquals(baseline.verifiedTopFlightClubs, actual.map { it.name })

                actual.forEach { template ->
                    val expectedId = requireNotNull(
                        StableTeamIdentityRegistry.idFor(baseline.country, template.name)
                    )
                    val actualId = GlobalFootballSystem.getGlobalId(baseline.country, template.name)
                    assertEquals(expectedId, actualId)
                    assertTrue(actualId < GlobalFootballSystem.VIRTUAL_TEAM_ID_FLOOR)
                    ids += actualId
                }
            }

        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `league sizes follow verified baseline without rewriting lower division sizes`() {
        val before = EuropeanDomesticBaseline2026_27.associations.associate { baseline ->
            baseline.country to DefaultData.countriesMap.getValue(baseline.country).teams
                .groupingBy { it.division }
                .eachCount()
        }

        val report = EuropeanFactualClubTargetMaterializer2026_27.installIntoDefaultData()

        EuropeanDomesticBaseline2026_27.associations.forEach { baseline ->
            val afterByDivision = DefaultData.countriesMap.getValue(baseline.country).teams
                .groupingBy { it.division }
                .eachCount()
            assertEquals(baseline.topDivisionClubCount, afterByDivision[1])
            before.getValue(baseline.country)
                .filterKeys { it != 1 }
                .forEach { (division, count) -> assertEquals(count, afterByDivision[division]) }
        }

        val expectedAfter = report.targetTeamsBefore + EuropeanDomesticBaseline2026_27.associations.sumOf { baseline ->
            val oldTop = before.getValue(baseline.country)[1] ?: 0
            baseline.topDivisionClubCount - oldTop
        }
        assertEquals(expectedAfter, report.targetTeamsAfter)
        assertEquals(18, DefaultData.countriesMap.getValue("Bélgica").teams.count { it.division == 1 })
        assertEquals(18, DefaultData.countriesMap.getValue("Turquia").teams.count { it.division == 1 })
        assertEquals(14, DefaultData.countriesMap.getValue("Sérvia").teams.count { it.division == 1 })
    }

    @Test
    fun `explicit metadata stays explicit while identity-only targets do not become factual-ready`() {
        EuropeanFactualClubTargetMaterializer2026_27.installIntoDefaultData()

        val arsenal = DefaultData.countriesMap.getValue("Inglaterra").teams.single { it.name == "Arsenal FC" }
        assertEquals("Emirates Stadium", arsenal.stadium)

        val realMadrid = DefaultData.countriesMap.getValue("Espanha").teams.single { it.name == "Real Madrid" }
        assertEquals("Santiago Bernabéu", realMadrid.stadium)

        val trabzonspor = DefaultData.countriesMap.getValue("Turquia").teams.single { it.name == "Trabzonspor" }
        assertEquals("Papara Park", trabzonspor.stadium)

        val juventus = DefaultData.countriesMap.getValue("Itália").teams.single { it.name == "Juventus" }
        assertEquals(1, juventus.division)
        assertEquals(
            EuropeanFactualClubSeedStatus.MISSING_EXPLICIT_TEMPLATE,
            EuropeanFactualClubSeedReadiness.assess("Itália", "Juventus").status
        )
    }

    @Test
    fun `reverse stable-id lookup resolves materialized factual targets`() {
        EuropeanFactualClubTargetMaterializer2026_27.installIntoDefaultData()

        listOf(
            "Itália" to "Juventus",
            "Países Baixos" to "Ajax",
            "Portugal" to "SL Benfica",
            "França" to "Olympique de Marseille"
        ).forEach { (country, name) ->
            val stableId = requireNotNull(StableTeamIdentityRegistry.idFor(country, name))
            val team = GlobalFootballSystem.getTeamByGlobalId(stableId)
            assertNotNull(team)
            assertEquals(stableId, team!!.id)
            assertEquals(name, team.name)
            assertEquals(country, team.country)
            assertEquals(1, team.division)
        }
    }

    @Test
    fun `lower division ids remain stable across factual catalog installation`() {
        val retainedLower = EuropeanDomesticBaseline2026_27.associations.flatMap { baseline ->
            DefaultData.countriesMap.getValue(baseline.country).teams
                .filter { it.division > 1 }
                .take(3)
                .map { template -> Triple(baseline.country, template.name, GlobalFootballSystem.getGlobalId(baseline.country, template.name)) }
        }

        EuropeanFactualClubTargetMaterializer2026_27.installIntoDefaultData()

        retainedLower.forEach { (country, name, oldId) ->
            val stillPresent = DefaultData.countriesMap.getValue(country).teams.any { it.name == name && it.division > 1 }
            if (stillPresent) {
                assertEquals(oldId, GlobalFootballSystem.getGlobalId(country, name))
            }
        }
    }

    @Test
    fun `installation is idempotent and does not duplicate teams`() {
        val first = EuropeanFactualClubTargetMaterializer2026_27.installIntoDefaultData()
        val snapshot = DefaultData.countriesMap.mapValues { (_, data) -> data.teams.toList() }
        val second = EuropeanFactualClubTargetMaterializer2026_27.installIntoDefaultData()

        assertEquals(first, second)
        assertEquals(snapshot, DefaultData.countriesMap.mapValues { (_, data) -> data.teams.toList() })

        val allIds = GlobalFootballSystem.keys.flatMap { country ->
            DefaultData.countriesMap.getValue(country).teams.map { template ->
                GlobalFootballSystem.getGlobalId(country, template.name)
            }
        }
        assertEquals(allIds.size, allIds.distinct().size)
        assertFalse(allIds.any { it <= 0L })
    }
}
