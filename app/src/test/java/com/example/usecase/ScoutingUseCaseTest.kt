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

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class ScoutingUseCaseTest {

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
    fun buyGlobalScoutReveal_succeeds_when_funds_available() = runTest {
        val save = GameSave(id = 1, bankBalance = 2_000_000L, globalScoutRevealWeeksRemaining = 0)
        repository.saveGameSave(save)

        val result = useCase.buyGlobalScoutReveal(save, weeks = 4)

        assertTrue(result is ScoutingUseCase.ScoutingResult.Success)
        val success = result as ScoutingUseCase.ScoutingResult.Success
        assertEquals(1_400_000L, success.updatedSave.bankBalance)
        assertEquals(4, success.updatedSave.globalScoutRevealWeeksRemaining)
    }

    @Test
    fun buyGlobalScoutReveal_fails_when_insufficient_funds() = runTest {
        val save = GameSave(id = 1, bankBalance = 100_000L, globalScoutRevealWeeksRemaining = 0)
        repository.saveGameSave(save)

        val result = useCase.buyGlobalScoutReveal(save, weeks = 4)

        assertTrue(result is ScoutingUseCase.ScoutingResult.Error)
    }

    @Test
    fun buyScoutReport_charges_advertised_price_and_marks_the_selected_player() = runTest {
        val save = GameSave(id = 1, bankBalance = 1_000_000L, globalScoutRevealWeeksRemaining = 0)
        repository.saveGameSave(save)
        repository.savePlayers(
            listOf(
                Player(id = 10, teamId = 1, name = "Alvo 1", age = 22, position = "ATA", force = 78, scoutedLevel = 0)
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
    fun buyScoutReport_fails_when_insufficient_funds() = runTest {
        val save = GameSave(id = 1, bankBalance = 40_000L, globalScoutRevealWeeksRemaining = 0)
        repository.saveGameSave(save)
        repository.savePlayers(
            listOf(
                Player(id = 11, teamId = 1, name = "Alvo 2", age = 24, position = "ZAG", force = 70, scoutedLevel = 0)
            )
        )

        val result = useCase.buyScoutReport(save, playerId = 11, level = 1)

        assertTrue(result is ScoutingUseCase.ScoutingResult.Error)
        assertEquals(40_000L, repository.getGameSave()?.bankBalance ?: -1L)
    }

    @Test
    fun getObservedForceRange_returns_exact_force_if_scouted_or_user_team() {
        val player = Player(id = 1, teamId = 1, name = "Test", age = 22, position = "ATA", force = 78, scoutedLevel = 5)
        val forceStr = player.getObservedForce(isGlobalReveal = false, isUserTeam = true)
        assertEquals("78", forceStr)
    }
}
