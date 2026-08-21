package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Atributos
import com.example.data.Fc26LoanPolicy
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.PlayerLoan
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase104SafeLoanLifecycleTest {

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
    fun `unknown end FC26 loan remains active without fabricated countdown`() = runTest {
        seedLoanState(repository)
        val save = GameSave(playerTeamId = BORROWER_ID, bankBalance = 10_000_000L)
        repository.saveGameSave(save)

        FinanceUseCase(repository).processWeeklyFinances(save, homeMatchCount = 0)

        val active = requireNotNull(repository.getActiveLoanForPlayer(PLAYER_ID))
        val player = requireNotNull(repository.getPlayer(PLAYER_ID))
        assertTrue(Fc26LoanPolicy.isUnknownEndSnapshotLoan(active))
        assertEquals(OWNER_ID, active.ownerTeamId)
        assertEquals(BORROWER_ID, active.borrowerTeamId)
        assertEquals(BORROWER_ID, player.teamId)
        assertEquals(OWNER_ID, player.originalTeamId)
        assertTrue(player.isOnLoan)
        assertEquals(0, player.loanWeeksRemaining)
    }

    @Test
    fun `loan return preserves identity attributes and contract and is idempotent`() = runTest {
        val before = seedLoanState(repository)
        val lifecycle = LoanLifecycleUseCase(repository)

        val first = lifecycle.returnToOwner(PLAYER_ID)
        assertTrue(first is LoanLifecycleUseCase.Result.Returned)
        val returned = requireNotNull(repository.getPlayer(PLAYER_ID))
        assertEquals(PLAYER_ID, returned.id)
        assertEquals(OWNER_ID, returned.teamId)
        assertNull(returned.originalTeamId)
        assertFalse(returned.isOnLoan)
        assertEquals(before.force, returned.force)
        assertEquals(before.potential, returned.potential)
        assertEquals(before.atributos, returned.atributos)
        assertEquals(before.contractDurationWeeks, returned.contractDurationWeeks)
        assertNull(repository.getActiveLoanForPlayer(PLAYER_ID))

        val second = lifecycle.returnToOwner(PLAYER_ID)
        assertTrue(second is LoanLifecycleUseCase.Result.AlreadyClosed)
        assertEquals(returned, repository.getPlayer(PLAYER_ID))
    }

    @Test
    fun `same deterministic snapshot loan upserts without duplication`() = runTest {
        seedTeams(repository)
        val player = loanedPlayer()
        repository.savePlayers(listOf(player))
        val loan = snapshotLoan()

        repository.saveLoan(loan)
        repository.saveLoan(loan)

        assertEquals(1, repository.getActiveLoans().size)
        assertEquals(loan, repository.getActiveLoanForPlayer(PLAYER_ID))
    }

    @Test
    fun `permanent purchase closes active loan and charges exactly once`() = runTest {
        seedLoanState(repository)
        val save = GameSave(playerTeamId = BUYER_ID, bankBalance = 100_000_000L, coachReputation = 80)
        repository.saveGameSave(save)
        val transfers = ProcessTransfersUseCase(repository)
        val price = 5_000_000L

        val first = transfers.executePurchase(
            save = save,
            player = requireNotNull(repository.getPlayer(PLAYER_ID)),
            price = price,
            currentRoster = emptyList()
        )
        assertTrue(first is ProcessTransfersUseCase.TransferResult.Success)
        val afterFirst = requireNotNull(repository.getGameSave())
        val bought = requireNotNull(repository.getPlayer(PLAYER_ID))
        assertEquals(100_000_000L - price, afterFirst.bankBalance)
        assertEquals(BUYER_ID, bought.teamId)
        assertNull(bought.originalTeamId)
        assertFalse(bought.isOnLoan)
        assertNull(repository.getActiveLoanForPlayer(PLAYER_ID))

        val second = transfers.executePurchase(
            save = afterFirst,
            player = bought,
            price = price,
            currentRoster = listOf(bought)
        )
        assertTrue(second is ProcessTransfersUseCase.TransferResult.Error)
        assertEquals(afterFirst.bankBalance, repository.getGameSave()?.bankBalance)
        assertEquals(bought, repository.getPlayer(PLAYER_ID))
    }

    @Test
    fun `borrower cannot sell player it does not own`() = runTest {
        seedLoanState(repository)
        val save = GameSave(playerTeamId = BORROWER_ID, bankBalance = 10_000_000L)
        repository.saveGameSave(save)
        val player = requireNotNull(repository.getPlayer(PLAYER_ID))
        val rosterGate = List(17) { index ->
            Player(
                id = 10_000L + index,
                teamId = BORROWER_ID,
                name = "Roster $index",
                age = 25,
                position = "MEI",
                force = 60
            )
        }

        val result = ProcessTransfersUseCase(repository).executeSale(
            save = save,
            player = player,
            price = 1_000_000L,
            currentRoster = rosterGate
        )

        assertTrue(result is ProcessTransfersUseCase.TransferResult.Error)
        assertTrue((result as ProcessTransfersUseCase.TransferResult.Error).reason.contains("emprestado"))
        assertEquals(10_000_000L, repository.getGameSave()?.bankBalance)
        assertEquals(player, repository.getPlayer(PLAYER_ID))
        assertEquals(snapshotLoan(), repository.getActiveLoanForPlayer(PLAYER_ID))
    }

    @Test
    fun `forced history failure rolls back balance ownership roster and loan together`() = runTest {
        seedLoanState(repository)
        val originalSave = GameSave(playerTeamId = BUYER_ID, bankBalance = 100_000_000L, coachReputation = 80)
        repository.saveGameSave(originalSave)
        val originalPlayer = requireNotNull(repository.getPlayer(PLAYER_ID))
        val originalLoan = requireNotNull(repository.getActiveLoanForPlayer(PLAYER_ID))

        db.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_phase104_transfer_history
            BEFORE INSERT ON transaction_history
            WHEN NEW.type = 'COMPRA'
            BEGIN
                SELECT RAISE(ABORT, 'forced phase 10.4 transfer failure');
            END
            """.trimIndent()
        )

        try {
            ProcessTransfersUseCase(repository).executePurchase(
                save = originalSave,
                player = originalPlayer,
                price = 5_000_000L,
                currentRoster = emptyList()
            )
            fail("Falha SQLite forçada deveria abortar toda a compra definitiva.")
        } catch (_: Exception) {
            // Esperado: Room deve reverter save + Player + PlayerLoan + histórico como uma unidade.
        }

        assertEquals(originalSave, repository.getGameSave())
        assertEquals(originalPlayer, repository.getPlayer(PLAYER_ID))
        assertEquals(originalLoan, repository.getActiveLoanForPlayer(PLAYER_ID))
        assertTrue(repository.getAllTransactions().isEmpty())
    }

    @Test
    fun `loan state stays isolated between independent save databases`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val otherDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val otherRepository = GameRepository(otherDb)
        try {
            seedLoanState(repository)
            seedLoanState(otherRepository)

            LoanLifecycleUseCase(repository).returnToOwner(PLAYER_ID)

            assertNull(repository.getActiveLoanForPlayer(PLAYER_ID))
            val otherLoan = requireNotNull(otherRepository.getActiveLoanForPlayer(PLAYER_ID))
            val otherPlayer = requireNotNull(otherRepository.getPlayer(PLAYER_ID))
            assertEquals(snapshotLoan(), otherLoan)
            assertEquals(BORROWER_ID, otherPlayer.teamId)
            assertEquals(OWNER_ID, otherPlayer.originalTeamId)
            assertTrue(otherPlayer.isOnLoan)
        } finally {
            otherDb.close()
        }
    }

    private suspend fun seedLoanState(target: GameRepository): Player {
        seedTeams(target)
        val player = loanedPlayer()
        target.savePlayers(listOf(player))
        target.saveLoan(snapshotLoan())
        return player
    }

    private suspend fun seedTeams(target: GameRepository) {
        target.saveTeams(
            listOf(
                Team(OWNER_ID, "Owner FC", "A", "SP", "Brasil", 1, rating = 80),
                Team(BORROWER_ID, "Borrower FC", "B", "RJ", "Brasil", 1, rating = 75),
                Team(BUYER_ID, "Buyer FC", "C", "MG", "Brasil", 1, rating = 78)
            )
        )
    }

    private fun loanedPlayer() = Player(
        id = PLAYER_ID,
        teamId = BORROWER_ID,
        originalTeamId = OWNER_ID,
        name = "FC26 Loan QA",
        age = 24,
        position = "MEI",
        force = 82,
        potential = 87,
        salary = 100_000L,
        contractDurationWeeks = 80,
        isOnLoan = true,
        loanWeeksRemaining = 0,
        atributos = Atributos(passe = 84, visaoJogo = 83, velocidade = 78)
    )

    private fun snapshotLoan() = PlayerLoan(
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

    companion object {
        private const val OWNER_ID = 10L
        private const val BORROWER_ID = 20L
        private const val BUYER_ID = 30L
        private const val PLAYER_ID = 100L
    }
}
