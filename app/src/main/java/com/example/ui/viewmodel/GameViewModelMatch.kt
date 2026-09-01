package com.example.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.usecase.DatabaseIntegrityUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private class StaleWeeklyMonthlyEvolutionRollback : RuntimeException()

internal fun collectAppearancePlayerIds(
    homeStarters: List<Player>,
    awayStarters: List<Player>,
    events: List<GameEngine.MatchEventDetail>
): Set<Long> = buildSet {
    addAll(homeStarters.map { it.id })
    addAll(awayStarters.map { it.id })
    addAll(
        events.asSequence()
            .filter { it.type == "SUBSTITUTION" }
            .mapNotNull { it.playerId }
            .toList()
    )
}

internal fun normalizeUnattributableGoalEvents(
    events: List<GameEngine.MatchEventDetail>,
    homePersistedPlayers: List<Player>,
    awayPersistedPlayers: List<Player>
): List<GameEngine.MatchEventDetail> {
    val homeHasEligiblePersistedPlayer = homePersistedPlayers.any {
        it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0
    }
    val awayHasEligiblePersistedPlayer = awayPersistedPlayers.any {
        it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0
    }
    if (homeHasEligiblePersistedPlayer && awayHasEligiblePersistedPlayer) return events

    return events.filterNot { event ->
        event.type == "GOAL" &&
            ((event.isHomeEvent && !homeHasEligiblePersistedPlayer) ||
                (!event.isHomeEvent && !awayHasEligiblePersistedPlayer))
    }
}

internal fun hasPendingUserFixtures(
    fixtures: List<Fixture>,
    userTeamId: Long
): Boolean = fixtures.any { fixture ->
    !fixture.isPlayed &&
        (fixture.homeTeamId == userTeamId || fixture.awayTeamId == userTeamId)
}

fun GameViewModel.startLiveMatch(fixture: Fixture) {
    liveMatchJob?.cancel()
    liveMatchJob = viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val home = repo.getTeam(fixture.homeTeamId) ?: GlobalFootballSystem.getVirtualTeam(fixture.homeTeamId)
        val away = repo.getTeam(fixture.awayTeamId) ?: GlobalFootballSystem.getVirtualTeam(fixture.awayTeamId)

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

        liveMatchFixture = fixture
        liveMatchHomeTeam = home
        liveMatchAwayTeam = away
        liveMatchHomePlayers = homeStarters
        liveMatchAwayPlayers = awayStarters

        val isRivalry = (home.rivalTeamId == away.id || away.rivalTeamId == home.id || (home.state == away.state && home.city == away.city))

        val matchEventsList = GameEngine.simulateMatchDetailed(
            homeTeam = home,
            awayTeam = away,
            homePlayers = homeStarters,
            awayPlayers = awayStarters,
            homeTactics = if (home.isPlayerControlled) playerFormation.value else "4-4-2",
            homeStyle = if (home.isPlayerControlled) playerStyle.value else "Equilibrado",
            awayTactics = if (away.isPlayerControlled) playerFormation.value else "4-4-2",
            awayStyle = if (away.isPlayerControlled) playerStyle.value else "Equilibrado",
            isRivalry = isRivalry,
            randomSeed = kotlin.random.Random.nextLong(),
            homeReserves = homeReserves,
            awayReserves = awayReserves
        )

        // `getStartingXIForTeam()` fornece atletas procedurais quando não existe nenhum atleta
        // persistido elegível. Esses ids não podem alimentar placar/estatísticas oficiais porque
        // não há Player correspondente no Room para receber o gol. A regra cobre tanto clube
        // virtual sem roster quanto roster real inteiro lesionado/suspenso.
        currentMatchEvents = normalizeUnattributableGoalEvents(matchEventsList, homePls, awayPls)

        _matchMinute.value = 0
        _matchHomeScore.value = 0
        _matchAwayScore.value = 0
        _matchEvents.value = emptyList()
        _matchState.value = GameViewModel.MatchState.PLAYING

        runMatchSimulationLoop()
    }
}

suspend fun GameViewModel.runMatchSimulationLoop() {
    var m = _matchMinute.value
    while (m < 90 && _matchState.value == GameViewModel.MatchState.PLAYING) {
        m++
        _matchMinute.value = m
        val eventsUpToM = currentMatchEvents.filter { it.minute <= m }
        _matchEvents.value = eventsUpToM
        _matchHomeScore.value = eventsUpToM.count { it.type == "GOAL" && it.isHomeEvent }
        _matchAwayScore.value = eventsUpToM.count { it.type == "GOAL" && !it.isHomeEvent }

        var remainingMs = getDelayForSpeed(liveMatchSpeed.value)
        while (remainingMs > 0 && _matchState.value == GameViewModel.MatchState.PLAYING) {
            val step = 50L.coerceAtMost(remainingMs)
            delay(step)
            val currentTargetDelay = getDelayForSpeed(liveMatchSpeed.value)
            if (currentTargetDelay < remainingMs) {
                remainingMs = currentTargetDelay
            } else {
                remainingMs -= step
            }
        }
    }

    if (m >= 90 && _matchState.value == GameViewModel.MatchState.PLAYING) {
        val fix = liveMatchFixture
        if (fix != null) {
            val homeScore = _matchHomeScore.value
            val awayScore = _matchAwayScore.value
            val (homePenalties, awayPenalties) = CompetitionRules.resolvePenaltiesIfNeeded(
                fixture = fix,
                homeScore = homeScore,
                awayScore = awayScore
            )
            val updatedFixture = fix.copy(
                homeScore = homeScore,
                awayScore = awayScore,
                homePenalties = homePenalties,
                awayPenalties = awayPenalties,
                isPlayed = true
            )
            repo.withTransaction {
                val persistedFixture = repo.getFixture(updatedFixture.id)
                if (persistedFixture?.isPlayed != true) {
                    repo.updateFixture(updatedFixture)
                    processMatchEventsAndStats(updatedFixture, currentMatchEvents)
                }
            }
        }
        // Complete the week lifecycle before exposing the post-match exit. This removes the
        // race where the button was clickable while CPU fixtures/evolution still owned the weekly
        // lifecycle and also guarantees that standings are already authoritative on return.
        val saveAfterMatch = repo.getGameSave()
        if (saveAfterMatch != null) {
            simulateCpuMatchesForCurrentWeek()
            val refreshedWeekFixtures = repo.getFixturesForWeek(
                saveAfterMatch.currentSeason,
                saveAfterMatch.currentWeek
            )
            if (!hasPendingUserFixtures(refreshedWeekFixtures, saveAfterMatch.playerTeamId)) {
                processWeekEndEconomicAndEvolution()
            }
        }
        _matchState.value = GameViewModel.MatchState.FINISHED
    }
}

fun GameViewModel.substitutePlayer(playerOut: Player, playerIn: Player) {
    viewModelScope.launch(Dispatchers.IO) {
        val currentMinute = _matchMinute.value
        val isHome = liveMatchHomeTeam?.isPlayerControlled == true
        if (isHome) {
            val list = liveMatchHomePlayers.toMutableList()
            val idx = list.indexOfFirst { it.id == playerOut.id }
            if (idx != -1) {
                list[idx] = playerIn
                liveMatchHomePlayers = list
                val newEvent = GameEngine.MatchEventDetail(
                    minute = currentMinute,
                    type = "SUBSTITUTION",
                    teamId = liveMatchHomeTeam?.id ?: 0L,
                    description = "Substituição: Sai ${playerOut.name}, Entra ${playerIn.name}",
                    isHomeEvent = true,
                    playerId = playerIn.id
                )
                currentMatchEvents = (currentMatchEvents + newEvent).sortedBy { it.minute }
                _matchEvents.value = (_matchEvents.value + newEvent).sortedBy { it.minute }
                liveTacticalFeedback.value = "Substituição realizada: ${playerIn.name} em campo!"
            }
        }
    }
}

fun GameViewModel.pauseLiveMatch() {
    _matchState.value = GameViewModel.MatchState.PAUSED
    liveMatchJob?.cancel()
}

fun GameViewModel.resumeLiveMatch() {
    if (_matchState.value == GameViewModel.MatchState.PAUSED) {
        _matchState.value = GameViewModel.MatchState.PLAYING
        liveMatchJob?.cancel()
        liveMatchJob = viewModelScope.launch(Dispatchers.IO) {
            runMatchSimulationLoop()
        }
    }
}

private suspend fun GameViewModel.simulateSingleUserFixtureSafely(
    userFixture: Fixture,
    save: GameSave
): Fixture {
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
    val rawEvents = GameEngine.simulateMatchDetailed(
        homeTeam = home,
        awayTeam = away,
        homePlayers = homeStarters,
        awayPlayers = awayStarters,
        homeTactics = if (home.isPlayerControlled) playerFormation.value else "4-4-2",
        homeStyle = if (home.isPlayerControlled) playerStyle.value else "Equilibrado",
        awayTactics = if (away.isPlayerControlled) playerFormation.value else "4-4-2",
        awayStyle = if (away.isPlayerControlled) playerStyle.value else "Equilibrado",
        isRivalry = isRivalry,
        randomSeed = kotlin.random.Random.nextLong(),
        homeReserves = homeReserves,
        awayReserves = awayReserves
    )
    val matchEvents = normalizeUnattributableGoalEvents(rawEvents, homePls, awayPls)
    val hGoals = matchEvents.count { it.type == "GOAL" && it.isHomeEvent }
    val aGoals = matchEvents.count { it.type == "GOAL" && !it.isHomeEvent }
    val updatedFixture = userFixture.copy(homeScore = hGoals, awayScore = aGoals, isPlayed = true)

    var committedFixture = updatedFixture
    repo.withTransaction {
        val persistedFixture = repo.getFixture(updatedFixture.id)
        if (persistedFixture?.isPlayed == true) {
            committedFixture = persistedFixture
            return@withTransaction
        }
        repo.updateFixture(updatedFixture)
        processMatchEventsAndStats(updatedFixture, matchEvents)
        committedFixture = repo.getFixture(updatedFixture.id) ?: updatedFixture
    }
    return committedFixture
}

/**
 * Finaliza a partida AO VIVO usando exatamente a sequência já preparada para ela.
 *
 * O botão Pular não é uma nova simulação: ele apenas revela os eventos restantes, deriva o placar
 * desses mesmos gols e persiste o fixture/estatísticas uma única vez. Isso mantém placar, feed de
 * eventos e estatísticas sob a mesma fonte de verdade, inclusive quando o usuário pula no meio do
 * jogo ou quando a partida está pausada.
 */
private suspend fun GameViewModel.finishPreparedLiveFixture(targetFixture: Fixture): Fixture {
    val preparedEvents = currentMatchEvents.toList().sortedBy { it.minute }
    val homeScore = preparedEvents.count { it.type == "GOAL" && it.isHomeEvent }
    val awayScore = preparedEvents.count { it.type == "GOAL" && !it.isHomeEvent }
    val (homePenalties, awayPenalties) = CompetitionRules.resolvePenaltiesIfNeeded(
        fixture = targetFixture,
        homeScore = homeScore,
        awayScore = awayScore
    )
    val finishedFixture = targetFixture.copy(
        homeScore = homeScore,
        awayScore = awayScore,
        homePenalties = homePenalties,
        awayPenalties = awayPenalties,
        isPlayed = true
    )

    var committedFixture = finishedFixture
    repo.withTransaction {
        val persistedFixture = repo.getFixture(finishedFixture.id)
        if (persistedFixture?.isPlayed == true) {
            committedFixture = persistedFixture
            return@withTransaction
        }
        repo.updateFixture(finishedFixture)
        processMatchEventsAndStats(finishedFixture, preparedEvents)
        committedFixture = repo.getFixture(finishedFixture.id) ?: finishedFixture
    }

    _matchEvents.value = preparedEvents
    _matchHomeScore.value = committedFixture.homeScore ?: homeScore
    _matchAwayScore.value = committedFixture.awayScore ?: awayScore
    _matchMinute.value = 90
    // The caller still owns the weekly lifecycle. FINISHED is published only after CPU fixtures
    // and the durable weekly close have completed.
    return committedFixture
}

fun GameViewModel.skipLiveMatch(fixture: Fixture? = null) {
    liveMatchJob?.cancel()
    liveMatchJob = viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val targetFixture = fixture ?: liveMatchFixture ?: run {
            val weekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek)
            weekFixtures.find { !it.isPlayed && (it.homeTeamId == save.playerTeamId || it.awayTeamId == save.playerTeamId) }
        }

        if (targetFixture != null && !targetFixture.isPlayed) {
            val isPreparedLiveFixture = liveMatchFixture?.id == targetFixture.id &&
                _matchState.value != GameViewModel.MatchState.IDLE
            val finished = if (isPreparedLiveFixture) {
                finishPreparedLiveFixture(targetFixture)
            } else {
                simulateSingleUserFixtureSafely(targetFixture, save)
            }

            var updated = repo.getFixture(targetFixture.id) ?: finished
            val decided = CompetitionRules.ensureKnockoutDecision(updated)
            if (decided != updated) {
                repo.updateFixture(decided)
                updated = repo.getFixture(decided.id) ?: decided
            }
            _matchHomeScore.value = updated.homeScore ?: 0
            _matchAwayScore.value = updated.awayScore ?: 0
            _matchMinute.value = 90
        }

        // FINISHED is the durable boundary: CPU fixtures and the weekly close are complete first.
        simulateCpuMatchesForCurrentWeek()
        val refreshedWeekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek)
        if (!hasPendingUserFixtures(refreshedWeekFixtures, save.playerTeamId)) {
            processWeekEndEconomicAndEvolution()
        }
        _matchState.value = GameViewModel.MatchState.FINISHED
    }
}

suspend fun GameViewModel.processMatchEventsAndStats(fixture: Fixture, events: List<Any>) {
    @Suppress("UNCHECKED_CAST")
    val detailEvents = events as? List<GameEngine.MatchEventDetail> ?: return

    val home = repo.getTeam(fixture.homeTeamId) ?: GlobalFootballSystem.getVirtualTeam(fixture.homeTeamId)
    val away = repo.getTeam(fixture.awayTeamId) ?: GlobalFootballSystem.getVirtualTeam(fixture.awayTeamId)
    val homePlayers = repo.getPlayersByTeam(fixture.homeTeamId)
    val awayPlayers = repo.getPlayersByTeam(fixture.awayTeamId)
    val homeStarters = getStartingXIForTeam(homePlayers, home.id, home.rating, home.name, home.country)
    val awayStarters = getStartingXIForTeam(awayPlayers, away.id, away.rating, away.name, away.country)

    val appearancePlayerIds = collectAppearancePlayerIds(homeStarters, awayStarters, detailEvents)
    val eventPlayerIds = detailEvents.flatMap { event ->
        listOfNotNull(event.playerId, event.scorerId)
    }
    val playerIds = (appearancePlayerIds + eventPlayerIds).distinct()
    if (playerIds.isEmpty()) return

    val playersToUpdate = mutableListOf<Player>()
    for (pid in playerIds) {
        val p = repo.getPlayer(pid) ?: continue
        var careerGoals = p.careerGoals
        var seasonGoals = p.gols
        var yellow = p.yellowCardsAccumulated
        var isSuspended = p.suspensionWeeksRemaining
        var injury = p.injuryWeeksRemaining

        val pEvents = detailEvents.filter { it.playerId == pid || it.scorerId == pid }
        for (ev in pEvents) {
            when (ev.type) {
                "GOAL" -> if (ev.scorerId == pid) {
                    careerGoals += 1
                    seasonGoals += 1
                }
                "CARD_YELLOW" -> {
                    yellow += 1
                    if (yellow >= 3) {
                        yellow = 0
                        isSuspended = 1
                    }
                }
                "CARD_RED" -> {
                    isSuspended = 1
                }
                "INJURY" -> {
                    injury = (1..4).random()
                }
            }
        }
        val appeared = pid in appearancePlayerIds
        playersToUpdate.add(
            p.copy(
                careerGoals = careerGoals,
                gols = seasonGoals,
                careerApps = p.careerApps + if (appeared) 1 else 0,
                partidasDisputadas = p.partidasDisputadas + if (appeared) 1 else 0,
                yellowCardsAccumulated = yellow,
                suspensionWeeksRemaining = isSuspended,
                injuryWeeksRemaining = injury
            )
        )
    }
    if (playersToUpdate.isNotEmpty()) {
        repo.updatePlayers(playersToUpdate)
    }
}

suspend fun GameViewModel.simulateCpuMatchesForCurrentWeek() {
    val save = repo.getGameSave() ?: return
    simulateWeekUseCase.simulateCpuMatchesForWeek(
        season = save.currentSeason,
        week = save.currentWeek,
        excludedTeamId = save.playerTeamId
    )
}

private suspend fun GameViewModel.prepareWeeklyIncomingOffer(): IncomingOffer? {
    val save = repo.getGameSave() ?: return null
    val userRoster = repo.getPlayersByTeam(save.playerTeamId)
    if (userRoster.size <= 16) return null
    val ownedCandidates = userRoster.filterNot { it.isOnLoan }
    if (ownedCandidates.isEmpty()) return null
    val otherTeams = repo.getAllTeams().filter { it.id != save.playerTeamId }
    if (otherTeams.isEmpty()) return null

    val seed = (save.currentSeason * 1000L + save.currentWeek * 10L + save.playerTeamId)
    val rand = kotlin.random.Random(seed)
    if (rand.nextDouble() >= 0.4) return null

    val candidatePlayer = ownedCandidates.shuffled(rand).firstOrNull() ?: return null
    val buyerTeam = otherTeams.shuffled(rand).firstOrNull() ?: return null
    val baseVal = candidatePlayer.calculateMarketValue()
    val variation = 0.85 + (rand.nextDouble() * 0.3)
    val price = (baseVal * variation).toLong().coerceAtLeast(50_000L)
    val offerType = if (rand.nextDouble() < 0.25) "EMPRESTIMO" else "COMPRA"
    val loanWeeks = if (offerType == "EMPRESTIMO") 26 else 0

    return IncomingOffer(
        id = com.example.util.TransactionIdGenerator.generateUniqueId(),
        player = candidatePlayer,
        buyerTeamName = buyerTeam.name,
        buyerTeamId = buyerTeam.id,
        offerType = offerType,
        price = price,
        durationWeeks = loanWeeks
    )
}

private fun GameViewModel.publishIncomingOffer(newOffer: IncomingOffer) {
    _incomingOffers.update { current ->
        val filtered = current.filterNot { it.player.id == newOffer.player.id }
        (filtered + newOffer).takeLast(5)
    }
}

suspend fun GameViewModel.generateWeeklyIncomingOffers() {
    prepareWeeklyIncomingOffer()?.let { publishIncomingOffer(it) }
}

/**
 * Finaliza uma semana como uma única unidade de persistência.
 *
 * Em semanas mensais, a parte CPU-heavy da evolução é preparada antes de adquirir a transação de
 * escrita. A transação continua sendo a única unidade de commit de finanças, contratos, evolução,
 * copas e avanço de calendário. O commit mensal faz uma projeção leve do universo já após a
 * manutenção semanal: mudanças legítimas de clube e novos jogadores são corrigidos apenas no
 * subconjunto afetado, enquanto qualquer alteração real dos inputs esportivos falha fechada.
 * Assim, nenhum caminho normal volta a calcular os ~60 mil jogadores com a transação Room aberta.
 *
 * Ofertas recebidas são apenas preparadas durante a transação e publicadas no StateFlow depois que
 * todo o fechamento semanal confirma o commit. Um rollback não pode deixar UI state de uma semana
 * que nunca foi persistida.
 *
 * Quando um input esportivo muda durante a preparação, uma exceção privada é usada apenas como
 * sinal interno de rollback da transação Room e é capturada imediatamente fora dela. O conflito
 * esperado nunca escapa para viewModelScope: a semana permanece intacta e pode ser tentada de novo.
 * Falhas inesperadas continuam propagando normalmente.
 */
suspend fun GameViewModel.processWeekEndEconomicAndEvolution() {
    val requestedSave = repo.getGameSave() ?: return
    val monthlyPeriod = if (requestedSave.currentWeek % 4 == 0) {
        "S${requestedSave.currentSeason}_W${requestedSave.currentWeek}"
    } else {
        null
    }
    val preparedMonthlyPlan = monthlyPeriod?.let { period ->
        playerEvolutionUseCase.prepareMonthlyEvolution(requestedSave, period)
    }
    var stagedIncomingOffer: IncomingOffer? = null
    var weeklyCloseCommitted = false

    try {
        repo.withTransaction {
            val save = repo.getGameSave() ?: return@withTransaction
            if (save.currentSeason != requestedSave.currentSeason ||
                save.currentWeek != requestedSave.currentWeek ||
                save.playerTeamId != requestedSave.playerTeamId
            ) {
                return@withTransaction
            }

            val currentWeekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek)
            if (currentWeekFixtures.any { !it.isPlayed }) {
                return@withTransaction
            }

            val isHomeMatch = currentWeekFixtures.any {
                it.isPlayed && it.homeTeamId == save.playerTeamId
            }

            // Snapshot loans use the main contract as their only trusted expiry signal. CPU owners
            // must make their normal sporting retention decision while that ownership relation is
            // still active, before FinanceUseCase closes a one-week contract as free agency.
            val cpuSquadManagement = com.example.usecase.CpuSquadManagementUseCase(repo)
            cpuSquadManagement.renewCpuContractsBeforeWeeklyTick()

            val userPlayers = repo.getPlayersByTeam(save.playerTeamId)
            val updatedSave = financeUseCase.processWeeklyFinances(save, isHomeMatch, userPlayers)

            processTransfersUseCase.processWeeklyContractsAndLoans()
            cpuSquadManagement.processWeeklyAfterContracts()

            stagedIncomingOffer = prepareWeeklyIncomingOffer()

            if (monthlyPeriod != null) {
                val committedPreparedPlan = preparedMonthlyPlan?.let { plan ->
                    playerEvolutionUseCase.commitMonthlyEvolution(
                        plan = plan,
                        allowWeeklyRosterCorrections = true
                    )
                } == true
                if (!committedPreparedPlan) {
                    throw StaleWeeklyMonthlyEvolutionRollback()
                }
            }

            CupCompetitionSystem.processProgression(save.currentSeason, save.currentWeek, repo)
            SuperMundialSystem.processProgression(save.currentSeason, save.currentWeek, repo)

            if (updatedSave.currentWeek >= GameCalendar.WEEKS_PER_SEASON) {
                advanceToNextSeason(updatedSave)
            } else {
                val nextWeekSave = updatedSave.copy(currentWeek = updatedSave.currentWeek + 1)
                repo.saveGameSave(nextWeekSave)
            }
            weeklyCloseCommitted = true
        }
    } catch (_: StaleWeeklyMonthlyEvolutionRollback) {
        _toastMessage.emit(
            "O estado de treino mudou durante o fechamento semanal. A semana não foi avançada; tente novamente."
        )
        return
    }

    if (weeklyCloseCommitted) {
        stagedIncomingOffer?.let { publishIncomingOffer(it) }
    }
}

data class SeasonStandingRow(
    val teamName: String,
    var pts: Int = 0,
    var gp: Int = 0,
    var w: Int = 0,
    var d: Int = 0,
    var l: Int = 0,
    var gf: Int = 0,
    var ga: Int = 0
) {
    val gd: Int get() = gf - ga
}

suspend fun GameViewModel.advanceToNextSeason(save: GameSave) {
    val transitionUseCase = com.example.usecase.SeasonTransitionUseCase(
        repository = repo,
        generateCalendarUseCase = generateCalendarUseCase,
        databaseIntegrityUseCase = com.example.usecase.DatabaseIntegrityUseCase(repo)
    )
    transitionUseCase.advanceToNextSeason(save)
}

private fun calculateSeasonStandings(teams: List<Team>, fixtures: List<Fixture>, compType: String): List<Pair<Team, SeasonStandingRow>> {
    val map = teams.associateWith { SeasonStandingRow(it.name) }.toMutableMap()
    val altCompType = when (compType) {
        "SERIE_A" -> "DIV_1"
        "SERIE_B" -> "DIV_2"
        "SERIE_C" -> "DIV_3"
        "SERIE_D" -> "DIV_4"
        "DIV_1" -> "SERIE_A"
        "DIV_2" -> "SERIE_B"
        "DIV_3" -> "SERIE_C"
        "DIV_4" -> "SERIE_D"
        else -> compType
    }
    val relevantFixtures = fixtures.filter { (it.competitionType == compType || it.competitionType == altCompType) && it.isPlayed }

    for (f in relevantFixtures) {
        val homeT = teams.find { it.id == f.homeTeamId }
        val awayT = teams.find { it.id == f.awayTeamId }
        val hG = f.homeScore ?: 0
        val aG = f.awayScore ?: 0

        if (homeT != null && awayT != null) {
            val hRow = map[homeT] ?: continue
            val aRow = map[awayT] ?: continue
            hRow.gf += hG
            hRow.ga += aG
            aRow.gf += aG
            aRow.ga += hG

            hRow.gp += 1
            aRow.gp += 1

            when {
                hG > aG -> {
                    hRow.pts += 3
                    hRow.w += 1
                    aRow.l += 1
                }
                aG > hG -> {
                    aRow.pts += 3
                    aRow.w += 1
                    hRow.l += 1
                }
                else -> {
                    hRow.pts += 1
                    aRow.pts += 1
                    hRow.d += 1
                    aRow.d += 1
                }
            }
        }
    }

    return map.entries.map { Pair(it.key, it.value) }.sortedWith(
        compareByDescending<Pair<Team, SeasonStandingRow>> { it.second.pts }
            .thenByDescending { it.second.w }
            .thenByDescending { it.second.gd }
            .thenByDescending { it.second.gf }
    )
}

suspend fun GameViewModel.generateFixturesForSeason(season: Int, teams: List<Team>, userTeamId: Long): List<Fixture> {
    val userCountry = _selectedCountry.value ?: "BRASIL"
    val qualificationStandings = if (season > 2026) {
        repo.getGlobalStandingsForSeason(season - 1)
    } else {
        emptyList()
    }
    return generateCalendarUseCase.generateSeasonFixtures(
        season = season,
        teams = teams,
        userTeamId = userTeamId,
        userCountry = userCountry,
        qualificationStandings = qualificationStandings
    )
}
