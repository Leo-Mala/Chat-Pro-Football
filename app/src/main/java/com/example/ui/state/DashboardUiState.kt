package com.example.ui.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.example.data.Fixture
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team

@Immutable
data class DashboardUiState(
    val save: GameSave? = null,
    val playerTeam: Team? = null,
    val nextFixture: Fixture? = null,
    val opponentTeam: Team? = null,
    val squadPlayers: List<Player> = emptyList(),
    val isSimulating: Boolean = false,
    val isLoading: Boolean = false,
    val simulationWeek: Int = 1,
    val simulationCompName: String = "",
    val simulationMatchInfo: String = ""
)

@Stable
@Immutable
data class TransferMarketState(
    val playerBalance: Long = 0L,
    val transferList: List<Player> = emptyList(),
    val isProcessingOrder: Boolean = false,
    val feedbackMessage: String? = null
)
