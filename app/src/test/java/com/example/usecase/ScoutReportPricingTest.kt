package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Regressão de preços do relatório de olheiros.
 *
 * A UI anuncia R$ 50.000 / R$ 150.000 / R$ 400.000 nos níveis 1 / 3 / 5 e o
 * relatório deve ser aplicado ao jogador escolhido, sem ativar reveal global.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class ScoutReportPricingTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var useCase: ScoutingUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = GameRepository(db)
        useCase = ScoutingUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun buyScoutReport_charges_advertised_price_and_marks_the_selected_player() = runTest {
        val save = GameSave(id = 1, bankBalance = 1_000_000L, globalScoutRevealWeeksRemaining = 0)
        repository.saveGameSave(save)
        repository.savePlayers(
            listOf(
                Player(
                    id = 10,
                    teamId = 1,
                    name = "Alvo 1",
                    age = 22,
                    position = "ATA",
                    force = 78,
                    scoutedLevel = 0
                )
            )
        )

        val level1Result = useCase.buyScoutReport(save, playerId = 10, level = 1)

        assertTrue(level1Result is ScoutingUseCase.ScoutingResult.Success)
        val level1 = level1Result as ScoutingUseCase.ScoutingResult.Success
        assertEquals(1_000_000L - 50_000L, level1.updatedSave.bankBalance)
        assertEquals(1, repository.getPlayer(10)?.scoutedLevel ?: -1)
        assertEquals(0, level1.updatedSave.globalScoutRevealWeeksRemaining)

        val level5Result = useCase.buyScoutReport(level1.updatedSave, playerId = 10, level = 5)

        assertTrue(level5Result is ScoutingUseCase.ScoutingResult.Success)
        val level5 = level5Result as ScoutingUseCase.ScoutingResult.Success
        assertEquals(1_000_000L - 50_000L - 400_000L, level5.updatedSave.bankBalance)
        assertEquals(5, repository.getPlayer(10)?.scoutedLevel ?: -1)
    }

    @Test
    fun scoutReportCost_matches_the_price_advertised_in_the_ui() {
        assertEquals(50_000L, ScoutingUseCase.scoutReportCost(1))
        assertEquals(150_000L, ScoutingUseCase.scoutReportCost(3))
        assertEquals(400_000L, ScoutingUseCase.scoutReportCost(5))
    }

    @Test
    fun buyScoutReport_fails_when_insufficient_funds() = runTest {
        val save = GameSave(id = 1, bankBalance = 40_000L, globalScoutRevealWeeksRemaining = 0)
        repository.saveGameSave(save)
        repository.savePlayers(
            listOf(
                Player(
                    id = 11,
                    teamId = 1,
                    name = "Alvo 2",
                    age = 24,
                    position = "ZAG",
                    force = 70,
                    scoutedLevel = 0
                )
            )
        )

        val result = useCase.buyScoutReport(save, playerId = 11, level = 1)

        assertTrue(result is ScoutingUseCase.ScoutingResult.Error)
        assertEquals(40_000L, repository.getGameSave()?.bankBalance ?: -1L)
    }
}
