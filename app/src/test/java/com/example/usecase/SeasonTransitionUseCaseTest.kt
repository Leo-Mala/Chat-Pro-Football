package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameCalendar
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class SeasonTransitionUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var useCase: SeasonTransitionUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        useCase = SeasonTransitionUseCase(
            repository = repository,
            generateCalendarUseCase = GenerateCalendarUseCase(repository),
            databaseIntegrityUseCase = DatabaseIntegrityUseCase(repository)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun freeAgent(age: Int = 24) = Player(
        id = 10L,
        teamId = 0L,
        name = "Jogador Teste",
        age = age,
        position = "MEI",
        force = 70
    )

    @Test
    fun transitionBeforeWeek40IsRejectedWithoutChangingCareer() = runTest {
        val save = GameSave(
            id = 1,
            currentSeason = 2026,
            currentWeek = GameCalendar.WEEKS_PER_SEASON - 1,
            playerTeamId = 0L
        )
        repository.saveGameSave(save)
        repository.savePlayers(listOf(freeAgent()))

        try {
            useCase.advanceToNextSeason(save)
            fail("A transição antes da semana 40 deveria ser rejeitada.")
        } catch (_: IllegalArgumentException) {
            // esperado
        }

        val persisted = requireNotNull(repository.getGameSave())
        val player = requireNotNull(repository.getPlayer(10L))
        assertEquals(2026, persisted.currentSeason)
        assertEquals(39, persisted.currentWeek)
        assertEquals(24, player.age)
    }

    @Test
    fun week40TransitionAdvancesSeasonAndAgesPlayerExactlyOnce() = runTest {
        val staleFinalWeekSave = GameSave(
            id = 1,
            currentSeason = 2026,
            currentWeek = GameCalendar.WEEKS_PER_SEASON,
            playerTeamId = 0L
        )
        repository.saveGameSave(staleFinalWeekSave)
        repository.savePlayers(listOf(freeAgent()))

        val firstResult = useCase.advanceToNextSeason(staleFinalWeekSave)

        assertEquals(2027, firstResult.currentSeason)
        assertEquals(1, firstResult.currentWeek)
        assertEquals(25, requireNotNull(repository.getPlayer(10L)).age)

        val secondResult = useCase.advanceToNextSeason(staleFinalWeekSave)

        assertEquals(2027, secondResult.currentSeason)
        assertEquals(1, secondResult.currentWeek)
        assertEquals(
            "Uma chamada repetida com o snapshot antigo não pode envelhecer o jogador duas vezes.",
            25,
            requireNotNull(repository.getPlayer(10L)).age
        )
    }
}
