package com.example

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.data.DefaultData
import com.example.data.GamePreferencesRepository
import com.example.data.GameSave
import com.example.data.GlobalFootballSystem
import com.example.data.Team
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.ui.viewmodel.GameViewModel
import com.example.usecase.TacticsUseCase
import com.example.usecase.YouthAcademyUseCase
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase106OverwriteFeedbackTest {

    private lateinit var application: Application
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
        clearSlot()

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
        clearSlot()
    }

    @Test
    fun concurrentCareerGuardRollsBackAndEmitsFailureToastInsteadOfCancellation() = runBlocking {
        val country = "Brasil"
        val template = DefaultData.getTeamsForCountry(country).first()
        val teamId = GlobalFootballSystem.getGlobalId(country, template.name)
        val originalTeam = Team(
            id = teamId,
            name = template.name,
            city = template.city,
            state = template.state,
            country = country,
            division = template.division,
            rating = template.rating,
            stadiumName = template.stadium,
            logoUrl = DefaultData.getLogoForTeam(template.name, country),
            isPlayerControlled = true
        )
        val originalSave = GameSave(
            coachName = "Carreira Concorrente",
            currentSeason = 2038,
            currentWeek = 18,
            playerTeamId = teamId,
            bankBalance = 123_000_000L
        )

        // A sessão foi capturada quando o slot ainda podia estar no fluxo de criação; uma carreira
        // aparece antes da transação destrutiva. O guard de deleteSave deve bloquear o overwrite.
        val session = viewModel.getOrCreateSession(slotId)
        session.repository.saveTeams(listOf(originalTeam))
        session.repository.saveGameSave(originalSave)

        val toast = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(30_000) { viewModel.toastMessage.first() }
        }

        viewModel.startNewGame(selectedTeamId = teamId, coachName = "Não Pode Sobrescrever")

        val message = toast.await()
        assertTrue(
            "Bloqueio de domínio deve cair no catch reportável do Novo Jogo",
            message.contains("Nenhum save existente foi sobrescrito")
        )
        assertEquals("GameSave concorrente deve sobreviver ao rollback", originalSave, session.repository.getGameSave())
        assertEquals("Clube controlado deve sobreviver ao rollback", originalTeam, session.repository.getTeam(teamId))
    }

    private fun clearSlot() {
        val name = SlotDatabaseFactory.databaseNameForSlot(slotId)
        val file = application.getDatabasePath(name)
        application.deleteDatabase(name)
        listOf("-wal", "-shm", "-journal").forEach { suffix -> File(file.path + suffix).delete() }
        file.delete()
    }
}
