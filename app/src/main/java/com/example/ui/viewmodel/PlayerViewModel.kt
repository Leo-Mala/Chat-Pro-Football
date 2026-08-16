package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.PlayerDataParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.data.repository.GameSaveRepository

sealed interface PlayerListUiState {
    object Loading : PlayerListUiState
    data class Success(
        val allPlayers: List<Player>,
        val filteredPlayers: List<Player>,
        val searchQuery: String = "",
        val selectedPosition: String? = null,
        val selectedTeamId: Long? = null
    ) : PlayerListUiState
    data class Error(val message: String) : PlayerListUiState
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    private val saveRepository: GameSaveRepository
) : AndroidViewModel(application) {

    private val _activeSlotId = MutableStateFlow<String?>(null)

    fun setSlotId(slotId: String) {
        _activeSlotId.value = slotId
    }

    fun clearSlot() {
        _activeSlotId.value = null
        _uiState.value = PlayerListUiState.Loading
    }

    private fun activeRepositoryOrNull(): GameRepository? {
        val slotId = _activeSlotId.value ?: return null
        return saveRepository.getRepositoryForSlot(slotId)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedPosition = MutableStateFlow<String?>(null)
    val selectedPosition = _selectedPosition.asStateFlow()

    private val _selectedTeamId = MutableStateFlow<Long?>(null)
    val selectedTeamId = _selectedTeamId.asStateFlow()

    private val _uiState = MutableStateFlow<PlayerListUiState>(PlayerListUiState.Loading)
    val uiState: StateFlow<PlayerListUiState> = _uiState.asStateFlow()

    init {
        loadAndObservePlayers()
    }

    private fun loadAndObservePlayers() {
        viewModelScope.launch {
            _activeSlotId
                .distinctUntilChanged()
                .flatMapLatest { slotId ->
                    if (slotId == null) {
                        flowOf(PlayerListUiState.Loading)
                    } else {
                        val repository = saveRepository.getRepositoryForSlot(slotId)
                        combine(
                            repository.allPlayersFlow,
                            _searchQuery,
                            _selectedPosition,
                            _selectedTeamId
                        ) { players, query, posFilter, teamFilter ->
                            if (players.isEmpty()) {
                                // Seed only the explicitly selected slot; never fall back to slot 1.
                                seedInitialPlayers(repository)
                                PlayerListUiState.Loading
                            } else {
                                val filtered = players.filter { player ->
                                    val matchesQuery = query.isBlank() ||
                                        player.name.contains(query, ignoreCase = true) ||
                                        player.nationality.contains(query, ignoreCase = true)

                                    val matchesPos = posFilter == null || player.position.equals(posFilter, ignoreCase = true)
                                    val matchesTeam = teamFilter == null || player.teamId == teamFilter

                                    matchesQuery && matchesPos && matchesTeam
                                }

                                PlayerListUiState.Success(
                                    allPlayers = players,
                                    filteredPlayers = filtered,
                                    searchQuery = query,
                                    selectedPosition = posFilter,
                                    selectedTeamId = teamFilter
                                )
                            }
                        }
                    }
                }
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    _uiState.value = PlayerListUiState.Error(e.message ?: "Erro desconhecido ao carregar jogadores")
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    private suspend fun seedInitialPlayers(repository: GameRepository) {
        val initialPlayers = listOf(
            Player(
                id = 252371L,
                teamId = 1L,
                name = "J. Bellingham",
                age = 22,
                nationality = "Inglaterra",
                position = "MEI",
                force = 90,
                energy = 100,
                moral = 90,
                salary = 320000L,
                contractDurationWeeks = 208,
                isFromAcademy = false,
                imageUrl = "https://cdn.sofifa.net/players/252/371/26_120.png",
                market_value = 174500000L,
                min_price = 148325000L,
                max_price = 370800000L,
                demand_level = "high",
                finishing = 88,
                passing = 83,
                pace = 80,
                strength = 80,
                vision = 90,
                defense = 78
            ),
            Player(
                id = 239053L,
                teamId = 1L,
                name = "F. Valverde",
                age = 26,
                nationality = "Uruguai",
                position = "MEI",
                force = 89,
                energy = 100,
                moral = 84,
                salary = 340000L,
                contractDurationWeeks = 208,
                isFromAcademy = false,
                imageUrl = "https://cdn.sofifa.net/players/239/053/26_120.png",
                market_value = 120500000L,
                min_price = 102425000L,
                max_price = 256100000L,
                demand_level = "high",
                finishing = 80,
                passing = 84,
                pace = 88,
                strength = 82,
                vision = 86,
                defense = 83
            ),
            Player(
                id = 238794L,
                teamId = 1L,
                name = "Vinícius Jr.",
                age = 24,
                nationality = "Brasil",
                position = "ATA",
                force = 91,
                energy = 100,
                moral = 92,
                salary = 380000L,
                contractDurationWeeks = 260,
                isFromAcademy = false,
                imageUrl = "https://cdn.sofifa.net/players/238/794/26_120.png",
                market_value = 180000000L,
                min_price = 155000000L,
                max_price = 390000000L,
                demand_level = "high",
                finishing = 89,
                passing = 81,
                pace = 95,
                strength = 68,
                vision = 85,
                defense = 32
            ),
            Player(
                id = 231747L,
                teamId = 1L,
                name = "Kylian Mbappé",
                age = 25,
                nationality = "França",
                position = "ATA",
                force = 91,
                energy = 100,
                moral = 88,
                salary = 450000L,
                contractDurationWeeks = 260,
                isFromAcademy = false,
                imageUrl = "https://cdn.sofifa.net/players/231/747/26_120.png",
                market_value = 180000000L,
                min_price = 155000000L,
                max_price = 390000000L,
                demand_level = "high",
                finishing = 92,
                passing = 80,
                pace = 97,
                strength = 77,
                vision = 83,
                defense = 36
            ),
            Player(
                id = 190871L,
                teamId = 2L,
                name = "Neymar Jr",
                age = 32,
                nationality = "Brasil",
                position = "MEI",
                force = 89,
                energy = 100,
                moral = 85,
                salary = 500000L,
                contractDurationWeeks = 104,
                isFromAcademy = false,
                imageUrl = "https://cdn.sofifa.net/players/190/871/26_120.png",
                market_value = 85000000L,
                min_price = 70000000L,
                max_price = 180000000L,
                demand_level = "high",
                finishing = 86,
                passing = 90,
                pace = 84,
                strength = 62,
                vision = 93,
                defense = 38
            ),
            Player(
                id = 271575L,
                teamId = 1L,
                name = "Endrick",
                age = 18,
                nationality = "Brasil",
                position = "ATA",
                force = 80,
                energy = 100,
                moral = 85,
                salary = 60000L,
                contractDurationWeeks = 260,
                isFromAcademy = true,
                imageUrl = "https://cdn.sofifa.net/players/271/575/26_120.png",
                market_value = 60000000L,
                min_price = 50000000L,
                max_price = 140000000L,
                demand_level = "high",
                finishing = 82,
                passing = 70,
                pace = 89,
                strength = 81,
                vision = 74,
                defense = 30
            )
        )
        repository.savePlayers(initialPlayers)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onPositionSelected(position: String?) {
        _selectedPosition.value = position
    }

    fun onTeamSelected(teamId: Long?) {
        _selectedTeamId.value = teamId
    }

    fun addPlayer(player: Player) {
        val repository = activeRepositoryOrNull() ?: return
        viewModelScope.launch {
            repository.savePlayers(listOf(player))
        }
    }

    fun updatePlayer(player: Player) {
        val repository = activeRepositoryOrNull() ?: return
        viewModelScope.launch {
            repository.updatePlayer(player)
        }
    }

    fun deletePlayer(playerId: Long) {
        val repository = activeRepositoryOrNull() ?: return
        viewModelScope.launch {
            repository.deletePlayer(playerId)
        }
    }
}
