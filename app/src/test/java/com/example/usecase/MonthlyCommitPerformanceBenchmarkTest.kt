package com.example.usecase

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.GameSave
import com.example.data.applyMonthlyEvolutionPlayerStateDeltas
import com.example.data.consumePristineCareerSeedTemplate
import com.example.data.currentMonthlyEvolutionRevisionSnapshotOrNull
import com.example.data.getMonthlyEvolutionHistoryFingerprints
import com.example.data.insertMonthlyEvolutionHistoryRowsBulk
import com.example.data.local.SlotDatabaseFactory
import com.example.data.monthlyEvolutionFingerprint
import com.example.data.pristineCareerSeedTemplateOrNull
import com.example.data.repository.GameSaveRepository
import com.example.data.resetMonthlyEvolutionCounters
import com.example.data.validateMonthlyEvolutionUniverseCommitment
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
    fun `canonical monthly commit profiles epoch fast path against full validation baseline`() =
        runBlocking {
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

            val useCase = PlayerEvolutionUseCase(repository)
            val plan = useCase.prepareMonthlyEvolution(
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
                val plannedFingerprints =
                    plan.historyLogs.mapTo(hashSetOf()) { it.monthlyEvolutionFingerprint() }
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
            val expectedRevision = requireNotNull(plan.expectedPlayerRevision) {
                "Production monthly plan must capture player epochs before scanning."
            }
            assertTrue(plan.expectedInputs.isEmpty())
            assertEquals(plan.expectedPlayerCount, commitment.size)

            val revisionSamples = LongArray(BENCHMARK_REPETITIONS)
            repeat(BENCHMARK_REPETITIONS) { index ->
                startedAtNs = System.nanoTime()
                val currentRevision =
                    repository.currentMonthlyEvolutionRevisionSnapshotOrNull()
                revisionSamples[index] = elapsedMillis(startedAtNs)
                assertEquals(expectedRevision, currentRevision)
            }

            val fullValidationSamples = LongArray(BENCHMARK_REPETITIONS)
            repeat(BENCHMARK_REPETITIONS) { index ->
                startedAtNs = System.nanoTime()
                val validation = repository.validateMonthlyEvolutionUniverseCommitment(
                    expected = commitment,
                    expectedTrainingCenterLevels = plan.expectedTrainingCenterLevels,
                    currentTrainingCenterLevels =
                        teamsById.mapValues { it.value.trainingCenterLevel },
                    allowRosterCorrections = true
                )
                fullValidationSamples[index] = elapsedMillis(startedAtNs)
                assertTrue(validation.valid)
                assertTrue(validation.correctionIds.isEmpty())
                assertEquals(plan.expectedPlayerCount, validation.currentPlayerCount)
            }

            val resetSamples = LongArray(BENCHMARK_REPETITIONS)
            var resetRows = 0
            repeat(BENCHMARK_REPETITIONS) { index ->
                try {
                    repository.withTransaction {
                        startedAtNs = System.nanoTime()
                        resetRows = repository.resetMonthlyEvolutionCounters()
                        resetSamples[index] = elapsedMillis(startedAtNs)
                        throw ProbeRollback()
                    }
                } catch (_: ProbeRollback) {
                    // Roll back so every sample sees the same persisted state and epoch.
                }
            }

            val playerWriteSamples = LongArray(BENCHMARK_REPETITIONS)
            var playerWriteRows = 0
            repeat(BENCHMARK_REPETITIONS) { index ->
                try {
                    repository.withTransaction {
                        startedAtNs = System.nanoTime()
                        playerWriteRows =
                            repository.applyMonthlyEvolutionPlayerStateDeltas(
                                plan.updatedPlayerStates
                            )
                        playerWriteSamples[index] = elapsedMillis(startedAtNs)
                        throw ProbeRollback()
                    }
                } catch (_: ProbeRollback) {
                    // Roll back both Player rows and trigger-generated revision updates.
                }
                assertEquals(plan.updatedPlayerStates.size, playerWriteRows)
            }

            var historyWriteRows = 0
            var tHistoryWritesBulkMillis = 0L
            if (plan.historyLogs.isNotEmpty()) {
                try {
                    repository.withTransaction {
                        startedAtNs = System.nanoTime()
                        historyWriteRows =
                            repository.insertMonthlyEvolutionHistoryRowsBulk(plan.historyLogs)
                        tHistoryWritesBulkMillis = elapsedMillis(startedAtNs)
                        throw ProbeRollback()
                    }
                } catch (_: ProbeRollback) {
                    // Same canonical state is retained for fast-path commit samples.
                }
            }
            assertEquals(plan.historyLogs.size, historyWriteRows)

            val fastCommitSamples = LongArray(BENCHMARK_REPETITIONS)
            repeat(BENCHMARK_REPETITIONS) { index ->
                try {
                    repository.withTransaction {
                        startedAtNs = System.nanoTime()
                        val committed = useCase.commitMonthlyEvolution(
                            plan = plan,
                            allowWeeklyRosterCorrections = true
                        )
                        fastCommitSamples[index] = elapsedMillis(startedAtNs)
                        assertTrue(committed)
                        throw ProbeRollback()
                    }
                } catch (_: ProbeRollback) {
                    // Nested Room transaction is intentionally rolled back. This keeps the plan
                    // revision/history valid for the next identical measurement.
                }
                assertEquals(
                    expectedRevision,
                    repository.currentMonthlyEvolutionRevisionSnapshotOrNull()
                )
            }

            val tRevisionCheckMillis = revisionSamples.averageMillis()
            val tCommitmentValidationMillis = fullValidationSamples.averageMillis()
            val tResetCountersMillis = resetSamples.averageMillis()
            val tPlayerWritesMillis = playerWriteSamples.averageMillis()
            val tTotalFastPathCommitMillis = fastCommitSamples.averageMillis()

            println(
                "PERF_MONTHLY_COMMIT_STAGES " +
                    "PLAYER_COUNT=${marker.playerCount} " +
                    "SAMPLES=$BENCHMARK_REPETITIONS " +
                    "T_HISTORY_LOOKUP=$tHistoryLookupMillis " +
                    "T_TEAM_READ=$tTeamReadMillis " +
                    "T_REVISION_CHECK=$tRevisionCheckMillis " +
                    "T_COMMITMENT_VALIDATE_BASELINE=$tCommitmentValidationMillis " +
                    "T_TOTAL_FAST_PATH_COMMIT=$tTotalFastPathCommitMillis " +
                    "T_RESET_COUNTERS=$tResetCountersMillis " +
                    "T_PLAYER_WRITES=$tPlayerWritesMillis " +
                    "T_HISTORY_WRITES_BULK=$tHistoryWritesBulkMillis " +
                    "REVISION_CHECK_SAMPLES=${revisionSamples.joinToString(",")} " +
                    "COMMITMENT_VALIDATE_SAMPLES=${fullValidationSamples.joinToString(",")} " +
                    "PLAYER_WRITE_SAMPLES=${playerWriteSamples.joinToString(",")} " +
                    "FAST_PATH_COMMIT_SAMPLES=${fastCommitSamples.joinToString(",")} " +
                    "COMMITMENT_ROWS_COUNT=${commitment.size} " +
                    "RESET_ROWS_COUNT=$resetRows " +
                    "PLAYER_WRITES_COUNT=$playerWriteRows " +
                    "HISTORY_ROWS_COUNT=${plan.historyLogs.size} " +
                    "BULK_HISTORY_ROWS_COUNT=$historyWriteRows"
            )
        }

    private fun LongArray.averageMillis(): Long =
        if (isEmpty()) 0L else (sum().toDouble() / size.toDouble()).toLong()

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

    private companion object {
        const val BENCHMARK_REPETITIONS = 3
    }
}
