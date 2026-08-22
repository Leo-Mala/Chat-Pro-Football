package com.example

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.data.GamePreferencesRepository
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GamePreferencesRestoreSafetyTest {

    private lateinit var context: Context
    private lateinit var databaseFactory: SlotDatabaseFactory
    private lateinit var saveRepository: GameSaveRepository
    private lateinit var repository: GamePreferencesRepository

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            context.dataStore.edit { it.clear() }
            context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot("2"))
            databaseFactory = SlotDatabaseFactory(context)
            saveRepository = GameSaveRepository(context, databaseFactory)
            repository = GamePreferencesRepository(context.dataStore, context, saveRepository)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            saveRepository.closeAllDatabases()
            context.dataStore.edit { it.clear() }
            context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot("2"))
        }
    }

    @Test
    fun restoredMetadataWithoutDatabaseIsRejectedAndSanitized() = runBlocking {
        repository.updateSlotMetadata(
            saveId = "2",
            coachName = "Restore Fantasma",
            teamName = "Sem Banco",
            season = 2030,
            week = 12,
            balance = 9_000_000L
        )

        val firstRead = repository.loadSaveSlots().single { it.id == "2" }
        assertFalse("Metadata sem banco não pode aparecer como save restaurado", firstRead.exists)

        val secondRead = repository.loadSaveSlots().single { it.id == "2" }
        assertFalse("Metadata fantasma deve permanecer saneada após a primeira leitura", secondRead.exists)
    }
}
