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
    fun buyScoutReport_charges_the_price_advertised_for_each_level() = runTest {
        val save = GameSave(id = 1, bankBalance = 1_000_000L, globalScoutRevealWeeksRemaining = 0)
        repository.saveGameSave(save)

        val level1 = useCase.buyScoutReport(save, level = 1)
        assertTrue(level1 is ScoutingUseCase.ScoutingResult.Success)
        assertEquals(
            1_000_000L - 50_000L,
            (level1 as ScoutingUseCase.ScoutingResult.Success).updatedSave.bankBalance
        )
        assertEquals(1, level1.updatedSave.globalScoutRevealWeeksRemaining)

        val afterLevel1 = level1.updatedSave
        val level3 = useCase.buyScoutReport(afterLevel1, level = 3)
        assertTrue(level3 is ScoutingUseCase.ScoutingResult.Success)
        assertEquals(
            afterLevel1.bankBalance - 150_000L,
            (level3 as ScoutingUseCase.ScoutingResult.Success).updatedSave.bankBalance
        )

        val afterLevel3 = level3.updatedSave
        val level5 = useCase.buyScoutReport(afterLevel3, level = 5)
        assertTrue(level5 is ScoutingUseCase.ScoutingResult.Success)
        assertEquals(
            afterLevel3.bankBalance - 400_000L,
            (level5 as ScoutingUseCase.ScoutingResult.Success).updatedSave.bankBalance
        )
        assertEquals(3, level5.updatedSave.globalScoutRevealWeeksRemaining)
    }

    @Test
    fun buyScoutReport_fails_when_insufficient_funds() = runTest {
        val save = GameSave(id = 1, bankBalance = 40_000L, globalScoutRevealWeeksRemaining = 0)
        repository.saveGameSave(save)

        val result = useCase.buyScoutReport(save, level = 1)

        assertTrue(result is ScoutingUseCase.ScoutingResult.Error)
    }

    @Test
    fun getObservedForceRange_returns_exact_force_if_scouted_or_user_team() {
        val player = Player(id = 1, teamId = 1, name = "Test", age = 22, position = "ATA", force = 78, scoutedLevel = 5)
        val forceStr = player.getObservedForce(isGlobalReveal = false, isUserTeam = true)
        assertEquals("78", forceStr)
    }
}
