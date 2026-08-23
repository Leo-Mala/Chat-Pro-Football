package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.usecase.DatabaseIntegrityUseCase
import com.example.usecase.GenerateCalendarUseCase
import com.example.usecase.SeasonTransitionUseCase
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Phase 10.8 full-scale season rollover profiler.
 *
 * The first execution on the Phase 10.8 branch intentionally measures the pre-optimization path.
 * The exact-head JSON artifact is retained as the immutable baseline before any production
 * optimization is applied. The same harness is then reused for the candidate implementation.
 *
 * The StressTest suffix is intentional. The repository's consolidated fast CI excludes long stress
 * tests, while this profiler has its own mandatory exact-head workflow. This prevents the expensive
 * full-scale run from being duplicated by a generic unit-test task that does not provide audit SHA.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase108FullScaleSeasonRolloverPerformanceStressTest {

    @Test
    fun `profile full season rollover on real 60885 player file backed room`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val auditHead = System.getenv("AUDIT_HEAD_SHA")?.takeIf { it.isNotBlank() } ?: "local"
        if (System.getenv("GITHUB_ACTIONS").equals("true", ignoreCase = true)) {
            assertTrue(
                "CI performance artifact must identify the immutable PR head SHA",
                auditHead.matches(Regex("[0-9a-f]{40}"))
            )
        }

        val dataset = requireNotNull(Fc26NormalizedDatasetLoader.loadValidatedOrNull(context.assets))
        assertEquals(18_405, dataset.players.size)

        val teams = buildUniverse()
        assertTrue("Expected full club universe", teams.size >= 2_500)
        assertEquals(0, teams.size - teams.map { it.id }.distinct().size)

        val plan = Fc26SeedPlanner.build(
            teams = teams,
            dataset = dataset,
            proceduralRosterFactory = { team ->
                DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
            }
        )
        assertEquals(18_405, plan.report.datasetPlayers)
        assertEquals(18_405, plan.report.bulkImportedFc26Players)
        assertEquals(60_885, plan.players.size)
        assertEquals(1_325, plan.report.datasetLoanPlayers)
        assertEquals(816, plan.report.resolvedLoans)
        assertEquals(509, plan.report.rejectedLoans)
        assertEquals(448, plan.report.borrowerNotFound)
        assertEquals(60, plan.report.ownerNotFound)
        assertEquals(1, plan.report.ambiguousLoans)
        assertEquals(0, plan.players.size - plan.players.map { it.id }.distinct().size)

        val sourceById = dataset.players.associateBy { it.stableId }
        val seededRealPlayers = plan.players.filter { StableRealPlayerIdentity.isRealPlayerId(it.id) }
        assertEquals(18_405, seededRealPlayers.size)
        var overallMutated = 0
        var potentialMutated = 0
        var attributesMutated = 0
        seededRealPlayers.forEach { player ->
            val source = requireNotNull(sourceById[player.id])
            if (player.force != source.overall) overallMutated++
            if (player.potential != source.potential) potentialMutated++
            if (player.atributos != source.atributos) attributesMutated++
        }
        assertEquals(0, overallMutated)
        assertEquals(0, potentialMutated)
        assertEquals(0, attributesMutated)

        val dbName = "phase_10_8_full_scale_rollover.db"
        context.deleteDatabase(dbName)
        val queryRecorder = QueryRecorder()
        val directExecutor = Executor { command -> command.run() }
        var database: AppDatabase? = null
        var reopened: AppDatabase? = null
        val samplerRunning = AtomicBoolean(false)
        val maxWalBytes = AtomicLong(0L)
        val maxHeapBytes = AtomicLong(0L)
        var sampler: Thread? = null

        try {
            database = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                dbName
            )
                .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .setQueryCallback(queryRecorder, directExecutor)
                .build()
            database.openHelper.writableDatabase
            val repository = GameRepository(database)
            val userTeam = teams.firstOrNull { it.country.equals("Brasil", ignoreCase = true) }
                ?: teams.first()
            val save = GameSave(
                coachName = "Phase 10.8",
                currentSeason = 2026,
                currentWeek = GameCalendar.WEEKS_PER_SEASON,
                playerTeamId = userTeam.id,
                bankBalance = 5_000_000_000L
            )

            repository.runInTransaction {
                repository.saveTeams(teams)
                repository.savePlayers(plan.players)
                repository.saveLoans(plan.loans)
                repository.saveGameSave(save)
            }

            val calendarUseCase = GenerateCalendarUseCase(repository)
            val currentFixtures = calendarUseCase.generateSeasonFixtures(
                season = save.currentSeason,
                teams = teams,
                userTeamId = save.playerTeamId,
                userCountry = userTeam.country
            )
            repository.saveFixtures(currentFixtures)
            val persistedCurrentFixtures = repository.getFixturesForSeason(save.currentSeason)
            val completedFixtures = persistedCurrentFixtures.map { fixture ->
                val homeGoals = ((fixture.homeTeamId + fixture.week + fixture.id) and 3L).toInt()
                val awayGoals = ((fixture.awayTeamId + fixture.week + fixture.id) and 3L).toInt()
                fixture.copy(
                    isPlayed = true,
                    homeScore = homeGoals,
                    awayScore = awayGoals
                )
            }
            repository.updateFixtures(completedFixtures)

            val dbFile = context.getDatabasePath(dbName)
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")
            val initialDbBytes = dbFile.length()
            val initialWalBytes = walFile.takeIf { it.exists() }?.length() ?: 0L
            val initialShmBytes = shmFile.takeIf { it.exists() }?.length() ?: 0L
            val heapBefore = usedHeapBytes()
            val gcBefore = gcCollectionCountOrNull()
            maxHeapBytes.set(heapBefore)
            maxWalBytes.set(initialWalBytes)

            queryRecorder.resetAndEnable()
            samplerRunning.set(true)
            sampler = Thread({
                while (samplerRunning.get()) {
                    maxWalBytes.accumulateAndGet(
                        walFile.takeIf { it.exists() }?.length() ?: 0L
                    ) { current, observed -> maxOf(current, observed) }
                    maxHeapBytes.accumulateAndGet(usedHeapBytes()) { current, observed ->
                        maxOf(current, observed)
                    }
                    try {
                        Thread.sleep(5L)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return@Thread
                    }
                }
            }, "phase-10-8-wal-heap-sampler").also { it.start() }

            val rolloverStarted = System.nanoTime()
            val updatedSave = SeasonTransitionUseCase(
                repository = repository,
                generateCalendarUseCase = calendarUseCase,
                databaseIntegrityUseCase = DatabaseIntegrityUseCase(repository)
            ).advanceToNextSeason(save)
            val rolloverMillis = elapsedMillis(rolloverStarted)

            samplerRunning.set(false)
            sampler?.join(5_000L)
            queryRecorder.disable()

            assertEquals(2027, updatedSave.currentSeason)
            assertEquals(1, updatedSave.currentWeek)
            val persistedPlayersAfterRollover = repository.getAllPlayers()
            assertEquals(60_885, persistedPlayersAfterRollover.size)
            assertEquals(60_885, persistedPlayersAfterRollover.map { it.id }.distinct().size)
            assertTrue(repository.getFixturesForSeason(2027).isNotEmpty())

            val heapAfter = usedHeapBytes()
            maxHeapBytes.accumulateAndGet(heapAfter) { current, observed -> maxOf(current, observed) }
            val gcAfter = gcCollectionCountOrNull()
            val finalDbBytes = dbFile.length()
            val finalWalBytes = walFile.takeIf { it.exists() }?.length() ?: 0L
            val finalShmBytes = shmFile.takeIf { it.exists() }?.length() ?: 0L
            val checkpoint = walCheckpoint(database)

            database.close()
            database = null

            val reopenStarted = System.nanoTime()
            reopened = AppDatabase.getDatabaseWithName(context, dbName)
            val reopenedRepository = GameRepository(reopened)
            val reopenedSave = reopenedRepository.getGameSave()
            val reopenedPlayers = reopenedRepository.getAllPlayers()
            val reopenMillis = elapsedMillis(reopenStarted)
            assertNotNull(reopenedSave)
            assertEquals(2027, reopenedSave!!.currentSeason)
            assertEquals(60_885, reopenedPlayers.size)
            assertTrue(reopenedRepository.getFixturesForSeason(2027).isNotEmpty())

            val querySummary = queryRecorder.snapshot()
            val activeLoanLookupCount = querySummary.normalizedCounts
                .filterKeys { sql ->
                    sql.contains("from player_loans") &&
                        sql.contains("playerid") &&
                        sql.contains("status = 'active'")
                }
                .values
                .sum()
            val teamUpdateCount = querySummary.normalizedCounts
                .filterKeys { sql -> sql.startsWith("update") && sql.contains("teams") }
                .values
                .sum()

            assertTrue(
                "Rollover exceeded conservative anti-runaway ceiling: ${rolloverMillis}ms",
                rolloverMillis <= 180_000L
            )
            assertTrue(
                "Observed heap exceeded conservative 60k anti-runaway envelope",
                maxHeapBytes.get() <= 900_000_000L
            )

            val observedGcCollections = if (gcBefore != null && gcAfter != null) {
                (gcAfter - gcBefore).coerceAtLeast(0L)
            } else {
                null
            }
            val report = linkedMapOf<String, Any?>(
                "phase" to "10.8",
                "measurementKind" to "pre_optimization_baseline",
                "auditHead" to auditHead,
                "environment" to mapOf(
                    "runtime" to "Robolectric sdk34 / GitHub Actions JVM",
                    "room" to "2.7.0",
                    "roomSchema" to APP_DATABASE_SCHEMA_VERSION,
                    "storage" to "file-backed SQLite WAL"
                ),
                "dataset" to mapOf(
                    "datasetPlayers" to plan.report.datasetPlayers,
                    "validatedPlayers" to dataset.players.size,
                    "processedPlayers" to plan.report.bulkImportedFc26Players,
                    "importedPlayers" to plan.report.bulkImportedFc26Players,
                    "notImported" to (plan.report.datasetPlayers - plan.report.bulkImportedFc26Players),
                    "persistedPlayersIncludingFallback" to plan.players.size,
                    "clubs" to teams.size,
                    "datasetLoanPlayers" to plan.report.datasetLoanPlayers,
                    "resolvedLoans" to plan.report.resolvedLoans,
                    "rejectedLoans" to plan.report.rejectedLoans,
                    "borrowerNotFound" to plan.report.borrowerNotFound,
                    "ownerNotFound" to plan.report.ownerNotFound,
                    "ambiguousLoans" to plan.report.ambiguousLoans,
                    "duplicatePlayerIds" to (plan.players.size - plan.players.map { it.id }.distinct().size),
                    "duplicateTeamIds" to (teams.size - teams.map { it.id }.distinct().size),
                    "overallMutated" to overallMutated,
                    "potentialMutated" to potentialMutated,
                    "attributesMutated" to attributesMutated
                ),
                "timingMillis" to mapOf(
                    "seasonRolloverTotal" to rolloverMillis,
                    "postRolloverReopenAndFullPlayerReload" to reopenMillis
                ),
                "queries" to mapOf(
                    "total" to querySummary.total,
                    "select" to querySummary.select,
                    "insert" to querySummary.insert,
                    "update" to querySummary.update,
                    "delete" to querySummary.delete,
                    "activeLoanLookupPerPlayer" to activeLoanLookupCount,
                    "teamUpdateStatements" to teamUpdateCount,
                    "byTable" to querySummary.byTable,
                    "topNormalizedStatements" to querySummary.normalizedCounts.entries
                        .sortedByDescending { it.value }
                        .take(20)
                        .associate { it.key to it.value }
                ),
                "memory" to mapOf(
                    "heapBeforeBytes" to heapBefore,
                    "heapPeakObservedBytes" to maxHeapBytes.get(),
                    "heapAfterBytes" to heapAfter,
                    "heapDeltaBytes" to (heapAfter - heapBefore),
                    "gcCollectionsObserved" to observedGcCollections
                ),
                "sqlite" to mapOf(
                    "dbInitialBytes" to initialDbBytes,
                    "dbFinalBytes" to finalDbBytes,
                    "walInitialBytes" to initialWalBytes,
                    "walPeakObservedBytes" to maxWalBytes.get(),
                    "walFinalBytes" to finalWalBytes,
                    "shmInitialBytes" to initialShmBytes,
                    "shmFinalBytes" to finalShmBytes,
                    "walCheckpointPassive" to checkpoint,
                    "outerRolloverTransactions" to 1,
                    "longestTransactionMillis" to rolloverMillis,
                    "totalTransactionMillis" to rolloverMillis
                ),
                "fixtures" to mapOf(
                    "completedPriorSeasonFixtures" to completedFixtures.size,
                    "newSeasonFixtures" to reopenedRepository.getFixturesForSeason(2027).size
                ),
                "existingBudgetsPreserved" to mapOf(
                    "phase101InitialPersistenceMillis" to 20_289,
                    "phase101FullReloadMillis" to 47_181,
                    "phase101MonthlyEvolutionMillis" to 65_553,
                    "phase101PeakHeapBytes" to 762_995_562
                )
            )

            val output = File(findRepositoryRoot(), "reports/phase_10_8_full_scale_rollover.json")
            output.parentFile.mkdirs()
            output.writeText(GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n")

            println(
                "PHASE_10_8_BASELINE head=$auditHead players=${plan.players.size} teams=${teams.size} " +
                    "rolloverMs=$rolloverMillis queries=${querySummary.total} loanLookups=$activeLoanLookupCount " +
                    "teamUpdates=$teamUpdateCount peakHeap=${maxHeapBytes.get()} peakWal=${maxWalBytes.get()}"
            )
        } finally {
            samplerRunning.set(false)
            runCatching { sampler?.interrupt() }
            runCatching { sampler?.join(1_000L) }
            runCatching { database?.close() }
            runCatching { reopened?.close() }
            context.deleteDatabase(dbName)
        }
    }

    private data class QuerySummary(
        val total: Int,
        val select: Int,
        val insert: Int,
        val update: Int,
        val delete: Int,
        val byTable: Map<String, Int>,
        val normalizedCounts: Map<String, Int>
    )

    private class QueryRecorder : RoomDatabase.QueryCallback {
        private var enabled = false
        private var total = 0
        private var select = 0
        private var insert = 0
        private var update = 0
        private var delete = 0
        private val byTable = linkedMapOf<String, Int>()
        private val normalizedCounts = linkedMapOf<String, Int>()

        @Synchronized
        fun resetAndEnable() {
            total = 0
            select = 0
            insert = 0
            update = 0
            delete = 0
            byTable.clear()
            normalizedCounts.clear()
            enabled = true
        }

        @Synchronized
        fun disable() {
            enabled = false
        }

        @Synchronized
        override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
            if (!enabled) return
            val normalized = sqlQuery
                .trim()
                .lowercase(Locale.ROOT)
                .replace(Regex("\\s+"), " ")
            total++
            when {
                normalized.startsWith("select") -> select++
                normalized.startsWith("insert") -> insert++
                normalized.startsWith("update") -> update++
                normalized.startsWith("delete") -> delete++
            }
            normalizedCounts[normalized] = (normalizedCounts[normalized] ?: 0) + 1
            TABLES.forEach { table ->
                if (normalized.contains(table)) {
                    byTable[table] = (byTable[table] ?: 0) + 1
                }
            }
        }

        @Synchronized
        fun snapshot(): QuerySummary = QuerySummary(
            total = total,
            select = select,
            insert = insert,
            update = update,
            delete = delete,
            byTable = byTable.toMap(),
            normalizedCounts = normalizedCounts.toMap()
        )
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

    private fun walCheckpoint(database: AppDatabase): Map<String, Int> {
        return database.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(PASSIVE)")
            .use { cursor ->
                if (!cursor.moveToFirst()) return@use emptyMap()
                mapOf(
                    "busy" to cursor.getInt(0),
                    "logFrames" to cursor.getInt(1),
                    "checkpointedFrames" to cursor.getInt(2)
                )
            }
    }

    /**
     * Android's compile classpath does not expose java.management, while Robolectric executes on a
     * host JVM that usually does. Reflection keeps the metric observational: when the host exposes
     * GC MXBeans we report their count; otherwise JSON records null instead of a fabricated zero.
     */
    private fun gcCollectionCountOrNull(): Long? = runCatching {
        val factory = Class.forName("java.lang.management.ManagementFactory")
        val beanInterface = Class.forName("java.lang.management.GarbageCollectorMXBean")
        val collectionCount = beanInterface.getMethod("getCollectionCount")
        val beans = factory.getMethod("getGarbageCollectorMXBeans").invoke(null) as Iterable<*>
        beans.sumOf { bean ->
            val count = (collectionCount.invoke(requireNotNull(bean)) as Number).toLong()
            count.coerceAtLeast(0L)
        }
    }.getOrNull()

    private fun usedHeapBytes(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    private fun elapsedMillis(startedNanos: Long): Long =
        (System.nanoTime() - startedNanos) / 1_000_000L

    private fun findRepositoryRoot(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile ?: return@repeat
        }
        error("Could not locate repository root")
    }

    companion object {
        private val TABLES = listOf(
            "game_save",
            "teams",
            "players",
            "fixtures",
            "player_loans",
            "global_league_standings",
            "transaction_history",
            "transfer_orders",
            "transfer_installments",
            "historico_evolucao"
        )
    }
}
