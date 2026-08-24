package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.usecase.DatabaseIntegrityUseCase
import com.example.usecase.GenerateCalendarUseCase
import com.example.usecase.SeasonTransitionObserver
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
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 10.8 exact-head, full-scale season rollover certification.
 *
 * The StressTest suffix is intentional. The consolidated fast CI excludes long stress tests while
 * this class is mandatory in its dedicated file-backed Room workflow for both normal and controlled
 * single-logical-CPU profiles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase108FullScaleSeasonRolloverPerformanceStressTest {

    @Test
    fun `certify full season rollover on real 60885 player file backed room`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val auditHead = System.getenv("AUDIT_HEAD_SHA")?.takeIf { it.isNotBlank() } ?: "local"
        val profile = System.getenv("PHASE108_PROFILE")?.lowercase(Locale.ROOT) ?: PROFILE_NORMAL
        assertTrue("Unsupported Phase 10.8 profile: $profile", profile in SUPPORTED_PROFILES)
        if (System.getenv("GITHUB_ACTIONS").equals("true", ignoreCase = true)) {
            assertTrue(
                "CI performance artifact must identify the immutable PR head SHA",
                auditHead.matches(Regex("[0-9a-f]{40}"))
            )
        }
        assertTrue("Room schema must be initialized", APP_DATABASE_SCHEMA_VERSION >= 2)

        val dataset = requireNotNull(Fc26NormalizedDatasetLoader.loadValidatedOrNull(context.assets))
        assertEquals(18_405, dataset.players.size)

        val teams = buildUniverse()
        assertEquals(2_524, teams.size)
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

        val dbName = "phase_10_8_full_scale_rollover_${profile}.db"
        context.deleteDatabase(dbName)
        val stageRecorder = StageRecorder()
        val queryRecorder = QueryRecorder { stageRecorder.currentStage() }
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
            }, "phase-10-8-wal-heap-sampler-$profile").also { it.start() }

            val rolloverStarted = System.nanoTime()
            val updatedSave = SeasonTransitionUseCase(
                repository = repository,
                generateCalendarUseCase = calendarUseCase,
                databaseIntegrityUseCase = DatabaseIntegrityUseCase(repository),
                observer = stageRecorder
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
            val finalDbBytesBeforeCheckpoint = dbFile.length()
            val finalWalBytesBeforeCheckpoint = walFile.takeIf { it.exists() }?.length() ?: 0L
            val finalShmBytes = shmFile.takeIf { it.exists() }?.length() ?: 0L
            val passiveCheckpoint = walCheckpoint(requireNotNull(database), "PASSIVE")
            val truncateCheckpoint = walCheckpoint(requireNotNull(database), "TRUNCATE")
            val walAfterTruncateBytes = walFile.takeIf { it.exists() }?.length() ?: 0L
            val finalDbBytesAfterCheckpoint = dbFile.length()

            database.close()
            database = null

            val reopenStarted = System.nanoTime()
            reopened = AppDatabase.getDatabaseWithName(context, dbName)
            val reopenedRepository = GameRepository(reopened)
            val reopenedSave = reopenedRepository.getGameSave()
            val reopenedPlayers = reopenedRepository.getAllPlayers()
            val reopenedFixtures = reopenedRepository.getFixturesForSeason(2027)
            val reopenMillis = elapsedMillis(reopenStarted)
            assertNotNull(reopenedSave)
            assertEquals(2027, reopenedSave!!.currentSeason)
            assertEquals(1, reopenedSave.currentWeek)
            assertEquals(60_885, reopenedPlayers.size)
            assertTrue(reopenedFixtures.isNotEmpty())

            val querySummary = queryRecorder.snapshot()
            val activeLoanLookupCount = querySummary.normalizedCounts
                .filterKeys { sql ->
                    sql.contains("from player_loans") &&
                        sql.contains("playerid") &&
                        sql.contains("status = 'active'") &&
                        sql.contains("limit 1")
                }
                .values
                .sum()
            val teamUpdateCount = querySummary.normalizedCounts
                .filterKeys { sql -> sql.startsWith("update") && sql.contains("`teams`") }
                .values
                .sum()
            val fullEntityPlayerUpdateCount = querySummary.normalizedCounts
                .filterKeys { sql ->
                    sql.startsWith("update or abort `players` set `id`") ||
                        sql.startsWith("update `players` set `id`")
                }
                .values
                .sum()
            val roomBeginTransactions = querySummary.normalizedCounts
                .filterKeys { sql -> sql.startsWith("begin") && sql.contains("transaction") }
                .values
                .sum()

            val rolloverBudgetMillis = if (profile == PROFILE_CONSTRAINED) {
                BUDGET_CONSTRAINED_ROLLOVER_MS
            } else {
                BUDGET_NORMAL_ROLLOVER_MS
            }
            assertTrue(
                "Rollover exceeded frozen $profile budget: ${rolloverMillis}ms > ${rolloverBudgetMillis}ms",
                rolloverMillis <= rolloverBudgetMillis
            )
            assertTrue(
                "Query budget exceeded: ${querySummary.total} > $BUDGET_QUERY_COUNT",
                querySummary.total <= BUDGET_QUERY_COUNT
            )
            assertEquals("Per-player active-loan N+1 remains", 0, activeLoanLookupCount)
            assertEquals("Full-entity Player update loop remains", 0, fullEntityPlayerUpdateCount)
            assertTrue(
                "All-team rewrite remains: $teamUpdateCount team updates",
                teamUpdateCount <= BUDGET_TEAM_UPDATES
            )
            assertTrue(
                "Observed heap exceeded frozen Phase 10.8 budget",
                maxHeapBytes.get() <= BUDGET_PEAK_HEAP_BYTES
            )
            assertTrue(
                "Observed WAL exceeded frozen Phase 10.8 budget",
                maxWalBytes.get() <= BUDGET_PEAK_WAL_BYTES
            )
            assertTrue(
                "WAL did not shrink after TRUNCATE checkpoint: $walAfterTruncateBytes bytes",
                walAfterTruncateBytes <= BUDGET_POST_TRUNCATE_WAL_BYTES
            )
            assertTrue(
                "Post-rollover reopen exceeded unchanged Phase 10.1 reload budget",
                reopenMillis <= BUDGET_REOPEN_MS
            )

            val observedGcCollections = if (gcBefore != null && gcAfter != null) {
                (gcAfter - gcBefore).coerceAtLeast(0L)
            } else {
                null
            }
            val stageMillis = stageRecorder.snapshotMillis()
            REQUIRED_STAGES.forEach { stage ->
                assertTrue("Missing measured rollover stage: $stage", stageMillis.containsKey(stage))
            }

            val report = linkedMapOf<String, Any?>(
                "phase" to "10.8",
                "measurementKind" to "post_optimization_candidate",
                "profile" to profile,
                "auditHead" to auditHead,
                "environment" to mapOf(
                    "runtime" to "Robolectric sdk34 / GitHub Actions JVM",
                    "room" to "2.7.0",
                    "roomSchema" to APP_DATABASE_SCHEMA_VERSION,
                    "storage" to "file-backed SQLite WAL",
                    "runnerOs" to System.getenv("RUNNER_OS"),
                    "runnerArch" to System.getenv("RUNNER_ARCH"),
                    "runnerLabel" to System.getenv("PHASE108_RUNNER_LABEL"),
                    "cpuConstraint" to System.getenv("PHASE108_CPU_CONSTRAINT"),
                    "javaVersion" to System.getProperty("java.version"),
                    "availableProcessorsReportedByJvm" to Runtime.getRuntime().availableProcessors(),
                    "maxJvmHeapBytes" to Runtime.getRuntime().maxMemory()
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
                    "postRolloverReopenAndFullPlayerReload" to reopenMillis,
                    "stages" to stageMillis
                ),
                "queries" to mapOf(
                    "total" to querySummary.total,
                    "select" to querySummary.select,
                    "insert" to querySummary.insert,
                    "update" to querySummary.update,
                    "delete" to querySummary.delete,
                    "activeLoanLookupPerPlayer" to activeLoanLookupCount,
                    "teamUpdateStatements" to teamUpdateCount,
                    "fullEntityPlayerUpdates" to fullEntityPlayerUpdateCount,
                    "roomBeginTransactionStatements" to roomBeginTransactions,
                    "byStage" to querySummary.byStage,
                    "byTable" to querySummary.byTable,
                    "topNormalizedStatements" to querySummary.normalizedCounts.entries
                        .sortedByDescending { it.value }
                        .take(24)
                        .associate { it.key to it.value }
                ),
                "nPlusOne" to mapOf(
                    "baselineActiveLoanLookups" to BASELINE_ACTIVE_LOAN_LOOKUPS,
                    "candidateActiveLoanLookups" to activeLoanLookupCount,
                    "activeLoanNPlusOneEliminated" to (activeLoanLookupCount == 0),
                    "baselineFullEntityPlayerUpdates" to BASELINE_FULL_PLAYER_UPDATES,
                    "candidateFullEntityPlayerUpdates" to fullEntityPlayerUpdateCount,
                    "fullEntityPlayerUpdateLoopEliminated" to (fullEntityPlayerUpdateCount == 0),
                    "baselineTeamUpdates" to BASELINE_TEAM_UPDATES,
                    "candidateTeamUpdates" to teamUpdateCount
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
                    "dbFinalBytesBeforeCheckpoint" to finalDbBytesBeforeCheckpoint,
                    "dbFinalBytesAfterCheckpoint" to finalDbBytesAfterCheckpoint,
                    "walInitialBytes" to initialWalBytes,
                    "walPeakObservedBytes" to maxWalBytes.get(),
                    "walFinalBytesBeforeCheckpoint" to finalWalBytesBeforeCheckpoint,
                    "walAfterTruncateCheckpointBytes" to walAfterTruncateBytes,
                    "shmInitialBytes" to initialShmBytes,
                    "shmFinalBytes" to finalShmBytes,
                    "walCheckpointPassive" to passiveCheckpoint,
                    "walCheckpointTruncate" to truncateCheckpoint,
                    "outerRolloverTransactions" to 1,
                    "longestTransactionMillis" to rolloverMillis,
                    "totalOuterTransactionMillis" to rolloverMillis
                ),
                "fixtures" to mapOf(
                    "completedPriorSeasonFixtures" to completedFixtures.size,
                    "newSeasonFixtures" to reopenedFixtures.size
                ),
                "budgets" to mapOf(
                    "seasonRolloverMillis" to rolloverBudgetMillis,
                    "queryCount" to BUDGET_QUERY_COUNT,
                    "activeLoanLookupPerPlayer" to 0,
                    "fullEntityPlayerUpdates" to 0,
                    "teamUpdateStatements" to BUDGET_TEAM_UPDATES,
                    "peakHeapBytes" to BUDGET_PEAK_HEAP_BYTES,
                    "peakWalBytes" to BUDGET_PEAK_WAL_BYTES,
                    "walAfterTruncateBytes" to BUDGET_POST_TRUNCATE_WAL_BYTES,
                    "reopenMillis" to BUDGET_REOPEN_MS,
                    "roomSchema" to APP_DATABASE_SCHEMA_VERSION
                ),
                "baselineComparison" to mapOf(
                    "baselineHead" to BASELINE_HEAD,
                    "baselineRolloverMillis" to BASELINE_ROLLOVER_MS,
                    "candidateRolloverMillis" to rolloverMillis,
                    "rolloverImprovementPercent" to improvementPercent(BASELINE_ROLLOVER_MS, rolloverMillis),
                    "baselineQueries" to BASELINE_QUERY_COUNT,
                    "candidateQueries" to querySummary.total,
                    "queryReductionPercent" to improvementPercent(BASELINE_QUERY_COUNT.toLong(), querySummary.total.toLong()),
                    "baselinePeakHeapBytes" to BASELINE_PEAK_HEAP_BYTES,
                    "candidatePeakHeapBytes" to maxHeapBytes.get(),
                    "baselinePeakWalBytes" to BASELINE_PEAK_WAL_BYTES,
                    "candidatePeakWalBytes" to maxWalBytes.get()
                ),
                "existingBudgetsPreserved" to mapOf(
                    "phase101InitialPersistenceMillis" to 20_289,
                    "phase101FullReloadMillis" to 47_181,
                    "phase101MonthlyEvolutionMillis" to 65_553,
                    "phase101PeakHeapBytes" to 762_995_562
                )
            )

            val suffix = if (profile == PROFILE_CONSTRAINED) "_constrained" else ""
            val output = File(findRepositoryRoot(), "reports/phase_10_8_full_scale_rollover$suffix.json")
            output.parentFile.mkdirs()
            output.writeText(GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n")

            println(
                "PHASE_10_8_CANDIDATE profile=$profile head=$auditHead players=${plan.players.size} teams=${teams.size} " +
                    "rolloverMs=$rolloverMillis queries=${querySummary.total} loanLookups=$activeLoanLookupCount " +
                    "fullPlayerUpdates=$fullEntityPlayerUpdateCount teamUpdates=$teamUpdateCount " +
                    "peakHeap=${maxHeapBytes.get()} peakWal=${maxWalBytes.get()} walAfterTruncate=$walAfterTruncateBytes"
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
        val byStage: Map<String, Int>,
        val byTable: Map<String, Int>,
        val normalizedCounts: Map<String, Int>
    )

    private class StageRecorder : SeasonTransitionObserver {
        private val current = AtomicReference<String?>(null)
        private val durationsNanos = linkedMapOf<String, Long>()

        override fun onStageStarted(stage: String) {
            current.set(stage)
        }

        @Synchronized
        override fun onStageFinished(stage: String, durationNanos: Long) {
            durationsNanos[stage] = (durationsNanos[stage] ?: 0L) + durationNanos
            current.compareAndSet(stage, null)
        }

        fun currentStage(): String? = current.get()

        @Synchronized
        fun snapshotMillis(): Map<String, Long> = durationsNanos.mapValues { (_, nanos) ->
            nanos / 1_000_000L
        }
    }

    private class QueryRecorder(
        private val stageProvider: () -> String?
    ) : RoomDatabase.QueryCallback {
        private var enabled = false
        private var total = 0
        private var select = 0
        private var insert = 0
        private var update = 0
        private var delete = 0
        private val byStage = linkedMapOf<String, Int>()
        private val byTable = linkedMapOf<String, Int>()
        private val normalizedCounts = linkedMapOf<String, Int>()

        @Synchronized
        fun resetAndEnable() {
            total = 0
            select = 0
            insert = 0
            update = 0
            delete = 0
            byStage.clear()
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
            val stage = stageProvider() ?: "transaction-overhead-or-unattributed"
            byStage[stage] = (byStage[stage] ?: 0) + 1
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
            byStage = byStage.toMap(),
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

    private fun walCheckpoint(database: AppDatabase, mode: String): Map<String, Int> {
        return database.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint($mode)")
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
     * host JVM that usually does. Reflection keeps this metric observational rather than fabricated.
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

    private fun improvementPercent(baseline: Long, candidate: Long): Double =
        if (baseline <= 0L) 0.0 else ((baseline - candidate).toDouble() * 100.0) / baseline.toDouble()

    private fun findRepositoryRoot(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile ?: return@repeat
        }
        error("Could not locate repository root")
    }

    companion object {
        private const val PROFILE_NORMAL = "normal"
        private const val PROFILE_CONSTRAINED = "constrained"
        private val SUPPORTED_PROFILES = setOf(PROFILE_NORMAL, PROFILE_CONSTRAINED)

        private const val BUDGET_NORMAL_ROLLOVER_MS = 20_000L
        private const val BUDGET_CONSTRAINED_ROLLOVER_MS = 60_000L
        private const val BUDGET_QUERY_COUNT = 25_000
        private const val BUDGET_TEAM_UPDATES = 500
        private const val BUDGET_PEAK_HEAP_BYTES = 350_000_000L
        private const val BUDGET_PEAK_WAL_BYTES = 85_000_000L
        private const val BUDGET_POST_TRUNCATE_WAL_BYTES = 1_048_576L
        private const val BUDGET_REOPEN_MS = 47_181L

        private const val BASELINE_HEAD = "0704e801d8367aec53e2be59cf090cf87e65aaca"
        private const val BASELINE_ROLLOVER_MS = 20_605L
        private const val BASELINE_QUERY_COUNT = 153_810
        private const val BASELINE_ACTIVE_LOAN_LOOKUPS = 2_960
        private const val BASELINE_FULL_PLAYER_UPDATES = 57_925
        private const val BASELINE_TEAM_UPDATES = 2_524
        private const val BASELINE_PEAK_HEAP_BYTES = 273_555_432L
        private const val BASELINE_PEAK_WAL_BYTES = 75_651_472L

        private val REQUIRED_STAGES = setOf(
            "load-save",
            "load-teams",
            "load-current-season-fixtures",
            "final-classification",
            "persist-final-standings-snapshot",
            "promotion-relegation",
            "persist-team-movements",
            "load-retiring-players",
            "load-active-loans",
            "retirement-and-loan-finalization",
            "player-age-and-season-reset",
            "persist-retirement-replacements",
            "database-integrity-and-free-agents",
            "previous-season-cleanup",
            "generate-new-season-fixtures",
            "persist-new-season-fixtures",
            "persist-canonical-save"
        )

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
