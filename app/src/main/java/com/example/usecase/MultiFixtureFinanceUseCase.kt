package com.example.usecase

import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.TransactionRecord

/**
 * Adapta o fechamento financeiro semanal para semanas com mais de uma partida em casa.
 *
 * A rotina base continua sendo executada exatamente uma vez, portanto salários,
 * patrocínio, sócios, juros, parcelas, academia e empréstimos não são duplicados.
 * Somente a bilheteria dos jogos em casa adicionais é creditada depois do fechamento base.
 */
class MultiFixtureFinanceUseCase(
    private val repository: GameRepository,
    private val financeUseCase: FinanceUseCase
) {

    suspend fun processWeeklyFinances(
        save: GameSave,
        homeMatchCount: Int,
        userPlayers: List<Player> = emptyList()
    ): GameSave {
        val normalizedHomeMatchCount = homeMatchCount.coerceAtLeast(0)
        var updatedSave = financeUseCase.processWeeklyFinances(
            save = save,
            isHomeMatch = normalizedHomeMatchCount > 0,
            userPlayers = userPlayers
        )

        val additionalHomeMatches = (normalizedHomeMatchCount - 1).coerceAtLeast(0)
        if (additionalHomeMatches == 0) return updatedSave

        val revenuePerAdditionalMatch = calculateTicketRevenuePerHomeMatch(updatedSave)
        val additionalRevenue = revenuePerAdditionalMatch * additionalHomeMatches.toLong()
        if (additionalRevenue <= 0L) return updatedSave

        updatedSave = repository.withTransaction {
            val persisted = repository.getGameSave() ?: updatedSave
            val credited = persisted.copy(
                bankBalance = persisted.bankBalance + additionalRevenue
            )
            repository.saveGameSave(credited)
            repository.saveTransaction(
                TransactionRecord(
                    week = persisted.currentWeek,
                    season = persisted.currentSeason,
                    type = "BILHETERIA_ADICIONAL",
                    description = "Bilheteria de $additionalHomeMatches jogo(s) adicional(is) em casa",
                    amount = additionalRevenue,
                    isIncome = true
                )
            )
            credited
        }

        return updatedSave
    }

    companion object {
        fun calculateTicketRevenuePerHomeMatch(save: GameSave): Long {
            val baseAttendanceRate = 0.4 + (save.coachReputation / 200.0)
            val priceFactor = (1.5 - ((save.ticketPrice - 30.0) * 0.012)).coerceIn(0.35, 1.5)
            val estimatedAttendance = (save.stadiumCapacity * baseAttendanceRate * priceFactor).toInt()
                .coerceIn(1000, save.stadiumCapacity)
            return (estimatedAttendance * save.ticketPrice).toLong()
        }
    }
}
