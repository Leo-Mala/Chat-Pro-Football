package com.example.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

fun GameViewModel.acceptCoachOffer(offer: CoachOffer) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val updatedSave = save.copy(
            playerTeamId = offer.teamId,
                    )
        repo.saveGameSave(updatedSave)
        repo.deleteOffers()
        _selectedTeamId.value = offer.teamId
    }
}

fun GameViewModel.getDynamicPlayerPrice(player: Player): Long {
    val base = player.force * 150_000L + player.potential * 100_000L
    val ageFactor = when {
        player.age < 21 -> 1.5
        player.age < 28 -> 1.2
        player.age < 32 -> 0.9
        else -> 0.6
    }
    return (base * ageFactor).toLong().coerceAtLeast(100_000L)
}

fun GameViewModel.declineIncomingOffer(offer: IncomingOffer) {
    viewModelScope.launch(Dispatchers.IO) {
        _incomingOffers.update { list -> list.filterNot { it.id == offer.id } }
        _toastMessage.emit("Oferta por ${offer.player.name} recusada.")
    }
}

fun GameViewModel.acceptIncomingOffer(offer: IncomingOffer) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val result = processTransfersUseCase.acceptIncomingOffer(save, offer)
        if (result is com.example.usecase.ProcessTransfersUseCase.TransferResult.Success) {
            _incomingOffers.update { list -> list.filterNot { it.id == offer.id } }
            _toastMessage.emit(result.message)
        } else if (result is com.example.usecase.ProcessTransfersUseCase.TransferResult.Error) {
            _toastMessage.emit(result.reason)
        }
    }
}

fun GameViewModel.executeInstantBuy(player: Player, onResult: (Boolean) -> Unit = {}) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val result = processTransfersUseCase.buyPlayer(save, player, getDynamicPlayerPrice(player))
        onResult(result is com.example.usecase.ProcessTransfersUseCase.TransferResult.Success)
    }
}

fun GameViewModel.submitPurchaseOffer(
    player: Player,
    offeredPrice: Long,
    paymentType: String,
    hasGoalBonus: Boolean = false,
    hasSolidarity: Boolean = false,
    onResult: (GameViewModel.IAOfferResult) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val valPrice = getDynamicPlayerPrice(player)
        if (offeredPrice < valPrice * 0.8) {
            onResult(GameViewModel.IAOfferResult("declined", 0L, "Oferta muito baixa recusada pela diretoria."))
        } else if (offeredPrice < valPrice) {
            val counter = (valPrice * 1.05).toLong()
            onResult(GameViewModel.IAOfferResult("counter", counter, "O clube fez uma contraproposta de R$ $counter."))
        } else {
            processTransfersUseCase.buyPlayerAdvanced(save, player, offeredPrice, if (paymentType == "PARCELADO") com.example.usecase.ProcessTransfersUseCase.INSTALLMENT_COUNT else 1)
            onResult(GameViewModel.IAOfferResult("accepted", 0L, "Oferta aceita! Jogador contratado."))
        }
    }
}

fun GameViewModel.buyPlayerAdvanced(
    player: Player,
    price: Long,
    paymentType: String,
    hasGoalBonus: Boolean = false,
    hasSolidarity: Boolean = false
) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        processTransfersUseCase.buyPlayerAdvanced(save, player, price, if (paymentType == "PARCELADO") com.example.usecase.ProcessTransfersUseCase.INSTALLMENT_COUNT else 1)
    }
}

fun GameViewModel.renewContract(player: Player, durationWeeks: Int = 52) {
    viewModelScope.launch(Dispatchers.IO) {
        val repository = getActiveRepository() ?: return@launch
        val save = repository.getGameSave() ?: return@launch
        if (durationWeeks <= 0) {
            _toastMessage.emit("Duração da renovação inválida!")
            return@launch
        }
        repository.withTransaction {
            val freshPlayer = repository.getPlayer(player.id)
            if (freshPlayer == null) {
                _toastMessage.emit("Jogador não encontrado no banco de dados!")
                return@withTransaction
            }
            if (freshPlayer.teamId != save.playerTeamId && freshPlayer.originalTeamId != save.playerTeamId) {
                _toastMessage.emit("O jogador não pertence ao seu clube!")
                return@withTransaction
            }
            if (freshPlayer.teamId == 0L && !freshPlayer.isOnLoan) {
                _toastMessage.emit("Agentes livres não podem ter contratos renovados!")
                return@withTransaction
            }
            val newDuration = freshPlayer.contractDurationWeeks + durationWeeks
            val newSalary = (freshPlayer.salary * 1.1).toLong().coerceAtLeast(3000L)
            val updatedPlayer = freshPlayer.copy(
                contractDurationWeeks = newDuration,
                salary = newSalary
            )
            repository.updatePlayer(updatedPlayer)
            _toastMessage.emit("Contrato de ${freshPlayer.name} renovado por mais $durationWeeks semanas!")
        }
    }
}

fun GameViewModel.sellPlayer(
    player: Player,
    price: Long = 0L,
    paymentType: String = "VISTA"
) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val salePrice = if (price > 0L) price else getDynamicPlayerPrice(player)
        val result = if (paymentType == "PARCELADO") {
            val otherTeams = repo.getAllTeams().filter { it.id != save.playerTeamId }
            val buyer = otherTeams.shuffled().find { repo.getPlayerCountByTeam(it.id) < 30 }
            if (buyer != null) {
                processTransfersUseCase.executeInstallmentSale(
                    save = save,
                    buyerTeamId = buyer.id,
                    player = player,
                    offerPrice = salePrice,
                    installments = com.example.usecase.ProcessTransfersUseCase.INSTALLMENT_COUNT
                )
            } else {
                com.example.usecase.ProcessTransfersUseCase.TransferResult.Error("Não houve clubes interessados no momento para compra parcelada.")
            }
        } else {
            processTransfersUseCase.sellPlayer(save, player, salePrice)
        }
        val msg = when (result) {
            is com.example.usecase.ProcessTransfersUseCase.TransferResult.Success -> result.message
            is com.example.usecase.ProcessTransfersUseCase.TransferResult.Error -> result.reason
        }
        _toastMessage.emit(msg)
    }
}

fun GameViewModel.promoteYouthAcademy() {
    viewModelScope.launch(Dispatchers.IO) {
        val repository = getActiveRepository() ?: return@launch
        val save = repository.getGameSave() ?: return@launch
        val prospect = parseProspects(save.academyProspects).firstOrNull() ?: return@launch
        promoteAcademyProspectInternal(repository, prospect)
    }
}

fun GameViewModel.parseProspects(rawString: String): List<GameViewModel.AcademyProspect> {
    return youthAcademyUseCase.parseProspects(rawString)
}

fun GameViewModel.upgradeAcademyLevel() {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val newLevel = save.academyLevel + 1
        val cost = newLevel * 500_000L
        if (save.bankBalance >= cost) {
            val updated = save.copy(
                academyLevel = newLevel,
                bankBalance = save.bankBalance - cost
            )
            repo.saveGameSave(updated)
        }
    }
}

fun GameViewModel.adjustAcademyInvestment(amount: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val updated = save.copy(academyWeeklyInvestment = amount)
        repo.saveGameSave(updated)
    }
}

fun GameViewModel.promoteAcademyProspect(prospect: GameViewModel.AcademyProspect) {
    viewModelScope.launch(Dispatchers.IO) {
        val repository = getActiveRepository() ?: return@launch
        promoteAcademyProspectInternal(repository, prospect)
    }
}

private suspend fun GameViewModel.promoteAcademyProspectInternal(
    repository: GameRepository,
    prospect: GameViewModel.AcademyProspect
) {
    repository.withTransaction {
        val save = repository.getGameSave() ?: return@withTransaction
        val currentProspects = parseProspects(save.academyProspects).toMutableList()
        if (!currentProspects.remove(prospect)) return@withTransaction

        val teamCountry = repository.getTeam(save.playerTeamId)?.country ?: selectedCountry.value
        val newPlayer = Player(
            teamId = save.playerTeamId,
            name = prospect.name,
            nationality = teamCountry,
            position = prospect.position,
            force = prospect.force,
            potential = prospect.potential,
            age = prospect.age,
            salary = prospect.force * 100L,
            isFromAcademy = true
        )

        repository.savePlayers(listOf(newPlayer))
        repository.saveGameSave(
            save.copy(
                academyProspects = youthAcademyUseCase.serializeProspects(currentProspects)
            )
        )
    }
}

fun GameViewModel.dismissAcademyProspect(prospect: GameViewModel.AcademyProspect) {
    viewModelScope.launch(Dispatchers.IO) {
        val repository = getActiveRepository() ?: return@launch
        repository.withTransaction {
            val save = repository.getGameSave() ?: return@withTransaction
            val currentList = parseProspects(save.academyProspects).toMutableList()
            if (!currentList.remove(prospect)) return@withTransaction
            repository.saveGameSave(
                save.copy(academyProspects = youthAcademyUseCase.serializeProspects(currentList))
            )
        }
    }
}
