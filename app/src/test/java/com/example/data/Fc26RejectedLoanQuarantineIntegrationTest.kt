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
    fun `full FC26 snapshot quarantines unresolved owners without inventing ownership`() {
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
        val quarantinedResolutions = plan.report.loanResolutions.filter { resolution ->
            resolution.status == Fc26LoanResolutionStatus.OWNER_NOT_FOUND ||
                resolution.status == Fc26LoanResolutionStatus.AMBIGUOUS_OWNER
        }

        assertTrue("The current FC26 snapshot must exercise unresolved-owner quarantine", quarantinedResolutions.isNotEmpty())
        assertEquals(
            plan.report.ownerNotFound,
            quarantinedResolutions.count { it.status == Fc26LoanResolutionStatus.OWNER_NOT_FOUND }
        )

        quarantinedResolutions.forEach { resolution ->
            val player = requireNotNull(playersById[resolution.playerId])
            val source = requireNotNull(sourceById[resolution.playerId])
            val metadata = requireNotNull(player.sourceMetadataOrNull())

            assertNull("Rejected owner signal must not assign borrower ownership at runtime", player.teamId)
            assertTrue("Rejected owner signal must stay fail-closed", player.isOnLoan)
            assertTrue(player.isFc26LoanOwnershipQuarantined())
            assertNull("Unknown owner must never be invented", player.originalTeamId)
            assertNull("Rejected signal must never create a PlayerLoan", loansByPlayerId[resolution.playerId])
            assertEquals(0, player.contractDurationWeeks)
            assertEquals(0L, player.salary)
            assertEquals("LOAN_OWNERSHIP_UNRESOLVED", metadata.assignmentStatus)
            assertEquals(resolution.status.name, metadata.loanResolutionStatus)
            assertEquals("NOT_AVAILABLE", metadata.loanTemporalCoverage)
            assertEquals("Factual borrower identity stays auditable only in metadata", resolution.borrowerTeamId, metadata.loanBorrowerTeamId)
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
