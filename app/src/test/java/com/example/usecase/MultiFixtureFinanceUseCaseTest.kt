package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class MultiFixtureFinanceUseCaseTest {

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
    fun twoHomeMatchesCreditSecondGateWithoutRepeatingWeeklySponsor() = runTest {
        val save = GameSave(
            coachName = "Finance QA",
            coachReputation = 50,
            currentSeason = 2026,
            currentWeek = 31,
            playerTeamId = 1L,
            bankBalance = 1_000_000L,
            stadiumCapacity = 10_000,
            ticketPrice = 20.0,
            sponsorName = "Patrocinador QA",
            sponsorWeekly = 1_000L,
            sponsorWeeksRemaining = 10,
            socioTorcedoresCount = 0,
            academyWeeklyInvestment = 0L
        )
        repository.saveGameSave(save)

        val useCase = MultiFixtureFinanceUseCase(repository, FinanceUseCase(repository))
        val result = useCase.processWeeklyFinances(
            save = save,
            homeMatchCount = 2,
            userPlayers = emptyList()
        )

        val ticketPerMatch = MultiFixtureFinanceUseCase.calculateTicketRevenuePerHomeMatch(save)
        val effectiveSocios = (save.coachReputation * 150L)
        val socioRevenue = effectiveSocios * 30L
        val sponsorRevenue = save.sponsorWeekly
        val salaryMinimum = 30_000L
        val maintenance = save.stadiumCapacity * 2L
        val expectedBank = save.bankBalance +
            socioRevenue + sponsorRevenue + (ticketPerMatch * 2L) -
            salaryMinimum - maintenance

        assertEquals(expectedBank, result.bankBalance)
        assertEquals(9, result.sponsorWeeksRemaining)
    }

    @Test
    fun zeroOrOneHomeMatchDoNotCreateAdditionalGateRevenue() = runTest {
        val save = GameSave(
            coachName = "Finance QA",
            coachReputation = 40,
            currentSeason = 2026,
            currentWeek = 10,
            playerTeamId = 1L,
            bankBalance = 500_000L,
            stadiumCapacity = 8_000,
            ticketPrice = 25.0,
            sponsorWeekly = 2_000L,
            sponsorWeeksRemaining = 5
        )

        repository.saveGameSave(save)
        val oneHome = MultiFixtureFinanceUseCase(repository, FinanceUseCase(repository))
            .processWeeklyFinances(save, homeMatchCount = 1)

        repository.saveGameSave(save)
        val baselineRepository = repository
        val directOneHome = FinanceUseCase(baselineRepository)
            .processWeeklyFinances(save, isHomeMatch = true)

        assertEquals(directOneHome.bankBalance, oneHome.bankBalance)
    }
}
