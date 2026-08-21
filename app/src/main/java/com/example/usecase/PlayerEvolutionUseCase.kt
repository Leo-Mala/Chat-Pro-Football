package com.example.usecase

import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.HistoricoEvolucao
import com.example.data.Player
import com.example.data.PlayerEvolutionMonthlyEngine
import com.example.data.PlayerEvolutionResult
import com.example.data.PlayerEvolutionSystem
import com.example.data.getMonthlyEvolutionHistoryFingerprints
import com.example.data.monthlyEvolutionFingerprint
import com.example.data.resetMonthlyEvolutionCounters
import kotlin.random.Random

/**
 * Immutable monthly-evolution plan. The expensive world-player calculation is intentionally
 * separated from the Room commit so callers that already own a larger atomic operation can do
 * CPU work before acquiring the database transaction and then fail closed if the save moved.
 */
data class MonthlyEvolutionPlan(
    val expectedSeason: Int,
    val expectedWeek: Int,
    val expectedPlayerTeamId: Long,
    val periodDate: String,
    val results: List<PlayerEvolutionResult>,
    /** Only players whose persisted football state changed; monthly counters are reset by SQL. */
    val updatedPlayers: List<Player>,
    val historyLogs: List<HistoricoEvolucao>
)

/**
 * UseCase responsável pela recuperação física, evolução mensal, gestão de lesões,
 * suspensões por cartão e renovação de contratos de atletas.
 */
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

    /** CPU-heavy monthly planning, intentionally outside the Room transaction. */
    suspend fun prepareMonthlyEvolution(
        save: GameSave,
        periodDate: String
    ): MonthlyEvolutionPlan {
        val allPlayers = repository.getAllPlayers()
        val allTeams = repository.getAllTeams().associateBy { it.id }
        val evolutionResults = PlayerEvolutionMonthlyEngine.process(allPlayers, allTeams, periodDate)

        val changedPlayers = ArrayList<Player>()
        val historyLogs = ArrayList<HistoricoEvolucao>()
        for (result in evolutionResults) {
            if (result.historyLogs.isNotEmpty() || result.netChange != 0.0) changedPlayers.add(result.player)
            if (result.historyLogs.isNotEmpty()) historyLogs.addAll(result.historyLogs)
        }

        return MonthlyEvolutionPlan(
            expectedSeason = save.currentSeason,
            expectedWeek = save.currentWeek,
            expectedPlayerTeamId = save.playerTeamId,
            periodDate = periodDate,
            results = evolutionResults,
            updatedPlayers = changedPlayers,
            historyLogs = historyLogs
        )
    }

    /**
     * Persists one prepared plan atomically and fail-closed against stale save state.
     *
     * Retrying the exact same plan is safe: counters and Player updates are naturally idempotent,
     * while evolution-history fingerprints already present for [MonthlyEvolutionPlan.periodDate]
     * are filtered before insert, preventing duplicate auto-generated audit rows.
     */
    suspend fun commitMonthlyEvolution(plan: MonthlyEvolutionPlan): Boolean = repository.withTransaction {
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
        val historyToInsert = if (existingHistory.isEmpty()) {
            plan.historyLogs
        } else {
            plan.historyLogs.filter { it.monthlyEvolutionFingerprint() !in existingHistory }
        }

        repository.resetMonthlyEvolutionCounters()
        if (plan.updatedPlayers.isNotEmpty()) repository.updatePlayers(plan.updatedPlayers)
        if (historyToInsert.isNotEmpty()) repository.saveHistoricoEvolucaoList(historyToInsert)
        true
    }

    /** Canonical monthly evolution API. */
    suspend fun executeMonthlyEvolution(
        save: GameSave,
        periodDate: String
    ): List<PlayerEvolutionResult> {
        val plan = prepareMonthlyEvolution(save, periodDate)
        check(commitMonthlyEvolution(plan)) {
            "O estado da carreira mudou durante o cálculo da evolução mensal; plano descartado."
        }
        return plan.results
    }

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
