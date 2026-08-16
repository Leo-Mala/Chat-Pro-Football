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
    fun getObservedForceRange_returns_exact_force_if_scouted_or_user_team() {
        val player = Player(id = 1, teamId = 1, name = "Test", age = 22, position = "ATA", force = 78, scoutedLevel = 5)
        val forceStr = player.getObservedForce(isGlobalReveal = false, isUserTeam = true)
        assertEquals("78", forceStr)
    }
}
