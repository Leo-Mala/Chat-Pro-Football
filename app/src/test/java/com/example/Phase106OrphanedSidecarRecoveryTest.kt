package com.example

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.data.GamePreferencesRepository
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.data.repository.SlotDatabaseState
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase106OrphanedSidecarRecoveryTest {

    private lateinit var context: Context
    private lateinit var factory: SlotDatabaseFactory
    private lateinit var saveRepository: GameSaveRepository
    private lateinit var preferencesRepository: GamePreferencesRepository
    private val slotId = "5"
    private val suffixes = listOf("-wal", "-shm", "-journal")

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.dataStore.edit { it.clear() }
        context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .edit().clear().commit()
        clearPhysicalArtifacts()
        reopenRepositories()
    }

    @After
    fun tearDown() = runBlocking {
        saveRepository.closeAllDatabases()
        context.dataStore.edit { it.clear() }
        context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .edit().clear().commit()
        clearPhysicalArtifacts()
        Unit
    }

    @Test
    fun orphanedWalShmAndJournalAreRecoveryRequiredUntilExplicitDeletion() = runBlocking {
        suffixes.forEach { suffix ->
            clearPhysicalArtifacts()
            preferencesRepository.removeSlotMetadata(slotId)

            val databaseFile = saveRepository.databaseFileForSlot(slotId)
            databaseFile.parentFile?.mkdirs()
            val sidecar = File(databaseFile.path + suffix)
            val originalBytes = "phase-10.6-orphaned-sidecar-$suffix".toByteArray()
            sidecar.writeBytes(originalBytes)

            assertFalse("Arquivo principal deve estar ausente no restore parcial", databaseFile.exists())
            assertTrue(sidecar.exists())

            val inspection = saveRepository.inspectSlot(slotId)
            assertEquals(SlotDatabaseState.RECOVERY_REQUIRED, inspection.state)
            assertFalse("Sidecar órfão nunca pode autorizar Novo Jogo", inspection.newGameAllowed)
            assertTrue(inspection.failureReason?.contains("OrphanedSQLiteSidecar") == true)
            assertFalse(saveRepository.isNewGameAllowed(slotId))

            val listed = preferencesRepository.loadSaveSlots().single { it.id == slotId }
            assertTrue("Restore parcial precisa permanecer ocupado", listed.exists)
            assertTrue(listed.recoveryRequired)
            assertTrue("Inspeção não pode apagar o sidecar", sidecar.exists())
            assertTrue(
                "Inspeção não pode alterar o artefato recuperável",
                sidecar.readBytes().contentEquals(originalBytes)
            )

            assertTrue(
                "Somente exclusão explícita deve remover sidecars órfãos",
                saveRepository.deleteSlotDatabase(slotId)
            )
            assertFalse(sidecar.exists())

            val afterDeletion = saveRepository.inspectSlot(slotId)
            assertEquals(SlotDatabaseState.MISSING, afterDeletion.state)
            assertTrue(afterDeletion.newGameAllowed)
        }
    }

    private fun reopenRepositories() {
        if (::saveRepository.isInitialized) {
            saveRepository.closeAllDatabases()
        }
        factory = SlotDatabaseFactory(context)
        saveRepository = GameSaveRepository(context, factory)
        preferencesRepository = GamePreferencesRepository(context.dataStore, context, saveRepository)
    }

    private fun clearPhysicalArtifacts() {
        val databaseName = SlotDatabaseFactory.databaseNameForSlot(slotId)
        val databaseFile = context.getDatabasePath(databaseName)
        context.deleteDatabase(databaseName)
        suffixes.forEach { suffix -> File(databaseFile.path + suffix).delete() }
        databaseFile.delete()
    }
}
