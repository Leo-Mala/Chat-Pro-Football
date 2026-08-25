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
import com.example.ui.viewmodel.selectSaveSlotSafely
import com.example.usecase.TacticsUseCase
import com.example.usecase.YouthAcademyUseCase
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase106SameSlotReopenFailureTest {

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
    fun failedReopenOfAlreadySelectedSlotClearsObsoleteSessionFailClosed() = runBlocking {
        // Publica uma sessão válida sem disparar o seed assíncrono de selectSaveSlot().
        val originalSession = viewModel.getOrCreateSession("1")
        viewModel._currentSaveId.value = "1"
        assertTrue(viewModel.activeSaveSession.value === originalSession)
        assertTrue(viewModel.currentSaveId.value == "1")

        // Simula o artefato físico ficando inválido antes de uma nova tentativa de abrir o mesmo slot.
        saveRepository.closeAndRemoveSlot("1")
        val databaseFile = saveRepository.databaseFileForSlot("1")
        databaseFile.writeBytes("phase-10.6-same-slot-reopen-failure".toByteArray())
        assertTrue(databaseFile.exists())

        viewModel.selectSaveSlotSafely("1")

        withTimeout(5_000) {
            while (viewModel.currentSaveId.value != null || viewModel.activeSaveSession.value != null) {
                delay(10)
            }
        }

        assertNull("Reopen fail-closed não pode deixar currentSaveId obsoleto", viewModel.currentSaveId.value)
        assertNull("Reopen fail-closed não pode deixar SaveSession com geração vencida", viewModel.activeSaveSession.value)
        assertTrue("Falha de reabertura não pode apagar o artefato", databaseFile.exists())

        withTimeout(5_000) {
            while (viewModel.saveSlots.value.firstOrNull { it.id == "1" }?.recoveryRequired != true) {
                delay(10)
            }
        }
        assertTrue(viewModel.saveSlots.value.single { it.id == "1" }.recoveryRequired)
    }

    private fun clearSlotOne() {
        val name = SlotDatabaseFactory.databaseNameForSlot("1")
        val file = application.getDatabasePath(name)
        application.deleteDatabase(name)
        listOf("-wal", "-shm", "-journal").forEach { suffix -> File(file.path + suffix).delete() }
        file.delete()
    }
}
