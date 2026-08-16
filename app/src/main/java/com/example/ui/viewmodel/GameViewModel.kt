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
        val chosenStarters = available.filter { it.isStarter }
        val startingXI = chosenStarters.take(11).toMutableList()
        if (startingXI.size < 11) {
            val remaining = available.filter { it !in startingXI }.sortedByDescending { it.force }.take(11 - startingXI.size)
            startingXI.addAll(remaining)
        }
        return startingXI
    }

    fun setPlayerStarter(playerId: Long, isStarter: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.withTransaction {
                    val player = repo.getPlayer(playerId) ?: return@withTransaction
                    val roster = repo.getPlayersByTeam(player.teamId)
                    
                    if (isStarter) {
                        val currentStarters = roster.filter { it.isStarter }
                        if (player.position == "GOL") {
                            val currentGK = currentStarters.find { it.position == "GOL" }
                            if (currentGK != null) {
                                repo.updatePlayer(currentGK.copy(isStarter = false))
                                _toastMessage.tryEmit("Goleiro titular alterado!")
                            } else {
                                if (currentStarters.size >= 11) {
                                    val lowestField = currentStarters.filter { it.position != "GOL" }.minByOrNull { it.force }
                                    if (lowestField != null) {
                                        repo.updatePlayer(lowestField.copy(isStarter = false))
                                    }
                                }
                                _toastMessage.tryEmit("${player.name} escalado como goleiro titular!")
                            }
                        } else {
                            val currentFieldStarters = currentStarters.filter { it.position != "GOL" }
                            if (currentFieldStarters.size >= 10) {
                                val lowestField = currentFieldStarters.minByOrNull { it.force }
                                if (lowestField != null) {
                                    repo.updatePlayer(lowestField.copy(isStarter = false))
                                    _toastMessage.tryEmit("${player.name} escalado! ${lowestField.name} foi para o banco.")
                                }
                            } else if (currentStarters.size >= 11) {
                                val lowest = currentStarters.minByOrNull { it.force }
                                if (lowest != null) {
                                    repo.updatePlayer(lowest.copy(isStarter = false))
                                }
                                _toastMessage.tryEmit("${player.name} escalado!")
                            } else {
                                _toastMessage.tryEmit("${player.name} escalado!")
                            }
                        }
                    } else {
                        if (player.position == "GOL") {
                            val startingGKs = roster.count { it.isStarter && it.position == "GOL" }
                            if (startingGKs <= 1) {
                                _toastMessage.tryEmit("O time deve jogar com exatamente 1 goleiro titular!")
                                return@withTransaction
                            }
                        }
                        _toastMessage.tryEmit("${player.name} foi para o banco.")
                    }
                    repo.updatePlayer(player.copy(isStarter = isStarter))
                }
            } catch (e: Exception) {
                Log.e("GameViewModel", "Erro ao alterar titularidade de jogador", e)
            }
        }
    }

    fun swapPlayers(starterId: Long, benchId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val starter = repo.getPlayer(starterId)
            val bench = repo.getPlayer(benchId)
            if (starter != null && bench != null) {
                if (bench.position == "GOL") {
                    val roster = repo.getPlayersByTeam(bench.teamId)
                    val otherGK = roster.find { it.isStarter && it.id != starter.id && it.position == "GOL" }
                    if (otherGK != null) {
                        repo.updatePlayer(otherGK.copy(isStarter = false))
                    }
                    _toastMessage.emit("Goleiro titular alterado!")
                } else if (starter.position == "GOL" && bench.position != "GOL") {
                    val roster = repo.getPlayersByTeam(bench.teamId)
                    val otherGK = roster.find { it.isStarter && it.id != starter.id && it.position == "GOL" }
                    if (otherGK == null) {
                        _toastMessage.emit("Não é permitido jogar sem um goleiro titular!")
                        return@launch
                    }
                } else {
                    _toastMessage.emit("Substituição realizada!")
                }
                
                val updatedStarter = starter.copy(isStarter = false)
                val updatedBench = bench.copy(isStarter = true)
                repo.updatePlayers(listOf(updatedStarter, updatedBench))
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

    val playerTeam: StateFlow<Team?> = combine(gameSave, allTeams) { save, teams ->
        save?.let { s -> teams.find { it.id == s.playerTeamId } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val playerRoster: StateFlow<List<Player>> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) {
            flowOf(emptyList())
        } else {
            playerTeam.flatMapLatest { team ->
                if (team != null) {
                    r.getPlayersForTeamFlow(team.id)
                } else {
                    flowOf(emptyList())
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentWeekFixtures: StateFlow<List<Fixture>> = combine(gameSave, allFixtures) { save, fixtures ->
        save?.let { s -> fixtures.filter { it.season == s.currentSeason && it.week == s.currentWeek } } ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playerNextFixture: StateFlow<Fixture?> = combine(allFixtures, gameSave) { fixtures, save ->
        if (save == null) null
        else {
            fixtures.filter { 
                !it.isPlayed && 
                it.week >= save.currentWeek && 
                (it.homeTeamId == save.playerTeamId || it.awayTeamId == save.playerTeamId) 
            }.minByOrNull { it.week }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Domain UseCases Composition
    val simulateWeekUseCase: SimulateWeekUseCase get() = SimulateWeekUseCase(repo)
    val processTransfersUseCase: ProcessTransfersUseCase get() = ProcessTransfersUseCase(repo)
    val generateCalendarUseCase: GenerateCalendarUseCase get() = GenerateCalendarUseCase(repo)
    val financeUseCase: com.example.usecase.FinanceUseCase get() = com.example.usecase.FinanceUseCase(repo)
    val scoutingUseCase: com.example.usecase.ScoutingUseCase get() = com.example.usecase.ScoutingUseCase(repo)
    val playerEvolutionUseCase: com.example.usecase.PlayerEvolutionUseCase get() = com.example.usecase.PlayerEvolutionUseCase(repo)

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
            val save = repo.getGameSave() ?: return@launch
            val team = repo.getTeam(save.playerTeamId) ?: return@launch
            val cost = 2_000_000L * team.trainingCenterLevel
            if (save.bankBalance >= cost && team.trainingCenterLevel < 5) {
                val updatedTeam = team.copy(trainingCenterLevel = team.trainingCenterLevel + 1)
                val updatedSave = save.copy(bankBalance = save.bankBalance - cost)
                repo.updateTeam(updatedTeam)
                repo.saveGameSave(updatedSave)
                _toastMessage.emit("Centro de Treinamento evoluído para Nível ${updatedTeam.trainingCenterLevel}!")
            } else {
                _toastMessage.emit("Saldo insuficiente ou nível máximo atingido.")
            }
        }
    }

    fun advanceMonthAndRunEvolution() {
        viewModelScope.launch(Dispatchers.IO) {
            val allPlayers = repo.getAllPlayers()
            val allTeams = repo.getAllTeams().associateBy { it.id }
            val save = repo.getGameSave()
            val periodDate = "${save?.currentSeason ?: 2026}-${save?.currentWeek ?: 1}"

            val results = PlayerEvolutionSystem.processMonthlyEvolution(allPlayers, allTeams, periodDate)

            // Atualiza jogadores no banco de dados
            val updatedPlayers = results.map { it.player }
            repo.updatePlayers(updatedPlayers)

            // Salva histórico de evolução
            val allLogs = results.flatMap { it.historyLogs }
            if (allLogs.isNotEmpty()) {
                repo.saveHistoricoEvolucaoList(allLogs)
            }

            // Exibe modal de resumo do time do usuário
            _monthlyEvolutionSummary.value = results.filter { it.player.teamId == save?.playerTeamId }
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
        liveMatchJob?.cancel()
        _matchState.value = MatchState.IDLE
        liveMatchFixture = null
        liveMatchHomeTeam = null
        liveMatchAwayTeam = null
        liveMatchHomePlayers = emptyList()
        liveMatchAwayPlayers = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            val save = repo.getGameSave() ?: return@launch
            val weekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek)
            val userFixture = weekFixtures.find { it.homeTeamId == save.playerTeamId || it.awayTeamId == save.playerTeamId }
            if (userFixture == null || userFixture.isPlayed) {
                simulateCpuMatchesForCurrentWeek()
                processWeekEndEconomicAndEvolution()
            }
        }
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
            val save = repo.getGameSave() ?: return@launch
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
                    val allPls = repo.getAllPlayers()
                    val myPlayers = allPls.filter { it.teamId == save.playerTeamId }
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
            
            val selectedStarters = mutableSetOf<Player>()
            
            // 1. Select Goalkeeper (GOL) - best by force * (energy / 100)
            val bestGk = available.filter { it.position == "GOL" }
                .maxByOrNull { it.force.toDouble() * (it.energy.toDouble() / 100.0) }
            if (bestGk != null) {
                selectedStarters.add(bestGk)
            }
            
            // 2. Select field players according to formation roles
            val roles = getFormationRoles(formation)
            for (role in roles) {
                val candidate = available.filter { it !in selectedStarters && it.position == role }
                    .maxByOrNull { it.force.toDouble() * (it.energy.toDouble() / 100.0) }
                if (candidate != null) {
                    selectedStarters.add(candidate)
                }
            }
            
            // 3. Fallback: if we still don't have 11 players, fill with remaining field players
            if (selectedStarters.size < 11) {
                val remainingField = available.filter { it !in selectedStarters && it.position != "GOL" }
                    .sortedByDescending { it.force.toDouble() * (it.energy.toDouble() / 100.0) }
                for (player in remainingField) {
                    if (selectedStarters.size >= 11) break
                    selectedStarters.add(player)
                }
            }
            
            // 4. Fallback 2: if still < 11, fill with any remaining goalkeeper
            if (selectedStarters.size < 11) {
                val remainingAll = available.filter { it !in selectedStarters }
                    .sortedByDescending { it.force.toDouble() * (it.energy.toDouble() / 100.0) }
                for (player in remainingAll) {
                    if (selectedStarters.size >= 11) break
                    selectedStarters.add(player)
                }
            }
            
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
        repo.updateFixture(updatedFixture)
        processMatchEventsAndStats(updatedFixture, matchEvents)
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
                        if (initialSave != null) {
                            cleanupDuplicateUnplayedFixtures(initialSave.currentSeason)
                        }
                        while (_isSimulatingSeason.value) {
                            val save = repo.getGameSave()
                            if (save == null) {
                                _isSimulatingSeason.value = false
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
                            if (updatedSave.currentWeek == 1 && currentWeekNum >= GameCalendar.WEEKS_PER_SEASON) {
                                val nextLog = "🏆 Temporada ${save.currentSeason} finalizada com sucesso! Iniciando Temporada ${updatedSave.currentSeason}..."
                                _simulationLogs.value = (listOf(nextLog) + _simulationLogs.value).take(25)
                                delay(1500)
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

        // Ensure rosters exist for teams in active country
        val allPlayers = targetRepo.getAllPlayers()
        val activeTeams = targetRepo.getAllTeams().filter { it.country == activeCountry }
        for (team in activeTeams) {
            val hasPlayers = allPlayers.any { it.teamId == team.id }
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

            // Always run robust roster repair synchronously to avoid race condition
            repairRostersIfNecessarySync(session)

            if (session.generation != sessionGeneration.get()) return@launch

            // Auto-heal missing season fixtures
            if (save != null) {
                val seasonFixtures = targetRepo.getFixturesForSeason(save.currentSeason)
                if (seasonFixtures.isEmpty() && teams.isNotEmpty()) {
                    val newFixtures = generateFixturesForSeason(save.currentSeason, teams, save.playerTeamId)
                    targetRepo.saveFixtures(newFixtures)
                }
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
        viewModelScope.launch(Dispatchers.IO) {
            val save = repo.getGameSave() ?: return@launch
            
            // 1. Reset week to 1
            val updatedSave = save.copy(currentWeek = 1, isGameOver = false)
            repo.saveGameSave(updatedSave)
            
            // 2. Clear and regenerate all fixtures for the current season
            repo.deleteFixtures()
            
            val currentTeams = repo.getAllTeams()
            val allGeneratedFixtures = generateFixturesForSeason(save.currentSeason, currentTeams, save.playerTeamId)
            repo.saveFixtures(allGeneratedFixtures)
            
            // 3. Reset player stats
            val allPlayers = repo.allPlayersFlow.first()
            val resetPlayers = allPlayers.map { p ->
                p.copy(
                    energy = 100,
                    moral = 75,
                    injuryWeeksRemaining = 0,
                    suspensionWeeksRemaining = 0,
                    yellowCardsAccumulated = 0,
                    careerGoals = 0
                )
            }
            repo.updatePlayers(resetPlayers)
            
            // 4. Delete coach offers
            repo.deleteOffers()
        }
    }

    fun selectCountry(country: String) {
        val session = _activeSaveSession.value ?: return
        val targetRepo = session.repository
        val generation = session.generation
        _selectedCountry.value = country

        viewModelScope.launch(Dispatchers.IO) {
            if (generation != sessionGeneration.get()) return@launch

            targetRepo.deleteSave()
            targetRepo.deleteFixtures()
            targetRepo.deleteOffers()
            _selectedTeamId.value = null

            seedAllDefaultTeams(targetRepo, country)
        }
    }

    enum class MatchState {
        IDLE, PLAYING, PAUSED, FINISHED
    }

    fun setTactics(formation: String, style: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val save = repo.getGameSave() ?: return@launch
            repo.saveGameSave(save.copy(playerFormation = formation, playerStyle = style))
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

    private suspend fun performStartNewGameInternal(coachName: String, teamId: Long) {
        val activeCountry = _selectedCountry.value ?: "BRASIL"
        val season = 2026

        // 1. Preparação dos dados de times em memória do zero
        val newTeamsToSeed = mutableListOf<Team>()
        for (countryKey in GlobalFootballSystem.keys) {
            val templates = DefaultData.getTeamsForCountry(countryKey)
            for (t in templates) {
                val globalId = GlobalFootballSystem.getGlobalId(countryKey, t.name)
                newTeamsToSeed.add(
                    Team(
                        id = globalId,
                        name = t.name,
                        city = t.city,
                        state = t.state,
                        country = countryKey,
                        division = t.division,
                        rating = t.rating,
                        stadiumName = t.stadium,
                        logoUrl = DefaultData.getLogoForTeam(t.name, countryKey),
                        isPlayerControlled = (globalId == teamId)
                    )
                )
            }
        }
        val dbTeams = newTeamsToSeed

        // 2. Geração limpa de jogadores para todos os times
        val allPlayersToSave = mutableListOf<Player>()
        for (t in dbTeams) {
            val roster = DefaultData.generateRosterForTeam(t.id, t.rating, t.name, t.country)
            allPlayersToSave.addAll(roster)
        }

        // 3. Cálculo do calendário em memória ANTES da transação do banco
        val allGeneratedFixtures = generateCalendarUseCase.generateSeasonFixtures(season, dbTeams, teamId, activeCountry)

        // 4. Preparação dos metadados do GameSave em memória
        val playerSelectedTeam = dbTeams.find { it.id == teamId }
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

        // 5. Transação no banco limpando TODAS as tabelas sem exceção
        repo.withTransaction {
            repo.deleteSave()
            repo.deleteTeams()
            repo.deletePlayers()
            repo.deleteFixtures()
            repo.deleteTransactions()
            repo.deleteOrders()
            repo.deleteLegends()
            repo.deleteRecords()
            repo.deleteOffers()
            repo.deleteAllHistorico()
            repo.deleteInstallments()
            repo.deleteLoans()

            repo.saveTeams(dbTeams)
            repo.savePlayers(allPlayersToSave)
            repo.saveFixtures(allGeneratedFixtures)
            repo.saveGameSave(save)
        }

        _selectedTeamId.value = teamId
    }


    fun startNewGame(selectedTeamId: Long, coachName: String = "Técnico") {
        viewModelScope.launch(Dispatchers.IO) {
            _isStartingNewGame.value = true
            try {
                simulationMutex.withLock {
                    performStartNewGameInternal(coachName, selectedTeamId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isStartingNewGame.value = false // Garante que a tela de carregamento SEMPRE feche
            }
        }
    }

    fun startNewGame(coachName: String, teamId: Long) {
        startNewGame(selectedTeamId = teamId, coachName = coachName)
    }
}
