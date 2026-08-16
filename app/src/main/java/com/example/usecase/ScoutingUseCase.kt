package com.example.usecase

import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.TransactionRecord

/**
 * UseCase responsável pela rede de olheiros (Scouting) e observação de jogadores.
 */
class ScoutingUseCase(private val repository: GameRepository) {

    sealed class ScoutingResult {
        data class Success(val updatedSave: GameSave, val message: String) : ScoutingResult()
        data class Error(val reason: String) : ScoutingResult()
    }

    /**
     * Eleva a cobertura de olheiros globais por um período de semanas.
     */
    suspend fun buyGlobalScoutReveal(save: GameSave, weeks: Int): ScoutingResult {
        val cost = weeks * 150000L
        if (save.bankBalance < cost) {
            return ScoutingResult.Error("Saldo insuficiente para contratar olheiros. Custo: R$ %,d.".format(cost))
        }

        val updatedSave = save.copy(
            globalScoutRevealWeeksRemaining = save.globalScoutRevealWeeksRemaining + weeks,
            bankBalance = save.bankBalance - cost
        )

        repository.saveGameSave(updatedSave)
        repository.saveTransaction(
            TransactionRecord(
                week = save.currentWeek,
                season = save.currentSeason,
                type = "MELHORIA_OLHEIROS",
                description = "Contratação de Rede de Olheiros (%d semanas)".format(weeks),
                amount = cost,
                isIncome = false
            )
        )

        return ScoutingResult.Success(updatedSave, "Rede de olheiros contratada por +%d semanas!".format(weeks))
    }

    /**
     * Retorna a força observada de um atleta considerando o nível de olheiros.
     */
    fun getObservedForceRange(player: Player, isRevealed: Boolean): String {
        return player.getObservedForce(isRevealed)
    }
}
