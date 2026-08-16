package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.example.data.repository.GameSaveRepository

enum class MatchState {
    IDLE,
    SIMULATING,
    FINISHED
}

data class LiveMatchEvent(
    val minute: Int,
    val description: String,
    val isGoal: Boolean = false,
    val isCard: Boolean = false,
    val isInjury: Boolean = false,
    val isWoodwork: Boolean = false,
    val isRedCard: Boolean = false,
    val homeScore: Int = 0,
    val awayScore: Int = 0
)

data class MatchStats(
    val homeShots: Int = 0,
    val awayShots: Int = 0,
    val homePossession: Int = 50,
    val awayPossession: Int = 50,
    val homeFouls: Int = 0,
    val awayFouls: Int = 0,
    val homeYellowCards: Int = 0,
    val awayYellowCards: Int = 0,
    val homeRedCards: Int = 0,
    val awayRedCards: Int = 0
)

@HiltViewModel
class MatchViewModel @Inject constructor(
    application: Application,
    private val saveRepository: GameSaveRepository
) : AndroidViewModel(application) {

    private var activeSlotId: String? = null

    fun setSlotId(slotId: String) {
        activeSlotId = slotId
    }

    fun clearSlot() {
        activeSlotId = null
    }

    private val repository: GameRepository
        get() {
            val slotId = activeSlotId
                ?: throw IllegalStateException("Nenhum save ativo configurado no MatchViewModel.")
            return saveRepository.getRepositoryForSlot(slotId)
        }

    private val _matchState = MutableStateFlow(MatchState.IDLE)
    val matchState: StateFlow<MatchState> = _matchState.asStateFlow()

    private val _currentMatchNarration = MutableStateFlow<List<LiveMatchEvent>>(emptyList())
    val currentMatchNarration: StateFlow<List<LiveMatchEvent>> = _currentMatchNarration.asStateFlow()

    private val _matchStats = MutableStateFlow(MatchStats())
    val matchStats: StateFlow<MatchStats> = _matchStats.asStateFlow()

    private val _activeFixture = MutableStateFlow<Fixture?>(null)
    val activeFixture: StateFlow<Fixture?> = _activeFixture.asStateFlow()

    private val _homeTeam = MutableStateFlow<Team?>(null)
    val homeTeam: StateFlow<Team?> = _homeTeam.asStateFlow()

    private val _awayTeam = MutableStateFlow<Team?>(null)
    val awayTeam: StateFlow<Team?> = _awayTeam.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage = _toastMessage.asSharedFlow()

    fun setupLiveMatch(fixture: Fixture, home: Team, away: Team) {
        _activeFixture.value = fixture
        _homeTeam.value = home
        _awayTeam.value = away
        _currentMatchNarration.value = emptyList()
        _matchStats.value = MatchStats()
        _matchState.value = MatchState.IDLE
    }

    fun startLiveMatchSimulation(
        homeStarters: List<Player>,
        awayStarters: List<Player>,
        onFinished: (Int, Int) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val fixture = _activeFixture.value ?: return@launch
            val home = _homeTeam.value ?: return@launch
            val away = _awayTeam.value ?: return@launch

            _matchState.value = MatchState.SIMULATING
            var hScore = 0
            var aScore = 0
            val events = ArrayDeque<LiveMatchEvent>()

            events.add(LiveMatchEvent(0, "Apito inicial! Começa o jogo entre ${home.name} e ${away.name}."))
            _currentMatchNarration.value = events.toList()

            var homeShots = 0
            var awayShots = 0
            var homeFouls = 0
            var awayFouls = 0
            var homeYellows = 0
            var awayYellows = 0

            val homeAttack = homeStarters.filter { it.position == "ATA" || it.position == "MEI" }.map { it.force }.average().takeIf { !it.isNaN() } ?: 70.0
            val awayAttack = awayStarters.filter { it.position == "ATA" || it.position == "MEI" }.map { it.force }.average().takeIf { !it.isNaN() } ?: 70.0
            val homeDef = homeStarters.filter { it.position == "DEF" || it.position == "GOL" }.map { it.force }.average().takeIf { !it.isNaN() } ?: 70.0
            val awayDef = awayStarters.filter { it.position == "DEF" || it.position == "GOL" }.map { it.force }.average().takeIf { !it.isNaN() } ?: 70.0

            var homeReds = 0
            var awayReds = 0

            for (minute in 1..90) {
                delay(100)

                if (Random.nextFloat() < 0.22f) {
                    val isHomeAttack = Random.nextFloat() < (homeAttack / (homeAttack + awayDef))
                    val attackingTeam = if (isHomeAttack) home else away
                    val defendingTeam = if (isHomeAttack) away else home

                    val attackers = if (isHomeAttack) homeStarters else awayStarters
                    val defenders = if (isHomeAttack) awayStarters else homeStarters
                    val goalkeeper = defenders.firstOrNull { it.position == "GOL" }?.name ?: "Goleiro"

                    val roll = Random.nextInt(100)
                    when {
                        // 1. GOL (14%)
                        roll < 14 -> {
                            if (isHomeAttack) hScore++ else aScore++
                            if (isHomeAttack) homeShots++ else awayShots++
                            val scorer = attackers.randomOrNull()?.name ?: "Atacante"
                            val goalTexts = listOf(
                                "GOOOOOOL DO ${attackingTeam.name.uppercase()}! $scorer balança as redes!",
                                "GOOOLÇO DE PLACA! $scorer acerta o ângulo sem chances para o goleiro!",
                                "GOOOOL! $scorer aproveita o rebote dentro da grande área e fuzila!",
                                "GOOOOL DE CABEÇA! $scorer sobe no terceiro andar e estufa a rede!",
                                "É GOL! $scorer solta a bomba no canto direito!"
                            )
                            events.add(
                                LiveMatchEvent(
                                    minute = minute,
                                    description = goalTexts.random(),
                                    isGoal = true,
                                    homeScore = hScore,
                                    awayScore = aScore
                                )
                            )
                        }
                        // 2. BOLA NA TRAVE (7%)
                        roll < 21 -> {
                            if (isHomeAttack) homeShots++ else awayShots++
                            val scorer = attackers.randomOrNull()?.name ?: "Atacante"
                            val woodworkTexts = listOf(
                                "BOLA NA TRAVE! Chute estrondoso de $scorer carimba o travessão!",
                                "NO TRAVESSÃO! Quase o gol do ${attackingTeam.name}!",
                                "NA TRAVE! $scorer se livra da zaga e carimba o poste direito!"
                            )
                            events.add(
                                LiveMatchEvent(
                                    minute = minute,
                                    description = woodworkTexts.random(),
                                    isWoodwork = true,
                                    homeScore = hScore,
                                    awayScore = aScore
                                )
                            )
                        }
                        // 3. DEFESA MILAGROSA (22%)
                        roll < 43 -> {
                            if (isHomeAttack) homeShots++ else awayShots++
                            val shooter = attackers.randomOrNull()?.name ?: "Atacante"
                            val saveTexts = listOf(
                                "DEFESA MILAGROSA! $goalkeeper espalma no ângulo o chute de $shooter!",
                                "PAREDÃO! Defesa espetacular de $goalkeeper à queima-roupa!",
                                "ESPALMA O GOLEIRO! $goalkeeper voa para evitar o gol do ${attackingTeam.name}!"
                            )
                            events.add(
                                LiveMatchEvent(
                                    minute = minute,
                                    description = saveTexts.random(),
                                    homeScore = hScore,
                                    awayScore = aScore
                                )
                            )
                        }
                        // 4. FALTA SIMPLES (17%)
                        roll < 60 -> {
                            if (isHomeAttack) homeFouls++ else awayFouls++
                            val offender = defenders.randomOrNull()?.name ?: "Jogador"
                            events.add(
                                LiveMatchEvent(
                                    minute = minute,
                                    description = "Falta dura cometida por $offender do ${defendingTeam.name}.",
                                    homeScore = hScore,
                                    awayScore = aScore
                                )
                            )
                        }
                        // 5. CARTÃO AMARELO (10%)
                        roll < 70 -> {
                            if (isHomeAttack) { homeYellows++; homeFouls++ } else { awayYellows++; awayFouls++ }
                            val offender = defenders.randomOrNull()?.name ?: "Jogador"
                            events.add(
                                LiveMatchEvent(
                                    minute = minute,
                                    description = "Cartão Amarelo aplicado a $offender (${defendingTeam.name}).",
                                    isCard = true,
                                    homeScore = hScore,
                                    awayScore = aScore
                                )
                            )
                        }
                        // 6. CARTÃO VERMELHO (3%)
                        roll < 73 -> {
                            if (isHomeAttack) { homeReds++; homeFouls++ } else { awayReds++; awayFouls++ }
                            val offender = defenders.randomOrNull()?.name ?: "Jogador"
                            events.add(
                                LiveMatchEvent(
                                    minute = minute,
                                    description = "CARTÃO VERMELHO! Expulsão direta de $offender do ${defendingTeam.name} após entrada violenta!",
                                    isCard = true,
                                    isRedCard = true,
                                    homeScore = hScore,
                                    awayScore = aScore
                                )
                            )
                        }
                        // 7. SENTIU/LESÃO LEVE (4%)
                        roll < 77 -> {
                            val injuredPlayer = (attackers + defenders).randomOrNull()?.name ?: "Atleta"
                            events.add(
                                LiveMatchEvent(
                                    minute = minute,
                                    description = "ATENDIMENTO: $injuredPlayer sente dores após dividida e recebe atendimento no campo.",
                                    isInjury = true,
                                    homeScore = hScore,
                                    awayScore = aScore
                                )
                            )
                        }
                    }

                    if (events.size >= 30) events.removeFirst()
                    _currentMatchNarration.value = events.toList()
                    _matchStats.value = MatchStats(
                        homeShots = homeShots,
                        awayShots = awayShots,
                        homePossession = 50 + ((homeAttack - awayAttack) / 2).toInt().coerceIn(-20, 20),
                        awayPossession = 50 - ((homeAttack - awayAttack) / 2).toInt().coerceIn(-20, 20),
                        homeFouls = homeFouls,
                        awayFouls = awayFouls,
                        homeYellowCards = homeYellows,
                        awayYellowCards = awayYellows,
                        homeRedCards = homeReds,
                        awayRedCards = awayReds
                    )
                }
            }

            if (events.size >= 30) events.removeFirst()
            events.add(LiveMatchEvent(90, "Fim de jogo! Placar final: ${home.name} $hScore x $aScore ${away.name}."))
            _currentMatchNarration.value = events.toList()
            _matchState.value = MatchState.FINISHED

            val updatedFixture = fixture.copy(
                homeScore = hScore,
                awayScore = aScore,
                isPlayed = true
            )
            repository.updateFixture(updatedFixture)

            withContext(Dispatchers.Main) {
                onFinished(hScore, aScore)
            }
        }
    }

    fun resetMatch() {
        _matchState.value = MatchState.IDLE
        _currentMatchNarration.value = emptyList()
        _activeFixture.value = null
        _homeTeam.value = null
        _awayTeam.value = null
    }
}
