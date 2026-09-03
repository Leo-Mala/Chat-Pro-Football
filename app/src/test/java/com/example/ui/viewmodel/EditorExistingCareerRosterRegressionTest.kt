package com.example.ui.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class EditorExistingCareerRosterRegressionTest {
    @Test fun `existing career never exposes synthetic ready zero before real roster`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = GameRepository(db)
            // Real career players reference an existing persisted club. Preserve the same FK invariant
            // in the regression fixture instead of relying on an impossible orphan roster.
            repository.saveTeams(listOf(Team(id = 1L, name = "Test Club", city = "Test", state = "TS", division = 1)))
            repository.savePlayers((1L..23L).map { id ->
                Player(id = id, teamId = 1L, name = "P$id", age = 24, position = "MEI", force = 99)
            })
            val states = editorPlayersLoadStateFlow(flowOf(repository), null).take(2).toList()
            assertTrue(states.first() is EditorPlayersLoadState.Loading)
            val ready = states.last() as EditorPlayersLoadState.Ready
            assertEquals(23, ready.players.size)
            assertEquals(setOf(99), ready.players.map { it.force }.toSet())
        } finally { db.close() }
    }
}
