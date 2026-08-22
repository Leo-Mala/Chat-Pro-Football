package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanMarketEligibilityTest {

    @Test
    fun `borrower sees its loanee as permanent transfer candidate`() {
        val loanee = player(
            teamId = BORROWER_ID,
            originalTeamId = OWNER_ID,
            isOnLoan = true
        )

        assertTrue(loanee.isTransferMarketCandidateFor(BORROWER_ID))
    }

    @Test
    fun `owner does not see loaned out player as acquisition target`() {
        val loanee = player(
            teamId = BORROWER_ID,
            originalTeamId = OWNER_ID,
            isOnLoan = true
        )

        assertFalse(loanee.isTransferMarketCandidateFor(OWNER_ID))
    }

    @Test
    fun `owner can identify its loaned out player for contract management`() {
        val loanee = player(
            teamId = BORROWER_ID,
            originalTeamId = OWNER_ID,
            isOnLoan = true
        )

        assertTrue(loanee.isOwnedLoanedOutBy(OWNER_ID))
        assertFalse(loanee.isOwnedLoanedOutBy(BORROWER_ID))
    }

    @Test
    fun `incomplete loan ownership fails closed in market and owner management`() {
        val inconsistent = player(
            teamId = BORROWER_ID,
            originalTeamId = null,
            isOnLoan = true
        )

        assertFalse(inconsistent.isTransferMarketCandidateFor(BORROWER_ID))
        assertFalse(inconsistent.isTransferMarketCandidateFor(OWNER_ID))
        assertFalse(inconsistent.isOwnedLoanedOutBy(OWNER_ID))
    }

    @Test
    fun `invalid target team identity fails closed in market and owner management`() {
        val loanee = player(
            teamId = BORROWER_ID,
            originalTeamId = OWNER_ID,
            isOnLoan = true
        )

        assertFalse(loanee.isTransferMarketCandidateFor(null))
        assertFalse(loanee.isTransferMarketCandidateFor(0L))
        assertFalse(loanee.isTransferMarketCandidateFor(-1L))
        assertFalse(loanee.isOwnedLoanedOutBy(null))
        assertFalse(loanee.isOwnedLoanedOutBy(0L))
        assertFalse(loanee.isOwnedLoanedOutBy(-1L))
    }

    @Test
    fun `normal own player remains excluded and external player remains available`() {
        assertFalse(player(teamId = OWNER_ID).isTransferMarketCandidateFor(OWNER_ID))
        assertTrue(player(teamId = BORROWER_ID).isTransferMarketCandidateFor(OWNER_ID))
        assertFalse(player(teamId = OWNER_ID).isOwnedLoanedOutBy(OWNER_ID))
    }

    private fun player(
        teamId: Long?,
        originalTeamId: Long? = null,
        isOnLoan: Boolean = false
    ) = Player(
        id = 77L,
        teamId = teamId,
        originalTeamId = originalTeamId,
        name = "Loan Market QA",
        age = 24,
        position = "MEI",
        force = 75,
        isOnLoan = isOnLoan
    )

    companion object {
        private const val OWNER_ID = 10L
        private const val BORROWER_ID = 20L
    }
}
