package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Team
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlobalStandingsSeasonTransitionTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var generateCalendarUseCase: GenerateCalendarUseCase
    private lateinit var databaseIntegrityUseCase: DatabaseIntegrityUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        generateCalendarUseCase = GenerateCalendarUseCase(repository)
        databaseIntegrityUseCase = DatabaseIntegrityUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seasonTransitionPersistsGlobalSnapshotBeforeReplacingFixtures() = runBlocking {
        val user = team(1, "Resultado Brasil", "Brasil", 65)
        val ratingFavorite = team(2, "Rating Brasil", "Brasil", 99)
        val argentina = listOf(
            team(10, "Argentina A", "Argentina", 91),
            team(11, "Argentina B", "Argentina", 87),
            team(12, "Argentina C", "Argentina", 81),
            team(13, "Argentina D", "Argentina", 76)
        )
        repository.saveTeams(listOf(user, ratingFavorite) + argentina)

        val save = GameSave(
            currentSeason = 2026,
            currentWeek = 40,
            playerTeamId = user.id
        )
        repository.saveGameSave(save)

        repository.saveFixtures(
            listOf(
                playedLeagueFixture(1, user.id, ratingFavorite.id, 2, 0),
                playedLeagueFixture(2, ratingFavorite.id, user.id, 0, 1)
            )
        )

        val transition = SeasonTransitionUseCase(
            repository = repository,
            generateCalendarUseCase = generateCalendarUseCase,
            databaseIntegrityUseCase = databaseIntegrityUseCase
        )

        val nextSave = transition.advanceToNextSeason(save)

        assertEquals(2027, nextSave.currentSeason)
        assertEquals(1, nextSave.currentWeek)

        val snapshot = repository.getGlobalStandingsForSeason(2026)
        assertEquals(6, snapshot.size)

        val brazil = snapshot.filter { it.country == "Brasil" }
        assertEquals(2, brazil.size)
        assertEquals(user.id, brazil.first().teamId)
        assertEquals(6, brazil.first().points)

        val argentinaRows = snapshot.filter { it.country == "Argentina" }
        assertEquals(4, argentinaRows.size)
        assertEquals(listOf(1, 2, 3, 4), argentinaRows.map { it.position })

        val nextFixtures = repository.getFixturesForSeason(2027)
        assertTrue("A nova temporada deve ser gerada depois do snapshot", nextFixtures.isNotEmpty())
        assertTrue("Fixtures antigos não devem sobreviver à transição", repository.getFixturesForSeason(2026).isEmpty())
    }

    private fun team(id: Long, name: String, country: String, rating: Int) = Team(
        id = id,
        name = name,
        city = name,
        state = "XX",
        country = country,
        division = 1,
        rating = rating
    )

    private fun playedLeagueFixture(
        id: Long,
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
        competitionType = "SERIE_A",
        isPlayed = true
    )
}
