package com.example

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.data.GamePreferencesRepository
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.ui.viewmodel.GameViewModel
import com.example.ui.viewmodel.ensureSaveActiveForEditor
import com.example.usecase.TacticsUseCase
import com.example.usecase.YouthAcademyUseCase
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase106EditorRecoveryEntryPointTest {

    private lateinit var application: Application
    private lateinit var saveRepository: GameSaveRepository
    private lateinit var preferencesRepository: GamePreferencesRepository
    private lateinit var viewModel: GameViewModel

    @Before
    fun setUp() = runBlocking {
        application = ApplicationProvider.getApplicationContext()
        application.dataStore.edit { it.clear() }
        application.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .edit().clear().commit()
        clearSlotOne()

        val factory = SlotDatabaseFactory(application)
        saveRepository = GameSaveRepository(application, factory)
        preferencesRepository = GamePreferencesRepository(application.dataStore, application, saveRepository)
        viewModel = GameViewModel(
            application = application,
            saveRepository = saveRepository,
            preferencesRepo = preferencesRepository,
            youthAcademyUseCase = YouthAcademyUseCase(),
            tacticsUseCase = TacticsUseCase()
        )
    }

    @After
    fun tearDown() = runBlocking {
        saveRepository.closeAllDatabases()
        application.dataStore.edit { it.clear() }
        application.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .edit().clear().commit()
        clearSlotOne()
    }

    @Test
    fun editorDoesNotOpenOrSeedWhenDefaultSlotRequiresRecovery() = runBlocking {
        val databaseFile = saveRepository.databaseFileForSlot("1")
        databaseFile.parentFile?.mkdirs()
        databaseFile.writeBytes(byteArrayOf())

        val ready = CompletableDeferred<Boolean>()
        viewModel.ensureSaveActiveForEditor { success ->
            ready.complete(success)
        }

        val success = withTimeout(5_000) { ready.await() }
        assertFalse("Editor não pode navegar quando o slot exige recuperação", success)
        assertNull("Falha de recovery não pode publicar sessão ativa", viewModel.currentSaveId.value)
        assertTrue("DB truncado deve ser preservado", databaseFile.exists())
        assertTrue("Editor não pode materializar schema ou seed", databaseFile.length() == 0L)

        val slots = preferencesRepository.loadSaveSlots()
        val slot = slots.single { it.id == "1" }
        assertTrue("Slot precisa reaparecer como recuperação", slot.exists)
        assertTrue(slot.recoveryRequired)
    }

    @Test
    fun editorPreparationBecomingStaleNeverNavigatesOrSeedsAfterExit() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val ready = CompletableDeferred<Boolean>()

        viewModel.ensureSaveActiveForEditor(
            onReady = { success -> ready.complete(success) },
            preparationCheckpoint = {
                entered.complete(Unit)
                release.await()
            }
        )

        // A sessão já foi validada/publicada, mas nenhum seed ocorreu ainda.
        withTimeout(5_000) { entered.await() }
        assertTrue(viewModel.currentSaveId.value == "1")

        // Simula lifecycle/menu mudando enquanto o preparo do editor está suspenso.
        viewModel.exitToSavesMenu()
        assertNull(viewModel.currentSaveId.value)
        release.complete(Unit)

        val success = withTimeout(5_000) { ready.await() }
        assertFalse("Callback obsoleto não pode navegar para o editor", success)
        assertNull("Saída do menu precisa continuar autoritativa", viewModel.currentSaveId.value)
        assertNull("Sessão obsoleta não pode ser republicada", viewModel.activeSaveSession.value)

        val repository = saveRepository.getRepositoryForSlot("1")
        assertTrue(
            "Preparo obsoleto precisa abortar antes do seed de times",
            repository.getAllTeams().isEmpty()
        )
        assertTrue(
            "Preparo obsoleto precisa abortar antes do seed de jogadores",
            repository.getAllPlayers().isEmpty()
        )
    }

    private fun clearSlotOne() {
        val name = SlotDatabaseFactory.databaseNameForSlot("1")
        val file = application.getDatabasePath(name)
        application.deleteDatabase(name)
        listOf("-wal", "-shm", "-journal").forEach { suffix -> File(file.path + suffix).delete() }
        file.delete()
    }
}
