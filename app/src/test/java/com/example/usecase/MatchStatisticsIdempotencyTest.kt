package com.example.usecase

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class MatchStatisticsIdempotencyTest {
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
    fun `persisted played fixture prevents second finalization`() = runTest {
        seedTeams()
        repository.saveFixtures(
            listOf(
                Fixture(
                    id = 50L,
                    season = 2026,
                    week = 4,
                    homeTeamId = 1L,
                    awayTeamId = 2L,
                    competitionType = "SERIE_A",
                    isPlayed = false
                )
            )
        )

        var statApplications = 0
        suspend fun finalizeOnce() {
            repository.runInTransaction {
                val persisted = repository.getFixture(50L)
                if (persisted?.isPlayed != true) {
                    repository.updateFixture(
                        persisted!!.copy(homeScore = 2, awayScore = 1, isPlayed = true)
                    )
                    statApplications++
                }
            }
        }

        finalizeOnce()
        finalizeOnce()

        assertEquals(1, statApplications)
        val persisted = requireNotNull(repository.getFixture(50L))
        assertTrue(persisted.isPlayed)
        assertEquals(2, persisted.homeScore)
        assertEquals(1, persisted.awayScore)
    }

    @Test
    fun `stale played result cannot overwrite committed score`() = runTest {
        seedTeams()
        val fixture = Fixture(
            id = 51L,
            season = 2026,
            week = 4,
            homeTeamId = 1L,
            awayTeamId = 2L,
            competitionType = "SERIE_A",
            isPlayed = false
        )
        repository.saveFixtures(listOf(fixture))
        repository.updateFixture(fixture.copy(homeScore = 2, awayScore = 1, isPlayed = true))

        repository.updateFixture(fixture.copy(homeScore = 0, awayScore = 4, isPlayed = true))

        val persisted = requireNotNull(repository.getFixture(51L))
        assertEquals(2, persisted.homeScore)
        assertEquals(1, persisted.awayScore)
    }

    @Test
    fun `same committed score may receive knockout penalty metadata`() = runTest {
        seedTeams()
        val fixture = Fixture(
            id = 52L,
            season = 2026,
            week = 4,
            homeTeamId = 1L,
            awayTeamId = 2L,
            competitionType = "CUP_TEST",
            isPlayed = false
        )
        repository.saveFixtures(listOf(fixture))
        repository.updateFixture(fixture.copy(homeScore = 1, awayScore = 1, isPlayed = true))

        repository.updateFixture(
            fixture.copy(
                homeScore = 1,
                awayScore = 1,
                homePenalties = 5,
                awayPenalties = 4,
                isPlayed = true
            )
        )

        val persisted = requireNotNull(repository.getFixture(52L))
        assertEquals(1, persisted.homeScore)
        assertEquals(1, persisted.awayScore)
        assertEquals(5, persisted.homePenalties)
        assertEquals(4, persisted.awayPenalties)
    }

    @Test
    fun `season reset clears seasonal goals but preserves cumulative career goals`() = runTest {
        repository.saveTeams(
            listOf(Team(id = 1L, name = "Owner", city = "A", state = "AA", division = 1, rating = 70))
        )
        repository.saveGameSave(GameSave(id = 1, currentSeason = 2026, currentWeek = 40, playerTeamId = 1L))
        repository.savePlayers(
            listOf(
                Player(
                    id = 10L,
                    teamId = 1L,
                    name = "Scorer",
                    age = 25,
                    position = "ATA",
                    force = 80,
                    careerGoals = 55,
                    careerApps = 100,
                    gols = 12,
                    partidasDisputadas = 20,
                    minutosJogados = 1_600,
                    mediaNotas = 7.2
                )
            )
        )

        val restarted = repository.restartSeasonStateAtomically(
            expectedSeason = 2026,
            expectedPlayerTeamId = 1L,
            replacementFixtures = emptyList()
        )

        assertTrue(restarted)
        val player = requireNotNull(repository.getPlayer(10L))
        assertEquals(55, player.careerGoals)
        assertEquals(100, player.careerApps)
        assertEquals(0, player.gols)
        assertEquals(0, player.partidasDisputadas)
        assertEquals(0, player.minutosJogados)
        assertEquals(0.0, player.mediaNotas, 0.0)
    }

    private suspend fun seedTeams() {
        repository.saveTeams(
            listOf(
                Team(id = 1L, name = "Home", city = "A", state = "AA", division = 1, rating = 70),
                Team(id = 2L, name = "Away", city = "B", state = "BB", division = 1, rating = 70)
            )
        )
    }
}
