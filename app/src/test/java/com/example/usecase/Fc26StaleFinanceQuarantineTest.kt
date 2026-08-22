package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Fc26LoanPolicy
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.PlayerLoan
import com.example.data.Team
import com.example.data.isTransferMarketCandidateFor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Fc26StaleFinanceQuarantineTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `weekly finance invalidates stale snapshot loan without granting borrower ownership`() = runTest {
        repository.saveTeams(
            listOf(
                Team(OWNER_ID, "Owner FC", "A", "SP", "Brasil", 1, rating = 80),
                Team(BORROWER_ID, "Borrower FC", "B", "RJ", "Brasil", 1, rating = 75),
                Team(UNRELATED_ID, "Unrelated FC", "C", "MG", "Brasil", 1, rating = 70)
            )
        )
        val stalePlayer = Player(
            id = PLAYER_ID,
            teamId = BORROWER_ID,
            originalTeamId = UNRELATED_ID,
            name = "Stale FC26 Loan",
            age = 24,
            position = "MEI",
            force = 80,
            salary = 100_000L,
            contractDurationWeeks = 80,
            isOnLoan = true,
            loanWeeksRemaining = 0
        )
        val loan = PlayerLoan(
            id = Fc26LoanPolicy.deterministicLoanId(PLAYER_ID),
            playerId = PLAYER_ID,
            ownerTeamId = OWNER_ID,
            borrowerTeamId = BORROWER_ID,
            startSeason = Fc26LoanPolicy.UNKNOWN_SEASON,
            startWeek = Fc26LoanPolicy.UNKNOWN_WEEK,
            durationWeeks = Fc26LoanPolicy.UNKNOWN_DURATION_WEEKS,
            remainingWeeks = Fc26LoanPolicy.UNKNOWN_DURATION_WEEKS,
            weeklyFee = 0L,
            status = "ACTIVE"
        )
        val save = GameSave(playerTeamId = BORROWER_ID, bankBalance = 20_000_000L)
        repository.savePlayers(listOf(stalePlayer))
        repository.saveLoan(loan)
        repository.saveGameSave(save)

        FinanceUseCase(repository).processWeeklyFinances(save, homeMatchCount = 0)

        assertNull("Invalid relationship must leave the ACTIVE set", repository.getActiveLoanForPlayer(PLAYER_ID))
        val quarantined = requireNotNull(repository.getPlayer(PLAYER_ID))
        assertNull("Untrusted borrower roster cannot become permanent ownership", quarantined.teamId)
        assertNull(quarantined.originalTeamId)
        assertTrue("Incomplete ownership remains visibly fail-closed", quarantined.isOnLoan)
        assertEquals(0, quarantined.loanWeeksRemaining)
        assertEquals(stalePlayer.contractDurationWeeks, quarantined.contractDurationWeeks)
        assertEquals(stalePlayer.salary, quarantined.salary)
        assertFalse(quarantined.isTransferMarketCandidateFor(BORROWER_ID))
        assertFalse(quarantined.isTransferMarketCandidateFor(OWNER_ID))
        assertFalse(quarantined.isTransferMarketCandidateFor(UNRELATED_ID))

        val purchase = ProcessTransfersUseCase(repository).executePurchase(
            save = requireNotNull(repository.getGameSave()),
            player = quarantined,
            price = 1_000_000L,
            currentRoster = emptyList()
        )
        assertTrue(purchase is ProcessTransfersUseCase.TransferResult.Error)
    }

    companion object {
        private const val OWNER_ID = 10L
        private const val BORROWER_ID = 20L
        private const val UNRELATED_ID = 30L
        private const val PLAYER_ID = 100L
    }
}
