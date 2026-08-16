package com.example.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.usecase.FinanceUseCase
import com.example.usecase.ScoutingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun GameViewModel.hireStaff(staffType: String, cost: Long = 0L) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        if (save.bankBalance >= cost) {
            val updated = when (staffType.uppercase()) {
                "COACH", "MEDICO", "DOCTOR" -> save.copy(hasHiredCoach = true, bankBalance = save.bankBalance - cost)
                "PREPARADOR", "PHYSICAL", "PHYSIO" -> save.copy(hasHiredPhysio = true, bankBalance = save.bankBalance - cost)
                else -> save.copy(bankBalance = save.bankBalance - cost)
            }
            repo.saveGameSave(updated)
        }
    }
}

fun GameViewModel.scoutPlayer(player: Player, level: Int = 1) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val result = scoutingUseCase.buyGlobalScoutReveal(save, 4)
        if (result is ScoutingUseCase.ScoutingResult.Success) {
            repo.saveGameSave(result.updatedSave)
        }
    }
}

fun GameViewModel.signSponsorContract(sponsorName: String, weeklyPayout: Long, weeks: Int, bonus: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val result = financeUseCase.signSponsorshipContract(save, sponsorName, weeklyPayout, weeks, bonus)
        if (result is FinanceUseCase.FinanceResult.Success) {
            repo.saveGameSave(result.updatedSave)
        }
    }
}

fun GameViewModel.adjustTicketPrice(newPrice: Double) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val result = financeUseCase.setTicketPrice(save, newPrice)
        if (result is FinanceUseCase.FinanceResult.Success) {
            repo.saveGameSave(result.updatedSave)
        }
    }
}

fun GameViewModel.upgradeTrainingCenter() {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val result = financeUseCase.upgradeTrainingCenter(save)
        if (result is FinanceUseCase.FinanceResult.Success) {
            repo.saveGameSave(result.updatedSave)
        }
    }
}

fun GameViewModel.expandStadium(seatsToAdd: Int) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val result = financeUseCase.upgradeStadium(save, seatsToAdd)
        if (result is FinanceUseCase.FinanceResult.Success) {
            repo.saveGameSave(result.updatedSave)
        }
    }
}

fun GameViewModel.takeBankLoan(amount: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val result = financeUseCase.requestLoan(save, amount)
        if (result is FinanceUseCase.FinanceResult.Success) {
            repo.saveGameSave(result.updatedSave)
        }
    }
}

fun GameViewModel.repayBankLoan(amount: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val result = financeUseCase.repayLoan(save, amount)
        if (result is FinanceUseCase.FinanceResult.Success) {
            repo.saveGameSave(result.updatedSave)
        }
    }
}
