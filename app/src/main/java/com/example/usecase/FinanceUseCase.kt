package com.example.usecase

import com.example.data.Fc26LoanPolicy
import com.example.data.GameCalendar
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.TransactionRecord

/**
 * UseCase responsável por toda a gestão financeira do clube, incluindo empréstimos,
 * expansão de estádio, receitas de bilheteria e patrocinadores, e despesas.
 */
class FinanceUseCase(private val repository: GameRepository) {

    companion object {
        const val BANK_LOAN_WEEKLY_INTEREST_RATE = 0.002

        fun calcNextDueDate(currentSeason: Int, currentWeek: Int, deltaWeeks: Int = 1): Pair<Int, Int> {
            return GameCalendar.advanceWeeks(currentSeason, currentWeek, deltaWeeks)
        }
    }

    sealed class FinanceResult {
        data class Success(val updatedSave: GameSave, val message: String) : FinanceResult()
        data class Error(val reason: String) : FinanceResult()
    }

    suspend fun processWeeklyFinances(
        save: GameSave,
        isHomeMatch: Boolean,
        userPlayers: List<com.example.data.Player> = emptyList()
    ): GameSave {
        if (!isHomeMatch) {
            return processWeeklyFinances(save, homeMatchCount = 0, userPlayers = userPlayers)
        }

        val persistedSave = repository.getGameSave() ?: save
        val playedHomeMatches = repository.getFixturesForWeek(
            persistedSave.currentSeason,
            persistedSave.currentWeek
        ).count { fixture ->
            fixture.isPlayed && fixture.homeTeamId == persistedSave.playerTeamId
        }

        return processWeeklyFinances(
            save = save,
            homeMatchCount = playedHomeMatches.coerceAtLeast(1),
            userPlayers = userPlayers
        )
    }

    suspend fun processWeeklyFinances(
        save: GameSave,
        homeMatchCount: Int,
        userPlayers: List<com.example.data.Player> = emptyList()
    ): GameSave = repository.withTransaction {
        val currentSave = repository.getGameSave() ?: save
        val safeHomeMatchCount = homeMatchCount.coerceAtLeast(0)

        val effectiveSocios = currentSave.socioTorcedoresCount.toLong().coerceAtLeast(currentSave.coachReputation * 150L)
        val socioRevenue = (effectiveSocios * 30.0).toLong()

        var sponsorName = currentSave.sponsorName
        var sponsorWeekly = currentSave.sponsorWeekly
        var sponsorWeeksRemaining = currentSave.sponsorWeeksRemaining
        val sponsorRevenue: Long

        if (sponsorWeeksRemaining > 0) {
            sponsorRevenue = sponsorWeekly
            sponsorWeeksRemaining--
            if (sponsorWeeksRemaining == 0) {
                sponsorName = "Nenhum"
                sponsorWeekly = 0L
            }
        } else {
            sponsorRevenue = (300000.0 + (currentSave.coachReputation * 15000.0)).toLong()
        }

        var ticketRevenue = 0L
        if (safeHomeMatchCount > 0) {
            val baseAttendanceRate = 0.4 + (currentSave.coachReputation / 200.0)
            val priceFactor = (1.5 - ((currentSave.ticketPrice - 30.0) * 0.012)).coerceIn(0.35, 1.5)
            val estimatedAttendance = (currentSave.stadiumCapacity * baseAttendanceRate * priceFactor).toInt()
                .coerceIn(1000, currentSave.stadiumCapacity)
            val ticketRevenuePerMatch = (estimatedAttendance * currentSave.ticketPrice).toLong()
            ticketRevenue = ticketRevenuePerMatch * safeHomeMatchCount.toLong()
        }

        val totalWeeklyIncome = socioRevenue + sponsorRevenue + ticketRevenue
        val roster = if (userPlayers.isNotEmpty()) userPlayers else repository.getPlayersByTeam(currentSave.playerTeamId)
        val playerSalaries = roster.sumOf { it.salary }.coerceAtLeast(30000L)
        val loanInterest = if (currentSave.loanAmount > 0L) (currentSave.loanAmount * BANK_LOAN_WEEKLY_INTEREST_RATE).toLong() else 0L
        val maintenanceCost = currentSave.stadiumCapacity * 2L
        val academyCost = currentSave.academyWeeklyInvestment.coerceAtLeast(0L)

        var totalInstallmentPaid = 0L
        var totalInstallmentReceived = 0L
        val activeInstallments = repository.getActiveInstallmentsForWeek(currentSave.currentSeason, currentSave.currentWeek)
        val updatedInstallments = mutableListOf<com.example.data.TransferInstallment>()

        for (inst in activeInstallments) {
            val rem = inst.remainingInstallments - 1
            val (nextSeason, nextWeek) = calcNextDueDate(currentSave.currentSeason, currentSave.currentWeek, 1)
            val newStatus = if (rem <= 0) "COMPLETED" else "ACTIVE"

            if (inst.buyerTeamId == currentSave.playerTeamId) {
                totalInstallmentPaid += inst.installmentAmount
                updatedInstallments += inst.copy(
                    remainingInstallments = rem,
                    nextDueWeek = nextWeek,
                    season = nextSeason,
                    status = newStatus
                )
            } else if (inst.sellerTeamId == currentSave.playerTeamId) {
                totalInstallmentReceived += inst.installmentAmount
                updatedInstallments += inst.copy(
                    remainingInstallments = rem,
                    nextDueWeek = nextWeek,
                    season = nextSeason,
                    status = newStatus
                )
            }
        }

        // Não usamos getAllPlayers() nem lookup por jogador no caminho saudável do snapshot.
        // Agrupamos pelos borrowers ativos e usamos a query existente/indexada por Player.teamId.
        // Lookup por PK fica restrito a relações já inconsistentes, onde o jogador não está no
        // roster do borrower e precisamos encerrar o estado sem inferir um destino.
        var loanFeesPaid = 0L
        val activeLoans = repository.getActiveLoans()
        val borrowerRostersByPlayerId = activeLoans
            .asSequence()
            .map { it.borrowerTeamId }
            .filter { it > 0L }
            .distinct()
            .flatMap { borrowerId -> repository.getPlayersByTeam(borrowerId).asSequence() }
            .associateBy { it.id }
        val updatedLoans = mutableListOf<com.example.data.PlayerLoan>()

        for (loan in activeLoans) {
            val rosterPlayer = borrowerRostersByPlayerId[loan.playerId]
            val player = rosterPlayer ?: repository.getPlayer(loan.playerId)

            if (Fc26LoanPolicy.isUnknownEndSnapshotLoan(loan)) {
                if (player == null || !player.isOnLoan || player.contractDurationWeeks <= 0) {
                    updatedLoans += loan.copy(remainingWeeks = 0, status = "COMPLETED")
                    if (player != null && player.isOnLoan) {
                        repository.updatePlayer(
                            player.copy(
                                teamId = null,
                                originalTeamId = null,
                                isOnLoan = false,
                                loanWeeksRemaining = 0,
                                isStarter = false,
                                salary = 0L
                            )
                        )
                    }
                } else if (
                    player.teamId != loan.borrowerTeamId ||
                    player.originalTeamId != loan.ownerTeamId
                ) {
                    updatedLoans += loan.copy(remainingWeeks = 0, status = "INVALID")
                    repository.updatePlayer(
                        player.copy(
                            originalTeamId = null,
                            isOnLoan = false,
                            loanWeeksRemaining = 0,
                            isStarter = false
                        )
                    )
                }
                continue
            }

            if (loan.borrowerTeamId == currentSave.playerTeamId) {
                loanFeesPaid += loan.weeklyFee
            }
            val rem = loan.remainingWeeks - 1
            val newStatus = if (rem <= 0) "COMPLETED" else "ACTIVE"
            updatedLoans += loan.copy(remainingWeeks = rem, status = newStatus)

            if (player != null) {
                if (rem <= 0) {
                    if (player.contractDurationWeeks <= 0) {
                        repository.updatePlayer(
                            player.copy(
                                teamId = null,
                                originalTeamId = null,
                                isOnLoan = false,
                                loanWeeksRemaining = 0,
                                isStarter = false,
                                salary = 0L
                            )
                        )
                    } else {
                        val ownerTeamId = player.originalTeamId ?: loan.ownerTeamId
                        repository.updatePlayer(
                            player.copy(
                                teamId = ownerTeamId,
                                originalTeamId = null,
                                isOnLoan = false,
                                loanWeeksRemaining = 0,
                                isStarter = false
                            )
                        )
                    }
                } else {
                    repository.updatePlayer(player.copy(isOnLoan = true, loanWeeksRemaining = rem))
                }
            }
        }

        val totalExpenses = playerSalaries + loanInterest + maintenanceCost + academyCost + totalInstallmentPaid + loanFeesPaid
        val netWeeklyBalance = totalWeeklyIncome + totalInstallmentReceived - totalExpenses
        val newBankBalance = (currentSave.bankBalance + netWeeklyBalance).coerceAtLeast(-1000000000L)

        val updatedSave = currentSave.copy(
            bankBalance = newBankBalance,
            sponsorName = sponsorName,
            sponsorWeekly = sponsorWeekly,
            sponsorWeeksRemaining = sponsorWeeksRemaining
        )

        repository.saveGameSave(updatedSave)
        for (inst in updatedInstallments) repository.updateInstallment(inst)
        for (loan in updatedLoans) repository.updateLoan(loan)

        if (totalWeeklyIncome + totalInstallmentReceived > 0) {
            repository.saveTransaction(
                TransactionRecord(
                    week = currentSave.currentWeek,
                    season = currentSave.currentSeason,
                    type = "RECEITA_SEMANAL",
                    description = "Receita Semanal (Sócios: R$ %,d, Patrocínio: R$ %,d, Bilheteria: R$ %,d, Parcelas Rec: R$ %,d)".format(
                        socioRevenue, sponsorRevenue, ticketRevenue, totalInstallmentReceived
                    ),
                    amount = totalWeeklyIncome + totalInstallmentReceived,
                    isIncome = true
                )
            )
        }

        if (totalExpenses > 0) {
            repository.saveTransaction(
                TransactionRecord(
                    week = currentSave.currentWeek,
                    season = currentSave.currentSeason,
                    type = "DESPESA_SEMANAL",
                    description = "Despesas (Salários: R$ %,d, Juros: R$ %,d, Estádio/Base: R$ %,d, Parcelas Pagas: R$ %,d)".format(
                        playerSalaries, loanInterest, maintenanceCost + academyCost, totalInstallmentPaid
                    ),
                    amount = totalExpenses,
                    isIncome = false
                )
            )
        }

        updatedSave
    }

    suspend fun requestLoan(save: GameSave, amount: Long): FinanceResult = repository.withTransaction {
        val currentSave = repository.getGameSave() ?: save
        if (amount <= 0) return@withTransaction FinanceResult.Error("Valor de empréstimo inválido.")

        val team = repository.getTeam(currentSave.playerTeamId)
        val maxLoanByDiv = when (team?.division) {
            1 -> 50_000_000L
            2 -> 25_000_000L
            3 -> 10_000_000L
            else -> 5_000_000L
        }

        if (currentSave.loanAmount + amount > maxLoanByDiv) {
            return@withTransaction FinanceResult.Error("O limite total de empréstimo bancário para a sua divisão é de R$ %,d.".format(maxLoanByDiv))
        }

        val updatedSave = currentSave.copy(
            bankBalance = currentSave.bankBalance + amount,
            loanAmount = currentSave.loanAmount + amount
        )
        repository.saveGameSave(updatedSave)
        repository.saveTransaction(
            TransactionRecord(
                week = currentSave.currentWeek,
                season = currentSave.currentSeason,
                type = "EMPRESTIMO",
                description = "Empréstimo Bancário Contratado",
                amount = amount,
                isIncome = true
            )
        )

        FinanceResult.Success(updatedSave, "Empréstimo de R$ %,d concedido com sucesso!".format(amount))
    }

    suspend fun repayLoan(save: GameSave, amount: Long): FinanceResult = repository.withTransaction {
        val currentSave = repository.getGameSave() ?: save
        if (amount <= 0) return@withTransaction FinanceResult.Error("Valor inválido para quitação.")
        if (currentSave.loanAmount <= 0L) return@withTransaction FinanceResult.Error("Você não possui empréstimos ativos.")

        val actualPay = minOf(amount, currentSave.loanAmount)
        if (currentSave.bankBalance < actualPay) {
            return@withTransaction FinanceResult.Error("Saldo bancário insuficiente para quitar este valor.")
        }

        val updatedSave = currentSave.copy(
            bankBalance = currentSave.bankBalance - actualPay,
            loanAmount = currentSave.loanAmount - actualPay
        )
        repository.saveGameSave(updatedSave)
        repository.saveTransaction(
            TransactionRecord(
                week = currentSave.currentWeek,
                season = currentSave.currentSeason,
                type = "PAGAMENTO_EMPRESTIMO",
                description = "Amortização de Empréstimo Bancário",
                amount = actualPay,
                isIncome = false
            )
        )

        FinanceResult.Success(updatedSave, "Quitação de R$ %,d realizada com sucesso!".format(actualPay))
    }

    suspend fun upgradeStadium(save: GameSave, seatsToAdd: Int): FinanceResult = repository.withTransaction {
        val currentSave = repository.getGameSave() ?: save
        if (seatsToAdd <= 0) return@withTransaction FinanceResult.Error("Quantidade de assentos inválida.")
        val costPerSeat = 1200L
        val totalCost = seatsToAdd * costPerSeat

        if (currentSave.bankBalance < totalCost) {
            return@withTransaction FinanceResult.Error("Saldo insuficiente para expandir o estádio. Custo: R$ %,d.".format(totalCost))
        }

        val newCapacity = currentSave.stadiumCapacity + seatsToAdd
        val updatedSave = currentSave.copy(
            stadiumCapacity = newCapacity,
            bankBalance = currentSave.bankBalance - totalCost
        )
        repository.saveGameSave(updatedSave)
        repository.saveTransaction(
            TransactionRecord(
                week = currentSave.currentWeek,
                season = currentSave.currentSeason,
                type = "EXPANSAO_ESTADIO",
                description = "Expansão do Estádio (+%d lugares)".format(seatsToAdd),
                amount = totalCost,
                isIncome = false
            )
        )

        FinanceResult.Success(updatedSave, "Estádio expandido para %,d lugares com sucesso!".format(newCapacity))
    }

    suspend fun awardCompetitionPrizeMoney(
        save: GameSave,
        competitionName: String,
        position: Int
    ): GameSave = repository.withTransaction {
        val currentSave = repository.getGameSave() ?: save
        val prizeAmount = when {
            competitionName.contains("Série A") || competitionName.contains("Brasileiro") -> when (position) {
                1 -> 45_000_000L
                2 -> 30_000_000L
                3 -> 25_000_000L
                4 -> 20_000_000L
                else -> 10_000_000L
            }
            competitionName.contains("Copa do Brasil") -> when (position) {
                1 -> 70_000_000L
                2 -> 30_000_000L
                else -> 15_000_000L
            }
            competitionName.contains("Libertadores") -> when (position) {
                1 -> 100_000_000L
                2 -> 40_000_000L
                else -> 20_000_000L
            }
            competitionName.contains("Mundial") -> when (position) {
                1 -> 150_000_000L
                2 -> 70_000_000L
                else -> 30_000_000L
            }
            else -> 10_000_000L
        }

        val newSocios = (currentSave.socioTorcedoresCount + (if (position == 1) 1500 else 500)).coerceAtMost(100_000)
        val updatedSave = currentSave.copy(
            bankBalance = currentSave.bankBalance + prizeAmount,
            socioTorcedoresCount = newSocios
        )

        repository.saveGameSave(updatedSave)
        repository.saveTransaction(
            TransactionRecord(
                week = currentSave.currentWeek,
                season = currentSave.currentSeason,
                type = "PREMIACAO",
                description = "Premiação $competitionName (Posição: ${position}º)",
                amount = prizeAmount,
                isIncome = true
            )
        )

        updatedSave
    }

    suspend fun setTicketPrice(save: GameSave, price: Double): FinanceResult = repository.withTransaction {
        val currentSave = repository.getGameSave() ?: save
        val clampedPrice = price.coerceIn(10.0, 300.0)
        val updatedSave = currentSave.copy(ticketPrice = clampedPrice)
        repository.saveGameSave(updatedSave)
        FinanceResult.Success(updatedSave, "Preço do ingresso atualizado para R$ %.2f".format(clampedPrice))
    }

    suspend fun signSponsorshipContract(
        save: GameSave,
        sponsorName: String,
        weeklyPayment: Long,
        durationWeeks: Int,
        upFrontBonus: Long = 0L
    ): FinanceResult = repository.withTransaction {
        val currentSave = repository.getGameSave() ?: save
        if (sponsorName.isBlank()) return@withTransaction FinanceResult.Error("Nome do patrocinador inválido.")
        if (weeklyPayment <= 0L) return@withTransaction FinanceResult.Error("Valor semanal inválido.")
        if (durationWeeks <= 0) return@withTransaction FinanceResult.Error("Duração de contrato inválida.")

        val updatedSave = currentSave.copy(
            bankBalance = currentSave.bankBalance + upFrontBonus,
            sponsorName = sponsorName,
            sponsorWeekly = weeklyPayment,
            sponsorWeeksRemaining = durationWeeks
        )
        repository.saveGameSave(updatedSave)

        if (upFrontBonus > 0L) {
            repository.saveTransaction(
                TransactionRecord(
                    week = currentSave.currentWeek,
                    season = currentSave.currentSeason,
                    type = "PATROCINIO_BONUS",
                    description = "Bônus Luvas de Patrocínio: $sponsorName",
                    amount = upFrontBonus,
                    isIncome = true
                )
            )
        }

        FinanceResult.Success(
            updatedSave,
            "Contrato fechado com $sponsorName! R$ %,d/semana por %d semanas.".format(weeklyPayment, durationWeeks)
        )
    }

    suspend fun upgradeTrainingCenter(save: GameSave): FinanceResult = repository.withTransaction {
        val currentSave = repository.getGameSave() ?: save
        val team = repository.getTeam(currentSave.playerTeamId)
            ?: return@withTransaction FinanceResult.Error("Time do jogador não encontrado.")

        val currentLevel = team.trainingCenterLevel
        if (currentLevel >= 5) {
            return@withTransaction FinanceResult.Error("Centro de Treinamento já está no nível máximo (Nível 5).")
        }

        val cost = currentLevel * 5_000_000L
        if (currentSave.bankBalance < cost) {
            return@withTransaction FinanceResult.Error("Saldo insuficiente. Custo de ampliação: R$ %,d.".format(cost))
        }

        val updatedTeam = team.copy(trainingCenterLevel = currentLevel + 1)
        repository.updateTeam(updatedTeam)

        val updatedSave = currentSave.copy(bankBalance = currentSave.bankBalance - cost)
        repository.saveGameSave(updatedSave)

        repository.saveTransaction(
            TransactionRecord(
                week = currentSave.currentWeek,
                season = currentSave.currentSeason,
                type = "MELHORIA_CT",
                description = "Ampliação do CT para Nível ${currentLevel + 1}",
                amount = cost,
                isIncome = false
            )
        )

        FinanceResult.Success(
            updatedSave,
            "Centro de Treinamento ampliado para o Nível ${currentLevel + 1}!"
        )
    }
}
