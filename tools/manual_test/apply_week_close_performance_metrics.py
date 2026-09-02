from pathlib import Path


def replace_region(text: str, start_anchor: str, end_anchor: str, replacement: str, label: str) -> str:
    start = text.find(start_anchor)
    if start < 0:
        raise SystemExit(f"{label}: start anchor not found")
    end = text.find(end_anchor, start)
    if end < 0:
        raise SystemExit(f"{label}: end anchor not found")
    return text[:start] + replacement + text[end:]


metrics_path = Path("app/src/main/java/com/example/ui/viewmodel/WeekClosePerformanceMetrics.kt")
metrics_path.write_text(r'''package com.example.ui.viewmodel

/**
 * Structured timings for the canonical weekly close. No Logcat output is emitted by production;
 * callers that need measurements (focused benchmarks/manual diagnostics) pass an explicit sink.
 */
data class WeekClosePerformanceMetrics(
    val season: Int,
    val week: Int,
    val tWeekFinanceMillis: Long,
    val tContractsMillis: Long,
    val tCpuSquadMillis: Long,
    val tTransfersMillis: Long,
    val tMonthlyPrepareMillis: Long,
    val tMonthlyCommitMillis: Long,
    val tCupsMillis: Long,
    val tWeekAdvanceMillis: Long,
    val tTotalWeekCloseMillis: Long,
    val monthlyPlayersCount: Int,
    val playersUpdatedCount: Int
) {
    fun asDiagnosticLine(prefix: String = "WEEK_CLOSE"): String =
        "$prefix " +
            "T_WEEK_FINANCE=$tWeekFinanceMillis " +
            "T_CONTRACTS=$tContractsMillis " +
            "T_CPU_SQUAD=$tCpuSquadMillis " +
            "T_TRANSFERS=$tTransfersMillis " +
            "T_MONTHLY_PREPARE=$tMonthlyPrepareMillis " +
            "T_MONTHLY_COMMIT=$tMonthlyCommitMillis " +
            "T_CUPS=$tCupsMillis " +
            "T_WEEK_ADVANCE=$tWeekAdvanceMillis " +
            "T_TOTAL_WEEK_CLOSE=$tTotalWeekCloseMillis " +
            "MONTHLY_PLAYERS_COUNT=$monthlyPlayersCount " +
            "PLAYERS_UPDATED_COUNT=$playersUpdatedCount"
}
''', encoding="utf-8")

match_path = Path("app/src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt")
match = match_path.read_text(encoding="utf-8")
start_anchor = "suspend fun GameViewModel.processWeekEndEconomicAndEvolution(targetRepo: GameRepository = repo) {"
end_anchor = "data class SeasonStandingRow("
new_function = r'''suspend fun GameViewModel.processWeekEndEconomicAndEvolution(
    targetRepo: GameRepository = repo,
    metricsSink: (WeekClosePerformanceMetrics) -> Unit = {}
) {
    val totalStartedAtNs = System.nanoTime()
    val requestedSave = targetRepo.getGameSave() ?: return
    val monthlyPeriod = if (requestedSave.currentWeek % 4 == 0) {
        "S${requestedSave.currentSeason}_W${requestedSave.currentWeek}"
    } else {
        null
    }

    var tMonthlyPrepareMillis = 0L
    val preparedMonthlyPlan = monthlyPeriod?.let { period ->
        val startedAtNs = System.nanoTime()
        val plan = com.example.usecase.PlayerEvolutionUseCase(targetRepo)
            .prepareMonthlyEvolution(requestedSave, period)
        tMonthlyPrepareMillis = (System.nanoTime() - startedAtNs) / 1_000_000L
        plan
    }

    var tWeekFinanceMillis = 0L
    var tContractsMillis = 0L
    var tCpuSquadMillis = 0L
    var tTransfersMillis = 0L
    var tMonthlyCommitMillis = 0L
    var tCupsMillis = 0L
    var tWeekAdvanceMillis = 0L
    var stagedIncomingOffer: IncomingOffer? = null
    var weeklyCloseCommitted = false

    try {
        targetRepo.withTransaction {
            val save = targetRepo.getGameSave() ?: return@withTransaction
            if (save.currentSeason != requestedSave.currentSeason ||
                save.currentWeek != requestedSave.currentWeek ||
                save.playerTeamId != requestedSave.playerTeamId
            ) {
                return@withTransaction
            }

            val currentWeekFixtures = targetRepo.getFixturesForWeek(save.currentSeason, save.currentWeek)
            if (currentWeekFixtures.any { !it.isPlayed }) {
                return@withTransaction
            }

            val isHomeMatch = currentWeekFixtures.any {
                it.isPlayed && it.homeTeamId == save.playerTeamId
            }

            val cpuSquadManagement = com.example.usecase.CpuSquadManagementUseCase(targetRepo)
            var stageStartedAtNs = System.nanoTime()
            cpuSquadManagement.renewCpuContractsBeforeWeeklyTick()
            tContractsMillis += (System.nanoTime() - stageStartedAtNs) / 1_000_000L

            val userPlayers = targetRepo.getPlayersByTeam(save.playerTeamId)
            stageStartedAtNs = System.nanoTime()
            val updatedSave = com.example.usecase.FinanceUseCase(targetRepo)
                .processWeeklyFinances(save, isHomeMatch, userPlayers)
            tWeekFinanceMillis = (System.nanoTime() - stageStartedAtNs) / 1_000_000L

            stageStartedAtNs = System.nanoTime()
            com.example.usecase.ProcessTransfersUseCase(targetRepo).processWeeklyContractsAndLoans()
            tContractsMillis += (System.nanoTime() - stageStartedAtNs) / 1_000_000L

            stageStartedAtNs = System.nanoTime()
            cpuSquadManagement.processWeeklyAfterContracts()
            tCpuSquadMillis = (System.nanoTime() - stageStartedAtNs) / 1_000_000L

            stageStartedAtNs = System.nanoTime()
            stagedIncomingOffer = prepareWeeklyIncomingOffer(targetRepo)
            tTransfersMillis = (System.nanoTime() - stageStartedAtNs) / 1_000_000L

            if (monthlyPeriod != null) {
                stageStartedAtNs = System.nanoTime()
                val committedPreparedPlan = preparedMonthlyPlan?.let { plan ->
                    com.example.usecase.PlayerEvolutionUseCase(targetRepo).commitMonthlyEvolution(
                        plan = plan,
                        allowWeeklyRosterCorrections = true
                    )
                } == true
                tMonthlyCommitMillis = (System.nanoTime() - stageStartedAtNs) / 1_000_000L
                if (!committedPreparedPlan) {
                    throw StaleWeeklyMonthlyEvolutionRollback()
                }
            }

            stageStartedAtNs = System.nanoTime()
            CupCompetitionSystem.processProgression(
                save.currentSeason,
                save.currentWeek,
                targetRepo
            )
            SuperMundialSystem.processProgression(
                save.currentSeason,
                save.currentWeek,
                targetRepo
            )
            tCupsMillis = (System.nanoTime() - stageStartedAtNs) / 1_000_000L

            stageStartedAtNs = System.nanoTime()
            if (updatedSave.currentWeek >= GameCalendar.WEEKS_PER_SEASON) {
                advanceToNextSeason(updatedSave, targetRepo)
            } else {
                val nextWeekSave = updatedSave.copy(currentWeek = updatedSave.currentWeek + 1)
                targetRepo.saveGameSave(nextWeekSave)
            }
            tWeekAdvanceMillis = (System.nanoTime() - stageStartedAtNs) / 1_000_000L
            weeklyCloseCommitted = true
        }
    } catch (_: StaleWeeklyMonthlyEvolutionRollback) {
        _toastMessage.emit(
            "O estado de treino mudou durante o fechamento semanal. A semana não foi avançada; tente novamente."
        )
        return
    }

    if (weeklyCloseCommitted) {
        metricsSink(
            WeekClosePerformanceMetrics(
                season = requestedSave.currentSeason,
                week = requestedSave.currentWeek,
                tWeekFinanceMillis = tWeekFinanceMillis,
                tContractsMillis = tContractsMillis,
                tCpuSquadMillis = tCpuSquadMillis,
                tTransfersMillis = tTransfersMillis,
                tMonthlyPrepareMillis = tMonthlyPrepareMillis,
                tMonthlyCommitMillis = tMonthlyCommitMillis,
                tCupsMillis = tCupsMillis,
                tWeekAdvanceMillis = tWeekAdvanceMillis,
                tTotalWeekCloseMillis = (System.nanoTime() - totalStartedAtNs) / 1_000_000L,
                monthlyPlayersCount = preparedMonthlyPlan?.expectedPlayerCount ?: 0,
                playersUpdatedCount = preparedMonthlyPlan?.updatedPlayers?.size ?: 0
            )
        )
    }

    if (weeklyCloseCommitted && activeSaveSession.value?.repository === targetRepo) {
        stagedIncomingOffer?.let { publishIncomingOffer(it) }
    }
}

'''
match = replace_region(match, start_anchor, end_anchor, new_function, "weekly close instrumentation")
match_path.write_text(match, encoding="utf-8")

benchmark_path = Path("app/src/test/java/com/example/usecase/WeekEightClosePerformanceBenchmarkTest.kt")
benchmark_path.write_text(r'''package com.example.usecase

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.Fixture
import com.example.data.GamePreferencesRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.ui.viewmodel.GameViewModel
import com.example.ui.viewmodel.WeekClosePerformanceMetrics
import com.example.ui.viewmodel.processWeekEndEconomicAndEvolution
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeekEightClosePerformanceBenchmarkTest {
    private lateinit var application: Application
    private lateinit var saveRepository: GameSaveRepository
    private val slotId = "3"

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        clearSlot()
        saveRepository = GameSaveRepository(application, SlotDatabaseFactory(application))
    }

    @After
    fun tearDown() {
        runBlocking { runCatching { saveRepository.closeAllDatabases() } }
        clearSlot()
    }

    @Test
    fun `week eight close measures complete sixty thousand player monthly universe`() = runBlocking {
        val repository = saveRepository.getRepositoryForSlot(slotId)
        val userTeam = Team(
            id = 1L,
            name = "Benchmark User",
            city = "BH",
            state = "MG",
            country = "Brasil",
            division = 1,
            isPlayerControlled = true,
            rating = 75
        )
        val cpuTeam = Team(
            id = 2L,
            name = "Benchmark CPU",
            city = "SP",
            state = "SP",
            country = "Brasil",
            division = 1,
            rating = 70
        )
        repository.saveTeams(listOf(userTeam, cpuTeam))

        val totalPlayers = 60_000
        val players = ArrayList<Player>(totalPlayers)
        repeat(totalPlayers) { index ->
            val teamId = when {
                index < 16 -> 1L
                index < 32 -> 2L
                else -> null
            }
            players += Player(
                id = 100_000L + index,
                teamId = teamId,
                name = "Bench %05d".format(index),
                age = 24 + (index % 7),
                position = if (index == 0 || index == 16) "GOL" else "ATA",
                force = 68 + (index % 5),
                potential = 80,
                salary = if (teamId == null) 0L else 10_000L,
                contractDurationWeeks = if (teamId == null) 0 else 52,
                minutosJogados = 0,
                mediaNotas = 0.0
            )
        }
        repository.savePlayers(players)

        repository.saveGameSave(
            GameSave(
                currentSeason = 2026,
                currentWeek = 8,
                playerTeamId = 1L,
                bankBalance = 5_000_000L,
                sponsorName = "Benchmark Sponsor",
                sponsorWeekly = 100_000L,
                sponsorWeeksRemaining = 10,
                academyWeeklyInvestment = 0L
            )
        )
        repository.saveFixtures(
            listOf(
                Fixture(
                    id = 808L,
                    season = 2026,
                    week = 8,
                    homeTeamId = 1L,
                    awayTeamId = 2L,
                    homeScore = 1,
                    awayScore = 0,
                    competitionType = "SERIE_A",
                    isPlayed = true
                )
            )
        )

        val viewModel = GameViewModel(
            application = application,
            saveRepository = saveRepository,
            preferencesRepo = GamePreferencesRepository(application.dataStore, application, saveRepository),
            youthAcademyUseCase = YouthAcademyUseCase(),
            tacticsUseCase = TacticsUseCase()
        )
        viewModel.getOrCreateSession(slotId)
        viewModel._currentSaveId.value = slotId

        var measured: WeekClosePerformanceMetrics? = null
        viewModel.processWeekEndEconomicAndEvolution(repository) { metrics -> measured = metrics }

        val metrics = measured
        assertNotNull("Fechamento confirmado precisa publicar métricas", metrics)
        metrics!!
        assertEquals(60_000, metrics.monthlyPlayersCount)
        assertEquals(9, repository.getGameSave()?.currentWeek)
        println(metrics.asDiagnosticLine(prefix = "PERF_BASELINE_WEEK8"))
    }

    private fun clearSlot() {
        val name = SlotDatabaseFactory.databaseNameForSlot(slotId)
        application.deleteDatabase(name)
        val file = application.getDatabasePath(name)
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            java.io.File(file.path + suffix).delete()
        }
    }
}
''', encoding="utf-8")
