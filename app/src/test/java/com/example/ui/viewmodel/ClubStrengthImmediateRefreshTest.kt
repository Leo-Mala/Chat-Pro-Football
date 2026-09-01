package com.example.ui.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class ClubStrengthImmediateRefreshTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = GameRepository(db)
    }
    @After fun tearDown() = db.close()

    @Test fun `targeted roster flow publishes strength edit without global player reload`() = runTest {
        repository.saveTeams(listOf(Team(id = 7L, name = "Club", city = "A", state = "AA", division = 1)))
        val roster = (1L..18L).map { Player(id = it, teamId = 7L, name = "P$it", age = 24, position = "MEI", force = 70) }
        repository.savePlayers(roster)
        val emitted = async {
            repository.getPlayersForTeamFlow(7L)
                .dropWhile { players -> players.size != 18 || players.any { it.force != 99 } }
                .first()
        }
        repository.updatePlayers(roster.map { it.copy(force = 99) })
        assertEquals(18, emitted.await().size)
        assertEquals(setOf(99), emitted.await().map { it.force }.toSet())
    }
}
