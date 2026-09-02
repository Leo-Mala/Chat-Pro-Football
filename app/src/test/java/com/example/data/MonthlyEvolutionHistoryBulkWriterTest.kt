package com.example.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonthlyEvolutionHistoryBulkWriterTest {
    private lateinit var application: Application
    private lateinit var saveRepository: GameSaveRepository
    private val slotId = "7"

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        clearSlot()
        saveRepository = GameSaveRepository(application, SlotDatabaseFactory(application))
    }

    @After
    fun tearDown() {
        runBlocking { runCatching { saveRepository.closeAllDatabases() } }
        clearSlot()
    }

    @Test
    fun `bulk monthly history preserves rows fingerprints and generated ids across batches`() = runBlocking {
        val repository = saveRepository.getRepositoryForSlot(slotId)
        repository.deleteAllHistorico()

        val rows = List(365) { index ->
            HistoricoEvolucao(
                jogadorId = (index % 7 + 1).toLong(),
                data = "S2026_W8",
                atributo = "attr_${index % 13}",
                valorAntigo = 40 + (index % 30),
                valorNovo = 41 + (index % 30)
            )
        }

        val inserted = repository.withTransaction {
            repository.insertMonthlyEvolutionHistoryRowsBulk(rows)
        }
        assertEquals(rows.size, inserted)

        val persisted = (1L..7L).flatMap(repository::getHistoricoPorJogador)
        assertEquals(rows.size, persisted.size)
        assertTrue(persisted.all { it.id > 0L })
        assertEquals(rows.size, persisted.map { it.id }.toSet().size)
        assertEquals(
            rows.mapTo(hashSetOf()) { it.monthlyEvolutionFingerprint() },
            persisted.mapTo(hashSetOf()) { it.monthlyEvolutionFingerprint() }
        )
    }
}
