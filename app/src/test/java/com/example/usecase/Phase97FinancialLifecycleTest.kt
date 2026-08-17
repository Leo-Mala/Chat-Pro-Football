package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class Phase97FinancialLifecycleTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var finance: FinanceUseCase
    private lateinit var transfers: ProcessTransfersUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        finance = FinanceUseCase(repository)
        transfers = ProcessTransfersUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `installments weekly interest partial and total loan repayment remain coherent`() = runTest {
        repository.saveTeams(
            listOf(
                Team(id = 1L, name = "Financeiro A", city = "A", state = "BR", division = 1),
                Team(id = 2L, name = "Financeiro B", city = "B", state = "BR", division = 1)
            )
        )
        var save = GameSave(
            currentSeason = 2026,
            currentWeek = 1,
            playerTeamId = 1L,
            coachReputation = 50,
            bankBalance = 10_000_000L,
            stadiumCapacity = 10_000,
            academyWeeklyInvestment = 10_000L,
            socioTorcedoresCount = 0,
            sponsorWeeksRemaining = 0
        )
        repository.saveGameSave(save)

        val target = Player(
            id = 200L,
            teamId = 2L,
            name = "Compra Parcelada",
            age = 24,
            position = "ATA",
            force = 70,
            salary = 30_000L,
            contractDurationWeeks = 100
        )
        repository.savePlayers(listOf(target))

        val purchase = transfers.buyPlayerAdvanced(
            save = save,
            player = target,
            offerPrice = 900_000L,
            installments = 3
        )
        assertTrue(purchase is ProcessTransfersUseCase.TransferResult.Success)
        val purchaseSuccess = purchase as ProcessTransfersUseCase.TransferResult.Success
        save = purchaseSuccess.updatedSave
        assertEquals(9_700_000L, save.bankBalance)
        assertEquals(1L, requireNotNull(repository.getPlayer(200L)).teamId)

        var installment = repository.getAllInstallments().single()
        assertEquals(900_000L, installment.totalAmount)
        assertEquals(300_000L, installment.downPayment)
        assertEquals(300_000L, installment.installmentAmount)
        assertEquals(2, installment.remainingInstallments)
        assertEquals(2, installment.nextDueWeek)
        assertEquals("ACTIVE", installment.status)

        val loanResult = finance.requestLoan(save, 1_000_000L)
        assertTrue(loanResult is FinanceUseCase.FinanceResult.Success)
        save = (loanResult as FinanceUseCase.FinanceResult.Success).updatedSave
        assertEquals(1_000_000L, save.loanAmount)
        assertEquals(10_700_000L, save.bankBalance)

        // Semana 1: a parcela ainda não venceu. Com empréstimo de R$ 1.000.000,
        // o juro semanal canônico é exatamente 0,2% = R$ 2.000.
        save = finance.processWeeklyFinances(save, homeMatchCount = 0)
        val weekOneExpense = repository.getAllTransactions()
            .last { it.type == "DESPESA_SEMANAL" && it.week == 1 }
        val weeklySalary = requireNotNull(repository.getPlayer(200L)).salary
        val expectedWeekOneExpense = weeklySalary + 2_000L + 20_000L + 10_000L
        assertEquals(expectedWeekOneExpense, weekOneExpense.amount)
        assertEquals(2, repository.getAllInstallments().single().remainingInstallments)

        save = save.copy(currentWeek = 2)
        repository.saveGameSave(save)
        save = finance.processWeeklyFinances(save, homeMatchCount = 0)
        installment = repository.getAllInstallments().single()
        assertEquals(1, installment.remainingInstallments)
        assertEquals(3, installment.nextDueWeek)
        assertEquals("ACTIVE", installment.status)
        val weekTwoExpense = repository.getAllTransactions()
            .last { it.type == "DESPESA_SEMANAL" && it.week == 2 }
        assertEquals(expectedWeekOneExpense + 300_000L, weekTwoExpense.amount)

        save = save.copy(currentWeek = 3)
        repository.saveGameSave(save)
        save = finance.processWeeklyFinances(save, homeMatchCount = 0)
        installment = repository.getAllInstallments().single()
        assertEquals(0, installment.remainingInstallments)
        assertEquals("COMPLETED", installment.status)
        assertTrue(repository.getActiveInstallments().isEmpty())

        val partial = finance.repayLoan(save, 400_000L)
        assertTrue(partial is FinanceUseCase.FinanceResult.Success)
        save = (partial as FinanceUseCase.FinanceResult.Success).updatedSave
        assertEquals(600_000L, save.loanAmount)

        val total = finance.repayLoan(save, 999_999_999L)
        assertTrue(total is FinanceUseCase.FinanceResult.Success)
        save = (total as FinanceUseCase.FinanceResult.Success).updatedSave
        assertEquals(0L, save.loanAmount)
        assertEquals(0L, requireNotNull(repository.getGameSave()).loanAmount)

        val installmentTransactions = repository.getAllTransactions().filter {
            it.type == "COMPRA_PARCELADA"
        }
        assertEquals(1, installmentTransactions.size)
        assertTrue(repository.getAllTransactions().any { it.type == "PAGAMENTO_EMPRESTIMO" })
    }
}
