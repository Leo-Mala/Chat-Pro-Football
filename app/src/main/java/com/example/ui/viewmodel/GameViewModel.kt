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
                    val candidate = reserves
                        .filter { it.position == starter.position && it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0 && it.id !in takenReserves }
                        .maxByOrNull { it.force }
                    if (candidate != null) {
                        updatedPlayers.add(starter.copy(isStarter = false))
                        updatedPlayers.add(candidate.copy(isStarter = true))
                        takenReserves.add(candidate.id)
                    } else {
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

    internal val _selectedTeamId = MutableStateFlow<Long?>(null)
    val selectedTeamId: StateFlow<Long?> = _selectedTeamId.asStateFlow()

    val playerTeam: StateFlow<Team?> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) flowOf(null) else r.gameSaveFlow.flatMapLatest { save ->
            val teamId = save?.playerTeamId ?: 0L
            if (teamId > 0L) r.getTeamFlow(teamId) else flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val playerLeagueTeams: StateFlow<List<Team>> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) flowOf(emptyList()) else r.gameSaveFlow.flatMapLatest { save ->
            val teamId = save?.playerTeamId ?: 0L
            if (teamId <= 0L) flowOf(emptyList()) else r.getTeamFlow(teamId).flatMapLatest { team ->
                if (team == null) flowOf(emptyList()) else r.getTeamsByCountryDivisionFlow(team.country, team.division)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playerLeagueFixtures: StateFlow<List<Fixture>> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) flowOf(emptyList()) else r.gameSaveFlow.flatMapLatest { save ->
            val teamId = save?.playerTeamId ?: 0L
            if (save == null || teamId <= 0L) flowOf(emptyList()) else r.getTeamFlow(teamId).flatMapLatest { team ->
                if (team == null) flowOf(emptyList()) else {
                    val competitionType = when (team.division) { 1 -> "SERIE_A"; 2 -> "SERIE_B"; 3 -> "SERIE_C"; else -> "SERIE_D" }
                    r.getPlayedFixturesForCompetitionFlow(save.currentSeason, competitionType)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardSeasonFixtures: StateFlow<List<Fixture>> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) flowOf(emptyList()) else r.gameSaveFlow.flatMapLatest { save ->
            if (save == null) flowOf(emptyList()) else r.getFixturesForSeasonFlow(save.currentSeason)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardSeasonTeams: StateFlow<List<Team>> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) flowOf(emptyList()) else r.gameSaveFlow.flatMapLatest { save ->
            if (save == null) flowOf(emptyList()) else r.getFixturesForSeasonFlow(save.currentSeason).flatMapLatest { fixtures ->
                val teamIds = fixtures.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.filter { it > 0L }.distinct()
                if (teamIds.isEmpty()) flowOf(emptyList()) else r.getTeamsByIdsFlow(teamIds)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nextOpponentTeam: StateFlow<Team?> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) flowOf(null) else r.gameSaveFlow.flatMapLatest { save ->
            if (save == null || save.playerTeamId <= 0L) flowOf(null) else r.getNextFixtureForTeamFlow(save.currentSeason, save.currentWeek, save.playerTeamId).flatMapLatest { fixture ->
                if (fixture == null) flowOf(null) else {
                    val opponentId = if (fixture.homeTeamId == save.playerTeamId) fixture.awayTeamId else fixture.homeTeamId
                    if (opponentId > 0L) r.getTeamFlow(opponentId) else flowOf(null)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val playerRoster: StateFlow<List<Player>> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) flowOf(emptyList()) else r.gameSaveFlow.flatMapLatest { save ->
            val teamId = save?.playerTeamId ?: 0L
            if (teamId > 0L) r.getPlayersForTeamFlow(teamId) else flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentWeekFixtures: StateFlow<List<Fixture>> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) flowOf(emptyList()) else r.gameSaveFlow.flatMapLatest { save ->
            if (save == null) flowOf(emptyList()) else r.getFixturesForWeekFlow(save.currentSeason, save.currentWeek)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playerNextFixture: StateFlow<Fixture?> = activeRepositoryFlow.flatMapLatest { r ->
        if (r == null) flowOf(null) else r.gameSaveFlow.flatMapLatest { save ->
            if (save == null || save.playerTeamId <= 0L) flowOf(null) else r.getNextFixtureForTeamFlow(save.currentSeason, save.currentWeek, save.playerTeamId)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val simulateWeekUseCase: SimulateWeekUseCase get() = SimulateWeekUseCase(repo)
    val processTransfersUseCase: ProcessTransfersUseCase get() = ProcessTransfersUseCase(repo)
    val generateCalendarUseCase: GenerateCalendarUseCase get() = GenerateCalendarUseCase(repo)
    val financeUseCase: com.example.usecase.FinanceUseCase get() = com.example.usecase.FinanceUseCase(repo)
    val scoutingUseCase: com.example.usecase.ScoutingUseCase get() = com.example.usecase.ScoutingUseCase(repo)
    val playerEvolutionUseCase: com.example.usecase.PlayerEvolutionUseCase get() = com.example.usecase.PlayerEvolutionUseCase(repo)
    val lineupUseCase: com.example.usecase.LineupUseCase get() = com.example.usecase.LineupUseCase(repo)

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

    fun changeLiveHomeFormation(newFormation: String) { liveHomeFormation.value = newFormation; triggerTacticalFeedback("Formação alterada para $newFormation!"); recalculateRemainingEvents() }
    fun changeLiveHomeStyle(newStyle: String) { liveHomeStyle.value = newStyle; triggerTacticalFeedback("Mentalidade alterada para $newStyle!"); recalculateRemainingEvents() }
    fun changeLiveMatchSpeed(newSpeed: String) { liveMatchSpeed.value = newSpeed }

    internal val _monthlyEvolutionSummary = MutableStateFlow<List<PlayerEvolutionResult>?>(null)
    val monthlyEvolutionSummary: StateFlow<List<PlayerEvolutionResult>?> = _monthlyEvolutionSummary.asStateFlow()

    fun updatePlayerTrainingFocus(player: Player, focus: String) {
        viewModelScope.launch(Dispatchers.IO) { repo.updatePlayer(player.copy(focoTreino = focus)); _toastMessage.emit("Foco de treino de ${player.name} alterado para $focus!") }
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
                    repo.updateTeam(updatedTeam); repo.saveGameSave(updatedSave)
                    "Centro de Treinamento evoluído para Nível ${updatedTeam.trainingCenterLevel}!"
                } else "Saldo insuficiente ou nível máximo atingido."
            }
            if (message != null) _toastMessage.emit(message)
        }
    }

    fun advanceMonthAndRunEvolution() {
        viewModelScope.launch(Dispatchers.IO) {
            val save = repo.getGameSave() ?: return@launch
            val periodDate = "${save.currentSeason}-${save.currentWeek}"
            val outcome = playerEvolutionUseCase.executeMonthlyEvolutionDetailed(save, periodDate)
            if (!outcome.committed) {
                _monthlyEvolutionSummary.value = null
                _toastMessage.emit("O estado de treino mudou durante a evolução mensal. Nenhuma alteração foi aplicada; tente novamente.")
                return@launch
            }
            _monthlyEvolutionSummary.value = outcome.results.filter { it.player.teamId == save.playerTeamId }
            _toastMessage.emit("Evolução mensal processada para todo o elenco!")
        }
    }

    fun dismissMonthlyEvolutionSummary() { _monthlyEvolutionSummary.value = null }
    internal val _gameSpeed = mutableStateOf(1)
    val gameSpeed: State<Int> = _gameSpeed
    fun setGameSpeed(speed: Int) { _gameSpeed.value = speed.coerceIn(1, 3) }

    internal fun triggerTacticalFeedback(message: String) {
        liveTacticalFeedback.value = message
        viewModelScope.launch { delay(3000); if (liveTacticalFeedback.value == message) liveTacticalFeedback.value = null }
    }

    fun recalculateRemainingEvents() {
        val fixture = liveMatchFixture ?: return
        val home = liveMatchHomeTeam ?: return
        val away = liveMatchAwayTeam ?: return
        val currentMin = _matchMinute.value
        val playedEvents = currentMatchEvents.filter { it.minute <= currentMin }
        viewModelScope.launch(Dispatchers.Default) {
            val isRivalry = home.rivalTeamId == away.id || away.rivalTeamId == home.id || (home.state == away.state && home.city == away.city)
            val newAllEvents = GameEngine.simulateMatchDetailed(home, away, liveMatchHomePlayers, liveMatchAwayPlayers, liveHomeFormation.value, liveHomeStyle.value, liveAwayFormation.value, liveAwayStyle.value, isRivalry, Random.nextLong())
            currentMatchEvents = playedEvents + newAllEvents.filter { it.minute > currentMin }
        }
    }

    fun getDelayForSpeed(speed: String): Long = when (speed) { "Tempo Real", "1x", "TR" -> 1600L; "1.5x" -> 1100L; "2x" -> 800L; "4x" -> 350L; "8x" -> 150L; "10x", "MAX" -> 50L; else -> 1600L }

    fun exitLiveMatch() {
        liveMatchJob?.cancel(); _matchState.value = MatchState.IDLE
        liveMatchFixture = null; liveMatchHomeTeam = null; liveMatchAwayTeam = null; liveMatchHomePlayers = emptyList(); liveMatchAwayPlayers = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            val save = repo.getGameSave() ?: return@launch
            val weekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek)
            val userFixture = weekFixtures.find { it.homeTeamId == save.playerTeamId || it.awayTeamId == save.playerTeamId }
            if (userFixture == null || userFixture.isPlayed) { simulateCpuMatchesForCurrentWeek(); processWeekEndEconomicAndEvolution() }
        }
    }

    internal val _watchlist = MutableStateFlow<Set<Long>>(emptySet())
    val watchlist: StateFlow<Set<Long>> = _watchlist.asStateFlow()
    internal fun loadWatchlist() { viewModelScope.launch(Dispatchers.IO) { preferencesRepo.watchlistPlayers.collect { _watchlist.value = it } } }
    fun toggleWatchlistPlayer(playerId: Long) { viewModelScope.launch(Dispatchers.IO) { _watchlist.value = preferencesRepo.toggleWatchlistPlayer(playerId) } }

    val playerFormation: StateFlow<String> = gameSave.map { it?.playerFormation ?: "4-4-2" }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "4-4-2")
    val playerStyle: StateFlow<String> = gameSave.map { it?.playerStyle ?: "Equilibrado" }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Equilibrado")

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
    internal val _incomingOffers = MutableStateFlow<List<IncomingOffer>>(emptyList())
    val incomingOffers: StateFlow<List<IncomingOffer>> = _incomingOffers.asStateFlow()
    internal val _selectedCountry = MutableStateFlow("Brasil")
    val selectedCountry: StateFlow<String> = _selectedCountry.asStateFlow()
    internal val _autoSaveEnabled = MutableStateFlow(true)
    val autoSaveEnabled: StateFlow<Boolean> = _autoSaveEnabled.asStateFlow()
    internal val _infiniteStaminaEnabled = MutableStateFlow(false)
    val infiniteStaminaEnabled: StateFlow<Boolean> = _infiniteStaminaEnabled.asStateFlow()
    internal val _autoLineupEnabled = MutableStateFlow(false)
    val autoLineupEnabled: StateFlow<Boolean> = _autoLineupEnabled.asStateFlow()
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

    val dashboardUiState: StateFlow<DashboardUiState> = combine(gameSave, playerTeam, playerNextFixture, playerRoster, _isSimulatingSeason, _simulationCurrentWeek, _simulationCompetitionName, _simulationMatchInfo, isStartingNewGame) { args ->
        @Suppress("UNCHECKED_CAST")
        DashboardUiState(
            save = args[0] as? GameSave,
            playerTeam = args[1] as? Team,
            nextFixture = args[2] as? Fixture,
            squadPlayers = (args[3] as? List<Player>) ?: emptyList(),
            isSimulating = (args[4] as? Boolean) ?: false,
            isLoading = ((args[4] as? Boolean) ?: false) || ((args[8] as? Boolean) ?: false),
            simulationWeek = (args[5] as? Int) ?: 1,
            simulationCompName = (args[6] as? String) ?: "",
            simulationMatchInfo = (args[7] as? String) ?: ""
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    internal val _saveStatus = MutableStateFlow<String?>(null)
    val saveStatus: StateFlow<String?> = _saveStatus.asStateFlow()
    internal val _validationResult = MutableStateFlow<DatabaseValidationResult?>(null)
    val validationResult: StateFlow<DatabaseValidationResult?> = _validationResult.asStateFlow()

    val captainPlayerId: StateFlow<Long?> = gameSave.map { it?.captainPlayerId }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val penaltyPlayerId: StateFlow<Long?> = gameSave.map { it?.penaltyPlayerId }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val freekickPlayerId: StateFlow<Long?> = gameSave.map { it?.freekickPlayerId }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val cornerPlayerId: StateFlow<Long?> = gameSave.map { it?.cornerPlayerId }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setTacticalRole(role: String, playerId: Long?) { viewModelScope.launch(Dispatchers.IO) { repo.withTransaction { val save = repo.getGameSave() ?: return@withTransaction; repo.saveGameSave(when (role) { "CAPTAIN" -> save.copy(captainPlayerId = playerId); "PENALTY" -> save.copy(penaltyPlayerId = playerId); "FREEKICK" -> save.copy(freekickPlayerId = playerId); "CORNER" -> save.copy(cornerPlayerId = playerId); else -> save }) } } }
    fun setAutoSaveEnabled(enabled: Boolean) { _autoSaveEnabled.value = enabled; viewModelScope.launch(Dispatchers.IO) { preferencesRepo.setAutoSaveEnabled(enabled) } }
    fun setInfiniteStaminaEnabled(enabled: Boolean) { _infiniteStaminaEnabled.value = enabled; viewModelScope.launch(Dispatchers.IO) { preferencesRepo.setInfiniteStaminaEnabled(enabled); if (enabled) { val save = repo.getGameSave(); if (save != null) { val p = repo.getPlayersByTeam(save.playerTeamId); if (p.isNotEmpty()) repo.updatePlayers(p.map { it.copy(energy = 100) }) } } } }
    fun setAutoLineupEnabled(enabled: Boolean) { _autoLineupEnabled.value = enabled; viewModelScope.launch(Dispatchers.IO) { preferencesRepo.setAutoLineupEnabled(enabled) } }
    internal fun getFormationRoles(formation: String): List<String> = tacticsUseCase.getFormationRoles(formation)

    fun autoLineup(teamId: Long, showToast: Boolean = false): kotlinx.coroutines.Job = viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val roster = repo.getPlayersByTeam(teamId)
        val available = roster.filter { it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0 }
        if (available.size < 11) { if (showToast) _toastMessage.emit("Aviso: Elenco muito reduzido para fazer a escalação automática!"); return@launch }
        val selected = mutableSetOf<Player>()
        available.filter { it.position == "GOL" }.maxByOrNull { it.force.toDouble() * it.energy / 100.0 }?.let { selected.add(it) }
        for (role in getFormationRoles(save.playerFormation)) available.filter { it !in selected && it.position == role }.maxByOrNull { it.force.toDouble() * it.energy / 100.0 }?.let { selected.add(it) }
        available.filter { it !in selected && it.position != "GOL" }.sortedByDescending { it.force.toDouble() * it.energy / 100.0 }.forEach { if (selected.size < 11) selected.add(it) }
        available.filter { it !in selected }.sortedByDescending { it.force.toDouble() * it.energy / 100.0 }.forEach { if (selected.size < 11) selected.add(it) }
        val updated = roster.mapNotNull { p -> val should = p in selected; if (p.isStarter != should) p.copy(isStarter = should) else null }
        if (updated.isNotEmpty()) repo.updatePlayers(updated)
        if (showToast) _toastMessage.emit("Escalação automática concluída com base na tática ${save.playerFormation}!")
    }

    internal suspend fun simulateSingleUserFixture(userFixture: Fixture, save: GameSave): Fixture {
        val home = repo.getTeam(userFixture.homeTeamId) ?: GlobalFootballSystem.getVirtualTeam(userFixture.homeTeamId)
        val away = repo.getTeam(userFixture.awayTeamId) ?: GlobalFootballSystem.getVirtualTeam(userFixture.awayTeamId)
        if (_autoLineupEnabled.value) autoLineup(save.playerTeamId).join() else autoReplaceSuspendedAndInjuredPlayers(save.playerTeamId).join()
        val homePls = repo.getPlayersByTeam(home.id); val awayPls = repo.getPlayersByTeam(away.id)
        val homeStarters = getStartingXIForTeam(homePls, home.id, home.rating, home.name, home.country); val awayStarters = getStartingXIForTeam(awayPls, away.id, away.rating, away.name, away.country)
        val homeReserves = homePls.filter { it !in homeStarters && it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0 }; val awayReserves = awayPls.filter { it !in awayStarters && it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0 }
        val isRivalry = home.rivalTeamId == away.id || away.rivalTeamId == home.id || (home.state == away.state && home.city == away.city)
        val events = GameEngine.simulateMatchDetailed(home, away, homeStarters, awayStarters, if (home.isPlayerControlled) playerFormation.value else "4-4-2", if (home.isPlayerControlled) playerStyle.value else "Equilibrado", if (away.isPlayerControlled) playerFormation.value else "4-4-2", if (away.isPlayerControlled) playerStyle.value else "Equilibrado", isRivalry, Random.nextLong(), homeReserves, awayReserves)
        val updated = userFixture.copy(homeScore = events.count { it.type == "GOAL" && it.isHomeEvent }, awayScore = events.count { it.type == "GOAL" && !it.isHomeEvent }, isPlayed = true)
        repo.withTransaction { repo.updateFixture(updated); processMatchEventsAndStats(updated, events) }
        return updated
    }

    internal suspend fun cleanupDuplicateUnplayedFixtures(season: Int) {
        try {
            val allFixtures = repo.getFixturesForSeason(season).filter { !it.isPlayed }; val idsToDelete = mutableListOf<Long>(); val seenKeys = mutableSetOf<String>()
            for (f in allFixtures) { val key = "${f.week}_${f.homeTeamId}_${f.awayTeamId}_${f.competitionType}"; if (!seenKeys.add(key)) idsToDelete.add(f.id) }
            val counts = mutableMapOf<String, MutableList<Fixture>>()
            for (f in allFixtures) if (f.id !in idsToDelete && f.competitionType.startsWith("CONTINENTAL_")) { val cat = f.competitionType.substringBefore("_GP_"); counts.getOrPut("${f.week}_${f.homeTeamId}_$cat") { mutableListOf() }.add(f); counts.getOrPut("${f.week}_${f.awayTeamId}_$cat") { mutableListOf() }.add(f) }
            for (fixtures in counts.values) if (fixtures.size > 1) for (i in 1 until fixtures.size) idsToDelete.add(fixtures[i].id)
            if (idsToDelete.isNotEmpty()) repo.deleteFixturesByIds(idsToDelete.distinct())
        } catch (e: Exception) { Log.e("GameViewModel", "Erro ao limpar partidas duplicadas", e) }
    }

    private suspend fun performSimulateOneWeekInternal() { val save = repo.getGameSave() ?: return; cleanupDuplicateUnplayedFixtures(save.currentSeason); var weekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek); var userFixture = weekFixtures.find { !it.isPlayed && (it.homeTeamId == save.playerTeamId || it.awayTeamId == save.playerTeamId) }; while (userFixture != null) { simulateSingleUserFixture(userFixture, save); weekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek); userFixture = weekFixtures.find { !it.isPlayed && (it.homeTeamId == save.playerTeamId || it.awayTeamId == save.playerTeamId) } }; simulateCpuMatchesForCurrentWeek(); processWeekEndEconomicAndEvolution() }
    suspend fun simulateOneWeek() = simulationMutex.withLock { performSimulateOneWeekInternal() }

    fun startSeasonSimulation() {
        if (_isSimulatingSeason.value) return
        _isSimulatingSeason.value = true; _simulationLogs.value = emptyList(); _simulationCompetitionName.value = "Iniciando simulação..."; _simulationMatchInfo.value = "Processando rodada..."; gameSave.value?.let { _simulationCurrentWeek.value = it.currentWeek }
        viewModelScope.launch(Dispatchers.IO) {
            try { simulationMutex.withLock { try { val initialSave = repo.getGameSave(); if (initialSave != null) cleanupDuplicateUnplayedFixtures(initialSave.currentSeason); while (_isSimulatingSeason.value) { val save = repo.getGameSave() ?: run { _isSimulatingSeason.value = false; break }; val seasonFixtures = repo.getFixturesForSeason(save.currentSeason); if (seasonFixtures.isEmpty()) { val teams = repo.getAllTeams(); if (teams.isNotEmpty()) repo.saveFixtures(generateFixturesForSeason(save.currentSeason, teams, save.playerTeamId)) }; val currentWeekNum = save.currentWeek; _simulationCurrentWeek.value = currentWeekNum; val weekFixtures = repo.getFixturesForWeek(save.currentSeason, currentWeekNum); val user = weekFixtures.filter { !it.isPlayed && (it.homeTeamId == save.playerTeamId || it.awayTeamId == save.playerTeamId) }; if (user.isNotEmpty()) { for (uf in user) { if (!_isSimulatingSeason.value) break; val compName = DefaultData.getCompetitionName(uf.competitionType, _selectedCountry.value); _simulationCompetitionName.value = compName; val updated = simulateSingleUserFixture(uf, save); val h = repo.getTeam(updated.homeTeamId) ?: GlobalFootballSystem.getVirtualTeam(updated.homeTeamId); val a = repo.getTeam(updated.awayTeamId) ?: GlobalFootballSystem.getVirtualTeam(updated.awayTeamId); val res = "${h.name} ${updated.homeScore} - ${updated.awayScore} ${a.name}"; _simulationMatchInfo.value = res; _simulationLogs.value = (listOf("Temp. ${save.currentSeason} | Sem. $currentWeekNum | $compName: $res") + _simulationLogs.value).take(25); delay(1200) } } else { _simulationCompetitionName.value = "Sem Jogo / Descanso"; _simulationMatchInfo.value = "Seu clube esteve de folga."; _simulationLogs.value = (listOf("Temp. ${save.currentSeason} | Sem. $currentWeekNum | Descanso") + _simulationLogs.value).take(25); delay(600) }; if (!_isSimulatingSeason.value) break; simulateCpuMatchesForCurrentWeek(); processWeekEndEconomicAndEvolution(); val updatedSave = repo.getGameSave() ?: break; if (updatedSave.currentWeek == 1 && currentWeekNum >= GameCalendar.WEEKS_PER_SEASON) { _simulationLogs.value = (listOf("🏆 Temporada ${save.currentSeason} finalizada com sucesso! Iniciando Temporada ${updatedSave.currentSeason}...") + _simulationLogs.value).take(25); delay(1500) } } } catch (e: Exception) { if (com.example.BuildConfig.DEBUG) Log.e("GameViewModel", "Erro durante a simulação de temporada", e); _simulationLogs.value = listOf("Erro na simulação: ${e.localizedMessage ?: "Erro desconhecido"}") + _simulationLogs.value } finally { _isSimulatingSeason.value = false; if (_autoSaveEnabled.value) performSaveGameInternal(false) } } } finally { _isSimulatingSeason.value = false } }
    }

    fun stopSeasonSimulation() { _isSimulatingSeason.value = false }
    fun triggerAutoSave(eventType: String) { if (!_isSimulatingSeason.value && eventType == "fim_temporada" && _autoSaveEnabled.value) saveGame(false) }
    fun backupCurrentDatabase() { _activeSaveSession.value?.let { backupDatabaseForSession(it) } }

    private fun backupDatabaseForSession(session: SaveSession) {
        val saveId = session.slotId; val context = getApplication<Application>().applicationContext; val dbName = saveRepository.databaseNameForSlot(saveId); val dbFile = saveRepository.databaseFileForSlot(saveId); if (!dbFile.exists()) return; val backupFile = context.getDatabasePath("${dbName}_backup")
        try { saveRepository.checkpointSlot(saveId); dbFile.inputStream().use { input -> backupFile.outputStream().use { output -> input.copyTo(output) } } } catch (e: Exception) { Log.e("GameViewModel", "Erro ao fazer backup local do banco do slot $saveId", e) }
    }

    private suspend fun performSaveGameInternal(manual: Boolean, onComplete: (() -> Unit)? = null) {
        val session = _activeSaveSession.value ?: return; val targetRepo = session.repository; val saveId = session.slotId
        try { backupDatabaseForSession(session); val save = targetRepo.getGameSave(); if (save != null) { val team = targetRepo.getTeam(save.playerTeamId); preferencesRepo.updateSlotMetadata(saveId, save.coachName, team?.name ?: "Sem Clube", save.currentSeason, save.currentWeek, save.bankBalance); loadSaveSlots(); withContext(Dispatchers.Main) { _saveStatus.value = if (manual) "Jogo salvo manualmente com sucesso!" else "Jogo salvo automaticamente..." }; delay(3000); withContext(Dispatchers.Main) { val expected = if (manual) "Jogo salvo manualmente com sucesso!" else "Jogo salvo automaticamente..."; if (_saveStatus.value == expected) _saveStatus.value = null } }; withContext(Dispatchers.Main) { onComplete?.invoke() } } catch (e: Exception) { Log.e("GameViewModel", "Erro ao salvar o jogo", e); withContext(Dispatchers.Main) { _saveStatus.value = "Erro ao salvar o jogo." } }
    }
    fun saveGame(manual: Boolean, onComplete: (() -> Unit)? = null) { viewModelScope.launch(Dispatchers.IO) { simulationMutex.withLock { performSaveGameInternal(manual, onComplete) } } }

    fun validateDatabase(silently: Boolean = false) { viewModelScope.launch(Dispatchers.IO) { try { val report = DatabaseIntegrityUseCase(repo).repairDatabase(); val result = DatabaseValidationResult(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), System.currentTimeMillis()); withContext(Dispatchers.Main) { _validationResult.value = result; if (!silently) { _saveStatus.value = if (report.issuesFound.isEmpty()) "Banco de dados íntegro e verificado!" else "Corretor Dinâmico: ${report.issuesFound.size} problema(s) corrigido(s)!"; viewModelScope.launch { delay(3000); if (_saveStatus.value?.startsWith("Banco de dados") == true || _saveStatus.value?.startsWith("Corretor Dinâmico") == true) _saveStatus.value = null } } } } catch (e: Exception) { Log.e("GameViewModel", "Erro ao validar banco de dados", e) } } }

    init {
        loadWatchlist()
        viewModelScope.launch(Dispatchers.IO) { preferencesRepo.autoSaveEnabled.collect { _autoSaveEnabled.value = it } }
        viewModelScope.launch(Dispatchers.IO) { preferencesRepo.infiniteStaminaEnabled.collect { _infiniteStaminaEnabled.value = it } }
        viewModelScope.launch(Dispatchers.IO) { preferencesRepo.autoLineupEnabled.collect { _autoLineupEnabled.value = it } }
        viewModelScope.launch { gameSave.collect { save -> if (save != null) { if (_selectedTeamId.value == null) _selectedTeamId.value = save.playerTeamId; withContext(Dispatchers.IO) { try { val currentRepository = getActiveRepository() ?: return@withContext; val team = currentRepository.getTeam(save.playerTeamId); if (team != null) { val resolved = DefaultData.getCountryForTeam(team.name); withContext(Dispatchers.Main) { if (_selectedCountry.value != resolved) _selectedCountry.value = resolved } }; if (!hasSelfHealedThisSave) { hasSelfHealedThisSave = true; DatabaseIntegrityUseCase(currentRepository).repairDatabase() } } catch (e: Exception) { Log.e("GameViewModel", "Erro ao verificar ou reparar integridade no carregamento", e) } } } else hasSelfHealedThisSave = false } }
        viewModelScope.launch { gameSave.collect { save -> val saveId = _currentSaveId.value; if (saveId != null && save != null) { val currentRepository = getActiveRepository(); if (currentRepository != null) try { val team = currentRepository.getTeam(save.playerTeamId); updateSlotMetadata(saveId, save.coachName, team?.name ?: "Sem Clube", save.currentSeason, save.currentWeek, save.bankBalance) } catch (e: Exception) { Log.e("GameViewModel", "Erro ao atualizar metadados do slot", e) } } } }
        val context = application.applicationContext; val legacyPrefs = application.getSharedPreferences("brasfut_retro_saves", android.content.Context.MODE_PRIVATE); val slot1Exists = legacyPrefs.getBoolean("slot_1_exists", false); val dbFile = context.getDatabasePath(com.example.data.local.SlotDatabaseFactory.LEGACY_SLOT_1_DATABASE_NAME)
        if (!slot1Exists && dbFile.exists()) viewModelScope.launch(Dispatchers.IO) { try { val dbTemp = AppDatabase.getDatabaseWithName(context, com.example.data.local.SlotDatabaseFactory.LEGACY_SLOT_1_DATABASE_NAME); val tempRepo = GameRepository(dbTemp); val save = tempRepo.getGameSave(); if (save != null) { val team = tempRepo.getTeam(save.playerTeamId); withContext(Dispatchers.Main) { updateSlotMetadata("1", save.coachName, team?.name ?: "Sem Clube", save.currentSeason, save.currentWeek, save.bankBalance) } } } catch (e: Exception) { Log.e("GameViewModel", "Erro ao migrar banco legado para o slot 1", e) } }
        loadSaveSlots()
    }

    fun loadSaveSlots() { viewModelScope.launch(Dispatchers.IO) { saveSlots.value = preferencesRepo.loadSaveSlots() } }
    fun updateSlotMetadata(saveId: String, coachName: String, teamName: String, season: Int, week: Int, balance: Long) { viewModelScope.launch(Dispatchers.IO) { preferencesRepo.updateSlotMetadata(saveId, coachName, teamName, season, week, balance); loadSaveSlots() } }
    fun removeSlotMetadata(saveId: String) { viewModelScope.launch(Dispatchers.IO) { preferencesRepo.removeSlotMetadata(saveId); loadSaveSlots() } }

    internal suspend fun seedAllDefaultTeams(targetRepo: GameRepository = repo, activeCountry: String = _selectedCountry.value) {
        val existingGlobalIds = targetRepo.getAllTeams().map { it.id }.toSet(); val newTeams = mutableListOf<Team>(); val newPlayers = mutableListOf<Player>()
        for (countryKey in GlobalFootballSystem.keys) for (t in DefaultData.getTeamsForCountry(countryKey)) { val globalId = GlobalFootballSystem.getGlobalId(countryKey, t.name); if (globalId !in existingGlobalIds) newTeams.add(Team(globalId, t.name, t.city, t.state, countryKey, t.division, false, t.rating, t.stadium, DefaultData.getLogoForTeam(t.name, countryKey))) }
        if (newTeams.isNotEmpty()) targetRepo.saveTeams(newTeams)
        val allPlayers = targetRepo.getAllPlayers(); val activeTeams = targetRepo.getAllTeams().filter { it.country == activeCountry }; for (team in activeTeams) if (allPlayers.none { it.teamId == team.id }) newPlayers.addAll(DefaultData.generateRosterForTeam(team.id, team.rating, team.name, activeCountry)); if (newPlayers.isNotEmpty()) targetRepo.savePlayers(newPlayers)
    }

    fun selectSaveSlot(saveId: String) {
        val gen = sessionGeneration.incrementAndGet(); val repository = saveRepository.getRepositoryForSlot(saveId); val session = SaveSession(saveId, repository, gen); _activeSaveSession.value = session; _currentSaveId.value = saveId; _selectedTeamId.value = null
        viewModelScope.launch(Dispatchers.IO) { if (session.generation != sessionGeneration.get()) return@launch; val targetRepo = session.repository; seedAllDefaultTeams(targetRepo, _selectedCountry.value); if (session.generation != sessionGeneration.get()) return@launch; val teams = targetRepo.getAllTeams(); val save = targetRepo.getGameSave(); if (save != null) { val targetTeam = targetRepo.getTeam(save.playerTeamId); if (targetTeam != null) withContext(Dispatchers.Main) { _selectedCountry.value = DefaultData.getCountryForTeam(targetTeam.name) } } else withContext(Dispatchers.Main) { _selectedCountry.value = "Brasil" }; repairRostersIfNecessarySync(session); if (session.generation != sessionGeneration.get()) return@launch; if (save != null) { val fixtures = targetRepo.getFixturesForSeason(save.currentSeason); if (fixtures.isEmpty() && teams.isNotEmpty()) targetRepo.saveFixtures(generateFixturesForSeason(save.currentSeason, teams, save.playerTeamId)) } }
    }

    fun repairRostersIfNecessary() { _activeSaveSession.value?.let { session -> viewModelScope.launch(Dispatchers.IO) { repairRostersIfNecessarySync(session) } } }
    suspend fun repairRostersIfNecessarySync(session: SaveSession) { if (session.hasSelfHealed) return; session.hasSelfHealed = true; try { DatabaseIntegrityUseCase(session.repository).repairDatabase() } catch (e: Exception) { Log.e("GameViewModel", "Erro no autorreparo de elencos", e) } }

    fun getAnnualSponsorForTeam(name: String, rating: Int, division: Int): Long = when (division) { 1 -> when { rating >= 85 -> 120000000L + (rating - 85) * 20000000L; rating >= 78 -> 50000000L + (rating - 78) * 10000000L; else -> 15000000L + (rating - 70) * 3000000L }; 2 -> 4000000L + (rating - 60) * 800000L; 3 -> 1200000L + (rating - 50) * 200000L; else -> 400000L + (rating - 30) * 50000L }
    fun getInitialBalanceForTeam(rating: Int, division: Int): Long = when (division) { 1 -> 25000000L + (rating - 60).coerceAtLeast(0) * 3500000L; 2 -> 8000000L + (rating - 50).coerceAtLeast(0) * 500000L; 3 -> 2500000L + (rating - 40).coerceAtLeast(0) * 150000L; else -> 800000L + (rating - 30).coerceAtLeast(0) * 40000L }

    fun exitToSavesMenu() { _isSimulatingSeason.value = false; sessionGeneration.incrementAndGet(); _activeSaveSession.value = null; _currentSaveId.value = null; _selectedTeamId.value = null; _matchState.value = MatchState.IDLE }
    fun deleteSaveSlot(saveId: String) { _isSimulatingSeason.value = false; viewModelScope.launch(Dispatchers.IO) { simulationMutex.withLock { if (_currentSaveId.value == saveId) { sessionGeneration.incrementAndGet(); _activeSaveSession.value = null; _currentSaveId.value = null; _selectedTeamId.value = null; _matchState.value = MatchState.IDLE }; preferencesRepo.removeSlotMetadata(saveId); saveRepository.deleteSlotDatabase(saveId); loadSaveSlots() } } }
    fun restartCurrentSeason() { _isSimulatingSeason.value = false; viewModelScope.launch(Dispatchers.IO) { simulationMutex.withLock { val save = repo.getGameSave() ?: return@withLock; val fixtures = generateFixturesForSeason(save.currentSeason, repo.getAllTeams(), save.playerTeamId); if (!repo.restartSeasonStateAtomically(save.currentSeason, save.playerTeamId, fixtures)) _toastMessage.emit("O estado da carreira mudou durante o reinício. Tente novamente.") } } }

    fun selectCountry(country: String) {
        val session = _activeSaveSession.value ?: return
        val targetRepo = session.repository
        val generation = session.generation
        _selectedCountry.value = country
        viewModelScope.launch(Dispatchers.IO) {
            if (generation != sessionGeneration.get()) return@launch
            val inspection = saveRepository.inspectSlot(session.slotId)
            if (!inspection.newGameAllowed) {
                saveSlots.value = preferencesRepo.loadSaveSlots()
                _toastMessage.emit(if (inspection.save != null) "Este slot contém uma carreira existente. Nenhum dado foi alterado." else "Este slot precisa de recuperação. Nenhum dado foi alterado.")
                Log.w("GameViewModel", "Troca de país bloqueada no slot ${session.slotId}: ${inspection.state}")
                return@launch
            }
            targetRepo.deleteSave(); targetRepo.deleteFixtures(); targetRepo.deleteOffers(); _selectedTeamId.value = null; seedAllDefaultTeams(targetRepo, country)
        }
    }

    enum class MatchState { IDLE, PLAYING, PAUSED, FINISHED }
    fun setTactics(formation: String, style: String) { viewModelScope.launch(Dispatchers.IO) { repo.withTransaction { val save = repo.getGameSave() ?: return@withTransaction; repo.saveGameSave(save.copy(playerFormation = formation, playerStyle = style)) } } }
    fun setTransferSearch(pos: String, minForce: Int) { _transferSearchPos.value = pos; _transferSearchMinForce.value = minForce }
    fun setTransferFilters(pos: String, minForce: Int, maxAge: Int, maxPrice: Long, sortBy: String) { _transferSearchPos.value = pos; _transferSearchMinForce.value = minForce; _transferSearchMaxAge.value = maxAge; _transferSearchMaxPrice.value = maxPrice; _transferSearchSortBy.value = sortBy }
    fun selectTeamForNewGame(teamId: Long) { _selectedTeamId.value = teamId }
    fun generateInitialProspects(country: String): String = youthAcademyUseCase.generateInitialProspects(country)

    private suspend fun performStartNewGameInternal(coachName: String, teamId: Long) {
        val saveId = _activeSaveSession.value?.slotId ?: return
        val inspection = saveRepository.inspectSlot(saveId)
        if (!inspection.newGameAllowed) {
            saveSlots.value = preferencesRepo.loadSaveSlots()
            _toastMessage.emit(if (inspection.save != null) "Este slot contém uma carreira existente. Exclua-a explicitamente antes de criar um novo jogo." else "Este slot precisa de recuperação. Novo jogo bloqueado para preservar os dados.")
            Log.w("GameViewModel", "Novo jogo bloqueado no slot $saveId: ${inspection.state}")
            return
        }
        val activeCountry = _selectedCountry.value ?: "BRASIL"
        val season = 2026
        val newTeamsToSeed = mutableListOf<Team>()
        for (countryKey in GlobalFootballSystem.keys) for (t in DefaultData.getTeamsForCountry(countryKey)) { val globalId = GlobalFootballSystem.getGlobalId(countryKey, t.name); newTeamsToSeed.add(Team(globalId, t.name, t.city, t.state, countryKey, t.division, globalId == teamId, t.rating, t.stadium, DefaultData.getLogoForTeam(t.name, countryKey))) }
        val dbTeams = newTeamsToSeed
        val allPlayersToSave = mutableListOf<Player>(); for (t in dbTeams) allPlayersToSave.addAll(DefaultData.generateRosterForTeam(t.id, t.rating, t.name, t.country))
        val allGeneratedFixtures = generateCalendarUseCase.generateSeasonFixtures(season, dbTeams, teamId, activeCountry)
        val playerSelectedTeam = dbTeams.find { it.id == teamId }; val initialAcademyProspects = generateInitialProspects(activeCountry); val initialSocioTorcedores = (playerSelectedTeam?.rating ?: 50) * 400
        val initBal = getInitialBalanceForTeam(playerSelectedTeam?.rating ?: 50, playerSelectedTeam?.division ?: 1); val initSponsWeekly = getAnnualSponsorForTeam(playerSelectedTeam?.name ?: "", playerSelectedTeam?.rating ?: 50, playerSelectedTeam?.division ?: 1) / 10
        val save = GameSave(coachName = coachName, coachReputation = 30, currentWeek = 1, currentSeason = season, playerTeamId = teamId, bankBalance = initBal, stadiumCapacity = DefaultData.getStadiumCapacityForTeam(playerSelectedTeam?.name ?: "", playerSelectedTeam?.rating ?: 50), ticketPrice = 20.0, sponsorWeekly = initSponsWeekly, academyProspects = initialAcademyProspects, socioTorcedoresCount = initialSocioTorcedores)
        repo.withTransaction { repo.deleteSave(); repo.deleteTeams(); repo.deletePlayers(); repo.deleteFixtures(); repo.deleteTransactions(); repo.deleteOrders(); repo.deleteLegends(); repo.deleteRecords(); repo.deleteOffers(); repo.deleteAllHistorico(); repo.deleteInstallments(); repo.deleteLoans(); repo.deleteGlobalStandings(); repo.saveTeams(dbTeams); repo.savePlayers(allPlayersToSave); repo.saveFixtures(allGeneratedFixtures); repo.saveGameSave(save) }
        _selectedTeamId.value = teamId
        preferencesRepo.updateSlotMetadata(saveId, save.coachName, playerSelectedTeam?.name ?: "Sem Clube", save.currentSeason, save.currentWeek, save.bankBalance)
        saveSlots.value = preferencesRepo.loadSaveSlots()
    }

    fun startNewGame(selectedTeamId: Long, coachName: String = "Técnico") { viewModelScope.launch(Dispatchers.IO) { _isStartingNewGame.value = true; try { simulationMutex.withLock { performStartNewGameInternal(coachName, selectedTeamId) } } catch (e: Exception) { Log.e("GameViewModel", "Erro ao iniciar novo jogo", e) } finally { _isStartingNewGame.value = false } } }
    fun startNewGame(coachName: String, teamId: Long) { startNewGame(selectedTeamId = teamId, coachName = coachName) }
}
