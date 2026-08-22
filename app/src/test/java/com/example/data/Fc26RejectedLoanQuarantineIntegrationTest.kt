package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Fc26RejectedLoanQuarantineIntegrationTest {

    @Test
    fun `full FC26 snapshot quarantines every unresolved ownership signal`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataset = requireNotNull(Fc26NormalizedDatasetLoader.loadValidatedOrNull(context.assets))
        val teams = buildCurrentUniverse()
        val plan = Fc26SeedPlanner.build(
            teams = teams,
            dataset = dataset,
            proceduralRosterFactory = { team ->
                DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
            }
        )

        val playersById = plan.players.associateBy { it.id }
        val sourceById = dataset.players.associateBy { it.stableId }
        val loansByPlayerId = plan.loans.associateBy { it.playerId }
        val quarantinedStatuses = setOf(
            Fc26LoanResolutionStatus.OWNER_NOT_FOUND,
            Fc26LoanResolutionStatus.AMBIGUOUS_OWNER,
            Fc26LoanResolutionStatus.BORROWER_NOT_FOUND,
            Fc26LoanResolutionStatus.AMBIGUOUS_BORROWER
        )
        val quarantinedResolutions = plan.report.loanResolutions.filter { it.status in quarantinedStatuses }

        assertEquals(plan.report.rejectedLoans, quarantinedResolutions.size)
        assertEquals(
            plan.report.ownerNotFound,
            quarantinedResolutions.count { it.status == Fc26LoanResolutionStatus.OWNER_NOT_FOUND }
        )
        assertEquals(
            plan.report.borrowerNotFound,
            quarantinedResolutions.count { it.status == Fc26LoanResolutionStatus.BORROWER_NOT_FOUND }
        )
        assertEquals(
            plan.report.ambiguousLoans,
            quarantinedResolutions.count {
                it.status == Fc26LoanResolutionStatus.AMBIGUOUS_OWNER ||
                    it.status == Fc26LoanResolutionStatus.AMBIGUOUS_BORROWER
            }
        )
        assertTrue("The current FC26 snapshot must exercise borrower-unresolved quarantine",
            quarantinedResolutions.any {
                it.status == Fc26LoanResolutionStatus.BORROWER_NOT_FOUND ||
                    it.status == Fc26LoanResolutionStatus.AMBIGUOUS_BORROWER
            }
        )

        quarantinedResolutions.forEach { resolution ->
            val player = requireNotNull(playersById[resolution.playerId])
            val source = requireNotNull(sourceById[resolution.playerId])
            val metadata = requireNotNull(player.sourceMetadataOrNull())

            assertNull("Rejected ownership signal must not create runtime club ownership", player.teamId)
            assertTrue("Rejected ownership signal must stay fail-closed", player.isOnLoan)
            assertTrue(player.isFc26LoanOwnershipQuarantined())
            assertNull("Unresolved ownership must never be invented", player.originalTeamId)
            assertNull("Rejected signal must never create a PlayerLoan", loansByPlayerId[resolution.playerId])
            assertEquals(0, player.contractDurationWeeks)
            assertEquals(0L, player.salary)
            assertEquals("LOAN_OWNERSHIP_UNRESOLVED", metadata.assignmentStatus)
            assertEquals(resolution.status.name, metadata.loanResolutionStatus)
            assertEquals("NOT_AVAILABLE", metadata.loanTemporalCoverage)
            assertEquals(resolution.ownerTeamId, metadata.loanOwnerTeamId)
            assertEquals(resolution.borrowerTeamId, metadata.loanBorrowerTeamId)
            assertTrue("Source contract provenance must survive quarantine", (metadata.sourceContractDurationWeeks ?: 0) > 0)
            assertTrue("Source salary provenance must survive quarantine", (metadata.sourceSalary ?: 0L) > 0L)
            assertEquals(source.stableId, player.id)
            assertEquals(source.overall, player.force)
            assertEquals(source.potential, player.potential)
            assertEquals(source.atributos, player.atributos)
        }

        assertEquals(plan.players.size, plan.players.map { it.id }.distinct().size)
        assertEquals(plan.loans.size, plan.loans.map { it.playerId }.distinct().size)
    }

    private fun buildCurrentUniverse(): List<Team> = buildList {
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
}
