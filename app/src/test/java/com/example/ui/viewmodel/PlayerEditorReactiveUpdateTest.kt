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
class PlayerEditorReactiveUpdateTest {
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
    fun `persisted player edit is emitted immediately by room flow`() = runTest {
        repository.saveTeams(
            listOf(Team(id = 1L, name = "Reactive", city = "A", state = "AA", division = 1, rating = 70))
        )
        val original = Player(
            id = 10L,
            teamId = 1L,
            name = "Edited Player",
            age = 24,
            position = "MEI",
            force = 70,
            finishing = 70,
            passing = 70,
            pace = 70,
            strength = 70,
            vision = 70,
            defense = 70
        )
        repository.savePlayers(listOf(original))

        val refreshed = async {
            repository.allPlayersFlow
                .dropWhile { players -> players.firstOrNull { it.id == 10L }?.force != 82 }
                .first()
                .first { it.id == 10L }
        }

        repository.updatePlayer(
            original.copy(
                force = 82,
                finishing = 84,
                passing = 83,
                pace = 81,
                strength = 80,
                vision = 85,
                defense = 76
            )
        )

        val emitted = refreshed.await()
        assertEquals(82, emitted.force)
        assertEquals(84, emitted.finishing)
        assertEquals(85, emitted.vision)
        assertEquals(82, repository.getPlayer(10L)?.force)
    }
}
