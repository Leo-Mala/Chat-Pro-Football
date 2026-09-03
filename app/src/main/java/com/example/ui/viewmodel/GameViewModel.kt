package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.usecase.SimulateWeekUseCase
import com.example.usecase.ProcessTransfersUseCase
import com.example.usecase.GenerateCalendarUseCase
import com.example.usecase.DatabaseIntegrityUseCase
import android.util.Log
import com.example.ui.state.DashboardUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import com.example.data.repository.GameSaveRepository
import com.example.data.model.SaveSlotMetadata

data class IncomingOffer(
    val id: Long,
    val player: Player,
    val buyerTeamName: String,
    val buyerTeamId: Long = 0L,
    val offerType: String, // "COMPRA" ou "EMPRESTIMO"
    val price: Long,
    val durationWeeks: Int = 0 // For loan, 0 for buy
)

internal fun shouldStopSeasonSimulation(targetSeason: Int, currentSeason: Int): Boolean =
    currentSeason != targetSeason

internal fun shouldPauseSeasonSimulationForExpiringContracts(expiringContractCount: Int): Boolean =
    expiringContractCount > 0

@HiltViewModel
class GameViewModel @Inject constructor(
    application: Application,
    private val saveRepository: GameSaveRepository,
    internal val preferencesRepo: GamePreferencesRepository,
    internal val youthAcademyUseCase: com.example.usecase.YouthAcademyUseCase,
    internal val tacticsUseCase: com.example.usecase.TacticsUseCase
) : AndroidViewModel(application) {

    data class IAOfferResult(
        val status: String,
        val counterPrice: Long = 0L,
        val message: String
    )

    data class AcademyProspect(
        val name: String,
        val age: Int,
        val position: String,
        val force: Int,
        val potential: Int
    )
    // MULTI-SAVE & SESSION SUPPORT
    internal val _currentSaveId = MutableStateFlow<String?>(null)
    val currentSaveId = _currentSaveId.asStateFlow()

    val coachAvatarId: StateFlow<String> = currentSaveId.flatMapLatest { saveId ->
        if (saveId.isNullOrBlank()) {
            flowOf(GamePreferencesRepository.DEFAULT_COACH_AVATAR_ID)
        } else {
            preferencesRepo.coachAvatarId(saveId)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        GamePreferencesRepository.DEFAULT_COACH_AVATAR_ID
    )

    private val sessionGeneration = java.util.concurrent.atomic.AtomicLong(0L)
    private val _activeSaveSession = MutableStateFlow<SaveSession?>(null)
    val activeSaveSession = _activeSaveSession.asStateFlow()

    fun getOrCreateSession(saveId: String): SaveSession {
        val current = _activeSaveSession.value
        if (current != null && current.slotId == saveId) {
            return current
        }
        val gen = sessionGeneration.incrementAndGet()
        val repository = saveRepository.getRepositoryForSlot(saveId)
        val session = SaveSession(saveId, repository, gen)
        _activeSaveSession.value = session
        return session
    }

    internal val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage = _toastMessage.asSharedFlow()

    val saveSlots = MutableStateFlow<List<SaveSlotMetadata>>(emptyList())

    // Active repository flow
    internal val activeRepositoryFlow: Flow<GameRepository?> = _currentSaveId.map { saveId ->
        if (saveId == null) {
            null
        } else {
            getOrCreateSession(saveId).repository
        }
    }.shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    fun getActiveRepository(): GameRepository? {
        val saveId = _currentSaveId.value ?: return null
        return getOrCreateSession(saveId).repository
    }

    internal val repo: GameRepository
        get() = getActiveRepository() ?: throw IllegalStateException("Nenhum save ativo selecionado.")

    // Flow states
    val gameSave: StateFlow<GameSave?> = activeRepositoryFlow.flatMapLatest { r ->
        r?.gameSaveFlow ?: flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allTeams: StateFlow<List<Team>> = activeRepositoryFlow.flatMapLatest { r ->
        r?.allTeamsFlow ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlayers: StateFlow<List<Player>> = activeRepositoryFlow.flatMapLatest { r ->
        r?.allPlayersFlow ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val seasonScorers: StateFlow<List<Player>> = activeRepositoryFlow.flatMapLatest { r ->
        r?.seasonScorersFlow ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFixtures: StateFlow<List<Fixture>> = activeRepositoryFlow.flatMapLatest { r ->
        r?.allFixturesFlow ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historicalRecords: StateFlow<List<HistoricalRecord>> = activeRepositoryFlow.flatMapLatest { r ->
        r?.allRecordsFlow ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val clubLegends: StateFlow<List<ClubLegend>> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) {
            flowOf(emptyList())
        } else {
            r.gameSaveFlow.flatMapLatest { save ->
                val teamId = save?.playerTeamId ?: 0L
                r.getLegendsForTeamFlow(teamId)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val coachOffers: StateFlow<List<CoachOffer>> = activeRepositoryFlow.flatMapLatest { r ->
        r?.coachOffersFlow ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactionHistory: StateFlow<List<TransactionRecord>> = activeRepositoryFlow.flatMapLatest { r ->
        r?.allTransactionsFlow ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transferOrders: StateFlow<List<TransferOrder>> = activeRepositoryFlow.flatMapLatest { r ->
        r?.allOrdersFlow ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getStartingXIForTeam(
        players: List<Player>,
        teamId: Long = 0L,
        teamRating: Int = 70,
        teamName: String = "Time",
        country: String = "Brasil"
    ): List<Player> {
        val available = players.filter { it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0 }.toMutableList()
        if (available.isEmpty()) {
            val generated = DefaultData.generateRosterForTeam(teamId, teamRating, teamName, country)
            available.addAll(generated)
        }

        val selectedGoalkeeper = GameEngine.selectMatchGoalkeeper(available)
        val startingXI = mutableListOf<Player>()
        selectedGoalkeeper?.let { startingXI.add(it) }

        val chosenStarters = available.filter {
            it.isStarter && it.id != selectedGoalkeeper?.id
        }
        startingXI.addAll(chosenStarters.take(11 - startingXI.size))
        if (startingXI.size < 11) {
            val selectedIds = startingXI.mapTo(hashSetOf()) { it.id }
            val remaining = available
                .filter { it.id !in selectedIds }
                .sortedByDescending { it.force }
                .take(11 - startingXI.size)
            startingXI.addAll(remaining)
        }
        return startingXI.take(11)
    }

    fun setPlayerStarter(playerId: Long, isStarter: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = lineupUseCase.setPlayerStarter(playerId, isStarter)
                _toastMessage.emit(result.message)
            } catch (e: Exception) {
                Log.e("GameViewModel", "Erro ao alterar titularidade de jogador", e)
            }
        }
    }

    fun swapPlayers(starterId: Long, benchId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = lineupUseCase.swapPlayers(starterId, benchId)
                _toastMessage.emit(result.message)
            } catch (e: Exception) {
                Log.e("GameViewModel", "Erro ao trocar jogadores da escalação", e)
            }
        }
    }

    fun autoReplaceSuspendedAndInjuredPlayers(teamId: Long): kotlinx.coroutines.Job {
        return viewModelScope.launch(Dispatchers.IO) {
            val players = repo.getPlayersByTeam(teamId)
            val starters = players.filter { it.isStarter }
            val reserves = players.filter { !it.isStarter }
            val updatedPlayers = mutableListOf<Player>()
            val takenReserves = mutableSetOf<Long>()

            for (starter in starters) {
                if (starter.suspensionWeeksRemaining > 0 || starter.injuryWeeksRemaining > 0) {
                    // Find a replacement from the reserves of the SAME position, who is not injured or suspended
                    val candidate = reserves
                        .filter { it.position == starter.position && it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0 && it.id !in takenReserves }
                        .maxByOrNull { it.force }
                    
                    if (candidate != null) {
                        updatedPlayers.add(starter.copy(isStarter = false))
                        updatedPlayers.add(candidate.copy(isStarter = true))
                        takenReserves.add(candidate.id)
                    } else {
                        // If no one of the exact same position is available, find ANY reserve who is available
                        val anyCandidate = reserves
                            .filter { it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0 && it.id !in takenReserves }
                            .maxByOrNull { it.force }
                        if (anyCandidate != null) {
                            updatedPlayers.add(starter.copy(isStarter = false))
                            updatedPlayers.add(anyCandidate.copy(isStarter = true))
                            takenReserves.add(anyCandidate.id)
                        }
                    }
                }
            }

            if (updatedPlayers.isNotEmpty()) {
                repo.updatePlayers(updatedPlayers)
            }
        }
    }

    // UI state derivations
    internal val _selectedTeamId = MutableStateFlow<Long?>(null)
    val selectedTeamId: StateFlow<Long?> = _selectedTeamId.asStateFlow()

    val playerTeam: StateFlow<Team?> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) {
            flowOf(null)
        } else {
            r.gameSaveFlow.flatMapLatest { save ->
                val teamId = save?.playerTeamId ?: 0L
                if (teamId > 0L) r.getTeamFlow(teamId) else flowOf(null)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Dashboard league context is intentionally scoped to the controlled club instead of
     * observing every Team/Fixture row in the save. Repository switching remains flatMapLatest,
     * so an old slot cannot keep feeding the new session.
     */
    val playerLeagueTeams: StateFlow<List<Team>> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) {
            flowOf(emptyList())
        } else {
            r.gameSaveFlow.flatMapLatest { save ->
                val teamId = save?.playerTeamId ?: 0L
                if (teamId <= 0L) {
                    flowOf(emptyList())
                } else {
                    r.getTeamFlow(teamId).flatMapLatest { team ->
                        if (team == null) flowOf(emptyList())
                        else r.getTeamsByCountryDivisionFlow(team.country, team.division)
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playerLeagueFixtures: StateFlow<List<Fixture>> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) {
            flowOf(emptyList())
        } else {
            r.gameSaveFlow.flatMapLatest { save ->
                val teamId = save?.playerTeamId ?: 0L
                if (save == null || teamId <= 0L) {
                    flowOf(emptyList())
                } else {
                    r.getTeamFlow(teamId).flatMapLatest { team ->
                        if (team == null) {
                            flowOf(emptyList())
                        } else {
                            val competitionType = when (team.division) {
                                1 -> "SERIE_A"
                                2 -> "SERIE_B"
                                3 -> "SERIE_C"
                                else -> "SERIE_D"
                            }
                            r.getPlayedFixturesForCompetitionFlow(
                                season = save.currentSeason,
                                competitionType = competitionType
                            )
                        }
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playerPlayedFixtures: StateFlow<List<Fixture>> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) {
            flowOf(emptyList())
        } else {
            r.gameSaveFlow.flatMapLatest { save ->
                val teamId = save?.playerTeamId ?: 0L
                if (teamId > 0L) r.getPlayedFixturesForTeamFlow(teamId) else flowOf(emptyList())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardSeasonFixtures: StateFlow<List<Fixture>> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) {
            flowOf(emptyList())
        } else {
            r.gameSaveFlow.flatMapLatest { save ->
                if (save == null) flowOf(emptyList())
                else r.getFixturesForSeasonFlow(save.currentSeason)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardSeasonTeams: StateFlow<List<Team>> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) {
            flowOf(emptyList())
        } else {
            r.gameSaveFlow.flatMapLatest { save ->
                if (save == null) {
                    flowOf(emptyList())
                } else {
                    r.getFixturesForSeasonFlow(save.currentSeason).flatMapLatest { fixtures ->
                        val teamIds = fixtures
                            .flatMap { listOf(it.homeTeamId, it.awayTeamId) }
                            .filter { it > 0L }
                            .distinct()
                        if (teamIds.isEmpty()) flowOf(emptyList())
                        else r.getTeamsByIdsFlow(teamIds)
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nextOpponentTeam: StateFlow<Team?> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) {
            flowOf(null)
        } else {
            r.gameSaveFlow.flatMapLatest { save ->
                if (save == null || save.playerTeamId <= 0L) {
                    flowOf(null)
                } else {
                    r.getNextFixtureForTeamFlow(
                        season = save.currentSeason,
                        week = save.currentWeek,
                        teamId = save.playerTeamId
                    ).flatMapLatest { fixture ->
                        if (fixture == null) {
                            flowOf(null)
                        } else {
                            val opponentId = if (fixture.homeTeamId == save.playerTeamId) {
                                fixture.awayTeamId
                            } else {
                                fixture.homeTeamId
                            }
                            if (opponentId > 0L) r.getTeamFlow(opponentId) else flowOf(null)
                        }
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val playerRoster: StateFlow<List<Player>> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) {
            flowOf(emptyList())
        } else {
            r.gameSaveFlow.flatMapLatest { save ->
                val teamId = save?.playerTeamId ?: 0L
                if (teamId > 0L) r.getPlayersForTeamFlow(teamId) else flowOf(emptyList())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentWeekFixtures: StateFlow<List<Fixture>> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) {
            flowOf(emptyList())
        } else {
            r.gameSaveFlow.flatMapLatest { save ->
                if (save == null) flowOf(emptyList())
                else r.getFixturesForWeekFlow(save.currentSeason, save.currentWeek)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playerNextFixture: StateFlow<Fixture?> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) {
            flowOf(null)
        } else {
            r.gameSaveFlow.flatMapLatest { save ->
                if (save == null || save.playerTeamId <= 0L) flowOf(null)
                else r.getNextFixtureForTeamFlow(save.currentSeason, save.currentWeek, save.playerTeamId)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Domain UseCases Composition
    val simulateWeekUseCase: SimulateWeekUseCase get() = SimulateWeekUseCase(repo)
    val processTransfersUseCase: ProcessTransfersUseCase get() = ProcessTransfersUseCase(repo)
    val generateCalendarUseCase: GenerateCalendarUseCase get() = GenerateCalendarUseCase(repo)
    val financeUseCase: com.example.usecase.FinanceUseCase get() = com.example.usecase.FinanceUseCase(repo)
    val scoutingUseCase: com.example.usecase.ScoutingUseCase get() = com.example.usecase.ScoutingUseCase(repo)
    val playerEvolutionUseCase: com.example.usecase.PlayerEvolutionUseCase get() = com.example.usecase.PlayerEvolutionUseCase(repo)
    val lineupUseCase: com.example.usecase.LineupUseCase get() = com.example.usecase.LineupUseCase(repo)

    // Match Engine UI states
    internal val _matchState = MutableStateFlow(MatchState.IDLE)
    val matchState: StateFlow<MatchState> = _matchState.asStateFlow()

    internal val _matchMinute = MutableStateFlow(0)
    val matchMinute: StateFlow<Int> = _matchMinute.asStateFlow()

    internal val _matchHomeScore = MutableStateFlow(0)
    val matchHomeScore: StateFlow<Int> = _matchHomeScore.asStateFlow()

    internal val _matchAwayScore = MutableStateFlow(0)
    val matchAwayScore: StateFlow<Int> = _matchAwayScore.asStateFlow()

    internal val _matchEvents = MutableStateFlow<List<GameEngine.MatchEventDetail>>(emptyList())
    val matchEvents: StateFlow<List<GameEngine.MatchEventDetail>> = _matchEvents.asStateFlow()

    // Temporary storage for live match details
    var liveMatchFixture: Fixture? = null
    var liveMatchHomeTeam: Team? = null
    var liveMatchAwayTeam: Team? = null
    var liveMatchHomePlayers: List<Player> = emptyList()
    var liveMatchAwayPlayers: List<Player> = emptyList()
    internal var currentMatchEvents: List<GameEngine.MatchEventDetail> = emptyList()
    internal var liveMatchJob: kotlinx.coroutines.Job? = null
    internal var hasSelfHealedThisSave = false
    private val simulationMutex = Mutex()
    val isStartingNewGame = MutableStateFlow(false)
    val _isStartingNewGame = isStartingNewGame
    val isProcessingAction = MutableStateFlow(false)
    val isProcessingActionFlow: StateFlow<Boolean> = isProcessingAction.asStateFlow()

    val liveHomeFormation = MutableStateFlow("4-4-2")
    val liveHomeStyle = MutableStateFlow("Equilibrada")
    val liveAwayFormation = MutableStateFlow("4-4-2")
    val liveAwayStyle = MutableStateFlow("Equilibrada")
    val liveMatchSpeed = MutableStateFlow("Tempo Real")
    val liveTacticalFeedback = MutableStateFlow<String?>(null)

    fun changeLiveHomeFormation(newFormation: String) {
        liveHomeFormation.value = newFormation
        triggerTacticalFeedback("Formação alterada para $newFormation!")
        recalculateRemainingEvents()
    }

    fun changeLiveHomeStyle(newStyle: String) {
        liveHomeStyle.value = newStyle
        triggerTacticalFeedback("Mentalidade alterada para $newStyle!")
        recalculateRemainingEvents()
    }

    fun changeLiveMatchSpeed(newSpeed: String) {
        liveMatchSpeed.value = newSpeed
    }

    // Monthly Evolution Summary State
    internal val _monthlyEvolutionSummary = MutableStateFlow<List<PlayerEvolutionResult>?>(null)
    val monthlyEvolutionSummary: StateFlow<List<PlayerEvolutionResult>?> = _monthlyEvolutionSummary.asStateFlow()

    fun updatePlayerTrainingFocus(player: Player, focus: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = player.copy(focoTreino = focus)
            repo.updatePlayer(updated)
            _toastMessage.emit("Foco de treino de ${player.name} alterado para $focus!")
        }
    }

    fun upgradeTrainingCenter() {
        viewModelScope.launch(Dispatchers.IO) {
            val message = repo.withTransaction {
                val save = repo.getGameSave() ?: return@withTransaction null
                val team = repo.getTeam(save.playerTeamId) ?: return@withTransaction null
                val cost = 2_000_000L * team.trainingCenterLevel
                if (save.bankBalance >= cost && team.trainingCenterLevel < 5) {
                    val updatedTeam = team.copy(trainingCenterLevel = team.trainingCenterLevel + 1)
                    val updatedSave = save.copy(bankBalance = save.bankBalance - cost)
                    repo.updateTeam(updatedTeam)
                    repo.saveGameSave(updatedSave)
                    "Centro de Treinamento evoluído para Nível ${updatedTeam.trainingCenterLevel}!"
                } else {
                    "Saldo insuficiente ou nível máximo atingido."
                }
            }
            if (message != null) {
                _toastMessage.emit(message)
            }
        }
    }

    fun advanceMonthAndRunEvolution() {
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val periodDate = "${save.currentSeason}-${save.currentWeek}"
        val outcome = playerEvolutionUseCase.executeMonthlyEvolutionDetailed(save, periodDate)
        if (!outcome.committed) {
            _monthlyEvolutionSummary.value = null
            _toastMessage.emit(
                "O estado de treino mudou durante a evolução mensal. Nenhuma alteração foi aplicada; tente novamente."
            )
            return@launch
        }

        // Exibe modal de resumo do time do usuário somente após o commit atômico de jogadores + histórico.
        _monthlyEvolutionSummary.value =
            outcome.results.filter { it.player.teamId == save.playerTeamId }
        _toastMessage.emit("Evolução mensal processada para todo o elenco!")
    }
}

    fun dismissMonthlyEvolutionSummary() {
        _monthlyEvolutionSummary.value = null
    }

    internal val _gameSpeed = mutableStateOf(1)
    val gameSpeed: State<Int> = _gameSpeed
    fun setGameSpeed(speed: Int) { _gameSpeed.value = speed.coerceIn(1, 3) }

    internal fun triggerTacticalFeedback(message: String) {
        liveTacticalFeedback.value = message
        viewModelScope.launch {
            delay(3000)
            if (liveTacticalFeedback.value == message) {
                liveTacticalFeedback.value = null
            }
        }
    }

    fun recalculateRemainingEvents() {
        val fixture = liveMatchFixture ?: return
        val home = liveMatchHomeTeam ?: return
        val away = liveMatchAwayTeam ?: return
        val homeStarters = liveMatchHomePlayers
        val awayStarters = liveMatchAwayPlayers
        
        val currentMin = _matchMinute.value
        
        // Filter played events
        val playedEvents = currentMatchEvents.filter { it.minute <= currentMin }
        
        viewModelScope.launch(Dispatchers.Default) {
            val isRivalry = (home.rivalTeamId == away.id || away.rivalTeamId == home.id || (home.state == away.state && home.city == away.city))
            val newAllEvents = GameEngine.simulateMatchDetailed(
                homeTeam = home,
                awayTeam = away,
                homePlayers = homeStarters,
                awayPlayers = awayStarters,
                homeTactics = liveHomeFormation.value,
                homeStyle = liveHomeStyle.value,
                awayTactics = liveAwayFormation.value,
                awayStyle = liveAwayStyle.value,
                isRivalry = isRivalry,
                randomSeed = Random.nextLong()
            )
            
            // Keep new events with minute > currentMin
            val remainingEvents = newAllEvents.filter { it.minute > currentMin }
            
            // Merge
            currentMatchEvents = playedEvents + remainingEvents
        }
    }

    fun getDelayForSpeed(speed: String): Long {
        return when (speed) {
            "Tempo Real", "1x", "TR" -> 1600L
            "1.5x" -> 1100L
            "2x" -> 800L
            "4x" -> 350L
            "8x" -> 150L
            "10x", "MAX" -> 50L
            else -> 1600L
        }
    }

    fun exitLiveMatch() {
        // All durable match/week work is completed before FINISHED is exposed.
        // Returning to Central is therefore immediate and safe to repeat.
        liveMatchJob?.cancel()
        liveMatchJob = null
        _matchState.value = MatchState.IDLE
        liveMatchFixture = null
        liveMatchHomeTeam = null
        liveMatchAwayTeam = null
        liveMatchHomePlayers = emptyList()
        liveMatchAwayPlayers = emptyList()
        currentMatchEvents = emptyList()
    }

    // Watchlist StateFlow
    internal val _watchlist = MutableStateFlow<Set<Long>>(emptySet())
    val watchlist: StateFlow<Set<Long>> = _watchlist.asStateFlow()

    internal fun loadWatchlist() {
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepo.watchlistPlayers.collect { set ->
                _watchlist.value = set
            }
        }
    }

    fun toggleWatchlistPlayer(playerId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = preferencesRepo.toggleWatchlistPlayer(playerId)
            _watchlist.value = updated
        }
    }

    // Tactical defaults inside the VM mapping directly from gameSave flow for database persistence
    val playerFormation: StateFlow<String> = gameSave.map { it?.playerFormation ?: "4-4-2" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "4-4-2")

    val playerStyle: StateFlow<String> = gameSave.map { it?.playerStyle ?: "Equilibrado" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Equilibrado")

    // Transfer search query state
    internal val _transferSearchPos = MutableStateFlow("TODOS")
    val transferSearchPos: StateFlow<String> = _transferSearchPos.asStateFlow()

    internal val _transferSearchMinForce = MutableStateFlow(40)
    val transferSearchMinForce: StateFlow<Int> = _transferSearchMinForce.asStateFlow()

    internal val _transferSearchMaxAge = MutableStateFlow(40)
    val transferSearchMaxAge: StateFlow<Int> = _transferSearchMaxAge.asStateFlow()

    internal val _transferSearchMaxPrice = MutableStateFlow(500000000L)
    val transferSearchMaxPrice: StateFlow<Long> = _transferSearchMaxPrice.asStateFlow()

    internal val _transferSearchSortBy = MutableStateFlow("FORCA_DESC")
    val transferSearchSortBy: StateFlow<String> = _transferSearchSortBy.asStateFlow()

    // State for transfer offers received for the player's squad
    internal val _incomingOffers = MutableStateFlow<List<IncomingOffer>>(emptyList())
    val incomingOffers: StateFlow<List<IncomingOffer>> = _incomingOffers.asStateFlow()

    internal val _selectedCountry = MutableStateFlow("Brasil")
    val selectedCountry: StateFlow<String> = _selectedCountry.asStateFlow()

    // Autosave config State
    internal val _autoSaveEnabled = MutableStateFlow(true)
    val autoSaveEnabled: StateFlow<Boolean> = _autoSaveEnabled.asStateFlow()

    // Infinite stamina cheat/setting State
    internal val _infiniteStaminaEnabled = MutableStateFlow(false)
    val infiniteStaminaEnabled: StateFlow<Boolean> = _infiniteStaminaEnabled.asStateFlow()

    // Auto lineup setting State
    internal val _autoLineupEnabled = MutableStateFlow(false)
    val autoLineupEnabled: StateFlow<Boolean> = _autoLineupEnabled.asStateFlow()

    // Season simulation States
    internal val _isSimulatingSeason = MutableStateFlow(false)
    val isSimulatingSeason: StateFlow<Boolean> = _isSimulatingSeason.asStateFlow()

    internal val _simulationCurrentWeek = MutableStateFlow(0)
    val simulationCurrentWeek: StateFlow<Int> = _simulationCurrentWeek.asStateFlow()

    internal val _simulationCompetitionName = MutableStateFlow("")
    val simulationCompetitionName: StateFlow<String> = _simulationCompetitionName.asStateFlow()

    internal val _simulationMatchInfo = MutableStateFlow("")
    val simulationMatchInfo: StateFlow<String> = _simulationMatchInfo.asStateFlow()

    internal val _simulationLogs = MutableStateFlow<List<String>>(emptyList())
    val simulationLogs: StateFlow<List<String>> = _simulationLogs.asStateFlow()

    val dashboardUiState: StateFlow<DashboardUiState> = combine(
        gameSave,
        playerTeam,
        playerNextFixture,
        playerRoster,
        _isSimulatingSeason,
        _simulationCurrentWeek,
        _simulationCompetitionName,
        _simulationMatchInfo,
        isStartingNewGame
    ) { args ->
        val save = args[0] as? GameSave
        val team = args[1] as? Team
        val nextFix = args[2] as? Fixture
        @Suppress("UNCHECKED_CAST")
        val roster = (args[3] as? List<Player>) ?: emptyList()
        val isSim = (args[4] as? Boolean) ?: false
        val simWeek = (args[5] as? Int) ?: 1
        val simComp = (args[6] as? String) ?: ""
        val simMatch = (args[7] as? String) ?: ""
        val isStartingNew = (args[8] as? Boolean) ?: false

        DashboardUiState(
            save = save,
            playerTeam = team,
            nextFixture = nextFix,
            squadPlayers = roster,
            isSimulating = isSim,
            isLoading = isSim || isStartingNew,
            simulationWeek = simWeek,
            simulationCompName = simComp,
            simulationMatchInfo = simMatch
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    // Save status discrete feedback message
    internal val _saveStatus = MutableStateFlow<String?>(null)
    val saveStatus: StateFlow<String?> = _saveStatus.asStateFlow()

    // Database validation state
    internal val _validationResult = MutableStateFlow<DatabaseValidationResult?>(null)
    val validationResult: StateFlow<DatabaseValidationResult?> = _validationResult.asStateFlow()

    // Tactical roles states mapping from gameSave flow
    val captainPlayerId: StateFlow<Long?> = gameSave.map { it?.captainPlayerId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val penaltyPlayerId: StateFlow<Long?> = gameSave.map { it?.penaltyPlayerId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val freekickPlayerId: StateFlow<Long?> = gameSave.map { it?.freekickPlayerId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val cornerPlayerId: StateFlow<Long?> = gameSave.map { it?.cornerPlayerId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setTacticalRole(role: String, playerId: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.withTransaction {
                val save = repo.getGameSave() ?: return@withTransaction
                val updatedSave = when (role) {
                    "CAPTAIN" -> save.copy(captainPlayerId = playerId)
                    "PENALTY" -> save.copy(penaltyPlayerId = playerId)
                    "FREEKICK" -> save.copy(freekickPlayerId = playerId)
                    "CORNER" -> save.copy(cornerPlayerId = playerId)
                    else -> save
                }
                repo.saveGameSave(updatedSave)
            }
        }
    }

    fun setAutoSaveEnabled(enabled: Boolean) {
        _autoSaveEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepo.setAutoSaveEnabled(enabled)
        }
    }

    fun setInfiniteStaminaEnabled(enabled: Boolean) {
        _infiniteStaminaEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepo.setInfiniteStaminaEnabled(enabled)
            if (enabled) {
                val save = repo.getGameSave()
                if (save != null) {
                    val myPlayers = repo.getPlayersByTeam(save.playerTeamId)
                    if (myPlayers.isNotEmpty()) {
                        val fullyRestored = myPlayers.map { it.copy(energy = 100) }
                        repo.updatePlayers(fullyRestored)
                    }
                }
            }
        }
    }

    fun setAutoLineupEnabled(enabled: Boolean) {
        _autoLineupEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepo.setAutoLineupEnabled(enabled)
        }
    }

    fun setCoachAvatarId(avatarId: String) {
        val saveId = _currentSaveId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepo.setCoachAvatarId(saveId, avatarId)
        }
    }

    internal fun getFormationRoles(formation: String): List<String> {
        return tacticsUseCase.getFormationRoles(formation)
    }

    fun autoLineup(teamId: Long, showToast: Boolean = false): kotlinx.coroutines.Job {
        return viewModelScope.launch(Dispatchers.IO) {
            val save = repo.getGameSave() ?: return@launch
            val formation = save.playerFormation
            val roster = repo.getPlayersByTeam(teamId)
            
            // Filter uninjured and unsuspended players
            val available = roster.filter { it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0 }
            if (available.size < 11) {
                if (showToast) {
                    _toastMessage.emit("Aviso: Elenco muito reduzido para fazer a escalação automática!")
                }
                return@launch
            }
            
            val selectedStarters = tacticsUseCase.selectAutoLineup(available, formation).toSet()

            // 5. Update database: all unselected players in roster are bench (isStarter = false), selected are starters
            val updatedPlayers = mutableListOf<Player>()
            for (player in roster) {
                val shouldBeStarter = player in selectedStarters
                if (player.isStarter != shouldBeStarter) {
                    updatedPlayers.add(player.copy(isStarter = shouldBeStarter))
                }
            }
            
            if (updatedPlayers.isNotEmpty()) {
                repo.updatePlayers(updatedPlayers)
            }
            if (showToast) {
                _toastMessage.emit("Escalação automática concluída com base na tática $formation!")
            }
        }
    }

    internal suspend fun simulateSingleUserFixture(userFixture: Fixture, save: GameSave): Fixture {
        val home = repo.getTeam(userFixture.homeTeamId) ?: GlobalFootballSystem.getVirtualTeam(userFixture.homeTeamId)
        val away = repo.getTeam(userFixture.awayTeamId) ?: GlobalFootballSystem.getVirtualTeam(userFixture.awayTeamId)
        
        if (_autoLineupEnabled.value) {
            autoLineup(save.playerTeamId).join()
        } else {
            autoReplaceSuspendedAndInjuredPlayers(save.playerTeamId).join()
        }
        
        val homePls = repo.getPlayersByTeam(home.id)
        val awayPls = repo.getPlayersByTeam(away.id)
        val homeStarters = getStartingXIForTeam(homePls, home.id, home.rating, home.name, home.country)
        val awayStarters = getStartingXIForTeam(awayPls, away.id, away.rating, away.name, away.country)
        val homeReserves = homePls.filter { it !in homeStarters && it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0 }
        val awayReserves = awayPls.filter { it !in awayStarters && it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0 }
        
        val isRivalry = (home.rivalTeamId == away.id || away.rivalTeamId == home.id || (home.state == away.state && home.city == away.city))
        
        val matchEvents = GameEngine.simulateMatchDetailed(
            homeTeam = home,
            awayTeam = away,
            homePlayers = homeStarters,
            awayPlayers = awayStarters,
            homeTactics = if (home.isPlayerControlled) playerFormation.value else "4-4-2",
            homeStyle = if (home.isPlayerControlled) playerStyle.value else "Equilibrado",
            awayTactics = if (away.isPlayerControlled) playerFormation.value else "4-4-2",
            awayStyle = if (away.isPlayerControlled) playerStyle.value else "Equilibrado",
            isRivalry = isRivalry,
            randomSeed = Random.nextLong(),
            homeReserves = homeReserves,
            awayReserves = awayReserves
        )
        
        val hGoals = matchEvents.count { it.type == "GOAL" && it.isHomeEvent }
        val aGoals = matchEvents.count { it.type == "GOAL" && !it.isHomeEvent }
        
        val updatedFixture = userFixture.copy(homeScore = hGoals, awayScore = aGoals, isPlayed = true)
        repo.withTransaction {
            val persistedFixture = repo.getFixture(updatedFixture.id)
            if (persistedFixture?.isPlayed != true) {
                repo.updateFixture(updatedFixture)
                processMatchEventsAndStats(updatedFixture, matchEvents)
            }
        }
        return updatedFixture
    }

    internal suspend fun cleanupDuplicateUnplayedFixtures(season: Int) {
        try {
            val allFixtures = repo.getFixturesForSeason(season).filter { !it.isPlayed }
            val idsToDelete = mutableListOf<Long>()
            
            // 1. Exact duplicates (same week, homeTeamId, awayTeamId, competitionType)
            val seenKeys = mutableSetOf<String>()
            for (f in allFixtures) {
                val key = "${f.week}_${f.homeTeamId}_${f.awayTeamId}_${f.competitionType}"
                if (!seenKeys.add(key)) {
                    idsToDelete.add(f.id)
                }
            }
            
            // 2. Duplicate group stage entries: if a team has >1 group stage match in the same week for continental comps
            val teamWeekCompCount = mutableMapOf<String, MutableList<Fixture>>()
            for (f in allFixtures) {
                if (f.id in idsToDelete) continue
                if (f.competitionType.startsWith("CONTINENTAL_")) {
                    val cat = f.competitionType.substringBefore("_GP_")
                    val homeKey = "${f.week}_${f.homeTeamId}_$cat"
                    val awayKey = "${f.week}_${f.awayTeamId}_$cat"
                    
                    teamWeekCompCount.getOrPut(homeKey) { mutableListOf() }.add(f)
                    teamWeekCompCount.getOrPut(awayKey) { mutableListOf() }.add(f)
                }
            }
            
            for ((_, fixtures) in teamWeekCompCount) {
                if (fixtures.size > 1) {
                    for (i in 1 until fixtures.size) {
                        idsToDelete.add(fixtures[i].id)
                    }
                }
            }
            
            if (idsToDelete.isNotEmpty()) {
                repo.deleteFixturesByIds(idsToDelete.distinct())
            }
        } catch (e: Exception) {
            Log.e("GameViewModel", "Erro ao limpar partidas duplicadas", e)
        }
    }

    private suspend fun performSimulateOneWeekInternal() {
        val save = repo.getGameSave() ?: return
        cleanupDuplicateUnplayedFixtures(save.currentSeason)
        var weekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek)
        var userFixture = weekFixtures.find { !it.isPlayed && (it.homeTeamId == save.playerTeamId || it.awayTeamId == save.playerTeamId) }
        
        while (userFixture != null) {
            simulateSingleUserFixture(userFixture, save)
            weekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek)
            userFixture = weekFixtures.find { !it.isPlayed && (it.homeTeamId == save.playerTeamId || it.awayTeamId == save.playerTeamId) }
        }
        
        simulateCpuMatchesForCurrentWeek()
        processWeekEndEconomicAndEvolution()
    }

    suspend fun simulateOneWeek() = simulationMutex.withLock {
        performSimulateOneWeekInternal()
    }

    fun startSeasonSimulation() {
        if (_isSimulatingSeason.value) return
        _isSimulatingSeason.value = true
        _simulationLogs.value = emptyList()
        _simulationCompetitionName.value = "Iniciando simulação..."
        _simulationMatchInfo.value = "Processando rodada..."
        gameSave.value?.let { _simulationCurrentWeek.value = it.currentWeek }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                simulationMutex.withLock {
                    try {
                        val initialSave = repo.getGameSave()
                        if (initialSave == null) {
                            _isSimulatingSeason.value = false
                            return@withLock
                        }
                        val targetSeason = initialSave.currentSeason
                        cleanupDuplicateUnplayedFixtures(targetSeason)

                        while (_isSimulatingSeason.value) {
                            val save = repo.getGameSave()
                            if (save == null) {
                                _isSimulatingSeason.value = false
                                break
                            }
                            if (shouldStopSeasonSimulation(targetSeason, save.currentSeason)) {
                                break
                            }
                            
                            // Auto-generate season fixtures if missing
                            val seasonFixtures = repo.getFixturesForSeason(save.currentSeason)
                            if (seasonFixtures.isEmpty()) {
                                val allTeams = repo.getAllTeams()
                                if (allTeams.isNotEmpty()) {
                                    val newFixtures = generateFixturesForSeason(save.currentSeason, allTeams, save.playerTeamId)
                                    repo.saveFixtures(newFixtures)
                                }
                            }
                            
                            val currentWeekNum = save.currentWeek
                            _simulationCurrentWeek.value = currentWeekNum

                            // Auto-simulation must never make a contract-renewal decision for the
                            // human manager. Stop before playing/closing the week so no fixture,
                            // finance or contract mutation for this week has been committed yet.
                            val expiringControlledContracts =
                                repo.getControlledRosterExpiringContractCount(save.playerTeamId)
                            if (shouldPauseSeasonSimulationForExpiringContracts(expiringControlledContracts)) {
                                val contractLabel = if (expiringControlledContracts == 1) "contrato vence" else "contratos vencem"
                                val pauseMessage =
                                    "$expiringControlledContracts $contractLabel ao fim da semana. Renove os contratos ou avance a semana manualmente."
                                _simulationCompetitionName.value = "Simulação pausada"
                                _simulationMatchInfo.value = pauseMessage
                                _simulationLogs.value = (
                                    listOf("Temp. ${save.currentSeason} | Sem. $currentWeekNum | Simulação pausada: $pauseMessage") +
                                        _simulationLogs.value
                                    ).take(25)
                                break
                            }
                            
                            val weekFixtures = repo.getFixturesForWeek(save.currentSeason, currentWeekNum)
                            val userUnplayedFixtures = weekFixtures.filter { !it.isPlayed && (it.homeTeamId == save.playerTeamId || it.awayTeamId == save.playerTeamId) }
                            
                            if (userUnplayedFixtures.isNotEmpty()) {
                                for (uf in userUnplayedFixtures) {
                                    if (!_isSimulatingSeason.value) break
                                    
                                    val compName = DefaultData.getCompetitionName(uf.competitionType, _selectedCountry.value)
                                    _simulationCompetitionName.value = compName
                                    
                                    val updatedFixture = simulateSingleUserFixture(uf, save)
                                    
                                    val homeTeam = repo.getTeam(updatedFixture.homeTeamId) ?: GlobalFootballSystem.getVirtualTeam(updatedFixture.homeTeamId)
                                    val awayTeam = repo.getTeam(updatedFixture.awayTeamId) ?: GlobalFootballSystem.getVirtualTeam(updatedFixture.awayTeamId)
                                    val resText = "${homeTeam.name} ${updatedFixture.homeScore} - ${updatedFixture.awayScore} ${awayTeam.name}"
                                    
                                    _simulationMatchInfo.value = resText
                                    val logItem = "Temp. ${save.currentSeason} | Sem. $currentWeekNum | $compName: $resText"
                                    _simulationLogs.value = (listOf(logItem) + _simulationLogs.value).take(25)
                                    
                                    delay(1200)
                                }
                            } else {
                                _simulationCompetitionName.value = "Sem Jogo / Descanso"
                                _simulationMatchInfo.value = "Seu clube esteve de folga."
                                val logItem = "Temp. ${save.currentSeason} | Sem. $currentWeekNum | Descanso"
                                _simulationLogs.value = (listOf(logItem) + _simulationLogs.value).take(25)
                                delay(600)
                            }
                            
                            if (!_isSimulatingSeason.value) break
                            
                            simulateCpuMatchesForCurrentWeek()
                            processWeekEndEconomicAndEvolution()
                            
                            val updatedSave = repo.getGameSave() ?: break
                            if (shouldStopSeasonSimulation(targetSeason, updatedSave.currentSeason)) {
                                val nextLog = "🏆 Temporada $targetSeason finalizada com sucesso! Temporada ${updatedSave.currentSeason} preparada."
                                _simulationLogs.value = (listOf(nextLog) + _simulationLogs.value).take(25)
                                _simulationCompetitionName.value = "Temporada $targetSeason concluída"
                                _simulationMatchInfo.value = "Temporada ${updatedSave.currentSeason} preparada."
                                delay(600)
                                break
                            }
                        }
                    } catch (e: Exception) {
                        if (com.example.BuildConfig.DEBUG) {
                            android.util.Log.e("GameViewModel", "Erro durante a simulação de temporada", e)
                        }
                        _simulationLogs.value = listOf("Erro na simulação: ${e.localizedMessage ?: "Erro desconhecido"}") + _simulationLogs.value
                    } finally {
                        _isSimulatingSeason.value = false
                        if (_autoSaveEnabled.value) {
                            performSaveGameInternal(manual = false)
                        }
                    }
                }
            } finally {
                _isSimulatingSeason.value = false
            }
        }
    }

    fun stopSeasonSimulation() {
        _isSimulatingSeason.value = false
    }

    fun triggerAutoSave(eventType: String) {
        if (_isSimulatingSeason.value) return
        if (eventType != "fim_temporada") return
        if (_autoSaveEnabled.value) {
            saveGame(manual = false)
        }
    }

    fun backupCurrentDatabase() {
        val session = _activeSaveSession.value ?: return
        backupDatabaseForSession(session)
    }

    private fun backupDatabaseForSession(session: SaveSession) {
        val saveId = session.slotId
        val context = getApplication<Application>().applicationContext
        val dbName = saveRepository.databaseNameForSlot(saveId)
        val dbFile = saveRepository.databaseFileForSlot(saveId)
        if (!dbFile.exists()) return

        val backupFile = context.getDatabasePath("${dbName}_backup")
        try {
            // Flush committed WAL pages before copying the main database file.
            saveRepository.checkpointSlot(saveId)
            dbFile.inputStream().use { input ->
                backupFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e("GameViewModel", "Erro ao fazer backup local do banco do slot $saveId", e)
        }
    }

    private suspend fun performSaveGameInternal(manual: Boolean, onComplete: (() -> Unit)? = null) {
        val session = _activeSaveSession.value ?: return
        val targetRepo = session.repository
        val saveId = session.slotId

        try {
            backupDatabaseForSession(session)
            val save = targetRepo.getGameSave()
            if (save != null) {
                val team = targetRepo.getTeam(save.playerTeamId)
                preferencesRepo.updateSlotMetadata(
                    saveId = saveId,
                    coachName = save.coachName,
                    teamName = team?.name ?: "Sem Clube",
                    season = save.currentSeason,
                    week = save.currentWeek,
                    balance = save.bankBalance
                )
                loadSaveSlots()
                withContext(Dispatchers.Main) {
                    _saveStatus.value = if (manual) "Jogo salvo manualmente com sucesso!" else "Jogo salvo automaticamente..."
                }
                delay(3000)
                withContext(Dispatchers.Main) {
                    if (_saveStatus.value == (if (manual) "Jogo salvo manualmente com sucesso!" else "Jogo salvo automaticamente...")) {
                        _saveStatus.value = null
                    }
                }
            }
            withContext(Dispatchers.Main) {
                onComplete?.invoke()
            }
        } catch (e: Exception) {
            Log.e("GameViewModel", "Erro ao salvar o jogo", e)
            withContext(Dispatchers.Main) {
                _saveStatus.value = "Erro ao salvar o jogo."
            }
        }
    }

    fun saveGame(manual: Boolean, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            simulationMutex.withLock {
                performSaveGameInternal(manual, onComplete)
            }
        }
    }

    fun validateDatabase(silently: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val integrityUseCase = DatabaseIntegrityUseCase(repo)
                val report = integrityUseCase.repairDatabase()
                val issuesCount = report.issuesFound.size

                val result = DatabaseValidationResult(
                    teamsWithoutCompleteRoster = emptyList(),
                    playersWithoutForce = emptyList(),
                    playersWithoutTeam = emptyList(),
                    teamsWithoutStadium = emptyList(),
                    teamsWithoutCityOrCountry = emptyList(),
                    teamsWithInvalidShields = emptyList(),
                    checkedAt = System.currentTimeMillis()
                )

                withContext(Dispatchers.Main) {
                    _validationResult.value = result
                    if (!silently) {
                        if (issuesCount == 0) {
                            _saveStatus.value = "Banco de dados íntegro e verificado!"
                        } else {
                            _saveStatus.value = "Corretor Dinâmico: $issuesCount problema(s) corrigido(s)!"
                        }
                        viewModelScope.launch(Dispatchers.Main) {
                            delay(3000)
                            if (_saveStatus.value != null && (_saveStatus.value!!.startsWith("Banco de dados") || _saveStatus.value!!.startsWith("Corretor Dinâmico"))) {
                                _saveStatus.value = null
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GameViewModel", "Erro ao validar banco de dados", e)
            }
        }
    }

    init {
        loadWatchlist()
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepo.autoSaveEnabled.collect { _autoSaveEnabled.value = it }
        }
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepo.infiniteStaminaEnabled.collect { _infiniteStaminaEnabled.value = it }
        }
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepo.autoLineupEnabled.collect { _autoLineupEnabled.value = it }
        }

        // 1. Automatically set selected team and country from save once loaded
        viewModelScope.launch {
            gameSave.collect { save ->
                if (save != null) {
                    if (_selectedTeamId.value == null) {
                        _selectedTeamId.value = save.playerTeamId
                    }
                    // Load team and set selected country to match the loaded save's team
                    withContext(Dispatchers.IO) {
                        try {
                            val currentRepository = getActiveRepository() ?: return@withContext
                            val team = currentRepository.getTeam(save.playerTeamId)
                            if (team != null) {
                                val resolvedCountry = DefaultData.getCountryForTeam(team.name)
                                withContext(Dispatchers.Main) {
                                    if (_selectedCountry.value != resolvedCountry) {
                                        _selectedCountry.value = resolvedCountry
                                    }
                                }
                            }
                            
                            // Integridade do Banco: delega verificação e reparo de elencos ao UseCase
                            if (!hasSelfHealedThisSave) {
                                hasSelfHealedThisSave = true
                                val integrityUseCase = DatabaseIntegrityUseCase(currentRepository)
                                integrityUseCase.repairDatabase()
                            }
                        } catch (e: Exception) {
                            Log.e("GameViewModel", "Erro ao verificar ou reparar integridade no carregamento", e)
                        }
                    }
                } else {
                    hasSelfHealedThisSave = false
                }
            }
        }

        // 2. Collect gameSave to update SharedPreferences metadata automatically
        viewModelScope.launch {
            gameSave.collect { save ->
                val saveId = _currentSaveId.value
                if (saveId != null && save != null) {
                    val currentRepository = getActiveRepository()
                    if (currentRepository != null) {
                        try {
                            val team = currentRepository.getTeam(save.playerTeamId)
                            updateSlotMetadata(
                                saveId = saveId,
                                coachName = save.coachName,
                                teamName = team?.name ?: "Sem Clube",
                                season = save.currentSeason,
                                week = save.currentWeek,
                                balance = save.bankBalance
                            )
                        } catch (e: Exception) {
                            Log.e("GameViewModel", "Erro ao atualizar metadados do slot", e)
                        }
                    }
                }
            }
        }

        // 4. Migrate database from old version to Slot 1 if it exists
        val context = application.applicationContext
        val legacyPrefs = application.getSharedPreferences("brasfut_retro_saves", android.content.Context.MODE_PRIVATE)
        val slot1Exists = legacyPrefs.getBoolean("slot_1_exists", false)
        val dbFile = context.getDatabasePath(com.example.data.local.SlotDatabaseFactory.LEGACY_SLOT_1_DATABASE_NAME)
        if (!slot1Exists && dbFile.exists()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val dbTemp = AppDatabase.getDatabaseWithName(context, com.example.data.local.SlotDatabaseFactory.LEGACY_SLOT_1_DATABASE_NAME)
                    val tempRepo = GameRepository(dbTemp)
                    val save = tempRepo.getGameSave()
                    if (save != null) {
                        val team = tempRepo.getTeam(save.playerTeamId)
                        withContext(Dispatchers.Main) {
                            updateSlotMetadata(
                                saveId = "1",
                                coachName = save.coachName,
                                teamName = team?.name ?: "Sem Clube",
                                season = save.currentSeason,
                                week = save.currentWeek,
                                balance = save.bankBalance
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GameViewModel", "Erro ao migrar banco legado para o slot 1", e)
                }
            }
        }

        loadSaveSlots()
    }

    fun loadSaveSlots() {
        viewModelScope.launch(Dispatchers.IO) {
            saveSlots.value = preferencesRepo.loadSaveSlots()
        }
    }

    fun updateSlotMetadata(
        saveId: String,
        coachName: String,
        teamName: String,
        season: Int,
        week: Int,
        balance: Long
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepo.updateSlotMetadata(saveId, coachName, teamName, season, week, balance)
            loadSaveSlots()
        }
    }

    fun removeSlotMetadata(saveId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepo.removeSlotMetadata(saveId)
            loadSaveSlots()
        }
    }

    internal suspend fun seedAllDefaultTeams(targetRepo: GameRepository = repo, activeCountry: String = _selectedCountry.value) {
        // Um slot recém-criado por createFromAsset já contém o universo global completo.
        // O marker íntegro permite evitar 2.524 resoluções de template/ID/logo e consultas de elenco.
        if (targetRepo.pristineCareerSeedTemplateOrNull() != null) return

        val existingTeams = targetRepo.getAllTeams()
        val existingGlobalIds = existingTeams.map { it.id }.toSet()
        val newTeamsToSeed = mutableListOf<Team>()
        val newPlayersToSeed = mutableListOf<Player>()

        for (countryKey in GlobalFootballSystem.keys) {
            val templates = DefaultData.getTeamsForCountry(countryKey)
            for (t in templates) {
                val globalId = GlobalFootballSystem.getGlobalId(countryKey, t.name)
                if (!existingGlobalIds.contains(globalId)) {
                    val team = Team(
                        id = globalId,
                        name = t.name,
                        city = t.city,
                        state = t.state,
                        country = countryKey,
                        division = t.division,
                        rating = t.rating,
                        stadiumName = t.stadium,
                        logoUrl = DefaultData.getLogoForTeam(t.name, countryKey),
                        isPlayerControlled = false
                    )
                    newTeamsToSeed.add(team)
                }
            }
        }
        if (newTeamsToSeed.isNotEmpty()) {
            targetRepo.saveTeams(newTeamsToSeed)
        }

        // Ensure rosters exist for teams in active country without materializing the global
        // ~60k Player table. This path runs while choosing a slot/country, before a career exists.
        val activeTeams = targetRepo.getAllTeams().filter { it.country == activeCountry }
        for (team in activeTeams) {
            val hasPlayers = targetRepo.getPlayerCountByTeam(team.id) > 0
            if (!hasPlayers) {
                val roster = DefaultData.generateRosterForTeam(team.id, team.rating, team.name, activeCountry)
                newPlayersToSeed.addAll(roster)
            }
        }
        if (newPlayersToSeed.isNotEmpty()) {
            targetRepo.savePlayers(newPlayersToSeed)
        }
    }

    fun selectSaveSlot(saveId: String) {
        val gen = sessionGeneration.incrementAndGet()
        val repository = saveRepository.getRepositoryForSlot(saveId)
        val session = SaveSession(saveId, repository, gen)
        _activeSaveSession.value = session
        _currentSaveId.value = saveId
        _selectedTeamId.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (session.generation != sessionGeneration.get()) return@launch
                val targetRepo = session.repository

                seedAllDefaultTeams(targetRepo, _selectedCountry.value)
                if (session.generation != sessionGeneration.get()) return@launch

                val teams = targetRepo.getAllTeams()
                val save = targetRepo.getGameSave()
                if (save != null) {
                    val targetTeam = targetRepo.getTeam(save.playerTeamId)
                    if (targetTeam != null) {
                        val resolvedCountry = DefaultData.getCountryForTeam(targetTeam.name)
                        withContext(Dispatchers.Main) {
                            _selectedCountry.value = resolvedCountry
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _selectedCountry.value = "Brasil"
                    }
                }

                repairRostersIfNecessarySync(session)

                if (session.generation != sessionGeneration.get()) return@launch

                if (save != null) {
                    val seasonFixtures = targetRepo.getFixturesForSeason(save.currentSeason)
                    if (seasonFixtures.isEmpty() && teams.isNotEmpty()) {
                        val newFixtures = generateFixturesForSeason(save.currentSeason, teams, save.playerTeamId)
                        targetRepo.saveFixtures(newFixtures)
                    }
                }

                if (session.generation != sessionGeneration.get()) return@launch
                recoverInterruptedWeeklyCloseIfNeeded(session)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("GameViewModel", "Falha ao abrir carreira do slot $saveId; preservando para recuperação", e)
                if (session.generation != sessionGeneration.get()) return@launch

                sessionGeneration.incrementAndGet()
                _activeSaveSession.value = null
                _currentSaveId.value = null
                _selectedTeamId.value = null
                _matchState.value = MatchState.IDLE
                saveRepository.closeAndRemoveSlot(saveId)

                try {
                    saveSlots.value = preferencesRepo.loadSaveSlots()
                } catch (reconcileError: kotlinx.coroutines.CancellationException) {
                    throw reconcileError
                } catch (reconcileError: Exception) {
                    Log.e("GameViewModel", "Falha ao reconciliar slot $saveId após erro de abertura", reconcileError)
                }
                _toastMessage.emit("Não foi possível abrir a carreira. O slot foi preservado para recuperação.")
            }
        }
    }

    fun repairRostersIfNecessary() {
        val session = _activeSaveSession.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repairRostersIfNecessarySync(session)
        }
    }

    suspend fun repairRostersIfNecessarySync(session: SaveSession) {
        if (session.hasSelfHealed) return
        session.hasSelfHealed = true
        try {
            val targetRepo = session.repository
            val integrityUseCase = DatabaseIntegrityUseCase(targetRepo)
            integrityUseCase.repairDatabase()

            val save = targetRepo.getGameSave()
            if (save != null) {
                val controlledTeam = targetRepo.getTeam(save.playerTeamId)
                if (controlledTeam != null) {
                    val currentRoster = targetRepo.getPlayersByTeam(controlledTeam.id)
                    val repairedPlayers = PlayerEvolutionSystem.repairHistoricalControlledTeam99Roster(
                        team = controlledTeam,
                        roster = currentRoster
                    )
                    if (repairedPlayers.isNotEmpty()) {
                        targetRepo.updatePlayers(repairedPlayers)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GameViewModel", "Erro no autorreparo de elencos", e)
        }
    }

    fun getAnnualSponsorForTeam(name: String, rating: Int, division: Int): Long {
        return when (division) {
            1 -> {
                when {
                    rating >= 85 -> 120000000L + (rating - 85) * 20000000L // Flamengo, Palmeiras, São Paulo scale
                    rating >= 78 -> 50000000L + (rating - 78) * 10000000L  // Grêmio, Inter, Bahia scale
                    else -> 15000000L + (rating - 70) * 3000000L         // Rest of Serie A
                }
            }
            2 -> 4000000L + (rating - 60) * 800000L                        // Serie B standard (R$ 4M to R$ 15M)
            3 -> 1200000L + (rating - 50) * 200000L                        // Serie C standard (R$ 1.2M to R$ 4M)
            else -> 400000L + (rating - 30) * 50000L                       // Serie D standard (R$ 400k to R$ 1.5M)
        }
    }

    fun getInitialBalanceForTeam(rating: Int, division: Int): Long {
        return when (division) {
            1 -> 25000000L + (rating - 60).coerceAtLeast(0) * 3500000L
            2 -> 8000000L + (rating - 50).coerceAtLeast(0) * 500000L
            3 -> 2500000L + (rating - 40).coerceAtLeast(0) * 150000L
            else -> 800000L + (rating - 30).coerceAtLeast(0) * 40000L
        }
    }

    fun exitToSavesMenu() {
        _isSimulatingSeason.value = false
        sessionGeneration.incrementAndGet()
        _activeSaveSession.value = null
        _currentSaveId.value = null
        _selectedTeamId.value = null
        _matchState.value = MatchState.IDLE
    }

    fun deleteSaveSlot(saveId: String) {
        _isSimulatingSeason.value = false
        viewModelScope.launch(Dispatchers.IO) {
            simulationMutex.withLock {
                if (_currentSaveId.value == saveId) {
                    sessionGeneration.incrementAndGet()
                    _activeSaveSession.value = null
                    _currentSaveId.value = null
                    _selectedTeamId.value = null
                    _matchState.value = MatchState.IDLE
                }

                preferencesRepo.removeSlotMetadata(saveId)
                saveRepository.deleteSlotDatabase(saveId)
                loadSaveSlots()
            }
        }
    }

    fun restartCurrentSeason() {
        _isSimulatingSeason.value = false
        viewModelScope.launch(Dispatchers.IO) {
            simulationMutex.withLock {
                val save = repo.getGameSave() ?: return@withLock
                val currentTeams = repo.getAllTeams()
                val allGeneratedFixtures = generateFixturesForSeason(
                    save.currentSeason,
                    currentTeams,
                    save.playerTeamId
                )
                val restarted = repo.restartSeasonStateAtomically(
                    expectedSeason = save.currentSeason,
                    expectedPlayerTeamId = save.playerTeamId,
                    replacementFixtures = allGeneratedFixtures
                )
                if (!restarted) {
                    _toastMessage.emit("O estado da carreira mudou durante o reinício. Tente novamente.")
                }
            }
        }
    }

    fun selectCountry(country: String) {
        val session = _activeSaveSession.value ?: return
        val targetRepo = session.repository
        val generation = session.generation
        _selectedCountry.value = country

        viewModelScope.launch(Dispatchers.IO) {
            if (generation != sessionGeneration.get()) return@launch

            targetRepo.withTransaction {
                targetRepo.deleteSave()
                targetRepo.deleteFixtures()
                targetRepo.deleteOffers()
                seedAllDefaultTeams(targetRepo, country)
            }
            _selectedTeamId.value = null
        }
    }

    enum class MatchState {
        IDLE, PLAYING, PAUSED, FINISHED
    }

    fun setTactics(formation: String, style: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.withTransaction {
                val save = repo.getGameSave() ?: return@withTransaction
                repo.saveGameSave(save.copy(playerFormation = formation, playerStyle = style))
            }
        }
    }

    fun setTransferSearch(pos: String, minForce: Int) {
        _transferSearchPos.value = pos
        _transferSearchMinForce.value = minForce
    }

    fun setTransferFilters(pos: String, minForce: Int, maxAge: Int, maxPrice: Long, sortBy: String) {
        _transferSearchPos.value = pos
        _transferSearchMinForce.value = minForce
        _transferSearchMaxAge.value = maxAge
        _transferSearchMaxPrice.value = maxPrice
        _transferSearchSortBy.value = sortBy
    }

    fun selectTeamForNewGame(teamId: Long) {
        _selectedTeamId.value = teamId
    }

    fun generateInitialProspects(country: String): String {
        return youthAcademyUseCase.generateInitialProspects(country)
    }

    private suspend fun performStartNewGameInternal(
        session: SaveSession,
        activeCountry: String,
        coachName: String,
        teamId: Long
    ) {
        val saveId = session.slotId
        val targetRepo = session.repository
        val generation = session.generation
        val season = 2026
        val totalStartedAtNs = System.nanoTime()
        CareerCreationPerformanceMonitor.clear()

        // Um slot novo pode ter sido criado diretamente do baseline Room empacotado.
        val prebuiltSeedMarker = targetRepo.pristineCareerSeedTemplateOrNull()
        val usePrebuiltCareerSeed = prebuiltSeedMarker != null

        // 1. No fast path, reutiliza os 2.524 clubes já validados no template em vez de
        // reconstruir objetos, recalcular IDs e resolver logos novamente.
        val clubSetupStartedAtNs = System.nanoTime()
        val dbTeams: List<Team> = if (usePrebuiltCareerSeed) {
            targetRepo.getAllTeams()
        } else {
            buildList {
                for (countryKey in GlobalFootballSystem.keys) {
                    val templates = DefaultData.getTeamsForCountry(countryKey)
                    for (t in templates) {
                        val globalId = GlobalFootballSystem.getGlobalId(countryKey, t.name)
                        add(Team(id = globalId, name = t.name, city = t.city, state = t.state, country = countryKey, division = t.division, rating = t.rating, stadiumName = t.stadium, logoUrl = DefaultData.getLogoForTeam(t.name, countryKey), isPlayerControlled = (globalId == teamId)))
                    }
                }
            }
        }
        val clubSetupMs = (System.nanoTime() - clubSetupStartedAtNs) / 1_000_000L

        // 2. Cálculo do calendário em memória ANTES da transação do banco.
        // O calendário também registra a intenção do seed factual para este mesmo repositório.
        val competitionCalendarStartedAtNs = System.nanoTime()
        val allGeneratedFixtures = generateCalendarUseCase.generateSeasonFixtures(season, dbTeams, teamId, activeCountry)
        val competitionCalendarMs = (System.nanoTime() - competitionCalendarStartedAtNs) / 1_000_000L

        // 3. Jogadores: banco-base procedural para slot novo; geração determinística como fallback.
        val rosterMaterializationStartedAtNs = System.nanoTime()
        val allPlayersToSave = if (usePrebuiltCareerSeed) {
            CareerCreationPerformanceMonitor.notePersistedPlayerCount(prebuiltSeedMarker!!.playerCount)
            emptyList()
        } else {
            buildList {
                for (t in dbTeams) {
                    addAll(DefaultData.generateRosterForTeam(t.id, t.rating, t.name, t.country))
                }
            }.also { CareerCreationPerformanceMonitor.notePersistedPlayerCount(it.size) }
        }
        val rosterMaterializationMs = (System.nanoTime() - rosterMaterializationStartedAtNs) / 1_000_000L
        Log.i(
            "CareerCreationPerformance",
            "PrebuiltProceduralSeed=$usePrebuiltCareerSeed proceduralPlayers=${allPlayersToSave.size}"
        )

        // 4. Preparação dos metadados do GameSave em memória
        val playerSelectedTeam = dbTeams.find { it.id == teamId }?.let { selected ->
            if (usePrebuiltCareerSeed && !selected.isPlayerControlled) selected.copy(isPlayerControlled = true) else selected
        }
        val initialAcademyProspects = generateInitialProspects(activeCountry)
        val initialSocioTorcedores = (playerSelectedTeam?.rating ?: 50) * 400

        val initBal = getInitialBalanceForTeam(playerSelectedTeam?.rating ?: 50, playerSelectedTeam?.division ?: 1)
        val initSponsWeekly = getAnnualSponsorForTeam(playerSelectedTeam?.name ?: "", playerSelectedTeam?.rating ?: 50, playerSelectedTeam?.division ?: 1) / 10

        val save = GameSave(
            coachName = coachName,
            coachReputation = 30,
            currentWeek = 1,
            currentSeason = season,
            playerTeamId = teamId,
            bankBalance = initBal,
            stadiumCapacity = DefaultData.getStadiumCapacityForTeam(playerSelectedTeam?.name ?: "", playerSelectedTeam?.rating ?: 50),
            ticketPrice = 20.0,
            sponsorWeekly = initSponsWeekly,
            academyProspects = initialAcademyProspects,
            socioTorcedoresCount = initialSocioTorcedores
        )

        // A sessão usada no clique é imutável para esta operação. Nunca redirecione o reset
        // para a propriedade dinâmica `repo`, que pode apontar para outro slot após uma troca.
        if (generation != sessionGeneration.get()) {
            throw kotlinx.coroutines.CancellationException("Sessão de Novo Jogo ficou obsoleta antes do commit.")
        }

        // 5. Transação no banco capturado limpando TODAS as tabelas sem exceção
        var databaseBootstrapMs = 0L
        var persistenceMs = 0L
        targetRepo.withTransaction {
            if (generation != sessionGeneration.get()) {
                throw kotlinx.coroutines.CancellationException("Sessão de Novo Jogo mudou antes da limpeza.")
            }
            val databaseBootstrapStartedAtNs = System.nanoTime()
            targetRepo.deleteSave()
            if (!usePrebuiltCareerSeed) {
                targetRepo.deleteTeams()
                targetRepo.deletePlayers()
            }
            targetRepo.deleteFixtures()
            targetRepo.deleteTransactions()
            targetRepo.deleteOrders()
            targetRepo.deleteLegends()
            targetRepo.deleteRecords()
            targetRepo.deleteOffers()
            targetRepo.deleteAllHistorico()
            targetRepo.deleteInstallments()
            if (!usePrebuiltCareerSeed) {
                targetRepo.deleteLoans()
            }
            targetRepo.deleteGlobalStandings()
            databaseBootstrapMs = (System.nanoTime() - databaseBootstrapStartedAtNs) / 1_000_000L

            val persistenceStartedAtNs = System.nanoTime()
            val teamSeedStartedAtNs = System.nanoTime()
            if (usePrebuiltCareerSeed) {
                // O template nasce com todos os clubes não-controlados; só a escolha do usuário
                // precisa virar delta. Evita regravar 2.524 clubes idênticos.
                playerSelectedTeam?.let { targetRepo.updateTeam(it) }
            } else {
                targetRepo.saveTeams(dbTeams)
            }
            val teamSeedAndPersistenceMs = (System.nanoTime() - teamSeedStartedAtNs) / 1_000_000L

            val playerPersistenceStartedAtNs = System.nanoTime()
            if (!usePrebuiltCareerSeed) {
                targetRepo.savePlayers(allPlayersToSave)
            }
            val playerPersistenceMs = (System.nanoTime() - playerPersistenceStartedAtNs) / 1_000_000L

            val fixturePersistenceStartedAtNs = System.nanoTime()
            targetRepo.saveFixtures(allGeneratedFixtures)
            val fixturePersistenceMs = (System.nanoTime() - fixturePersistenceStartedAtNs) / 1_000_000L

            val saveRowStartedAtNs = System.nanoTime()
            targetRepo.saveGameSave(save)
            if (usePrebuiltCareerSeed) {
                // Consome o marker na MESMA transação do GameSave. Qualquer falha reverte ambos,
                // impedindo que uma carreira parcial seja confundida com baseline reutilizável.
                targetRepo.consumePristineCareerSeedTemplate()
            }
            val saveRowPersistenceMs = (System.nanoTime() - saveRowStartedAtNs) / 1_000_000L
            persistenceMs = (System.nanoTime() - persistenceStartedAtNs) / 1_000_000L
            Log.i(
                "CareerCreationPerformance",
                "PersistenceBreakdown(teamSeedAndPersistenceMs=$teamSeedAndPersistenceMs, " +
                    "playerPersistenceMs=$playerPersistenceMs, fixturePersistenceMs=$fixturePersistenceMs, " +
                    "saveRowPersistenceMs=$saveRowPersistenceMs)"
            )
        }

        if (generation == sessionGeneration.get()) {
            _selectedTeamId.value = teamId
        }

        // A carreira recém-criada deve aparecer imediatamente. A metadata é derivada do
        // mesmo repositório/slot capturado, mesmo se a UI trocar de sessão após o commit.
        preferencesRepo.updateSlotMetadata(
            saveId = saveId,
            coachName = save.coachName,
            teamName = playerSelectedTeam?.name ?: "Sem Clube",
            season = save.currentSeason,
            week = save.currentWeek,
            balance = save.bankBalance
        )
        // updateSlotMetadata acabou de persistir a projeção autoritativa deste slot.
        // Não reconcilie os cinco bancos aqui: loadSaveSlots() inspeciona cada slot múltiplas
        // vezes e transformava uma simples publicação de UI em I/O pesado no caminho crítico.
        // A reconciliação completa continua sendo feita nas entradas normais da tela de saves.
        val createdSlotMetadata = SaveSlotMetadata(
            id = saveId,
            exists = true,
            coachName = save.coachName,
            teamName = playerSelectedTeam?.name ?: "Sem Clube",
            season = save.currentSeason,
            week = save.currentWeek,
            balance = save.bankBalance
        )
        saveSlots.value = saveSlots.value
            .filterNot { it.id == saveId }
            .plus(createdSlotMetadata)
            .sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }

        val totalMs = (System.nanoTime() - totalStartedAtNs) / 1_000_000L
        val performanceSnapshot = CareerCreationPerformanceSnapshot(
            databaseBootstrapMs = databaseBootstrapMs,
            rosterMaterializationMs = rosterMaterializationMs,
            clubSetupMs = clubSetupMs,
            competitionCalendarMs = competitionCalendarMs,
            persistenceMs = persistenceMs,
            totalMs = totalMs,
            teamCount = dbTeams.size,
            playerCount = allPlayersToSave.size,
            fixtureCount = allGeneratedFixtures.size
        )
        CareerCreationPerformanceMonitor.record(performanceSnapshot)
        Log.i("CareerCreationPerformance", performanceSnapshot.toString())
        CareerCreationPerformanceMonitor.latest?.let { diagnostic ->
            _toastMessage.emit(
                "DIAG criação: total=${diagnostic.totalMs}ms | jogadores=${diagnostic.rosterMaterializationMs}ms | " +
                    "banco=${diagnostic.persistenceMs}ms | calendário=${diagnostic.competitionCalendarMs}ms"
            )
        }
    }

    fun startNewGame(selectedTeamId: Long, coachName: String = "Técnico") {
        val session = _activeSaveSession.value ?: return
        val activeCountry = _selectedCountry.value
        viewModelScope.launch(Dispatchers.IO) {
            _isStartingNewGame.value = true
            try {
                simulationMutex.withLock {
                    if (session.generation != sessionGeneration.get()) return@withLock
                    performStartNewGameInternal(
                        session = session,
                        activeCountry = activeCountry,
                        coachName = coachName,
                        teamId = selectedTeamId
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("GameViewModel", "Erro ao iniciar Novo Jogo no slot ${session.slotId}", e)
                _toastMessage.emit("Não foi possível iniciar o Novo Jogo. Nenhum save existente foi sobrescrito.")
            } finally {
                _isStartingNewGame.value = false
            }
        }
    }

    fun startNewGame(coachName: String, teamId: Long) {
        startNewGame(selectedTeamId = teamId, coachName = coachName)
    }
}
