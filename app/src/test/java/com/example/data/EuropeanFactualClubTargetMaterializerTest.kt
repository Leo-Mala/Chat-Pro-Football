package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EuropeanFactualClubTargetMaterializerTest {

    private fun installedReport(): EuropeanFactualClubTargetMaterializer2026_27.InstallationReport {
        ApplicationProvider.getApplicationContext<Context>()
        return requireNotNull(EuropeanFactualClubTargetMaterializer2026_27.currentInstallationReport()) {
            "MainApplication deve instalar os alvos factuais antes do uso do catálogo."
        }
    }

    @Test
    fun `verified top flights materialize exact factual names counts and stable ids`() {
        val report = installedReport()

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
    fun `league sizes follow verified baseline without rewriting lower division layers`() {
        val report = installedReport()
        val before = EuropeanDomesticBaseline2026_27.associations.associate { baseline ->
            val legacy = requireNotNull(
                EuropeanFactualClubTargetMaterializer2026_27.legacyTeamsForIdAllocation(baseline.country)
            )
            baseline.country to legacy.groupingBy { it.division }.eachCount()
        }

        EuropeanDomesticBaseline2026_27.associations.forEach { baseline ->
            val actualTeams = DefaultData.countriesMap.getValue(baseline.country).teams
            val afterByDivision = actualTeams.groupingBy { it.division }.eachCount()
            assertEquals(baseline.topDivisionClubCount, afterByDivision[1])
            before.getValue(baseline.country)
                .filterKeys { it != 1 }
                .forEach { (division, count) -> assertEquals(count, afterByDivision[division]) }

            val legacyLower = requireNotNull(
                EuropeanFactualClubTargetMaterializer2026_27.legacyTeamsForIdAllocation(baseline.country)
            ).filter { it.division > 1 }
            val materializedLower = actualTeams.filter { it.division > 1 }
            assertEquals(legacyLower, materializedLower)
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
        installedReport()

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
        installedReport()

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
    fun `lower division stable allocation keeps legacy ordering`() {
        installedReport()

        EuropeanDomesticBaseline2026_27.associations.forEach { baseline ->
            val legacy = requireNotNull(
                EuropeanFactualClubTargetMaterializer2026_27.legacyTeamsForIdAllocation(baseline.country)
            )
            val retainedNames = DefaultData.countriesMap.getValue(baseline.country).teams
                .filter { it.division > 1 }
                .mapTo(hashSetOf()) { it.name }

            legacy.filter { it.division > 1 && it.name in retainedNames }
                .take(5)
                .forEach { template ->
                    val id = GlobalFootballSystem.getGlobalId(baseline.country, template.name)
                    assertTrue(id > 0L)
                    assertFalse(GlobalFootballSystem.isGeneratedVirtualTeamId(id))
                }
        }
    }

    @Test
    fun `installation is idempotent and does not duplicate team ids`() {
        val first = installedReport()
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
