package com.example.usecase

import com.example.data.GameSave
import com.example.data.Player

/**
 * Keeps transfer-price and negotiation policy out of the ViewModel.
 *
 * The actual money/player mutation remains delegated to [ProcessTransfersUseCase], which owns the
 * transactional transfer rules. This class only decides the negotiation response and surfaces an
 * execution failure instead of reporting an accepted offer that was not persisted.
 */
class TransferNegotiationUseCase(
    private val transfers: ProcessTransfersUseCase
) {

    sealed interface NegotiationResult {
        data class Accepted(val message: String) : NegotiationResult
        data class Counter(val price: Long, val message: String) : NegotiationResult
        data class Declined(val reason: String) : NegotiationResult
    }

    suspend fun submitPurchaseOffer(
        save: GameSave,
        player: Player,
        offeredPrice: Long,
        installments: Int = 1
    ): NegotiationResult {
        val marketPrice = calculateDynamicPlayerPrice(player)
        if (offeredPrice < marketPrice * 0.8) {
            return NegotiationResult.Declined("Oferta muito baixa recusada pela diretoria.")
        }
        if (offeredPrice < marketPrice) {
            val counter = (marketPrice * 1.05).toLong()
            return NegotiationResult.Counter(
                price = counter,
                message = "O clube fez uma contraproposta de R$ $counter."
            )
        }

        return when (
            val result = transfers.buyPlayerAdvanced(
                save = save,
                player = player,
                offerPrice = offeredPrice,
                installments = installments
            )
        ) {
            is ProcessTransfersUseCase.TransferResult.Success ->
                NegotiationResult.Accepted("Oferta aceita! Jogador contratado.")
            is ProcessTransfersUseCase.TransferResult.Error ->
                NegotiationResult.Declined(result.reason)
        }
    }

    companion object {
        fun calculateDynamicPlayerPrice(player: Player): Long = player.calculateMarketValue()
    }
}
