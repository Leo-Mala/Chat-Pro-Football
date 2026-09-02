package com.example.usecase

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.GameSave
import com.example.data.HistoricoEvolucao
import com.example.data.MonthlyEvolutionInputSnapshot
import com.example.data.Player
import com.example.data.PlayerEvolutionMonthlyEngine
import com.example.data.PlayerEvolutionResult
import com.example.data.consumePristineCareerSeedTemplate
import com.example.data.forEachMonthlyEvolutionPlayerBatch
import com.example.data.getMonthlyEvolutionPlayerCount
import com.example.data.getMonthlyEvolutionPlayersBatch
import com.example.data.local.SlotDatabaseFactory
import com.example.data.pristineCareerSeedTemplateOrNull
import com.example.data.repository.GameSaveRepository
import com.example.data.toMonthlyEvolutionInputSnapshot
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
class MonthlyPreparePerformanceBenchmarkTest {
    private lateinit var application: Application
    private lateinit var saveRepository: GameSaveRepository
    private val slotId = "7"

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
    fun `single cursor preserves exact legacy paged player order`() = runBlocking {
        val repository = saveRepository.getRepositoryForSlot(slotId)
        val marker = requireNotNull(repository.pristineCareerSeedTemplateOrNull()) {
            "Order regression precisa do career_seed_template.db canônico e intocado."
        }
        assertTrue(marker.playerCount >= 60_000)

        val legacyIds = ArrayList<Long>(marker.playerCount)
        var offset = 0
        while (offset < marker.playerCount) {
            val batch = repository.getMonthlyEvolutionPlayersBatch(BATCH_SIZE, offset)
            check(batch.isNotEmpty()) {
                "Legacy monthly scan ended at $offset of ${marker.playerCount} rows."
            }
            batch.mapTo(legacyIds) { it.id }
            offset += batch.size
        }

        val streamingIds = ArrayList<Long>(marker.playerCount)
        val processed = repository.forEachMonthlyEvolutionPlayerBatch(BATCH_SIZE) { batch ->
            batch.mapTo(streamingIds) { it.id }
        }

        assertEquals(marker.playerCount, processed)
        assertEquals(legacyIds, streamingIds)
    }

    @Test
    fun `canonical monthly prepare profiles read snapshot engine and collection stages`() = runBlocking {
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

        val totalStartedAtNs = System.nanoTime()

        var startedAtNs = System.nanoTime()
        val expectedPlayerCount = repository.getMonthlyEvolutionPlayerCount()
        val tCountReadMillis = elapsedMillis(startedAtNs)
        assertEquals(marker.playerCount, expectedPlayerCount)

        startedAtNs = System.nanoTime()
        val allTeams = repository.getAllTeams().associateBy { it.id }
        val tTeamReadMillis = elapsedMillis(startedAtNs)

        val evolutionResults = ArrayList<PlayerEvolutionResult>(minOf(expectedPlayerCount, 4096))
        val changedPlayers = ArrayList<Player>()
        val historyLogs = ArrayList<HistoricoEvolucao>()
        val expectedInputs = ArrayList<MonthlyEvolutionInputSnapshot>(expectedPlayerCount)
        val referencedTeamIds = HashSet<Long>()

        var playerReadNanos = 0L
        var tEngineMillis = 0L
        var tResultCollectMillis = 0L
        var tSnapshotBuildMillis = 0L
        var batchCount = 0

        val processed = repository.forEachMonthlyEvolutionPlayerBatch(
            batchSize = BATCH_SIZE,
            onBatchReadNanos = { playerReadNanos += it }
        ) { batch ->
            startedAtNs = System.nanoTime()
            val batchResults = PlayerEvolutionMonthlyEngine.processChanged(batch, allTeams, PERIOD_DATE)
            tEngineMillis += elapsedMillis(startedAtNs)

            startedAtNs = System.nanoTime()
            evolutionResults.addAll(batchResults)
            for (result in batchResults) {
                if (result.historyLogs.isNotEmpty() || result.netChange != 0.0) {
                    changedPlayers.add(result.player)
                }
                if (result.historyLogs.isNotEmpty()) historyLogs.addAll(result.historyLogs)
            }
            tResultCollectMillis += elapsedMillis(startedAtNs)

            startedAtNs = System.nanoTime()
            for (player in batch) {
                expectedInputs.add(player.toMonthlyEvolutionInputSnapshot())
                player.teamId?.let(referencedTeamIds::add)
            }
            tSnapshotBuildMillis += elapsedMillis(startedAtNs)
            batchCount++
        }
        val tPlayerReadMillis = playerReadNanos / 1_000_000L

        startedAtNs = System.nanoTime()
        val expectedTrainingLevels = referencedTeamIds.associateWith { teamId ->
            allTeams[teamId]?.trainingCenterLevel ?: 1
        }
        val tTrainingMapMillis = elapsedMillis(startedAtNs)
        val tTotalPrepareMillis = elapsedMillis(totalStartedAtNs)

        assertEquals(expectedPlayerCount, expectedInputs.size)
        assertEquals(expectedPlayerCount, processed)
        assertTrue(batchCount > 1)
        assertTrue(changedPlayers.isNotEmpty())
        assertTrue(historyLogs.isNotEmpty())
        assertTrue(expectedTrainingLevels.isNotEmpty())

        println(
            "PERF_MONTHLY_PREPARE_STAGES " +
                "T_COUNT_READ=$tCountReadMillis " +
                "T_TEAM_READ=$tTeamReadMillis " +
                "T_PLAYER_READ=$tPlayerReadMillis " +
                "T_ENGINE=$tEngineMillis " +
                "T_RESULT_COLLECT=$tResultCollectMillis " +
                "T_SNAPSHOT_BUILD=$tSnapshotBuildMillis " +
                "T_TRAINING_MAP=$tTrainingMapMillis " +
                "T_TOTAL_PREPARE=$tTotalPrepareMillis " +
                "MONTHLY_PLAYERS_COUNT=$expectedPlayerCount " +
                "BATCH_COUNT=$batchCount " +
                "PLAYERS_UPDATED_COUNT=${changedPlayers.size} " +
                "HISTORY_ROWS_COUNT=${historyLogs.size}"
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

    private companion object {
        const val BATCH_SIZE = 4096
        const val PERIOD_DATE = "S2026_W8"
    }
}
