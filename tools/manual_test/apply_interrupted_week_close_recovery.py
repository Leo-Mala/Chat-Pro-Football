from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: anchor count={count}, expected=1")
    return text.replace(old, new, 1)


match_path = Path("app/src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt")
match = match_path.read_text(encoding="utf-8")

old_sim = '''suspend fun GameViewModel.simulateCpuMatchesForCurrentWeek() {
    val save = repo.getGameSave() ?: return
    simulateWeekUseCase.simulateCpuMatchesForWeek(
        season = save.currentSeason,
        week = save.currentWeek,
        excludedTeamId = save.playerTeamId
    )
}
'''
new_sim = '''suspend fun GameViewModel.simulateCpuMatchesForCurrentWeek(targetRepo: GameRepository = repo) {
    val save = targetRepo.getGameSave() ?: return
    com.example.usecase.SimulateWeekUseCase(targetRepo).simulateCpuMatchesForWeek(
        season = save.currentSeason,
        week = save.currentWeek,
        excludedTeamId = save.playerTeamId
    )
}
'''
match = replace_once(match, old_sim, new_sim, "cpu simulation target repository")

prepare_start = match.index("private suspend fun GameViewModel.prepareWeeklyIncomingOffer()")
prepare_end = match.index("private fun GameViewModel.publishIncomingOffer", prepare_start)
prepare = match[prepare_start:prepare_end]
prepare = replace_once(
    prepare,
    "private suspend fun GameViewModel.prepareWeeklyIncomingOffer(): IncomingOffer?",
    "private suspend fun GameViewModel.prepareWeeklyIncomingOffer(targetRepo: GameRepository = repo): IncomingOffer?",
    "incoming offer signature",
)
prepare = prepare.replace("repo.", "targetRepo.")
match = match[:prepare_start] + prepare + match[prepare_end:]

close_start = match.index("suspend fun GameViewModel.processWeekEndEconomicAndEvolution()")
close_end = match.index("data class SeasonStandingRow", close_start)
close = match[close_start:close_end]
close = replace_once(
    close,
    "suspend fun GameViewModel.processWeekEndEconomicAndEvolution()",
    "suspend fun GameViewModel.processWeekEndEconomicAndEvolution(targetRepo: GameRepository = repo)",
    "weekly close signature",
)
close = close.replace("repo.", "targetRepo.")
close = close.replace(
    "playerEvolutionUseCase.prepareMonthlyEvolution(requestedSave, period)",
    "com.example.usecase.PlayerEvolutionUseCase(targetRepo).prepareMonthlyEvolution(requestedSave, period)",
)
close = close.replace(
    "playerEvolutionUseCase.commitMonthlyEvolution(",
    "com.example.usecase.PlayerEvolutionUseCase(targetRepo).commitMonthlyEvolution(",
)
close = close.replace(
    "financeUseCase.processWeeklyFinances(save, isHomeMatch, userPlayers)",
    "com.example.usecase.FinanceUseCase(targetRepo).processWeeklyFinances(save, isHomeMatch, userPlayers)",
)
close = close.replace(
    "processTransfersUseCase.processWeeklyContractsAndLoans()",
    "com.example.usecase.ProcessTransfersUseCase(targetRepo).processWeeklyContractsAndLoans()",
)
close = close.replace("prepareWeeklyIncomingOffer()", "prepareWeeklyIncomingOffer(targetRepo)")
close = close.replace("advanceToNextSeason(updatedSave)", "advanceToNextSeason(updatedSave, targetRepo)")
close = replace_once(
    close,
    '''    if (weeklyCloseCommitted) {
        stagedIncomingOffer?.let { publishIncomingOffer(it) }
    }
''',
    '''    if (weeklyCloseCommitted && activeSaveSession.value?.repository === targetRepo) {
        stagedIncomingOffer?.let { publishIncomingOffer(it) }
    }
''',
    "weekly offer publication guard",
)
match = match[:close_start] + close + match[close_end:]

old_advance = '''suspend fun GameViewModel.advanceToNextSeason(save: GameSave) {
    val transitionUseCase = com.example.usecase.SeasonTransitionUseCase(
        repository = repo,
        generateCalendarUseCase = generateCalendarUseCase,
        databaseIntegrityUseCase = com.example.usecase.DatabaseIntegrityUseCase(repo)
    )
    transitionUseCase.advanceToNextSeason(save)
}
'''
new_advance = '''suspend fun GameViewModel.advanceToNextSeason(save: GameSave, targetRepo: GameRepository = repo) {
    val transitionUseCase = com.example.usecase.SeasonTransitionUseCase(
        repository = targetRepo,
        generateCalendarUseCase = com.example.usecase.GenerateCalendarUseCase(targetRepo),
        databaseIntegrityUseCase = com.example.usecase.DatabaseIntegrityUseCase(targetRepo)
    )
    transitionUseCase.advanceToNextSeason(save)
}
'''
match = replace_once(match, old_advance, new_advance, "season transition target repository")
match_path.write_text(match, encoding="utf-8")

vm_path = Path("app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt")
vm = vm_path.read_text(encoding="utf-8")
old_open_tail = '''                        targetRepo.saveFixtures(newFixtures)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
'''
new_open_tail = '''                        targetRepo.saveFixtures(newFixtures)
                    }
                }

                if (session.generation != sessionGeneration.get()) return@launch
                recoverInterruptedWeeklyCloseIfNeeded(session)
            } catch (e: kotlinx.coroutines.CancellationException) {
'''
vm = replace_once(vm, old_open_tail, new_open_tail, "select slot recovery hook")
vm_path.write_text(vm, encoding="utf-8")

recovery_path = Path("app/src/main/java/com/example/ui/viewmodel/WeeklyCloseRecovery.kt")
recovery_path.write_text('''package com.example.ui.viewmodel

import com.example.data.Fixture

internal fun shouldRecoverInterruptedWeeklyClose(
    fixtures: List<Fixture>,
    userTeamId: Long
): Boolean {
    val userFixtures = fixtures.filter { fixture ->
        fixture.homeTeamId == userTeamId || fixture.awayTeamId == userTeamId
    }
    return userFixtures.isNotEmpty() && userFixtures.all { it.isPlayed }
}

private fun GameViewModel.isRecoverySessionActive(session: SaveSession): Boolean {
    val active = activeSaveSession.value ?: return false
    return active.slotId == session.slotId &&
        active.generation == session.generation &&
        active.repository === session.repository &&
        currentSaveId.value == session.slotId
}

/**
 * Completa somente a metade durável de uma semana que já teve a partida do usuário persistida.
 * Sem partida do usuário na semana (folga) ou com partida ainda pendente, a abertura é passiva.
 *
 * O repositório é capturado da SaveSession e nunca é resolvido novamente pelo slot ativo durante a
 * recuperação. Uma troca de carreira pode interromper a recuperação antes do fechamento, mas não
 * pode redirecionar CPU fixtures/finanças/evolução para outro banco.
 */
internal suspend fun GameViewModel.recoverInterruptedWeeklyCloseIfNeeded(
    session: SaveSession
): Boolean {
    if (!isRecoverySessionActive(session)) return false
    val targetRepo = session.repository
    val before = targetRepo.getGameSave() ?: return false
    var fixtures = targetRepo.getFixturesForWeek(before.currentSeason, before.currentWeek)
    if (!shouldRecoverInterruptedWeeklyClose(fixtures, before.playerTeamId)) return false

    simulateCpuMatchesForCurrentWeek(targetRepo)
    if (!isRecoverySessionActive(session)) return false

    fixtures = targetRepo.getFixturesForWeek(before.currentSeason, before.currentWeek)
    if (fixtures.any { !it.isPlayed }) return false

    processWeekEndEconomicAndEvolution(targetRepo)
    val after = targetRepo.getGameSave() ?: return false
    return after.currentSeason != before.currentSeason || after.currentWeek != before.currentWeek
}
''', encoding="utf-8")

test_path = Path("app/src/test/java/com/example/InterruptedWeeklyCloseRecoveryTest.kt")
test_path.write_text('''package com.example

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
    fun tearDown() = runBlocking {
        runCatching { saveRepository.closeAllDatabases() }
        application.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))
        application.deleteDatabase("${SlotDatabaseFactory.databaseNameForSlot(slotId)}_backup")
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
                    homeTeamId = 2L,
                    awayTeamId = 3L,
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
        Team(3L, "CPU B", "RJ", "RJ", "Brasil", 1, false, 68)
    )
}
''', encoding="utf-8")
