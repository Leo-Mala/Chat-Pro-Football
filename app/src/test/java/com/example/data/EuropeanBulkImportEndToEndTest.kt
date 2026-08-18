package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EuropeanBulkImportEndToEndTest {
    @Test fun `API fixture canonical asset loader planner materializes Team Player PlayerLoan`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataset = EuropeanCanonicalDatasetLoader.loadForTesting(context.assets)
        val teams: List<Team> = dataset.toSeedTeams { 75 }
        val plan = dataset.buildSeedPlan(teams) { team -> error("Fixture must not fallback for ${team.name}") }
        assertEquals(5, teams.size)
        assertEquals(5, plan.factualSquadTeamIds.size)
        assertTrue(plan.proceduralFallbackTeamIds.isEmpty())
        assertTrue(plan.blockedLoans.isEmpty())
        assertEquals(91, plan.players.size)
        assertEquals(1, plan.loans.size)
        val loan: PlayerLoan = plan.loans.single()
        val loanPlayer: Player = plan.players.single { it.id == loan.playerId }
        assertEquals(loan.borrowerTeamId, loanPlayer.teamId)
        assertEquals(loan.ownerTeamId, loanPlayer.originalTeamId)
        assertTrue(loanPlayer.isOnLoan)
    }
}
