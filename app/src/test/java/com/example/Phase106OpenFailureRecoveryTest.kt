package com.example

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.data.GamePreferencesRepository
import com.example.data.GameSave
import com.example.data.Team
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.ui.viewmodel.GameViewModel
import com.example.ui.viewmodel.selectSaveSlotSafely
import com.example.usecase.TacticsUseCase
import com.example.usecase.YouthAcademyUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
class Phase106OpenFailureRecoveryTest {

    private lateinit var application: Application
    private lateinit var factory: SlotDatabaseFactory
    private lateinit var saveRepository: GameSaveRepository
    private lateinit var preferencesRepository: GamePreferencesRepository
    private lateinit var viewModel: GameViewModel
    private val slotId = "1"

    @Before
    fun setUp() = runBlocking {
        application = ApplicationProvider.getApplicationContext()
        application.dataStore.edit { it.clear() }
        application.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .edit().clear().commit()
        application.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))

        factory = SlotDatabaseFactory(application)
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
    fun tearDown() {
        runBlocking {
            saveRepository.closeAllDatabases()
            application.dataStore.edit { it.clear() }
            application.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
                .edit().clear().commit()
            application.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))
        }
    }

    @Test
    fun listedCareerThatBecomesUnreadableLeavesLoadingAndReturnsAsRecoveryRequired() = runBlocking {
        val repository = saveRepository.getRepositoryForSlot(slotId)
        val team = Team(
            id = 98_001L,
            name = "Clube Antes da Falha",
            city = "Belo Horizonte",
            state = "MG",
            country = "Brasil",
            division = 1,
            rating = 82,
            isPlayerControlled = true
        )
        val save = GameSave(
            coachName = "Técnico Antes da Falha",
            currentWeek = 15,
            currentSeason = 2037,
            playerTeamId = team.id,
            bankBalance = 88_000_000L
        )
        repository.saveTeams(listOf(team))
        repository.saveGameSave(save)
        preferencesRepository.updateSlotMetadata(
            saveId = slotId,
            coachName = save.coachName,
            teamName = team.name,
            season = save.currentSeason,
            week = save.currentWeek,
            balance = save.bankBalance
        )

        val listedBeforeFailure = preferencesRepository.loadSaveSlots().single { it.id == slotId }
        assertTrue(listedBeforeFailure.exists)
        assertFalse(listedBeforeFailure.recoveryRequired)
        viewModel.saveSlots.value = preferencesRepository.loadSaveSlots()

        saveRepository.closeAndRemoveSlot(slotId)
        val dbFile = saveRepository.databaseFileForSlot(slotId)
        dbFile.writeBytes("phase-10.6-open-failure".toByteArray())
        assertTrue(dbFile.exists())

        viewModel.selectSaveSlotSafely(slotId)

        repeat(200) {
            val recovery = viewModel.saveSlots.value.firstOrNull { it.id == slotId }?.recoveryRequired == true
            if (viewModel.currentSaveId.value == null && recovery) return@repeat
            delay(10)
        }

        assertNull("Falha de abertura deve encerrar a sessão/loading", viewModel.currentSaveId.value)
        val recoveredSlot = viewModel.saveSlots.value.single { it.id == slotId }
        assertTrue("Slot deve reaparecer como recuperação necessária", recoveredSlot.exists)
        assertTrue(recoveredSlot.recoveryRequired)
        assertTrue("Falha de abertura não pode apagar o banco", dbFile.exists())
    }
}
