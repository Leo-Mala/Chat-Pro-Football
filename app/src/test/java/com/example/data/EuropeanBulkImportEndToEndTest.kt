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
    @Test fun `open data canonical assets materialize Premier League and verified loan`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataset = EuropeanCanonicalDatasetLoader.loadForTesting(context.assets)
        val teams: List<Team> = dataset.toSeedTeams { 75 }
        val plan = dataset.buildSeedPlan(teams) { team ->
            error("FACTUAL dataset must not fallback for ${team.name}")
        }

        assertEquals(20, teams.size)
        assertEquals(20, plan.factualSquadTeamIds.size)
        assertTrue(plan.proceduralFallbackTeamIds.isEmpty())
        assertTrue(plan.blockedLoans.isEmpty())
        assertEquals(486, plan.players.size)
        assertEquals(1, plan.loans.size)

        val garnacho = plan.players.single { it.name == "Alejandro Garnacho" }
        val loan: PlayerLoan = plan.loans.single { it.playerId == garnacho.id }
        val chelseaId = requireNotNull(StableTeamIdentityRegistry.idFor("Inglaterra", "Chelsea FC"))
        val astonVillaId = requireNotNull(StableTeamIdentityRegistry.idFor("Inglaterra", "Aston Villa"))

        assertEquals(chelseaId, loan.ownerTeamId)
        assertEquals(astonVillaId, loan.borrowerTeamId)
        assertEquals(astonVillaId, garnacho.teamId)
        assertEquals(chelseaId, garnacho.originalTeamId)
        assertTrue(garnacho.isOnLoan)
    }
}
