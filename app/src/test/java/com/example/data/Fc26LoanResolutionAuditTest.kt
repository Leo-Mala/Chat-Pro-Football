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
class Fc26LoanResolutionAuditTest {

    @Test
    fun `audit all FC26 loan markers without inventing duration or fuzzy identity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataset = requireNotNull(Fc26NormalizedDatasetLoader.loadValidatedOrNull(context.assets))
        val teams = buildCurrentProFootballUniverse()
        val audit = Fc26LoanIdentityResolver.audit(dataset, teams)

        assertEquals(18_405, dataset.players.size)
        assertEquals(1_325, dataset.manifest.loanedPlayerCount)
        assertEquals(dataset.manifest.loanedPlayerCount, audit.datasetLoanPlayers)
        assertEquals(audit.datasetLoanPlayers, audit.resolutions.size)
        assertEquals(
            audit.datasetLoanPlayers,
            audit.identityResolvedUndated + audit.unresolvedIdentity
        )
        assertEquals(0, audit.durationResolved)
        assertEquals(0, audit.materializableActiveLoans)
        assertEquals(
            audit.resolutions.size,
            audit.resolutions.map { it.sourcePlayerId }.distinct().size
        )
        assertEquals(
            audit.resolutions.size,
            audit.resolutions.map { it.stablePlayerId }.distinct().size
        )
        assertTrue(
            audit.resolutions
                .filter { it.identityStatus == Fc26LoanIdentityStatus.RESOLVED_IDENTITY_UNDATED }
                .all { it.validDistinctIdentity }
        )
        assertTrue(
            audit.resolutions.all {
                it.ownerEvidence in setOf(
                    Fc26LoanOwnerEvidence.EXACT_SOURCE_CLUB_MATCH,
                    Fc26LoanOwnerEvidence.UNIQUE_STABLE_MATERIALIZED_TARGET_NAME,
                    Fc26LoanOwnerEvidence.NONE
                )
            }
        )

        writeReport(dataset, teams, audit)
    }

    private fun writeReport(
        dataset: Fc26Dataset,
        teams: List<Team>,
        audit: Fc26LoanIdentityAudit
    ) {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val byIdentityStatus = audit.resolutions
            .groupingBy { it.identityStatus.name }
            .eachCount()
            .toSortedMap()
        val byBorrowerStatus = audit.resolutions
            .groupingBy { it.borrowerStatus.name }
            .eachCount()
            .toSortedMap()
        val byOwnerStatus = audit.resolutions
            .groupingBy { it.ownerStatus.name }
            .eachCount()
            .toSortedMap()
        val byOwnerEvidence = audit.resolutions
            .groupingBy { it.ownerEvidence.name }
            .eachCount()
            .toSortedMap()

        val unresolvedOwnerNames = audit.resolutions
            .asSequence()
            .filter { !it.ownerResolved }
            .groupingBy { it.ownerSourceName ?: "<MISSING>" }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { linkedMapOf<String, Any?>("ownerName" to it.key, "players" to it.value) }

        val report = linkedMapOf<String, Any?>(
            "phase" to "9.14B-loan-identity-audit",
            "baselineMainSha" to "9d7753f727c174956b466abf3fb868d3cc812056",
            "datasetVersion" to dataset.manifest.datasetVersion,
            "datasetPlayers" to dataset.players.size,
            "targetTeams" to teams.size,
            "datasetLoanPlayers" to audit.datasetLoanPlayers,
            "borrowerResolved" to audit.borrowerResolved,
            "ownerResolved" to audit.ownerResolved,
            "bothSidesResolved" to audit.bothSidesResolved,
            "identityResolvedUndated" to audit.identityResolvedUndated,
            "unresolvedIdentity" to audit.unresolvedIdentity,
            "durationResolved" to audit.durationResolved,
            "materializableActiveLoans" to audit.materializableActiveLoans,
            "identityStatusBreakdown" to byIdentityStatus,
            "borrowerStatusBreakdown" to byBorrowerStatus,
            "ownerStatusBreakdown" to byOwnerStatus,
            "ownerEvidenceBreakdown" to byOwnerEvidence,
            "unresolvedOwnerNames" to unresolvedOwnerNames,
            "cases" to audit.resolutions.map { resolution ->
                linkedMapOf<String, Any?>(
                    "sourcePlayerId" to resolution.sourcePlayerId,
                    "stablePlayerId" to resolution.stablePlayerId,
                    "playerName" to resolution.playerName,
                    "borrowerSourceClubTeamId" to resolution.borrowerSourceClubTeamId,
                    "borrowerSourceClubName" to resolution.borrowerSourceClubName,
                    "borrowerStatus" to resolution.borrowerStatus.name,
                    "borrowerTargetTeamId" to resolution.borrowerTargetTeamId,
                    "borrowerTargetTeamName" to resolution.borrowerTargetTeamName,
                    "ownerSourceName" to resolution.ownerSourceName,
                    "ownerStatus" to resolution.ownerStatus.name,
                    "ownerEvidence" to resolution.ownerEvidence.name,
                    "ownerSourceClubTeamId" to resolution.ownerSourceClubTeamId,
                    "ownerTargetTeamId" to resolution.ownerTargetTeamId,
                    "ownerTargetTeamName" to resolution.ownerTargetTeamName,
                    "identityStatus" to resolution.identityStatus.name
                )
            }
        )

        val output = File(findRepositoryRoot(), "reports/phase_9_14b_fc26_loan_identity_audit.json")
        output.parentFile.mkdirs()
        output.writeText(gson.toJson(report) + "\n", Charsets.UTF_8)

        println(
            "PHASE_9_14B_LOAN_AUDIT loans=${audit.datasetLoanPlayers} " +
                "borrowerResolved=${audit.borrowerResolved} ownerResolved=${audit.ownerResolved} " +
                "bothResolved=${audit.bothSidesResolved} identityResolvedUndated=${audit.identityResolvedUndated} " +
                "unresolved=${audit.unresolvedIdentity} durationResolved=${audit.durationResolved}"
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
