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
    fun tearDown() {
        runBlocking {
            runCatching { saveRepository.closeAllDatabases() }
            application.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))
            application.deleteDatabase("${SlotDatabaseFactory.databaseNameForSlot(slotId)}_backup")
        }
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

    @Test
    fun `stale monthly plan rolls back weekly close without leaking generated offers`() = runBlocking {
        val repository = saveRepository.getRepositoryForSlot(slotId)
        val originalTeam = Team(
            id = 1L,
            name = "Time Stale",
            city = "Cidade",
            state = "BR",
            country = "Brasil",
            division = 1,
            isPlayerControlled = true,
            rating = 70,
            trainingCenterLevel = 1
        )
        val buyerTeam = Team(
            id = 2L,
            name = "Time Comprador",
            city = "Outra Cidade",
            state = "BR",
            country = "Brasil",
            division = 1,
            rating = 68
        )
        repository.saveTeams(listOf(originalTeam, buyerTeam))

        // Seed 2021041 (season 2021, week 4, team 1) deterministically enters the 40% offer path.
        // With 17 user players and another team available, the legacy implementation would publish
        // an IncomingOffer before the stale monthly-plan rollback and leak it into UI state.
        val originalSave = GameSave(
            currentSeason = 2021,
            currentWeek = 4,
            playerTeamId = 1L,
            bankBalance = 5_000_000L,
            sponsorName = "Patrocinador",
            sponsorWeekly = 100_000L,
            sponsorWeeksRemaining = 10,
            stadiumCapacity = 10_000,
            academyWeeklyInvestment = 10_000L
        )
        repository.saveGameSave(originalSave)

        val originalPlayers = (0 until 17).map { index ->
            Player(
                id = 10L + index,
                teamId = 1L,
                name = "Atleta Stale $index",
                age = 25,
                position = "ATA",
                force = 70,
                salary = 20_000L,
                contractDurationWeeks = 10,
                minutosJogados = if (index == 0) 240 else 0,
                mediaNotas = if (index == 0) 7.5 else 6.5
            )
        }
        val originalPlayer = originalPlayers.first()
        repository.savePlayers(originalPlayers)
        assertTrue(viewModel.incomingOffers.value.isEmpty())

        // O plano mensal captura CT nível 1 antes da transação. O tick de contratos abaixo dispara
        // esta mutação dentro da própria transação semanal; o commit mensal deve detectar o drift,
        // solicitar rollback e o wrapper do fechamento deve absorver somente esse conflito esperado.
        repository.db.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER make_monthly_plan_stale
            BEFORE UPDATE OF contractDurationWeeks ON players
            BEGIN
                UPDATE teams SET trainingCenterLevel = 2 WHERE id = 1;
            END
            """.trimIndent()
        )

        viewModel.processWeekEndEconomicAndEvolution()

        assertEquals(originalSave, repository.getGameSave())
        originalPlayers.forEach { player ->
            assertEquals(player, repository.getPlayer(player.id))
        }
        assertEquals(originalTeam, repository.getTeam(originalTeam.id))
        assertTrue(repository.getAllTransactions().isEmpty())
        assertTrue(repository.getHistoricoPorJogador(originalPlayer.id).isEmpty())
        assertTrue("Rollback semanal não pode publicar oferta de uma semana não persistida", viewModel.incomingOffers.value.isEmpty())
    }
}
