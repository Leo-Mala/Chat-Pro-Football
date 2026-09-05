package com.example.usecase

import android.app.Application
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Diagnostic counterpart of [MonthlyCommitPerformanceBenchmarkTest] executed against Android's
 * actual SQLite/Room stack. Every sample deletes and recreates the slot database before preparing
 * the monthly plan. Timings and logical cardinalities are recorded independently for each sample.
 *
 * The production monthly engine intentionally follows the existing unseeded kotlin.random.Random
 * call pattern, so changed-player/history cardinalities may vary between otherwise identical fresh
 * database samples. This diagnostic therefore validates structural persistence invariants rather
 * than incorrectly requiring stochastic outcomes to be bit-for-bit identical.
 */
@RunWith(AndroidJUnit4::class)
class MonthlyCommitPerformanceAndroidBenchmarkTest {
    private lateinit var application: Application

    private class ProbeRollback : RuntimeException()

    private data class Sample(
        val playerCount: Int,
        val changedPlayers: Int,
        val historyRows: Int,
        val resetRows: Int,
        val playerWriteRows: Int,
        val bulkHistoryRows: Int,
        val revisionCheckMillis: Long,
        val commitmentValidationMillis: Long,
        val resetCountersMillis: Long,
        val playerWritesMillis: Long,
        val historyWritesMillis: Long,
        val fastCommitMillis: Long
    )

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        clearSlot()
    }

    @Test
    fun canonicalMonthlyCommitMeasuresFreshAndroidSqliteSamples() = runBlocking {
        val samples = mutableListOf<Sample>()

        repeat(BENCHMARK_REPETITIONS) { sampleIndex ->
            clearSlot()
            val saveRepository = GameSaveRepository(application, SlotDatabaseFactory(application))
            try {
                val repository = saveRepository.getRepositoryForSlot(SLOT_ID)
                val marker = requireNotNull(repository.pristineCareerSeedTemplateOrNull()) {
                    "Android profiler must start from the canonical pristine career seed template."
                }
                assertTrue(marker.playerCount >= 60_000)

                val userTeam = repository.getAllTeams().minByOrNull { it.id }
                    ?: error("Canonical corpus must contain clubs.")
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

                val existingHistory = if (plan.historyLogs.isEmpty()) {
                    emptySet()
                } else {
                    repository.getMonthlyEvolutionHistoryFingerprints(plan.periodDate)
                }
                if (plan.historyLogs.isNotEmpty()) {
                    val plannedFingerprints =
                        plan.historyLogs.mapTo(hashSetOf()) { it.monthlyEvolutionFingerprint() }
                    assertTrue(plannedFingerprints.none { it in existingHistory })
                }

                val teamsById = repository.getAllTeams().associateBy { it.id }
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

                var startedAtNs = System.nanoTime()
                val currentRevision = repository.currentMonthlyEvolutionRevisionSnapshotOrNull()
                val revisionCheckMillis = elapsedMillis(startedAtNs)
                assertEquals(expectedRevision, currentRevision)

                startedAtNs = System.nanoTime()
                val validation = repository.validateMonthlyEvolutionUniverseCommitment(
                    expected = commitment,
                    expectedTrainingCenterLevels = plan.expectedTrainingCenterLevels,
                    currentTrainingCenterLevels = teamsById.mapValues { it.value.trainingCenterLevel },
                    allowRosterCorrections = true
                )
                val commitmentValidationMillis = elapsedMillis(startedAtNs)
                assertTrue(validation.valid)
                assertTrue(validation.correctionIds.isEmpty())
                assertEquals(plan.expectedPlayerCount, validation.currentPlayerCount)

                var resetRows = 0
                var resetCountersMillis = 0L
                try {
                    repository.withTransaction {
                        startedAtNs = System.nanoTime()
                        resetRows = repository.resetMonthlyEvolutionCounters()
                        resetCountersMillis = elapsedMillis(startedAtNs)
                        throw ProbeRollback()
                    }
                } catch (_: ProbeRollback) {
                    // Keep the exact pre-commit state for subsequent stage probes.
                }

                var playerWriteRows = 0
                var playerWritesMillis = 0L
                try {
                    repository.withTransaction {
                        startedAtNs = System.nanoTime()
                        playerWriteRows = repository.applyMonthlyEvolutionPlayerStateDeltas(
                            plan.updatedPlayerStates
                        )
                        playerWritesMillis = elapsedMillis(startedAtNs)
                        throw ProbeRollback()
                    }
                } catch (_: ProbeRollback) {
                    // Roll back Player rows and trigger-generated revision updates.
                }
                assertEquals(plan.updatedPlayerStates.size, playerWriteRows)

                var bulkHistoryRows = 0
                var historyWritesMillis = 0L
                if (plan.historyLogs.isNotEmpty()) {
                    try {
                        repository.withTransaction {
                            startedAtNs = System.nanoTime()
                            bulkHistoryRows = repository.insertMonthlyEvolutionHistoryRowsBulk(
                                plan.historyLogs
                            )
                            historyWritesMillis = elapsedMillis(startedAtNs)
                            throw ProbeRollback()
                        }
                    } catch (_: ProbeRollback) {
                        // Preserve the same state for the complete fast-path commit probe.
                    }
                }
                assertEquals(plan.historyLogs.size, bulkHistoryRows)

                var fastCommitMillis = 0L
                try {
                    repository.withTransaction {
                        startedAtNs = System.nanoTime()
                        val committed = useCase.commitMonthlyEvolution(
                            plan = plan,
                            allowWeeklyRosterCorrections = true
                        )
                        fastCommitMillis = elapsedMillis(startedAtNs)
                        assertTrue(committed)
                        throw ProbeRollback()
                    }
                } catch (_: ProbeRollback) {
                    // Keep this sample pristine until the database is closed and deleted.
                }
                assertEquals(
                    expectedRevision,
                    repository.currentMonthlyEvolutionRevisionSnapshotOrNull()
                )

                val sample = Sample(
                    playerCount = marker.playerCount,
                    changedPlayers = plan.updatedPlayerStates.size,
                    historyRows = plan.historyLogs.size,
                    resetRows = resetRows,
                    playerWriteRows = playerWriteRows,
                    bulkHistoryRows = bulkHistoryRows,
                    revisionCheckMillis = revisionCheckMillis,
                    commitmentValidationMillis = commitmentValidationMillis,
                    resetCountersMillis = resetCountersMillis,
                    playerWritesMillis = playerWritesMillis,
                    historyWritesMillis = historyWritesMillis,
                    fastCommitMillis = fastCommitMillis
                )
                samples += sample
                emitSample(sampleIndex + 1, sample)
            } finally {
                runCatching { saveRepository.closeAllDatabases() }
                clearSlot()
            }
        }

        val summary = buildString {
            append("PERF_ANDROID_MONTHLY_COMMIT_STAGES ")
            append("API=${android.os.Build.VERSION.SDK_INT} ")
            append("SAMPLES=$BENCHMARK_REPETITIONS ")
            append("PLAYER_COUNTS=${samples.joinToString(",") { it.playerCount.toString() }} ")
            append("CHANGED_PLAYER_COUNTS=${samples.joinToString(",") { it.changedPlayers.toString() }} ")
            append("HISTORY_ROW_COUNTS=${samples.joinToString(",") { it.historyRows.toString() }} ")
            append("RESET_ROW_COUNTS=${samples.joinToString(",") { it.resetRows.toString() }} ")
            append("PLAYER_WRITE_ROW_COUNTS=${samples.joinToString(",") { it.playerWriteRows.toString() }} ")
            append("BULK_HISTORY_ROW_COUNTS=${samples.joinToString(",") { it.bulkHistoryRows.toString() }} ")
            append("REVISION_CHECK_SAMPLES=${samples.joinToString(",") { it.revisionCheckMillis.toString() }} ")
            append("COMMITMENT_VALIDATE_SAMPLES=${samples.joinToString(",") { it.commitmentValidationMillis.toString() }} ")
            append("RESET_COUNTER_SAMPLES=${samples.joinToString(",") { it.resetCountersMillis.toString() }} ")
            append("PLAYER_WRITE_SAMPLES=${samples.joinToString(",") { it.playerWritesMillis.toString() }} ")
            append("HISTORY_WRITE_SAMPLES=${samples.joinToString(",") { it.historyWritesMillis.toString() }} ")
            append("FAST_COMMIT_SAMPLES=${samples.joinToString(",") { it.fastCommitMillis.toString() }}")
        }
        Log.i(LOG_TAG, summary)
        println(summary)

        assertEquals(BENCHMARK_REPETITIONS, samples.size)
        assertEquals(
            "canonical player count changed across fresh DB samples",
            1,
            samples.map { it.playerCount }.toSet().size
        )
        assertTrue(samples.all { it.changedPlayers in 1..it.playerCount })
        assertTrue(samples.all { it.historyRows >= 0 })
        assertTrue(samples.all { it.resetRows >= 0 })
        assertTrue(samples.all { it.playerWriteRows == it.changedPlayers })
        assertTrue(samples.all { it.bulkHistoryRows == it.historyRows })
    }

    private fun emitSample(index: Int, sample: Sample) {
        val line =
            "PERF_ANDROID_MONTHLY_SAMPLE index=$index " +
                "playerCount=${sample.playerCount} " +
                "changedPlayers=${sample.changedPlayers} " +
                "historyRows=${sample.historyRows} " +
                "resetRows=${sample.resetRows} " +
                "playerWriteRows=${sample.playerWriteRows} " +
                "bulkHistoryRows=${sample.bulkHistoryRows} " +
                "revisionCheckMs=${sample.revisionCheckMillis} " +
                "commitmentValidateMs=${sample.commitmentValidationMillis} " +
                "resetMs=${sample.resetCountersMillis} " +
                "playerWritesMs=${sample.playerWritesMillis} " +
                "historyWritesMs=${sample.historyWritesMillis} " +
                "fastCommitMs=${sample.fastCommitMillis}"
        Log.i(LOG_TAG, line)
        println(line)
    }

    private fun elapsedMillis(startedAtNs: Long): Long =
        (System.nanoTime() - startedAtNs) / 1_000_000L

    private fun clearSlot() {
        val name = SlotDatabaseFactory.databaseNameForSlot(SLOT_ID)
        application.deleteDatabase(name)
        val file = application.getDatabasePath(name)
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            java.io.File(file.path + suffix).delete()
        }
    }

    private companion object {
        const val SLOT_ID = "6"
        const val BENCHMARK_REPETITIONS = 3
        const val LOG_TAG = "MonthlyCommitBenchmark"
    }
}
