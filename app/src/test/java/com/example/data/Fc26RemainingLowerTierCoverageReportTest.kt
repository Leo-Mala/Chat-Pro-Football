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
class Fc26RemainingLowerTierCoverageReportTest {

    @Test
    fun `phase 9_11A2 resolves only audited clubs without regressions and writes report`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataset = requireNotNull(Fc26NormalizedDatasetLoader.loadValidatedOrNull(context.assets))
        assertEquals(18_405, dataset.players.size)
        assertTrue(EuropeanFactualClubTargetMaterializer2026_27.isInstalled())

        // MainApplication installs A2. Remove only A2 to reproduce the exact validated 9.11A1 seed.
        EuropeanAuditedLowerTierClubTargetMaterializer2026_27.resetForTests()
        assertTrue(EuropeanFactualClubTargetMaterializer2026_27.isInstalled())

        val beforeTeams = buildCurrentProFootballUniverse()
        val beforePlan = buildPlan(beforeTeams, dataset)
        val beforeMatches = beforePlan.report.clubMatches.associateBy { it.sourceClubTeamId }
        val beforeAudits = Fc26ClubMatcher.auditCandidates(dataset, beforeTeams)
        val beforeIdsByIdentity = beforeTeams.associate { (it.country to it.name) to it.id }

        assertEquals(2_524, beforeTeams.size)
        assertEquals(296, beforePlan.report.matchedClubs)
        assertEquals(364, beforePlan.report.unmatchedClubs)
        assertEquals(2, beforePlan.report.ambiguousClubs)
        assertEquals(8_355, beforePlan.report.importedFc26Players)
        assertEquals(10_050, beforePlan.report.skippedDatasetPlayers)
        assertEquals(366, beforeAudits.size)
        assertEquals(10_050, beforeAudits.sumOf { it.playerCount })

        val install = EuropeanAuditedLowerTierClubTargetMaterializer2026_27.installIntoDefaultData()
        val afterTeams = buildCurrentProFootballUniverse()
        val afterPlan = buildPlan(afterTeams, dataset)
        val afterMatches = afterPlan.report.clubMatches.associateBy { it.sourceClubTeamId }
        val afterAudits = Fc26ClubMatcher.auditCandidates(dataset, afterTeams)

        assertEquals(beforeTeams.size, afterTeams.size)
        assertEquals(afterTeams.size, afterTeams.map { it.id }.distinct().size)
        assertEquals(47, install.factualLowerTierClubs)

        val expectedGainedIds = buildSet {
            Fc26RemainingClubCoverage2026_27.existingTargetNameVariants.forEach {
                add(it.sourceClubTeamId)
            }
            Fc26RemainingClubCoverage2026_27.lowerTierFactualTargets.forEach {
                add(it.sourceClubTeamId)
            }
        }
        assertEquals(89, expectedGainedIds.size)

        val gained = beforeMatches.values.mapNotNull { before ->
            val after = afterMatches.getValue(before.sourceClubTeamId)
            if (before.status != Fc26ClubMatchStatus.MATCHED && after.status == Fc26ClubMatchStatus.MATCHED) {
                after
            } else {
                null
            }
        }
        val lost = beforeMatches.values.mapNotNull { before ->
            val after = afterMatches.getValue(before.sourceClubTeamId)
            if (before.status == Fc26ClubMatchStatus.MATCHED && after.status != Fc26ClubMatchStatus.MATCHED) {
                before to after
            } else {
                null
            }
        }
        val redirected = beforeMatches.values.mapNotNull { before ->
            val after = afterMatches.getValue(before.sourceClubTeamId)
            if (
                before.status == Fc26ClubMatchStatus.MATCHED &&
                after.status == Fc26ClubMatchStatus.MATCHED &&
                (before.targetTeamId != after.targetTeamId || before.targetTeamName != after.targetTeamName)
            ) {
                before to after
            } else {
                null
            }
        }

        assertEquals(expectedGainedIds, gained.mapTo(linkedSetOf()) { it.sourceClubTeamId })
        assertEquals("No previously matched FC26 club may be lost", 0, lost.size)
        assertEquals("No previously matched FC26 club may be redirected", 0, redirected.size)

        val expectedGainedPlayers = dataset.sourceClubs
            .filter { it.sourceClubTeamId in expectedGainedIds }
            .sumOf { it.players.size }
        assertEquals(2_497, expectedGainedPlayers)
        assertEquals(expectedGainedPlayers, gained.sumOf { it.playerCount })

        assertEquals(beforePlan.report.matchedClubs + 89, afterPlan.report.matchedClubs)
        assertEquals(beforePlan.report.unmatchedClubs - 89, afterPlan.report.unmatchedClubs)
        assertEquals(beforePlan.report.ambiguousClubs, afterPlan.report.ambiguousClubs)
        assertEquals(
            beforePlan.report.importedFc26Players + expectedGainedPlayers,
            afterPlan.report.importedFc26Players
        )
        assertEquals(
            beforePlan.report.skippedDatasetPlayers - expectedGainedPlayers,
            afterPlan.report.skippedDatasetPlayers
        )
        assertEquals(275, afterPlan.report.unmatchedClubs)
        assertEquals(2, afterPlan.report.ambiguousClubs)
        assertEquals(10_852, afterPlan.report.importedFc26Players)
        assertEquals(7_553, afterPlan.report.skippedDatasetPlayers)
        assertEquals(277, afterAudits.size)
        assertEquals(7_553, afterAudits.sumOf { it.playerCount })

        // Any club name that survives the materialization unchanged must retain exactly the same ID.
        val lowerTargetKeys = Fc26RemainingClubCoverage2026_27.lowerTierFactualTargets
            .mapTo(hashSetOf()) { it.country to it.canonicalName }
        val afterIdsByIdentity = afterTeams.associate { (it.country to it.name) to it.id }
        val commonUnchangedKeys = beforeIdsByIdentity.keys
            .intersect(afterIdsByIdentity.keys)
            .filterNot { it in lowerTargetKeys }
        val changedLegacyIds = commonUnchangedKeys.mapNotNull { key ->
            val beforeId = beforeIdsByIdentity.getValue(key)
            val afterId = afterIdsByIdentity.getValue(key)
            if (beforeId != afterId) Triple(key, beforeId, afterId) else null
        }
        assertEquals("Unchanged club IDs must not drift", emptyList<Any>(), changedLegacyIds)

        // Every new stable lower-tier target is reversible by Team.id and keeps its audited division.
        Fc26RemainingClubCoverage2026_27.lowerTierFactualTargets.forEach { target ->
            val id = requireNotNull(StableTeamIdentityRegistry.idFor(target.country, target.canonicalName))
            val team = requireNotNull(GlobalFootballSystem.getTeamByGlobalId(id))
            assertEquals(target.canonicalName, team.name)
            assertEquals(target.country, team.country)
            assertEquals(target.division, team.division)
        }

        // FC26 ratings/potential remain source-derived for every real player after the new mappings.
        val sourceByStableId = dataset.players.associateBy { it.stableId }
        val mutatedRatings = afterPlan.players.asSequence()
            .filter { StableRealPlayerIdentity.isRealPlayerId(it.id) }
            .count { player ->
                val source = sourceByStableId[player.id] ?: return@count true
                source.overall != player.force || source.potential != player.potential
            }
        assertEquals(0, mutatedRatings)

        val gainedDetails = gained.sortedWith(
            compareByDescending<Fc26ClubMatch> { it.playerCount }
                .thenBy { it.sourceClubName }
                .thenBy { it.sourceClubTeamId }
        ).map { match ->
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
            "phase" to "9.11A2",
            "source" to linkedMapOf(
                "dataset" to dataset.manifest.datasetSource,
                "datasetVersion" to dataset.manifest.datasetVersion,
                "datasetPlayers" to dataset.players.size,
                "datasetClubs" to dataset.sourceClubs.size
            ),
            "implementation" to linkedMapOf(
                "existingTargetNameVariants" to Fc26RemainingClubCoverage2026_27.existingTargetNameVariants.size,
                "lowerTierFactualClubs" to install.factualLowerTierClubs,
                "lowerTierCountries" to install.countries,
                "implementedClubs" to expectedGainedIds.size,
                "implementedPlayers" to expectedGainedPlayers,
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
                "skippedPlayers" to beforePlan.report.skippedDatasetPlayers,
                "blockedPlayers" to beforeAudits.sumOf { it.playerCount }
            ),
            "after" to linkedMapOf(
                "targetTeams" to afterTeams.size,
                "matchedClubs" to afterPlan.report.matchedClubs,
                "unmatchedClubs" to afterPlan.report.unmatchedClubs,
                "ambiguousClubs" to afterPlan.report.ambiguousClubs,
                "importedPlayers" to afterPlan.report.importedFc26Players,
                "skippedPlayers" to afterPlan.report.skippedDatasetPlayers,
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
                "fc26RatingsMutated" to false,
                "fc26PotentialMutated" to false,
                "fc26AttributesMutated" to false,
                "efootballOnlyPlayersImported" to false,
                "roomMigrationRequired" to false,
                "roomVersion" to 21,
                "lostPreviouslyMatchedClubs" to lost.size,
                "redirectedPreviouslyMatchedClubs" to redirected.size,
                "unchangedLegacyIdsChanged" to changedLegacyIds.size
            )
        )

        val root = findRepositoryRoot()
        val reportFile = File(root, "reports/fc26_remaining_lower_tier_coverage_report.json")
        reportFile.parentFile.mkdirs()
        reportFile.writeText(GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", Charsets.UTF_8)

        println(
            "FC26_9_11A2 beforeMatched=${beforePlan.report.matchedClubs} " +
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
