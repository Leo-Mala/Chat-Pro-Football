package com.example.ui.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameEngine
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class ClubStrengthReactiveUpdateTest {
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
    fun `club rating recomputed from edited roster is emitted by room flow`() = runTest {
        val team = Team(id = 1L, name = "Reactive Club", city = "A", state = "AA", division = 1, rating = 60)
        repository.saveTeams(listOf(team))
        val roster = (1L..11L).map { id ->
            Player(id = id, teamId = 1L, name = "P$id", age = 24, position = if (id == 1L) "GOL" else "MEI", force = 60)
        }
        repository.savePlayers(roster)

        val before = GameEngine.calculateTeamRating(repository.getPlayersByTeam(1L))
        repository.updatePlayer(roster.last().copy(force = 99))
        val after = GameEngine.calculateTeamRating(repository.getPlayersByTeam(1L))
        assertNotEquals("Edited roster must change derived club strength", before, after)

        val refreshed = async {
            repository.allTeamsFlow
                .dropWhile { teams -> teams.firstOrNull { it.id == 1L }?.rating != after }
                .first()
                .first { it.id == 1L }
        }

        repository.updateTeam(team.copy(rating = after))

        assertEquals(after, refreshed.await().rating)
        assertEquals(after, repository.getTeam(1L)?.rating)
        assertTrue(after in 15..99)
    }
}
