package com.example

import android.app.Application
import android.os.Looper
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.data.GamePreferencesRepository
import com.example.data.GameSave
import com.example.data.Team
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.ui.viewmodel.GameViewModel
import com.example.ui.viewmodel.executeInstantBuy
import com.example.ui.viewmodel.parseProspects
import com.example.ui.viewmodel.promoteAcademyProspect
import com.example.ui.viewmodel.renewContract
import com.example.ui.viewmodel.repayBankLoan
import com.example.ui.viewmodel.skipLiveMatch
import com.example.ui.viewmodel.takeBankLoan
import com.example.usecase.ProcessTransfersUseCase
import com.example.usecase.TacticsUseCase
import com.example.usecase.YouthAcademyUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CareerFunctionalFlowTest {

    private lateinit var application: Application
    private val repositoriesToClose = mutableListOf<GameSaveRepository>()

    private data class Harness(
        val saveRepository: GameSaveRepository,
        val preferencesRepository: GamePreferencesRepository,
        val viewModel: GameViewModel
    )

    @Before
    fun setup() = runBlocking {
        application = ApplicationProvider.getApplicationContext()
        idleMainLooper()
        cleanPersistentState()
    }

    @After
    fun tearDown() = runBlocking {
        idleMainLooper()
        repositoriesToClose.forEach { repository ->
            runCatching { repository.closeAllDatabases() }
        }
        repositoriesToClose.clear()
        cleanPersistentState()
        idleMainLooper()
    }

    @Test
    fun `real career can be created managed advanced saved closed and reopened`() = runBlocking {
        val harness = newHarness()
        val viewModel = harness.viewModel

        // Real UI flow starts by selecting a physical save slot.
        viewModel.selectSaveSlot("1")
        val repository = harness.saveRepository.getRepositoryForSlot("1")

        awaitCondition(timeoutMs = 90_000L) {
            repository.getAllTeams().isNotEmpty()
        }

        val selectedTeam = repository.getAllTeams()
            .firstOrNull { it.country.equals("Brasil", ignoreCase = true) && it.division == 1 }
            ?: repository.getAllTeams().first()

        viewModel.startNewGame(
            selectedTeamId = selectedTeam.id,
            coachName = "QA Fase 7"
        )

        awaitCondition(timeoutMs = 180_000L) {
            val save = repository.getGameSave()
            save?.coachName == "QA Fase 7" &&
                repository.getPlayersByTeam(selectedTeam.id).size >= 18 &&
                repository.getFixturesForSeason(2026).isNotEmpty()
        }

        val initialSave = requireNotNull(repository.getGameSave())
        assertEquals(2026, initialSave.currentSeason)
        assertEquals(1, initialSave.currentWeek)
        assertEquals(selectedTeam.id, initialSave.playerTeamId)
        assertTrue(repository.getPlayersByTeam(selectedTeam.id).size >= 18)
        assertTrue(repository.getFixturesForSeason(2026).isNotEmpty())

        // A newly created career must be visible immediately in the saves menu.
        awaitCondition {
            val metadata = harness.preferencesRepository.loadSaveSlots().first { it.id == "1" }
            metadata.exists &&
                metadata.coachName == "QA Fase 7" &&
                metadata.teamName == selectedTeam.name &&
                metadata.season == 2026 &&
                metadata.week == 1
        }

        // Youth academy must be usable immediately in a brand-new career.
        val initialProspects = viewModel.parseProspects(initialSave.academyProspects)
        assertEquals(2, initialProspects.size)
        val promotedProspect = initialProspects.first()
        val rosterBeforePromotion = repository.getPlayersByTeam(selectedTeam.id)
        val rosterCountBeforePromotion = rosterBeforePromotion.size
        val rosterIdsBeforePromotion = rosterBeforePromotion.map { it.id }.toSet()

        val promotionJob = viewModel.promoteAcademyProspect(promotedProspect)
        withTimeout(30_000L) { promotionJob.join() }

        val saveAfterPromotion = requireNotNull(repository.getGameSave())
        assertEquals(
            rosterCountBeforePromotion + 1,
            repository.getPlayerCountByTeam(selectedTeam.id)
        )
        assertEquals(
            initialProspects.size - 1,
            viewModel.parseProspects(saveAfterPromotion.academyProspects).size
        )
        val promotedPlayer = repository.getPlayersByTeam(selectedTeam.id)
            .single { it.id !in rosterIdsBeforePromotion }
        assertEquals(promotedProspect.name, promotedPlayer.name)
        assertTrue(promotedPlayer.isFromAcademy)
        assertEquals(selectedTeam.country, promotedPlayer.nationality)

        // Contract renewal must persist through the same active session.
        val renewalTarget = repository.getPlayersByTeam(selectedTeam.id)
            .first { it.contractDurationWeeks > 0 && !it.isOnLoan }
        val originalContractWeeks = renewalTarget.contractDurationWeeks
        viewModel.renewContract(renewalTarget, durationWeeks = 8)
        awaitCondition {
            repository.getPlayer(renewalTarget.id)?.contractDurationWeeks == originalContractWeeks + 8
        }

        // Give the functional test a deliberately large management budget so it tests
        // management actions instead of depending on the balance of the chosen club.
        val financiallyPreparedSave = requireNotNull(repository.getGameSave()).copy(
            bankBalance = 5_000_000_000L,
            coachReputation = 100,
            stadiumCapacity = 100_000,
            ticketPrice = 200.0,
            socioTorcedoresCount = 100_000
        )
        repository.saveGameSave(financiallyPreparedSave)

        // Bank loan + partial repayment through the same public actions exposed to the UI.
        viewModel.takeBankLoan(1_000_000L)
        awaitCondition {
            repository.getGameSave()?.loanAmount == 1_000_000L
        }
        val balanceAfterLoan = requireNotNull(repository.getGameSave()).bankBalance
        assertTrue(balanceAfterLoan >= financiallyPreparedSave.bankBalance + 1_000_000L)

        viewModel.repayBankLoan(400_000L)
        awaitCondition {
            repository.getGameSave()?.loanAmount == 600_000L
        }
        assertEquals(
            balanceAfterLoan - 400_000L,
            requireNotNull(repository.getGameSave()).bankBalance
        )

        // Player loan: exercise the production use case used by the management layer.
        val loanTarget = repository.getAllPlayers()
            .asSequence()
            .filter {
                it.teamId != selectedTeam.id &&
                    it.teamId != 0L &&
                    !it.isOnLoan &&
                    it.contractDurationWeeks >= 4
            }
            .minByOrNull { it.force }
            ?: error("Nenhum jogador elegível para empréstimo no banco recém-criado")

        val loanResult = viewModel.processTransfersUseCase.loanPlayer(
            save = requireNotNull(repository.getGameSave()),
            player = loanTarget,
            loanWeeks = 4,
            weeklyFee = 1_000L
        )
        assertTrue(loanResult is ProcessTransfersUseCase.TransferResult.Success)
        assertTrue(requireNotNull(repository.getPlayer(loanTarget.id)).isOnLoan)
        assertTrue(repository.getAllLoans().any { it.playerId == loanTarget.id && it.status == "ACTIVE" })

        // Instant purchase: go through the public GameViewModel action used by the UI.
        val purchaseTarget = repository.getAllPlayers()
            .asSequence()
            .filter {
                it.id != loanTarget.id &&
                    it.teamId != selectedTeam.id &&
                    it.teamId != 0L &&
                    !it.isOnLoan
            }
            .minByOrNull { it.force }
            ?: error("Nenhum jogador elegível para compra no banco recém-criado")

        val purchaseResult = CompletableDeferred<Boolean>()
        viewModel.executeInstantBuy(purchaseTarget) { success ->
            if (!purchaseResult.isCompleted) purchaseResult.complete(success)
        }
        assertTrue(awaitDeferred(purchaseResult, timeoutMs = 30_000L))
        awaitCondition {
            repository.getPlayer(purchaseTarget.id)?.teamId == selectedTeam.id
        }

        // Play/simulate the user's real weekly fixture and advance all weekly systems.
        val saveBeforeWeek = requireNotNull(repository.getGameSave())
        val weekOneFixtures = repository.getFixturesForWeek(
            saveBeforeWeek.currentSeason,
            saveBeforeWeek.currentWeek
        )
        val userFixture = weekOneFixtures.firstOrNull {
            !it.isPlayed &&
                (it.homeTeamId == selectedTeam.id || it.awayTeamId == selectedTeam.id)
        }
        assertNotNull("A nova carreira precisa ter um jogo do usuário na semana inicial", userFixture)

        val renewedContractBeforeWeek = requireNotNull(repository.getPlayer(renewalTarget.id)).contractDurationWeeks
        viewModel.skipLiveMatch(requireNotNull(userFixture))

        awaitCondition(timeoutMs = 90_000L) {
            repository.getGameSave()?.currentWeek == saveBeforeWeek.currentWeek + 1
        }

        val progressedSave = requireNotNull(repository.getGameSave())
        assertEquals(2, progressedSave.currentWeek)
        assertTrue(
            repository.getFixturesForWeek(saveBeforeWeek.currentSeason, saveBeforeWeek.currentWeek)
                .first { it.id == userFixture.id }
                .isPlayed
        )
        assertEquals(
            renewedContractBeforeWeek - 1,
            requireNotNull(repository.getPlayer(renewalTarget.id)).contractDurationWeeks
        )
        assertEquals(selectedTeam.id, requireNotNull(repository.getPlayer(purchaseTarget.id)).teamId)
        assertTrue(requireNotNull(repository.getPlayer(loanTarget.id)).isOnLoan)
        assertEquals(600_000L, requireNotNull(repository.getGameSave()).loanAmount)

        // Exercise the same public save action used by the UI and wait for its callback.
        val saveCompleted = CompletableDeferred<Unit>()
        viewModel.saveGame(manual = true) {
            if (!saveCompleted.isCompleted) saveCompleted.complete(Unit)
        }
        awaitDeferred(saveCompleted, timeoutMs = 60_000L)

        val databaseName = harness.saveRepository.databaseNameForSlot("1")
        val backupFile = application.getDatabasePath("${databaseName}_backup")
        assertTrue(harness.saveRepository.databaseFileForSlot("1").exists())
        assertTrue("Backup físico do slot ativo não foi criado", backupFile.exists())

        awaitCondition {
            val metadata = harness.preferencesRepository.loadSaveSlots().first { it.id == "1" }
            metadata.exists && metadata.week == progressedSave.currentWeek
        }

        // Simulate closing the career/database and reopening it through a fresh VM/repository.
        viewModel.exitToSavesMenu()
        idleMainLooper()
        delay(300L)
        harness.saveRepository.closeAllDatabases()

        val reopenedHarness = newHarness()
        reopenedHarness.viewModel.selectSaveSlot("1")
        val reopenedRepository = reopenedHarness.saveRepository.getRepositoryForSlot("1")

        awaitCondition(timeoutMs = 90_000L) {
            reopenedRepository.getGameSave()?.currentWeek == progressedSave.currentWeek
        }

        val reopenedSave = requireNotNull(reopenedRepository.getGameSave())
        assertEquals("QA Fase 7", reopenedSave.coachName)
        assertEquals(progressedSave.currentSeason, reopenedSave.currentSeason)
        assertEquals(progressedSave.currentWeek, reopenedSave.currentWeek)
        assertEquals(progressedSave.playerTeamId, reopenedSave.playerTeamId)
        assertEquals(600_000L, reopenedSave.loanAmount)
        assertEquals(selectedTeam.id, requireNotNull(reopenedRepository.getPlayer(purchaseTarget.id)).teamId)
        assertTrue(requireNotNull(reopenedRepository.getPlayer(loanTarget.id)).isOnLoan)
        val reopenedAcademyPlayer = requireNotNull(reopenedRepository.getPlayer(promotedPlayer.id))
        assertTrue(reopenedAcademyPlayer.isFromAcademy)
        assertEquals(selectedTeam.country, reopenedAcademyPlayer.nationality)
        assertEquals(1, reopenedHarness.viewModel.parseProspects(reopenedSave.academyProspects).size)
    }

    @Test
    fun `all five physical save slots survive database close and reopen independently`() = runBlocking {
        val harness = newHarness()

        for (slotNumber in 1..5) {
            val slotId = slotNumber.toString()
            val teamId = 10_000L + slotNumber
            val repository = harness.saveRepository.getRepositoryForSlot(slotId)
            val team = Team(
                id = teamId,
                name = "Clube Slot $slotId",
                city = "Cidade $slotId",
                state = "MG",
                country = "Brasil",
                division = 1,
                rating = 70 + slotNumber
            )
            repository.saveTeams(listOf(team))
            repository.saveGameSave(
                GameSave(
                    coachName = "Técnico Slot $slotId",
                    currentSeason = 2026 + slotNumber,
                    currentWeek = slotNumber,
                    playerTeamId = teamId,
                    bankBalance = slotNumber * 1_000_000L
                )
            )
            harness.preferencesRepository.updateSlotMetadata(
                saveId = slotId,
                coachName = "Técnico Slot $slotId",
                teamName = team.name,
                season = 2026 + slotNumber,
                week = slotNumber,
                balance = slotNumber * 1_000_000L
            )
            assertTrue(harness.saveRepository.databaseFileForSlot(slotId).exists())
        }

        assertEquals(5, harness.preferencesRepository.loadSaveSlots().count { it.exists })
        harness.saveRepository.closeAllDatabases()

        val reopenedHarness = newHarness()
        for (slotNumber in 1..5) {
            val slotId = slotNumber.toString()
            val repository = reopenedHarness.saveRepository.getRepositoryForSlot(slotId)
            val save = requireNotNull(repository.getGameSave())
            assertEquals("Técnico Slot $slotId", save.coachName)
            assertEquals(2026 + slotNumber, save.currentSeason)
            assertEquals(slotNumber, save.currentWeek)
            assertEquals(10_000L + slotNumber, save.playerTeamId)
            assertEquals(slotNumber * 1_000_000L, save.bankBalance)
        }
    }

    private fun newHarness(): Harness {
        val factory = SlotDatabaseFactory(application)
        val saveRepository = GameSaveRepository(application, factory)
        val preferencesRepository = GamePreferencesRepository(application.dataStore, application, saveRepository)
        val viewModel = GameViewModel(
            application = application,
            saveRepository = saveRepository,
            preferencesRepo = preferencesRepository,
            youthAcademyUseCase = YouthAcademyUseCase(),
            tacticsUseCase = TacticsUseCase()
        )
        repositoriesToClose += saveRepository
        return Harness(saveRepository, preferencesRepository, viewModel)
    }

    private suspend fun awaitCondition(
        timeoutMs: Long = 30_000L,
        pollMs: Long = 50L,
        condition: suspend () -> Boolean
    ) {
        withTimeout(timeoutMs) {
            while (true) {
                idleMainLooper()
                if (condition()) break
                delay(pollMs)
            }
        }
    }

    private suspend fun <T> awaitDeferred(
        deferred: CompletableDeferred<T>,
        timeoutMs: Long
    ): T = withTimeout(timeoutMs) {
        while (!deferred.isCompleted) {
            idleMainLooper()
            delay(50L)
        }
        idleMainLooper()
        deferred.await()
    }

    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private suspend fun cleanPersistentState() {
        runCatching {
            application.dataStore.edit { preferences -> preferences.clear() }
        }
        application
            .getSharedPreferences("brasfut_retro_saves", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        for (slotNumber in 1..5) {
            val slotId = slotNumber.toString()
            val databaseName = SlotDatabaseFactory.databaseNameForSlot(slotId)
            application.deleteDatabase(databaseName)
            application.deleteDatabase("${databaseName}_backup")
        }
    }
}
