package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Fc26FallbackArchitectureReportTest {

    @Test
    fun `phase 9 14 reduces procedural population without changing FC26 coverage`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataset = requireNotNull(Fc26NormalizedDatasetLoader.loadValidatedOrNull(context.assets))
        val teams = buildCurrentProFootballUniverse()
        val plan = Fc26SeedPlanner.build(
            teams = teams,
            dataset = dataset,
            proceduralRosterFactory = { team ->
                DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
            }
        )

        assertEquals(18_405, dataset.players.size)
        assertEquals(18_405, plan.report.bulkImportedFc26Players)
        assertEquals(400, plan.report.matchedClubs)
        assertEquals(260, plan.report.unmatchedClubs)
        assertEquals(2, plan.report.ambiguousClubs)
        assertEquals(7_161, plan.report.importedUnassignedClubPlayers)
        assertEquals(2_124, plan.report.fallbackRostersRequired)
        assertEquals(42_480, plan.report.fallbackPlayersGenerated)
        assertEquals(60_885, plan.players.size)
        assertEquals(0, plan.players.size - plan.players.map { it.id }.distinct().size)

        val realPlayers = plan.players.filter { StableRealPlayerIdentity.isRealPlayerId(it.id) }
        assertEquals(18_405, realPlayers.size)

        val report = linkedMapOf<String, Any?>(
            "phase" to "9.14",
            "baselineMainSha" to "08acb8558b2ecce172701ae0dfb4bd7bd495fcb2",
            "datasetPlayers" to dataset.players.size,
            "bulkImportedFc26Players" to plan.report.bulkImportedFc26Players,
            "matchedClubs" to plan.report.matchedClubs,
            "unmatchedClubs" to plan.report.unmatchedClubs,
            "ambiguousClubs" to plan.report.ambiguousClubs,
            "unassignedSourceClubPlayers" to plan.report.importedUnassignedClubPlayers,
            "fallbackRosters" to plan.report.fallbackRostersRequired,
            "fallbackRosterSizeBefore" to 30,
            "fallbackRosterSizeAfter" to Fc26FallbackRosterPolicy.TARGET_SIZE,
            "proceduralPlayersBefore" to 63_720,
            "proceduralPlayersAfter" to plan.report.fallbackPlayersGenerated,
            "proceduralPlayersRemoved" to (63_720 - plan.report.fallbackPlayersGenerated),
            "persistedPlayersBefore" to 82_125,
            "plannedPlayersAfter" to plan.players.size,
            "plannedPlayersReduced" to (82_125 - plan.players.size),
            "duplicatePlayerIds" to (plan.players.size - plan.players.map { it.id }.distinct().size),
            "roomVersion" to 21,
            "fuzzyMatchesPromoted" to 0
        )

        val output = File(findRepositoryRoot(), "reports/phase_9_14_fallback_architecture.json")
        output.parentFile.mkdirs()
        output.writeText(GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n")
    }

    private fun buildCurrentProFootballUniverse(): List<Team> = buildList {
        for (countryKey in GlobalFootballSystem.keys) {
            for (template in DefaultData.getTeamsForCountry(countryKey)) {
                add(
                    Team(
                        id = GlobalFootballSystem.getGlobalId(countryKey, template.name),
                        name = template.name,
                        city = template.city,
                        state = template.state,
                        country = countryKey,
                        division = template.division,
                        rating = template.rating,
                        stadiumName = template.stadium
                    )
                )
            }
        }
    }

    private fun findRepositoryRoot(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile ?: return@repeat
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
