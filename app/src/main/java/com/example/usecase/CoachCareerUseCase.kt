package com.example.usecase

import com.example.data.CoachOffer
import com.example.data.GameRepository

/**
 * Save-scoped coach-career mutations.
 *
 * UI state is intentionally not touched here. The caller only publishes the selected team after
 * the database transaction has committed successfully.
 */
class CoachCareerUseCase(private val repository: GameRepository) {

    sealed interface AcceptOfferResult {
        data class Success(val teamId: Long) : AcceptOfferResult
        data object Rejected : AcceptOfferResult
    }

    suspend fun acceptOffer(offer: CoachOffer): AcceptOfferResult = repository.withTransaction {
        val save = repository.getGameSave() ?: return@withTransaction AcceptOfferResult.Rejected
        if (repository.getTeam(offer.teamId) == null) {
            return@withTransaction AcceptOfferResult.Rejected
        }

        repository.saveGameSave(save.copy(playerTeamId = offer.teamId))
        repository.deleteOffers()
        AcceptOfferResult.Success(offer.teamId)
    }
}
