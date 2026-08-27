package com.example.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class RestartSeasonPersistenceTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private val dbName = "restart-season-${System.nanoTime()}.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        db = openDatabase()
        repository = GameRepository(db)
    }

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `restart returns same career to week one resets season stats and survives reopen`() = runTest {
        val teams = listOf(
            Team(id = 1L, name = "User", city = "A", state = "AA", division = 1, rating = 80, isPlayerControlled = true),
            Team(id = 2L, name = "Rival", city = "B", state = "BB", division = 1, rating = 75)
        )
        repository.saveTeams(teams)
        repository.savePlayers(
            listOf(
                Player(
                    id = 101L,
                    teamId = 1L,
                    name = "Atleta",
                    age = 26,
                    position = "ATA",
                    force = 82,
                    energy = 42,
                    moral = 51,
                    gols = 9,
                    partidasDisputadas = 12,
                    careerApps = 40,
                    careerGoals = 21,
                    yellowCardsAccumulated = 2,
                    suspensionWeeksRemaining = 1
                )
            )
        )
        repository.saveGameSave(
            GameSave(
                coachName = "Técnico Persistente",
                currentSeason = 2026,
                currentWeek = 19,
                playerTeamId = 1L,
                bankBalance = 12_345_678L,
                isGameOver = true,
                careerMatches = 40,
                careerWins = 20
            )
        )
        repository.saveFixtures(
            listOf(
                Fixture(
                    id = 1L,
                    season = 2026,
                    week = 18,
                    homeTeamId = 1L,
                    awayTeamId = 2L,
                    homeScore = 3,
                    awayScore = 1,
                    competitionType = "SERIE_A",
                    isPlayed = true
                )
            )
        )

        val replacement = listOf(
            Fixture(
                id = 200L,
                season = 2026,
                week = 1,
                homeTeamId = 2L,
                awayTeamId = 1L,
                competitionType = "SERIE_A",
                isPlayed = false
            )
        )

        assertTrue(
            repository.restartSeasonStateAtomically(
                expectedSeason = 2026,
                expectedPlayerTeamId = 1L,
                replacementFixtures = replacement
            )
        )

        db.close()
        db = openDatabase()
        repository = GameRepository(db)

        val reopenedSave = requireNotNull(repository.getGameSave())
        assertEquals(2026, reopenedSave.currentSeason)
        assertEquals(1, reopenedSave.currentWeek)
        assertEquals(1L, reopenedSave.playerTeamId)
        assertEquals("Técnico Persistente", reopenedSave.coachName)
        assertEquals(12_345_678L, reopenedSave.bankBalance)
        assertEquals(40, reopenedSave.careerMatches)
        assertEquals(20, reopenedSave.careerWins)
        assertFalse(reopenedSave.isGameOver)

        val reopenedFixtures = repository.getFixturesForWeek(2026, 1)
        assertEquals(1, reopenedFixtures.size)
        assertFalse(reopenedFixtures.single().isPlayed)
        assertEquals(200L, reopenedFixtures.single().id)
        assertTrue(repository.getFixturesForWeek(2026, 18).isEmpty())

        val player = requireNotNull(repository.getPlayer(101L))
        assertEquals(0, player.gols)
        assertEquals(0, player.partidasDisputadas)
        assertEquals(0, player.yellowCardsAccumulated)
        assertEquals(0, player.suspensionWeeksRemaining)
        assertEquals(40, player.careerApps)
        assertEquals(21, player.careerGoals)
    }

    private fun openDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
}
