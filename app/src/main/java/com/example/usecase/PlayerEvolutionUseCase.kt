package com.example.usecase

import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.HistoricoEvolucao
import com.example.data.MonthlyEvolutionCommitmentBuilder
import com.example.data.MonthlyEvolutionInputSnapshot
import com.example.data.MonthlyEvolutionPlayerState
import com.example.data.MonthlyEvolutionRevisionSnapshot
import com.example.data.MonthlyEvolutionUniverseCommitment
import com.example.data.Player
import com.example.data.PlayerEvolutionMonthlyEngine
import com.example.data.PlayerEvolutionResult
import com.example.data.PlayerEvolutionSystem
import com.example.data.Team
import com.example.data.applyMonthlyEvolutionPlayerStateDeltas
import com.example.data.forEachMonthlyEvolutionPlayerBatch
import com.example.data.getMonthlyEvolutionHistoryFingerprints
import com.example.data.getMonthlyEvolutionInputSnapshots
import com.example.data.getMonthlyEvolutionPlayerCount
import com.example.data.insertMonthlyEvolutionHistoryRowsBulk
import com.example.data.monthlyEvolutionFingerprint
import com.example.data.prepareMonthlyEvolutionRevisionSnapshot
import com.example.data.currentMonthlyEvolutionRevisionSnapshotOrNull
import com.example.data.resetMonthlyEvolutionCounters
import com.example.data.toMonthlyEvolutionInputSnapshot
import com.example.data.toMonthlyEvolutionPlayerState
import com.example.data.validateMonthlyEvolutionRosterInputs
import com.example.data.validateMonthlyEvolutionRosterRevisionOnly
import com.example.data.validateMonthlyEvolutionUniverseCommitment
import kotlin.random.Random

/**
 * Immutable monthly-evolution plan. The expensive world-player calculation is intentionally
 * separated from the Room commit so the production weekly path can calculate before acquiring the
 * write transaction and then fail closed if any evolution input became stale.
 */
data class MonthlyEvolutionPlan(
    val expectedSeason: Int,
    val expectedWeek: Int,
    val expectedPlayerTeamId: Long,
    val periodDate: String,
    val results: List<PlayerEvolutionResult>,
    /** Only players whose persisted evolution-owned state changed. */
    val updatedPlayers: List<Player>,
    val historyLogs: List<HistoricoEvolucao>,
    /** Detailed/legacy stale-plan snapshots. Compact production plans leave this empty. */
    val expectedInputs: List<MonthlyEvolutionInputSnapshot> = emptyList(),
    /** Primitive-array SHA-256 proof retained by the compact production path. */
    val expectedUniverseCommitment: MonthlyEvolutionUniverseCommitment? = null,
    /** Exact universe size at preparation; detects players inserted after a standalone plan. */
    val expectedPlayerCount: Int = expectedInputs.size,
    /** Training-center level influences evolution and therefore participates in stale validation. */
    val expectedTrainingCenterLevels: Map<Long, Int> = emptyMap(),
    /**
     * Compact four-column persistence state for the weekly/season path. Existing detailed/manual
     * plans remain source-compatible through the default conversion from [updatedPlayers].
     */
    val updatedPlayerStates: List<MonthlyEvolutionPlayerState> =
        updatedPlayers.map { it.toMonthlyEvolutionPlayerState() },
    /**
     * O(1) invalidation proof captured atomically before the first Player read. Null means the
     * auxiliary tracker could not be proven safe and commit must use the legacy full validation.
     */
    val expectedPlayerRevision: MonthlyEvolutionRevisionSnapshot? = null
)

/**
 * Explicit outcome for a standalone monthly-evolution attempt.
 *
 * A stale plan is an expected concurrency outcome, not an exceptional crash condition. When
 * [committed] is false the plan was discarded before any monthly write and [results] is empty.
 */
data class MonthlyEvolutionExecutionOutcome(
    val committed: Boolean,
    val results: List<PlayerEvolutionResult>
)

/**
 * UseCase responsável pela recuperação física, evolução mensal, gestão de lesões,
 * suspensões por cartão e renovação de contratos de atletas.
 */
// 4,096 preserves the exact global ORDER BY and sequential RNG stream while keeping each in-memory
// working batch bounded. The compact path now obtains those batches from one ordered SQLite cursor.
private const val MONTHLY_EVOLUTION_BATCH_SIZE = 4096

class PlayerEvolutionUseCase(private val repository: GameRepository) {

    /**
     * Atualiza o estado físico dos jogadores ao final de cada semana:
     * - Recuperação de stamina
     * - Redução do tempo de lesão e suspensão
     */
    suspend fun processPostMatchRecovery(
        save: GameSave,
        userPlayers: List<Player>,
        trainingCenterLevel: Int = 1,
        infiniteStamina: Boolean = false
    ): List<Player> {
        val updatedPlayers = userPlayers.map { player ->
            var newEnergy = player.energy
            if (infiniteStamina) {
                newEnergy = 100
            } else {
                val recoveryRate = 15 + (trainingCenterLevel * 3)
                newEnergy = (player.energy + recoveryRate).coerceAtMost(100)
            }

            val newInjury =
                if (player.injuryWeeksRemaining > 0) player.injuryWeeksRemaining - 1 else 0
            val newSuspension =
                if (player.suspensionWeeksRemaining > 0) player.suspensionWeeksRemaining - 1 else 0

            player.copy(
                energy = newEnergy,
                injuryWeeksRemaining = newInjury,
                suspensionWeeksRemaining = newSuspension
            )
        }

        if (updatedPlayers.isNotEmpty()) repository.updatePlayers(updatedPlayers)
        return updatedPlayers
    }

    /**
     * CPU-heavy monthly planning. The weekly production path defaults to compact result retention:
     * every player is still processed in the same order with the same RNG calls, but no-op result
     * objects are not kept alive through the weekly commit. Standalone callers that need the legacy
     * one-result-per-player detail can opt into [retainDetailedResults].
     */
    suspend fun prepareMonthlyEvolution(
        save: GameSave,
        periodDate: String,
        retainDetailedResults: Boolean = false
    ): MonthlyEvolutionPlan {
        // Must be captured before COUNT(*) or any streaming Player query. A concurrent writer after
        // this point is observed by SQLite triggers regardless of which repository/editor path wrote.
        val expectedPlayerRevision = repository.prepareMonthlyEvolutionRevisionSnapshot()
        val expectedPlayerCount = repository.getMonthlyEvolutionPlayerCount()
        val allTeams = repository.getAllTeams().associateBy { it.id }
        val evolutionResults = ArrayList<PlayerEvolutionResult>(
            if (retainDetailedResults) expectedPlayerCount else 0
        )
        val changedPlayers = if (retainDetailedResults) ArrayList<Player>() else null
        val changedPlayerStates =
            if (retainDetailedResults) null else ArrayList<MonthlyEvolutionPlayerState>()
        val historyLogs = ArrayList<HistoricoEvolucao>()
        val expectedInputs = if (retainDetailedResults) {
            ArrayList<MonthlyEvolutionInputSnapshot>(expectedPlayerCount)
        } else null
        val commitmentBuilder = if (retainDetailedResults) null else {
            MonthlyEvolutionCommitmentBuilder(expectedPlayerCount)
        }
        val referencedTeamIds = HashSet<Long>()

        fun processBatch(batch: List<Player>, detailed: Boolean) {
            val batchResults = if (detailed) {
                PlayerEvolutionMonthlyEngine.process(batch, allTeams, periodDate)
            } else {
                PlayerEvolutionMonthlyEngine.processChanged(batch, allTeams, periodDate)
            }
            if (detailed) evolutionResults.addAll(batchResults)

            for (result in batchResults) {
                if (result.historyLogs.isNotEmpty() || result.netChange != 0.0) {
                    if (detailed) {
                        changedPlayers!!.add(result.player)
                    } else {
                        changedPlayerStates!!.add(result.player.toMonthlyEvolutionPlayerState())
                    }
                }
                if (result.historyLogs.isNotEmpty()) historyLogs.addAll(result.historyLogs)
            }
            for (player in batch) {
                if (detailed) {
                    expectedInputs!!.add(player.toMonthlyEvolutionInputSnapshot())
                }
                player.teamId?.let(referencedTeamIds::add)
            }
        }

        // Keep the exact ORDER BY force DESC, name ASC and the same 4,096-player engine boundaries.
        // Detailed callers retain the legacy Room pagination because their returned results expose
        // full entities. The compact weekly path streams the same projection through one cursor so
        // SQLite performs the global sort once instead of repeating it for every LIMIT/OFFSET page.
        if (retainDetailedResults) {
            var offset = 0
            while (offset < expectedPlayerCount) {
                val batch = repository.getAllPlayersBatch(MONTHLY_EVOLUTION_BATCH_SIZE, offset)
                check(batch.isNotEmpty()) {
                    "Monthly evolution player scan ended at $offset of $expectedPlayerCount rows."
                }
                processBatch(batch, detailed = true)
                offset += batch.size
            }
            check(offset == expectedPlayerCount) {
                "Monthly evolution detailed scan read $offset of $expectedPlayerCount players."
            }
        } else {
            val processed = repository.forEachMonthlyEvolutionPlayerBatch(
                batchSize = MONTHLY_EVOLUTION_BATCH_SIZE,
                onPlayerRead = { player, atributosStorage ->
                    commitmentBuilder!!.add(player, atributosStorage)
                }
            ) { batch ->
                processBatch(batch, detailed = false)
            }
            check(processed == expectedPlayerCount) {
                "Monthly evolution compact scan read $processed of $expectedPlayerCount players."
            }
        }

        val expectedUniverseCommitment = commitmentBuilder?.build()
        if (retainDetailedResults) {
            check(expectedInputs!!.size == expectedPlayerCount) {
                "Monthly evolution expected $expectedPlayerCount detailed inputs but captured ${expectedInputs.size}."
            }
        } else {
            check(expectedUniverseCommitment?.size == expectedPlayerCount) {
                "Monthly evolution compact commitment size mismatch."
            }
        }

        val expectedTrainingLevels = referencedTeamIds.associateWith { teamId ->
            allTeams[teamId]?.trainingCenterLevel ?: 1
        }

        return MonthlyEvolutionPlan(
            expectedSeason = save.currentSeason,
            expectedWeek = save.currentWeek,
            expectedPlayerTeamId = save.playerTeamId,
            periodDate = periodDate,
            results = evolutionResults,
            updatedPlayers = changedPlayers ?: emptyList(),
            historyLogs = historyLogs,
            expectedInputs = expectedInputs ?: emptyList(),
            expectedUniverseCommitment = expectedUniverseCommitment,
            expectedPlayerRevision = expectedPlayerRevision,
            expectedPlayerCount = expectedPlayerCount,
            expectedTrainingCenterLevels = expectedTrainingLevels,
            updatedPlayerStates = changedPlayerStates
                ?: changedPlayers.orEmpty().map { it.toMonthlyEvolutionPlayerState() }
        )
    }

    /**
     * Persists one prepared plan atomically and fail-closed against stale save/player/team state.
     *
     * Full Player entities are never replayed. The commit validates lightweight evolution inputs
     * and writes only `atributosJson`, `force`, monthly minutes and `evolucaoMensal`. Contract,
     * salary, team, fitness, transfer and lineup columns therefore cannot be restored from a stale
     * prepared snapshot.
     *
     * [allowWeeklyRosterCorrections] is reserved for the canonical weekly-close transaction. That
     * lifecycle can legitimately expire a contract/loan, move a player, or create a small number of
     * emergency players after the world plan was prepared. If the persisted football epoch did not
     * move, roster-only changes are validated from id/teamId without rehashing every football input.
     * Any football epoch change, missing tracker or tracker-integrity failure falls back to the
     * original complete SHA-256 proof and remains fail-closed.
     *
     * Retrying an already committed standalone plan is safe when it produced history: if every
     * history fingerprint for this plan is already present, the method returns successfully before
     * counters or players are touched again.
     */
    suspend fun commitMonthlyEvolution(
        plan: MonthlyEvolutionPlan,
        allowWeeklyRosterCorrections: Boolean = false
    ): Boolean = repository.withTransaction {
        val currentSave = repository.getGameSave() ?: return@withTransaction false
        if (currentSave.currentSeason != plan.expectedSeason ||
            currentSave.currentWeek != plan.expectedWeek ||
            currentSave.playerTeamId != plan.expectedPlayerTeamId
        ) {
            return@withTransaction false
        }

        val existingHistory = if (plan.historyLogs.isEmpty()) {
            emptySet()
        } else {
            repository.getMonthlyEvolutionHistoryFingerprints(plan.periodDate)
        }
        if (plan.historyLogs.isNotEmpty() && existingHistory.isNotEmpty()) {
            val plannedFingerprints =
                plan.historyLogs.mapTo(hashSetOf()) { it.monthlyEvolutionFingerprint() }
            val alreadyCommitted = plannedFingerprints.all { it in existingHistory }
            if (alreadyCommitted) return@withTransaction true
            if (plannedFingerprints.any { it in existingHistory }) return@withTransaction false
        }

        var currentTeamsById: Map<Long, Team>? = null
        if (plan.expectedTrainingCenterLevels.isNotEmpty()) {
            currentTeamsById = repository.getAllTeams().associateBy { it.id }
            if (plan.expectedTrainingCenterLevels.any { (teamId, level) ->
                    (currentTeamsById[teamId]?.trainingCenterLevel ?: 1) != level
                }
            ) {
                return@withTransaction false
            }
        }

        // Read both epochs in one SELECT before any monthly-owned Player UPDATE. There is
        // intentionally no suppression: successful monthly writes advance footballRevision so a
        // different plan prepared in parallel becomes stale.
        val preparedRevision = plan.expectedPlayerRevision
        val currentRevision = if (preparedRevision == null) {
            null
        } else {
            repository.currentMonthlyEvolutionRevisionSnapshotOrNull()
        }

        var correctionIds: Set<Long> = emptySet()
        val compactCommitment = plan.expectedUniverseCommitment
        if (compactCommitment != null) {
            val exactEpochMatch =
                preparedRevision != null && currentRevision == preparedRevision
            val footballEpochMatch =
                preparedRevision != null &&
                    currentRevision != null &&
                    currentRevision.footballRevision == preparedRevision.footballRevision

            if (!exactEpochMatch) {
                val teams = currentTeamsById ?: repository.getAllTeams().associateBy { it.id }.also {
                    currentTeamsById = it
                }
                val validation = if (footballEpochMatch) {
                    repository.validateMonthlyEvolutionRosterRevisionOnly(
                        expected = compactCommitment,
                        expectedTrainingCenterLevels = plan.expectedTrainingCenterLevels,
                        currentTrainingCenterLevels =
                            teams.mapValues { it.value.trainingCenterLevel },
                        allowRosterCorrections = allowWeeklyRosterCorrections
                    )
                } else {
                    // Includes missing/tampered tracking and any football-input epoch change.
                    repository.validateMonthlyEvolutionUniverseCommitment(
                        expected = compactCommitment,
                        expectedTrainingCenterLevels = plan.expectedTrainingCenterLevels,
                        currentTrainingCenterLevels =
                            teams.mapValues { it.value.trainingCenterLevel },
                        allowRosterCorrections = allowWeeklyRosterCorrections
                    )
                }
                if (!validation.valid) return@withTransaction false
                correctionIds = validation.correctionIds
            }
        } else if (plan.expectedInputs.isNotEmpty()) {
            // Detailed/manual compatibility path keeps its existing explicit snapshot validation.
            if (!allowWeeklyRosterCorrections) {
                if (plan.expectedPlayerCount > 0 &&
                    repository.getMonthlyEvolutionPlayerCount() != plan.expectedPlayerCount
                ) {
                    return@withTransaction false
                }
                val currentInputs =
                    repository.getMonthlyEvolutionInputSnapshots(plan.expectedInputs.map { it.id })
                if (currentInputs.size != plan.expectedInputs.size ||
                    plan.expectedInputs.any { expected -> currentInputs[expected.id] != expected }
                ) {
                    return@withTransaction false
                }
            } else {
                val teams = currentTeamsById ?: repository.getAllTeams().associateBy { it.id }.also {
                    currentTeamsById = it
                }
                val validation = repository.validateMonthlyEvolutionRosterInputs(
                    expectedInputs = plan.expectedInputs,
                    expectedTrainingCenterLevels = plan.expectedTrainingCenterLevels,
                    currentTrainingCenterLevels = teams.mapValues { it.value.trainingCenterLevel }
                )
                if (!validation.valid) return@withTransaction false
                correctionIds = validation.correctionIds
            }
        }

        var playerStatesToPersist = plan.updatedPlayerStates
        var historyToPersist = plan.historyLogs

        if (correctionIds.isNotEmpty()) {
            val correctionPlayers = ArrayList<Player>(correctionIds.size)
            for (playerId in correctionIds) {
                val player = repository.getPlayer(playerId) ?: return@withTransaction false
                correctionPlayers.add(player)
            }
            correctionPlayers.sortWith(
                compareByDescending<Player> { it.force }
                    .thenBy { it.name }
                    .thenBy { it.id }
            )

            val teams = currentTeamsById ?: repository.getAllTeams().associateBy { it.id }
            val correctedResults = PlayerEvolutionMonthlyEngine.process(
                correctionPlayers,
                teams,
                plan.periodDate
            )
            val correctedPlayerStates = ArrayList<MonthlyEvolutionPlayerState>()
            val correctedHistory = ArrayList<HistoricoEvolucao>()
            for (result in correctedResults) {
                if (result.historyLogs.isNotEmpty() || result.netChange != 0.0) {
                    correctedPlayerStates.add(result.player.toMonthlyEvolutionPlayerState())
                }
                if (result.historyLogs.isNotEmpty()) correctedHistory.addAll(result.historyLogs)
            }

            playerStatesToPersist = buildList {
                addAll(plan.updatedPlayerStates.filter { it.id !in correctionIds })
                addAll(correctedPlayerStates)
            }
            historyToPersist = buildList {
                addAll(plan.historyLogs.filter { it.jogadorId !in correctionIds })
                addAll(correctedHistory)
            }
        }

        // Epoch comparison is already complete. These writes intentionally fire the football
        // trigger so any separately prepared plan sees a newer revision.
        repository.resetMonthlyEvolutionCounters()
        if (playerStatesToPersist.isNotEmpty()) {
            check(
                repository.applyMonthlyEvolutionPlayerStateDeltas(playerStatesToPersist) ==
                    playerStatesToPersist.size
            ) {
                "Falha fail-closed ao persistir delta de evolução mensal."
            }
        }
        if (historyToPersist.isNotEmpty()) {
            check(
                repository.insertMonthlyEvolutionHistoryRowsBulk(historyToPersist) ==
                    historyToPersist.size
            ) {
                "Falha fail-closed ao persistir histórico da evolução mensal."
            }
        }
        true
    }

    internal suspend fun executePreparedMonthlyEvolution(
        plan: MonthlyEvolutionPlan
    ): MonthlyEvolutionExecutionOutcome {
        val committed = commitMonthlyEvolution(plan)
        return if (committed) {
            MonthlyEvolutionExecutionOutcome(committed = true, results = plan.results)
        } else {
            MonthlyEvolutionExecutionOutcome(committed = false, results = emptyList())
        }
    }

    suspend fun executeMonthlyEvolutionDetailed(
        save: GameSave,
        periodDate: String
    ): MonthlyEvolutionExecutionOutcome {
        val plan = prepareMonthlyEvolution(save, periodDate, retainDetailedResults = true)
        return executePreparedMonthlyEvolution(plan)
    }

    suspend fun executeMonthlyEvolution(
        save: GameSave,
        periodDate: String
    ): List<PlayerEvolutionResult> =
        executeMonthlyEvolutionDetailed(save, periodDate).results

    suspend fun promoteYouthPlayer(
        save: GameSave,
        name: String,
        position: String,
        currentRosterSize: Int
    ): Pair<Boolean, String> {
        if (currentRosterSize >= 35) {
            return Pair(false, "Elenco principal já atingiu o limite de 35 atletas.")
        }

        val baseForce = Random.nextInt(52, 68)
        val potential = (baseForce + Random.nextInt(15, 28)).coerceAtMost(95)
        val age = Random.nextInt(16, 20)
        val youthPlayer = Player(
            teamId = save.playerTeamId,
            name = name,
            age = age,
            position = position,
            force = baseForce,
            potential = potential,
            moral = 85,
            energy = 100,
            contractDurationWeeks = 156
        )
        repository.savePlayers(listOf(youthPlayer))
        return Pair(
            true,
            "Jovem promessa ${youthPlayer.name} (${youthPlayer.position}, Força: ${youthPlayer.force}) promovido com sucesso!"
        )
    }

    suspend fun processPostMatchExperience(
        players: List<Player>,
        matchRatings: Map<Long, Double>
    ): List<Player> {
        val updated = PlayerEvolutionSystem.processPostMatchExperience(players, matchRatings)
        if (updated.isNotEmpty()) repository.updatePlayers(updated)
        return updated
    }
}
