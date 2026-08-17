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
class GlobalCpuMultiSeasonTransitionTest {

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
    fun cpuWorldKeepsMovingAndLowerSnapshotRetentionStaysBounded() = runBlocking {
        val brazil = listOf(
            team(1, "Brasil A", "Brasil", 85, 1),
            team(2, "Brasil B", "Brasil", 75, 1)
        )
        val argentinaUpper = (10L..13L).mapIndexed { index, id ->
            team(id, "ARG A ${index + 1}", "Argentina", 92 - index * 6, 1)
        }
        val argentinaLower = (20L..23L).mapIndexed { index, id ->
            team(id, "ARG B ${index + 1}", "Argentina", 72 - index * 4, 2)
        }
        repository.saveTeams(brazil + argentinaUpper + argentinaLower)

        var save = GameSave(
            currentSeason = 2026,
            currentWeek = 40,
            playerTeamId = brazil.first().id
        )
        repository.saveGameSave(save)
        repository.saveFixtures(
            listOf(
                fixture(2026, 1, brazil[0].id, brazil[1].id, 1, 0),
                fixture(2026, 2, brazil[1].id, brazil[0].id, 0, 1)
            )
        )

        val transition = SeasonTransitionUseCase(
            repository = repository,
            generateCalendarUseCase = generateCalendarUseCase,
            databaseIntegrityUseCase = databaseIntegrityUseCase
        )

        save = transition.advanceToNextSeason(save)
        assertEquals(2027, save.currentSeason)
        val afterFirst = repository.getAllTeams()

        val snapshot2026 = repository.getGlobalStandingsForSeason(2026)
        assertTrue(snapshot2026.any { it.country == "Argentina" && it.division == 2 })

        // Prever a segunda movimentação usando exatamente o mesmo mundo que entrou em 2027.
        val expected2027 = GlobalLeagueSimulationUseCase().buildSeasonStandings(
            season = 2027,
            teams = afterFirst,
            detailedFixtures = repository.getFixturesForSeason(2027),
            detailedCountry = "Brasil"
        )
        val expectedRelegated = expected2027
            .filter { it.country == "Argentina" && it.division == 1 }
            .sortedBy { it.position }
            .takeLast(2)
            .map { it.teamId }
            .toSet()
        val expectedPromoted = expected2027
            .filter { it.country == "Argentina" && it.division == 2 }
            .sortedBy { it.position }
            .take(2)
            .map { it.teamId }
            .toSet()

        save = save.copy(currentWeek = 40)
        repository.saveGameSave(save)
        save = transition.advanceToNextSeason(save)

        assertEquals(2028, save.currentSeason)
        val afterSecond = repository.getAllTeams().associateBy { it.id }
        expectedRelegated.forEach { id -> assertEquals(2, afterSecond.getValue(id).division) }
        expectedPromoted.forEach { id -> assertEquals(1, afterSecond.getValue(id).division) }

        // Ao gravar 2027, as divisões inferiores de 2026 são podadas, mas a elite histórica fica.
        val retained2026 = repository.getGlobalStandingsForSeason(2026)
        assertTrue(retained2026.isNotEmpty())
        assertTrue(retained2026.all { it.division == 1 })

        // A temporada mais recente permanece completa para contexto e eventual navegação.
        val retained2027 = repository.getGlobalStandingsForSeason(2027)
        assertTrue(retained2027.any { it.country == "Argentina" && it.division == 1 })
        assertTrue(retained2027.any { it.country == "Argentina" && it.division == 2 })
    }

    private fun team(
        id: Long,
        name: String,
        country: String,
        rating: Int,
        division: Int
    ) = Team(
        id = id,
        name = name,
        city = name,
        state = "XX",
        country = country,
        division = division,
        rating = rating
    )

    private fun fixture(
        season: Int,
        id: Long,
        homeId: Long,
        awayId: Long,
        homeScore: Int,
        awayScore: Int
    ) = Fixture(
        id = id,
        season = season,
        week = id.toInt(),
        homeTeamId = homeId,
        awayTeamId = awayId,
        homeScore = homeScore,
        awayScore = awayScore,
        competitionType = "SERIE_A",
        isPlayed = true
    )
}
