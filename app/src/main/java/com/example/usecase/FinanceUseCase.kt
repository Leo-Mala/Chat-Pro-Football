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

    /**
     * Compatibilidade com os fluxos antigos, que conhecem apenas a existência ou não
     * de uma partida em casa na semana. Quando há ao menos uma, consulta os fixtures já
     * concluídos da semana para preservar corretamente semanas com dois jogos em casa.
     */
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

    /**
     * Processa a renda semanal de bilheteria, sócios-torcedores e patrocinadores,
     * bem como folha salarial, academia, parcelas e empréstimos.
     *
     * [homeMatchCount] permite contabilizar corretamente semanas com liga + Copa/continental.
     * Receitas e despesas semanais recorrentes continuam sendo processadas apenas uma vez.
     */
    suspend fun processWeeklyFinances(
        save: GameSave,
        homeMatchCount: Int,
        userPlayers: List<com.example.data.Player> = emptyList()
    ): GameSave = repository.withTransaction {
        val currentSave = repository.getGameSave() ?: save
        val safeHomeMatchCount = homeMatchCount.coerceAtLeast(0)

        val effectiveSocios = currentSave.socioTorcedoresCount.toLong().coerceAtLeast(currentSave.coachReputation * 150L)
        val socioRevenue = (effectiveSocios * 30.0).toLong()

        // Sponsor processing
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

        // Folha salarial semanal dos jogadores
        val roster = if (userPlayers.isNotEmpty()) userPlayers else repository.getPlayersByTeam(currentSave.playerTeamId)
        val playerSalaries = roster.sumOf { it.salary }.coerceAtLeast(30000L)

        // Pagamento de juros de empréstimo (0.2% semanal do valor devido)
        val loanInterest = if (currentSave.loanAmount > 0L) (currentSave.loanAmount * BANK_LOAN_WEEKLY_INTEREST_RATE).toLong() else 0L

        // Manutenção de estádio
        val maintenanceCost = (currentSave.stadiumCapacity * 2L)

        // Investimento em academia de base
        val academyCost = currentSave.academyWeeklyInvestment.coerceAtLeast(0L)

        // Processar parcelas ativas de transferências do time do jogador que VENCEM na semana atual
        var totalInstallmentPaid = 0L
        var totalInstallmentReceived = 0L
        val activeInstallments = repository.getActiveInstallmentsForWeek(currentSave.currentSeason, currentSave.currentWeek)
        val updatedInstallments = mutableListOf<com.example.data.TransferInstallment>()

        for (inst in activeInstallments) {
            var updated = inst
            val rem = inst.remainingInstallments - 1
            val (nextSeason, nextWeek) = calcNextDueDate(currentSave.currentSeason, currentSave.currentWeek, 1)
            val newStatus = if (rem <= 0) "COMPLETED" else "ACTIVE"

            if (inst.buyerTeamId == currentSave.playerTeamId) {
                totalInstallmentPaid += inst.installmentAmount
                updated = inst.copy(
                    remainingInstallments = rem,
                    nextDueWeek = nextWeek,
                    season = nextSeason,
                    status = newStatus
                )
                updatedInstallments.add(updated)
            } else if (inst.sellerTeamId == currentSave.playerTeamId) {
                totalInstallmentReceived += inst.installmentAmount
                updated = inst.copy(
                    remainingInstallments = rem,
                    nextDueWeek = nextWeek,
                    season = nextSeason,
                    status = newStatus
                )
                updatedInstallments.add(updated)
            }
        }

        // Processar empréstimos de jogadores ativos.
        // Snapshot FC26 sem data de término permanece ACTIVE até um evento explícito de carreira;
        // jamais decrementamos um prazo inexistente nem cobramos taxa inventada.
        var loanFeesPaid = 0L
        val activeLoans = repository.getActiveLoans()
        val updatedLoans = mutableListOf<com.example.data.PlayerLoan>()

        for (loan in activeLoans) {
            val player = repository.getPlayer(loan.playerId)

            if (Fc26LoanPolicy.isUnknownEndSnapshotLoan(loan)) {
                if (player == null || !player.isOnLoan || player.contractDurationWeeks <= 0) {
                    // O contrato principal expirou/liberou o atleta ou outra operação já publicou
                    // a mudança de roster. Fechar novamente é idempotente e evita relação órfã.
                    updatedLoans.add(loan.copy(remainingWeeks = 0, status = "COMPLETED"))
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
                    // Fail-closed em inconsistência de runtime: não movemos o jogador para clube
                    // algum por inferência. A relação deixa de ser ativa e o estado Player atual é
                    // preservado para que o save continue auditável.
                    updatedLoans.add(loan.copy(remainingWeeks = 0, status = "INVALID"))
                }
                continue
            }

            if (loan.borrowerTeamId == currentSave.playerTeamId) {
                loanFeesPaid += loan.weeklyFee
            }
            val rem = loan.remainingWeeks - 1
            val newStatus = if (rem <= 0) "COMPLETED" else "ACTIVE"
            val updated = loan.copy(remainingWeeks = rem, status = newStatus)
            updatedLoans.add(updated)

            // Strictly synchronize Player entity state with loan status
            if (player != null) {
                if (rem <= 0) {
                    val hasExpiredContract = player.contractDurationWeeks <= 0
                    if (hasExpiredContract) {
                        // Contrato expirou durante o empréstimo: torna-se Agente Livre canônico.
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
                        // Contrato ainda ativo: devolve ao clube proprietário original.
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
                    repository.updatePlayer(
                        player.copy(
                            isOnLoan = true,
                            loanWeeksRemaining = rem
                        )
                    )
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

        for (inst in updatedInstallments) {
            repository.updateInstallment(inst)
        }
        for (loan in updatedLoans) {
            repository.updateLoan(loan)
        }

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

    /**
     * Solicita um empréstimo bancário.
     */
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

    /**
     * Quita um valor do empréstimo bancário.
     */
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

    /**
     * Expande a capacidade do estádio.
     */
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

    /**
     * Premiação por término de competição (Série A, Copa do Brasil, Libertadores, Super Mundial)
     */
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
        val newBalance = currentSave.bankBalance + prizeAmount
        val updatedSave = currentSave.copy(
            bankBalance = newBalance,
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

    /**
     * Ajusta o valor do ingresso dos jogos em casa.
     */
    suspend fun setTicketPrice(save: GameSave, price: Double): FinanceResult = repository.withTransaction {
        val currentSave = repository.getGameSave() ?: save
        val clampedPrice = price.coerceIn(10.0, 300.0)
        val updatedSave = currentSave.copy(ticketPrice = clampedPrice)
        repository.saveGameSave(updatedSave)
        FinanceResult.Success(updatedSave, "Preço do ingresso atualizado para R$ %.2f".format(clampedPrice))
    }

    /**
     * Assina contrato de patrocínio com bônus de entrada e pagamento semanal.
     */
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

        val newBalance = currentSave.bankBalance + upFrontBonus
        val updatedSave = currentSave.copy(
            bankBalance = newBalance,
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

    /**
     * Melhora o Centro de Treinamento do clube.
     */
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
