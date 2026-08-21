package com.example.usecase

import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.PlayerLoan

/**
 * Owns player-loan lifecycle transitions for both gameplay loans and FC26 snapshot loans.
 *
 * FC26 loans may have no trustworthy end date. Those rows remain ACTIVE until an explicit career
 * event closes them; this class never invents a duration. All state transitions are save-slot scoped
 * by [GameRepository] and execute inside a single Room transaction.
 */
class LoanLifecycleUseCase(private val repository: GameRepository) {

    sealed interface Result {
        data class Returned(val player: Player, val loan: PlayerLoan) : Result
        data class ClosedForPermanentTransfer(val player: Player, val loan: PlayerLoan) : Result
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

    /**
     * Atomically closes the active loan while moving ownership/roster to a permanent buyer.
     * Financial and contract mutations that are part of a market purchase must be executed by the
     * caller inside the same outer repository transaction before publishing success.
     */
    suspend fun closeForPermanentTransfer(playerId: Long, newOwnerTeamId: Long): Result =
        repository.withTransaction {
            if (playerId <= 0L || newOwnerTeamId <= 0L) {
                return@withTransaction Result.Rejected("Referência de transferência inválida")
            }
            if (repository.getTeam(newOwnerTeamId) == null) {
                return@withTransaction Result.Rejected("Novo clube proprietário não existe")
            }
            val player = repository.getPlayer(playerId)
                ?: return@withTransaction Result.Rejected("Jogador não encontrado")
            val loan = repository.getActiveLoanForPlayer(playerId)
                ?: return@withTransaction Result.AlreadyClosed(player)
            if (loan.ownerTeamId <= 0L || loan.borrowerTeamId <= 0L || loan.ownerTeamId == loan.borrowerTeamId) {
                return@withTransaction Result.Rejected("Empréstimo ativo possui referência inválida")
            }

            val transferred = player.copy(
                teamId = newOwnerTeamId,
                originalTeamId = null,
                isOnLoan = false,
                loanWeeksRemaining = 0,
                isStarter = false
            )
            val completedLoan = loan.copy(status = "COMPLETED", remainingWeeks = 0)
            repository.updatePlayer(transferred)
            repository.updateLoan(completedLoan)
            Result.ClosedForPermanentTransfer(transferred, completedLoan)
        }
}
