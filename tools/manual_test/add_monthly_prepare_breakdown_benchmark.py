from pathlib import Path

path = Path("app/src/test/java/com/example/usecase/MonthlyPrepareBreakdownBenchmarkTest.kt")
path.write_text(r'''package com.example.usecase

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.HistoricoEvolucao
import com.example.data.MonthlyEvolutionInputSnapshot
import com.example.data.Player
import com.example.data.PlayerEvolutionMonthlyEngine
import com.example.data.PlayerEvolutionResult
import com.example.data.getMonthlyEvolutionPlayerCount
import com.example.data.local.SlotDatabaseFactory
import com.example.data.pristineCareerSeedTemplateOrNull
import com.example.data.repository.GameSaveRepository
import com.example.data.toMonthlyEvolutionInputSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonthlyPrepareBreakdownBenchmarkTest {
    private fun elapsedMillis(start: Long): Long = (System.nanoTime() - start) / 1_000_000L

    @Test
    fun `profile canonical monthly preparation stages`() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val slotId = "4"
        val dbName = SlotDatabaseFactory.databaseNameForSlot(slotId)
        application.deleteDatabase(dbName)
        val saveRepository = GameSaveRepository(application, SlotDatabaseFactory(application))
        val repository = saveRepository.getRepositoryForSlot(slotId)
        try {
            val marker = requireNotNull(repository.pristineCareerSeedTemplateOrNull())
            assertTrue(marker.playerCount >= 60_000)
            val expectedPlayerCount = repository.getMonthlyEvolutionPlayerCount()
            assertEquals(marker.playerCount, expectedPlayerCount)

            val teamsStart = System.nanoTime()
            val allTeams = repository.getAllTeams().associateBy { it.id }
            val teamsMillis = elapsedMillis(teamsStart)

            val legacyIds = ArrayList<Long>(expectedPlayerCount)
            var legacyOffsetReadMillis = 0L
            var legacyOffset = 0
            while (legacyOffset < expectedPlayerCount) {
                val stageStart = System.nanoTime()
                val batch = repository.getAllPlayersBatch(4096, legacyOffset)
                legacyOffsetReadMillis += elapsedMillis(stageStart)
                check(batch.isNotEmpty())
                for (player in batch) legacyIds.add(player.id)
                legacyOffset += batch.size
            }
            assertEquals(expectedPlayerCount, legacyIds.size)

            val oneShotReadStart = System.nanoTime()
            val allPlayers = repository.getAllPlayers()
            val oneShotReadMillis = elapsedMillis(oneShotReadStart)
            assertEquals(expectedPlayerCount, allPlayers.size)
            assertEquals(
                "Single ordered read must preserve the exact legacy LIMIT/OFFSET player sequence",
                legacyIds,
                allPlayers.map { it.id }
            )

            val expectedInputs = ArrayList<MonthlyEvolutionInputSnapshot>(expectedPlayerCount)
            val changedPlayers = ArrayList<Player>()
            val historyLogs = ArrayList<HistoricoEvolucao>()
            var calcMillis = 0L
            var collectMillis = 0L
            var snapshotMillis = 0L
            var offset = 0

            while (offset < allPlayers.size) {
                val endExclusive = minOf(offset + 4096, allPlayers.size)
                val batch = allPlayers.subList(offset, endExclusive)

                var stageStart = System.nanoTime()
                val batchResults: List<PlayerEvolutionResult> = PlayerEvolutionMonthlyEngine.processChanged(
                    batch,
                    allTeams,
                    "S2026_W8"
                )
                calcMillis += elapsedMillis(stageStart)

                stageStart = System.nanoTime()
                for (result in batchResults) {
                    if (result.historyLogs.isNotEmpty() || result.netChange != 0.0) changedPlayers.add(result.player)
                    if (result.historyLogs.isNotEmpty()) historyLogs.addAll(result.historyLogs)
                }
                collectMillis += elapsedMillis(stageStart)

                stageStart = System.nanoTime()
                for (player in batch) expectedInputs.add(player.toMonthlyEvolutionInputSnapshot())
                snapshotMillis += elapsedMillis(stageStart)
                offset = endExclusive
            }

            assertEquals(expectedPlayerCount, expectedInputs.size)
            println(
                "PERF_MONTHLY_PREPARE_BREAKDOWN " +
                    "T_TEAMS_READ=$teamsMillis " +
                    "T_PLAYER_READ_LEGACY_OFFSET=$legacyOffsetReadMillis " +
                    "T_PLAYER_READ_ONESHOT=$oneShotReadMillis " +
                    "T_ENGINE_CALC=$calcMillis " +
                    "T_RESULT_COLLECT=$collectMillis " +
                    "T_SNAPSHOT_CAPTURE=$snapshotMillis " +
                    "MONTHLY_PLAYERS_COUNT=$expectedPlayerCount " +
                    "PLAYERS_UPDATED_COUNT=${changedPlayers.size} " +
                    "HISTORY_ROWS=${historyLogs.size}"
            )
        } finally {
            runCatching { saveRepository.closeAllDatabases() }
            application.deleteDatabase(dbName)
            val file = application.getDatabasePath(dbName)
            listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
                java.io.File(file.path + suffix).delete()
            }
        }
    }
}
''', encoding="utf-8")
