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
    fun `materialization preserves configured division sizes and replaces slots in place`() {
        val report = installedReport()
        assertEquals(report.targetTeamsBefore, report.targetTeamsAfter)

        val germany = DefaultData.countriesMap.getValue("Alemanha").teams
        assertEquals(18, germany.count { it.division == 1 })
        assertEquals(18, germany.count { it.division == 2 })
        assertEquals(20, germany.count { it.division == 3 })

        val italy = DefaultData.countriesMap.getValue("Itália").teams
        assertEquals(20, italy.count { it.division == 1 })
        assertEquals(20, italy.count { it.division == 2 })
        assertEquals(60, italy.count { it.division == 3 })
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
