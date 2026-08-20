package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.GamePreferencesRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.ui.viewmodel.GameViewModel
import com.example.ui.viewmodel.processWeekEndEconomicAndEvolution
import com.example.usecase.TacticsUseCase
import com.example.usecase.YouthAcademyUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeeklyFinalizationAtomicityTest {

    private lateinit var application: Application
    private lateinit var saveRepository: GameSaveRepository
    private lateinit var viewModel: GameViewModel
    private val slotId = "5"

    @Before
    fun setup() = runBlocking {
        application = ApplicationProvider.getApplicationContext()
        application.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))

        val factory = SlotDatabaseFactory(application)
        saveRepository = GameSaveRepository(application, factory)
        viewModel = GameViewModel(
            application = application,
            saveRepository = saveRepository,
            preferencesRepo = GamePreferencesRepository(application.dataStore, application),
            youthAcademyUseCase = YouthAcademyUseCase(),
            tacticsUseCase = TacticsUseCase()
        )

        viewModel.getOrCreateSession(slotId)
        viewModel._currentSaveId.value = slotId
    }

    @After
    fun tearDown() = runBlocking {
        runCatching { saveRepository.closeAllDatabases() }
        application.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))
        application.deleteDatabase("${SlotDatabaseFactory.databaseNameForSlot(slotId)}_backup")
    }

    @Test
    fun `week finalization rolls back finances contracts and history when week advance fails`() = runBlocking {
        val repository = saveRepository.getRepositoryForSlot(slotId)
        repository.saveTeams(
            listOf(
                Team(
                    id = 1L,
                    name = "Time Atomicidade",
                    city = "Cidade",
                    state = "BR",
                    country = "Brasil",
                    division = 1,
                    isPlayerControlled = true,
                    rating = 70
                )
            )
        )

        val originalSave = GameSave(
            currentSeason = 2026,
            currentWeek = 1,
            playerTeamId = 1L,
            bankBalance = 5_000_000L,
            sponsorName = "Patrocinador",
            sponsorWeekly = 100_000L,
            sponsorWeeksRemaining = 10,
            stadiumCapacity = 10_000,
            academyWeeklyInvestment = 10_000L
        )
        repository.saveGameSave(originalSave)

        val originalPlayer = Player(
            id = 10L,
            teamId = 1L,
            name = "Atleta Atomicidade",
            age = 25,
            position = "ATA",
            force = 70,
            salary = 20_000L,
            contractDurationWeeks = 10
        )
        repository.savePlayers(listOf(originalPlayer))

        repository.db.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_week_advance
            BEFORE INSERT ON game_save
            WHEN NEW.currentWeek = 2 AND NEW.currentSeason = 2026
            BEGIN
                SELECT RAISE(ABORT, 'forced week advance failure');
            END
            """.trimIndent()
        )

        try {
            viewModel.processWeekEndEconomicAndEvolution()
            fail("Falha forçada no avanço da semana deveria abortar todo o fechamento semanal.")
        } catch (_: Exception) {
            // esperado: a transação externa deve desfazer finanças, contratos e histórico
        }

        assertEquals(originalSave, repository.getGameSave())
        assertEquals(originalPlayer, repository.getPlayer(originalPlayer.id))
        assertTrue(repository.getAllTransactions().isEmpty())
    }
}
