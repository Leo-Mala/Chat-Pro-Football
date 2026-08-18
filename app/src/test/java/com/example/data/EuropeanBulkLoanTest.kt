package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EuropeanBulkLoanTest {
    @Test fun `loan is represented once and keeps stable owner borrower identity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataset = EuropeanCanonicalDatasetLoader.loadForTesting(context.assets)
        val loan = dataset.loans.single()
        val persisted = loan.toPlayerLoan()
        assertEquals("Alejandro Garnacho", loan.player.fullName)
        assertEquals(4L, loan.ownerTeamId)
        assertEquals(7L, loan.borrowerTeamId)
        assertEquals(loan.player.stableId, persisted.playerId)
        assertEquals(loan.ownerTeamId, persisted.ownerTeamId)
        assertEquals(loan.borrowerTeamId, persisted.borrowerTeamId)
        assertTrue(StableRealPlayerIdentity.isRealPlayerId(persisted.playerId))
        val activeIds = dataset.squads.flatMap { it.players }.map { it.stableId }.toSet()
        assertFalse(loan.player.stableId in activeIds)
    }
}
