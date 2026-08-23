package com.example

import android.app.Application
import android.content.Context
import android.os.Looper
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase106EditorPreparationSerializationTest {

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

        saveRepository = GameSaveRepository(application, SlotDatabaseFactory(application))
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
    fun concurrentEditorEntrySerializesBootstrapAndNeverDuplicatesPlayers() = runBlocking {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val firstReady = CompletableDeferred<Boolean>()

        val secondAttempted = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        val secondReady = CompletableDeferred<Boolean>()

        viewModel.ensureSaveActiveForEditor(
            preparationCheckpoint = {
                firstEntered.complete(Unit)
                releaseFirst.await()
            },
            onReady = { firstReady.complete(it) }
        )
        withTimeout(5_000) { firstEntered.await() }

        viewModel.ensureSaveActiveForEditor(
            preparationAttemptCheckpoint = {
                secondAttempted.complete(Unit)
            },
            preparationCheckpoint = {
                secondEntered.complete(Unit)
                releaseSecond.await()
            },
            onReady = { secondReady.complete(it) }
        )

        // Prova que a segunda coroutine realmente chegou ao mutex; a ausência de entrada no
        // checkpoint interno passa a ser uma propriedade determinística, não uma janela temporal.
        withTimeout(5_000) { secondAttempted.await() }
        assertFalse(
            "A segunda preparação não pode atravessar o mutex enquanto a primeira ainda está ativa",
            secondEntered.isCompleted
        )

        releaseFirst.complete(Unit)
        assertTrue("Primeira preparação precisa concluir", awaitMainThreadDeferred(firstReady))

        withTimeout(5_000) { secondEntered.await() }
        val repository = saveRepository.getRepositoryForSlot("1")
        val playersAfterFirstBootstrap = repository.getAllPlayers().size
        assertTrue("Primeiro bootstrap precisa materializar jogadores", playersAfterFirstBootstrap > 0)

        releaseSecond.complete(Unit)
        assertTrue("Segunda preparação serializada precisa concluir", awaitMainThreadDeferred(secondReady))

        val playersAfterSecondBootstrap = repository.getAllPlayers().size
        assertEquals(
            "Segunda entrada no editor não pode semear um segundo roster",
            playersAfterFirstBootstrap,
            playersAfterSecondBootstrap
        )
    }

    private suspend fun <T> awaitMainThreadDeferred(deferred: CompletableDeferred<T>): T =
        withTimeout(5_000) {
            while (!deferred.isCompleted) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(1)
            }
            deferred.await()
        }

    private fun clearSlotOne() {
        val name = SlotDatabaseFactory.databaseNameForSlot("1")
        val file = application.getDatabasePath(name)
        application.deleteDatabase(name)
        listOf("-wal", "-shm", "-journal").forEach { suffix -> File(file.path + suffix).delete() }
        file.delete()
    }
}
