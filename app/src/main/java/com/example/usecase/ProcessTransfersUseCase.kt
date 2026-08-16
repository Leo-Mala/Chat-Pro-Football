package com.example.usecase

import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.TransactionRecord
import com.example.data.Team
import com.example.ui.viewmodel.IncomingOffer
import com.example.util.TransactionIdGenerator

/**
 * UseCase responsável pela validação e execução de transferências do mercado de contratações.
 * Garante segurança contra estouro de limites numéricos (overflow) e controle de caixa.
 */
class ProcessTransfersUseCase(private val repository: GameRepository) {

    companion object {
        const val INSTALLMENT_COUNT = 3
    }

    sealed class TransferResult {
        data class Success(val updatedSave: GameSave, val updatedPlayer: Player, val message: String) : TransferResult()
        data class Error(val reason: String) : TransferResult()
    }

    /**
     * Alias para compatibilidade com testes e GameViewModel
     */
    suspend fun buyPlayer(save: GameSave, player: Player, offerPrice: Long): TransferResult {
        val roster = repository.getPlayersByTeam(save.playerTeamId)
        return executePurchase(save, player, offerPrice, roster)
    }

    /**
     * Alias para compatibilidade com testes e GameViewModel
     */
    suspend fun sellPlayer(save: GameSave, player: Player, offerPrice: Long): TransferResult {
        val roster = repository.getPlayersByTeam(save.playerTeamId)
        return executeSale(save, player, offerPrice, roster)
    }

    /**
     * Oferta avançada de compra com parcelamento
     */
    suspend fun buyPlayerAdvanced(
        save: GameSave,
        player: Player,
        offerPrice: Long,
        installments: Int = INSTALLMENT_COUNT
    ): TransferResult {
        if (installments <= 1) {
            return buyPlayer(save, player, offerPrice)
        }

        if (offerPrice <= 0L) {
            return TransferResult.Error("O valor da oferta deve ser maior que zero!")
        }
        if (player.teamId == save.playerTeamId) {
            return TransferResult.Error("O jogador já pertence ao seu clube!")
        }
        if (save.bankBalance < 0L) {
            return TransferResult.Error("Clubes endividados não podem realizar contratações!")
        }

        val roster = repository.getPlayersByTeam(save.playerTeamId)
        if (roster.size >= 35) {
            return TransferResult.Error("Limite máximo de 35 jogadores no elenco atingido!")
        }

        val wageCap = calculateWeeklyWageCap(save)
        val currentWageBill = (roster.sumOf { it.salary } * 0.18).toLong()
        val newPlayerSalary = player.calculateSalary(save.coachReputation.toDouble())

        if (currentWageBill + (newPlayerSalary * 0.18).toLong() > wageCap) {
            return TransferResult.Error("A folha salarial excederia o limite permitido de R$ %,d!".format(wageCap))
        }

        val remainingWeeks = (installments - 1).coerceAtLeast(1)
        val weeklyInstallment = (offerPrice / installments).coerceAtLeast(1L)
        val downPayment = offerPrice - (weeklyInstallment * remainingWeeks)

        return repository.withTransaction {
            val freshPlayer = repository.getPlayer(player.id)
            if (freshPlayer == null) {
                return@withTransaction TransferResult.Error("Jogador não encontrado no banco de dados!")
            }
            if (freshPlayer.teamId == save.playerTeamId) {
                return@withTransaction TransferResult.Error("O jogador já pertence ao seu clube!")
            }
            if (player.teamId == 0L && freshPlayer.teamId != 0L) {
                return@withTransaction TransferResult.Error("Este agente livre já assinou com outro clube!")
            }
            val currentSave = repository.getGameSave() ?: save
            if (currentSave.bankBalance < downPayment) {
                return@withTransaction TransferResult.Error("Saldo insuficiente em caixa para pagar a entrada de R$ %,d!".format(downPayment))
            }

            val newBalance = (currentSave.bankBalance - downPayment).coerceAtLeast(0L)
            val updatedSave = currentSave.copy(bankBalance = newBalance)
            val updatedPlayer = freshPlayer.copy(
                teamId = currentSave.playerTeamId,
                moral = 90,
                salary = newPlayerSalary,
                contractDurationWeeks = if (freshPlayer.contractDurationWeeks <= 0) 52 else freshPlayer.contractDurationWeeks,
                isStarter = false,
                isOnLoan = false,
                loanWeeksRemaining = 0
            )

            repository.saveGameSave(updatedSave)
            repository.updatePlayers(listOf(updatedPlayer))

            val (dueSeason, dueWeek) = FinanceUseCase.calcNextDueDate(currentSave.currentSeason, currentSave.currentWeek, 1)

            val uniqueTransferId = TransactionIdGenerator.generateUniqueId()

            repository.saveInstallment(
                com.example.data.TransferInstallment(
                    transferId = uniqueTransferId,
                    playerId = freshPlayer.id,
                    buyerTeamId = currentSave.playerTeamId,
                    sellerTeamId = freshPlayer.teamId,
                    totalAmount = offerPrice,
                    downPayment = downPayment,
                    installmentAmount = weeklyInstallment,
                    totalInstallments = remainingWeeks,
                    remainingInstallments = remainingWeeks,
                    nextDueWeek = dueWeek,
                    season = dueSeason,
                    status = "ACTIVE"
                )
            )

            repository.saveTransaction(
                TransactionRecord(
                    week = currentSave.currentWeek,
                    season = currentSave.currentSeason,
                    type = "COMPRA_PARCELADA",
                    description = "Entrada da compra parcelada do jogador ${freshPlayer.name} (%dx de R$ %,d)".format(remainingWeeks, weeklyInstallment),
                    amount = downPayment,
                    isIncome = false
                )
            )

            TransferResult.Success(
                updatedSave = updatedSave,
                updatedPlayer = updatedPlayer,
                message = "Jogador ${freshPlayer.name} contratado parcelado! Entrada: R$ %,d + %dx R$ %,d/sem.".format(downPayment, remainingWeeks, weeklyInstallment)
            )
        }
    }

    /**
     * Vende jogador por parcelamento para outro clube.
     */
    suspend fun executeInstallmentSale(
        save: GameSave,
        buyerTeamId: Long,
        player: Player,
        offerPrice: Long,
        installments: Int = INSTALLMENT_COUNT
    ): TransferResult {
        if (installments < 1) {
            return TransferResult.Error("Número de parcelas inválido!")
        }
        if (offerPrice <= 0L) {
            return TransferResult.Error("O valor da oferta deve ser maior que zero!")
        }
        if (buyerTeamId == save.playerTeamId) {
            return TransferResult.Error("O clube comprador não pode ser o seu próprio clube!")
        }

        val buyerTeam = repository.getTeam(buyerTeamId)
        if (buyerTeam == null) {
            return TransferResult.Error("Clube comprador não foi encontrado!")
        }

        val buyerRosterSize = repository.getPlayerCountByTeam(buyerTeamId)
        if (buyerRosterSize >= 35) {
            return TransferResult.Error("O clube comprador já possui o elenco cheio (35 jogadores)!")
        }

        val remainingWeeks = (installments - 1).coerceAtLeast(1)
        val weeklyInstallment = (offerPrice / installments).coerceAtLeast(1L)
        val downPayment = offerPrice - (weeklyInstallment * remainingWeeks)

        return repository.withTransaction {
            val freshPlayer = repository.getPlayer(player.id)
            if (freshPlayer == null) {
                return@withTransaction TransferResult.Error("Jogador não encontrado no banco de dados!")
            }
            if (freshPlayer.teamId != save.playerTeamId) {
                return@withTransaction TransferResult.Error("O jogador não pertence mais ao seu clube!")
            }

            val currentSave = repository.getGameSave() ?: save
            val newBalance = try {
                Math.addExact(currentSave.bankBalance, downPayment)
            } catch (e: ArithmeticException) {
                Long.MAX_VALUE
            }

            val updatedSave = currentSave.copy(bankBalance = newBalance)
            val updatedPlayer = freshPlayer.copy(
                teamId = buyerTeamId,
                isStarter = false,
                isOnLoan = false,
                loanWeeksRemaining = 0
            )

            repository.saveGameSave(updatedSave)
            repository.updatePlayers(listOf(updatedPlayer))

            if (remainingWeeks > 0) {
                val (dueSeason, dueWeek) = FinanceUseCase.calcNextDueDate(currentSave.currentSeason, currentSave.currentWeek, 1)
                val uniqueTransferId = TransactionIdGenerator.generateUniqueId()

                repository.saveInstallment(
                    com.example.data.TransferInstallment(
                        transferId = uniqueTransferId,
                        playerId = freshPlayer.id,
                        buyerTeamId = buyerTeamId,
                        sellerTeamId = currentSave.playerTeamId,
                        totalAmount = offerPrice,
                        downPayment = downPayment,
                        installmentAmount = weeklyInstallment,
                        totalInstallments = remainingWeeks,
                        remainingInstallments = remainingWeeks,
                        nextDueWeek = dueWeek,
                        season = dueSeason,
                        status = "ACTIVE"
                    )
                )
            }

            repository.saveTransaction(
                TransactionRecord(
                    week = currentSave.currentWeek,
                    season = currentSave.currentSeason,
                    type = "VENDA_PARCELADA",
                    description = "Entrada da venda parcelada do jogador ${freshPlayer.name} (%dx de R$ %,d)".format(remainingWeeks, weeklyInstallment),
                    amount = downPayment,
                    isIncome = true
                )
            )

            TransferResult.Success(
                updatedSave = updatedSave,
                updatedPlayer = updatedPlayer,
                message = "Jogador ${freshPlayer.name} vendido parcelado! Recebido: R$ %,d + %dx R$ %,d/sem.".format(downPayment, remainingWeeks, weeklyInstallment)
            )
        }
    }

    /**
     * Contrata jogador por empréstimo por um determinado número de semanas.
     */
    suspend fun loanPlayer(
        save: GameSave,
        player: Player,
        loanWeeks: Int = 26,
        weeklyFee: Long = 15000L
    ): TransferResult {
        if (loanWeeks <= 0) {
            return TransferResult.Error("Duração do empréstimo inválida!")
        }
        if (weeklyFee < 0L) {
            return TransferResult.Error("Taxa semanal do empréstimo inválida!")
        }

        val roster = repository.getPlayersByTeam(save.playerTeamId)
        if (roster.size >= 35) {
            return TransferResult.Error("Elenco cheio (máximo 35 jogadores)!")
        }

        return repository.withTransaction {
            val freshPlayer = repository.getPlayer(player.id)
            if (freshPlayer == null) {
                return@withTransaction TransferResult.Error("Jogador não encontrado no banco de dados!")
            }
            if (freshPlayer.teamId == save.playerTeamId) {
                return@withTransaction TransferResult.Error("O jogador já pertence ao seu clube!")
            }
            if (freshPlayer.isOnLoan) {
                return@withTransaction TransferResult.Error("O jogador já está emprestado!")
            }
            if (freshPlayer.teamId == 0L) {
                return@withTransaction TransferResult.Error("Agentes livres não podem ser emprestados!")
            }
            if (freshPlayer.contractDurationWeeks < loanWeeks) {
                return@withTransaction TransferResult.Error("O contrato do jogador (${freshPlayer.contractDurationWeeks} sem) é mais curto que o empréstimo ($loanWeeks sem)!")
            }

            val activeLoan = repository.getActiveLoanForPlayer(freshPlayer.id)
            if (activeLoan != null) {
                return@withTransaction TransferResult.Error("Já existe um empréstimo ativo para este jogador!")
            }

            val ownerTeamId = if (freshPlayer.originalTeamId != 0L) freshPlayer.originalTeamId else freshPlayer.teamId
            val ownerTeam = repository.getTeam(ownerTeamId)
            if (ownerTeam == null) {
                return@withTransaction TransferResult.Error("Clube proprietário não foi encontrado!")
            }

            val updatedPlayer = freshPlayer.copy(
                originalTeamId = ownerTeamId,
                teamId = save.playerTeamId,
                isOnLoan = true,
                loanWeeksRemaining = loanWeeks,
                isStarter = false
            )
            repository.updatePlayers(listOf(updatedPlayer))

            repository.saveLoan(
                com.example.data.PlayerLoan(
                    playerId = freshPlayer.id,
                    ownerTeamId = ownerTeamId,
                    borrowerTeamId = save.playerTeamId,
                    startSeason = save.currentSeason,
                    startWeek = save.currentWeek,
                    durationWeeks = loanWeeks,
                    remainingWeeks = loanWeeks,
                    weeklyFee = weeklyFee,
                    status = "ACTIVE"
                )
            )

            TransferResult.Success(
                updatedSave = save,
                updatedPlayer = updatedPlayer,
                message = "Jogador ${freshPlayer.name} contratado por empréstimo por %d semanas!".format(loanWeeks)
            )
        }
    }

    /**
     * Processa a regressão de contratos e o encerramento de empréstimos semanais.
     * ÚNICA fonte de verdade para expiração de contratos. Empréstimos são decrementados em FinanceUseCase.
     */
    suspend fun processWeeklyContractsAndLoans() {
        val allPlayers = repository.getAllPlayers()
        val updatedPlayers = mutableListOf<Player>()

        for (player in allPlayers) {
            var updated = player
            var modified = false

            if (updated.contractDurationWeeks > 0) {
                val newWeeks = updated.contractDurationWeeks - 1
                if (newWeeks <= 0) {
                    if (updated.isOnLoan) {
                        // Se estiver emprestado, zera a duração do contrato sem transformar em Free Agent ainda.
                        // A virada para Free Agent ocorrerá ao término do empréstimo em FinanceUseCase.
                        updated = updated.copy(
                            contractDurationWeeks = 0,
                            isStarter = false,
                            salary = 0L
                        )
                    } else {
                        updated = updated.copy(
                            contractDurationWeeks = 0,
                            teamId = 0L, // Free Agent
                            originalTeamId = 0L,
                            isStarter = false,
                            salary = 0L
                        )
                    }
                } else {
                    updated = updated.copy(contractDurationWeeks = newWeeks)
                }
                modified = true
            }

            if (modified) {
                updatedPlayers.add(updated)
            }
        }

        if (updatedPlayers.isNotEmpty()) {
            repository.updatePlayers(updatedPlayers)
        }
    }

    /**
     * Calcula o teto salarial permitido com base na receita média semanal estimada do clube.
     */
    fun calculateWeeklyWageCap(save: GameSave): Long {
        val socioRevenue = save.socioTorcedoresCount * 45.0
        val sponsorRevenue = 300000.0 + (save.coachReputation * 15000.0)
        val baseAttendanceRate = 0.4 + (save.coachReputation / 200.0)
        val priceFactor = (1.5 - ((save.ticketPrice - 30.0) * 0.012)).coerceIn(0.35, 1.5)
        val maxCap = maxOf(1000, save.stadiumCapacity)
        val minCap = minOf(1000, maxCap)
        val estimatedAttendance = (save.stadiumCapacity * baseAttendanceRate * priceFactor).toInt().coerceIn(minCap, maxCap)
        val estimatedWeeklyTicketRevenue = (estimatedAttendance * save.ticketPrice)

        val averageWeeklyRevenue = sponsorRevenue + socioRevenue + estimatedWeeklyTicketRevenue
        val wageCap = (averageWeeklyRevenue * 0.65).toLong()

        return wageCap.coerceIn(50000L, 5000000000L)
    }

    /**
     * Tenta realizar a compra de um jogador garantindo limites financeiros, de elenco e transações atômicas.
     */
    suspend fun executePurchase(
        save: GameSave,
        player: Player,
        price: Long,
        currentRoster: List<Player>
    ): TransferResult {
        if (price <= 0L) {
            return TransferResult.Error("O valor da oferta de transferência deve ser maior que zero!")
        }

        if (player.teamId == save.playerTeamId) {
            return TransferResult.Error("O jogador já pertence ao seu clube!")
        }

        if (save.bankBalance < 0L) {
            return TransferResult.Error("Clubes endividados não podem realizar contratações!")
        }

        if (currentRoster.size >= 35) {
            return TransferResult.Error("Limite máximo de 35 jogadores no elenco atingido!")
        }

        val safePrice = price.coerceAtLeast(1L)
        if (save.bankBalance < safePrice) {
            return TransferResult.Error("Saldo insuficiente em caixa para realizar esta operação!")
        }

        val wageCap = calculateWeeklyWageCap(save)
        val currentWageBill = (currentRoster.sumOf { it.salary } * 0.18).toLong()
        val newPlayerSalary = player.calculateSalary(save.coachReputation.toDouble())

        if (currentWageBill + (newPlayerSalary * 0.18).toLong() > wageCap) {
            return TransferResult.Error("A folha salarial excederia o limite permitido de R$ %,d!".format(wageCap))
        }

        return repository.withTransaction {
            val freshPlayer = repository.getPlayer(player.id)
            if (freshPlayer == null) {
                return@withTransaction TransferResult.Error("Jogador não encontrado no banco de dados!")
            }
            if (freshPlayer.teamId == save.playerTeamId) {
                return@withTransaction TransferResult.Error("O jogador já pertence ao seu clube!")
            }
            if (player.teamId == 0L && freshPlayer.teamId != 0L) {
                return@withTransaction TransferResult.Error("Este agente livre já assinou com outro clube!")
            }
            val currentSave = repository.getGameSave() ?: save
            if (currentSave.bankBalance < safePrice) {
                return@withTransaction TransferResult.Error("Saldo insuficiente em caixa para realizar esta operação!")
            }

            val newBalance = (currentSave.bankBalance - safePrice).coerceAtLeast(0L)
            val updatedSave = currentSave.copy(bankBalance = newBalance)
            val updatedPlayer = freshPlayer.copy(
                teamId = currentSave.playerTeamId,
                moral = 90,
                salary = newPlayerSalary,
                contractDurationWeeks = if (freshPlayer.contractDurationWeeks <= 0) 52 else freshPlayer.contractDurationWeeks,
                isStarter = false,
                isOnLoan = false,
                loanWeeksRemaining = 0
            )

            repository.saveGameSave(updatedSave)
            repository.updatePlayers(listOf(updatedPlayer))

            repository.saveTransaction(
                TransactionRecord(
                    week = currentSave.currentWeek,
                    season = currentSave.currentSeason,
                    type = "COMPRA",
                    description = "Contratação do jogador ${freshPlayer.name}",
                    amount = safePrice,
                    isIncome = false
                )
            )

            TransferResult.Success(
                updatedSave = updatedSave,
                updatedPlayer = updatedPlayer,
                message = "Jogador ${freshPlayer.name} contratado com sucesso!"
            )
        }
    }

    /**
     * Tenta realizar a venda de um jogador com transação atômica.
     */
    suspend fun executeSale(
        save: GameSave,
        player: Player,
        price: Long,
        currentRoster: List<Player>
    ): TransferResult {
        if (price <= 0L) {
            return TransferResult.Error("O valor de venda deve ser maior que zero!")
        }

        if (currentRoster.size <= 16) {
            return TransferResult.Error("Não é possível vender. Seu elenco deve ter no mínimo 16 jogadores.")
        }

        val safePrice = price.coerceAtLeast(1L)
        val otherTeams = repository.getAllTeams().filter { it.id != save.playerTeamId }

        if (otherTeams.isEmpty()) {
            return TransferResult.Error("Não houve clubes interessados no momento. O atleta ${player.name} permanecerá na equipe.")
        }

        val maxAttempts = 50
        var buyer: Team? = null
        val candidates = otherTeams.shuffled()

        var attempts = 0
        for (candidate in candidates) {
            if (attempts >= maxAttempts) break
            attempts++
            val candidateRosterSize = repository.getPlayerCountByTeam(candidate.id)
            if (candidateRosterSize < 30) {
                buyer = candidate
                break
            }
        }

        if (buyer == null) {
            return TransferResult.Error("Não houve clubes interessados no momento. O atleta ${player.name} permanecerá na equipe.")
        }

        return repository.withTransaction {
            val freshPlayer = repository.getPlayer(player.id)
            if (freshPlayer == null) {
                return@withTransaction TransferResult.Error("Jogador não encontrado no banco de dados!")
            }
            if (freshPlayer.teamId != save.playerTeamId) {
                return@withTransaction TransferResult.Error("O jogador não pertence mais ao seu clube!")
            }
            val currentSave = repository.getGameSave() ?: save
            val newBalance = try {
                Math.addExact(currentSave.bankBalance, safePrice)
            } catch (e: ArithmeticException) {
                Long.MAX_VALUE
            }

            val updatedSave = currentSave.copy(bankBalance = newBalance)
            val updatedPlayer = freshPlayer.copy(
                teamId = buyer.id,
                moral = 70,
                isStarter = false,
                isOnLoan = false,
                loanWeeksRemaining = 0
            )

            repository.saveGameSave(updatedSave)
            repository.updatePlayers(listOf(updatedPlayer))

            repository.saveTransaction(
                TransactionRecord(
                    week = currentSave.currentWeek,
                    season = currentSave.currentSeason,
                    type = "VENDA",
                    description = "Venda do jogador ${freshPlayer.name} para ${buyer.name}",
                    amount = safePrice,
                    isIncome = true
                )
            )

            TransferResult.Success(
                updatedSave = updatedSave,
                updatedPlayer = updatedPlayer,
                message = "Atleta ${freshPlayer.name} vendido ao ${buyer.name} por R$ %,d!".format(safePrice)
            )
        }
    }

    /**
     * Aceita proposta recebida por um jogador, direcionando rigorosamente para o clube comprador da oferta.
     */
    suspend fun acceptIncomingOffer(save: GameSave, offer: IncomingOffer): TransferResult {
        return repository.withTransaction {
            val freshPlayer = repository.getPlayer(offer.player.id)
            if (freshPlayer == null) {
                return@withTransaction TransferResult.Error("Jogador não encontrado no banco de dados!")
            }
            if (freshPlayer.teamId != save.playerTeamId) {
                return@withTransaction TransferResult.Error("O jogador não pertence mais ao seu clube!")
            }

            val currentRoster = repository.getPlayersByTeam(save.playerTeamId)
            if (currentRoster.size <= 16) {
                return@withTransaction TransferResult.Error("Não é possível vender. Seu elenco deve ter no mínimo 16 jogadores.")
            }

            if (offer.offerType == "EMPRESTIMO") {
                val borrowerTeam = if (offer.buyerTeamId != 0L) repository.getTeam(offer.buyerTeamId) else repository.getAllTeams().find { it.name == offer.buyerTeamName }
                if (borrowerTeam == null) {
                    return@withTransaction TransferResult.Error("Clube interessado não foi encontrado!")
                }
                val borrowerRosterSize = repository.getPlayerCountByTeam(borrowerTeam.id)
                if (borrowerRosterSize >= 35) {
                    return@withTransaction TransferResult.Error("O clube interessado já está com o elenco cheio!")
                }

                val loanWeeks = if (offer.durationWeeks > 0) offer.durationWeeks else 26
                val updatedPlayer = freshPlayer.copy(
                    originalTeamId = freshPlayer.teamId,
                    teamId = borrowerTeam.id,
                    isOnLoan = true,
                    loanWeeksRemaining = loanWeeks,
                    isStarter = false
                )
                repository.updatePlayer(updatedPlayer)

                repository.saveLoan(
                    com.example.data.PlayerLoan(
                        playerId = freshPlayer.id,
                        ownerTeamId = save.playerTeamId,
                        borrowerTeamId = borrowerTeam.id,
                        startSeason = save.currentSeason,
                        startWeek = save.currentWeek,
                        durationWeeks = loanWeeks,
                        remainingWeeks = loanWeeks,
                        weeklyFee = offer.price,
                        status = "ACTIVE"
                    )
                )

                TransferResult.Success(
                    updatedSave = save,
                    updatedPlayer = updatedPlayer,
                    message = "Proposta de empréstimo aceita! ${freshPlayer.name} emprestado ao ${borrowerTeam.name} por $loanWeeks semanas."
                )
            } else {
                val buyerTeam = if (offer.buyerTeamId != 0L) repository.getTeam(offer.buyerTeamId) else repository.getAllTeams().find { it.name == offer.buyerTeamName }
                if (buyerTeam == null) {
                    return@withTransaction TransferResult.Error("Clube comprador não foi encontrado!")
                }
                val buyerRosterSize = repository.getPlayerCountByTeam(buyerTeam.id)
                if (buyerRosterSize >= 35) {
                    return@withTransaction TransferResult.Error("O clube comprador está com o elenco cheio!")
                }

                val safePrice = offer.price.coerceAtLeast(1L)
                val currentSave = repository.getGameSave() ?: save
                val newBalance = try {
                    Math.addExact(currentSave.bankBalance, safePrice)
                } catch (e: ArithmeticException) {
                    Long.MAX_VALUE
                }

                val updatedSave = currentSave.copy(bankBalance = newBalance)
                val updatedPlayer = freshPlayer.copy(
                    teamId = buyerTeam.id,
                    moral = 70,
                    isStarter = false,
                    isOnLoan = false,
                    loanWeeksRemaining = 0
                )

                repository.saveGameSave(updatedSave)
                repository.updatePlayer(updatedPlayer)

                repository.saveTransaction(
                    TransactionRecord(
                        week = currentSave.currentWeek,
                        season = currentSave.currentSeason,
                        type = "VENDA",
                        description = "Venda do jogador ${freshPlayer.name} para ${buyerTeam.name}",
                        amount = safePrice,
                        isIncome = true
                    )
                )

                TransferResult.Success(
                    updatedSave = updatedSave,
                    updatedPlayer = updatedPlayer,
                    message = "Proposta aceita! Atleta ${freshPlayer.name} vendido ao ${buyerTeam.name} por R$ %,d!".format(safePrice)
                )
            }
        }
    }

    suspend fun acceptIncomingOffer(save: GameSave, player: Player, offerPrice: Long): TransferResult {
        val dummyOffer = IncomingOffer(
            id = System.currentTimeMillis(),
            player = player,
            buyerTeamName = "",
            buyerTeamId = 0L,
            offerType = "COMPRA",
            price = offerPrice
        )
        return acceptIncomingOffer(save, dummyOffer)
    }
}
