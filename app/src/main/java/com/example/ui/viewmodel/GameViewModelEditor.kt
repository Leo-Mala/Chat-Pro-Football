package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.repository.SlotRecoveryRequiredException
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
private val editorPreparationMutexesGuard = Any()

private fun GameViewModel.editorPreparationMutex(): Mutex =
    synchronized(editorPreparationMutexesGuard) {
        editorPreparationMutexes.getOrPut(this) { Mutex() }
    }

private fun GameViewModel.isEditorSessionCurrent(session: SaveSession): Boolean =
    activeSaveSession.value === session && currentSaveId.value == session.slotId

private suspend fun notifyEditorReady(onReady: (Boolean) -> Unit, ready: Boolean) {
    withContext(Dispatchers.Main) {
        onReady(ready)
    }
}

fun GameViewModel.ensureSaveActiveForEditor(
    preparationCheckpoint: suspend () -> Unit = {},
    preparationAttemptCheckpoint: suspend () -> Unit = {},
    onReady: (Boolean) -> Unit = {}
) {
    viewModelScope.launch(Dispatchers.IO) {
        // Checkpoint inerte em produção. Em teste, comprova que uma segunda coroutine realmente
        // chegou à fronteira de serialização antes de verificarmos que ela não entrou no bootstrap.
        preparationAttemptCheckpoint()
        editorPreparationMutex().withLock {
            val targetSaveId = _currentSaveId.value ?: "1"
            var editorSession: SaveSession? = null
            try {
                // Materializa/valida Room em IO antes de publicar currentSaveId. O preparo inteiro fica
                // vinculado à mesma SaveSession e nunca pode concluir em favor de uma sessão posterior.
                val session = getOrCreateSession(targetSaveId)
                editorSession = session
                val currentRepository = session.repository
                if (_currentSaveId.value == null) {
                    _currentSaveId.value = targetSaveId
                }
                if (!isEditorSessionCurrent(session)) {
                    notifyEditorReady(onReady, false)
                    return@launch
                }

                // Checkpoint inerte em produção e útil para provar races de lifecycle sem sleeps frágeis.
                preparationCheckpoint()
                if (!isEditorSessionCurrent(session)) {
                    notifyEditorReady(onReady, false)
                    return@launch
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
                        return@launch
                    }
                    currentRepository.saveTeams(seededTeams)
                    dbTeams = seededTeams
                }

                if (!isEditorSessionCurrent(session)) {
                    notifyEditorReady(onReady, false)
                    return@launch
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
                        return@launch
                    }
                    currentRepository.savePlayers(allPlayersToSave)
                }

                // A UI só navega se a sessão usada no preparo ainda for exatamente a ativa.
                notifyEditorReady(onReady, isEditorSessionCurrent(session))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SlotRecoveryRequiredException) {
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
                Log.e("GameViewModel", "Falha fail-closed ao preparar editor no slot $targetSaveId", e)
                val session = editorSession
                if (session == null || isEditorSessionCurrent(session)) {
                    exitToSavesMenu()
                    loadSaveSlots()
                }
                notifyEditorReady(onReady, false)
            }
        }
    }
}

fun GameViewModel.ensureRosterForTeam(teamId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val players = repo.getPlayersByTeam(teamId)
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
            repo.savePlayers(defaultPlayers)
        }
    }
}

fun GameViewModel.saveTeamFromEditor(team: Team) {
    viewModelScope.launch(Dispatchers.IO) {
        val finalTeamId = if (team.id == 0L) System.currentTimeMillis() else team.id
        val teamToSave = team.copy(id = finalTeamId)
        repo.saveTeams(listOf(teamToSave))

        val existingPlayers = repo.getPlayersByTeam(finalTeamId)
        if (existingPlayers.isEmpty()) {
            val roster = DefaultData.generateRosterForTeam(finalTeamId, teamToSave.rating, teamToSave.name, teamToSave.country)
            repo.savePlayers(roster)
        } else {
            val currentAvg = existingPlayers.map { it.force }.average().toInt()
            val delta = teamToSave.rating - currentAvg
            if (delta != 0) {
                val updatedPlayers = existingPlayers.map { p ->
                    val newForce = (p.force + delta).coerceIn(30, 99)
                    p.copy(
                        force = newForce,
                        potential = maxOf(p.potential, newForce + 3).coerceIn(35, 99)
                    )
                }
                repo.savePlayers(updatedPlayers)
            }
        }
    }
}

fun GameViewModel.saveTeamStrength(teamId: Long, attack: Int, mid: Int, def: Int) {
    viewModelScope.launch(Dispatchers.IO) {
        val newRating = ((attack + mid + def) / 3).coerceIn(15, 99)
        val team = repo.getTeam(teamId) ?: return@launch
        repo.updateTeam(team.copy(rating = newRating))

        // Atualizar também os atributos individuais dos jogadores do time proporcionalmente
        val players = repo.getPlayersByTeam(teamId)
        val updatedPlayers = players.map { player ->
            val currentAttr = player.getAtributosObject()
            val oldForce = player.force.coerceAtLeast(1)
            val ratio = newRating.toDouble() / oldForce.toDouble()

            // Escala os atributos mantendo as proporções originais do atleta
            val scaledAttr = currentAttr.copy(
                finalizacao = (currentAttr.finalizacao * ratio).roundToInt().coerceIn(10, 99),
                passe = (currentAttr.passe * ratio).roundToInt().coerceIn(10, 99),
                velocidade = (currentAttr.velocidade * ratio).roundToInt().coerceIn(10, 99),
                forca = (currentAttr.forca * ratio).roundToInt().coerceIn(10, 99),
                visaoJogo = (currentAttr.visaoJogo * ratio).roundToInt().coerceIn(10, 99),
                desarme = (currentAttr.desarme * ratio).roundToInt().coerceIn(10, 99)
            )

            val newJson = AtributosConverter.atributosToJson(scaledAttr)
            player.copy(
                force = newRating,
                potential = maxOf(player.potential, newRating + 3).coerceIn(15, 99),
                atributosJson = newJson,
                finishing = scaledAttr.finalizacao,
                passing = scaledAttr.passe,
                pace = scaledAttr.velocidade,
                strength = scaledAttr.forca,
                vision = scaledAttr.visaoJogo,
                defense = scaledAttr.desarme
            )
        }
        repo.updatePlayers(updatedPlayers)
    }
}

fun GameViewModel.deleteTeamFromEditor(teamId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        repo.deleteTeam(teamId)
    }
}

/**
 * Persiste a edição do atleta e recalcula a força dos clubes afetados na mesma transação Room.
 * O callback só é entregue no Main depois do commit, para que a tela nunca feche sobre um snapshot
 * antigo de jogador/time. Ao transferir um atleta pelo editor, tanto o clube de origem quanto o de
 * destino são recalculados.
 */
fun GameViewModel.savePlayerFromEditor(
    player: Player,
    onComplete: (Boolean) -> Unit = {}
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            repo.withTransaction {
                val previous = if (player.id == 0L) null else repo.getPlayer(player.id)
                if (player.id == 0L) {
                    repo.savePlayers(listOf(player))
                } else {
                    repo.updatePlayer(player)
                }

                val affectedTeamIds = buildSet {
                    previous?.teamId?.let(::add)
                    player.teamId?.let(::add)
                }
                for (teamId in affectedTeamIds) {
                    val roster = repo.getPlayersByTeam(teamId)
                    val team = repo.getTeam(teamId) ?: continue
                    val calculated = GameEngine.calculateTeamRating(roster)
                    if (team.rating != calculated) {
                        repo.updateTeam(team.copy(rating = calculated))
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
        }
    }
}

fun GameViewModel.deletePlayerFromEditor(playerId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        repo.deletePlayer(playerId)
    }
}

fun GameViewModel.transferPlayerFromEditor(playerId: Long, targetTeamId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val player = repo.getPlayer(playerId) ?: return@launch
        val updated = player.copy(teamId = targetTeamId, originalTeamId = null, isOnLoan = false, loanWeeksRemaining = 0)
        repo.updatePlayer(updated)
    }
}
