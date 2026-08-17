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

        val transition = newTransition()
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

    @Test
    fun cpuCountryPromotionAndRelegationUsesPersistedCompactSnapshots() = runBlocking {
        val user = team(1, "Brasil User", "Brasil", 80)
        val brazilOther = team(2, "Brasil CPU", "Brasil", 70)
        val argentinaUpper = (10L..13L).mapIndexed { index, id ->
            team(id, "ARG A ${index + 1}", "Argentina", 92 - index * 7, division = 1)
        }
        val argentinaLower = (20L..23L).mapIndexed { index, id ->
            team(id, "ARG B ${index + 1}", "Argentina", 74 - index * 5, division = 2)
        }
        val allTeams = listOf(user, brazilOther) + argentinaUpper + argentinaLower
        repository.saveTeams(allTeams)

        val save = GameSave(currentSeason = 2026, currentWeek = 40, playerTeamId = user.id)
        repository.saveGameSave(save)
        val detailedFixtures = listOf(
            playedLeagueFixture(1, user.id, brazilOther.id, 1, 0),
            playedLeagueFixture(2, brazilOther.id, user.id, 0, 1)
        )
        repository.saveFixtures(detailedFixtures)

        val expectedSnapshot = GlobalLeagueSimulationUseCase().buildSeasonStandings(
            season = 2026,
            teams = allTeams,
            detailedFixtures = detailedFixtures,
            detailedCountry = "Brasil"
        )
        val expectedRelegated = expectedSnapshot
            .filter { it.country == "Argentina" && it.division == 1 }
            .sortedBy { it.position }
            .takeLast(2)
            .map { it.teamId }
            .toSet()
        val expectedPromoted = expectedSnapshot
            .filter { it.country == "Argentina" && it.division == 2 }
            .sortedBy { it.position }
            .take(2)
            .map { it.teamId }
            .toSet()

        newTransition().advanceToNextSeason(save)

        val updated = repository.getAllTeams().associateBy { it.id }
        expectedRelegated.forEach { id -> assertEquals(2, updated.getValue(id).division) }
        expectedPromoted.forEach { id -> assertEquals(1, updated.getValue(id).division) }
        argentinaUpper.filterNot { it.id in expectedRelegated }
            .forEach { assertEquals(1, updated.getValue(it.id).division) }
        argentinaLower.filterNot { it.id in expectedPromoted }
            .forEach { assertEquals(2, updated.getValue(it.id).division) }

        // O snapshot é histórico: mantém a divisão disputada antes da movimentação.
        val persistedSnapshot = repository.getGlobalStandingsForSeason(2026)
        expectedRelegated.forEach { id ->
            assertEquals(1, persistedSnapshot.single { it.teamId == id }.division)
        }
        expectedPromoted.forEach { id ->
            assertEquals(2, persistedSnapshot.single { it.teamId == id }.division)
        }
    }

    @Test
    fun countryWithoutExplicitHierarchyUsesGenericTwoSpotMovement() = runBlocking {
        val user = team(1, "Brasil User", "Brasil", 80)
        val brazilOther = team(2, "Brasil CPU", "Brasil", 70)
        val franceUpper = (30L..33L).mapIndexed { index, id ->
            team(id, "FRA A ${index + 1}", "França", 90 - index * 5, division = 1)
        }
        val franceLower = (40L..43L).mapIndexed { index, id ->
            team(id, "FRA B ${index + 1}", "França", 70 - index * 5, division = 2)
        }
        val allTeams = listOf(user, brazilOther) + franceUpper + franceLower
        repository.saveTeams(allTeams)

        val save = GameSave(currentSeason = 2026, currentWeek = 40, playerTeamId = user.id)
        repository.saveGameSave(save)
        val detailedFixtures = listOf(
            playedLeagueFixture(1, user.id, brazilOther.id, 1, 0),
            playedLeagueFixture(2, brazilOther.id, user.id, 0, 1)
        )
        repository.saveFixtures(detailedFixtures)

        val expectedSnapshot = GlobalLeagueSimulationUseCase().buildSeasonStandings(
            season = 2026,
            teams = allTeams,
            detailedFixtures = detailedFixtures,
            detailedCountry = "Brasil"
        )
        val expectedRelegated = expectedSnapshot
            .filter { it.country == "França" && it.division == 1 }
            .sortedBy { it.position }
            .takeLast(2)
            .map { it.teamId }
            .toSet()
        val expectedPromoted = expectedSnapshot
            .filter { it.country == "França" && it.division == 2 }
            .sortedBy { it.position }
            .take(2)
            .map { it.teamId }
            .toSet()

        newTransition().advanceToNextSeason(save)

        val updated = repository.getAllTeams().associateBy { it.id }
        expectedRelegated.forEach { id -> assertEquals(2, updated.getValue(id).division) }
        expectedPromoted.forEach { id -> assertEquals(1, updated.getValue(id).division) }
        franceUpper.filterNot { it.id in expectedRelegated }
            .forEach { assertEquals(1, updated.getValue(it.id).division) }
        franceLower.filterNot { it.id in expectedPromoted }
            .forEach { assertEquals(2, updated.getValue(it.id).division) }

        // A França usa exatamente 2 vagas genéricas, não as 4 específicas do Brasil.
        assertEquals(2, expectedRelegated.size)
        assertEquals(2, expectedPromoted.size)

        val snapshot = repository.getGlobalStandingsForSeason(2026)
        assertEquals(4, snapshot.count { it.country == "França" && it.division == 1 })
        assertEquals(4, snapshot.count { it.country == "França" && it.division == 2 })
    }

    private fun newTransition() = SeasonTransitionUseCase(
        repository = repository,
        generateCalendarUseCase = generateCalendarUseCase,
        databaseIntegrityUseCase = databaseIntegrityUseCase
    )

    private fun team(
        id: Long,
        name: String,
        country: String,
        rating: Int,
        division: Int = 1
    ) = Team(
        id = id,
        name = name,
        city = name,
        state = "XX",
        country = country,
        division = division,
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
