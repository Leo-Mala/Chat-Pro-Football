package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.repository.SlotRecoveryRequiredException
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * O bootstrap do editor pode materializar times/jogadores em um slot pré-carreira. Duas entradas
 * concorrentes da mesma sessão não podem observar a mesma tabela vazia e semear o mesmo universo
 * duas vezes.
 *
 * A serialização é por GameViewModel, não global ao processo. Assim uma sessão encerrada/teste
 * cancelado nunca consegue manter um mutex global preso e bloquear uma nova sessão independente.
 * WeakHashMap evita reter ViewModels depois que seu lifecycle termina.
 */
private val editorPreparationMutexes = WeakHashMap<GameViewModel, Mutex>()
private val editorPlayerSaveMutexes = WeakHashMap<GameViewModel, Mutex>()
private val editorPreparedSessions = WeakHashMap<GameViewModel, SaveSession>()
private val editorPreparationWaiters = WeakHashMap<GameViewModel, MutableList<CompletableDeferred<Unit>>>()
private val editorMutexesGuard = Any()

private fun GameViewModel.editorPreparationMutex(): Mutex =
    synchronized(editorMutexesGuard) {
        editorPreparationMutexes.getOrPut(this) { Mutex() }
    }

private fun GameViewModel.editorPlayerSaveMutex(): Mutex =
    synchronized(editorMutexesGuard) {
        editorPlayerSaveMutexes.getOrPut(this) { Mutex() }
    }

private fun GameViewModel.isEditorSessionCurrent(session: SaveSession): Boolean =
    activeSaveSession.value === session && currentSaveId.value == session.slotId

private fun GameViewModel.clearPreparedEditorSession() {
    synchronized(editorMutexesGuard) {
        editorPreparedSessions.remove(this)
    }
}

private fun GameViewModel.markPreparedEditorSession(session: SaveSession) {
    synchronized(editorMutexesGuard) {
        editorPreparedSessions[this] = session
    }
}

/**
 * Retorna exclusivamente o repositório cuja sessão concluiu o bootstrap do Editor e continua ativa.
 * Assim nenhuma ação exposta durante o primeiro frame, nem uma ação atrasada depois de Voltar/trocar
 * de slot, consegue gravar em um banco que não passou pela preparação atual.
 */
private fun GameViewModel.preparedEditorRepositoryOrNull() =
    synchronized(editorMutexesGuard) {
        val preparedSession = editorPreparedSessions[this]
        if (
            preparedSession != null &&
            activeSaveSession.value === preparedSession &&
            currentSaveId.value == preparedSession.slotId
        ) {
            preparedSession.repository
        } else {
            null
        }
    }

/**
 * Aguarda somente a preparação que pertence ao lifecycle da tela. A mutação nunca inicia ou recria
 * uma sessão por conta própria: isso preserva o contrato fail-closed e evita que um `viewModelScope`
 * destacado continue semeando depois que o usuário saiu do Editor. O waiter também cobre o primeiro
 * frame, quando a ação pode chegar imediatamente antes de o `LaunchedEffect` entrar no bootstrap.
 */
private suspend fun GameViewModel.awaitPreparedEditorRepositoryOrNull(): GameRepository? {
    preparedEditorRepositoryOrNull()?.let { return it }

    val waiter = CompletableDeferred<Unit>()
    synchronized(editorMutexesGuard) {
        editorPreparationWaiters.getOrPut(this) { mutableListOf() }.add(waiter)
    }

    // Fecha a janela em que a preparação pode ter terminado entre a primeira leitura e o registro.
    preparedEditorRepositoryOrNull()?.let { repository ->
        synchronized(editorMutexesGuard) {
            editorPreparationWaiters[this]?.remove(waiter)
        }
        waiter.complete(Unit)
        return repository
    }

    val signalled = withTimeoutOrNull(120_000L) {
        waiter.await()
        true
    } ?: false

    synchronized(editorMutexesGuard) {
        editorPreparationWaiters[this]?.let { waiters ->
            waiters.remove(waiter)
            if (waiters.isEmpty()) editorPreparationWaiters.remove(this)
        }
    }

    val repository = preparedEditorRepositoryOrNull()
    if (!signalled && repository == null) {
        _toastMessage.emit("O Editor ainda não está disponível. Tente novamente.")
    }
    return repository
}

private fun GameViewModel.releaseEditorPreparationWaiters() {
    val waiters = synchronized(editorMutexesGuard) {
        editorPreparationWaiters.remove(this)?.toList().orEmpty()
    }
    waiters.forEach { waiter -> waiter.complete(Unit) }
}

private suspend fun notifyEditorReady(onReady: (Boolean) -> Unit, ready: Boolean) {
    withContext(Dispatchers.Main) {
        onReady(ready)
    }
}

/**
 * Prepara o slot do Editor na coroutine do chamador.
 *
 * A tela chama esta função dentro de `LaunchedEffect`; portanto sair do Editor cancela esta mesma
 * coroutine. Não existe um `viewModelScope.launch` destacado capaz de continuar semeando/reabrindo
 * um slot depois que `exitToSavesMenu()` invalidou a sessão.
 */
suspend fun GameViewModel.ensureSaveActiveForEditor(
    preparationCheckpoint: suspend () -> Unit = {},
    preparationAttemptCheckpoint: suspend () -> Unit = {},
    onReady: (Boolean) -> Unit = {}
) = withContext(Dispatchers.IO) {
    try {
        preparationAttemptCheckpoint()
        editorPreparationMutex().withLock {
            clearPreparedEditorSession()
            val targetSaveId = _currentSaveId.value ?: "1"
            var editorSession: SaveSession? = null
            try {
                val session = getOrCreateSession(targetSaveId)
                editorSession = session
                val currentRepository = session.repository
                if (_currentSaveId.value == null) {
                    _currentSaveId.value = targetSaveId
                }
                if (!isEditorSessionCurrent(session)) {
                    notifyEditorReady(onReady, false)
                    return@withLock
                }

                preparationCheckpoint()
                if (!isEditorSessionCurrent(session)) {
                    notifyEditorReady(onReady, false)
                    return@withLock
                }

                var dbTeams = currentRepository.getAllTeams()
                if (dbTeams.isEmpty()) {
                    val seededTeams = mutableListOf<Team>()
                    for (countryKey in GlobalFootballSystem.keys) {
                        val templates = DefaultData.getTeamsForCountry(countryKey)
                        for (t in templates) {
                            val globalId = GlobalFootballSystem.getGlobalId(countryKey, t.name)
                            seededTeams.add(
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
                                    isPlayerControlled = (globalId == 1L)
                                )
                            )
                        }
                    }
                    if (!isEditorSessionCurrent(session)) {
                        notifyEditorReady(onReady, false)
                        return@withLock
                    }
                    currentRepository.saveTeams(seededTeams)
                    dbTeams = seededTeams
                }

                if (!isEditorSessionCurrent(session)) {
                    notifyEditorReady(onReady, false)
                    return@withLock
                }

                val allDbPlayers = currentRepository.getAllPlayers()
                if (allDbPlayers.isEmpty()) {
                    val allPlayersToSave = mutableListOf<Player>()
                    for (t in dbTeams) {
                        val roster = DefaultData.generateRosterForTeam(t.id, t.rating, t.name, t.country)
                        allPlayersToSave.addAll(roster)
                    }
                    if (!isEditorSessionCurrent(session)) {
                        notifyEditorReady(onReady, false)
                        return@withLock
                    }
                    currentRepository.savePlayers(allPlayersToSave)
                }

                val ready = isEditorSessionCurrent(session)
                if (ready) {
                    markPreparedEditorSession(session)
                } else {
                    clearPreparedEditorSession()
                }
                notifyEditorReady(onReady, ready)
            } catch (e: CancellationException) {
                clearPreparedEditorSession()
                throw e
            } catch (e: SlotRecoveryRequiredException) {
                clearPreparedEditorSession()
                Log.w(
                    "GameViewModel",
                    "Editor bloqueado para slot $targetSaveId em recuperação: ${e.inspection.failureReason}"
                )
                val session = editorSession
                if (session == null || isEditorSessionCurrent(session)) {
                    exitToSavesMenu()
                    loadSaveSlots()
                }
                notifyEditorReady(onReady, false)
            } catch (e: Exception) {
                clearPreparedEditorSession()
                Log.e("GameViewModel", "Falha fail-closed ao preparar editor no slot $targetSaveId", e)
                val session = editorSession
                if (session == null || isEditorSessionCurrent(session)) {
                    exitToSavesMenu()
                    loadSaveSlots()
                }
                notifyEditorReady(onReady, false)
            }
        }
    } finally {
        // Acorda ações do primeiro frame tanto em sucesso quanto em falha/cancelamento. Elas ainda
        // precisam passar por `preparedEditorRepositoryOrNull()`, então um bootstrap incompleto nunca
        // autoriza escrita.
        releaseEditorPreparationWaiters()
    }
}

fun GameViewModel.editorPlayersForTeamFlow(teamId: Long?): Flow<List<Player>> =
    activeRepositoryFlow.flatMapLatest { repository ->
        if (repository == null || teamId == null) flowOf(emptyList())
        else repository.getPlayersForTeamFlow(teamId)
    }

sealed interface EditorPlayersLoadState {
    data object Inactive : EditorPlayersLoadState
    data class Loading(val teamId: Long?) : EditorPlayersLoadState
    data class Ready(val teamId: Long?, val players: List<Player>) : EditorPlayersLoadState
}

private fun Flow<List<Player>>.asEditorPlayersLoadState(teamId: Long?): Flow<EditorPlayersLoadState> =
    map<List<Player>, EditorPlayersLoadState> { players ->
        EditorPlayersLoadState.Ready(teamId, players)
    }.onStart { emit(EditorPlayersLoadState.Loading(teamId)) }

internal fun editorPlayersLoadStateFlow(
    repositoryFlow: Flow<GameRepository?>,
    teamId: Long?
): Flow<EditorPlayersLoadState> =
    repositoryFlow.flatMapLatest { repository ->
        if (repository == null) {
            flowOf(EditorPlayersLoadState.Loading(teamId))
        } else {
            val playersFlow = if (teamId == null) repository.allPlayersFlow
            else repository.getPlayersForTeamFlow(teamId)
            playersFlow.asEditorPlayersLoadState(teamId)
        }
    }

fun GameViewModel.editorPlayersForEditorFlow(
    teamId: Long?,
    active: Boolean
): Flow<EditorPlayersLoadState> {
    if (!active) return flowOf(EditorPlayersLoadState.Inactive)
    return editorPlayersLoadStateFlow(activeRepositoryFlow, teamId)
}

internal fun editorRosterCountOrNull(
    state: EditorPlayersLoadState,
    teamId: Long?
): Int? = (state as? EditorPlayersLoadState.Ready)
    ?.takeIf { it.teamId == teamId }
    ?.players
    ?.size

internal fun applyEditedTeamStrength(players: List<Player>, newRating: Int): List<Player> =
    players.map { player ->
        val currentAttr = player.getAtributosObject()
        val oldForce = player.force.coerceAtLeast(1)
        val ratio = newRating.toDouble() / oldForce.toDouble()
        val scaledAttr = currentAttr.copy(
            finalizacao = (currentAttr.finalizacao * ratio).roundToInt().coerceIn(10, 99),
            passe = (currentAttr.passe * ratio).roundToInt().coerceIn(10, 99),
            velocidade = (currentAttr.velocidade * ratio).roundToInt().coerceIn(10, 99),
            forca = (currentAttr.forca * ratio).roundToInt().coerceIn(10, 99),
            visaoJogo = (currentAttr.visaoJogo * ratio).roundToInt().coerceIn(10, 99),
            desarme = (currentAttr.desarme * ratio).roundToInt().coerceIn(10, 99)
        )
        player.copy(
            force = newRating,
            potential = maxOf(player.potential, newRating + 3).coerceIn(15, 99),
            atributosJson = AtributosConverter.atributosToJson(scaledAttr),
            finishing = scaledAttr.finalizacao,
            passing = scaledAttr.passe,
            pace = scaledAttr.velocidade,
            strength = scaledAttr.forca,
            vision = scaledAttr.visaoJogo,
            defense = scaledAttr.desarme
        )
    }

internal fun synchronizeExistingRosterForEditedTeam(
    players: List<Player>,
    newRating: Int
): List<Player> = applyEditedTeamStrength(players, newRating)

internal fun resolveEditedTeamRating(team: Team, roster: List<Player>): Int =
    if (roster.isNotEmpty() && roster.all { it.force == team.rating }) {
        team.rating
    } else {
        GameEngine.calculateTeamRating(roster)
    }

fun GameViewModel.ensureRosterForTeam(teamId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val editorRepository = preparedEditorRepositoryOrNull()
            ?: awaitPreparedEditorRepositoryOrNull()
            ?: return@launch
        val players = editorRepository.getPlayersByTeam(teamId)
        if (players.isEmpty()) {
            val defaultPlayers = List(18) { idx ->
                Player(
                    teamId = teamId,
                    name = "Jogador ${idx + 1}",
                    position = if (idx == 0) "GK" else if (idx < 5) "DF" else if (idx < 10) "MF" else "FW",
                    force = 60 + (idx % 15),
                    potential = 75,
                    age = 20 + (idx % 10),
                    salary = 5000L
                )
            }
            editorRepository.savePlayers(defaultPlayers)
        }
    }
}

fun GameViewModel.saveTeamFromEditor(team: Team) {
    viewModelScope.launch(Dispatchers.IO) {
        val editorRepository = preparedEditorRepositoryOrNull()
            ?: awaitPreparedEditorRepositoryOrNull()
            ?: return@launch
        val finalTeamId = if (team.id == 0L) System.currentTimeMillis() else team.id
        val teamToSave = team.copy(id = finalTeamId)

        editorRepository.withTransaction {
            editorRepository.saveTeams(listOf(teamToSave))
            val existingPlayers = editorRepository.getPlayersByTeam(finalTeamId)
            if (existingPlayers.isEmpty()) {
                val generatedRoster = DefaultData.generateRosterForTeam(
                    finalTeamId,
                    teamToSave.rating,
                    teamToSave.name,
                    teamToSave.country
                )
                editorRepository.savePlayers(
                    synchronizeExistingRosterForEditedTeam(generatedRoster, teamToSave.rating)
                )
            } else {
                editorRepository.updatePlayers(
                    synchronizeExistingRosterForEditedTeam(existingPlayers, teamToSave.rating)
                )
            }
        }
    }
}

fun GameViewModel.saveTeamStrength(teamId: Long, attack: Int, mid: Int, def: Int) {
    viewModelScope.launch(Dispatchers.IO) {
        val editorRepository = preparedEditorRepositoryOrNull()
            ?: awaitPreparedEditorRepositoryOrNull()
            ?: return@launch
        val newRating = ((attack + mid + def) / 3).coerceIn(15, 99)
        editorRepository.withTransaction {
            val team = editorRepository.getTeam(teamId) ?: return@withTransaction
            editorRepository.updateTeam(team.copy(rating = newRating))
            val players = editorRepository.getPlayersByTeam(teamId)
            editorRepository.updatePlayers(
                synchronizeExistingRosterForEditedTeam(players, newRating)
            )
        }
    }
}

fun GameViewModel.deleteTeamFromEditor(teamId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val editorRepository = preparedEditorRepositoryOrNull()
            ?: awaitPreparedEditorRepositoryOrNull()
            ?: return@launch
        editorRepository.deleteTeam(teamId)
    }
}

/**
 * Persiste a edição do atleta e recalcula a força dos clubes afetados na mesma transação Room.
 * Só uma gravação do diálogo pode ficar em voo por ViewModel. Isso protege especialmente o cadastro
 * com id=0 contra duplo toque, que de outro modo poderia gerar duas linhas auto-ID para o mesmo atleta.
 */
fun GameViewModel.savePlayerFromEditor(
    player: Player,
    onComplete: (Boolean) -> Unit = {}
) {
    viewModelScope.launch(Dispatchers.IO) {
        val editorRepository = preparedEditorRepositoryOrNull()
            ?: awaitPreparedEditorRepositoryOrNull()
        if (editorRepository == null) {
            withContext(Dispatchers.Main) { onComplete(false) }
            return@launch
        }

        val saveMutex = editorPlayerSaveMutex()
        if (!saveMutex.tryLock()) {
            withContext(Dispatchers.Main) { onComplete(false) }
            return@launch
        }
        try {
            if (preparedEditorRepositoryOrNull() !== editorRepository) {
                withContext(Dispatchers.Main) { onComplete(false) }
                return@launch
            }
            editorRepository.withTransaction {
                val previous = if (player.id == 0L) null else editorRepository.getPlayer(player.id)
                if (player.id == 0L) {
                    editorRepository.savePlayers(listOf(player))
                } else {
                    editorRepository.updatePlayer(player)
                }

                val affectedTeamIds = buildSet {
                    previous?.teamId?.let(::add)
                    player.teamId?.let(::add)
                }
                for (teamId in affectedTeamIds) {
                    val roster = editorRepository.getPlayersByTeam(teamId)
                    val team = editorRepository.getTeam(teamId) ?: continue
                    val calculated = resolveEditedTeamRating(team, roster)
                    if (team.rating != calculated) {
                        editorRepository.updateTeam(team.copy(rating = calculated))
                    }
                }
            }
            withContext(Dispatchers.Main) { onComplete(true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("GameViewModel", "Falha ao salvar jogador pelo editor", e)
            _toastMessage.emit("Não foi possível salvar o jogador. Tente novamente.")
            withContext(Dispatchers.Main) { onComplete(false) }
        } finally {
            saveMutex.unlock()
        }
    }
}

fun GameViewModel.deletePlayerFromEditor(playerId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val editorRepository = preparedEditorRepositoryOrNull()
            ?: awaitPreparedEditorRepositoryOrNull()
            ?: return@launch
        editorRepository.deletePlayer(playerId)
    }
}

fun GameViewModel.transferPlayerFromEditor(playerId: Long, targetTeamId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val editorRepository = preparedEditorRepositoryOrNull()
            ?: awaitPreparedEditorRepositoryOrNull()
            ?: return@launch
        val player = editorRepository.getPlayer(playerId) ?: return@launch
        val updated = player.copy(teamId = targetTeamId, originalTeamId = null, isOnLoan = false, loanWeeksRemaining = 0)
        editorRepository.updatePlayer(updated)
    }
}
