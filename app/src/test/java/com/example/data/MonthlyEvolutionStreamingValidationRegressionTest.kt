package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class MonthlyEvolutionStreamingValidationRegressionTest {
    @Test fun `streaming validation matches stable world and rejects mutated input`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = GameRepository(db)
            repository.saveTeams(listOf(
                Team(id = 1L, name = "A", city = "A", state = "AA", division = 1, trainingCenterLevel = 1),
                Team(id = 2L, name = "B", city = "B", state = "BB", division = 1, trainingCenterLevel = 1)
            ))
            repository.savePlayers((1L..2500L).map { id ->
                Player(id = id, teamId = if (id % 2L == 0L) 1L else 2L, name = "P$id",
                    age = 20 + (id % 10).toInt(), position = "MEI", force = 60 + (id % 30).toInt())
            })
            val expected = repository.getAllMonthlyEvolutionInputSnapshots().values.toList()
            val levels = mapOf(1L to 1, 2L to 1)
            val stable = repository.validateMonthlyEvolutionRosterInputs(expected, levels, levels)
            assertTrue(stable.valid)
            assertTrue(stable.correctionIds.isEmpty())
            assertEquals(2500, stable.currentPlayerCount)

            repository.updatePlayers(listOf(repository.getPlayer(100L)!!.copy(force = 99)))
            assertFalse(repository.validateMonthlyEvolutionRosterInputs(expected, levels, levels).valid)
        } finally { db.close() }
    }

    @Test fun `new weekly player is a targeted correction`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = GameRepository(db)
            repository.savePlayers(listOf(Player(id = 1L, teamId = null, name = "P1", age = 22, position = "MEI", force = 70)))
            val expected = repository.getAllMonthlyEvolutionInputSnapshots().values.toList()
            repository.savePlayers(listOf(Player(id = 2L, teamId = null, name = "P2", age = 18, position = "ATA", force = 55)))
            val validation = repository.validateMonthlyEvolutionRosterInputs(expected, emptyMap(), emptyMap())
            assertTrue(validation.valid)
            assertEquals(setOf(2L), validation.correctionIds)
        } finally { db.close() }
    }
}
