package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DetailedGroupTopology
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.LeagueSeasonFormat
import com.example.data.Team
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroupedDetailedPromotionPriorityTest {

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
    fun groupChampionsStayAheadOfHigherScoringRunnerInSnapshotAndPromotion() = runBlocking {
        val upperTeams = teams(
            firstId = 1L,
            count = 20,
            division = 2,
            ratingBase = 70
        )
        val lowerTeams = teams(
            firstId = 101L,
            count = 60,
            division = 3,
            ratingBase = 50
        )
        val allTeams = upperTeams + lowerTeams
        repository.saveTeams(allTeams)

        val save = GameSave(
            currentSeason = 2026,
            currentWeek = 40,
            playerTeamId = upperTeams.first().id
        )
        repository.saveGameSave(save)

        val calendar = GenerateCalendarUseCase(repository)
        val generated = calendar.generateSeasonFixtures(
            season = 2026,
            teams = allTeams,
            userTeamId = save.playerTeamId,
            userCountry = "Itália"
        )
        val upperFixtures = generated
            .filter { it.competitionType == "SERIE_B" }
            .map { it.copy(homeScore = 0, awayScore = 0, isPlayed = true) }

        val groups = LeagueSeasonFormat.buildDetailedGroups(lowerTeams)
        assertEquals(3, groups.size)
        val groupChampions = groups.map { it.first().id }.toSet()
        val dominantGroup = groups[1]
        val dominantChampionId = dominantGroup[0].id
        val dominantRunnerId = dominantGroup[1].id

        val lowerFixtures = generated
            .filter { it.competitionType == "SERIE_C" }
            .map { fixture -> scoreLowerFixture(fixture, groups) }

        val snapshot = GlobalLeagueSimulationUseCase().buildSeasonStandings(
            season = 2026,
            teams = allTeams,
            detailedFixtures = upperFixtures + lowerFixtures,
            detailedCountry = "Itália"
        ).filter { it.division == 3 }

        assertEquals(60, snapshot.size)
        assertTrue(snapshot.take(3).all { it.teamId in groupChampions })
        assertEquals(dominantChampionId, snapshot.first().teamId)
        assertTrue(snapshot.single { it.teamId == dominantRunnerId }.points > 100)
        assertTrue(
            snapshot
                .filter { it.teamId in groupChampions && it.teamId != dominantChampionId }
                .all { it.points < 50 }
        )
        assertTrue(snapshot.single { it.teamId == dominantRunnerId }.position > 3)

        repository.saveFixtures(upperFixtures + lowerFixtures)
        SeasonTransitionUseCase(
            repository = repository,
            generateCalendarUseCase = calendar,
            databaseIntegrityUseCase = mockk(relaxed = true)
        ).advanceToNextSeason(save)

        val updated = repository.getAllTeams().associateBy { it.id }
        val promotedLowerIds = lowerTeams
            .map { it.id }
            .filter { updated.getValue(it).division == 2 }
            .toSet()

        assertEquals(2, promotedLowerIds.size)
        assertTrue(promotedLowerIds.all { it in groupChampions })
        assertFalse(dominantRunnerId in promotedLowerIds)
    }

    @Test
    fun topologyExposesStableGroupLabelsForUi() {
        val lowerTeams = teams(
            firstId = 101L,
            count = 60,
            division = 3,
            ratingBase = 50
        )
        val calendar = GenerateCalendarUseCase(repository)
        val fixtures = calendar.generateSeasonFixtures(
            season = 2026,
            teams = lowerTeams,
            userTeamId = lowerTeams.first().id,
            userCountry = "Itália"
        ).filter { it.competitionType == "SERIE_C" }

        val groupIndexByTeamId = DetailedGroupTopology.groupIndexByTeamId(
            teamIds = lowerTeams.map { it.id }.toSet(),
            fixtures = fixtures
        )

        assertEquals(60, groupIndexByTeamId.size)
        assertEquals(setOf(0, 1, 2), groupIndexByTeamId.values.toSet())
        assertEquals("A", DetailedGroupTopology.groupLabel(0))
        assertEquals("B", DetailedGroupTopology.groupLabel(1))
        assertEquals("C", DetailedGroupTopology.groupLabel(2))
        assertEquals("AA", DetailedGroupTopology.groupLabel(26))
    }

    private fun scoreLowerFixture(
        fixture: Fixture,
        groups: List<List<Team>>
    ): Fixture {
        val groupIndex = groups.indexOfFirst { group ->
            group.any { it.id == fixture.homeTeamId }
        }
        val group = groups[groupIndex]
        val championId = group[0].id

        if (groupIndex == 1) {
            val runnerId = group[1].id
            return when {
                fixture.homeTeamId == championId -> fixture.copy(
                    homeScore = 2,
                    awayScore = 0,
                    isPlayed = true
                )
                fixture.awayTeamId == championId -> fixture.copy(
                    homeScore = 0,
                    awayScore = 2,
                    isPlayed = true
                )
                fixture.homeTeamId == runnerId -> fixture.copy(
                    homeScore = 2,
                    awayScore = 0,
                    isPlayed = true
                )
                fixture.awayTeamId == runnerId -> fixture.copy(
                    homeScore = 0,
                    awayScore = 2,
                    isPlayed = true
                )
                else -> fixture.copy(homeScore = 0, awayScore = 0, isPlayed = true)
            }
        }

        val designatedBottomId = group.last().id
        return when {
            fixture.homeTeamId == championId && fixture.awayTeamId == designatedBottomId -> fixture.copy(
                homeScore = 1,
                awayScore = 0,
                isPlayed = true
            )
            fixture.awayTeamId == championId && fixture.homeTeamId == designatedBottomId -> fixture.copy(
                homeScore = 0,
                awayScore = 1,
                isPlayed = true
            )
            else -> fixture.copy(homeScore = 0, awayScore = 0, isPlayed = true)
        }
    }

    private fun teams(
        firstId: Long,
        count: Int,
        division: Int,
        ratingBase: Int
    ): List<Team> = (0 until count).map { index ->
        val id = firstId + index
        Team(
            id = id,
            name = "Clube $id",
            city = "Cidade $id",
            state = "IT",
            country = "Itália",
            division = division,
            rating = ratingBase + (index % 20)
        )
    }
}
