package com.example

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.data.GamePreferencesRepository
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Team
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.data.repository.SlotDatabaseState
import com.example.data.repository.SlotRecoveryRequiredException
import com.example.ui.viewmodel.GameViewModel
import com.example.ui.viewmodel.selectSaveSlotSafely
import com.example.usecase.TacticsUseCase
import com.example.usecase.YouthAcademyUseCase
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
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
class Phase106LifecycleRaceRegressionTest {

    private lateinit var application: Application
    private lateinit var factory: SlotDatabaseFactory
    private lateinit var saveRepository: GameSaveRepository
    private lateinit var preferencesRepository: GamePreferencesRepository
    private lateinit var viewModel: GameViewModel

    @Before
    fun setUp() = runBlocking {
        application = ApplicationProvider.getApplicationContext()
        clearMetadata()
        clearAllSlots()
        reopenRepositories()
        viewModel = GameViewModel(
            application = application,
            saveRepository = saveRepository,
            preferencesRepo = preferencesRepository,
            youthAcademyUseCase = YouthAcademyUseCase(),
            tacticsUseCase = TacticsUseCase()
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            saveRepository.closeAllDatabases()
            clearMetadata()
            clearAllSlots()
        }
    }

    @Test
    fun orphanedSidecarAppearingAfterListingCannotBeOpenedOrSeeded() = runBlocking {
        val slotId = "1"
        viewModel.saveSlots.value = preferencesRepository.loadSaveSlots()
        assertFalse(viewModel.saveSlots.value.single { it.id == slotId }.exists)

        val databaseFile = saveRepository.databaseFileForSlot(slotId)
        databaseFile.parentFile?.mkdirs()
        val sidecar = File(databaseFile.path + "-wal")
        val originalBytes = "phase-10.6-sidecar-after-listing".toByteArray()
        sidecar.writeBytes(originalBytes)

        var centralBarrierBlocked = false
        try {
            saveRepository.getRepositoryForSlot(slotId)
        } catch (e: SlotRecoveryRequiredException) {
            centralBarrierBlocked = true
        }

        assertTrue("Barreira central deve bloquear primeiro open sobre sidecar órfão", centralBarrierBlocked)
        assertFalse("Room não pode criar arquivo principal", databaseFile.exists())
        assertTrue(sidecar.readBytes().contentEquals(originalBytes))

        viewModel.selectSaveSlotSafely(slotId)

        withTimeout(5_000) {
            while (viewModel.saveSlots.value.single { it.id == slotId }.recoveryRequired.not()) {
                delay(10)
            }
        }

        assertNull("Seleção bloqueada não pode criar sessão", viewModel.currentSaveId.value)
        assertFalse("Seed não pode materializar banco sobre sidecar órfão", databaseFile.exists())
        assertTrue("Sidecar recuperável deve sobreviver", sidecar.exists())
        assertTrue("Bytes do sidecar não podem mudar", sidecar.readBytes().contentEquals(originalBytes))
        assertFalse(saveRepository.isNewGameAllowed(slotId))
    }

    @Test
    fun nonCanonicalGameSaveRowsAreRecoveryAndPartialDeleteIsBlocked() = runBlocking {
        val slotId = "2"
        val repository = saveRepository.getRepositoryForSlot(slotId)

        repository.saveGameSave(
            GameSave(
                id = 2,
                coachName = "Linha Residual",
                currentSeason = 2042,
                currentWeek = 7,
                playerTeamId = 0L
            )
        )

        var inspection = saveRepository.inspectSlot(slotId)
        assertEquals(SlotDatabaseState.RECOVERY_REQUIRED, inspection.state)
        assertFalse(inspection.newGameAllowed)
        assertTrue(inspection.failureReason?.contains("UnexpectedGameSaveRows") == true)
        assertEquals(1, countGameSaveRows(repository))

        var blocked = false
        try {
            repository.deleteSave()
        } catch (e: kotlinx.coroutines.CancellationException) {
            blocked = true
        }
        assertTrue("Qualquer linha game_save deve bloquear reset parcial", blocked)
        assertEquals("Linha id=2 precisa sobreviver", 1, countGameSaveRows(repository))

        repository.saveGameSave(GameSave(id = 1, coachName = "Canônica"))
        inspection = saveRepository.inspectSlot(slotId)
        assertEquals("id=1 + resíduo também é recovery", SlotDatabaseState.RECOVERY_REQUIRED, inspection.state)
        assertEquals(2, countGameSaveRows(repository))

        blocked = false
        try {
            repository.deleteSave()
        } catch (e: kotlinx.coroutines.CancellationException) {
            blocked = true
        }
        assertTrue(blocked)
        assertEquals("Nenhuma linha pode ser apagada silenciosamente", 2, countGameSaveRows(repository))
    }

    @Test
    fun reconciliationDeleteRaceCannotPublishGhostCareer() = runBlocking {
        val slotId = "3"
        createCareer(slotId, "Carreira que será excluída", 93_003L)
        clearMetadata()

        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val racingRepository = GamePreferencesRepository(
            OneShotBlockingDataStore(application.dataStore, entered, release),
            application,
            saveRepository
        )

        val load = async(Dispatchers.Default) { racingRepository.loadSaveSlots() }
        entered.await()

        assertTrue("Delete explícito precisa remover o slot durante a reconciliação", saveRepository.deleteSlotDatabase(slotId))
        release.complete(Unit)

        val slot = load.await().single { it.id == slotId }
        assertFalse("Snapshot VALID antigo não pode vencer o delete", slot.exists)
        assertFalse(slot.recoveryRequired)
        assertEquals(SlotDatabaseState.MISSING, saveRepository.inspectSlot(slotId).state)
        assertFalse(
            "Metadata fantasma criada pela passagem antiga deve ser saneada",
            application.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
                .getBoolean("slot_${slotId}_exists", true)
        )
    }

    @Test
    fun reconciliationCreateRaceCannotPublishEmptySlot() = runBlocking {
        val slotId = "4"
        preferencesRepository.updateSlotMetadata(
            saveId = slotId,
            coachName = "Metadata fantasma",
            teamName = "Fantasma",
            season = 1999,
            week = 9,
            balance = 1L
        )

        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val racingRepository = GamePreferencesRepository(
            OneShotBlockingDataStore(application.dataStore, entered, release),
            application,
            saveRepository
        )

        val load = async(Dispatchers.Default) { racingRepository.loadSaveSlots() }
        entered.await()

        val expected = createCareer(slotId, "Carreira criada durante a corrida", 94_004L)
        release.complete(Unit)

        val slot = load.await().single { it.id == slotId }
        assertTrue("Carreira criada durante saneamento não pode ser publicada como vazia", slot.exists)
        assertFalse(slot.recoveryRequired)
        assertEquals(expected.coachName, slot.coachName)
        assertEquals(expected.currentSeason, slot.season)
        assertEquals(expected.currentWeek, slot.week)
        assertEquals(SlotDatabaseState.VALID_CAREER, saveRepository.inspectSlot(slotId).state)
        assertTrue(
            "Metadata final deve ser reconstruída da carreira Room",
            application.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
                .getBoolean("slot_${slotId}_exists", false)
        )
    }

    private suspend fun createCareer(slotId: String, coachName: String, teamId: Long): GameSave {
        val repository = saveRepository.getRepositoryForSlot(slotId)
        val team = Team(
            id = teamId,
            name = "Clube $slotId",
            city = "Belo Horizonte",
            state = "MG",
            country = "Brasil",
            division = 1,
            rating = 80
        )
        val save = GameSave(
            coachName = coachName,
            currentSeason = 2041,
            currentWeek = 12,
            playerTeamId = teamId,
            bankBalance = 41_000_000L
        )
        repository.saveTeams(listOf(team))
        repository.saveGameSave(save)
        return save
    }

    private fun countGameSaveRows(repository: GameRepository): Int =
        repository.db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM game_save")
            .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private fun reopenRepositories() {
        if (::saveRepository.isInitialized) {
            saveRepository.closeAllDatabases()
        }
        factory = SlotDatabaseFactory(application)
        saveRepository = GameSaveRepository(application, factory)
        preferencesRepository = GamePreferencesRepository(application.dataStore, application, saveRepository)
    }

    private suspend fun clearMetadata() {
        application.dataStore.edit { it.clear() }
        application.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun clearAllSlots() {
        (1..5).forEach { index ->
            val databaseName = SlotDatabaseFactory.databaseNameForSlot(index.toString())
            val file = application.getDatabasePath(databaseName)
            application.deleteDatabase(databaseName)
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                File(file.path + suffix).delete()
            }
            file.delete()
        }
    }

    private class OneShotBlockingDataStore(
        private val delegate: DataStore<Preferences>,
        private val entered: CompletableDeferred<Unit>,
        private val release: CompletableDeferred<Unit>
    ) : DataStore<Preferences> {
        private val shouldBlock = AtomicBoolean(true)

        override val data: Flow<Preferences>
            get() = delegate.data

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences {
            if (shouldBlock.compareAndSet(true, false)) {
                entered.complete(Unit)
                release.await()
            }
            return delegate.updateData(transform)
        }
    }
}
