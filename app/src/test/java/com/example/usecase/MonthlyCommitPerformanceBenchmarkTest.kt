package com.example.usecase

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.GameSave
import com.example.data.applyMonthlyEvolutionPlayerStateDeltas
import com.example.data.consumePristineCareerSeedTemplate
import com.example.data.validateMonthlyEvolutionUniverseCommitment
import com.example.data.getMonthlyEvolutionHistoryFingerprints
import com.example.data.insertMonthlyEvolutionHistoryRowsBulk
import com.example.data.local.SlotDatabaseFactory
import com.example.data.monthlyEvolutionFingerprint
import com.example.data.pristineCareerSeedTemplateOrNull
import com.example.data.repository.GameSaveRepository
import com.example.data.resetMonthlyEvolutionCounters
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonthlyCommitPerformanceBenchmarkTest {
    private lateinit var application: Application
    private lateinit var saveRepository: GameSaveRepository
    private val slotId = "6"

    private class ProbeRollback : RuntimeException()

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
    fun `canonical monthly commit profiles validation and write stages without persisting probes`() = runBlocking {
        val repository = saveRepository.getRepositoryForSlot(slotId)
        val marker = requireNotNull(repository.pristineCareerSeedTemplateOrNull()) {
            "Profiler precisa iniciar do career_seed_template.db canônico e intocado."
        }
        assertTrue(marker.playerCount >= 60_000)

        val userTeam = repository.getAllTeams().minByOrNull { it.id }
            ?: error("Corpus canônico precisa conter clubes.")
        repository.consumePristineCareerSeedTemplate()
        val save = GameSave(
            currentSeason = 2026,
            currentWeek = 8,
            playerTeamId = userTeam.id,
            bankBalance = 5_000_000L,
            sponsorName = "Benchmark Sponsor",
            sponsorWeekly = 100_000L,
            sponsorWeeksRemaining = 10,
            academyWeeklyInvestment = 0L
        )
        repository.saveGameSave(save)

        val plan = PlayerEvolutionUseCase(repository).prepareMonthlyEvolution(
            save = save,
            periodDate = "S2026_W8"
        )
        assertEquals(marker.playerCount, plan.expectedPlayerCount)
        assertTrue(plan.updatedPlayerStates.isNotEmpty())

        var startedAtNs = System.nanoTime()
        val existingHistory = if (plan.historyLogs.isEmpty()) {
            emptySet()
        } else {
            repository.getMonthlyEvolutionHistoryFingerprints(plan.periodDate)
        }
        val tHistoryLookupMillis = elapsedMillis(startedAtNs)
        if (plan.historyLogs.isNotEmpty()) {
            val plannedFingerprints = plan.historyLogs.mapTo(hashSetOf()) { it.monthlyEvolutionFingerprint() }
            assertTrue(plannedFingerprints.none { it in existingHistory })
        }

        startedAtNs = System.nanoTime()
        val teamsById = repository.getAllTeams().associateBy { it.id }
        val tTeamReadMillis = elapsedMillis(startedAtNs)
        assertTrue(
            plan.expectedTrainingCenterLevels.all { (teamId, level) ->
                (teamsById[teamId]?.trainingCenterLevel ?: 1) == level
            }
        )

        val commitment = requireNotNull(plan.expectedUniverseCommitment) {
            "Production monthly plan must retain the compact universe commitment."
        }
        assertTrue(plan.expectedInputs.isEmpty())
        assertEquals(plan.expectedPlayerCount, commitment.size)

        startedAtNs = System.nanoTime()
        val validation = repository.validateMonthlyEvolutionUniverseCommitment(
            expected = commitment,
            expectedTrainingCenterLevels = plan.expectedTrainingCenterLevels,
            currentTrainingCenterLevels = teamsById.mapValues { it.value.trainingCenterLevel },
            allowRosterCorrections = true
        )
        val tCommitmentValidationMillis = elapsedMillis(startedAtNs)
        assertTrue(validation.valid)
        assertTrue(validation.correctionIds.isEmpty())
        assertEquals(plan.expectedPlayerCount, validation.currentPlayerCount)

        var resetRows = 0
        var tResetCountersMillis = 0L
        try {
            repository.withTransaction {
                startedAtNs = System.nanoTime()
                resetRows = repository.resetMonthlyEvolutionCounters()
                tResetCountersMillis = elapsedMillis(startedAtNs)
                throw ProbeRollback()
            }
        } catch (_: ProbeRollback) {
            // Deliberately rollback: each write stage is measured against the same persisted state.
        }

        var playerWriteRows = 0
        var tPlayerWritesMillis = 0L
        try {
            repository.withTransaction {
                startedAtNs = System.nanoTime()
                playerWriteRows = repository.applyMonthlyEvolutionPlayerStateDeltas(plan.updatedPlayerStates)
                tPlayerWritesMillis = elapsedMillis(startedAtNs)
                throw ProbeRollback()
            }
        } catch (_: ProbeRollback) {
            // Deliberately rollback so the history probe and the real benchmark remain independent.
        }
        assertEquals(plan.updatedPlayerStates.size, playerWriteRows)

        var tHistoryWritesMillis = 0L
        if (plan.historyLogs.isNotEmpty()) {
            try {
                repository.withTransaction {
                    startedAtNs = System.nanoTime()
                    repository.saveHistoricoEvolucaoList(plan.historyLogs)
                    tHistoryWritesMillis = elapsedMillis(startedAtNs)
                    throw ProbeRollback()
                }
            } catch (_: ProbeRollback) {
                // Deliberately rollback; no benchmark probe may mutate the canonical career state.
            }
        }

        var bulkHistoryRows = 0
        var tHistoryWritesBulkMillis = 0L
        if (plan.historyLogs.isNotEmpty()) {
            try {
                repository.withTransaction {
                    startedAtNs = System.nanoTime()
                    bulkHistoryRows = repository.insertMonthlyEvolutionHistoryRowsBulk(plan.historyLogs)
                    tHistoryWritesBulkMillis = elapsedMillis(startedAtNs)
                    throw ProbeRollback()
                }
            } catch (_: ProbeRollback) {
                // Same state as the Room probe; compare only the persistence strategy.
            }
        }
        assertEquals(plan.historyLogs.size, bulkHistoryRows)

        println(
            "PERF_MONTHLY_COMMIT_STAGES " +
                "T_HISTORY_LOOKUP=$tHistoryLookupMillis " +
                "T_TEAM_READ=$tTeamReadMillis " +
                "T_COMMITMENT_VALIDATE=$tCommitmentValidationMillis " +
                "T_RESET_COUNTERS=$tResetCountersMillis " +
                "T_PLAYER_WRITES=$tPlayerWritesMillis " +
                "T_HISTORY_WRITES=$tHistoryWritesMillis " +
                "T_HISTORY_WRITES_BULK=$tHistoryWritesBulkMillis " +
                "COMMITMENT_ROWS_COUNT=${commitment.size} " +
                "RESET_ROWS_COUNT=$resetRows " +
                "PLAYER_WRITES_COUNT=$playerWriteRows " +
                "HISTORY_ROWS_COUNT=${plan.historyLogs.size} " +
                "BULK_HISTORY_ROWS_COUNT=$bulkHistoryRows"
        )
    }

    private fun elapsedMillis(startedAtNs: Long): Long =
        (System.nanoTime() - startedAtNs) / 1_000_000L

    private fun clearSlot() {
        val name = SlotDatabaseFactory.databaseNameForSlot(slotId)
        application.deleteDatabase(name)
        val file = application.getDatabasePath(name)
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            java.io.File(file.path + suffix).delete()
        }
    }
}
