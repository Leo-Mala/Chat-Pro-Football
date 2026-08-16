package com.example.usecase

import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.PlayerEvolutionResult
import com.example.data.PlayerEvolutionSystem
import kotlin.random.Random

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
     * Processa a evolução mensal dos atletas do clube e de toda a liga.
     */
    suspend fun executeMonthlyEvolution(
        save: GameSave,
        periodDate: String
    ): List<PlayerEvolutionResult> {
        val allPlayers = repository.getAllPlayers()
        val allTeams = repository.getAllTeams().associateBy { it.id }

        val evolutionResults = PlayerEvolutionSystem.processMonthlyEvolution(allPlayers, allTeams, periodDate)

        val updatedPlayers = evolutionResults.map { it.player }
        val allLogs = evolutionResults.flatMap { it.historyLogs }

        if (updatedPlayers.isNotEmpty()) {
            repository.updatePlayers(updatedPlayers)
        }
        if (allLogs.isNotEmpty()) {
            repository.saveHistoricoEvolucaoList(allLogs)
        }

        return evolutionResults
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
