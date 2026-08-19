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
class Fc26RemainingFactualBaselinesA3ReportTest {

    @Test
    fun `phase 9_11A3 resolves only LFP audited clubs and preserves A2`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataset = requireNotNull(Fc26NormalizedDatasetLoader.loadValidatedOrNull(context.assets))
        assertEquals(18_405, dataset.players.size)
        assertTrue(EuropeanFactualClubTargetMaterializer2026_27.isInstalled())
        assertTrue(EuropeanAuditedLowerTierClubTargetMaterializer2026_27.isInstalled())

        // MainApplication installs A3. Remove only A3 to reproduce the exact validated A2 baseline.
        EuropeanAuditedFactualBaselinesA3Materializer2026_27.resetForTests()
        assertTrue(EuropeanAuditedLowerTierClubTargetMaterializer2026_27.isInstalled())

        val beforeTeams = buildCurrentProFootballUniverse()
        val beforePlan = buildPlan(beforeTeams, dataset)
        val beforeMatches = beforePlan.report.clubMatches.associateBy { it.sourceClubTeamId }
        val beforeAudits = Fc26ClubMatcher.auditCandidates(dataset, beforeTeams)
        val beforeIdsByIdentity = beforeTeams.associate { (it.country to it.name) to it.id }

        assertEquals(2_524, beforeTeams.size)
        assertEquals(385, beforePlan.report.matchedClubs)
        assertEquals(275, beforePlan.report.unmatchedClubs)
        assertEquals(2, beforePlan.report.ambiguousClubs)
        assertEquals(10_852, beforePlan.report.importedFc26Players)
        assertEquals(7_553, beforePlan.report.skippedDatasetPlayers)
        assertEquals(277, beforeAudits.size)
        assertEquals(7_553, beforeAudits.sumOf { it.playerCount })

        val install = EuropeanAuditedFactualBaselinesA3Materializer2026_27.installIntoDefaultData()
        val afterTeams = buildCurrentProFootballUniverse()
        val afterPlan = buildPlan(afterTeams, dataset)
        val afterMatches = afterPlan.report.clubMatches.associateBy { it.sourceClubTeamId }
        val afterAudits = Fc26ClubMatcher.auditCandidates(dataset, afterTeams)

        assertEquals(15, install.factualClubs)
        assertEquals(1, install.countries)
        assertEquals(beforeTeams.size, afterTeams.size)
        assertEquals(afterTeams.size, afterTeams.map { it.id }.distinct().size)
        assertEquals(2_524, afterTeams.size)

        val expectedGainedIds = Fc26RemainingFactualBaselinesA3_2026_27.factualTargets
            .mapTo(linkedSetOf()) { it.sourceClubTeamId }
        assertEquals(15, expectedGainedIds.size)

        val gained = beforeMatches.values.mapNotNull { before ->
            val after = afterMatches.getValue(before.sourceClubTeamId)
            if (before.status != Fc26ClubMatchStatus.MATCHED && after.status == Fc26ClubMatchStatus.MATCHED) after else null
        }
        val lost = beforeMatches.values.mapNotNull { before ->
            val after = afterMatches.getValue(before.sourceClubTeamId)
            if (before.status == Fc26ClubMatchStatus.MATCHED && after.status != Fc26ClubMatchStatus.MATCHED) before to after else null
        }
        val redirected = beforeMatches.values.mapNotNull { before ->
            val after = afterMatches.getValue(before.sourceClubTeamId)
            if (
                before.status == Fc26ClubMatchStatus.MATCHED &&
                after.status == Fc26ClubMatchStatus.MATCHED &&
                (before.targetTeamId != after.targetTeamId || before.targetTeamName != after.targetTeamName)
            ) before to after else null
        }

        assertEquals(expectedGainedIds, gained.mapTo(linkedSetOf()) { it.sourceClubTeamId })
        assertEquals(0, lost.size)
        assertEquals(0, redirected.size)

        val expectedGainedPlayers = dataset.sourceClubs
            .filter { it.sourceClubTeamId in expectedGainedIds }
            .sumOf { it.players.size }
        assertEquals(392, expectedGainedPlayers)
        assertEquals(392, gained.sumOf { it.playerCount })

        assertEquals(400, afterPlan.report.matchedClubs)
        assertEquals(260, afterPlan.report.unmatchedClubs)
        assertEquals(2, afterPlan.report.ambiguousClubs)
        assertEquals(11_244, afterPlan.report.importedFc26Players)
        assertEquals(7_161, afterPlan.report.skippedDatasetPlayers)
        assertEquals(262, afterAudits.size)
        assertEquals(7_161, afterAudits.sumOf { it.playerCount })

        val targetKeys = Fc26RemainingFactualBaselinesA3_2026_27.factualTargets
            .mapTo(hashSetOf()) { it.country to it.canonicalName }
        val afterIdsByIdentity = afterTeams.associate { (it.country to it.name) to it.id }
        val changedLegacyIds = beforeIdsByIdentity.keys
            .intersect(afterIdsByIdentity.keys)
            .filterNot { it in targetKeys }
            .mapNotNull { key ->
                val beforeId = beforeIdsByIdentity.getValue(key)
                val afterId = afterIdsByIdentity.getValue(key)
                if (beforeId != afterId) Triple(key, beforeId, afterId) else null
            }
        assertEquals(emptyList<Any>(), changedLegacyIds)

        Fc26RemainingFactualBaselinesA3_2026_27.factualTargets.forEach { target ->
            val id = requireNotNull(StableTeamIdentityRegistry.idFor(target.country, target.canonicalName))
            val team = requireNotNull(GlobalFootballSystem.getTeamByGlobalId(id))
            assertEquals(target.canonicalName, team.name)
            assertEquals(target.country, team.country)
            assertEquals(target.division, team.division)
            assertEquals(id, team.id)
        }

        // Every real FC26 player — not only the newly gained sample — keeps source overall,
        // potential and the complete gameplay attribute object byte-for-byte at mapping level.
        val sourceByStableId = dataset.players.associateBy { it.stableId }
        val mutatedPlayers = afterPlan.players.asSequence()
            .filter { StableRealPlayerIdentity.isRealPlayerId(it.id) }
            .filter { player ->
                val source = sourceByStableId[player.id] ?: return@filter true
                source.overall != player.force ||
                    source.potential != player.potential ||
                    source.atributos != player.atributos
            }
            .map { it.id }
            .toList()
        assertEquals(emptyList<Long>(), mutatedPlayers)

        val gainedDetails = gained.sortedBy { it.sourceClubTeamId }.map { match ->
            linkedMapOf<String, Any?>(
                "sourceClubTeamId" to match.sourceClubTeamId,
                "sourceClubName" to match.sourceClubName,
                "playerCount" to match.playerCount,
                "targetTeamId" to match.targetTeamId,
                "targetTeamName" to match.targetTeamName,
                "reason" to match.reason
            )
        }
        val unresolvedDetails = afterAudits.map { audit ->
            linkedMapOf<String, Any?>(
                "sourceClubTeamId" to audit.sourceClubTeamId,
                "sourceClubName" to audit.sourceClubName,
                "leagueId" to audit.leagueId,
                "leagueName" to audit.leagueName,
                "sourceCountry" to audit.sourceCountry,
                "playerCount" to audit.playerCount,
                "status" to audit.currentStatus.name,
                "materializationStatus" to audit.materializationStatus.name
            )
        }

        val report = linkedMapOf<String, Any?>(
            "phase" to "9.11A3",
            "source" to linkedMapOf(
                "dataset" to dataset.manifest.datasetSource,
                "datasetVersion" to dataset.manifest.datasetVersion,
                "datasetPlayers" to dataset.players.size,
                "datasetClubs" to dataset.sourceClubs.size,
                "factualBasis" to Fc26RemainingFactualBaselinesA3_2026_27.LFP_LIGUE_2_2026_27
            ),
            "implementation" to linkedMapOf(
                "implementedClubs" to expectedGainedIds.size,
                "implementedPlayers" to expectedGainedPlayers,
                "countries" to install.countries,
                "targetTeamsBefore" to install.targetTeamsBefore,
                "targetTeamsAfter" to install.targetTeamsAfter,
                "metadataOrigins" to install.metadataOrigins.mapKeys { it.key.name }
            ),
            "before" to linkedMapOf(
                "targetTeams" to beforeTeams.size,
                "matchedClubs" to beforePlan.report.matchedClubs,
                "unmatchedClubs" to beforePlan.report.unmatchedClubs,
                "ambiguousClubs" to beforePlan.report.ambiguousClubs,
                "importedPlayers" to beforePlan.report.importedFc26Players,
                "blockedPlayers" to beforeAudits.sumOf { it.playerCount }
            ),
            "after" to linkedMapOf(
                "targetTeams" to afterTeams.size,
                "matchedClubs" to afterPlan.report.matchedClubs,
                "unmatchedClubs" to afterPlan.report.unmatchedClubs,
                "ambiguousClubs" to afterPlan.report.ambiguousClubs,
                "importedPlayers" to afterPlan.report.importedFc26Players,
                "blockedPlayers" to afterAudits.sumOf { it.playerCount }
            ),
            "transitionAudit" to linkedMapOf(
                "gainedMatches" to gained.size,
                "gainedPlayers" to gained.sumOf { it.playerCount },
                "lostMatches" to lost.size,
                "redirectedMatches" to redirected.size,
                "unchangedLegacyIdsChanged" to changedLegacyIds.size,
                "gainedClubs" to gainedDetails
            ),
            "remaining" to linkedMapOf(
                "unresolvedClubs" to afterAudits.size,
                "unresolvedPlayers" to afterAudits.sumOf { it.playerCount },
                "unmatchedClubs" to afterAudits.count { it.currentStatus == Fc26ClubMatchStatus.UNMATCHED },
                "ambiguousClubs" to afterAudits.count { it.currentStatus == Fc26ClubMatchStatus.AMBIGUOUS },
                "clubs" to unresolvedDetails
            ),
            "safety" to linkedMapOf(
                "fuzzyAutoMatchingIntroduced" to false,
                "arbitraryAliasesIntroduced" to false,
                "fc26RatingsMutated" to mutatedPlayers.isNotEmpty(),
                "fc26PotentialMutated" to mutatedPlayers.isNotEmpty(),
                "fc26AttributesMutated" to mutatedPlayers.isNotEmpty(),
                "efootballOnlyPlayersImported" to false,
                "roomMigrationRequired" to false,
                "roomVersion" to 21,
                "lostPreviouslyMatchedClubs" to lost.size,
                "redirectedPreviouslyMatchedClubs" to redirected.size,
                "unchangedLegacyIdsChanged" to changedLegacyIds.size
            )
        )

        val root = findRepositoryRoot()
        val reportFile = File(root, "reports/fc26_remaining_factual_baselines_a3_report.json")
        reportFile.parentFile.mkdirs()
        reportFile.writeText(GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", Charsets.UTF_8)

        println(
            "FC26_9_11A3 beforeMatched=${beforePlan.report.matchedClubs} " +
                "afterMatched=${afterPlan.report.matchedClubs} gained=${gained.size} " +
                "gainedPlayers=${gained.sumOf { it.playerCount }} lost=${lost.size} " +
                "redirected=${redirected.size} remaining=${afterAudits.size} " +
                "remainingPlayers=${afterAudits.sumOf { it.playerCount }}"
        )
    }

    private fun buildPlan(teams: List<Team>, dataset: Fc26Dataset): Fc26SeedPlanner.Plan =
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
