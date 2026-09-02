package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.Fixture
import com.example.data.GamePreferencesRepository
import com.example.data.GameSave
import com.example.data.Team
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.ui.viewmodel.GameViewModel
import com.example.ui.viewmodel.recoverInterruptedWeeklyCloseIfNeeded
import com.example.ui.viewmodel.shouldRecoverInterruptedWeeklyClose
import com.example.usecase.TacticsUseCase
import com.example.usecase.YouthAcademyUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InterruptedWeeklyCloseRecoveryTest {
    private lateinit var application: Application
    private lateinit var saveRepository: GameSaveRepository
    private val slotId = "4"

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        application.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))
        reopenRepository()
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
    fun `persisted user match recovers cpu fixtures and weekly close exactly once after restart`() = runBlocking {
        val repository = saveRepository.getRepositoryForSlot(slotId)
        repository.saveTeams(testTeams())
        repository.saveGameSave(testSave())
        repository.saveFixtures(
            listOf(
                Fixture(
                    id = 401L,
                    season = 2026,
                    week = 1,
                    homeTeamId = 1L,
                    awayTeamId = 2L,
                    homeScore = 2,
                    awayScore = 1,
                    competitionType = "SERIE_A",
                    isPlayed = true
                ),
                Fixture(
                    id = 402L,
                    season = 2026,
                    week = 1,
                    homeTeamId = 3L,
                    awayTeamId = 4L,
                    competitionType = "SERIE_A",
                    isPlayed = false
                )
            )
        )

        // Simula kill/restart depois do commit da partida do usuário e antes do week-close.
        saveRepository.closeAllDatabases()
        reopenRepository()
        val firstVm = createViewModel()
        val firstSession = firstVm.getOrCreateSession(slotId)
        firstVm._currentSaveId.value = slotId

        assertTrue(firstVm.recoverInterruptedWeeklyCloseIfNeeded(firstSession))
        val firstRepo = firstSession.repository
        val afterFirstRecovery = requireNotNull(firstRepo.getGameSave())
        assertEquals(2, afterFirstRecovery.currentWeek)
        assertTrue(requireNotNull(firstRepo.getFixture(402L)).isPlayed)
        val firstBalance = afterFirstRecovery.bankBalance
        val firstSponsorWeeks = afterFirstRecovery.sponsorWeeksRemaining
        val firstTransactions = firstRepo.getAllTransactions().size

        // Novo processo: semana já avançada não pode receber finanças/evolução uma segunda vez.
        saveRepository.closeAllDatabases()
        reopenRepository()
        val secondVm = createViewModel()
        val secondSession = secondVm.getOrCreateSession(slotId)
        secondVm._currentSaveId.value = slotId

        assertFalse(secondVm.recoverInterruptedWeeklyCloseIfNeeded(secondSession))
        val afterSecondOpen = requireNotNull(secondSession.repository.getGameSave())
        assertEquals(2, afterSecondOpen.currentWeek)
        assertEquals(firstBalance, afterSecondOpen.bankBalance)
        assertEquals(firstSponsorWeeks, afterSecondOpen.sponsorWeeksRemaining)
        assertEquals(firstTransactions, secondSession.repository.getAllTransactions().size)
    }

    @Test
    fun `bye week never auto advances just because every existing fixture is played`() {
        val fixtures = listOf(
            Fixture(
                id = 410L,
                season = 2026,
                week = 1,
                homeTeamId = 2L,
                awayTeamId = 3L,
                homeScore = 0,
                awayScore = 0,
                competitionType = "SERIE_A",
                isPlayed = true
            )
        )
        assertFalse(shouldRecoverInterruptedWeeklyClose(fixtures, userTeamId = 1L))
    }

    @Test
    fun `pending user match is not classified as interrupted weekly close`() {
        val fixtures = listOf(
            Fixture(
                id = 411L,
                season = 2026,
                week = 1,
                homeTeamId = 1L,
                awayTeamId = 2L,
                competitionType = "SERIE_A",
                isPlayed = false
            )
        )
        assertFalse(shouldRecoverInterruptedWeeklyClose(fixtures, userTeamId = 1L))
    }

    private fun createViewModel(): GameViewModel = GameViewModel(
        application = application,
        saveRepository = saveRepository,
        preferencesRepo = GamePreferencesRepository(application.dataStore, application, saveRepository),
        youthAcademyUseCase = YouthAcademyUseCase(),
        tacticsUseCase = TacticsUseCase()
    )

    private fun reopenRepository() {
        saveRepository = GameSaveRepository(application, SlotDatabaseFactory(application))
    }

    private fun testSave() = GameSave(
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

    private fun testTeams() = listOf(
        Team(1L, "Usuário", "BH", "MG", "Brasil", 1, true, 75),
        Team(2L, "CPU A", "SP", "SP", "Brasil", 1, false, 70),
        Team(3L, "CPU B", "RJ", "RJ", "Brasil", 1, false, 68),
        Team(4L, "CPU C", "POA", "RS", "Brasil", 1, false, 67)
    )
}
