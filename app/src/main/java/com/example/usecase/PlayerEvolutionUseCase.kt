package com.example.usecase

import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.HistoricoEvolucao
import com.example.data.Player
import com.example.data.PlayerEvolutionResult
import com.example.data.PlayerEvolutionSystem
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
                // Recupera entre 15 e 25 de energia por semana sem jogo, ou recupera stamina base
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

        if (updatedPlayers.isNotEmpty()) {
            repository.updatePlayers(updatedPlayers)
        }

        return updatedPlayers
    }

    /**
     * Executa somente a parte CPU-heavy da evolução mensal.
     *
     * Em uma carreira de ~60k jogadores este é o trecho dominante do fechamento mensal. Mantê-lo
     * fora da transação reduz o tempo de lock sem relaxar atomicidade: o commit abaixo valida o
     * snapshot de temporada/semana/clube antes de persistir qualquer alteração.
     *
     * O plano também elimina writes completos desnecessários: todos os jogadores continuam tendo
     * os contadores mensais resetados no commit, mas somente atletas com mudança real de atributos
     * ou força precisam de um @Update completo.
     */
    suspend fun prepareMonthlyEvolution(
        save: GameSave,
        periodDate: String
    ): MonthlyEvolutionPlan {
        val allPlayers = repository.getAllPlayers()
        val allTeams = repository.getAllTeams().associateBy { it.id }
        val evolutionResults = PlayerEvolutionSystem.processMonthlyEvolution(allPlayers, allTeams, periodDate)
        val changedPlayers = evolutionResults.asSequence()
            .filter { result -> result.historyLogs.isNotEmpty() || result.netChange != 0.0 }
            .map { it.player }
            .toList()

        return MonthlyEvolutionPlan(
            expectedSeason = save.currentSeason,
            expectedWeek = save.currentWeek,
            expectedPlayerTeamId = save.playerTeamId,
            results = evolutionResults,
            updatedPlayers = changedPlayers,
            historyLogs = evolutionResults.flatMap { it.historyLogs }
        )
    }

    /**
     * Persiste um plano já calculado como uma única unidade e rejeita plano stale antes do
     * primeiro write. Quando chamado de dentro de outra transação Room, a transação é reutilizada.
     *
     * A ordem é deliberada: primeiro os contadores mensais de todo o universo são zerados por um
     * único action set SQL; depois somente os jogadores que realmente evoluíram/declinaram recebem
     * o estado completo calculado (incluindo o `evolucaoMensal` não-zero quando aplicável).
     */
    suspend fun commitMonthlyEvolution(plan: MonthlyEvolutionPlan): Boolean = repository.withTransaction {
        val currentSave = repository.getGameSave() ?: return@withTransaction false
        if (currentSave.currentSeason != plan.expectedSeason ||
            currentSave.currentWeek != plan.expectedWeek ||
            currentSave.playerTeamId != plan.expectedPlayerTeamId
        ) {
            return@withTransaction false
        }

        repository.resetMonthlyEvolutionCounters()
        if (plan.updatedPlayers.isNotEmpty()) {
            repository.updatePlayers(plan.updatedPlayers)
        }
        if (plan.historyLogs.isNotEmpty()) {
            repository.saveHistoricoEvolucaoList(plan.historyLogs)
        }
        true
    }

    /**
     * API canônica para evolução mensal manual. O cálculo pesado acontece antes da transação e o
     * commit é fail-closed caso o save tenha mudado enquanto o plano era calculado.
     */
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

    /**
     * Promove um jovem da categoria de base para o elenco profissional.
     */
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
        return Pair(true, "Jovem promessa ${youthPlayer.name} (${youthPlayer.position}, Força: ${youthPlayer.force}) promovido com sucesso!")
    }

    /**
     * Processa a experiência de pós-partida dos atletas e atualiza a persistência.
     */
    suspend fun processPostMatchExperience(
        players: List<Player>,
        matchRatings: Map<Long, Double>
    ): List<Player> {
        val updated = PlayerEvolutionSystem.processPostMatchExperience(players, matchRatings)
        if (updated.isNotEmpty()) {
            repository.updatePlayers(updated)
        }
        return updated
    }
}
