package com.example

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.example.data.GamePreferencesRepository
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase106MetadataCancellationTest {

    private lateinit var context: Context
    private lateinit var factory: SlotDatabaseFactory
    private lateinit var saveRepository: GameSaveRepository
    private lateinit var legacyPrefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        legacyPrefs = context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
        legacyPrefs.edit().clear().commit()
        context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot("1"))
        factory = SlotDatabaseFactory(context)
        saveRepository = GameSaveRepository(context, factory)
    }

    @After
    fun tearDown() {
        saveRepository.closeAllDatabases()
        legacyPrefs.edit().clear().commit()
        context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot("1"))
    }

    @Test
    fun cancelledMetadataWriteDoesNotFallBackToLegacyCommit() = runBlocking {
        val repository = GamePreferencesRepository(CancellingDataStore(cancelOnRead = false), context, saveRepository)

        var cancelled = false
        try {
            repository.updateSlotMetadata("1", "Cancelado", "Clube", 2030, 10, 1_000L)
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue("CancellationException deve ser propagada", cancelled)
        assertFalse(
            "Cancelamento não pode continuar para o fallback legado",
            legacyPrefs.getBoolean("slot_1_exists", false)
        )
    }

    @Test
    fun cancelledMetadataRemovalDoesNotProceedToLegacyOrPhysicalDeleteChain() = runBlocking {
        legacyPrefs.edit()
            .putBoolean("slot_1_exists", true)
            .putString("slot_1_coach_name", "Preservar")
            .commit()
        val repository = GamePreferencesRepository(CancellingDataStore(cancelOnRead = false), context, saveRepository)

        var cancelled = false
        try {
            repository.removeSlotMetadata("1")
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue("CancellationException deve interromper remoção", cancelled)
        assertTrue(
            "Metadata legada não pode ser removida depois de cancelamento",
            legacyPrefs.getBoolean("slot_1_exists", false)
        )
    }

    @Test
    fun cancelledMetadataReadIsNotConvertedIntoOrdinaryStorageFallback() = runBlocking {
        val repository = GamePreferencesRepository(CancellingDataStore(cancelOnRead = true), context, saveRepository)

        var cancelled = false
        try {
            repository.loadSaveSlots()
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue("Cancelamento da leitura precisa chegar ao chamador", cancelled)
    }

    private class CancellingDataStore(
        private val cancelOnRead: Boolean
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
            if (cancelOnRead) throw CancellationException("forced read cancellation")
            // O teste de escrita/remoção não consome data; se consumir, cancele de forma segura.
            throw CancellationException("unexpected data collection")
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            throw CancellationException("forced metadata cancellation")
        }
    }
}
