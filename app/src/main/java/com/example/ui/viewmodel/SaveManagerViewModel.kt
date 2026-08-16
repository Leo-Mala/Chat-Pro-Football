package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DefaultData
import com.example.data.Fixture
import com.example.data.GameSave
import com.example.data.HistoricalRecord
import com.example.data.Player
import com.example.data.Team
import com.example.data.TransactionRecord
import com.example.data.ClubLegend
import com.example.data.CoachOffer
import com.example.data.HistoricoEvolucao
import com.example.data.TransferOrder
import com.example.data.TransferInstallment
import com.example.data.PlayerLoan
import com.example.data.repository.GameSaveRepository
import com.example.data.GamePreferencesRepository
import com.example.data.model.SaveSlotMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SaveSlot(
    val id: String,
    val exists: Boolean,
    val coachName: String = "",
    val teamName: String = "",
    val season: Int = 2026,
    val week: Int = 1,
    val balance: Long = 0L
)

@HiltViewModel
class SaveManagerViewModel @Inject constructor(
    application: Application,
    private val gameSaveRepository: GameSaveRepository,
    private val preferencesRepository: GamePreferencesRepository
) : AndroidViewModel(application) {

    companion object {
        const val MAX_SAVE_SLOTS = 5
    }

    private val _currentSaveId = MutableStateFlow<String?>(null)
    val currentSaveId: StateFlow<String?> = _currentSaveId.asStateFlow()

    private val _saveSlots = MutableStateFlow<List<SaveSlot>>(emptyList())
    val saveSlots: StateFlow<List<SaveSlot>> = _saveSlots.asStateFlow()

    private val _gameSave = MutableStateFlow<GameSave?>(null)
    val gameSave: StateFlow<GameSave?> = _gameSave.asStateFlow()

    private val _availableTeams = MutableStateFlow<List<Team>>(emptyList())
    val availableTeams: StateFlow<List<Team>> = _availableTeams.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage = _toastMessage.asSharedFlow()

    init {
        loadSaveSlots()
    }

    fun loadSaveSlots() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val slots = preferencesRepository.loadSaveSlots().map { meta ->
                    SaveSlot(
                        id = meta.id,
                        exists = meta.exists,
                        coachName = meta.coachName,
                        teamName = meta.teamName,
                        season = meta.season,
                        week = meta.week,
                        balance = meta.balance
                    )
                }
                _saveSlots.value = slots
            } catch (e: Exception) {
                Log.e("SaveManagerViewModel", "Erro ao carregar slots de salvamento", e)
            }
        }
    }

    fun selectSaveSlot(slotId: String) {
        _currentSaveId.value = slotId
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repo = gameSaveRepository.getRepositoryForSlot(slotId)
                val save = repo.getGameSave()
                val teams = repo.getAllTeams()

                // Ignore a stale load if the user selected another slot meanwhile.
                if (_currentSaveId.value != slotId) return@launch

                _gameSave.value = save
                _availableTeams.value = teams
            } catch (e: Exception) {
                Log.e("SaveManagerViewModel", "Erro ao selecionar slot de salvamento $slotId", e)
            }
        }
    }

    fun startNewGameInSlot(slotId: String, coachName: String, selectedTeam: Team) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repo = gameSaveRepository.getRepositoryForSlot(slotId)

                val newSave = GameSave(
                    id = 1,
                    coachName = coachName.ifBlank { "Técnico" },
                    playerTeamId = selectedTeam.id,
                    currentSeason = 2026,
                    currentWeek = 1,
                    bankBalance = 5_000_000L
                )

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

                    repo.saveGameSave(newSave)

                    val initialTeams = mutableListOf<Team>()
                    val initialPlayers = mutableListOf<Player>()

                    for (countryKey in com.example.data.GlobalFootballSystem.keys) {
                        val countryTeams = DefaultData.getTeamsForCountry(countryKey)
                        for (t in countryTeams) {
                            val globalId = com.example.data.GlobalFootballSystem.getGlobalId(countryKey, t.name)
                            val isUserTeam = (globalId == selectedTeam.id)
                            val teamObj = Team(
                                id = globalId,
                                name = t.name,
                                city = t.city,
                                state = t.state,
                                country = countryKey,
                                division = t.division,
                                rating = t.rating,
                                stadiumName = t.stadium,
                                logoUrl = DefaultData.getLogoForTeam(t.name, countryKey),
                                isPlayerControlled = isUserTeam
                            )
                            initialTeams.add(teamObj)
                            val roster = DefaultData.generateRosterForTeam(globalId, t.rating, t.name, countryKey)
                            initialPlayers.addAll(roster)
                        }
                    }

                    repo.saveTeams(initialTeams)
                    repo.savePlayers(initialPlayers)
                }

                val teams = repo.getAllTeams()
                if (teams.isNotEmpty()) {
                    val calendarUseCase = com.example.usecase.GenerateCalendarUseCase(repo)
                    val initialFixtures = mutableListOf<Fixture>()
                    val groupedTeams = teams.groupBy { Pair(it.country, it.division) }
                    for ((_, teamGroup) in groupedTeams) {
                        if (teamGroup.size >= 2) {
                            val divCode = when (teamGroup.firstOrNull()?.division) {
                                1 -> "SERIE_A"
                                2 -> "SERIE_B"
                                3 -> "SERIE_C"
                                else -> "SERIE_D"
                            }
                            val groupFixtures = calendarUseCase.generateRoundRobinFixtures(2026, teamGroup, divCode, 1)
                            initialFixtures.addAll(groupFixtures)
                        }
                    }
                    val worldCupFixtures = com.example.data.SuperMundialSystem.generateGroupStageFixtures(2026, teams, selectedTeam.id)
                    initialFixtures.addAll(worldCupFixtures)
                    repo.saveFixtures(initialFixtures)
                }

                _currentSaveId.value = slotId
                _gameSave.value = newSave
                preferencesRepository.updateSlotMetadata(
                    saveId = slotId,
                    coachName = newSave.coachName,
                    teamName = selectedTeam.name,
                    season = newSave.currentSeason,
                    week = newSave.currentWeek,
                    balance = newSave.bankBalance
                )

                _toastMessage.emit("Carreira iniciada com o ${selectedTeam.name}!")
                loadSaveSlots()
            } catch (e: Exception) {
                Log.e("SaveManagerViewModel", "Erro ao iniciar novo jogo no slot $slotId", e)
                _toastMessage.emit("Erro ao iniciar nova carreira.")
            }
        }
    }

    fun deleteSaveSlot(slotId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (_currentSaveId.value == slotId) {
                    _currentSaveId.value = null
                    _gameSave.value = null
                    _availableTeams.value = emptyList()
                }

                // Delete the actual database file using the same naming source used to create it.
                gameSaveRepository.deleteSlotDatabase(slotId)
                preferencesRepository.removeSlotMetadata(slotId)

                _toastMessage.emit("Save deletado com sucesso.")
                loadSaveSlots()
            } catch (e: Exception) {
                Log.e("SaveManagerViewModel", "Erro ao deletar save slot $slotId", e)
                _toastMessage.emit("Erro ao deletar save.")
            }
        }
    }

    fun exitToSavesMenu() {
        _currentSaveId.value = null
        _gameSave.value = null
        loadSaveSlots()
    }

    data class SaveBackupData(
        val backupVersion: Int = 1,
        val databaseVersion: Int = 18,
        val appVersion: String = "1.0",
        val createdAt: Long = System.currentTimeMillis(),
        val save: GameSave,
        val teams: List<Team>,
        val players: List<Player>,
        val fixtures: List<Fixture>,
        val records: List<HistoricalRecord>? = emptyList(),
        val transactions: List<TransactionRecord>? = emptyList(),
        val installments: List<TransferInstallment>? = emptyList(),
        val loans: List<PlayerLoan>? = emptyList(),
        val legends: List<ClubLegend>? = emptyList(),
        val offers: List<CoachOffer>? = emptyList(),
        val orders: List<TransferOrder>? = emptyList(),
        val historicoEvolucao: List<HistoricoEvolucao>? = emptyList()
    )

    suspend fun exportSaveToJson(slotId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = gameSaveRepository.getRepositoryForSlot(slotId)
            val save = repo.getGameSave() ?: throw IllegalStateException("Save não encontrado no slot $slotId")
            val teams = repo.getAllTeams()
            val players = repo.getAllPlayers()
            val fixtures = repo.getAllFixtures()
            val records = repo.getAllHistoricalRecords()
            val transactions = repo.getAllTransactions()
            val installments = repo.getAllInstallments()
            val loans = repo.getAllLoans()
            val legends = repo.getAllLegends()
            val offers = repo.getAllOffers()
            val orders = repo.getOrders()
            val historico = repo.getAllHistorico()

            val backup = SaveBackupData(
                backupVersion = 1,
                databaseVersion = 18,
                appVersion = "1.0",
                createdAt = System.currentTimeMillis(),
                save = save,
                teams = teams,
                players = players,
                fixtures = fixtures,
                records = records,
                transactions = transactions,
                installments = installments,
                loans = loans,
                legends = legends,
                offers = offers,
                orders = orders,
                historicoEvolucao = historico
            )
            Gson().toJson(backup)
        }.onFailure { e ->
            Log.e("SaveManagerViewModel", "Erro ao exportar save do slot $slotId", e)
        }
    }

    fun validateBackupIntegrity(backup: SaveBackupData): Boolean {
        if (backup.backupVersion < 1) return false
        if (backup.databaseVersion <= 0) return false
        if (backup.save == null) return false
        if (backup.teams.isNullOrEmpty()) return false
        if (backup.players.isNullOrEmpty()) return false

        val teamIds = backup.teams.map { it.id }.toSet()
        if (teamIds.size != backup.teams.size) return false
        if (!teamIds.contains(backup.save.playerTeamId)) return false

        val playerIds = mutableSetOf<Long>()
        for (player in backup.players) {
            if (player.id != 0L && !playerIds.add(player.id)) return false
            if (player.teamId != 0L && !teamIds.contains(player.teamId)) return false
            if (player.contractDurationWeeks < 0) return false
            if (player.force !in 1..99) return false
        }

        val fixtureList = backup.fixtures ?: emptyList()
        for (fixture in fixtureList) {
            if (!teamIds.contains(fixture.homeTeamId) || !teamIds.contains(fixture.awayTeamId)) {
                return false
            }
        }

        if (backup.save.currentSeason < 2000 || backup.save.currentWeek < 1) return false

        return true
    }

    suspend fun importSaveFromJson(slotId: String, jsonStr: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (jsonStr.isBlank()) {
                throw IllegalArgumentException("O conteúdo do backup JSON está vazio.")
            }

            val gson = Gson()
            val backup = gson.fromJson(jsonStr, SaveBackupData::class.java)
                ?: throw IllegalArgumentException("Falha ao desserializar a estrutura JSON do backup.")

            if (!validateBackupIntegrity(backup)) {
                _toastMessage.emit("Backup inválido ou incompatível.")
                throw IllegalArgumentException("A estrutura do backup não passou na validação de integridade.")
            }

            val repo = gameSaveRepository.getRepositoryForSlot(slotId)
            repo.withTransaction {
                repo.deleteSave()
                repo.deleteTeams()
                repo.deletePlayers()
                repo.deleteFixtures()
                repo.deleteRecords()
                repo.deleteTransactions()
                repo.deleteInstallments()
                repo.deleteLoans()
                repo.deleteLegends()
                repo.deleteOffers()
                repo.deleteOrders()
                repo.deleteAllHistorico()

                repo.saveGameSave(backup.save)
                repo.saveTeams(backup.teams)
                repo.savePlayers(backup.players)
                repo.saveFixtures(backup.fixtures)

                val recordsList = backup.records ?: emptyList()
                if (recordsList.isNotEmpty()) {
                    repo.saveRecords(recordsList)
                }
                val txList = backup.transactions ?: emptyList()
                for (tx in txList) {
                    repo.saveTransaction(tx)
                }
                val instList = backup.installments ?: emptyList()
                if (instList.isNotEmpty()) {
                    repo.saveInstallments(instList)
                }
                val loansList = backup.loans ?: emptyList()
                if (loansList.isNotEmpty()) {
                    repo.saveLoans(loansList)
                }
                val legendsList = backup.legends ?: emptyList()
                if (legendsList.isNotEmpty()) {
                    repo.saveLegends(legendsList)
                }
                val offersList = backup.offers ?: emptyList()
                if (offersList.isNotEmpty()) {
                    repo.saveOffers(offersList)
                }
                val ordersList = backup.orders ?: emptyList()
                for (order in ordersList) {
                    repo.saveOrder(order)
                }
                val histList = backup.historicoEvolucao ?: emptyList()
                if (histList.isNotEmpty()) {
                    repo.saveHistoricoEvolucaoList(histList)
                }

                val restoredSave = repo.getGameSave()
                val restoredTeams = repo.getAllTeams()
                val restoredPlayers = repo.getAllPlayers()
                if (restoredSave == null || restoredTeams.isEmpty() || restoredPlayers.isEmpty()) {
                    throw IllegalStateException("Integridade pós-restauração falhou.")
                }
            }

            val restoredTeamName = backup.teams.firstOrNull { it.id == backup.save.playerTeamId }?.name ?: "Sem Clube"
            preferencesRepository.updateSlotMetadata(
                saveId = slotId,
                coachName = backup.save.coachName,
                teamName = restoredTeamName,
                season = backup.save.currentSeason,
                week = backup.save.currentWeek,
                balance = backup.save.bankBalance
            )
            loadSaveSlots()
            _toastMessage.emit("Save restaurado com sucesso!")
            true
        }.onFailure { e ->
            Log.e("SaveManagerViewModel", "Erro ao importar backup no slot $slotId", e)
            _toastMessage.emit("Erro ao restaurar backup: ${e.localizedMessage ?: "Falha desconhecida"}")
        }
    }
}
