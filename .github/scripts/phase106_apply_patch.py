from pathlib import Path
import textwrap


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"expected block not found in {path}")
    p.write_text(text.replace(old, new, 1))


# GameSaveRepository: central physical fail-closed guard for every first open.
p = Path("app/src/main/java/com/example/data/repository/GameSaveRepository.kt")
text = p.read_text()
marker = '''data class SlotDatabaseInspection(
    val state: SlotDatabaseState,
    val save: GameSave? = null,
    val teamName: String? = null,
    val failureReason: String? = null
) {
    val newGameAllowed: Boolean
        get() = state == SlotDatabaseState.MISSING || state == SlotDatabaseState.EMPTY
}
'''
if "class SlotRecoveryRequiredException" not in text:
    replacement = marker + '''
class SlotRecoveryRequiredException(
    val inspection: SlotDatabaseInspection
) : IllegalStateException(
    "Slot exige recuperação antes da abertura: ${inspection.failureReason ?: inspection.state.name}"
)
'''
    if marker not in text:
        raise SystemExit("SlotDatabaseInspection marker not found")
    text = text.replace(marker, replacement, 1)
old_open = '''    @Synchronized
    fun getDatabaseForSlot(slotId: String): AppDatabase {
        return databaseFactory.getDatabaseForSlot(slotId)
    }

    @Synchronized
    fun getRepositoryForSlot(slotId: String): GameRepository {
        return repositories.getOrPut(slotId) {
            GameRepository(getDatabaseForSlot(slotId))
        }
    }
'''
new_open = '''    private fun requirePhysicalOpenAllowed(slotId: String) {
        physicalRecoveryInspection(slotId)?.let { inspection ->
            throw SlotRecoveryRequiredException(inspection)
        }
    }

    @Synchronized
    fun getDatabaseForSlot(slotId: String): AppDatabase {
        requirePhysicalOpenAllowed(slotId)
        return databaseFactory.getDatabaseForSlot(slotId)
    }

    @Synchronized
    fun getRepositoryForSlot(slotId: String): GameRepository {
        requirePhysicalOpenAllowed(slotId)
        return repositories.getOrPut(slotId) {
            GameRepository(databaseFactory.getDatabaseForSlot(slotId))
        }
    }
'''
if old_open not in text:
    raise SystemExit("GameSaveRepository open block not found")
p.write_text(text.replace(old_open, new_open, 1))


# GameRepository.deleteSave: any row in game_save blocks partial deletion.
replace_once(
    "app/src/main/java/com/example/data/repository.kt",
    '''    suspend fun deleteSave() = db.withTransaction {
        if (db.gameSaveDao().getGameSave() != null) {
            throw CancellationException(
                "Exclusão parcial de GameSave bloqueada: remova explicitamente o banco do slot."
            )
        }
        db.gameSaveDao().deleteSave()
    }
''',
    '''    suspend fun deleteSave() = db.withTransaction {
        val gameSaveRowCount = db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM game_save")
            .use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        if (gameSaveRowCount > 0) {
            throw CancellationException(
                "Exclusão parcial de GameSave bloqueada: $gameSaveRowCount linha(s) preservada(s); remova explicitamente o banco do slot."
            )
        }
        db.gameSaveDao().deleteSave()
    }
''',
)


# GameViewModel.selectSaveSlot: inspect before any session/seed, then revalidate after acquisition.
p = Path("app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt")
text = p.read_text()
start = text.index("    fun selectSaveSlot(saveId: String) {")
end = text.index("    fun repairRostersIfNecessary()", start)
select_block = '''    fun selectSaveSlot(saveId: String) {
        val gen = sessionGeneration.incrementAndGet()
        _selectedTeamId.value = null

        viewModelScope.launch(Dispatchers.IO) {
            suspend fun returnToRecoveryState(message: String) {
                if (gen != sessionGeneration.get()) return
                sessionGeneration.incrementAndGet()
                _activeSaveSession.value = null
                _currentSaveId.value = null
                _selectedTeamId.value = null
                _matchState.value = MatchState.IDLE
                saveRepository.closeAndRemoveSlot(saveId)

                try {
                    saveSlots.value = preferencesRepo.loadSaveSlots()
                } catch (reconcileError: kotlinx.coroutines.CancellationException) {
                    throw reconcileError
                } catch (reconcileError: Exception) {
                    Log.e("GameViewModel", "Falha ao reconciliar slot $saveId após bloqueio de abertura", reconcileError)
                }
                _toastMessage.emit(message)
            }

            try {
                val initialInspection = saveRepository.inspectSlot(saveId)
                if (initialInspection.state == com.example.data.repository.SlotDatabaseState.RECOVERY_REQUIRED) {
                    returnToRecoveryState("A carreira deste slot precisa de recuperação. Nenhum dado foi alterado.")
                    return@launch
                }
                if (gen != sessionGeneration.get()) return@launch

                // Há uma segunda barreira física síncrona dentro de getRepositoryForSlot().
                val repository = saveRepository.getRepositoryForSlot(saveId)
                val stableInspection = saveRepository.inspectSlot(saveId)
                if (stableInspection.state == com.example.data.repository.SlotDatabaseState.RECOVERY_REQUIRED) {
                    returnToRecoveryState("A carreira deste slot precisa de recuperação. Nenhum dado foi alterado.")
                    return@launch
                }
                if (gen != sessionGeneration.get()) return@launch

                val session = SaveSession(saveId, repository, gen)
                _activeSaveSession.value = session
                _currentSaveId.value = saveId

                val targetRepo = session.repository
                seedAllDefaultTeams(targetRepo, _selectedCountry.value)
                if (session.generation != sessionGeneration.get()) return@launch

                val teams = targetRepo.getAllTeams()
                val save = targetRepo.getGameSave()
                if (save != null) {
                    val targetTeam = targetRepo.getTeam(save.playerTeamId)
                    if (targetTeam != null) {
                        val resolvedCountry = DefaultData.getCountryForTeam(targetTeam.name)
                        withContext(Dispatchers.Main) {
                            _selectedCountry.value = resolvedCountry
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _selectedCountry.value = "Brasil"
                    }
                }

                repairRostersIfNecessarySync(session)
                if (session.generation != sessionGeneration.get()) return@launch

                if (save != null) {
                    val seasonFixtures = targetRepo.getFixturesForSeason(save.currentSeason)
                    if (seasonFixtures.isEmpty() && teams.isNotEmpty()) {
                        val newFixtures = generateFixturesForSeason(save.currentSeason, teams, save.playerTeamId)
                        targetRepo.saveFixtures(newFixtures)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("GameViewModel", "Falha ao abrir carreira do slot $saveId; preservando para recuperação", e)
                returnToRecoveryState("Não foi possível abrir a carreira. O slot foi preservado para recuperação.")
            }
        }
    }

'''
p.write_text(text[:start] + select_block + text[end:])


# GamePreferencesRepository: revalidate after metadata effects and before publishing a slot.
p = Path("app/src/main/java/com/example/data/GamePreferencesRepository.kt")
text = p.read_text()
if "MAX_RECONCILIATION_RETRIES" not in text:
    text = text.replace(
        '        private const val TAG = "GamePreferencesRepo"\n',
        '        private const val TAG = "GamePreferencesRepo"\n        private const val MAX_RECONCILIATION_RETRIES = 3\n',
        1,
    )
start = text.index("    suspend fun loadSaveSlots(): List<SaveSlotMetadata> {")
end = text.index("    private fun readStoredSlotMetadata", start)
reconcile_block = '''    suspend fun loadSaveSlots(): List<SaveSlotMetadata> =
        (1..5).map { index -> reconcileSlot(index.toString()) }

    private suspend fun readPreferencesSnapshot(): Preferences? = try {
        dataStore.data.first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Falha ao ler DataStore de metadata; usando fallback legado e Room", e)
        null
    }

    private suspend fun reconcileSlot(saveId: String, attempt: Int = 0): SaveSlotMetadata {
        val stored = readStoredSlotMetadata(readPreferencesSnapshot(), saveId)
        val before = saveRepository.inspectSlot(saveId)
        val projected = projectInspection(saveId, stored, before)

        // Última suspensão antes da publicação: detecta create/delete/update que venceu a corrida.
        val after = saveRepository.inspectSlot(saveId)
        if (sameSemanticSnapshot(before, after)) {
            return projected
        }

        if (attempt >= MAX_RECONCILIATION_RETRIES) {
            val latestStored = readStoredSlotMetadata(readPreferencesSnapshot(), saveId)
            return recoveryMetadata(
                saveId,
                latestStored,
                "O estado do slot mudou repetidamente durante a reconciliação. Os dados foram preservados e um novo jogo está bloqueado."
            )
        }
        return reconcileSlot(saveId, attempt + 1)
    }

    private suspend fun projectInspection(
        saveId: String,
        stored: StoredSlotMetadata,
        inspection: com.example.data.repository.SlotDatabaseInspection
    ): SaveSlotMetadata = when (inspection.state) {
        SlotDatabaseState.MISSING,
        SlotDatabaseState.EMPTY -> {
            if (stored.exists) {
                try {
                    removeSlotMetadata(saveId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Falha ao sanear metadata fantasma do slot $saveId", e)
                }
            }
            SaveSlotMetadata(id = saveId, exists = false)
        }

        SlotDatabaseState.VALID_CAREER -> {
            val save = checkNotNull(inspection.save)
            val authoritative = SaveSlotMetadata(
                id = saveId,
                exists = true,
                coachName = save.coachName,
                teamName = inspection.teamName ?: "Sem Clube",
                season = save.currentSeason,
                week = save.currentWeek,
                balance = save.bankBalance
            )
            if (!stored.matches(authoritative)) {
                try {
                    updateSlotMetadata(
                        saveId = saveId,
                        coachName = authoritative.coachName,
                        teamName = authoritative.teamName,
                        season = authoritative.season,
                        week = authoritative.week,
                        balance = authoritative.balance
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Carreira do slot $saveId recuperada, mas metadata não pôde ser reconstruída", e)
                }
            }
            authoritative
        }

        SlotDatabaseState.RECOVERY_REQUIRED -> recoveryMetadata(saveId, stored)
    }

    private fun recoveryMetadata(
        saveId: String,
        stored: StoredSlotMetadata,
        message: String = "O banco deste slot não pôde ser validado. Os dados foram preservados e um novo jogo está bloqueado."
    ): SaveSlotMetadata = SaveSlotMetadata(
        id = saveId,
        exists = true,
        coachName = stored.coachName.ifBlank { "Carreira preservada" },
        teamName = stored.teamName.ifBlank { "Recuperação necessária" },
        season = stored.season,
        week = stored.week,
        balance = stored.balance,
        recoveryRequired = true,
        recoveryMessage = message
    )

    private fun sameSemanticSnapshot(
        first: com.example.data.repository.SlotDatabaseInspection,
        second: com.example.data.repository.SlotDatabaseInspection
    ): Boolean {
        if (first.state != second.state) return false
        return when (first.state) {
            SlotDatabaseState.VALID_CAREER -> first.save == second.save && first.teamName == second.teamName
            SlotDatabaseState.RECOVERY_REQUIRED -> first.failureReason == second.failureReason
            SlotDatabaseState.MISSING,
            SlotDatabaseState.EMPTY -> true
        }
    }

'''
p.write_text(text[:start] + reconcile_block + text[end:])


# Deterministic regressions for sidecar/open, non-canonical rows, load-vs-delete and load-vs-create.
test = '''package com.example

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.data.GamePreferencesRepository
import com.example.data.GameSave
import com.example.data.Team
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.data.repository.SlotDatabaseState
import com.example.data.repository.SlotRecoveryRequiredException
import com.example.ui.viewmodel.GameViewModel
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
            application,
            saveRepository,
            preferencesRepository,
            YouthAcademyUseCase(),
            TacticsUseCase()
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
        val bytes = "phase-10.6-race-sidecar".toByteArray()
        sidecar.writeBytes(bytes)

        var blocked = false
        try {
            saveRepository.getRepositoryForSlot(slotId)
        } catch (e: SlotRecoveryRequiredException) {
            blocked = true
        }
        assertTrue(blocked)
        assertFalse(databaseFile.exists())
        assertTrue(sidecar.readBytes().contentEquals(bytes))

        viewModel.selectSaveSlot(slotId)
        withTimeout(5_000) {
            while (viewModel.saveSlots.value.single { it.id == slotId }.recoveryRequired.not()) delay(10)
        }
        assertNull(viewModel.currentSaveId.value)
        assertFalse(databaseFile.exists())
        assertTrue(sidecar.readBytes().contentEquals(bytes))
    }

    @Test
    fun nonCanonicalGameSaveRowsAreRecoveryAndPartialDeleteIsBlocked() = runBlocking {
        val slotId = "2"
        val repository = saveRepository.getRepositoryForSlot(slotId)
        repository.saveGameSave(GameSave(id = 2, coachName = "Linha Residual", currentSeason = 2042))
        assertEquals(SlotDatabaseState.RECOVERY_REQUIRED, saveRepository.inspectSlot(slotId).state)
        assertEquals(1, countGameSaveRows(slotId))

        var blocked = false
        try { repository.deleteSave() } catch (e: kotlinx.coroutines.CancellationException) { blocked = true }
        assertTrue(blocked)
        assertEquals(1, countGameSaveRows(slotId))

        repository.saveGameSave(GameSave(id = 1, coachName = "Canônica"))
        assertEquals(SlotDatabaseState.RECOVERY_REQUIRED, saveRepository.inspectSlot(slotId).state)
        assertEquals(2, countGameSaveRows(slotId))
        blocked = false
        try { repository.deleteSave() } catch (e: kotlinx.coroutines.CancellationException) { blocked = true }
        assertTrue(blocked)
        assertEquals(2, countGameSaveRows(slotId))
    }

    @Test
    fun reconciliationDeleteRaceCannotPublishGhostCareer() = runBlocking {
        val slotId = "3"
        createCareer(slotId, "Carreira excluída", 93_003L)
        clearMetadata()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val racing = GamePreferencesRepository(OneShotBlockingDataStore(application.dataStore, entered, release), application, saveRepository)
        val load = async(Dispatchers.Default) { racing.loadSaveSlots() }
        entered.await()
        assertTrue(saveRepository.deleteSlotDatabase(slotId))
        release.complete(Unit)
        val slot = load.await().single { it.id == slotId }
        assertFalse(slot.exists)
        assertFalse(slot.recoveryRequired)
        assertEquals(SlotDatabaseState.MISSING, saveRepository.inspectSlot(slotId).state)
    }

    @Test
    fun reconciliationCreateRaceCannotPublishEmptySlot() = runBlocking {
        val slotId = "4"
        preferencesRepository.updateSlotMetadata(slotId, "Fantasma", "Fantasma", 1999, 9, 1L)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val racing = GamePreferencesRepository(OneShotBlockingDataStore(application.dataStore, entered, release), application, saveRepository)
        val load = async(Dispatchers.Default) { racing.loadSaveSlots() }
        entered.await()
        val expected = createCareer(slotId, "Carreira criada na corrida", 94_004L)
        release.complete(Unit)
        val slot = load.await().single { it.id == slotId }
        assertTrue(slot.exists)
        assertFalse(slot.recoveryRequired)
        assertEquals(expected.coachName, slot.coachName)
        assertEquals(SlotDatabaseState.VALID_CAREER, saveRepository.inspectSlot(slotId).state)
    }

    private suspend fun createCareer(slotId: String, coachName: String, teamId: Long): GameSave {
        val repository = saveRepository.getRepositoryForSlot(slotId)
        val team = Team(teamId, "Clube $slotId", "Belo Horizonte", "MG", "Brasil", 1, false, 80)
        val save = GameSave(coachName = coachName, currentSeason = 2041, currentWeek = 12, playerTeamId = teamId, bankBalance = 41_000_000L)
        repository.saveTeams(listOf(team))
        repository.saveGameSave(save)
        return save
    }

    private fun countGameSaveRows(slotId: String): Int =
        saveRepository.getDatabaseForSlot(slotId).openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM game_save")
            .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private fun reopenRepositories() {
        if (::saveRepository.isInitialized) saveRepository.closeAllDatabases()
        factory = SlotDatabaseFactory(application)
        saveRepository = GameSaveRepository(application, factory)
        preferencesRepository = GamePreferencesRepository(application.dataStore, application, saveRepository)
    }

    private suspend fun clearMetadata() {
        application.dataStore.edit { it.clear() }
        application.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun clearAllSlots() {
        (1..5).forEach { index ->
            val name = SlotDatabaseFactory.databaseNameForSlot(index.toString())
            val file = application.getDatabasePath(name)
            application.deleteDatabase(name)
            listOf("-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
            file.delete()
        }
    }

    private class OneShotBlockingDataStore(
        private val delegate: DataStore<Preferences>,
        private val entered: CompletableDeferred<Unit>,
        private val release: CompletableDeferred<Unit>
    ) : DataStore<Preferences> {
        private val shouldBlock = AtomicBoolean(true)
        override val data: Flow<Preferences> get() = delegate.data
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (shouldBlock.compareAndSet(true, false)) {
                entered.complete(Unit)
                release.await()
            }
            return delegate.updateData(transform)
        }
    }
}
'''
Path("app/src/test/java/com/example/Phase106LifecycleRaceRegressionTest.kt").write_text(textwrap.dedent(test))
