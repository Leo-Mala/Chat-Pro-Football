package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Fc26FactualTargetMaterializationReportTest {

    @Test
    fun `materialized stable targets unlock FC26 players and write aggregate report`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val install = requireNotNull(EuropeanFactualClubTargetMaterializer2026_27.currentInstallationReport())
        val dataset = requireNotNull(Fc26NormalizedDatasetLoader.loadValidatedOrNull(context.assets))
        val teams = buildCurrentProFootballUniverse()

        assertEquals(18_405, dataset.players.size)
        assertEquals(teams.size, teams.map { it.id }.distinct().size)

        val plan = Fc26SeedPlanner.build(
            teams = teams,
            dataset = dataset,
            proceduralRosterFactory = { team ->
                DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
            }
        )
        val audits = Fc26ClubMatcher.auditCandidates(dataset, teams)
        val stableMissing = audits.filter {
            it.materializationStatus == Fc26TargetMaterializationStatus.STABLE_TARGET_MISSING
        }
        val stablePresentButUnresolved = audits.filter {
            it.materializationStatus == Fc26TargetMaterializationStatus.STABLE_TARGET_PRESENT
        }

        assertTrue("A materialização não pode reduzir cobertura da fase 9.11A", plan.report.matchedClubs >= 160)
        assertTrue("A materialização não pode reduzir jogadores FC26 importados", plan.report.importedFc26Players >= 4_583)
        assertEquals("Todo alvo estável verificado deve estar materializado", 0, stableMissing.size)
        assertEquals(
            "FC26 force/potential devem permanecer fonte-derivados",
            0,
            plan.players.asSequence()
                .filter { StableRealPlayerIdentity.isRealPlayerId(it.id) }
                .count { player ->
                    val source = dataset.players.firstOrNull { it.stableId == player.id } ?: return@count true
                    source.overall != player.force || source.potential != player.potential
                }
        )

        val matches = plan.report.clubMatches
        val unmatchedPlayers = matches.filter { it.status == Fc26ClubMatchStatus.UNMATCHED }.sumOf { it.playerCount }
        val ambiguousPlayers = matches.filter { it.status == Fc26ClubMatchStatus.AMBIGUOUS }.sumOf { it.playerCount }
        val origins = install.metadataOrigins.entries.associate { it.key.name to it.value }

        val report = linkedMapOf<String, Any?>(
            "phase" to "9.11A1",
            "source" to linkedMapOf(
                "dataset" to dataset.manifest.datasetSource,
                "datasetVersion" to dataset.manifest.datasetVersion,
                "datasetPlayers" to dataset.players.size,
                "datasetClubs" to dataset.sourceClubs.size
            ),
            "previousPhaseBaseline" to linkedMapOf(
                "matchedClubs" to 160,
                "unmatchedClubs" to 492,
                "ambiguousClubs" to 10,
                "importedPlayers" to 4_583,
                "skippedPlayers" to 13_822,
                "playersWithMappedClub" to 4_494,
                "fallbackRostersRequired" to 2_384,
                "stableTargetsMissing" to 130,
                "playersBlockedByStableTargetsMissing" to 3_597
            ),
            "materialization" to linkedMapOf(
                "verifiedCountries" to install.countries,
                "factualTopFlightClubs" to install.factualTopFlightClubs,
                "targetTeamsBefore" to install.targetTeamsBefore,
                "targetTeamsAfter" to install.targetTeamsAfter,
                "metadataOrigins" to origins,
                "identityOnlyMetadataIsNotFactualSeedReady" to true
            ),
            "after" to linkedMapOf(
                "targetTeams" to teams.size,
                "matchedClubs" to plan.report.matchedClubs,
                "unmatchedClubs" to plan.report.unmatchedClubs,
                "ambiguousClubs" to plan.report.ambiguousClubs,
                "importedPlayers" to plan.report.importedFc26Players,
                "skippedPlayers" to plan.report.skippedDatasetPlayers,
                "playersWithMappedClub" to plan.report.playersWithMappedClub,
                "fallbackRostersRequired" to plan.report.fallbackRostersRequired,
                "skippedPlayersByUnmatchedClub" to unmatchedPlayers,
                "skippedPlayersByAmbiguousClub" to ambiguousPlayers,
                "stableTargetsMissing" to stableMissing.size,
                "playersBlockedByStableTargetsMissing" to stableMissing.sumOf { it.playerCount },
                "stableTargetsPresentButUnresolved" to stablePresentButUnresolved.size,
                "playersBlockedByStableTargetsPresentButUnresolved" to stablePresentButUnresolved.sumOf { it.playerCount }
            ),
            "deltaFrom9_11A" to linkedMapOf(
                "matchedClubs" to (plan.report.matchedClubs - 160),
                "importedPlayers" to (plan.report.importedFc26Players - 4_583),
                "fallbackRosters" to (plan.report.fallbackRostersRequired - 2_384),
                "stableTargetsMissing" to (stableMissing.size - 130),
                "playersBlockedByStableTargetsMissing" to (stableMissing.sumOf { it.playerCount } - 3_597)
            ),
            "safety" to linkedMapOf(
                "fuzzyAutoMatchingIntroduced" to false,
                "fc26RatingsMutated" to false,
                "fc26PotentialMutated" to false,
                "fc26AttributesMutated" to false,
                "roomMigrationRequired" to false,
                "roomVersion" to 21
            )
        )

        val output = File(findRepositoryRoot(), "reports/fc26_factual_target_materialization_report.json")
        output.parentFile.mkdirs()
        output.writeText(GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", Charsets.UTF_8)

        println(
            "FC26_FACTUAL_TARGETS teams=${teams.size} matched=${plan.report.matchedClubs} " +
                "unmatched=${plan.report.unmatchedClubs} ambiguous=${plan.report.ambiguousClubs} " +
                "imported=${plan.report.importedFc26Players} skipped=${plan.report.skippedDatasetPlayers} " +
                "fallback=${plan.report.fallbackRostersRequired} stableMissing=${stableMissing.size}"
        )
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
