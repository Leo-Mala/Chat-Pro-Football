package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.usecase.CpuSquadManagementUseCase
import com.example.usecase.ProcessTransfersUseCase
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase912WeeklyLifecyclePerformanceTest {

    @Test
    fun `full FC26 career measures weekly lifecycle without player data mutation`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataset = requireNotNull(Fc26NormalizedDatasetLoader.loadValidatedOrNull(context.assets))
        assertEquals(18_405, dataset.players.size)

        val teams = buildCurrentProFootballUniverse()
        val plan = Fc26SeedPlanner.build(
            teams = teams,
            dataset = dataset,
            proceduralRosterFactory = { team ->
                DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
            }
        )
        assertEquals(18_405, plan.report.bulkImportedFc26Players)
        assertEquals(82_125, plan.players.size)

        val dbName = "phase_9_13_weekly_lifecycle.db"
        context.deleteDatabase(dbName)
        val database = AppDatabase.getDatabaseWithName(context, dbName)
        try {
            val repository = GameRepository(database)
            val userTeamId = plan.players.first { it.teamId != null }.teamId ?: teams.first().id
            repository.runInTransaction {
                repository.saveTeams(teams)
                repository.savePlayers(plan.players)
                repository.saveGameSave(
                    GameSave(
                        coachName = "Phase 9.13 Performance",
                        currentSeason = 2026,
                        currentWeek = 2,
                        playerTeamId = userTeamId
                    )
                )
            }

            val cpu = CpuSquadManagementUseCase(repository)
            val transfers = ProcessTransfersUseCase(repository)

            val renewalStarted = System.nanoTime()
            val renewedContracts = cpu.renewCpuContractsBeforeWeeklyTick()
            val renewalMillis = elapsedMillis(renewalStarted)

            val contractStarted = System.nanoTime()
            transfers.processWeeklyContractsAndLoans()
            val contractMillis = elapsedMillis(contractStarted)

            val integrityStarted = System.nanoTime()
            val integrityReport = cpu.processWeeklyAfterContracts()
            val integrityMillis = elapsedMillis(integrityStarted)

            val persisted = repository.getAllPlayers()
            assertEquals(82_125, persisted.size)
            assertEquals(persisted.size, persisted.map { it.id }.distinct().size)

            val realById = persisted
                .asSequence()
                .filter { StableRealPlayerIdentity.isRealPlayerId(it.id) }
                .associateBy { it.id }
            assertEquals(18_405, realById.size)

            var overallMutated = 0
            var potentialMutated = 0
            var attributesMutated = 0
            dataset.players.forEach { source ->
                val player = requireNotNull(realById[source.stableId])
                if (player.force != source.overall) overallMutated++
                if (player.potential != source.potential) potentialMutated++
                if (player.atributos != source.atributos) attributesMutated++
            }
            assertEquals(0, overallMutated)
            assertEquals(0, potentialMutated)
            assertEquals(0, attributesMutated)

            val report = linkedMapOf<String, Any?>(
                "baselineMainSha" to "400b0faf6a0dbda8657743c6c7bc7fea5b9cff77",
                "phase912BaselineRenewalMillis" to 21_508,
                "phase912BaselineContractTickMillis" to 306,
                "phase912BaselineCpuIntegrityMillis" to 60,
                "phase912BaselineCombinedMillis" to 21_874,
                "persistedPlayers" to persisted.size,
                "fc26Players" to realById.size,
                "renewedContracts" to renewedContracts,
                "renewalMillis" to renewalMillis,
                "contractTickMillis" to contractMillis,
                "cpuIntegrityMillis" to integrityMillis,
                "combinedMillis" to (renewalMillis + contractMillis + integrityMillis),
                "teamsChecked" to integrityReport.teamsChecked,
                "freeAgentsSigned" to integrityReport.freeAgentsSigned,
                "emergencyPlayersGenerated" to integrityReport.emergencyPlayersGenerated,
                "overallMutated" to overallMutated,
                "potentialMutated" to potentialMutated,
                "attributesMutated" to attributesMutated,
                "duplicatePlayerIds" to (persisted.size - persisted.map { it.id }.distinct().size),
                "roomVersion" to 21
            )

            val output = File(findRepositoryRoot(), "reports/phase_9_13_weekly_lifecycle_performance.json")
            output.parentFile.mkdirs()
            output.writeText(GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n")

            println(
                "PHASE_9_13_PERF players=${persisted.size} fc26=${realById.size} " +
                    "renewalMs=$renewalMillis contractMs=$contractMillis integrityMs=$integrityMillis " +
                    "combinedMs=${renewalMillis + contractMillis + integrityMillis}"
            )
        } finally {
            database.close()
            context.deleteDatabase(dbName)
        }
    }

    private fun buildCurrentProFootballUniverse(): List<Team> = buildList {
        for (countryKey in GlobalFootballSystem.keys) {
            for (template in DefaultData.getTeamsForCountry(countryKey)) {
                val globalId = GlobalFootballSystem.getGlobalId(countryKey, template.name)
                add(
                    Team(
                        id = globalId,
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

    private fun elapsedMillis(startedNanos: Long): Long =
        (System.nanoTime() - startedNanos) / 1_000_000L
}
