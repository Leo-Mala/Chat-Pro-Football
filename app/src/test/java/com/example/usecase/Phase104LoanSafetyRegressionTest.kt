package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Atributos
import com.example.data.Fc26LoanPolicy
import com.example.data.Fc26LoanResolution
import com.example.data.Fc26LoanResolutionStatus
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.PlayerLoan
import com.example.data.Team
import com.example.data.isFc26LoanOwnershipQuarantined
import com.example.data.markFc26LoanResolution
import com.example.ui.viewmodel.IncomingOffer
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
class Phase104LoanSafetyRegressionTest {

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
    fun `snapshot loan closes in the final main-contract week`() = runTest {
        seedTeams()
        val player = loanedPlayer(contractWeeks = 1)
        repository.savePlayers(listOf(player))
        repository.saveLoan(snapshotLoan())
        val save = GameSave(playerTeamId = BORROWER_ID, bankBalance = 10_000_000L)
        repository.saveGameSave(save)

        FinanceUseCase(repository).processWeeklyFinances(save, homeMatchCount = 0)

        assertNull(repository.getActiveLoanForPlayer(PLAYER_ID))
        val expired = requireNotNull(repository.getPlayer(PLAYER_ID))
        assertNull(expired.teamId)
        assertNull(expired.originalTeamId)
        assertFalse(expired.isOnLoan)
        assertEquals(0, expired.loanWeeksRemaining)
        assertEquals(0, expired.contractDurationWeeks)
        assertEquals(0L, expired.salary)
    }

    @Test
    fun `CPU owner renews retained snapshot loanee before finance expiry`() = runTest {
        seedTeams()
        val before = loanedPlayer(contractWeeks = 1)
        repository.savePlayers(listOf(before))
        repository.saveLoan(snapshotLoan())
        // Buyer FC is the human club, so OWNER_ID remains CPU-managed.
        val save = GameSave(playerTeamId = BUYER_ID, bankBalance = 10_000_000L)
        repository.saveGameSave(save)

        val renewedCount = CpuSquadManagementUseCase(repository).renewCpuContractsBeforeWeeklyTick()

        assertEquals(1, renewedCount)
        val renewed = requireNotNull(repository.getPlayer(PLAYER_ID))
        assertEquals(156, renewed.contractDurationWeeks)
        assertEquals(BORROWER_ID, renewed.teamId)
        assertEquals(OWNER_ID, renewed.originalTeamId)
        assertTrue(renewed.isOnLoan)
        assertEquals(snapshotLoan(), repository.getActiveLoanForPlayer(PLAYER_ID))

        FinanceUseCase(repository).processWeeklyFinances(save, homeMatchCount = 0)

        val afterFinance = requireNotNull(repository.getPlayer(PLAYER_ID))
        assertEquals(156, afterFinance.contractDurationWeeks)
        assertEquals(BORROWER_ID, afterFinance.teamId)
        assertEquals(OWNER_ID, afterFinance.originalTeamId)
        assertTrue(afterFinance.isOnLoan)
        assertEquals(snapshotLoan(), repository.getActiveLoanForPlayer(PLAYER_ID))
    }

    @Test
    fun `stale active loan cannot move player during explicit return`() = runTest {
        seedTeams()
        repository.savePlayers(listOf(loanedPlayer()))
        val loan = snapshotLoan()
        repository.saveLoan(loan)
        val stale = requireNotNull(repository.getPlayer(PLAYER_ID)).copy(
            teamId = BUYER_ID,
            originalTeamId = null,
            isOnLoan = false,
            loanWeeksRemaining = 0
        )
        repository.updatePlayer(stale)

        val result = LoanLifecycleUseCase(repository).returnToOwner(PLAYER_ID)

        assertTrue(result is LoanLifecycleUseCase.Result.Rejected)
        assertTrue((result as LoanLifecycleUseCase.Result.Rejected).reason.contains("inconsistente"))
        assertEquals(stale, repository.getPlayer(PLAYER_ID))
        assertEquals(loan, repository.getActiveLoanForPlayer(PLAYER_ID))
    }

    @Test
    fun `borrower cannot renew main contract of a resolved loanee`() = runTest {
        seedTeams()
        val before = loanedPlayer()
        repository.savePlayers(listOf(before))
        repository.saveLoan(snapshotLoan())
        repository.saveGameSave(GameSave(playerTeamId = BORROWER_ID))

        val result = ContractLifecycleUseCase(repository).renewPlayerContract(PLAYER_ID, 52)

        assertTrue(result is ContractLifecycleUseCase.RenewalResult.Rejected)
        assertTrue((result as ContractLifecycleUseCase.RenewalResult.Rejected).reason.contains("proprietário"))
        assertEquals(before, repository.getPlayer(PLAYER_ID))
        assertEquals(snapshotLoan(), repository.getActiveLoanForPlayer(PLAYER_ID))
    }

    @Test
    fun `owner can renew main contract while resolved loan remains intact`() = runTest {
        seedTeams()
        val before = loanedPlayer()
        repository.savePlayers(listOf(before))
        repository.saveLoan(snapshotLoan())
        repository.saveGameSave(GameSave(playerTeamId = OWNER_ID))

        val result = ContractLifecycleUseCase(repository).renewPlayerContract(PLAYER_ID, 52)

        assertTrue(result is ContractLifecycleUseCase.RenewalResult.Success)
        val renewed = requireNotNull(repository.getPlayer(PLAYER_ID))
        assertEquals(before.contractDurationWeeks + 52, renewed.contractDurationWeeks)
        assertEquals((before.salary * 1.1).toLong(), renewed.salary)
        assertEquals(BORROWER_ID, renewed.teamId)
        assertEquals(OWNER_ID, renewed.originalTeamId)
        assertTrue(renewed.isOnLoan)
        assertEquals(snapshotLoan(), repository.getActiveLoanForPlayer(PLAYER_ID))
    }

    @Test
    fun `stale PlayerLoan cannot authorize contract renewal`() = runTest {
        seedTeams()
        repository.savePlayers(listOf(loanedPlayer()))
        repository.saveLoan(snapshotLoan())
        repository.saveGameSave(GameSave(playerTeamId = OWNER_ID))
        val stale = requireNotNull(repository.getPlayer(PLAYER_ID)).copy(originalTeamId = null)
        repository.updatePlayer(stale)

        val result = ContractLifecycleUseCase(repository).renewPlayerContract(PLAYER_ID, 52)

        assertTrue(result is ContractLifecycleUseCase.RenewalResult.Rejected)
        assertTrue((result as ContractLifecycleUseCase.RenewalResult.Rejected).reason.contains("inconsistente"))
        assertEquals(stale, repository.getPlayer(PLAYER_ID))
    }

    @Test
    fun `quarantined unresolved owner cannot renew or award borrower ownership`() = runTest {
        seedTeams()
        val base = quarantinablePlayer(QUARANTINE_PLAYER_BASE_ID)
        val quarantined = base.markFc26LoanResolution(
            Fc26LoanResolution(
                sourcePlayerId = 90_000L,
                playerId = base.id,
                playerName = base.name,
                ownerSourceName = "Unknown Owner",
                borrowerSourceTeamId = 1L,
                borrowerSourceName = "Borrower FC",
                ownerTeamId = null,
                borrowerTeamId = BORROWER_ID,
                status = Fc26LoanResolutionStatus.OWNER_NOT_FOUND,
                reason = "test rejected ownership"
            )
        )
        repository.savePlayers(listOf(quarantined))
        repository.saveGameSave(GameSave(playerTeamId = BORROWER_ID))

        val result = ContractLifecycleUseCase(repository).renewPlayerContract(quarantined.id, 52)

        assertTrue(result is ContractLifecycleUseCase.RenewalResult.Rejected)
        val persisted = requireNotNull(repository.getPlayer(quarantined.id))
        assertNull("Quarantine must not promote factual borrower to runtime owner", persisted.teamId)
        assertNull(persisted.originalTeamId)
        assertTrue(persisted.isFc26LoanOwnershipQuarantined())
        assertEquals(0, persisted.contractDurationWeeks)
        assertEquals(0L, persisted.salary)
        assertNull(repository.getActiveLoanForPlayer(persisted.id))
    }

    @Test
    fun `unknown and ambiguous owner quarantines block borrower ownership operations`() = runTest {
        seedTeams()
        val save = GameSave(playerTeamId = BORROWER_ID, bankBalance = 50_000_000L)
        repository.saveGameSave(save)
        val transfers = ProcessTransfersUseCase(repository)
        val statuses = listOf(
            Fc26LoanResolutionStatus.OWNER_NOT_FOUND,
            Fc26LoanResolutionStatus.AMBIGUOUS_OWNER
        )

        statuses.forEachIndexed { index, status ->
            val id = QUARANTINE_PLAYER_BASE_ID + index
            val base = quarantinablePlayer(id)
            val quarantined = base.markFc26LoanResolution(
                Fc26LoanResolution(
                    sourcePlayerId = 90_000L + index,
                    playerId = id,
                    playerName = base.name,
                    ownerSourceName = "Unknown Owner $index",
                    borrowerSourceTeamId = 1L,
                    borrowerSourceName = "Borrower FC",
                    ownerTeamId = null,
                    borrowerTeamId = BORROWER_ID,
                    status = status,
                    reason = "test rejected ownership"
                )
            )
            repository.savePlayers(listOf(quarantined))

            assertNull(quarantined.teamId)
            assertNull(quarantined.originalTeamId)
            assertTrue(quarantined.isOnLoan)
            assertTrue(quarantined.isFc26LoanOwnershipQuarantined())
            assertEquals(0, quarantined.contractDurationWeeks)
            assertEquals(0L, quarantined.salary)
            assertEquals(base.id, quarantined.id)
            assertEquals(base.force, quarantined.force)
            assertEquals(base.potential, quarantined.potential)
            assertEquals(base.atributos, quarantined.atributos)
            assertNull(repository.getActiveLoanForPlayer(id))

            val sale = transfers.executeSale(
                save = save,
                player = quarantined,
                price = 1_000_000L,
                currentRoster = List(17) { rosterPlayer(30_000L + index * 100 + it) }
            )
            assertTrue("$status borrower sale must fail closed", sale is ProcessTransfersUseCase.TransferResult.Error)

            val purchase = transfers.executePurchase(
                save = save,
                player = quarantined,
                price = 1_000_000L,
                currentRoster = listOf(quarantined)
            )
            assertTrue("$status borrower purchase/conversion must fail closed", purchase is ProcessTransfersUseCase.TransferResult.Error)

            val reloan = transfers.acceptIncomingOffer(
                save,
                IncomingOffer(
                    id = 50_000L + index,
                    player = quarantined,
                    buyerTeamName = "Buyer FC",
                    buyerTeamId = BUYER_ID,
                    offerType = "EMPRESTIMO",
                    price = 10_000L,
                    durationWeeks = 12
                )
            )
            assertTrue("$status borrower re-loan must fail closed", reloan is ProcessTransfersUseCase.TransferResult.Error)

            assertEquals(quarantined, repository.getPlayer(id))
            assertNull(repository.getActiveLoanForPlayer(id))
            assertEquals(50_000_000L, repository.getGameSave()?.bankBalance)
        }

        assertEquals(statuses.size, repository.getAllPlayers().count { it.id >= QUARANTINE_PLAYER_BASE_ID })
    }

    private suspend fun seedTeams() {
        repository.saveTeams(
            listOf(
                Team(OWNER_ID, "Owner FC", "A", "SP", "Brasil", 1, rating = 80),
                Team(BORROWER_ID, "Borrower FC", "B", "RJ", "Brasil", 1, rating = 75),
                Team(BUYER_ID, "Buyer FC", "C", "MG", "Brasil", 1, rating = 78)
            )
        )
    }

    private fun loanedPlayer(contractWeeks: Int = 80) = Player(
        id = PLAYER_ID,
        teamId = BORROWER_ID,
        originalTeamId = OWNER_ID,
        name = "FC26 Final Week QA",
        age = 24,
        position = "MEI",
        force = 82,
        potential = 87,
        salary = 100_000L,
        contractDurationWeeks = contractWeeks,
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

    private fun quarantinablePlayer(id: Long) = Player(
        id = id,
        teamId = BORROWER_ID,
        name = "Rejected FC26 Loan $id",
        age = 23,
        position = "ATA",
        force = 81,
        potential = 88,
        salary = 90_000L,
        contractDurationWeeks = 52,
        atributos = Atributos(finalizacao = 85, velocidade = 86),
        atributosJson = """{"import":{"source":"FC26","sourcePlayerId":$id,"datasetVersion":"test","birthDateIso":"2003-01-01","primaryPosition":"ST","alternativePositions":[],"sourceClubTeamId":1,"sourceClubName":"Borrower FC","leagueId":1,"leagueName":"Test League","clubLoanedFrom":"Unknown Owner"}}"""
    )

    private fun rosterPlayer(id: Long) = Player(
        id = id,
        teamId = BORROWER_ID,
        name = "Roster $id",
        age = 25,
        position = "MEI",
        force = 60
    )

    companion object {
        private const val OWNER_ID = 10L
        private const val BORROWER_ID = 20L
        private const val BUYER_ID = 30L
        private const val PLAYER_ID = 100L
        private const val QUARANTINE_PLAYER_BASE_ID = 200L
    }
}
