package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.TransactionRecord
import com.example.data.TransferOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.example.data.repository.GameSaveRepository

data class TransferFilter(
    val query: String = "",
    val position: String = "TODAS",
    val maxPrice: Long = Long.MAX_VALUE,
    val minForce: Int = 0
)

@HiltViewModel
class TransfersViewModel @Inject constructor(
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
                ?: throw IllegalStateException("Nenhum save ativo configurado no TransfersViewModel.")
            return saveRepository.getRepositoryForSlot(slotId)
        }

    private val _filter = MutableStateFlow(TransferFilter())
    val filter: StateFlow<TransferFilter> = _filter.asStateFlow()

    private val _selectedPlayer = MutableStateFlow<Player?>(null)
    val selectedPlayer: StateFlow<Player?> = _selectedPlayer.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage = _toastMessage.asSharedFlow()

    val allPlayers: StateFlow<List<Player>> = _activeSlotId
        .flatMapLatest { slotId ->
            if (slotId == null) {
                flowOf(emptyList<Player>())
            } else {
                saveRepository.getRepositoryForSlot(slotId).allPlayersFlow
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val transferOrders: StateFlow<List<TransferOrder>> = _activeSlotId
        .flatMapLatest { slotId ->
            if (slotId == null) {
                flowOf(emptyList<TransferOrder>())
            } else {
                saveRepository.getRepositoryForSlot(slotId).allOrdersFlow
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val availableMarketPlayers: StateFlow<List<Player>> = combine(
        allPlayers,
        _filter
    ) { players, currentFilter ->
        players.filter { player ->
            val playerVal = player.calculateMarketValue()
            val matchesQuery = currentFilter.query.isBlank() || player.name.contains(currentFilter.query, ignoreCase = true)
            val matchesPos = currentFilter.position == "TODAS" || player.position.equals(currentFilter.position, ignoreCase = true)
            val matchesPrice = playerVal <= currentFilter.maxPrice
            val matchesForce = player.force >= currentFilter.minForce
            matchesQuery && matchesPos && matchesPrice && matchesForce
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateFilter(query: String? = null, position: String? = null, maxPrice: Long? = null, minForce: Int? = null) {
        val current = _filter.value
        _filter.value = current.copy(
            query = query ?: current.query,
            position = position ?: current.position,
            maxPrice = maxPrice ?: current.maxPrice,
            minForce = minForce ?: current.minForce
        )
    }

    fun selectPlayerForTransfer(player: Player?) {
        _selectedPlayer.value = player
    }

    fun buyPlayer(player: Player, userTeamId: Long, userBankBalance: Long, season: Int, week: Int, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cost = player.calculateMarketValue()
                if (userBankBalance < cost) {
                    _toastMessage.emit("Saldo insuficiente para contratar ${player.name}!")
                    kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete(false) }
                    return@launch
                }

                val updatedPlayer = player.copy(
                    teamId = userTeamId,
                    isStarter = false
                )
                repository.updatePlayer(updatedPlayer)

                // Save transaction record
                repository.saveTransaction(
                    TransactionRecord(
                        season = season,
                        week = week,
                        description = "Contratação de ${player.name}",
                        amount = -cost,
                        isIncome = false,
                        type = "TRANSFERENCIA"
                    )
                )

                // Deduct team balance
                val save = repository.getGameSave()
                if (save != null) {
                    repository.saveGameSave(save.copy(bankBalance = save.bankBalance - cost))
                }

                _toastMessage.emit("${player.name} foi contratado com sucesso!")
                _selectedPlayer.value = null
                kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                _toastMessage.emit("Erro ao contratar jogador: ${e.localizedMessage ?: "Erro desconhecido"}")
                kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun sellPlayer(player: Player, offerPrice: Long, season: Int, week: Int, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedPlayer = player.copy(
                teamId = -1L,
                isStarter = false
            )
            repository.updatePlayer(updatedPlayer)

            // Save transaction record
            repository.saveTransaction(
                TransactionRecord(
                    season = season,
                    week = week,
                    description = "Venda de ${player.name}",
                    amount = offerPrice,
                    isIncome = true,
                    type = "TRANSFERENCIA"
                )
            )

            // Increase team balance
            val save = repository.getGameSave()
            if (save != null) {
                repository.saveGameSave(save.copy(bankBalance = save.bankBalance + offerPrice))
            }

            _toastMessage.emit("${player.name} vendido por R$ ${offerPrice / 1_000_000}M!")
            onComplete(true)
        }
    }

    fun addTransferOrder(order: TransferOrder) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveOrder(order)
            _toastMessage.emit("Ordem de transferência adicionada!")
        }
    }

    fun cancelTransferOrder(order: TransferOrder) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteOrder(order)
            _toastMessage.emit("Ordem cancelada com sucesso.")
        }
    }
}
