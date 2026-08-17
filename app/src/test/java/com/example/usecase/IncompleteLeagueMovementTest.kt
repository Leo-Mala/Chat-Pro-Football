package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.GameCalendar
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class IncompleteLeagueMovementTest {

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

    @Test
    fun partialButPlayedLeaguesDoNotTriggerPromotionOrRelegation() = runTest {
        val serieA = (1L..4L).map { id -> team(id, division = 1) }
        val serieB = (5L..8L).map { id -> team(id, division = 2) }
        repository.saveTeams(serieA + serieB)

        val save = GameSave(
            id = 1,
            currentSeason = 2026,
            currentWeek = GameCalendar.WEEKS_PER_SEASON,
            playerTeamId = 1L
        )
        repository.saveGameSave(save)

        // Há apenas uma partida concluída em cada divisão. Para 4 clubes seriam necessárias
        // 12 partidas por divisão (N * (N - 1)) em turno + returno.
        repository.saveFixtures(
            listOf(
                playedFixture(1L, "SERIE_A", 1L, 2L, 0, 3),
                playedFixture(2L, "SERIE_B", 5L, 6L, 4, 0)
            )
        )

        useCase.advanceToNextSeason(save)

        serieA.forEach { original ->
            assertEquals(
                "Série A parcial não pode rebaixar clubes",
                1,
                requireNotNull(repository.getTeam(original.id)).division
            )
        }
        serieB.forEach { original ->
            assertEquals(
                "Série B parcial não pode promover clubes",
                2,
                requireNotNull(repository.getTeam(original.id)).division
            )
        }
    }

    private fun team(id: Long, division: Int) = Team(
        id = id,
        name = "Clube $id",
        city = "Cidade $id",
        state = "BR",
        country = "Brasil",
        division = division,
        rating = 70 + id.toInt()
    )

    private fun playedFixture(
        id: Long,
        competitionType: String,
        homeId: Long,
        awayId: Long,
        homeScore: Int,
        awayScore: Int
    ) = Fixture(
        id = id,
        season = 2026,
        week = id.toInt(),
        homeTeamId = homeId,
        awayTeamId = awayId,
        homeScore = homeScore,
        awayScore = awayScore,
        competitionType = competitionType,
        isPlayed = true
    )
}
