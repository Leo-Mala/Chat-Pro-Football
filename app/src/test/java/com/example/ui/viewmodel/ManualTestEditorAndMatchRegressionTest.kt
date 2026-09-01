package com.example.ui.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameEngine
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

class Post90FinalizationStateRegressionTest {
    @Test
    fun `90 minutes enters finalizing and removes live controls immediately`() {
        val gate = LiveMatchFinalizationGate()
        assertTrue(gate.tryBegin())
        assertTrue(gate.finalizing.value)
        assertFalse(liveMatchUserControlsEnabled(GameViewModel.MatchState.PLAYING, gate.isActive()))
    }
}

class LiveMatchFinalizationCannotBeCancelledRegressionTest {
    @Test
    fun `pause resume and tactical controls are unavailable while finalizing`() {
        assertFalse(liveMatchUserControlsEnabled(GameViewModel.MatchState.PLAYING, true))
        assertFalse(liveMatchUserControlsEnabled(GameViewModel.MatchState.PAUSED, true))
        assertTrue(liveMatchUserControlsEnabled(GameViewModel.MatchState.PLAYING, false))
        assertTrue(liveMatchUserControlsEnabled(GameViewModel.MatchState.PAUSED, false))
    }
}

class LiveMatchFinalizationIdempotencyRegressionTest {
    @Test
    fun `only one owner can acquire finalization gate and finished cannot restart it`() {
        val gate = LiveMatchFinalizationGate()
        assertTrue(gate.tryBegin())
        assertFalse(gate.tryBegin())
        assertFalse(liveMatchCanBeginFinalization(GameViewModel.MatchState.PLAYING, gate.isActive()))
        gate.complete()
        assertFalse(gate.finalizing.value)
        assertFalse(liveMatchCanBeginFinalization(GameViewModel.MatchState.FINISHED, false))
    }
}

class TeamStrength99RealEditorPathRegressionTest {
    @Test
    fun `real team editor synchronization sets every existing player to 99`() {
        val roster = (1L..30L).map { id ->
  Player(
      id = id,
      teamId = 1L,
      name = "P$id",
      age = 24,
      position = "MEI",
      force = 60 + (id % 30).toInt()
  )
        }
        val result = synchronizeExistingRosterForEditedTeam(roster, 99)
        assertEquals(30, result.size)
        assertEquals(setOf(99), result.map { it.force }.toSet())
        assertEquals(roster.map { it.id }, result.map { it.id })
    }
}

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class TeamStrength99PersistenceRegressionTest {
    @Test
    fun `99 team and roster remain 99 after Room persistence readback`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
  ApplicationProvider.getApplicationContext(),
  AppDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
  val repository = GameRepository(db)
  val team = Team(id = 1L, name = "Cruzeiro", city = "Belo Horizonte", state = "MG", division = 1, rating = 99)
  val roster = (1L..30L).map { id ->
      Player(id = id, teamId = 1L, name = "P$id", age = 24, position = "MEI", force = 70 + (id % 20).toInt())
  }
  repository.saveTeams(listOf(team))
  repository.savePlayers(roster)
  repository.withTransaction {
      repository.updateTeam(team)
      repository.updatePlayers(synchronizeExistingRosterForEditedTeam(roster, 99))
  }

  assertEquals(99, repository.getTeam(1L)?.rating)
  assertEquals(setOf(99), repository.getPlayersByTeam(1L).map { it.force }.toSet())
        } finally {
  db.close()
        }
    }
}

class TeamRatingDoesNotDropAfter99EditRegressionTest {
    @Test
    fun `dynamic fatigue formula cannot overwrite explicit uniform editor 99`() {
        val team = Team(id = 1L, name = "Cruzeiro", city = "Belo Horizonte", state = "MG", division = 1, rating = 99)
        val roster = (1L..30L).map { id ->
  Player(
      id = id,
      teamId = 1L,
      name = "P$id",
      age = 24,
      position = if (id == 1L) "GOL" else "MEI",
      force = 99,
      energy = 90,
      moral = 90
  )
        }
        assertTrue(GameEngine.calculateTeamRating(roster) < 99)
        assertEquals(99, resolveEditedTeamRating(team, roster))
    }
}

class EditorRosterNoFalseZeroRegressionTest {
    @Test
    fun `loading roster has no count while a real empty snapshot reports zero`() {
        val loading = EditorPlayersLoadState.Loading(1L)
        assertNull(editorRosterCountOrNull(loading, 1L))

        val empty = EditorPlayersLoadState.Ready(1L, emptyList())
        assertEquals(0, editorRosterCountOrNull(empty, 1L))

        val thirty = EditorPlayersLoadState.Ready(
  1L,
  (1L..30L).map { id ->
      Player(id = id, teamId = 1L, name = "P$id", age = 22, position = "MEI", force = 70)
  }
        )
        assertEquals(30, editorRosterCountOrNull(thirty, 1L))
        assertNull(editorRosterCountOrNull(thirty, 2L))
    }
}
