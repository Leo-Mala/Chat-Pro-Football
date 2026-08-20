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
class ProcessTransfersUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var useCase: ProcessTransfersUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = GameRepository(db)
        useCase = ProcessTransfersUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun buyPlayer_succeeds_when_balance_is_sufficient() = runTest {
        val save = GameSave(id = 1, playerTeamId = 1L, bankBalance = 10_000_000L)
        repository.saveGameSave(save)
        repository.saveTeams(
            listOf(
                Team(id = 1L, name = "Time A", city = "São Paulo", state = "SP", division = 1),
                Team(id = 2L, name = "Time B", city = "Rio de Janeiro", state = "RJ", division = 1)
            )
        )

        val player = Player(
            id = 10L,
            teamId = 2L,
            name = "Craque",
            age = 25,
            position = "ATA",
            force = 80,
            market_value = 3_000_000L
        )
        repository.savePlayers(listOf(player))

        val result = useCase.buyPlayer(save, player, offerPrice = 3_000_000L)

        assertTrue(result is ProcessTransfersUseCase.TransferResult.Success)
        val success = result as ProcessTransfersUseCase.TransferResult.Success
        assertEquals(7_000_000L, success.updatedSave.bankBalance)
        assertEquals(1L, success.updatedPlayer.teamId)
    }

    @Test
    fun buyPlayer_fails_when_balance_is_insufficient() = runTest {
        val save = GameSave(id = 1, playerTeamId = 1L, bankBalance = 1_000_000L)
        repository.saveGameSave(save)

        val player = Player(
            id = 10L,
            teamId = 2L,
            name = "Craque Extraordinario",
            age = 26,
            position = "ATA",
            force = 88,
            market_value = 15_000_000L
        )

        val result = useCase.buyPlayer(save, player, offerPrice = 15_000_000L)

        assertTrue(result is ProcessTransfersUseCase.TransferResult.Error)
        val error = result as ProcessTransfersUseCase.TransferResult.Error
        assertTrue(error.reason.contains("insuficiente"))
    }

    @Test
    fun sellPlayer_succeeds_when_buyer_exists() = runTest {
        val save = GameSave(id = 1, playerTeamId = 1L, bankBalance = 5_000_000L)
        repository.saveGameSave(save)

        val team1 = Team(id = 1L, name = "Time A", city = "Sp", state = "SP", division = 1)
        val team2 = Team(id = 2L, name = "Time B", city = "Rj", state = "RJ", division = 1)
        repository.saveTeams(listOf(team1, team2))

        val roster1 = (1..18).map { id ->
            Player(id = id.toLong(), teamId = 1L, name = "Jogador $id", age = 22, position = "MEI", force = 60)
        }
        repository.savePlayers(roster1)

        val playerToSell = roster1[0]
        val result = useCase.sellPlayer(save, playerToSell, offerPrice = 2_000_000L)

        assertTrue(result is ProcessTransfersUseCase.TransferResult.Success)
        val success = result as ProcessTransfersUseCase.TransferResult.Success
        assertEquals(7_000_000L, success.updatedSave.bankBalance)
    }

    @Test
    fun weekly_contract_tick_preserves_existing_expiration_semantics() = runTest {
        repository.saveTeams(
            listOf(
                Team(id = 1L, name = "Owner", city = "A", state = "AA", division = 1),
                Team(id = 2L, name = "Borrower", city = "B", state = "BB", division = 1)
            )
        )
        repository.savePlayers(
            listOf(
                Player(
                    id = 101L,
                    teamId = 1L,
                    name = "Two Weeks",
                    age = 24,
                    position = "MEI",
                    force = 70,
                    salary = 9_000L,
                    contractDurationWeeks = 2,
                    isStarter = true
                ),
                Player(
                    id = 102L,
                    teamId = 1L,
                    name = "Expires",
                    age = 25,
                    position = "ATA",
                    force = 71,
                    salary = 10_000L,
                    contractDurationWeeks = 1,
                    isStarter = true
                ),
                Player(
                    id = 103L,
                    teamId = 2L,
                    originalTeamId = 1L,
                    name = "Loan Expires",
                    age = 23,
                    position = "DEF",
                    force = 69,
                    salary = 8_000L,
                    contractDurationWeeks = 1,
                    isStarter = true,
                    isOnLoan = true,
                    loanWeeksRemaining = 4
                ),
                Player(
                    id = 104L,
                    teamId = 1L,
                    name = "Already Zero",
                    age = 28,
                    position = "GOL",
                    force = 68,
                    salary = 7_000L,
                    contractDurationWeeks = 0,
                    isStarter = true
                )
            )
        )

        useCase.processWeeklyContractsAndLoans()

        val twoWeeks = requireNotNull(repository.getPlayer(101L))
        assertEquals(1, twoWeeks.contractDurationWeeks)
        assertEquals(1L, twoWeeks.teamId)
        assertEquals(9_000L, twoWeeks.salary)
        assertTrue(twoWeeks.isStarter)

        val expired = requireNotNull(repository.getPlayer(102L))
        assertEquals(0, expired.contractDurationWeeks)
        assertEquals(null, expired.teamId)
        assertEquals(null, expired.originalTeamId)
        assertEquals(0L, expired.salary)
        assertTrue(!expired.isStarter)

        val loanExpired = requireNotNull(repository.getPlayer(103L))
        assertEquals(0, loanExpired.contractDurationWeeks)
        assertEquals(2L, loanExpired.teamId)
        assertEquals(1L, loanExpired.originalTeamId)
        assertEquals(0L, loanExpired.salary)
        assertTrue(loanExpired.isOnLoan)
        assertTrue(!loanExpired.isStarter)

        val alreadyZero = requireNotNull(repository.getPlayer(104L))
        assertEquals(0, alreadyZero.contractDurationWeeks)
        assertEquals(1L, alreadyZero.teamId)
        assertEquals(7_000L, alreadyZero.salary)
        assertTrue(alreadyZero.isStarter)
    }
}
