package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.usecase.CpuSquadManagementUseCase
import com.example.usecase.GenerateCalendarUseCase
import com.example.usecase.GlobalLeagueSimulationUseCase
import com.example.usecase.PlayerEvolutionUseCase
import com.example.usecase.ProcessTransfersUseCase
import com.example.usecase.SimulateWeekUseCase
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
 * Diagnostic-only performance harness for the frozen post-Phase-9.14 baseline.
 *
 * This test intentionally lives behind the *StressTest naming convention so the normal
 * non-stress regression task excludes it. It writes measurements only; production behavior,
 * FC26 facts and Room schema are never changed by this branch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlobalMainAuditPerformanceStressTest {

    @Test
    fun `measure frozen main seed persistence lifecycle and season primitives`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metrics = linkedMapOf<String, Any?>()
        val heapSamples = linkedMapOf<String, Long>()
        heapSamples["beforeDataset"] = usedHeapBytes()

        lateinit var dataset: Fc26Dataset
        val datasetStarted = System.nanoTime()
        dataset = requireNotNull(Fc26NormalizedDatasetLoader.loadValidatedOrNull(context.assets))
        val datasetLoadMillis = elapsedMillis(datasetStarted)
        assertEquals(18_405, dataset.players.size)
        heapSamples["afterDataset"] = usedHeapBytes()

        val teamsStarted = System.nanoTime()
        val teams = buildCurrentProFootballUniverse()
        val teamsBuildMillis = elapsedMillis(teamsStarted)
        assertTrue(teams.isNotEmpty())
        assertEquals(teams.size, teams.map { it.id }.distinct().size)

        val matches = Fc26ClubMatcher.match(dataset, teams)
        val mappedTargetIds = matches
            .asSequence()
            .filter { it.status == Fc26ClubMatchStatus.MATCHED }
            .mapNotNull { it.targetTeamId }
            .toSet()
        val fallbackTeams = teams.filterNot { it.id in mappedTargetIds }

        var fallbackPlayersMeasured = 0
        val fallbackStarted = System.nanoTime()
        fallbackTeams.forEach { team ->
            fallbackPlayersMeasured += Fc26FallbackRosterPolicy.select(
                DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
            ).size
        }
        val fallbackGenerationMillis = elapsedMillis(fallbackStarted)

        val planStarted = System.nanoTime()
        val plan = Fc26SeedPlanner.build(
            teams = teams,
            dataset = dataset,
            proceduralRosterFactory = { team ->
                DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
            }
        )
        val seedPlanningMillis = elapsedMillis(planStarted)
        heapSamples["afterSeedPlan"] = usedHeapBytes()

        assertEquals(18_405, plan.report.bulkImportedFc26Players)
        assertEquals(plan.report.fallbackPlayersGenerated, fallbackPlayersMeasured)
        assertEquals(0, plan.players.size - plan.players.map { it.id }.distinct().size)

        val fallbackTeamIds = fallbackTeams.mapTo(hashSetOf()) { it.id }
        val playerCountsByTeam = plan.players.filter { it.teamId != null }.groupingBy { it.teamId }.eachCount()
        val userTeam = teams.firstOrNull {
            it.country.equals("Brasil", ignoreCase = true) &&
                it.id in fallbackTeamIds &&
                playerCountsByTeam[it.id] == Fc26FallbackRosterPolicy.TARGET_SIZE
        } ?: fallbackTeams.first { playerCountsByTeam[it.id] == Fc26FallbackRosterPolicy.TARGET_SIZE }

        val dbName = "global_main_audit_post_9_14.db"
        context.deleteDatabase(dbName)
        var database: AppDatabase? = null
        var reopened: AppDatabase? = null

        try {
            database = AppDatabase.getDatabaseWithName(context, dbName)
            val repository = GameRepository(database)
            val initialSave = GameSave(
                coachName = "Global Audit",
                coachReputation = 100,
                currentSeason = 2026,
                currentWeek = 1,
                playerTeamId = userTeam.id,
                bankBalance = 5_000_000_000L,
                stadiumCapacity = 100_000,
                ticketPrice = 200.0,
                socioTorcedoresCount = 100_000
            )

            val persistenceStarted = System.nanoTime()
            repository.runInTransaction {
                repository.saveTeams(teams)
                repository.savePlayers(plan.players)
                repository.saveGameSave(initialSave)
            }
            val initialPersistenceMillis = elapsedMillis(persistenceStarted)
            heapSamples["afterInitialPersistence"] = usedHeapBytes()
            assertEquals(plan.players.size, repository.getAllPlayers().size)

            database.close()
            database = null
            val databaseBytes = databaseFootprintBytes(context, dbName)

            val reopenStarted = System.nanoTime()
            reopened = AppDatabase.getDatabaseWithName(context, dbName)
            val reopenedRepository = GameRepository(reopened)
            val reopenedSave = requireNotNull(reopenedRepository.getGameSave())
            val reloadedPlayers = reopenedRepository.getAllPlayers()
            val reopenAndFullPlayerReloadMillis = elapsedMillis(reopenStarted)
            assertEquals(plan.players.size, reloadedPlayers.size)
            heapSamples["afterReopenAndPlayerReload"] = usedHeapBytes()

            val cpu = CpuSquadManagementUseCase(reopenedRepository)
            val transfers = ProcessTransfersUseCase(reopenedRepository)

            val renewalStarted = System.nanoTime()
            val renewedContracts = cpu.renewCpuContractsBeforeWeeklyTick()
            val cpuContractRenewalMillis = elapsedMillis(renewalStarted)

            val contractStarted = System.nanoTime()
            transfers.processWeeklyContractsAndLoans()
            val contractTickMillis = elapsedMillis(contractStarted)

            val integrityStarted = System.nanoTime()
            val integrityReport = cpu.processWeeklyAfterContracts()
            val cpuSquadIntegrityMillis = elapsedMillis(integrityStarted)

            val transferCandidate = reopenedRepository.getFreeAgents()
                .asSequence()
                .filter { !it.isOnLoan }
                .minByOrNull { it.force }
                ?: error("Audit expected at least one unassigned/free-agent player")
            val transferStarted = System.nanoTime()
            val transferResult = transfers.buyPlayer(
                save = requireNotNull(reopenedRepository.getGameSave()),
                player = transferCandidate,
                offerPrice = 1L
            )
            val transferProcessingMillis = elapsedMillis(transferStarted)
            assertTrue(transferResult is ProcessTransfersUseCase.TransferResult.Success)

            val calendarUseCase = GenerateCalendarUseCase(reopenedRepository)
            val calendarStarted = System.nanoTime()
            val fixtures = calendarUseCase.generateSeasonFixtures(
                season = 2026,
                teams = teams,
                userTeamId = userTeam.id,
                userCountry = userTeam.country
            )
            val calendarGenerationMillis = elapsedMillis(calendarStarted)
            EuropeanNewSaveSeedCoordinator.clear(reopenedRepository)

            val calendarPersistStarted = System.nanoTime()
            reopenedRepository.saveFixtures(fixtures)
            val calendarPersistenceMillis = elapsedMillis(calendarPersistStarted)

            val advanceWeekStarted = System.nanoTime()
            val weekOneCpuMatches = SimulateWeekUseCase(reopenedRepository).simulateCpuMatchesForWeek(
                season = 2026,
                week = 1,
                excludedTeamId = userTeam.id
            )
            val advanceWeekCpuSimulationMillis = elapsedMillis(advanceWeekStarted)

            val evolutionStarted = System.nanoTime()
            val evolutionResults = PlayerEvolutionUseCase(reopenedRepository).executeMonthlyEvolution(
                save = requireNotNull(reopenedRepository.getGameSave()),
                periodDate = "2026-01"
            )
            val monthlyEvolutionMillis = elapsedMillis(evolutionStarted)
            heapSamples["afterMonthlyEvolution"] = usedHeapBytes()

            val globalStarted = System.nanoTime()
            val globalStandings = GlobalLeagueSimulationUseCase().buildSeasonStandings(
                season = 2026,
                teams = teams,
                detailedFixtures = reopenedRepository.getFixturesForSeason(2026),
                detailedCountry = userTeam.country
            )
            val globalStandingsBuildMillis = elapsedMillis(globalStarted)

            val standingsPersistStarted = System.nanoTime()
            reopenedRepository.saveGlobalStandingsForSeason(2026, globalStandings)
            val globalStandingsPersistenceMillis = elapsedMillis(standingsPersistStarted)
            heapSamples["afterGlobalSeason"] = usedHeapBytes()

            val observedHeapPeak = heapSamples.values.maxOrNull() ?: 0L
            val seedAndInitialPersistenceCoreMillis = seedPlanningMillis + initialPersistenceMillis

            metrics["auditBaselineSha"] = "74a004658f01140d235e3c32b59a02d1e7034798"
            metrics["environment"] = "Robolectric sdk34 on GitHub Actions JVM; heap values are JVM proxy checkpoints, not Android-device profiler peaks"
            metrics["datasetPlayers"] = dataset.players.size
            metrics["targetTeams"] = teams.size
            metrics["matchedClubs"] = plan.report.matchedClubs
            metrics["unmatchedClubs"] = plan.report.unmatchedClubs
            metrics["ambiguousClubs"] = plan.report.ambiguousClubs
            metrics["mappedFc26Players"] = plan.report.playersWithMappedClub
            metrics["trueDatasetFreeAgents"] = plan.report.importedFreeAgents
            metrics["unassignedFc26Players"] = plan.report.importedUnassignedClubPlayers
            metrics["fallbackRosters"] = plan.report.fallbackRostersRequired
            metrics["fallbackPlayers"] = plan.report.fallbackPlayersGenerated
            metrics["persistedPlayers"] = reloadedPlayers.size
            metrics["datasetLoadMillis"] = datasetLoadMillis
            metrics["teamUniverseBuildMillis"] = teamsBuildMillis
            metrics["fallbackGenerationMillis"] = fallbackGenerationMillis
            metrics["seedPlanningMillis"] = seedPlanningMillis
            metrics["initialPersistenceMillis"] = initialPersistenceMillis
            metrics["seedAndInitialPersistenceCoreMillis"] = seedAndInitialPersistenceCoreMillis
            metrics["databaseBytesAfterInitialClose"] = databaseBytes
            metrics["reopenAndFullPlayerReloadMillis"] = reopenAndFullPlayerReloadMillis
            metrics["cpuContractRenewalMillis"] = cpuContractRenewalMillis
            metrics["renewedCpuContracts"] = renewedContracts
            metrics["contractTickMillis"] = contractTickMillis
            metrics["cpuSquadIntegrityMillis"] = cpuSquadIntegrityMillis
            metrics["cpuTeamsChecked"] = integrityReport.teamsChecked
            metrics["transferProcessingMillis"] = transferProcessingMillis
            metrics["calendarGenerationMillis"] = calendarGenerationMillis
            metrics["calendarPersistenceMillis"] = calendarPersistenceMillis
            metrics["calendarFixtures"] = fixtures.size
            metrics["advanceWeekCpuSimulationMillis"] = advanceWeekCpuSimulationMillis
            metrics["weekOneCpuMatches"] = weekOneCpuMatches.size
            metrics["monthlyEvolutionMillis"] = monthlyEvolutionMillis
            metrics["monthlyEvolutionPlayers"] = evolutionResults.size
            metrics["globalStandingsBuildMillis"] = globalStandingsBuildMillis
            metrics["globalStandingsPersistenceMillis"] = globalStandingsPersistenceMillis
            metrics["globalStandingRows"] = globalStandings.size
            metrics["heapCheckpointBytes"] = heapSamples
            metrics["observedCheckpointPeakHeapBytes"] = observedHeapPeak

            val output = File(findRepositoryRoot(), "reports/global_main_audit_performance.json")
            output.parentFile.mkdirs()
            output.writeText(GsonBuilder().setPrettyPrinting().create().toJson(metrics) + "\n")

            println(
                "GLOBAL_MAIN_AUDIT players=${reloadedPlayers.size} datasetMs=$datasetLoadMillis " +
                    "seedMs=$seedPlanningMillis persistMs=$initialPersistenceMillis reopenMs=$reopenAndFullPlayerReloadMillis " +
                    "renewalMs=$cpuContractRenewalMillis contractMs=$contractTickMillis integrityMs=$cpuSquadIntegrityMillis " +
                    "transferMs=$transferProcessingMillis calendarMs=$calendarGenerationMillis weekMs=$advanceWeekCpuSimulationMillis " +
                    "evolutionMs=$monthlyEvolutionMillis globalMs=$globalStandingsBuildMillis dbBytes=$databaseBytes"
            )
        } finally {
            runCatching { database?.close() }
            runCatching { reopened?.close() }
            context.deleteDatabase(dbName)
        }
    }

    private fun buildCurrentProFootballUniverse(): List<Team> = buildList {
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
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }

    private fun databaseFootprintBytes(context: Context, dbName: String): Long {
        val db = context.getDatabasePath(dbName)
        return listOf(db, File(db.path + "-wal"), File(db.path + "-shm"))
            .filter { it.exists() }
            .sumOf { it.length() }
    }

    private fun usedHeapBytes(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    private fun elapsedMillis(startedNanos: Long): Long =
        (System.nanoTime() - startedNanos) / 1_000_000L
}
