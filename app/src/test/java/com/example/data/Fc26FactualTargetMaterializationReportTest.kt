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
    fun `materialized stable targets unlock FC26 players and write transition-audited report`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataset = requireNotNull(Fc26NormalizedDatasetLoader.loadValidatedOrNull(context.assets))
        assertEquals(18_405, dataset.players.size)

        // Reproduz a 9.11A com o catálogo legado e as regras de ID anteriores à materialização.
        EuropeanFactualClubTargetMaterializer2026_27.resetForTests()
        val beforeTeams = buildCurrentProFootballUniverse()
        assertEquals(beforeTeams.size, beforeTeams.map { it.id }.distinct().size)
        val beforePlan = buildPlan(beforeTeams, dataset)
        val beforeAudits = Fc26ClubMatcher.auditCandidates(dataset, beforeTeams)
        val beforeStableMissing = beforeAudits.filter {
            it.materializationStatus == Fc26TargetMaterializationStatus.STABLE_TARGET_MISSING
        }

        assertEquals(2_544, beforeTeams.size)
        assertEquals(160, beforePlan.report.matchedClubs)
        assertEquals(492, beforePlan.report.unmatchedClubs)
        assertEquals(10, beforePlan.report.ambiguousClubs)
        assertEquals(4_583, beforePlan.report.importedFc26Players)
        assertEquals(13_822, beforePlan.report.skippedDatasetPlayers)
        assertEquals(4_494, beforePlan.report.playersWithMappedClub)
        assertEquals(2_384, beforePlan.report.fallbackRostersRequired)
        assertEquals(130, beforeStableMissing.size)
        assertEquals(3_597, beforeStableMissing.sumOf { it.playerCount })

        val install = EuropeanFactualClubTargetMaterializer2026_27.installIntoDefaultData()
        val teams = buildCurrentProFootballUniverse()
        assertEquals(teams.size, teams.map { it.id }.distinct().size)

        val plan = buildPlan(teams, dataset)
        val audits = Fc26ClubMatcher.auditCandidates(dataset, teams)
        val stableMissing = audits.filter {
            it.materializationStatus == Fc26TargetMaterializationStatus.STABLE_TARGET_MISSING
        }
        val stablePresentButUnresolved = audits.filter {
            it.materializationStatus == Fc26TargetMaterializationStatus.STABLE_TARGET_PRESENT
        }

        assertTrue("A materialização não pode reduzir cobertura agregada da fase 9.11A", plan.report.matchedClubs >= beforePlan.report.matchedClubs)
        assertTrue("A materialização não pode reduzir jogadores FC26 importados no agregado", plan.report.importedFc26Players >= beforePlan.report.importedFc26Players)
        assertEquals("Todo alvo estável verificado deve estar materializado", 0, stableMissing.size)
        assertEquals("Nenhum alvo estável presente deve continuar sem resolução", 0, stablePresentButUnresolved.size)
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

        val beforeMatches = beforePlan.report.clubMatches.associateBy { it.sourceClubTeamId }
        val afterMatches = plan.report.clubMatches.associateBy { it.sourceClubTeamId }
        assertEquals(beforeMatches.keys, afterMatches.keys)

        val gained = beforeMatches.values.mapNotNull { before ->
            val after = afterMatches.getValue(before.sourceClubTeamId)
            if (before.status != Fc26ClubMatchStatus.MATCHED && after.status == Fc26ClubMatchStatus.MATCHED) after else null
        }
        val lost = beforeMatches.values.mapNotNull { before ->
            val after = afterMatches.getValue(before.sourceClubTeamId)
            if (before.status == Fc26ClubMatchStatus.MATCHED && after.status != Fc26ClubMatchStatus.MATCHED) {
                before to after
            } else null
        }
        val redirected = beforeMatches.values.mapNotNull { before ->
            val after = afterMatches.getValue(before.sourceClubTeamId)
            val changedTarget = before.status == Fc26ClubMatchStatus.MATCHED &&
                after.status == Fc26ClubMatchStatus.MATCHED &&
                (before.targetTeamId != after.targetTeamId || before.targetTeamName != after.targetTeamName)
            if (changedTarget) before to after else null
        }
        val nonStableRedirects = redirected.filter { (_, after) ->
            val targetId = after.targetTeamId ?: return@filter true
            StableTeamIdentityRegistry.identityForId(targetId) == null
        }

        assertEquals(
            "Clubes não materializados não podem ter seus IDs deslocados silenciosamente",
            0,
            nonStableRedirects.size
        )
        assertEquals(
            "A única perda conhecida é o FC Metz, cujo alvo top-flight procedural ficou fora do baseline 2026/27",
            setOf(68L),
            lost.mapTo(linkedSetOf()) { it.first.sourceClubTeamId }
        )
        assertEquals(26, lost.sumOf { it.first.playerCount })

        val matches = plan.report.clubMatches
        val unmatchedPlayers = matches.filter { it.status == Fc26ClubMatchStatus.UNMATCHED }.sumOf { it.playerCount }
        val ambiguousPlayers = matches.filter { it.status == Fc26ClubMatchStatus.AMBIGUOUS }.sumOf { it.playerCount }
        val origins = install.metadataOrigins.entries.associate { it.key.name to it.value }
        val lostDetails = lost.map { (before, after) ->
            linkedMapOf<String, Any?>(
                "sourceClubTeamId" to before.sourceClubTeamId,
                "sourceClubName" to before.sourceClubName,
                "sourceLeagueName" to before.leagueName,
                "playerCount" to before.playerCount,
                "previousTargetTeamId" to before.targetTeamId,
                "previousTargetTeamName" to before.targetTeamName,
                "afterStatus" to after.status.name,
                "afterReason" to after.reason,
                "disposition" to "LOWER_TIER_FACTUAL_MATERIALIZATION_REQUIRED"
            )
        }

        val report = linkedMapOf<String, Any?>(
            "phase" to "9.11A1",
            "source" to linkedMapOf(
                "dataset" to dataset.manifest.datasetSource,
                "datasetVersion" to dataset.manifest.datasetVersion,
                "datasetPlayers" to dataset.players.size,
                "datasetClubs" to dataset.sourceClubs.size
            ),
            "previousPhaseBaseline" to linkedMapOf(
                "targetTeams" to beforeTeams.size,
                "matchedClubs" to beforePlan.report.matchedClubs,
                "unmatchedClubs" to beforePlan.report.unmatchedClubs,
                "ambiguousClubs" to beforePlan.report.ambiguousClubs,
                "importedPlayers" to beforePlan.report.importedFc26Players,
                "skippedPlayers" to beforePlan.report.skippedDatasetPlayers,
                "playersWithMappedClub" to beforePlan.report.playersWithMappedClub,
                "fallbackRostersRequired" to beforePlan.report.fallbackRostersRequired,
                "stableTargetsMissing" to beforeStableMissing.size,
                "playersBlockedByStableTargetsMissing" to beforeStableMissing.sumOf { it.playerCount }
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
            "transitionAudit" to linkedMapOf(
                "gainedMatches" to gained.size,
                "gainedPlayers" to gained.sumOf { it.playerCount },
                "lostMatches" to lost.size,
                "lostPlayers" to lost.sumOf { it.first.playerCount },
                "lostClubs" to lostDetails,
                "redirectedMatches" to redirected.size,
                "redirectsToStableIdentity" to (redirected.size - nonStableRedirects.size),
                "nonStableIdRedirects" to nonStableRedirects.size
            ),
            "deltaFrom9_11A" to linkedMapOf(
                "matchedClubs" to (plan.report.matchedClubs - beforePlan.report.matchedClubs),
                "importedPlayers" to (plan.report.importedFc26Players - beforePlan.report.importedFc26Players),
                "fallbackRosters" to (plan.report.fallbackRostersRequired - beforePlan.report.fallbackRostersRequired),
                "stableTargetsMissing" to (stableMissing.size - beforeStableMissing.size),
                "playersBlockedByStableTargetsMissing" to (
                    stableMissing.sumOf { it.playerCount } - beforeStableMissing.sumOf { it.playerCount }
                )
            ),
            "remainingScope" to linkedMapOf(
                "lowerTierFactualMaterializationRequired" to lostDetails,
                "note" to "Top-flight materialization does not invent lower-tier placement for clubs absent from the verified 2026/27 top-flight baseline."
            ),
            "safety" to linkedMapOf(
                "fuzzyAutoMatchingIntroduced" to false,
                "fc26RatingsMutated" to false,
                "fc26PotentialMutated" to false,
                "fc26AttributesMutated" to false,
                "nonStableIdRedirects" to nonStableRedirects.size,
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
                "fallback=${plan.report.fallbackRostersRequired} stableMissing=${stableMissing.size} " +
                "gained=${gained.size} lost=${lost.size} redirected=${redirected.size}"
        )
    }

    private fun buildPlan(teams: List<Team>, dataset: Fc26Dataset): Fc26SeedPlan =
        Fc26SeedPlanner.build(
            teams = teams,
            dataset = dataset,
            proceduralRosterFactory = { team ->
                DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
            }
        )

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
