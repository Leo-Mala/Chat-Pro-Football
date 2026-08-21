package com.example.ui.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun GameViewModel.hireStaff(staffType: String, cost: Long = 0L) {
    viewModelScope.launch(Dispatchers.IO) {
        repo.withTransaction {
            val save = repo.getGameSave() ?: return@withTransaction
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
}

fun GameViewModel.scoutPlayer(player: com.example.data.Player, level: Int = 1) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        scoutingUseCase.buyGlobalScoutReveal(save, 4)
    }
}

fun GameViewModel.signSponsorContract(sponsorName: String, weeklyPayout: Long, weeks: Int, bonus: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        financeUseCase.signSponsorshipContract(save, sponsorName, weeklyPayout, weeks, bonus)
    }
}

fun GameViewModel.adjustTicketPrice(newPrice: Double) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        financeUseCase.setTicketPrice(save, newPrice)
    }
}

fun GameViewModel.expandStadium(seatsToAdd: Int) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        financeUseCase.upgradeStadium(save, seatsToAdd)
    }
}

fun GameViewModel.takeBankLoan(amount: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        financeUseCase.requestLoan(save, amount)
    }
}

fun GameViewModel.repayBankLoan(amount: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        financeUseCase.repayLoan(save, amount)
    }
}
