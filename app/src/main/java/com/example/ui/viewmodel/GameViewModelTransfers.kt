package com.example.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.data.CoachOffer
import com.example.data.Player
import com.example.usecase.AcademyProspect as DomainAcademyProspect
import com.example.usecase.CoachCareerUseCase
import com.example.usecase.ContractLifecycleUseCase
import com.example.usecase.LoanLifecycleUseCase
import com.example.usecase.ProcessTransfersUseCase
import com.example.usecase.TransferNegotiationUseCase
import com.example.usecase.YouthAcademyManagementUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

fun GameViewModel.acceptCoachOffer(offer: CoachOffer) {
    viewModelScope.launch(Dispatchers.IO) {
        when (val result = CoachCareerUseCase(repo).acceptOffer(offer)) {
            is CoachCareerUseCase.AcceptOfferResult.Success -> _selectedTeamId.value = result.teamId
            CoachCareerUseCase.AcceptOfferResult.Rejected -> Unit
        }
    }
}

fun GameViewModel.getDynamicPlayerPrice(player: Player): Long =
    TransferNegotiationUseCase.calculateDynamicPlayerPrice(player)

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
        if (result is ProcessTransfersUseCase.TransferResult.Success) {
            _incomingOffers.update { list -> list.filterNot { it.id == offer.id } }
            _toastMessage.emit(result.message)
        } else if (result is ProcessTransfersUseCase.TransferResult.Error) {
            _toastMessage.emit(result.reason)
        }
    }
}

fun GameViewModel.executeInstantBuy(player: Player, onResult: (Boolean) -> Unit = {}) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val result = processTransfersUseCase.buyPlayer(save, player, getDynamicPlayerPrice(player))
        onResult(result is ProcessTransfersUseCase.TransferResult.Success)
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
        val installments = if (paymentType == "PARCELADO") ProcessTransfersUseCase.INSTALLMENT_COUNT else 1
        val result = TransferNegotiationUseCase(processTransfersUseCase).submitPurchaseOffer(
            save = save,
            player = player,
            offeredPrice = offeredPrice,
            installments = installments
        )
        when (result) {
            is TransferNegotiationUseCase.NegotiationResult.Accepted ->
                onResult(GameViewModel.IAOfferResult("accepted", 0L, result.message))
            is TransferNegotiationUseCase.NegotiationResult.Counter ->
                onResult(GameViewModel.IAOfferResult("counter", result.price, result.message))
            is TransferNegotiationUseCase.NegotiationResult.Declined ->
                onResult(GameViewModel.IAOfferResult("declined", 0L, result.reason))
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
        processTransfersUseCase.buyPlayerAdvanced(
            save,
            player,
            price,
            if (paymentType == "PARCELADO") ProcessTransfersUseCase.INSTALLMENT_COUNT else 1
        )
    }
}

/**
 * A ação principal do diálogo de elenco é ownership-aware: jogador normal renova contrato; loanee
 * que está no roster do usuário é devolvido explicitamente ao owner. A renovação propriamente dita
 * continua protegida por [ContractLifecycleUseCase], então chamadas diretas do borrower também
 * falham fechadas.
 */
fun GameViewModel.renewContract(player: Player, durationWeeks: Int = 52) {
    viewModelScope.launch(Dispatchers.IO) {
        val repository = getActiveRepository() ?: return@launch
        val save = repository.getGameSave() ?: return@launch

        if (player.isOnLoan && player.teamId == save.playerTeamId) {
            when (val result = LoanLifecycleUseCase(repository).returnToOwner(player.id)) {
                is LoanLifecycleUseCase.Result.Returned ->
                    _toastMessage.emit("${result.player.name} foi devolvido ao clube proprietário.")
                is LoanLifecycleUseCase.Result.AlreadyClosed ->
                    _toastMessage.emit("O empréstimo já estava encerrado.")
                is LoanLifecycleUseCase.Result.Rejected ->
                    _toastMessage.emit(result.reason)
            }
            return@launch
        }

        when (val result = ContractLifecycleUseCase(repository).renewPlayerContract(player.id, durationWeeks)) {
            is ContractLifecycleUseCase.RenewalResult.Success -> _toastMessage.emit(result.message)
            is ContractLifecycleUseCase.RenewalResult.Rejected -> _toastMessage.emit(result.reason)
            ContractLifecycleUseCase.RenewalResult.Unavailable -> Unit
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
                    installments = ProcessTransfersUseCase.INSTALLMENT_COUNT
                )
            } else {
                ProcessTransfersUseCase.TransferResult.Error(
                    "Não houve clubes interessados no momento para compra parcelada."
                )
            }
        } else {
            processTransfersUseCase.sellPlayer(save, player, salePrice)
        }
        val msg = when (result) {
            is ProcessTransfersUseCase.TransferResult.Success -> result.message
            is ProcessTransfersUseCase.TransferResult.Error -> result.reason
        }
        _toastMessage.emit(msg)
    }
}

fun GameViewModel.promoteYouthAcademy() {
    viewModelScope.launch(Dispatchers.IO) {
        val repository = getActiveRepository() ?: return@launch
        val save = repository.getGameSave() ?: return@launch
        val prospect = youthAcademyUseCase.parseProspects(save.academyProspects).firstOrNull() ?: return@launch
        handleAcademyResult(
            YouthAcademyManagementUseCase(repository, youthAcademyUseCase)
                .promoteProspect(prospect, selectedCountry.value)
        )
    }
}

fun GameViewModel.parseProspects(rawString: String): List<GameViewModel.AcademyProspect> =
    youthAcademyUseCase.parseProspects(rawString).map { it.toViewModelProspect() }

fun GameViewModel.upgradeAcademyLevel() {
    viewModelScope.launch(Dispatchers.IO) {
        val repository = getActiveRepository() ?: return@launch
        YouthAcademyManagementUseCase(repository, youthAcademyUseCase).upgradeAcademyLevel()
    }
}

fun GameViewModel.adjustAcademyInvestment(amount: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val repository = getActiveRepository() ?: return@launch
        YouthAcademyManagementUseCase(repository, youthAcademyUseCase).adjustAcademyInvestment(amount)
    }
}

fun GameViewModel.promoteAcademyProspect(prospect: GameViewModel.AcademyProspect): Job {
    return viewModelScope.launch(Dispatchers.IO) {
        val repository = getActiveRepository() ?: return@launch
        handleAcademyResult(
            YouthAcademyManagementUseCase(repository, youthAcademyUseCase)
                .promoteProspect(prospect.toDomainProspect(), selectedCountry.value)
        )
    }
}

fun GameViewModel.dismissAcademyProspect(prospect: GameViewModel.AcademyProspect) {
    viewModelScope.launch(Dispatchers.IO) {
        val repository = getActiveRepository() ?: return@launch
        YouthAcademyManagementUseCase(repository, youthAcademyUseCase)
            .dismissProspect(prospect.toDomainProspect())
    }
}

private suspend fun GameViewModel.handleAcademyResult(
    result: YouthAcademyManagementUseCase.AcademyResult
) {
    if (result is YouthAcademyManagementUseCase.AcademyResult.Rejected) {
        _toastMessage.emit(result.reason)
    }
}

private fun GameViewModel.AcademyProspect.toDomainProspect(): DomainAcademyProspect =
    DomainAcademyProspect(
        name = name,
        age = age,
        position = position,
        force = force,
        potential = potential
    )

private fun DomainAcademyProspect.toViewModelProspect(): GameViewModel.AcademyProspect =
    GameViewModel.AcademyProspect(
        name = name,
        age = age,
        position = position,
        force = force,
        potential = potential
    )
