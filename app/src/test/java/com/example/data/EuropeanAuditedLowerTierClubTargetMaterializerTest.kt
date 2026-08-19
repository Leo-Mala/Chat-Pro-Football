package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EuropeanAuditedLowerTierClubTargetMaterializerTest {

    private fun installedReport(): EuropeanAuditedLowerTierClubTargetMaterializer2026_27.InstallationReport {
        ApplicationProvider.getApplicationContext<Context>()
        if (!EuropeanFactualClubTargetMaterializer2026_27.isInstalled()) {
            EuropeanFactualClubTargetMaterializer2026_27.installIntoDefaultData()
        }
        return EuropeanAuditedLowerTierClubTargetMaterializer2026_27.currentInstallationReport()
            ?: EuropeanAuditedLowerTierClubTargetMaterializer2026_27.installIntoDefaultData()
    }

    @Test
    fun `only organizer-verified Germany and Italy lower-tier targets are materialized`() {
        val report = installedReport()
        assertEquals(2, report.countries)
        assertEquals(47, report.factualLowerTierClubs)
        assertEquals(report.targetTeamsBefore, report.targetTeamsAfter)

        val targets = Fc26RemainingClubCoverage2026_27.lowerTierFactualTargets
        assertEquals(34, targets.count { it.country == "Alemanha" })
        assertEquals(13, targets.count { it.country == "Itália" })

        val ids = targets.map { target ->
            val template = DefaultData.countriesMap.getValue(target.country).teams.single { team ->
                team.name.equals(target.canonicalName, ignoreCase = true)
            }
            assertEquals(target.division, template.division)

            val expectedId = requireNotNull(
                StableTeamIdentityRegistry.idFor(target.country, target.canonicalName)
            )
            val actualId = GlobalFootballSystem.getGlobalId(target.country, target.canonicalName)
            assertEquals(expectedId, actualId)
            assertTrue(actualId < GlobalFootballSystem.VIRTUAL_TEAM_ID_FLOOR)

            val reversed = GlobalFootballSystem.getTeamByGlobalId(actualId)
            assertNotNull(reversed)
            assertEquals(target.country, reversed!!.country)
            assertEquals(target.canonicalName, reversed.name)
            assertEquals(target.division, reversed.division)
            actualId
        }

        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `materialization preserves phase 9_11A1 division sizes and replaces slots in place`() {
        ApplicationProvider.getApplicationContext<Context>()
        EuropeanAuditedLowerTierClubTargetMaterializer2026_27.resetForTests()
        if (!EuropeanFactualClubTargetMaterializer2026_27.isInstalled()) {
            EuropeanFactualClubTargetMaterializer2026_27.installIntoDefaultData()
        }

        val beforeByCountryAndDivision = listOf("Alemanha", "Itália").associateWith { country ->
            DefaultData.countriesMap.getValue(country).teams
                .groupingBy { it.division }
                .eachCount()
        }
        val beforeTotal = DefaultData.countriesMap.values.sumOf { it.teams.size }

        val report = EuropeanAuditedLowerTierClubTargetMaterializer2026_27.installIntoDefaultData()
        val afterTotal = DefaultData.countriesMap.values.sumOf { it.teams.size }

        assertEquals(beforeTotal, afterTotal)
        assertEquals(report.targetTeamsBefore, report.targetTeamsAfter)
        listOf("Alemanha", "Itália").forEach { country ->
            val after = DefaultData.countriesMap.getValue(country).teams
                .groupingBy { it.division }
                .eachCount()
            assertEquals("Division sizes drifted for $country", beforeByCountryAndDivision.getValue(country), after)
        }
    }

    @Test
    fun `phase 9_11A2 installation is idempotent`() {
        val first = installedReport()
        val snapshot = DefaultData.countriesMap.mapValues { (_, data) -> data.teams.toList() }
        val second = EuropeanAuditedLowerTierClubTargetMaterializer2026_27.installIntoDefaultData()

        assertEquals(first, second)
        assertEquals(snapshot, DefaultData.countriesMap.mapValues { (_, data) -> data.teams.toList() })
    }
}
