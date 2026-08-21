package com.example.usecase

import com.example.data.GameRepository
import com.example.data.Player

/**
 * Owns persistence rules for the youth academy while [YouthAcademyUseCase] remains the codec and
 * prospect generator. No UI state is mutated here; callers publish feedback only after commit.
 */
class YouthAcademyManagementUseCase(
    private val repository: GameRepository,
    private val academyCodec: YouthAcademyUseCase
) {
    sealed interface AcademyResult {
        data object Success : AcademyResult
        data object Unavailable : AcademyResult
        data class Rejected(val reason: String) : AcademyResult
    }

    suspend fun promoteProspect(
        prospect: AcademyProspect,
        fallbackCountry: String
    ): AcademyResult {
        val save = repository.getGameSave() ?: return AcademyResult.Unavailable
        val currentProspects = academyCodec.parseProspects(save.academyProspects).toMutableList()
        if (!currentProspects.remove(prospect)) {
            return AcademyResult.Rejected("A base mudou durante a promoção. Tente novamente.")
        }

        val teamCountry = repository.getTeam(save.playerTeamId)?.country ?: fallbackCountry
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

        val committed = repository.promoteAcademyPlayerAtomically(
            expectedPlayerTeamId = save.playerTeamId,
            expectedAcademyProspects = save.academyProspects,
            player = newPlayer,
            updatedAcademyProspects = academyCodec.serializeProspects(currentProspects)
        )
        return if (committed) AcademyResult.Success
        else AcademyResult.Rejected("A base mudou durante a promoção. Tente novamente.")
    }

    suspend fun upgradeAcademyLevel(): AcademyResult = repository.withTransaction {
        val save = repository.getGameSave() ?: return@withTransaction AcademyResult.Unavailable
        val newLevel = save.academyLevel + 1
        val cost = newLevel * 500_000L
        if (save.bankBalance < cost) {
            return@withTransaction AcademyResult.Rejected("Saldo insuficiente para melhorar a base.")
        }
        repository.saveGameSave(
            save.copy(
                academyLevel = newLevel,
                bankBalance = save.bankBalance - cost
            )
        )
        AcademyResult.Success
    }

    suspend fun adjustAcademyInvestment(amount: Long): AcademyResult = repository.withTransaction {
        val save = repository.getGameSave() ?: return@withTransaction AcademyResult.Unavailable
        repository.saveGameSave(save.copy(academyWeeklyInvestment = amount))
        AcademyResult.Success
    }

    suspend fun dismissProspect(prospect: AcademyProspect): AcademyResult = repository.withTransaction {
        val save = repository.getGameSave() ?: return@withTransaction AcademyResult.Unavailable
        val currentList = academyCodec.parseProspects(save.academyProspects).toMutableList()
        if (!currentList.remove(prospect)) {
            return@withTransaction AcademyResult.Rejected("A lista da base mudou. Tente novamente.")
        }
        repository.saveGameSave(
            save.copy(academyProspects = academyCodec.serializeProspects(currentList))
        )
        AcademyResult.Success
    }
}
