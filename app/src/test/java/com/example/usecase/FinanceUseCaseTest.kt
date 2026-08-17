package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.GameSave
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
class FinanceUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var useCase: FinanceUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = GameRepository(db)
        useCase = FinanceUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun requestLoan_successfully_grants_loan_when_within_limit() = runTest {
        val save = GameSave(id = 1, bankBalance = 1_000_000L, loanAmount = 0L)
        repository.saveGameSave(save)

        val result = useCase.requestLoan(save, 5_000_000L)

        assertTrue(result is FinanceUseCase.FinanceResult.Success)
        val success = result as FinanceUseCase.FinanceResult.Success
        assertEquals(6_000_000L, success.updatedSave.bankBalance)
        assertEquals(5_000_000L, success.updatedSave.loanAmount)

        val saved = repository.getGameSave()
        assertEquals(6_000_000L, saved?.bankBalance)
    }

    @Test
    fun requestLoan_fails_when_exceeding_maximum_limit() = runTest {
        val save = GameSave(id = 1, bankBalance = 1_000_000L, loanAmount = 48_000_000L)
        repository.saveGameSave(save)

        val result = useCase.requestLoan(save, 5_000_000L)

        assertTrue(result is FinanceUseCase.FinanceResult.Error)
        val error = result as FinanceUseCase.FinanceResult.Error
        assertTrue(error.reason.contains("limite total"))
    }

    @Test
    fun repayLoan_reduces_bank_balance_and_loan_amount() = runTest {
        val save = GameSave(id = 1, bankBalance = 10_000_000L, loanAmount = 5_000_000L)
        repository.saveGameSave(save)

        val result = useCase.repayLoan(save, 2_000_000L)

        assertTrue(result is FinanceUseCase.FinanceResult.Success)
        val success = result as FinanceUseCase.FinanceResult.Success
        assertEquals(8_000_000L, success.updatedSave.bankBalance)
        assertEquals(3_000_000L, success.updatedSave.loanAmount)
    }

    @Test
    fun upgradeStadium_fails_when_bank_balance_is_insufficient() = runTest {
        val save = GameSave(id = 1, bankBalance = 100_000L, stadiumCapacity = 10_000)
        repository.saveGameSave(save)

        val result = useCase.upgradeStadium(save, 1_000)

        assertTrue(result is FinanceUseCase.FinanceResult.Error)
    }

    @Test
    fun processWeeklyFinances_calculates_income_and_expenses() = runTest {
        val save = GameSave(
            id = 1,
            bankBalance = 2_000_000L,
            socioTorcedoresCount = 5000,
            coachReputation = 50,
            ticketPrice = 30.0,
            stadiumCapacity = 20000,
            loanAmount = 1_000_000L,
            hasHiredPhysio = true
        )
        repository.saveGameSave(save)

        val updatedSave = useCase.processWeeklyFinances(save, isHomeMatch = true)

        assertTrue(updatedSave.bankBalance != 2_000_000L)
    }

    @Test
    fun processWeeklyFinances_counts_every_played_home_match_in_same_week() = runTest {
        val save = GameSave(
            id = 1,
            currentSeason = 2026,
            currentWeek = 31,
            playerTeamId = 10L,
            bankBalance = 2_000_000L,
            socioTorcedoresCount = 5000,
            coachReputation = 50,
            ticketPrice = 30.0,
            stadiumCapacity = 20000,
            sponsorWeeksRemaining = 0
        )
        repository.saveGameSave(save)
        repository.saveFixtures(
            listOf(
                Fixture(
                    id = 100L,
                    season = 2026,
                    week = 31,
                    homeTeamId = 10L,
                    awayTeamId = 20L,
                    competitionType = "SERIE_A",
                    homeScore = 2,
                    awayScore = 0,
                    isPlayed = true
                ),
                Fixture(
                    id = 101L,
                    season = 2026,
                    week = 31,
                    homeTeamId = 10L,
                    awayTeamId = 30L,
                    competitionType = "COPA",
                    homeScore = 1,
                    awayScore = 0,
                    isPlayed = true
                )
            )
        )

        useCase.processWeeklyFinances(save, isHomeMatch = true)

        val income = repository.getAllTransactions().single { it.type == "RECEITA_SEMANAL" }
        // 7,500 effective members * R$30 = 225,000
        // fallback sponsor = 300,000 + 50*15,000 = 1,050,000
        // attendance = 19,500 * R$30 = 585,000 per home match; two matches = 1,170,000
        assertEquals(2_445_000L, income.amount)
    }

    @Test
    fun setTicketPrice_updates_and_clamps_ticket_price() = runTest {
        val save = GameSave(id = 1, ticketPrice = 25.0)
        repository.saveGameSave(save)

        val result = useCase.setTicketPrice(save, 50.0)

        assertTrue(result is FinanceUseCase.FinanceResult.Success)
        val success = result as FinanceUseCase.FinanceResult.Success
        assertEquals(50.0, success.updatedSave.ticketPrice, 0.01)
    }

    @Test
    fun signSponsorshipContract_updates_sponsor_and_grants_bonus() = runTest {
        val save = GameSave(id = 1, bankBalance = 1_000_000L, sponsorName = "Nenhum")
        repository.saveGameSave(save)

        val result = useCase.signSponsorshipContract(
            save = save,
            sponsorName = "Banco Master",
            weeklyPayment = 500_000L,
            durationWeeks = 38,
            upFrontBonus = 2_000_000L
        )

        assertTrue(result is FinanceUseCase.FinanceResult.Success)
        val success = result as FinanceUseCase.FinanceResult.Success
        assertEquals(3_000_000L, success.updatedSave.bankBalance)
        assertEquals("Banco Master", success.updatedSave.sponsorName)
        assertEquals(500_000L, success.updatedSave.sponsorWeekly)
        assertEquals(38, success.updatedSave.sponsorWeeksRemaining)
    }

    @Test
    fun upgradeTrainingCenter_upgrades_team_tc_level() = runTest {
        val save = GameSave(id = 1, bankBalance = 10_000_000L, playerTeamId = 10L)
        repository.saveGameSave(save)
        val team = com.example.data.Team(id = 10L, name = "Meu Time", city = "SP", state = "SP", division = 1, trainingCenterLevel = 1)
        repository.saveTeams(listOf(team))

        val result = useCase.upgradeTrainingCenter(save)

        assertTrue(result is FinanceUseCase.FinanceResult.Success)
        val updatedTeam = repository.getTeam(10L)
        assertEquals(2, updatedTeam?.trainingCenterLevel)
    }
}
