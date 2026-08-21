package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.usecase.PlayerEvolutionUseCase
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase 10.1 reproducible ~60k-player performance gate.
 *
 * Runtime measurements vary across hosted runners, so the gate uses deliberately tolerant
 * regression ceilings derived from the recorded pre-10.1 baseline rather than pretending that a
 * volatile wall-clock value is a strict product contract. The ceilings only reject material
 * regressions (for example, a ~22s monthly path turning into several minutes or runaway heap).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlobalMainAuditPerformanceStressTest {

    companion object {
        private const val BASELINE_INITIAL_PERSISTENCE_MS = 6_763L
        private const val BASELINE_FULL_RELOAD_MS = 15_727L
        private const val BASELINE_MONTHLY_EVOLUTION_MS = 21_851L
        private const val BASELINE_PEAK_HEAP_BYTES = 435_997_464L

        private const val TIME_REGRESSION_TOLERANCE = 3L
        private const val HEAP_REGRESSION_NUMERATOR = 7L
        private const val HEAP_REGRESSION_DENOMINATOR = 4L
    }

    @Test
    fun `measure seed persistence reload and monthly evolution at 60k scale`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metrics = linkedMapOf<String, Any?>()
        val heap = linkedMapOf<String, Long>()
        heap["beforeDataset"] = usedHeapBytes()

        val datasetStart = System.nanoTime()
        val dataset = requireNotNull(Fc26NormalizedDatasetLoader.loadValidatedOrNull(context.assets))
        val datasetLoadMillis = elapsedMillis(datasetStart)
        assertEquals(18_405, dataset.players.size)

        val teamsStart = System.nanoTime()
        val teams = buildUniverse()
        val teamUniverseBuildMillis = elapsedMillis(teamsStart)
        assertTrue(teams.isNotEmpty())
        assertEquals(teams.size, teams.map { it.id }.distinct().size)

        val seedStart = System.nanoTime()
        val seed = Fc26SeedPlanner.build(
            teams = teams,
            dataset = dataset,
            proceduralRosterFactory = { team ->
                DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
            }
        )
        val seedPlanningMillis = elapsedMillis(seedStart)
        assertEquals(18_405, seed.report.bulkImportedFc26Players)
        assertTrue("Expected the production universe to exercise ~60k-player scale", seed.players.size >= 60_000)
        assertEquals(seed.players.size, seed.players.map { it.id }.distinct().size)
        heap["afterSeedPlan"] = usedHeapBytes()

        val playerCounts = seed.players.filter { it.teamId != null }.groupingBy { it.teamId }.eachCount()
        val userTeam = teams.first { playerCounts[it.id] != null }
        val dbName = "phase_10_1_performance_60k.db"
        context.deleteDatabase(dbName)
        var db: AppDatabase? = null
        var reopened: AppDatabase? = null

        try {
            db = AppDatabase.getDatabaseWithName(context, dbName)
            val repository = GameRepository(db)
            val save = GameSave(
                coachName = "Phase 10.1",
                currentSeason = 2026,
                currentWeek = 4,
                playerTeamId = userTeam.id,
                bankBalance = 5_000_000_000L
            )

            val persistStart = System.nanoTime()
            repository.runInTransaction {
                repository.saveTeams(teams)
                repository.savePlayers(seed.players)
                repository.saveGameSave(save)
            }
            val initialPersistenceMillis = elapsedMillis(persistStart)
            heap["afterPersistence"] = usedHeapBytes()

            db.close()
            db = null
            val databaseBytes = databaseFootprintBytes(context, dbName)

            val reopenStart = System.nanoTime()
            reopened = AppDatabase.getDatabaseWithName(context, dbName)
            val reopenedRepository = GameRepository(reopened)
            val reloaded = reopenedRepository.getAllPlayers()
            val reopenAndFullPlayerReloadMillis = elapsedMillis(reopenStart)
            assertEquals(seed.players.size, reloaded.size)
            heap["afterReload"] = usedHeapBytes()

            val evolutionStart = System.nanoTime()
            val evolution = PlayerEvolutionUseCase(reopenedRepository).executeMonthlyEvolution(
                save = requireNotNull(reopenedRepository.getGameSave()),
                periodDate = "2026-01"
            )
            val monthlyEvolutionMillis = elapsedMillis(evolutionStart)
            assertEquals(reloaded.size, evolution.size)
            heap["afterEvolution"] = usedHeapBytes()
            val observedPeakHeap = heap.values.maxOrNull() ?: 0L

            assertTrue(
                "Initial persistence materially regressed: ${initialPersistenceMillis}ms",
                initialPersistenceMillis <= BASELINE_INITIAL_PERSISTENCE_MS * TIME_REGRESSION_TOLERANCE
            )
            assertTrue(
                "Full reload materially regressed: ${reopenAndFullPlayerReloadMillis}ms",
                reopenAndFullPlayerReloadMillis <= BASELINE_FULL_RELOAD_MS * TIME_REGRESSION_TOLERANCE
            )
            assertTrue(
                "Monthly evolution materially regressed: ${monthlyEvolutionMillis}ms",
                monthlyEvolutionMillis <= BASELINE_MONTHLY_EVOLUTION_MS * TIME_REGRESSION_TOLERANCE
            )
            assertTrue(
                "Observed heap materially regressed: $observedPeakHeap bytes",
                observedPeakHeap <=
                    BASELINE_PEAK_HEAP_BYTES * HEAP_REGRESSION_NUMERATOR / HEAP_REGRESSION_DENOMINATOR
            )

            val auditHead = System.getenv("AUDIT_HEAD_SHA")?.takeIf { it.isNotBlank() } ?: "local"
            if (System.getenv("GITHUB_ACTIONS").equals("true", ignoreCase = true)) {
                assertTrue(
                    "CI performance artifact must identify the immutable PR head SHA",
                    auditHead.matches(Regex("[0-9a-f]{40}"))
                )
            }

            metrics["auditHead"] = auditHead
            metrics["environment"] = "Robolectric sdk34 / GitHub Actions JVM"
            metrics["datasetPlayers"] = dataset.players.size
            metrics["persistedPlayers"] = reloaded.size
            metrics["targetTeams"] = teams.size
            metrics["fallbackPlayers"] = seed.report.fallbackPlayersGenerated
            metrics["datasetLoadMillis"] = datasetLoadMillis
            metrics["teamUniverseBuildMillis"] = teamUniverseBuildMillis
            metrics["seedPlanningMillis"] = seedPlanningMillis
            metrics["initialPersistenceMillis"] = initialPersistenceMillis
            metrics["reopenAndFullPlayerReloadMillis"] = reopenAndFullPlayerReloadMillis
            metrics["monthlyEvolutionMillis"] = monthlyEvolutionMillis
            metrics["databaseBytes"] = databaseBytes
            metrics["heapCheckpointBytes"] = heap
            metrics["observedCheckpointPeakHeapBytes"] = observedPeakHeap
            metrics["regressionBudget"] = mapOf(
                "initialPersistenceMillis" to BASELINE_INITIAL_PERSISTENCE_MS * TIME_REGRESSION_TOLERANCE,
                "fullReloadMillis" to BASELINE_FULL_RELOAD_MS * TIME_REGRESSION_TOLERANCE,
                "monthlyEvolutionMillis" to BASELINE_MONTHLY_EVOLUTION_MS * TIME_REGRESSION_TOLERANCE,
                "peakHeapBytes" to
                    BASELINE_PEAK_HEAP_BYTES * HEAP_REGRESSION_NUMERATOR / HEAP_REGRESSION_DENOMINATOR
            )

            val output = File(findRepositoryRoot(), "reports/global_main_audit_performance.json")
            output.parentFile.mkdirs()
            output.writeText(GsonBuilder().setPrettyPrinting().create().toJson(metrics) + "\n")

            println(
                "PHASE_10_1_60K head=$auditHead players=${reloaded.size} seedMs=$seedPlanningMillis " +
                    "persistMs=$initialPersistenceMillis reloadMs=$reopenAndFullPlayerReloadMillis " +
                    "evolutionMs=$monthlyEvolutionMillis peakHeap=$observedPeakHeap dbBytes=$databaseBytes"
            )
        } finally {
            runCatching { db?.close() }
            runCatching { reopened?.close() }
            context.deleteDatabase(dbName)
        }
    }

    private fun buildUniverse(): List<Team> = buildList {
        for (countryKey in GlobalFootballSystem.keys) {
            for (template in DefaultData.getTeamsForCountry(countryKey)) {
                add(
                    Team(
                        id = GlobalFootballSystem.getGlobalId(countryKey, template.name),
                        name = template.name,
                        city = template.city,
                        state = template.state,
                        country = countryKey,
                        division = template.division,
                        rating = template.rating,
                        stadiumName = template.stadium
                    )
                )
            }
        }
    }

    private fun findRepositoryRoot(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile ?: return@repeat
        }
        error("Could not locate repository root")
    }

    private fun databaseFootprintBytes(context: Context, dbName: String): Long {
        val file = context.getDatabasePath(dbName)
        return listOf(file, File(file.path + "-wal"), File(file.path + "-shm"))
            .filter { it.exists() }
            .sumOf { it.length() }
    }

    private fun usedHeapBytes(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    private fun elapsedMillis(startedNanos: Long): Long =
        (System.nanoTime() - startedNanos) / 1_000_000L
}
