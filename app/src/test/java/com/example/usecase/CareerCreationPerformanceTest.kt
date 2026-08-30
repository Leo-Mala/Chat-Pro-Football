package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.CareerCreationPerformanceMonitor
import com.example.data.CareerCreationPerformanceSnapshot
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.MatchSlot
import com.example.data.Player
import com.example.data.Team
import com.example.data.model.SaveSlotMetadata
import com.example.ui.viewmodel.MutableStateFlow as SaveSlotsMutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class CareerCreationPerformanceTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository

    @Before
    fun setup() {
        CareerCreationPerformanceMonitor.clear()
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
    }

    @After
    fun tearDown() {
        CareerCreationPerformanceMonitor.clear()
        db.close()
    }

    @Test
    fun `fresh bulk roster persistence avoids upsert and restores all player indexes`() = runTest {
        repository.saveTeams(
            listOf(Team(id = 1L, name = "Performance", city = "A", state = "AA", division = 1, rating = 70))
        )
        val players = (1L..5_000L).map { id ->
            Player(
                id = id,
                teamId = 1L,
                name = "Perf $id",
                age = 24,
                position = if (id % 11L == 0L) "GOL" else "MEI",
                force = 70
            )
        }

        val started = System.nanoTime()
        repository.savePlayers(players)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L

        assertEquals(5_000, repository.getPlayerCountByTeam(1L))
        assertTrue("5k-player fresh bulk persistence took ${elapsedMs}ms", elapsedMs < 10_000L)

        val indexNames = db.openHelper.readableDatabase
            .query("PRAGMA index_list(`players`)")
            .use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(nameColumn))
                }
            }
        assertTrue("teamId/position/force index was not restored", "index_players_teamId_position_force" in indexNames)
        assertTrue("teamId/isStarter index was not restored", "index_players_teamId_isStarter" in indexNames)
        assertTrue("originalTeamId index was not restored", "index_players_originalTeamId" in indexNames)
    }

    @Test
    fun `fresh fixture persistence restores all schedule indexes`() = runTest {
        val teams = (1L..50L).map { id ->
            Team(
                id = id,
                name = "Fixture Team $id",
                city = "City $id",
                state = "ST",
                country = "Brasil",
                division = 1,
                rating = 70
            )
        }
        repository.saveTeams(teams)

        val fixtures = buildList {
            for (week in 1..40) {
                for (pair in 0 until 25) {
                    val home = pair * 2L + 1L
                    val away = home + 1L
                    add(
                        Fixture(
                            season = 2026,
                            week = week,
                            matchSlot = MatchSlot.WEEKEND,
                            homeTeamId = if (week % 2 == 1) home else away,
                            awayTeamId = if (week % 2 == 1) away else home,
                            competitionType = "PERF_FIXTURE"
                        )
                    )
                }
            }
        }

        val started = System.nanoTime()
        repository.saveFixtures(fixtures)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L

        assertEquals(1_000, repository.getFixturesForSeason(2026).size)
        assertTrue("1k-fixture fresh bulk persistence took ${elapsedMs}ms", elapsedMs < 10_000L)

        val indexNames = db.openHelper.readableDatabase
            .query("PRAGMA index_list(`fixtures`)")
            .use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(nameColumn))
                }
            }
        val expectedIndexes = setOf(
            "index_fixtures_season",
            "index_fixtures_week",
            "index_fixtures_homeTeamId",
            "index_fixtures_awayTeamId",
            "index_fixtures_competitionType",
            "index_fixtures_season_week",
            "index_fixtures_season_week_matchSlot"
        )
        assertTrue("fixture indexes were not fully restored: $indexNames", indexNames.containsAll(expectedIndexes))
    }

    @Test
    fun `real career phase monitor retains all required timing dimensions`() {
        val snapshot = CareerCreationPerformanceSnapshot(
            databaseBootstrapMs = 100,
            rosterMaterializationMs = 200,
            clubSetupMs = 50,
            competitionCalendarMs = 300,
            persistenceMs = 400,
            totalMs = 1_100,
            teamCount = 2_500,
            playerCount = 60_000,
            fixtureCount = 8_000
        )

        CareerCreationPerformanceMonitor.record(snapshot)

        val recorded = CareerCreationPerformanceMonitor.latest
        assertNotNull(recorded)
        assertEquals(snapshot, recorded)
        assertTrue(recorded!!.totalMs >= recorded.persistenceMs)
    }

    @Test
    fun `new career fast slot publication preserves unknown slot safety`() {
        val state = SaveSlotsMutableStateFlow<List<SaveSlotMetadata>>(emptyList())

        state.value = listOf(
            SaveSlotMetadata(
                id = "3",
                exists = true,
                coachName = "Novo",
                teamName = "Cruzeiro"
            )
        )
        assertTrue(
            "partial fast publication must not expose unreconciled slots as empty",
            state.value.isEmpty()
        )

        val complete = (1..5).map { slot ->
            SaveSlotMetadata(
                id = slot.toString(),
                exists = slot == 3,
                coachName = if (slot == 3) "Novo" else "",
                teamName = if (slot == 3) "Cruzeiro" else ""
            )
        }
        state.value = complete

        assertEquals((1..5).map(Int::toString), state.value.map { it.id })
        assertTrue(state.value.single { it.id == "3" }.exists)
    }
}
