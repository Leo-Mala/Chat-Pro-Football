package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.CareerCreationPerformanceMonitor
import com.example.data.CareerCreationPerformanceSnapshot
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.Team
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
}
