package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.example.data.repository.GameSaveRepository

data class SquadTactics(
    val formation: String = "4-3-3",
    val style: String = "EQUILIBRADO",
    val captainId: Long? = null,
    val penaltyTakerId: Long? = null,
    val freeKickTakerId: Long? = null
)

@HiltViewModel
class SquadViewModel @Inject constructor(
    application: Application,
    private val saveRepository: GameSaveRepository
) : AndroidViewModel(application) {

    private val _activeSlotId = MutableStateFlow<String?>(null)

    fun setSlotId(slotId: String) {
        _activeSlotId.value = slotId
    }

    fun clearSlot() {
        _activeSlotId.value = null
    }

    private val repository: GameRepository
        get() {
            val slotId = _activeSlotId.value
                ?: throw IllegalStateException("Nenhum save ativo configurado no SquadViewModel.")
            return saveRepository.getRepositoryForSlot(slotId)
        }

    private val _tactics = MutableStateFlow(SquadTactics())
    val tactics: StateFlow<SquadTactics> = _tactics.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage = _toastMessage.asSharedFlow()

    private val activePlayersFlow = _activeSlotId
        .flatMapLatest { slotId ->
            if (slotId == null) {
                flowOf(emptyList<Player>())
            } else {
                saveRepository.getRepositoryForSlot(slotId).allPlayersFlow
            }
        }

    val allPlayers: StateFlow<List<Player>> = activePlayersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val injuredPlayers: StateFlow<List<Player>> = activePlayersFlow.map { players ->
        players.filter { it.injuryWeeksRemaining > 0 }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateFormation(newFormation: String) {
        _tactics.value = _tactics.value.copy(formation = newFormation)
        viewModelScope.launch {
            _toastMessage.emit("Tática alterada para $newFormation")
        }
    }

    fun updateTacticalStyle(newStyle: String) {
        _tactics.value = _tactics.value.copy(style = newStyle)
        viewModelScope.launch {
            _toastMessage.emit("Estilo de jogo alterado para $newStyle")
        }
    }

    fun setStarter(playerId: Long, isStarter: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val player = repository.getPlayer(playerId) ?: return@launch
            val roster = repository.getPlayersByTeam(player.teamId)

            if (isStarter) {
                val currentStarters = roster.filter { it.isStarter }
                if (player.position == "GOL") {
                    val currentGK = currentStarters.find { it.position == "GOL" }
                    if (currentGK != null) {
                        repository.updatePlayer(currentGK.copy(isStarter = false))
                    }
                    repository.updatePlayer(player.copy(isStarter = true))
                    _toastMessage.emit("${player.name} escalado como goleiro titular!")
                } else {
                    val fieldStarters = currentStarters.filter { it.position != "GOL" }
                    if (fieldStarters.size >= 10) {
                        val lowestField = fieldStarters.minByOrNull { it.force }
                        if (lowestField != null) {
                            repository.updatePlayer(lowestField.copy(isStarter = false))
                        }
                    }
                    repository.updatePlayer(player.copy(isStarter = true))
                    _toastMessage.emit("${player.name} escalado no time titular!")
                }
            } else {
                repository.updatePlayer(player.copy(isStarter = false))
                _toastMessage.emit("${player.name} movido para o banco de reservas.")
            }
        }
    }

    fun setCaptain(playerId: Long) {
        _tactics.value = _tactics.value.copy(captainId = playerId)
        viewModelScope.launch {
            val player = repository.getPlayer(playerId)
            if (player != null) {
                _toastMessage.emit("${player.name} é o novo Capitão da equipe!")
            }
        }
    }

    fun setPenaltyTaker(playerId: Long) {
        _tactics.value = _tactics.value.copy(penaltyTakerId = playerId)
        viewModelScope.launch {
            val player = repository.getPlayer(playerId)
            if (player != null) {
                _toastMessage.emit("${player.name} é o cobrador oficial de pênaltis!")
            }
        }
    }

    fun treatInjuryInMedicalDepartment(player: Player, treatmentCost: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (player.injuryWeeksRemaining <= 0) return@launch

            val newWeeks = (player.injuryWeeksRemaining - 1).coerceAtLeast(0)
            repository.updatePlayer(player.copy(injuryWeeksRemaining = newWeeks))

            val save = repository.getGameSave()
            if (save != null && save.bankBalance >= treatmentCost) {
                repository.saveGameSave(save.copy(bankBalance = save.bankBalance - treatmentCost))
                _toastMessage.emit("Tratamento acelerado para ${player.name}!")
            } else {
                _toastMessage.emit("Tratamento básico realizado para ${player.name}.")
            }
        }
    }

    fun autoSelectBestStartingXI(userTeamId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val players = repository.getPlayersByTeam(userTeamId)
            val available = players.filter { it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0 }

            // Reset all starters
            players.forEach { repository.updatePlayer(it.copy(isStarter = false)) }

            // Select best GK
            val bestGK = available.filter { it.position == "GOL" }.maxByOrNull { it.force }
            bestGK?.let { repository.updatePlayer(it.copy(isStarter = true)) }

            // Select 10 best field players
            val fieldPlayers = available.filter { it.position != "GOL" }.sortedByDescending { it.force }.take(10)
            fieldPlayers.forEach { repository.updatePlayer(it.copy(isStarter = true)) }

            _toastMessage.emit("Escalação ideal selecionada automaticamente!")
        }
    }
}
