package com.example.usecase

import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.PlayerLoan

/**
 * Owns explicit player-loan return transitions for gameplay loans. The return is save-slot scoped by
 * [GameRepository] and executes inside one Room transaction. Permanent market transfers keep their
 * close operation in ProcessTransfersUseCase so finances, contract, roster and loan share one commit.
 */
class LoanLifecycleUseCase(private val repository: GameRepository) {

    sealed interface Result {
        data class Returned(val player: Player, val loan: PlayerLoan) : Result
        data class AlreadyClosed(val player: Player?) : Result
        data class Rejected(val reason: String) : Result
    }

    suspend fun returnToOwner(playerId: Long): Result = repository.withTransaction {
        if (playerId <= 0L) return@withTransaction Result.Rejected("playerId inválido")
        val player = repository.getPlayer(playerId)
            ?: return@withTransaction Result.Rejected("Jogador não encontrado")
        val loan = repository.getActiveLoanForPlayer(playerId)
            ?: return@withTransaction Result.AlreadyClosed(player)

        if (loan.ownerTeamId <= 0L || loan.borrowerTeamId <= 0L || loan.ownerTeamId == loan.borrowerTeamId) {
            return@withTransaction Result.Rejected("Empréstimo ativo possui referência inválida")
        }
        if (!player.isOnLoan ||
            player.teamId != loan.borrowerTeamId ||
            player.originalTeamId != loan.ownerTeamId
        ) {
            return@withTransaction Result.Rejected("Estado Player/PlayerLoan inconsistente; retorno recusado por segurança")
        }
        if (repository.getTeam(loan.ownerTeamId) == null) {
            return@withTransaction Result.Rejected("Clube proprietário não existe neste save")
        }

        val returnedPlayer = if (player.contractDurationWeeks <= 0) {
            player.copy(
                teamId = null,
                originalTeamId = null,
                isOnLoan = false,
                loanWeeksRemaining = 0,
                isStarter = false,
                salary = 0L
            )
        } else {
            player.copy(
                teamId = loan.ownerTeamId,
                originalTeamId = null,
                isOnLoan = false,
                loanWeeksRemaining = 0,
                isStarter = false
            )
        }
        val completedLoan = loan.copy(status = "COMPLETED", remainingWeeks = 0)
        repository.updatePlayer(returnedPlayer)
        repository.updateLoan(completedLoan)
        Result.Returned(returnedPlayer, completedLoan)
    }
}
