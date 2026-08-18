package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EuropeanRealLoanSnapshotTest {

    @Test
    fun `Onana loan keeps factual identity while player acts for borrower`() {
        val loan = AndreOnanaLoan2026_27.snapshot
        val persistedLoan = loan.toPlayerLoan()
        val borrowerPlayer = loan.toBorrowerPlayer(borrowerTeamRating = 78)

        assertEquals(5L, loan.ownerTeamId)
        assertEquals(
            StableTeamIdentityRegistry.idFor("Turquia", "Trabzonspor"),
            loan.borrowerTeamId
        )
        assertNotEquals(loan.ownerTeamId, loan.borrowerTeamId)

        assertEquals(loan.player.stableId, borrowerPlayer.id)
        assertEquals(loan.borrowerTeamId, borrowerPlayer.teamId)
        assertTrue(borrowerPlayer.isOnLoan)
        assertEquals(loan.ownerTeamId, borrowerPlayer.originalTeamId)
        assertEquals(GameCalendar.WEEKS_PER_SEASON, borrowerPlayer.loanWeeksRemaining)

        assertEquals(loan.player.stableId, persistedLoan.playerId)
        assertEquals(loan.ownerTeamId, persistedLoan.ownerTeamId)
        assertEquals(loan.borrowerTeamId, persistedLoan.borrowerTeamId)
        assertEquals("ACTIVE", persistedLoan.status)
    }

    @Test
    fun `loan catalog rejects two active loans for same factual player`() {
        val first = AndreOnanaLoan2026_27.snapshot
        val duplicated = first.copy(
            borrowerCountry = "Espanha",
            borrowerClubName = "Real Madrid"
        )

        var failed = false
        try {
            EuropeanRealLoanCatalog(listOf(first, duplicated))
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun `loan catalog materializes existing V21 entities without changing player id`() {
        val catalog = EuropeanRealLoans.catalog
        val loan = catalog.all().single()
        val ratings = mapOf(loan.borrowerTeamId to 78)

        val players = catalog.materializePlayers(ratings)
        val loans = catalog.materializeLoans()

        assertEquals(1, players.size)
        assertEquals(1, loans.size)
        assertEquals(players.single().id, loans.single().playerId)
        assertEquals(loan.player.stableId, players.single().id)
        assertFalse(StableRealPlayerIdentity.isRealPlayerId(0L))
        assertTrue(StableRealPlayerIdentity.isRealPlayerId(players.single().id))
    }

    @Test
    fun `factual loan sources are official and dated`() {
        val loan = AndreOnanaLoan2026_27.snapshot

        assertEquals("2026-08-18", loan.verifiedAsOfIso)
        assertTrue(loan.sourceRefs.all { it.startsWith("https://www.manutd.com/") })
        assertEquals(2026, loan.season)
        assertEquals(1, loan.startWeek)
        assertEquals(GameCalendar.WEEKS_PER_SEASON, loan.durationWeeks)
    }
}
