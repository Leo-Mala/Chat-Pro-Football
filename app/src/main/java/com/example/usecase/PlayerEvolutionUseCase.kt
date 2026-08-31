package com.example.usecase

import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.HistoricoEvolucao
import com.example.data.MonthlyEvolutionInputSnapshot
import com.example.data.Player
import com.example.data.PlayerEvolutionMonthlyEngine
import com.example.data.PlayerEvolutionResult
import com.example.data.PlayerEvolutionSystem
import com.example.data.Team
import com.example.data.applyMonthlyEvolutionPlayerStates
import com.example.data.getAllMonthlyEvolutionInputSnapshots
import com.example.data.getMonthlyEvolutionHistoryFingerprints
import com.example.data.getMonthlyEvolutionInputSnapshots
import com.example.data.getMonthlyEvolutionPlayerCount
import com.example.data.monthlyEvolutionFingerprint
import com.example.data.resetMonthlyEvolutionCounters
import com.example.data.toMonthlyEvolutionInputSnapshot
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
    /** Lightweight snapshots of every evolution input, used only for stale-plan validation. */
    val expectedInputs: List<MonthlyEvolutionInputSnapshot> = emptyList(),
    /** Exact universe size at preparation; detects players inserted after a standalone plan. */
    val expectedPlayerCount: Int = expectedInputs.size,
    /** Training-center level influences evolution and therefore participates in stale validation. */
    val expectedTrainingCenterLevels: Map<Long, Int> = emptyMap()
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
private const val MONTHLY_EVOLUTION_BATCH_SIZE = 512

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

            val newInjury = if (player.injuryWeeksRemaining > 0) player.injuryWeeksRemaining - 1 else 0
            val newSuspension = if (player.suspensionWeeksRemaining > 0) player.suspensionWeeksRemaining - 1 else 0

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
        val expectedPlayerCount = repository.getMonthlyEvolutionPlayerCount()
        val allTeams = repository.getAllTeams().associateBy { it.id }
        val evolutionResults = ArrayList<PlayerEvolutionResult>(
            if (retainDetailedResults) expectedPlayerCount else minOf(expectedPlayerCount, 4096)
        )
        val changedPlayers = ArrayList<Player>()
        val historyLogs = ArrayList<HistoricoEvolucao>()
        val expectedInputs = ArrayList<MonthlyEvolutionInputSnapshot>(expectedPlayerCount)
        val referencedTeamIds = HashSet<Long>()

        // Keep the exact ORDER BY force DESC, name ASC and call the same evolution engine in
        // sequence. Kotlin Random.Default therefore sees the same uninterrupted call sequence;
        // only the lifetime of each full Player batch changes.
        var offset = 0
        while (offset < expectedPlayerCount) {
            val batch = repository.getAllPlayersBatch(MONTHLY_EVOLUTION_BATCH_SIZE, offset)
            check(batch.isNotEmpty()) {
                "Monthly evolution player scan ended at $offset of $expectedPlayerCount rows."
            }
            val batchResults = if (retainDetailedResults) {
                PlayerEvolutionMonthlyEngine.process(batch, allTeams, periodDate)
            } else {
                PlayerEvolutionMonthlyEngine.processChanged(batch, allTeams, periodDate)
            }
            evolutionResults.addAll(batchResults)

            for (result in batchResults) {
                if (result.historyLogs.isNotEmpty() || result.netChange != 0.0) changedPlayers.add(result.player)
                if (result.historyLogs.isNotEmpty()) historyLogs.addAll(result.historyLogs)
            }
            for (player in batch) {
                expectedInputs.add(player.toMonthlyEvolutionInputSnapshot())
                player.teamId?.let(referencedTeamIds::add)
            }
            offset += batch.size
        }
        check(expectedInputs.size == expectedPlayerCount) {
            "Monthly evolution expected $expectedPlayerCount inputs but captured ${expectedInputs.size}."
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
            updatedPlayers = changedPlayers,
            historyLogs = historyLogs,
            expectedInputs = expectedInputs,
            expectedPlayerCount = expectedPlayerCount,
            expectedTrainingCenterLevels = expectedTrainingLevels
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
     * emergency players after the 60k plan was prepared. Instead of rerunning the whole universe
     * while Room is locked, the commit scans a lightweight projection and recalculates only players
     * whose effective training-center level changed plus newly inserted players. Any mutation of an
     * actual football input (attributes, force, minutes, rating, focus, age, potential, position)
     * still fails closed and rolls the weekly transaction back.
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
            val plannedFingerprints = plan.historyLogs.mapTo(hashSetOf()) { it.monthlyEvolutionFingerprint() }
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

        var correctionIds: Set<Long> = emptySet()
        if (plan.expectedInputs.isNotEmpty()) {
            if (!allowWeeklyRosterCorrections) {
                if (plan.expectedPlayerCount > 0 &&
                    repository.getMonthlyEvolutionPlayerCount() != plan.expectedPlayerCount
                ) {
                    return@withTransaction false
                }
                val currentInputs = repository.getMonthlyEvolutionInputSnapshots(plan.expectedInputs.map { it.id })
                if (currentInputs.size != plan.expectedInputs.size ||
                    plan.expectedInputs.any { expected -> currentInputs[expected.id] != expected }
                ) {
                    return@withTransaction false
                }
            } else {
                val currentInputs = repository.getAllMonthlyEvolutionInputSnapshots()
                val expectedById = plan.expectedInputs.associateBy { it.id }

                if (expectedById.keys.any { it !in currentInputs }) return@withTransaction false

                val teams = currentTeamsById ?: repository.getAllTeams().associateBy { it.id }.also {
                    currentTeamsById = it
                }
                val corrections = linkedSetOf<Long>()

                for ((playerId, expected) in expectedById) {
                    val current = currentInputs.getValue(playerId)
                    if (!expected.sameEvolutionStateIgnoringTeam(current)) {
                        return@withTransaction false
                    }
                    if (expected.teamId != current.teamId) {
                        val oldLevel = expected.teamId?.let { plan.expectedTrainingCenterLevels[it] } ?: 1
                        val newLevel = current.teamId?.let { teams[it]?.trainingCenterLevel } ?: 1
                        if (oldLevel != newLevel) corrections.add(playerId)
                    }
                }

                for (playerId in currentInputs.keys) {
                    if (playerId !in expectedById) corrections.add(playerId)
                }
                correctionIds = corrections
            }
        }

        var playersToPersist = plan.updatedPlayers
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
            val correctedUpdatedPlayers = ArrayList<Player>()
            val correctedHistory = ArrayList<HistoricoEvolucao>()
            for (result in correctedResults) {
                if (result.historyLogs.isNotEmpty() || result.netChange != 0.0) {
                    correctedUpdatedPlayers.add(result.player)
                }
                if (result.historyLogs.isNotEmpty()) correctedHistory.addAll(result.historyLogs)
            }

            playersToPersist = buildList {
                addAll(plan.updatedPlayers.filter { it.id !in correctionIds })
                addAll(correctedUpdatedPlayers)
            }
            historyToPersist = buildList {
                addAll(plan.historyLogs.filter { it.jogadorId !in correctionIds })
                addAll(correctedHistory)
            }
        }

        repository.resetMonthlyEvolutionCounters()
        if (playersToPersist.isNotEmpty()) {
            check(repository.applyMonthlyEvolutionPlayerStates(playersToPersist) == playersToPersist.size) {
                "Falha fail-closed ao persistir delta de evolução mensal."
            }
        }
        if (historyToPersist.isNotEmpty()) repository.saveHistoricoEvolucaoList(historyToPersist)
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
    ): List<PlayerEvolutionResult> = executeMonthlyEvolutionDetailed(save, periodDate).results

    suspend fun promoteYouthPlayer(
        save: GameSave,
        name: String,
        position: String,
        currentRosterSize: Int
    ): Pair<Boolean, String> {
        if (currentRosterSize >= 35) return Pair(false, "Elenco principal já atingiu o limite de 35 atletas.")

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
        return Pair(true, "Jovem promessa ${youthPlayer.name} (${youthPlayer.position}, Força: ${youthPlayer.force}) promovido com sucesso!")
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
