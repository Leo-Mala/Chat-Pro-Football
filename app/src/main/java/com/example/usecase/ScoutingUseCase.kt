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
     * Compra um relatório de olheiros para um jogador específico.
     *
     * O preço cobrado é exatamente o anunciado na UI para cada nível
     * (R$ 50.000 / R$ 150.000 / R$ 400.000 nos níveis 1 / 3 / 5) e o nível
     * escolhido é gravado no estado de observação daquele jogador, que é o
     * que define a precisão exibida em [Player.getObservedForce]. Nenhum
     * reveal global é ativado por esta compra.
     */
    suspend fun buyScoutReport(save: GameSave, playerId: Long, level: Int): ScoutingResult =
        repository.withTransaction {
            val normalizedLevel = level.coerceIn(1, 5)
            val cost = scoutReportCost(normalizedLevel)
            val currentSave = repository.getGameSave() ?: save
            val target = repository.getPlayer(playerId)
                ?: return@withTransaction ScoutingResult.Error("Jogador não encontrado para o relatório de olheiros.")

            if (target.scoutedLevel >= normalizedLevel) {
                return@withTransaction ScoutingResult.Error(
                    "Você já possui um relatório deste nível ou superior para ${target.name}."
                )
            }

            if (currentSave.bankBalance < cost) {
                return@withTransaction ScoutingResult.Error(
                    "Saldo insuficiente para contratar olheiros. Custo: R$ %,d.".format(cost)
                )
            }

            repository.updatePlayer(target.copy(scoutedLevel = normalizedLevel))
            val updatedSave = currentSave.copy(bankBalance = currentSave.bankBalance - cost)
            repository.saveGameSave(updatedSave)
            repository.saveTransaction(
                TransactionRecord(
                    week = currentSave.currentWeek,
                    season = currentSave.currentSeason,
                    type = "MELHORIA_OLHEIROS",
                    description = "Relatório de Olheiros Nível %d (%s)".format(normalizedLevel, target.name),
                    amount = cost,
                    isIncome = false
                )
            )

            ScoutingResult.Success(
                updatedSave,
                "Relatório nível %d de ${target.name} contratado!".format(normalizedLevel)
            )
        }

    /**
     * Retorna a força observada de um atleta considerando o nível de olheiros.
     */
    fun getObservedForceRange(player: Player, isRevealed: Boolean): String {
        return player.getObservedForce(isRevealed)
    }

    companion object {
        private val SCOUT_REPORT_COSTS = mapOf(
            1 to 50_000L,
            2 to 100_000L,
            3 to 150_000L,
            4 to 275_000L,
            5 to 400_000L
        )

        fun scoutReportCost(level: Int): Long = SCOUT_REPORT_COSTS.getValue(level.coerceIn(1, 5))
    }
}
