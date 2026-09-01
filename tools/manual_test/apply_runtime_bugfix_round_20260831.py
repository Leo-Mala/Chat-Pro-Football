from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_exact(path: str, old: str, new: str, expected: int = 1) -> None:
    text = read(path)
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"{path}: expected {expected}, found {count}: {old[:120]!r}")
    write(path, text.replace(old, new))


def replace_regex(path: str, pattern: str, replacement: str) -> None:
    text = read(path)
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{path}: regex did not match exactly once: {pattern[:120]!r}")
    write(path, updated)


# ---------------------------------------------------------------------------
# A. SAVE/LOAD: existing career open is observational and has one repair pass.
# ---------------------------------------------------------------------------
vm = "app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt"
replace_regex(
    vm,
    r"    fun selectSaveSlot\(saveId: String\) \{.*?\n    \}\n\n    fun repairRostersIfNecessary\(\)",
    '''    fun selectSaveSlot(saveId: String) {
        val gen = sessionGeneration.incrementAndGet()
        val repository = saveRepository.getRepositoryForSlot(saveId)
        val session = SaveSession(saveId, repository, gen)
        _activeSaveSession.value = session
        _currentSaveId.value = saveId
        _selectedTeamId.value = null
        val loadStartedAtNs = System.nanoTime()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (session.generation != sessionGeneration.get()) return@launch
                val targetRepo = session.repository
                val save = targetRepo.getGameSave()

                if (save == null) {
                    // Only a pre-career slot may materialize defaults. Opening an existing career
                    // must not seed, advance the calendar, debit finances or regenerate fixtures.
                    seedAllDefaultTeams(targetRepo, _selectedCountry.value)
                    if (session.generation != sessionGeneration.get()) return@launch
                    withContext(Dispatchers.Main) { _selectedCountry.value = "Brasil" }
                } else {
                    val targetTeam = targetRepo.getTeam(save.playerTeamId)
                    if (targetTeam != null) {
                        val resolvedCountry = DefaultData.getCountryForTeam(targetTeam.name)
                        withContext(Dispatchers.Main) { _selectedCountry.value = resolvedCountry }
                    }
                }

                // One integrity preflight per SaveSession. The gameSave collector must not run a
                // second global repair while the same slot is still opening.
                repairRostersIfNecessarySync(session)
                val elapsedMs = (System.nanoTime() - loadStartedAtNs) / 1_000_000L
                Log.i("CareerLoadPerformance", "slot=$saveId existing=${save != null} total=${elapsedMs}ms")
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

    fun repairRostersIfNecessary()'''
)
replace_exact(
    vm,
    '''                            // Integridade do Banco: delega verificação e reparo de elencos ao UseCase
                            if (!hasSelfHealedThisSave) {
                                hasSelfHealedThisSave = true
                                val integrityUseCase = DatabaseIntegrityUseCase(currentRepository)
                                integrityUseCase.repairDatabase()
                            }
''',
    '''                            // Integridade já foi verificada uma única vez pela SaveSession em selectSaveSlot.
'''
)

# Match -> Dashboard race: keep FINISHED until weekly close is durable.
replace_exact(
    vm,
    '''    fun exitLiveMatch() {
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
''',
    '''    fun exitLiveMatch() {
        liveMatchJob?.cancel()
        val session = _activeSaveSession.value ?: return
        val generation = session.generation
        viewModelScope.launch(Dispatchers.IO) {
            simulationMutex.withLock {
                if (generation != sessionGeneration.get() || _activeSaveSession.value !== session) return@withLock
                try {
                    val targetRepo = session.repository
                    val save = targetRepo.getGameSave() ?: return@withLock
                    val weekFixtures = targetRepo.getFixturesForWeek(save.currentSeason, save.currentWeek)
                    val hasPendingUserFixture = weekFixtures.any {
                        !it.isPlayed && (it.homeTeamId == save.playerTeamId || it.awayTeamId == save.playerTeamId)
                    }
                    if (!hasPendingUserFixture) {
                        simulateCpuMatchesForCurrentWeek()
                        if (!processWeekEndEconomicAndEvolution()) {
                            _toastMessage.emit("A partida terminou, mas o fechamento semanal ainda não foi concluído. Tente voltar novamente.")
                            return@withLock
                        }
                    }
                    withContext(Dispatchers.Main) {
                        if (generation == sessionGeneration.get() && _activeSaveSession.value === session) {
                            _matchState.value = MatchState.IDLE
                            liveMatchFixture = null
                            liveMatchHomeTeam = null
                            liveMatchAwayTeam = null
                            liveMatchHomePlayers = emptyList()
                            liveMatchAwayPlayers = emptyList()
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("GameViewModel", "Falha ao concluir semana ao sair da partida", e)
                    _toastMessage.emit("Não foi possível concluir o fechamento semanal. O estado da partida foi preservado.")
                }
            }
        }
    }
'''
)

# Restart is session-bound and reports real completion to the confirmation dialog.
replace_regex(
    vm,
    r"    fun restartCurrentSeason\(\) \{.*?\n    \}\n\n    fun selectCountry",
    '''    fun restartCurrentSeason(onComplete: (Boolean) -> Unit = {}) {
        _isSimulatingSeason.value = false
        val session = _activeSaveSession.value
        if (session == null) {
            onComplete(false)
            return
        }
        val generation = session.generation
        isProcessingAction.value = true
        viewModelScope.launch(Dispatchers.IO) {
            var success = false
            try {
                simulationMutex.withLock {
                    if (generation != sessionGeneration.get() || _activeSaveSession.value !== session) return@withLock
                    val targetRepo = session.repository
                    val save = targetRepo.getGameSave() ?: return@withLock
                    val currentTeams = targetRepo.getAllTeams()
                    val replacementFixtures = generateFixturesForSeason(save.currentSeason, currentTeams, save.playerTeamId)
                    val restarted = targetRepo.restartSeasonStateAtomically(
                        expectedSeason = save.currentSeason,
                        expectedPlayerTeamId = save.playerTeamId,
                        replacementFixtures = replacementFixtures
                    )
                    if (!restarted) {
                        _toastMessage.emit("O estado da carreira mudou durante o reinício. Tente novamente.")
                        return@withLock
                    }
                    _incomingOffers.value = emptyList()
                    _monthlyEvolutionSummary.value = null
                    success = true
                    _toastMessage.emit("Temporada atual reiniciada com sucesso.")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("GameViewModel", "Falha ao reiniciar temporada", e)
                _toastMessage.emit("Não foi possível reiniciar a temporada. Nenhum reinício parcial foi confirmado.")
            } finally {
                withContext(Dispatchers.Main) {
                    isProcessingAction.value = false
                    onComplete(success)
                }
            }
        }
    }

    fun selectCountry'''
)

# ---------------------------------------------------------------------------
# B. SIMULATION: weekly close has a real result, monthly stale conflict retries once,
# and the button stops at actual end of the current season or explicit user stop.
# ---------------------------------------------------------------------------
match_vm = "app/src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt"
replace_exact(
    match_vm,
    "suspend fun GameViewModel.processWeekEndEconomicAndEvolution() {\n    val requestedSave = repo.getGameSave() ?: return",
    "suspend fun GameViewModel.processWeekEndEconomicAndEvolution(): Boolean {\n    val requestedSave = repo.getGameSave() ?: return false"
)
replace_exact(
    match_vm,
    '''        return
    }

    if (weeklyCloseCommitted) {
        stagedIncomingOffer?.let { publishIncomingOffer(it) }
    }
}
''',
    '''        return false
    }

    if (weeklyCloseCommitted) {
        stagedIncomingOffer?.let { publishIncomingOffer(it) }
    }
    return weeklyCloseCommitted
}
'''
)
replace_regex(
    vm,
    r"    fun startSeasonSimulation\(\) \{.*?\n    \}\n\n    fun stopSeasonSimulation\(\)",
    '''    fun startSeasonSimulation() {
        if (_isSimulatingSeason.value || isProcessingAction.value) return
        val session = _activeSaveSession.value ?: return
        val generation = session.generation
        val targetRepo = session.repository
        _isSimulatingSeason.value = true
        _simulationLogs.value = emptyList()
        _simulationCompetitionName.value = "Iniciando simulação..."
        _simulationMatchInfo.value = "Processando rodada..."
        gameSave.value?.let { _simulationCurrentWeek.value = it.currentWeek }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                simulationMutex.withLock {
                    val initialSave = targetRepo.getGameSave() ?: error("Carreira não encontrada para simulação.")
                    val targetSeason = initialSave.currentSeason
                    cleanupDuplicateUnplayedFixtures(targetSeason)
                    while (_isSimulatingSeason.value && generation == sessionGeneration.get() && _activeSaveSession.value === session) {
                        val save = targetRepo.getGameSave() ?: error("Carreira indisponível durante a simulação.")
                        if (save.currentSeason != targetSeason) break
                        check(targetRepo.getFixturesForSeason(save.currentSeason).isNotEmpty()) {
                            "A temporada ${save.currentSeason} não possui calendário persistido."
                        }
                        val week = save.currentWeek
                        _simulationCurrentWeek.value = week
                        val userFixtures = targetRepo.getFixturesForWeek(save.currentSeason, week).filter {
                            !it.isPlayed && (it.homeTeamId == save.playerTeamId || it.awayTeamId == save.playerTeamId)
                        }
                        if (userFixtures.isNotEmpty()) {
                            for (fixture in userFixtures) {
                                if (!_isSimulatingSeason.value) break
                                val compName = DefaultData.getCompetitionName(fixture.competitionType, _selectedCountry.value)
                                _simulationCompetitionName.value = compName
                                val updated = simulateSingleUserFixture(fixture, save)
                                val home = targetRepo.getTeam(updated.homeTeamId) ?: GlobalFootballSystem.getVirtualTeam(updated.homeTeamId)
                                val away = targetRepo.getTeam(updated.awayTeamId) ?: GlobalFootballSystem.getVirtualTeam(updated.awayTeamId)
                                val result = "${home.name} ${updated.homeScore} - ${updated.awayScore} ${away.name}"
                                _simulationMatchInfo.value = result
                                _simulationLogs.value = (listOf("Temp. ${save.currentSeason} | Sem. $week | $compName: $result") + _simulationLogs.value).take(25)
                                delay(1200)
                            }
                        } else {
                            _simulationCompetitionName.value = "Sem Jogo / Descanso"
                            _simulationMatchInfo.value = "Seu clube esteve de folga."
                            _simulationLogs.value = (listOf("Temp. ${save.currentSeason} | Sem. $week | Descanso") + _simulationLogs.value).take(25)
                            delay(600)
                        }
                        if (!_isSimulatingSeason.value) break
                        simulateCpuMatchesForCurrentWeek()
                        var closed = processWeekEndEconomicAndEvolution()
                        if (!closed && _isSimulatingSeason.value) {
                            _simulationMatchInfo.value = "Revalidando fechamento da Semana $week..."
                            closed = processWeekEndEconomicAndEvolution()
                        }
                        check(closed) { "A Semana $week não pôde ser fechada atomicamente." }
                        val updatedSave = targetRepo.getGameSave() ?: error("Carreira indisponível após fechamento semanal.")
                        if (updatedSave.currentSeason != targetSeason) {
                            _simulationLogs.value = (listOf("🏆 Temporada $targetSeason finalizada com sucesso!") + _simulationLogs.value).take(25)
                            _simulationMatchInfo.value = "Temporada concluída."
                            _isSimulatingSeason.value = false
                            break
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("GameViewModel", "Erro durante a simulação de temporada", e)
                val detail = e.localizedMessage ?: "erro interno sem detalhe"
                _simulationLogs.value = (listOf("Falha na simulação: $detail") + _simulationLogs.value).take(25)
                _simulationMatchInfo.value = "Simulação interrompida por falha de persistência."
                _toastMessage.emit("A simulação encontrou uma falha e foi interrompida sem fingir cancelamento: $detail")
            } finally {
                _isSimulatingSeason.value = false
                if (_autoSaveEnabled.value && generation == sessionGeneration.get() && _activeSaveSession.value === session) {
                    performSaveGameInternal(manual = false)
                }
            }
        }
    }

    fun stopSeasonSimulation()'''
)

# ---------------------------------------------------------------------------
# C. EDITED STRENGTH: one coherent overall/attributes representation.
# ---------------------------------------------------------------------------
editor_vm = "app/src/main/java/com/example/ui/viewmodel/GameViewModelEditor.kt"
editor_text = read(editor_vm)
marker = "fun GameViewModel.saveTeamFromEditor(team: Team) {\n"
if editor_text.count(marker) != 1:
    raise RuntimeError("GameViewModelEditor marker mismatch")
helper = '''private fun Atributos.shiftedForEditedOverall(delta: Int): Atributos = copy(
    reflexos=(reflexos+delta).coerceIn(10,99), pegada=(pegada+delta).coerceIn(10,99), umContraUm=(umContraUm+delta).coerceIn(10,99),
    saidaDeGol=(saidaDeGol+delta).coerceIn(10,99), lancamento=(lancamento+delta).coerceIn(10,99), desarme=(desarme+delta).coerceIn(10,99),
    marcacao=(marcacao+delta).coerceIn(10,99), cabeceio=(cabeceio+delta).coerceIn(10,99), passeCurto=(passeCurto+delta).coerceIn(10,99),
    cruzamento=(cruzamento+delta).coerceIn(10,99), drible=(drible+delta).coerceIn(10,99), passe=(passe+delta).coerceIn(10,99),
    primeiroToque=(primeiroToque+delta).coerceIn(10,99), finalizacao=(finalizacao+delta).coerceIn(10,99), chuteDeLonge=(chuteDeLonge+delta).coerceIn(10,99),
    controleBola=(controleBola+delta).coerceIn(10,99), posicionamento=(posicionamento+delta).coerceIn(10,99), concentracao=(concentracao+delta).coerceIn(10,99),
    sangueFrio=(sangueFrio+delta).coerceIn(10,99), antecipacao=(antecipacao+delta).coerceIn(10,99), bravura=(bravura+delta).coerceIn(10,99),
    trabalhoEquipe=(trabalhoEquipe+delta).coerceIn(10,99), decisao=(decisao+delta).coerceIn(10,99), semBola=(semBola+delta).coerceIn(10,99),
    visaoJogo=(visaoJogo+delta).coerceIn(10,99), criatividade=(criatividade+delta).coerceIn(10,99), agressividade=(agressividade+delta).coerceIn(10,99),
    lideranca=(lideranca+delta).coerceIn(10,99), regularidade=(regularidade+delta).coerceIn(10,99), agilidade=(agilidade+delta).coerceIn(10,99),
    impulsao=(impulsao+delta).coerceIn(10,99), forca=(forca+delta).coerceIn(10,99), velocidade=(velocidade+delta).coerceIn(10,99),
    aceleracao=(aceleracao+delta).coerceIn(10,99), resistencia=(resistencia+delta).coerceIn(10,99)
)

internal fun Player.withEditedOverall(targetRating: Int): Player {
    val target = targetRating.coerceIn(15, 99)
    val posicao = Posicao.fromCode(position)
    var coherent = getAtributosObject()
    repeat(8) {
        val delta = target - CalculadoraNota.calcularNota(posicao, coherent)
        if (delta != 0) coherent = coherent.shiftedForEditedOverall(delta)
    }
    return copy(
        force=target, potential=maxOf(potential,target+3).coerceIn(15,99), atributos=coherent,
        atributosJson=AtributosConverter.atributosToJson(coherent), finishing=coherent.finalizacao,
        passing=coherent.passe, pace=coherent.velocidade, strength=coherent.forca,
        vision=coherent.visaoJogo, defense=coherent.desarme
    )
}

'''
write(editor_vm, editor_text.replace(marker, helper + marker, 1))
replace_exact(
    editor_vm,
    '''                val updatedPlayers = existingPlayers.map { p ->
                    val newForce = (p.force + delta).coerceIn(30, 99)
                    p.copy(
                        force = newForce,
                        potential = maxOf(p.potential, newForce + 3).coerceIn(35, 99)
                    )
                }
''',
    '''                val updatedPlayers = existingPlayers.map { p ->
                    val newForce = (p.force + delta).coerceIn(30, 99)
                    p.withEditedOverall(newForce)
                }
'''
)
replace_regex(
    editor_vm,
    r"        val players = editorRepository.getPlayersByTeam\(teamId\)\n        val updatedPlayers = players.map \{ player ->.*?\n        editorRepository.updatePlayers\(updatedPlayers\)",
    '''        val players = editorRepository.getPlayersByTeam(teamId)
        val updatedPlayers = players.map { player -> player.withEditedOverall(newRating) }
        editorRepository.updatePlayers(updatedPlayers)'''
)
replace_exact(
    "app/src/main/java/com/example/ui/components/squad/SquadComponents.kt",
    "    val notaGeral = remember(posEnum, atributos) { CalculadoraNota.calcularNota(posEnum, atributos) }\n",
    "    val notaGeral = player.force\n"
)
replace_exact(
    "app/src/main/java/com/example/data/PlayerEvolutionMonthlyEngine.kt",
    '''                val updatedPlayer = player.copy(
                    atributosJson = newJson,
                    force = newCalculatedForce,
''',
    '''                val updatedPlayer = player.copy(
                    atributos = newAtributos,
                    atributosJson = newJson,
                    force = newCalculatedForce,
'''
)
monthly = "app/src/main/java/com/example/data/MonthlyEvolutionMaintenanceQueries.kt"
replace_exact(
    monthly,
    "        SET atributosJson = ?, force = ?, minutosJogados = 0, evolucaoMensal = ?\n",
    "        SET atributosJson = ?, atributos = ?, force = ?, minutosJogados = 0, evolucaoMensal = ?\n"
)
replace_exact(
    monthly,
    '''        if (player.atributosJson == null) statement.bindNull(1) else statement.bindString(1, player.atributosJson)
        statement.bindLong(2, player.force.toLong())
        statement.bindDouble(3, player.evolucaoMensal)
        statement.bindLong(4, player.id)
''',
    '''        if (player.atributosJson == null) statement.bindNull(1) else statement.bindString(1, player.atributosJson)
        val atributosStorage = AtributosConverter.atributosToJson(player.atributos)
        if (atributosStorage == null) statement.bindNull(2) else statement.bindString(2, atributosStorage)
        statement.bindLong(3, player.force.toLong())
        statement.bindDouble(4, player.evolucaoMensal)
        statement.bindLong(5, player.id)
'''
)

# ---------------------------------------------------------------------------
# D. MARKET: 30 is seed size; 35 is already the canonical CPU maximum.
# ---------------------------------------------------------------------------
transfer_vm = "app/src/main/java/com/example/ui/viewmodel/GameViewModelTransfers.kt"
replace_exact(
    transfer_vm,
    "repo.getPlayerCountByTeam(it.id) < 30",
    "repo.getPlayerCountByTeam(it.id) < com.example.usecase.CpuSquadManagementUseCase.MAX_SQUAD_SIZE"
)
transfer_uc = "app/src/main/java/com/example/usecase/ProcessTransfersUseCase.kt"
replace_exact(
    transfer_uc,
    '''        var buyer: Team? = null
        var attempts = 0
        for (candidate in otherTeams.shuffled()) {
            if (attempts++ >= 50) break
            if (repository.getPlayerCountByTeam(candidate.id) < 30) {
                buyer = candidate
                break
            }
        }
''',
    '''        var buyer: Team? = null
        for (candidate in otherTeams.shuffled()) {
            if (repository.getPlayerCountByTeam(candidate.id) < CpuSquadManagementUseCase.MAX_SQUAD_SIZE) {
                buyer = candidate
                break
            }
        }
'''
)

# ---------------------------------------------------------------------------
# E. UI semantics.
# ---------------------------------------------------------------------------
replace_exact(
    "app/src/main/java/com/example/ui/screens/MatchSimulationScreen.kt",
    '                    text = "PARTIDA EM ANDAMENTO",',
    '                    text = if (matchState == GameViewModel.MatchState.FINISHED) "PARTIDA ENCERRADA" else "PARTIDA EM ANDAMENTO",'
)

league_ui = "app/src/main/java/com/example/ui/state/LeagueDivisionUi.kt"
text = read(league_ui).replace("import com.example.data.LeagueHierarchy\n", "import com.example.data.LeagueHierarchy\nimport com.example.data.LeagueDivision\n", 1)
insert = '''
    data class PositionStatus(val compactLabel: String, val description: String, val positive: Boolean=false, val danger: Boolean=false)

    fun positionStatus(position: Int, teamCount: Int, division: LeagueDivision): PositionStatus {
        if (position <= 0 || teamCount <= 0) return PositionStatus(division.name, "Meio da Tabela")
        val relegationStart = if (division.relegationSpots > 0) teamCount - division.relegationSpots + 1 else Int.MAX_VALUE
        if (position >= relegationStart) return PositionStatus("▼ Z${division.relegationSpots}", "Zona de Rebaixamento", danger=true)
        if (division.promotionSpots > 0 && position <= division.promotionSpots) return PositionStatus("▲ G${division.promotionSpots}", "Zona de Acesso", positive=true)
        if (division.divisionLevel == 1 && position <= minOf(4, teamCount)) return PositionStatus("▲ G4", "Zona Continental", positive=true)
        return PositionStatus(division.name.ifBlank { "${division.divisionLevel}ª Divisão" }, "Meio da Tabela")
    }
'''
pos = text.rfind("\n}")
if pos < 0: raise RuntimeError("LeagueDivisionUi end not found")
write(league_ui, text[:pos] + insert + text[pos:])

# Dashboard classification block.
dashboard = "app/src/main/java/com/example/ui/components/dashboard/DashboardTabContent.kt"
text = read(dashboard).replace("import com.example.ui.screens.TeamBadge\n", "import com.example.ui.screens.TeamBadge\nimport com.example.ui.state.LeagueDivisionUi\n", 1)
old = '''                        val hierarchy = remember(selectedCountry) { LeagueHierarchyLoader.getHierarchyForCountry(selectedCountry) }
                        val divName = remember(hierarchy, pTeam) {
                            hierarchy.divisions.find { it.divisionLevel == pTeam.division }?.name ?: "Série A"
                        }
                        Text(
                            text = if (playerTeamPosition <= 4) "▲ G4" else divName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (playerTeamPosition <= 4) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Text(
                        text = if (playerTeamPosition <= 4) "Zona de Acesso" else "Meio da Tabela",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
'''
new = '''                        val hierarchy = remember(selectedCountry) { LeagueHierarchyLoader.getHierarchyForCountry(selectedCountry) }
                        val divisionRule = remember(hierarchy, pTeam) {
                            hierarchy.divisions.find { it.divisionLevel == pTeam.division }
                                ?: LeagueDivision("SERIE_A", "Série A", pTeam.division, 0, 0)
                        }
                        val positionStatus = remember(playerTeamPosition, leagueTeams.size, divisionRule) {
                            LeagueDivisionUi.positionStatus(playerTeamPosition, leagueTeams.size, divisionRule)
                        }
                        Text(
                            text = positionStatus.compactLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                positionStatus.danger -> Color.Red
                                positionStatus.positive -> Color(0xFF2E7D32)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    val hierarchyForStatus = remember(selectedCountry) { LeagueHierarchyLoader.getHierarchyForCountry(selectedCountry) }
                    val divisionForStatus = hierarchyForStatus.divisions.find { it.divisionLevel == pTeam.division }
                        ?: LeagueDivision("SERIE_A", "Série A", pTeam.division, 0, 0)
                    Text(
                        text = LeagueDivisionUi.positionStatus(playerTeamPosition, leagueTeams.size, divisionForStatus).description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
'''
if old not in text: raise RuntimeError("Dashboard status block not found")
write(dashboard, text.replace(old, new, 1))

coach = "app/src/main/java/com/example/ui/screens/CoachScreen.kt"
text = read(coach)
marker = "\n@Composable\nfun CoachTab(viewModel: GameViewModel) {\n"
helper = '''
internal fun footballPointsEfficiency(wins: Int, draws: Int, losses: Int): Int {
    val games = wins + draws + losses
    if (games <= 0) return 0
    return kotlin.math.round((wins * 3 + draws) * 100.0 / (games * 3.0)).toInt()
}

'''
if marker not in text: raise RuntimeError("CoachTab marker not found")
text = text.replace(marker, "\n" + helper + "@Composable\nfun CoachTab(viewModel: GameViewModel) {\n", 1)
text = text.replace("        val winRate = if (totalMatches > 0) (wins.toDouble() / totalMatches * 100).toInt() else 0\n", "        val efficiency = footballPointsEfficiency(wins, draws, losses)\n", 1)
text = text.replace('                    Text("$winRate%", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)\n', '                    Text("$efficiency%", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)\n', 1)
# Restart confirmation stays open while domain transaction runs.
text = text.replace("var showRestartConfirm by remember { mutableStateOf(false) }", "var showRestartConfirm by remember { mutableStateOf(false) }\n    var restartInProgress by remember { mutableStateOf(false) }", 1)
text = text.replace("onDismissRequest = { showRestartConfirm = false }", "onDismissRequest = { if (!restartInProgress) showRestartConfirm = false }", 1)
text = text.replace('''                        onClick = {
                            viewModel.restartCurrentSeason()
                            showRestartConfirm = false
                        },
''', '''                        onClick = {
                            restartInProgress = true
                            viewModel.restartCurrentSeason { success ->
                                restartInProgress = false
                                if (success) showRestartConfirm = false
                            }
                        },
''', 1)
text = text.replace('Text("RECOMEÇAR", fontWeight = FontWeight.Bold)', 'Text(if (restartInProgress) "REINICIANDO..." else "RECOMEÇAR", fontWeight = FontWeight.Bold)', 1)
write(coach, text)

# Technical disambiguation suffix stays persisted but is hidden in presentation.
write("app/src/main/java/com/example/ui/state/TeamPresentation.kt", '''package com.example.ui.state

import com.example.data.Team

object TeamPresentation {
    private val proceduralSuffix = Regex("""^(.+)\\s+(\\d+)$""")
    fun displayName(team: Team): String {
        val match = proceduralSuffix.matchEntire(team.name.trim()) ?: return team.name
        return if (match.groupValues[1].trim().equals(team.city.trim(), ignoreCase=true)) team.city else team.name
    }
}
''')
standings = "app/src/main/java/com/example/ui/screens/StandingsScreen.kt"
text = read(standings)
if "import com.example.ui.state.TeamPresentation" not in text:
    text = text.replace("import com.example.data.*\n", "import com.example.data.*\nimport com.example.ui.state.TeamPresentation\n", 1)
# Only text rendering, never stored names/IDs.
text = text.replace("text = team.name", "text = TeamPresentation.displayName(team)")
text = text.replace("text = homeTeam.name", "text = TeamPresentation.displayName(homeTeam)")
text = text.replace("text = awayTeam.name", "text = TeamPresentation.displayName(awayTeam)")
write(standings, text)

# ---------------------------------------------------------------------------
# Requested focused regression classes. Static checks are paired with the existing
# Room/domain tests already run by this workflow; no 20/100-season stress is started.
# ---------------------------------------------------------------------------
write("app/src/test/java/com/example/usecase/SaveReopenNoProgressRegressionTest.kt", r'''package com.example.usecase

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveReopenNoProgressRegressionTest {
    @Test fun `existing save open cannot seed regenerate fixtures or advance a week`() {
        val source = project("src/main/java/com/example/ui/viewmodel/GameViewModel.kt").readText()
        val block = source.substringAfter("fun selectSaveSlot(saveId: String)").substringBefore("fun repairRostersIfNecessary()")
        val saveRead = block.indexOf("val save = targetRepo.getGameSave()")
        val seed = block.indexOf("seedAllDefaultTeams")
        assertTrue(saveRead >= 0 && seed > saveRead)
        assertTrue(block.substring(saveRead, seed).contains("if (save == null)"))
        assertFalse(block.contains("targetRepo.saveFixtures"))
        assertFalse(block.contains("processWeekEndEconomicAndEvolution"))
    }
    @Test fun `load repair is owned by one SaveSession`() {
        val source = project("src/main/java/com/example/ui/viewmodel/GameViewModel.kt").readText()
        val init = source.substringAfter("init {").substringBefore("fun loadSaveSlots()")
        assertFalse(init.contains("DatabaseIntegrityUseCase(currentRepository)"))
        assertTrue(source.contains("CareerLoadPerformance"))
    }
    private fun project(path: String)=listOf(File(path),File("app/$path"),File("../app/$path")).first{it.exists()}
}
''')

write("app/src/test/java/com/example/usecase/SeasonSimulationMonthlyBoundaryRegressionTest.kt", r'''package com.example.usecase

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonSimulationMonthlyBoundaryRegressionTest {
    @Test fun `season simulation retries a monthly close without resimulating fixtures`() {
        val source = project("src/main/java/com/example/ui/viewmodel/GameViewModel.kt").readText()
        val block = source.substringAfter("fun startSeasonSimulation()").substringBefore("fun stopSeasonSimulation()")
        assertTrue(block.contains("var closed = processWeekEndEconomicAndEvolution()"))
        assertTrue(block.contains("Revalidando fechamento da Semana"))
        assertTrue(block.contains("closed = processWeekEndEconomicAndEvolution()"))
        assertTrue(block.contains("updatedSave.currentSeason != targetSeason"))
    }
    @Test fun `weekly close reports commit instead of silent unit return`() {
        val source = project("src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt").readText()
        assertTrue(source.contains("processWeekEndEconomicAndEvolution(): Boolean"))
        assertTrue(source.contains("return weeklyCloseCommitted"))
    }
    private fun project(path:String)=listOf(File(path),File("app/$path"),File("../app/$path")).first{it.exists()}
}
''')

write("app/src/test/java/com/example/usecase/RestartSeasonAtomicRegressionTest.kt", r'''package com.example.usecase

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartSeasonAtomicRegressionTest {
    @Test fun `restart remains repository atomic and ui waits for commit callback`() {
        val repo = project("src/main/java/com/example/data/repository.kt").readText()
        val vm = project("src/main/java/com/example/ui/viewmodel/GameViewModel.kt").readText()
        val coach = project("src/main/java/com/example/ui/screens/CoachScreen.kt").readText()
        assertTrue(repo.contains("restartSeasonStateAtomically"))
        assertTrue(repo.substringAfter("restartSeasonStateAtomically").contains("withTransaction"))
        assertTrue(vm.contains("restartCurrentSeason(onComplete: (Boolean) -> Unit = {})"))
        assertTrue(coach.contains("viewModel.restartCurrentSeason { success ->"))
        assertTrue(coach.contains("if (success) showRestartConfirm = false"))
    }
    private fun project(path:String)=listOf(File(path),File("app/$path"),File("../app/$path")).first{it.exists()}
}
''')

write("app/src/test/java/com/example/usecase/RestartSeasonEditorRosterRegressionTest.kt", r'''package com.example.usecase

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartSeasonEditorRosterRegressionTest {
    @Test fun `restart never recreates teams or players and editor waits until reset completes`() {
        val repo = project("src/main/java/com/example/data/repository.kt").readText()
        val block = repo.substringAfter("restartSeasonStateAtomically").substringBefore("suspend fun getActiveLoans")
        assertFalse(block.contains("saveTeams("))
        assertFalse(block.contains("deleteAllPlayers"))
        assertTrue(block.contains("resetAllSeasonStats"))
        val coach = project("src/main/java/com/example/ui/screens/CoachScreen.kt").readText()
        assertTrue(coach.contains("restartInProgress = true"))
    }
    private fun project(path:String)=listOf(File(path),File("app/$path"),File("../app/$path")).first{it.exists()}
}
''')

write("app/src/test/java/com/example/ui/viewmodel/EditedStrengthPersistenceRegressionTest.kt", r'''package com.example.ui.viewmodel

import com.example.data.Atributos
import com.example.data.CalculadoraNota
import com.example.data.Player
import com.example.data.Posicao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditedStrengthPersistenceRegressionTest {
    @Test fun `edited 99 has coherent force embedded attributes and json`() {
        val original = Player(id=1,teamId=1,name="Gabriel Rocha",age=25,position="MEI",force=78,atributos=Atributos(passe=76,visaoJogo=78,criatividade=77,velocidade=75))
        val edited = original.withEditedOverall(99)
        assertEquals(99, edited.force)
        assertEquals(edited.atributos, edited.getAtributosObject())
        assertEquals(99, CalculadoraNota.calcularNota(Posicao.fromCode(edited.position), edited.getAtributosObject()))
        assertTrue(edited.atributosJson?.isNotBlank() == true)
    }
    @Test fun `player detail reads canonical persisted force`() {
        val source = java.io.File("src/main/java/com/example/ui/components/squad/SquadComponents.kt").takeIf{it.exists()} ?: java.io.File("app/src/main/java/com/example/ui/components/squad/SquadComponents.kt")
        assertTrue(source.readText().contains("val notaGeral = player.force"))
    }
}
''')

write("app/src/test/java/com/example/usecase/PlayerSaleBuyerCapacityRegressionTest.kt", r'''package com.example.usecase

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSaleBuyerCapacityRegressionTest {
    @Test fun `cash and installment buyer discovery use canonical cpu max thirty five`() {
        val vm = project("src/main/java/com/example/ui/viewmodel/GameViewModelTransfers.kt").readText()
        val uc = project("src/main/java/com/example/usecase/ProcessTransfersUseCase.kt").readText()
        assertFalse(vm.contains("getPlayerCountByTeam(it.id) < 30"))
        assertFalse(uc.contains("getPlayerCountByTeam(candidate.id) < 30"))
        assertTrue(vm.contains("CpuSquadManagementUseCase.MAX_SQUAD_SIZE"))
        assertTrue(uc.contains("CpuSquadManagementUseCase.MAX_SQUAD_SIZE"))
        assertFalse(uc.contains("attempts++ >= 50"))
    }
    private fun project(path:String)=listOf(File(path),File("app/$path"),File("../app/$path")).first{it.exists()}
}
''')

write("app/src/test/java/com/example/ui/state/LeaguePositionLabelRegressionTest.kt", r'''package com.example.ui.state

import com.example.data.LeagueDivision
import com.example.data.Team
import org.junit.Assert.assertEquals
import org.junit.Test

class LeaguePositionLabelRegressionTest {
    @Test fun `serie a first is continental and seventeenth is relegation`() {
        val d=LeagueDivision("SERIE_A","Série A",1,0,4)
        assertEquals("Zona Continental",LeagueDivisionUi.positionStatus(1,20,d).description)
        assertEquals("Zona de Rebaixamento",LeagueDivisionUi.positionStatus(17,20,d).description)
    }
    @Test fun `access only exists where promotion spots exist`() {
        val d=LeagueDivision("SERIE_B","Série B",2,4,4)
        assertEquals("Zona de Acesso",LeagueDivisionUi.positionStatus(4,20,d).description)
        assertEquals("Meio da Tabela",LeagueDivisionUi.positionStatus(10,20,d).description)
    }
    @Test fun `procedural Quilmes numeric suffix is hidden only in presentation`() {
        val t=Team(id=1,name="Quilmes 1",city="Quilmes",state="AR",country="Argentina",division=2)
        assertEquals("Quilmes",TeamPresentation.displayName(t))
        assertEquals("Quilmes 1",t.name)
    }
}
''')

write("app/src/test/java/com/example/ui/screens/CareerEfficiencyRegressionTest.kt", r'''package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class CareerEfficiencyRegressionTest {
    @Test fun `four wins two draws five losses equals forty two percent points efficiency`() {
        assertEquals(42,footballPointsEfficiency(4,2,5))
    }
    @Test fun `zero games equals zero`() { assertEquals(0,footballPointsEfficiency(0,0,0)) }
}
''')

# Source invariants before Gradle.
assert "targetRepo.saveFixtures" not in read(vm).split("fun selectSaveSlot(saveId: String)",1)[1].split("fun repairRostersIfNecessary()",1)[0]
assert "getPlayerCountByTeam(it.id) < 30" not in read(transfer_vm)
assert "getPlayerCountByTeam(candidate.id) < 30" not in read(transfer_uc)
assert "atributos = newAtributos" in read("app/src/main/java/com/example/data/PlayerEvolutionMonthlyEngine.kt")
assert "SET atributosJson = ?, atributos = ?, force = ?" in read(monthly)

# Stage every tested source/test file so the existing workflow's later explicit git-add list
# cannot accidentally omit this round's fixes.
subprocess.run(["git", "add", "app/src/main/java", "app/src/test/java"], cwd=ROOT, check=True)

# Mandatory focused suite for this manual-test round. Existing workflow runs preserved live-match,
# completed-fixture, crest and statistics regressions immediately afterward.
tests = [
    "com.example.usecase.SaveReopenNoProgressRegressionTest",
    "com.example.usecase.SeasonSimulationMonthlyBoundaryRegressionTest",
    "com.example.usecase.RestartSeasonAtomicRegressionTest",
    "com.example.usecase.RestartSeasonEditorRosterRegressionTest",
    "com.example.ui.viewmodel.EditedStrengthPersistenceRegressionTest",
    "com.example.usecase.PlayerSaleBuyerCapacityRegressionTest",
    "com.example.ui.state.LeaguePositionLabelRegressionTest",
    "com.example.ui.screens.CareerEfficiencyRegressionTest",
]
cmd = ["./gradlew", "testDebugUnitTest", "-PexcludeStressTests=true"]
for test in tests:
    cmd += ["--tests", test]
cmd += ["--stacktrace"]
subprocess.run(cmd, cwd=ROOT, check=True)
print("round_20260831 integrity/simulation/market/UI patch applied and focused regressions passed")
