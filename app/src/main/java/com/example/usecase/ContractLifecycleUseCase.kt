package com.example.usecase

import com.example.data.GameRepository
import com.example.data.Player

/**
 * Owns player-contract mutations that used to live in the ViewModel layer.
 *
 * The repository is save-slot scoped by the caller. All validation and persistence happen inside
 * the same Room transaction so a team switch or player move cannot make a previously-read UI
 * snapshot overwrite the current contract state.
 */
class ContractLifecycleUseCase(private val repository: GameRepository) {

    sealed interface RenewalResult {
        data class Success(val player: Player, val message: String) : RenewalResult
        data class Rejected(val reason: String) : RenewalResult
        data object Unavailable : RenewalResult
    }

    suspend fun renewPlayerContract(
        playerId: Long,
        durationWeeks: Int = 52
    ): RenewalResult = repository.withTransaction {
        if (durationWeeks <= 0) {
            return@withTransaction RenewalResult.Rejected("Duração da renovação inválida!")
        }

        val save = repository.getGameSave() ?: return@withTransaction RenewalResult.Unavailable
        val freshPlayer = repository.getPlayer(playerId)
            ?: return@withTransaction RenewalResult.Rejected("Jogador não encontrado no banco de dados!")

        if (freshPlayer.teamId != save.playerTeamId && freshPlayer.originalTeamId != save.playerTeamId) {
            return@withTransaction RenewalResult.Rejected("O jogador não pertence ao seu clube!")
        }
        if (freshPlayer.teamId == null && !freshPlayer.isOnLoan) {
            return@withTransaction RenewalResult.Rejected("Agentes livres não podem ter contratos renovados!")
        }

        val updatedPlayer = freshPlayer.copy(
            contractDurationWeeks = freshPlayer.contractDurationWeeks + durationWeeks,
            salary = (freshPlayer.salary * 1.1).toLong().coerceAtLeast(3000L)
        )
        repository.updatePlayer(updatedPlayer)

        RenewalResult.Success(
            player = updatedPlayer,
            message = "Contrato de ${freshPlayer.name} renovado por mais $durationWeeks semanas!"
        )
    }
}
