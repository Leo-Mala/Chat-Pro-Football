package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.GameCalendar
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Team
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeepDetailedMovementAliasTest {

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
    fun userCountryMovesAcrossFourthAndFifthLevelsWhenFixturesUseDivAliases() = runBlocking {
        val fourthChampion = team(401, "D Campeão", division = 4, rating = 70)
        val fourthBottom = team(402, "D Rebaixado", division = 4, rating = 99)
        val fifthBottom = team(501, "E Segundo", division = 5, rating = 99)
        val fifthChampion = team(502, "E Promovido", division = 5, rating = 40)
        repository.saveTeams(listOf(fourthChampion, fourthBottom, fifthBottom, fifthChampion))

        val save = GameSave(
            currentSeason = 2026,
            currentWeek = GameCalendar.WEEKS_PER_SEASON,
            playerTeamId = fourthChampion.id
        )
        repository.saveGameSave(save)
        repository.saveFixtures(
            listOf(
                fixture(1, fourthChampion.id, fourthBottom.id, 2, 0, "DIV_4"),
                fixture(2, fourthBottom.id, fourthChampion.id, 0, 1, "DIV_4"),
                fixture(3, fifthChampion.id, fifthBottom.id, 2, 0, "DIV_5"),
                fixture(4, fifthBottom.id, fifthChampion.id, 0, 1, "DIV_5")
            )
        )

        SeasonTransitionUseCase(
            repository = repository,
            generateCalendarUseCase = GenerateCalendarUseCase(repository),
            databaseIntegrityUseCase = DatabaseIntegrityUseCase(repository)
        ).advanceToNextSeason(save)

        val updated = repository.getAllTeams().associateBy { it.id }
        assertEquals(4, updated.getValue(fourthChampion.id).division)
        assertEquals(5, updated.getValue(fourthBottom.id).division)
        assertEquals(5, updated.getValue(fifthBottom.id).division)
        assertEquals(4, updated.getValue(fifthChampion.id).division)
    }

    private fun team(
        id: Long,
        name: String,
        division: Int,
        rating: Int
    ) = Team(
        id = id,
        name = name,
        city = name,
        state = "MG",
        country = "Brasil",
        division = division,
        rating = rating
    )

    private fun fixture(
        id: Long,
        homeId: Long,
        awayId: Long,
        homeScore: Int,
        awayScore: Int,
        competitionType: String
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
