package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class MonthlyEvolutionBatchWriteRegressionTest {
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
    fun `set based monthly delta writes preserve owned columns triggers and chunk boundaries`() = runTest {
        repository.saveTeams(
            listOf(
                Team(id = 1L, name = "A", city = "A", state = "AA", division = 1),
                Team(id = 2L, name = "B", city = "B", state = "BB", division = 1)
            )
        )
        val originals = (1L..205L).map { id ->
            Player(
                id = id,
                teamId = if (id % 2L == 0L) 1L else 2L,
                name = "P$id",
                age = 20 + (id % 12L).toInt(),
                position = if (id % 3L == 0L) "ATA" else "MEI",
                force = 40 + (id % 35L).toInt(),
                salary = 10_000L + id,
                contractDurationWeeks = 20 + (id % 25L).toInt(),
                atributosJson = if (id % 4L == 0L) null else "{\"seed\":$id}",
                minutosJogados = 90 + (id % 50L).toInt(),
                evolucaoMensal = 0.25
            )
        }
        repository.savePlayers(originals)

        val planned = originals.map { player ->
            player.copy(
                atributosJson = if (player.id % 5L == 0L) null else "{\"evolved\":${player.id}}",
                force = if (player.id == 1L) 99 else (player.force + 2).coerceAtMost(99),
                minutosJogados = 999,
                evolucaoMensal = (player.id % 17L).toDouble() / 10.0
            )
        }
        val plannedStates = planned.map { it.toMonthlyEvolutionPlayerState() }
        val beforeRevision = requireNotNull(repository.prepareMonthlyEvolutionRevisionSnapshot())

        val updated = repository.withTransaction {
            repository.applyMonthlyEvolutionPlayerStateDeltas(plannedStates)
        }
        assertEquals(plannedStates.size, updated)

        val afterRevision = requireNotNull(repository.currentMonthlyEvolutionRevisionSnapshotOrNull())
        assertEquals(
            beforeRevision.footballRevision + plannedStates.size,
            afterRevision.footballRevision
        )
        assertEquals(beforeRevision.rosterRevision, afterRevision.rosterRevision)

        val plannedById = planned.associateBy { it.id }
        val originalsById = originals.associateBy { it.id }
        repository.getAllPlayers().forEach { persisted ->
            val expected = plannedById.getValue(persisted.id)
            val original = originalsById.getValue(persisted.id)
            assertEquals(expected.atributosJson, persisted.atributosJson)
            assertEquals(expected.force, persisted.force)
            assertEquals(0, persisted.minutosJogados)
            assertEquals(expected.evolucaoMensal, persisted.evolucaoMensal, 0.0)
            assertEquals(original.teamId, persisted.teamId)
            assertEquals(original.salary, persisted.salary)
            assertEquals(original.contractDurationWeeks, persisted.contractDurationWeeks)
        }
        assertEquals(99, repository.getPlayer(1L)?.force)
        assertNull(repository.getPlayer(5L)?.atributosJson)

        val nonexistent = plannedStates.first().copy(id = 999_999L)
        val missingUpdates = repository.withTransaction {
            repository.applyMonthlyEvolutionPlayerStateDeltas(listOf(nonexistent))
        }
        assertEquals(0, missingUpdates)

        // The scratch table must not leak a missing-id row into the next invocation.
        val retryState = plannedStates.first().copy(force = 98)
        val retryUpdates = repository.withTransaction {
            repository.applyMonthlyEvolutionPlayerStateDeltas(listOf(retryState))
        }
        assertEquals(1, retryUpdates)
        assertEquals(98, repository.getPlayer(retryState.id)?.force)
    }
}
