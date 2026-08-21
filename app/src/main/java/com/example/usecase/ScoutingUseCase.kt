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
     * A cobrança, o novo estado do save e o histórico financeiro são uma única unidade atômica.
     */
    suspend fun buyGlobalScoutReveal(save: GameSave, weeks: Int): ScoutingResult = repository.withTransaction {
        if (weeks <= 0) {
            return@withTransaction ScoutingResult.Error("Duração da rede de olheiros inválida.")
        }

        val currentSave = repository.getGameSave() ?: save
        val cost = weeks * 150000L
        if (currentSave.bankBalance < cost) {
            return@withTransaction ScoutingResult.Error("Saldo insuficiente para contratar olheiros. Custo: R$ %,d.".format(cost))
        }

        val updatedSave = currentSave.copy(
            globalScoutRevealWeeksRemaining = currentSave.globalScoutRevealWeeksRemaining + weeks,
            bankBalance = currentSave.bankBalance - cost
        )

        repository.saveGameSave(updatedSave)
        repository.saveTransaction(
            TransactionRecord(
                week = currentSave.currentWeek,
                season = currentSave.currentSeason,
                type = "MELHORIA_OLHEIROS",
                description = "Contratação de Rede de Olheiros (%d semanas)".format(weeks),
                amount = cost,
                isIncome = false
            )
        )

        ScoutingResult.Success(updatedSave, "Rede de olheiros contratada por +%d semanas!".format(weeks))
    }

    /**
     * Retorna a força observada de um atleta considerando o nível de olheiros.
     */
    fun getObservedForceRange(player: Player, isRevealed: Boolean): String {
        return player.getObservedForce(isRevealed)
    }
}
