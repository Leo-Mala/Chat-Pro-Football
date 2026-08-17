package com.example.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.usecase.DatabaseIntegrityUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

        currentMatchEvents = matchEventsList

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
        _matchState.value = GameViewModel.MatchState.FINISHED
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
            repo.updateFixture(updatedFixture)
            processMatchEventsAndStats(updatedFixture, currentMatchEvents)
        }
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

fun GameViewModel.skipLiveMatch(fixture: Fixture? = null) {
    liveMatchJob?.cancel()
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val targetFixture = fixture ?: liveMatchFixture ?: run {
            val weekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek)
            weekFixtures.find { !it.isPlayed && (it.homeTeamId == save.playerTeamId || it.awayTeamId == save.playerTeamId) }
        }

        if (targetFixture != null && !targetFixture.isPlayed) {
            var updated = simulateSingleUserFixture(targetFixture, save)
            val decided = CompetitionRules.ensureKnockoutDecision(updated)
            if (decided != updated) {
                repo.updateFixture(decided)
                updated = decided
            }
            _matchHomeScore.value = updated.homeScore ?: 0
            _matchAwayScore.value = updated.awayScore ?: 0
            _matchMinute.value = 90
            _matchState.value = GameViewModel.MatchState.FINISHED
        } else {
            _matchState.value = GameViewModel.MatchState.FINISHED
        }

        simulateCpuMatchesForCurrentWeek()

        val refreshedWeekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek)
        if (!hasPendingUserFixtures(refreshedWeekFixtures, save.playerTeamId)) {
            processWeekEndEconomicAndEvolution()
        }
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
        var goals = p.careerGoals
        var yellow = p.yellowCardsAccumulated
        var isSuspended = p.suspensionWeeksRemaining
        var injury = p.injuryWeeksRemaining

        val pEvents = detailEvents.filter { it.playerId == pid || it.scorerId == pid }
        for (ev in pEvents) {
            when (ev.type) {
                "GOAL" -> if (ev.scorerId == pid) goals += 1
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
        playersToUpdate.add(
            p.copy(
                careerGoals = goals,
                careerApps = p.careerApps + if (pid in appearancePlayerIds) 1 else 0,
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

suspend fun GameViewModel.generateWeeklyIncomingOffers() {
    val save = repo.getGameSave() ?: return
    val userPlayers = repo.getPlayersByTeam(save.playerTeamId)
    if (userPlayers.size <= 16) return
    val otherTeams = repo.getAllTeams().filter { it.id != save.playerTeamId }
    if (otherTeams.isEmpty()) return

    val seed = (save.currentSeason * 1000L + save.currentWeek * 10L + save.playerTeamId)
    val rand = kotlin.random.Random(seed)

    if (rand.nextDouble() < 0.4) {
        val candidatePlayer = userPlayers.shuffled(rand).firstOrNull() ?: return
        val buyerTeam = otherTeams.shuffled(rand).firstOrNull() ?: return
        val baseVal = candidatePlayer.calculateMarketValue()
        val variation = 0.85 + (rand.nextDouble() * 0.3)
        val price = (baseVal * variation).toLong().coerceAtLeast(50_000L)
        val offerType = if (rand.nextDouble() < 0.25) "EMPRESTIMO" else "COMPRA"
        val loanWeeks = if (offerType == "EMPRESTIMO") 26 else 0

        val newOffer = IncomingOffer(
            id = com.example.util.TransactionIdGenerator.generateUniqueId(),
            player = candidatePlayer,
            buyerTeamName = buyerTeam.name,
            buyerTeamId = buyerTeam.id,
            offerType = offerType,
            price = price,
            durationWeeks = loanWeeks
        )

        _incomingOffers.update { current ->
            val filtered = current.filterNot { it.player.id == candidatePlayer.id }
            (filtered + newOffer).takeLast(5)
        }
    }
}

suspend fun GameViewModel.processWeekEndEconomicAndEvolution() {
    val save = repo.getGameSave() ?: return

    val currentWeekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek)
    val isHomeMatch = currentWeekFixtures.any {
        it.isPlayed && it.homeTeamId == save.playerTeamId
    }

    val userPlayers = repo.getPlayersByTeam(save.playerTeamId)
    val updatedSave = financeUseCase.processWeeklyFinances(save, isHomeMatch, userPlayers)

    // A CPU decide renovações imediatamente antes do único tick semanal de contratos.
    val cpuSquadManagement = com.example.usecase.CpuSquadManagementUseCase(repo)
    cpuSquadManagement.renewCpuContractsBeforeWeeklyTick()
    processTransfersUseCase.processWeeklyContractsAndLoans()
    cpuSquadManagement.processWeeklyAfterContracts()

    // Generate incoming transfer offers for user players
    generateWeeklyIncomingOffers()

    // Execute monthly evolution every 4 weeks, including the canonical final week (48).
    if (save.currentWeek % 4 == 0) {
        playerEvolutionUseCase.executeMonthlyEvolution(save, "S${save.currentSeason}_W${save.currentWeek}")
    }

    // Progress domestic/continental cups only after every match of the week is complete.
    CupCompetitionSystem.processProgression(save.currentSeason, save.currentWeek, repo)

    // Progress Super Mundial de Clubes knockouts / champion recording.
    SuperMundialSystem.processProgression(save.currentSeason, save.currentWeek, repo)

    if (updatedSave.currentWeek >= GameCalendar.WEEKS_PER_SEASON) {
        advanceToNextSeason(updatedSave)
    } else {
        val nextWeekSave = updatedSave.copy(currentWeek = updatedSave.currentWeek + 1)
        repo.saveGameSave(nextWeekSave)
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
