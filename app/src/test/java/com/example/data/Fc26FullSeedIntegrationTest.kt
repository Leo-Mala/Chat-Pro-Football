package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Fc26FullSeedIntegrationTest {

    @Test
    fun `full FC26 seed maps persists reloads and writes audit report`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataset = requireNotNull(Fc26NormalizedDatasetLoader.loadValidatedOrNull(context.assets))
        assertEquals(18_405, dataset.players.size)
        assertEquals(662, dataset.sourceClubs.size)

        val teams = buildCurrentProFootballUniverse()
        assertTrue(teams.isNotEmpty())
        assertEquals(teams.size, teams.map { it.id }.distinct().size)

        val heapBefore = usedHeapBytes()
        val planStarted = System.nanoTime()
        val plan = Fc26SeedPlanner.build(
            teams = teams,
            dataset = dataset,
            proceduralRosterFactory = { team ->
                DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
            }
        )
        val planMillis = elapsedMillis(planStarted)
        val heapAfterPlan = usedHeapBytes()

        assertEquals(18_405, plan.report.datasetPlayers)
        assertEquals(18_405, plan.report.bulkImportedFc26Players)
        assertEquals(
            plan.report.datasetPlayers,
            plan.report.importedFc26Players + plan.report.skippedDatasetPlayers
        )
        assertEquals(
            plan.report.datasetPlayers,
            plan.report.playersWithMappedClub + plan.report.importedFreeAgents + plan.report.importedUnassignedClubPlayers
        )
        assertEquals(
            plan.report.importedUnassignedClubPlayers,
            plan.report.importedUnmatchedClubPlayers + plan.report.importedAmbiguousClubPlayers
        )
        assertEquals(plan.report.skippedDatasetPlayers, plan.report.importedUnassignedClubPlayers)
        assertEquals(
            plan.report.datasetClubs,
            plan.report.matchedClubs + plan.report.unmatchedClubs + plan.report.ambiguousClubs
        )
        assertEquals(0, plan.report.successfullyMappedLoans)
        assertTrue(plan.players.map { it.id }.distinct().size == plan.players.size)

        // Bulk contract: every player from the supplied FC26 dataset must exist in the plan exactly once.
        val importedRealPlayers = plan.players
            .asSequence()
            .filter { StableRealPlayerIdentity.isRealPlayerId(it.id) }
            .associateBy { it.id }
        assertEquals(dataset.players.size, importedRealPlayers.size)

        var overallMutated = 0
        var potentialMutated = 0
        var attributesMutated = 0
        dataset.players.forEach { source ->
            val imported = requireNotNull(importedRealPlayers[source.stableId]) {
                "FC26 player not imported: sourcePlayerId=${source.sourcePlayerId} name=${source.fullName}"
            }
            if (source.overall != imported.force) overallMutated += 1
            if (source.potential != imported.potential) potentialMutated += 1
            if (source.atributos != imported.atributos) attributesMutated += 1
            assertEquals(source.stableId, imported.id)
            assertEquals(source.primaryPosition, imported.sourceMetadataOrNull()?.primaryPosition)
            assertEquals(source.clubTeamId, imported.sourceMetadataOrNull()?.sourceClubTeamId)
            assertEquals(source.clubName, imported.sourceMetadataOrNull()?.sourceClubName)
        }
        assertEquals(0, overallMutated)
        assertEquals(0, potentialMutated)
        assertEquals(0, attributesMutated)

        val unresolvedSourceIds = plan.report.clubMatches
            .filter { it.status != Fc26ClubMatchStatus.MATCHED }
            .mapTo(mutableSetOf()) { it.sourceClubTeamId }
        val unresolvedImported = dataset.players.filter { it.clubTeamId in unresolvedSourceIds }
        assertEquals(plan.report.importedUnassignedClubPlayers, unresolvedImported.size)
        unresolvedImported.forEach { source ->
            val imported = requireNotNull(importedRealPlayers[source.stableId])
            assertEquals("Unresolved source-club player must remain unassigned", null, imported.teamId)
            assertEquals(source.clubTeamId, imported.sourceMetadataOrNull()?.sourceClubTeamId)
            assertEquals(source.clubName, imported.sourceMetadataOrNull()?.sourceClubName)
        }

        val realSample = plan.players.firstOrNull { StableRealPlayerIdentity.isRealPlayerId(it.id) }
        assertNotNull("At least one FC26 player must map into the current game universe", realSample)
        val sampledPlayer = requireNotNull(realSample)
        val sourceSample = dataset.players.single { it.stableId == sampledPlayer.id }
        assertEquals(sourceSample.overall, sampledPlayer.force)
        assertEquals(sourceSample.potential, sampledPlayer.potential)

        val dbName = "fc26_full_seed_integration.db"
        context.deleteDatabase(dbName)
        var database: AppDatabase? = null
        var reopened: AppDatabase? = null
        try {
            database = AppDatabase.getDatabaseWithName(context, dbName)
            val repository = GameRepository(database)
            val persistStarted = System.nanoTime()
            repository.runInTransaction {
                repository.saveTeams(teams)
                repository.savePlayers(plan.players)
                repository.saveGameSave(
                    GameSave(
                        coachName = "FC26 Integration QA",
                        currentSeason = 2026,
                        currentWeek = 1,
                        playerTeamId = sampledPlayer.teamId ?: teams.first().id
                    )
                )
            }
            val persistMillis = elapsedMillis(persistStarted)
            val persistedCount = repository.getAllPlayers().size
            assertEquals(plan.players.size, persistedCount)
            assertEquals(sourceSample.overall, repository.getPlayer(sampledPlayer.id)?.force)
            assertEquals(sourceSample.potential, repository.getPlayer(sampledPlayer.id)?.potential)

            database.close()
            database = null

            val databaseBytes = databaseFootprintBytes(context, dbName)
            reopened = AppDatabase.getDatabaseWithName(context, dbName)
            val reopenedRepository = GameRepository(reopened)
            val reloaded = requireNotNull(reopenedRepository.getPlayer(sampledPlayer.id))
            assertEquals("FC26 overall must survive Room close/reopen", sourceSample.overall, reloaded.force)
            assertEquals("FC26 potential must survive Room close/reopen", sourceSample.potential, reloaded.potential)
            assertEquals(sourceSample.primaryPosition, reloaded.sourceMetadataOrNull()?.primaryPosition)

            writeAuditReports(
                dataset = dataset,
                teams = teams,
                plan = plan,
                planMillis = planMillis,
                persistMillis = persistMillis,
                databaseBytes = databaseBytes,
                heapBefore = heapBefore,
                heapAfterPlan = heapAfterPlan,
                sampleSourcePlayerId = sourceSample.sourcePlayerId,
                sampleInternalPlayerId = sampledPlayer.id,
                sampleForce = reloaded.force,
                samplePotential = reloaded.potential,
                persistedPlayerCount = persistedCount,
                overallMutated = overallMutated,
                potentialMutated = potentialMutated,
                attributesMutated = attributesMutated
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

    private fun writeAuditReports(
        dataset: Fc26Dataset,
        teams: List<Team>,
        plan: Fc26SeedPlanner.Plan,
        planMillis: Long,
        persistMillis: Long,
        databaseBytes: Long,
        heapBefore: Long,
        heapAfterPlan: Long,
        sampleSourcePlayerId: Long,
        sampleInternalPlayerId: Long,
        sampleForce: Int,
        samplePotential: Int,
        persistedPlayerCount: Int,
        overallMutated: Int,
        potentialMutated: Int,
        attributesMutated: Int
    ) {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val matchByStatus = plan.report.clubMatches.groupBy { it.status }
        val unmatchedPlayers = matchByStatus[Fc26ClubMatchStatus.UNMATCHED].orEmpty().sumOf { it.playerCount }
        val ambiguousPlayers = matchByStatus[Fc26ClubMatchStatus.AMBIGUOUS].orEmpty().sumOf { it.playerCount }
        val audits = Fc26ClubMatcher.auditCandidates(dataset, teams, limitPerClub = 5)
        val matchBySourceId = plan.report.clubMatches.associateBy { it.sourceClubTeamId }

        val report = linkedMapOf<String, Any?>(
            "datasetSource" to dataset.manifest.datasetSource,
            "datasetVersion" to dataset.manifest.datasetVersion,
            "datasetPlayers" to plan.report.datasetPlayers,
            "processedPlayers" to plan.report.datasetPlayers,
            "playersImported" to plan.report.bulkImportedFc26Players,
            "playersNotImported" to (plan.report.datasetPlayers - plan.report.bulkImportedFc26Players),
            "clubCoverageImportedPlayers" to plan.report.importedFc26Players,
            "clubCoverageUnresolvedPlayers" to plan.report.skippedDatasetPlayers,
            "datasetClubs" to plan.report.datasetClubs,
            "proFootballTargetTeams" to teams.size,
            "clubsResolved" to plan.report.matchedClubs,
            "clubsUnmatched" to plan.report.unmatchedClubs,
            "clubsAmbiguous" to plan.report.ambiguousClubs,
            "playersWithMappedClub" to plan.report.playersWithMappedClub,
            "trueDatasetFreeAgents" to plan.report.importedFreeAgents,
            "unassignedPlayersFromUnresolvedClubs" to plan.report.importedUnassignedClubPlayers,
            "unassignedPlayersFromUnmatchedClubs" to plan.report.importedUnmatchedClubPlayers,
            "unassignedPlayersFromAmbiguousClubs" to plan.report.importedAmbiguousClubPlayers,
            "loanPlayers" to plan.report.datasetLoanPlayers,
            "successfullyMappedLoans" to plan.report.successfullyMappedLoans,
            "unresolvedLoans" to plan.report.unresolvedLoans,
            "fallbackRostersRequired" to plan.report.fallbackRostersRequired,
            "playersFromUnmatchedClubs" to unmatchedPlayers,
            "playersFromAmbiguousClubs" to ambiguousPlayers,
            "duplicatePlayerIds" to 0,
            "duplicateTeamIds" to 0,
            "overallMutated" to overallMutated,
            "potentialMutated" to potentialMutated,
            "attributesMutated" to attributesMutated,
            "persistedPlayersIncludingFallback" to persistedPlayerCount,
            "playersByCountry" to dataset.players.groupingBy { it.nationality }.eachCount().toSortedMap(),
            "playersByLeague" to dataset.players.groupingBy { it.leagueName ?: "FREE_AGENT" }.eachCount().toSortedMap(),
            "playersByClub" to dataset.players.groupingBy { it.clubName ?: "FREE_AGENT" }.eachCount().toSortedMap(),
            "minimumOverall" to dataset.players.minOf { it.overall },
            "maximumOverall" to dataset.players.maxOf { it.overall },
            "averageOverall" to dataset.players.map { it.overall }.average(),
            "minimumPotential" to dataset.players.minOf { it.potential },
            "maximumPotential" to dataset.players.maxOf { it.potential },
            "averagePotential" to dataset.players.map { it.potential }.average(),
            "playersWithoutPosition" to dataset.players.count { it.positions.isEmpty() },
            "playersWithoutNationality" to dataset.players.count { it.nationality.isBlank() },
            "performance" to linkedMapOf(
                "seedPlanningMillis" to planMillis,
                "roomBulkPersistenceMillis" to persistMillis,
                "databaseBytesAfterClose" to databaseBytes,
                "heapUsedBeforePlanBytes" to heapBefore,
                "heapUsedAfterPlanBytes" to heapAfterPlan,
                "heapDeltaDuringPlanBytes" to (heapAfterPlan - heapBefore)
            ),
            "reloadProof" to linkedMapOf(
                "sourcePlayerId" to sampleSourcePlayerId,
                "internalPlayerId" to sampleInternalPlayerId,
                "forceAfterReload" to sampleForce,
                "potentialAfterReload" to samplePotential
            ),
            "clubMatches" to plan.report.clubMatches
        )

        val unresolved = audits.map { audit ->
            val match = matchBySourceId.getValue(audit.sourceClubTeamId)
            linkedMapOf<String, Any?>(
                "club_team_id" to audit.sourceClubTeamId,
                "source_name" to audit.sourceClubName,
                "country" to audit.sourceCountry,
                "league_id" to audit.leagueId,
                "competition" to audit.leagueName,
                "players" to audit.playerCount,
                "status" to audit.currentStatus.name,
                "reason" to match.reason,
                "materialization_status" to audit.materializationStatus.name,
                "expected_stable_team_id" to audit.expectedStableTeamId,
                "expected_stable_team_name" to audit.expectedStableTeamName,
                "possible_targets" to audit.candidates.map { candidate ->
                    linkedMapOf<String, Any?>(
                        "target_team_id" to candidate.targetTeamId,
                        "target_name" to candidate.targetTeamName,
                        "country" to candidate.targetCountry,
                        "division" to candidate.targetDivision,
                        "diagnostic_score" to candidate.score,
                        "reasons" to candidate.reasons
                    )
                }
            )
        }

        val unresolvedReport = linkedMapOf<String, Any?>(
            "datasetVersion" to dataset.manifest.datasetVersion,
            "datasetPlayers" to dataset.players.size,
            "datasetClubs" to dataset.sourceClubs.size,
            "playersImported" to plan.report.bulkImportedFc26Players,
            "playersNotImported" to (plan.report.datasetPlayers - plan.report.bulkImportedFc26Players),
            "clubsResolved" to plan.report.matchedClubs,
            "clubsUnmatched" to plan.report.unmatchedClubs,
            "clubsAmbiguous" to plan.report.ambiguousClubs,
            "unassignedPlayersFromUnresolvedClubs" to plan.report.importedUnassignedClubPlayers,
            "note" to "possible_targets are diagnostic only; no fuzzy candidate is auto-promoted to MATCHED",
            "unresolvedClubs" to unresolved
        )

        val repoRoot = findRepositoryRoot()
        val mappingOutput = File(repoRoot, "reports/fc26_club_mapping_report.json")
        val bulkOutput = File(repoRoot, "reports/fc26_bulk_import_report.json")
        val unresolvedOutput = File(repoRoot, "reports/fc26_bulk_import_unresolved.json")
        mappingOutput.parentFile.mkdirs()
        mappingOutput.writeText(gson.toJson(report) + "\n", Charsets.UTF_8)
        bulkOutput.writeText(gson.toJson(report) + "\n", Charsets.UTF_8)
        unresolvedOutput.writeText(gson.toJson(unresolvedReport) + "\n", Charsets.UTF_8)

        println(
            "FC26_BULK_IMPORT dataset=${plan.report.datasetPlayers} imported=${plan.report.bulkImportedFc26Players} " +
                "notImported=${plan.report.datasetPlayers - plan.report.bulkImportedFc26Players} " +
                "clubs=${plan.report.datasetClubs} resolved=${plan.report.matchedClubs} " +
                "unmatched=${plan.report.unmatchedClubs} ambiguous=${plan.report.ambiguousClubs} " +
                "unassigned=${plan.report.importedUnassignedClubPlayers} overallMutated=$overallMutated " +
                "potentialMutated=$potentialMutated attributesMutated=$attributesMutated " +
                "planMs=$planMillis persistMs=$persistMillis dbBytes=$databaseBytes"
        )
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

    private fun elapsedMillis(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000L
}
