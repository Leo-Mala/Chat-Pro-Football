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
        // This class validates the historical Phase 9.11A1 contract. MainApplication now installs
        // Phase 9.11A2 as well, so remove only A2 before asserting the A1 catalog snapshot.
        EuropeanAuditedLowerTierClubTargetMaterializer2026_27.resetForTests()
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
    fun `league sizes follow baseline and only exact or explicit promoted lower duplicates are removed`() {
        val report = installedReport()
        var expectedTotalAfter = report.targetTeamsBefore

        EuropeanDomesticBaseline2026_27.associations.forEach { baseline ->
            val legacy = requireNotNull(
                EuropeanFactualClubTargetMaterializer2026_27.legacyTeamsForIdAllocation(baseline.country)
            )
            val legacyTopCount = legacy.count { it.division == 1 }
            val explicitTemplates = DefaultData.originalMap[baseline.country]?.teams.orEmpty()
            val promotedIds = baseline.verifiedTopFlightClubs.mapNotNullTo(hashSetOf()) { club ->
                StableTeamIdentityRegistry.idFor(baseline.country, club)
            }
            val expectedLower = legacy.filter { template ->
                if (template.division == 1) return@filter false
                val exactCanonicalDuplicate = baseline.verifiedTopFlightClubs.any {
                    it.equals(template.name, ignoreCase = true)
                }
                if (exactCanonicalDuplicate) return@filter false
                val isExplicitTemplate = explicitTemplates.any { it == template }
                if (!isExplicitTemplate) return@filter true
                val stableId = StableTeamIdentityRegistry.idFor(baseline.country, template.name)
                stableId == null || stableId !in promotedIds
            }

            val actualTeams = DefaultData.countriesMap.getValue(baseline.country).teams
            val actualTop = actualTeams.filter { it.division == 1 }
            val actualLower = actualTeams.filter { it.division > 1 }

            assertEquals(baseline.topDivisionClubCount, actualTop.size)
            assertEquals(expectedLower, actualLower)

            expectedTotalAfter += baseline.topDivisionClubCount - legacyTopCount
            expectedTotalAfter -= legacy.count { it.division > 1 } - expectedLower.size
        }

        assertEquals(expectedTotalAfter, report.targetTeamsAfter)
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
