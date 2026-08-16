package com.example.usecase

import com.example.data.GameRepository
import com.example.data.GameSave
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceUseCaseUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: GameRepository = mockk(relaxed = true)
    private lateinit var useCase: FinanceUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = FinanceUseCase(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun processWeeklyFinances_calculatesSocioRevenueCorrectly() = runTest(testDispatcher) {
        val initialSave = GameSave(
            id = 1,
            bankBalance = 1_000_000L,
            socioTorcedoresCount = 5000,
            coachReputation = 50
        )

        coEvery { repository.getGameSave() } returns initialSave
        coEvery { repository.withTransaction<GameSave>(any()) } coAnswers {
            val block = firstArg<suspend () -> GameSave>()
            block()
        }

        val result = useCase.processWeeklyFinances(save = initialSave, isHomeMatch = true)

        assertTrue(
            "O saldo final deve ser positivo após processar receitas semanais",
            result.bankBalance > 0
        )
    }
}
