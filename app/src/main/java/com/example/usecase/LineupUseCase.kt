package com.example.usecase

import com.example.data.GameRepository
import com.example.data.Player

/**
 * Mutations de escalação isoladas do ViewModel.
 *
 * Toda decisão de domínio e persistência acontece dentro da transação. O chamador recebe apenas
 * um resultado imutável depois que a transação terminou, podendo então publicar feedback de UI
 * sem risco de anunciar sucesso antes do commit.
 */
class LineupUseCase(private val repository: GameRepository) {

    sealed interface Result {
        data class Success(val message: String) : Result
        data class Rejected(val message: String) : Result
    }

    suspend fun setPlayerStarter(playerId: Long, isStarter: Boolean): Result =
        repository.withTransaction {
            val player = repository.getPlayer(playerId)
                ?: return@withTransaction Result.Rejected("Jogador não encontrado.")
            val teamId = player.teamId
                ?: return@withTransaction Result.Rejected("Free agent não pode ser escalado.")
            val roster = repository.getPlayersByTeam(teamId)
            val currentStarters = roster.filter { it.isStarter }

            if (!isStarter) {
                if (!player.isStarter) {
                    return@withTransaction Result.Success("${player.name} já está no banco.")
                }
                if (player.position == GOALKEEPER) {
                    val startingGoalkeepers = currentStarters.count { it.position == GOALKEEPER }
                    if (startingGoalkeepers <= 1) {
                        return@withTransaction Result.Rejected(
                            "O time deve jogar com exatamente 1 goleiro titular!"
                        )
                    }
                }
                repository.updatePlayer(player.copy(isStarter = false))
                return@withTransaction Result.Success("${player.name} foi para o banco.")
            }

            if (player.isStarter) {
                return@withTransaction Result.Success("${player.name} já está entre os titulares.")
            }

            val updates = mutableListOf<Player>()
            val message: String

            if (player.position == GOALKEEPER) {
                val currentGoalkeeper = currentStarters.firstOrNull { it.position == GOALKEEPER }
                if (currentGoalkeeper != null) {
                    updates += currentGoalkeeper.copy(isStarter = false)
                    message = "Goleiro titular alterado!"
                } else {
                    if (currentStarters.size >= STARTERS_LIMIT) {
                        val lowestField = currentStarters
                            .filter { it.position != GOALKEEPER }
                            .minByOrNull { it.force }
                        if (lowestField != null) {
                            updates += lowestField.copy(isStarter = false)
                        }
                    }
                    message = "${player.name} escalado como goleiro titular!"
                }
            } else {
                val currentFieldStarters = currentStarters.filter { it.position != GOALKEEPER }
                if (currentFieldStarters.size >= FIELD_STARTERS_LIMIT) {
                    val lowestField = currentFieldStarters.minByOrNull { it.force }
                    if (lowestField != null) {
                        updates += lowestField.copy(isStarter = false)
                        message = "${player.name} escalado! ${lowestField.name} foi para o banco."
                    } else {
                        message = "${player.name} escalado!"
                    }
                } else if (currentStarters.size >= STARTERS_LIMIT) {
                    val lowest = currentStarters.minByOrNull { it.force }
                    if (lowest != null) {
                        updates += lowest.copy(isStarter = false)
                    }
                    message = "${player.name} escalado!"
                } else {
                    message = "${player.name} escalado!"
                }
            }

            updates += player.copy(isStarter = true)
            repository.updatePlayers(updates)
            Result.Success(message)
        }

    suspend fun swapPlayers(starterId: Long, benchId: Long): Result =
        repository.withTransaction {
            if (starterId == benchId) {
                return@withTransaction Result.Rejected("Selecione jogadores diferentes.")
            }

            val starter = repository.getPlayer(starterId)
                ?: return@withTransaction Result.Rejected("Titular não encontrado.")
            val bench = repository.getPlayer(benchId)
                ?: return@withTransaction Result.Rejected("Reserva não encontrado.")

            if (starter.teamId == null || starter.teamId != bench.teamId) {
                return@withTransaction Result.Rejected("Os jogadores precisam pertencer ao mesmo clube.")
            }
            if (!starter.isStarter || bench.isStarter) {
                return@withTransaction Result.Rejected("A troca exige um titular e um reserva.")
            }

            val roster = repository.getPlayersByTeam(starter.teamId)
            val updates = mutableListOf<Player>()
            val message: String

            if (bench.position == GOALKEEPER) {
                roster.firstOrNull {
                    it.isStarter && it.id != starter.id && it.position == GOALKEEPER
                }?.let { otherGoalkeeper ->
                    updates += otherGoalkeeper.copy(isStarter = false)
                }
                message = "Goleiro titular alterado!"
            } else if (starter.position == GOALKEEPER) {
                val otherGoalkeeper = roster.firstOrNull {
                    it.isStarter && it.id != starter.id && it.position == GOALKEEPER
                }
                if (otherGoalkeeper == null) {
                    return@withTransaction Result.Rejected(
                        "Não é permitido jogar sem um goleiro titular!"
                    )
                }
                message = "Substituição realizada!"
            } else {
                message = "Substituição realizada!"
            }

            updates += starter.copy(isStarter = false)
            updates += bench.copy(isStarter = true)
            repository.updatePlayers(updates)
            Result.Success(message)
        }

    private companion object {
        const val GOALKEEPER = "GOL"
        const val STARTERS_LIMIT = 11
        const val FIELD_STARTERS_LIMIT = 10
    }
}
