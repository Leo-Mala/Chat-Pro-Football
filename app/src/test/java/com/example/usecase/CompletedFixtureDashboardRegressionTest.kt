package com.example.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Team
import kotlinx.coroutines.flow.first
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
class CompletedFixtureDashboardRegressionTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private val dbName = "completed-fixture-dashboard-regression.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        openDatabase()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `finish A then dashboard and reopen both keep A completed and expose B`() = runTest {
        repository.saveTeams(
            listOf(
                Team(id = 1L, name = "Cruzeiro", city = "BH", state = "MG", division = 1, rating = 75),
                Team(id = 2L, name = "São Paulo", city = "SP", state = "SP", division = 1, rating = 75),
                Team(id = 3L, name = "Santos", city = "Santos", state = "SP", division = 1, rating = 72)
            )
        )
        repository.saveGameSave(GameSave(currentSeason = 2026, currentWeek = 1, playerTeamId = 1L))
        repository.saveFixtures(
            listOf(
                Fixture(id = 101L, season = 2026, week = 1, homeTeamId = 1L, awayTeamId = 2L, competitionType = "SERIE_A"),
                Fixture(id = 102L, season = 2026, week = 2, homeTeamId = 3L, awayTeamId = 1L, competitionType = "SERIE_A")
            )
        )

        val fixtureA = requireNotNull(repository.getFixture(101L))
        repository.updateFixture(fixtureA.copy(homeScore = 1, awayScore = 0, isPlayed = true))

        val immediateNext = repository.getNextFixtureForTeamFlow(2026, 1, 1L).first()
        assertEquals(102L, immediateNext?.id)
        assertTrue(requireNotNull(repository.getFixture(101L)).isPlayed)

        db.close()
        openDatabase()

        val reopenedA = requireNotNull(repository.getFixture(101L))
        val reopenedNext = repository.getNextFixtureForTeamFlow(2026, 1, 1L).first()
        assertTrue(reopenedA.isPlayed)
        assertEquals(1, reopenedA.homeScore)
        assertEquals(0, reopenedA.awayScore)
        assertEquals(102L, reopenedNext?.id)
        assertEquals(1L, repository.getGameSave()?.playerTeamId)
    }

    private fun openDatabase() {
        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        repository = GameRepository(db)
    }
}
