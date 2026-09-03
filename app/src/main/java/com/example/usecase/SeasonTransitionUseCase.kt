package com.example.usecase

import com.example.data.*
import kotlin.random.Random

/**
 * UseCase responsável pela transição completa e atômica de temporada.
 * Processa promoção/rebaixamento, envelhecimento de atletas, classificação global,
 * purga de dados antigos e geração do novo calendário de jogos.
 */
class SeasonTransitionUseCase(
    private val repository: GameRepository,
    private val generateCalendarUseCase: GenerateCalendarUseCase,
    private val databaseIntegrityUseCase: DatabaseIntegrityUseCase,
    private val globalLeagueSimulationUseCase: GlobalLeagueSimulationUseCase = GlobalLeagueSimulationUseCase(),
    private val observer: SeasonTransitionObserver = SeasonTransitionObserver.NONE
) {

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

    suspend fun advanceToNextSeason(save: GameSave): GameSave = repository.withTransaction {
        val persistedSave = measuredStage("load-save") {
            repository.getGameSave() ?: save
        }

        if (persistedSave.currentSeason != save.currentSeason) {
            return@withTransaction persistedSave
        }

        require(persistedSave.currentWeek >= GameCalendar.WEEKS_PER_SEASON) {
            "Transição de temporada permitida somente após a semana ${GameCalendar.WEEKS_PER_SEASON}."
        }

        val sourceSave = persistedSave
        val currentSeason = sourceSave.currentSeason
        val nextSeason = currentSeason + 1

        val allTeams = measuredStage("load-teams") {
            repository.getAllTeams()
        }
        val seasonFixtures = measuredStage("load-current-season-fixtures") {
            repository.getFixturesForSeason(currentSeason)
        }
        val currentUserCountry = allTeams.firstOrNull { it.id == sourceSave.playerTeamId }?.country
            ?: "Brasil"

        // Snapshot the controlled club's completed season before fixtures are purged. Because the
        // whole transition is one Room transaction, these career counters commit atomically with
        // the rollover and roll back together on any later failure.
        val completedUserFixtures = seasonFixtures.filter { fixture ->
            fixture.isPlayed &&
                fixture.homeScore != null && fixture.awayScore != null &&
                (fixture.homeTeamId == sourceSave.playerTeamId || fixture.awayTeamId == sourceSave.playerTeamId)
        }
        var seasonWins = 0
        var seasonDraws = 0
        var seasonLosses = 0
        var seasonGoalsScored = 0
        var seasonGoalsConceded = 0
        for (fixture in completedUserFixtures) {
            val homeGoals = requireNotNull(fixture.homeScore)
            val awayGoals = requireNotNull(fixture.awayScore)
            val userIsHome = fixture.homeTeamId == sourceSave.playerTeamId
            val userGoals = if (userIsHome) homeGoals else awayGoals
            val opponentGoals = if (userIsHome) awayGoals else homeGoals
            seasonGoalsScored += userGoals
            seasonGoalsConceded += opponentGoals
            when {
                userGoals > opponentGoals -> seasonWins++
                userGoals < opponentGoals -> seasonLosses++
                else -> seasonDraws++
            }
        }
        val sourceSaveWithCareerStats = sourceSave.copy(
            careerMatches = sourceSave.careerMatches + completedUserFixtures.size,
            careerWins = sourceSave.careerWins + seasonWins,
            careerDraws = sourceSave.careerDraws + seasonDraws,
            careerLosses = sourceSave.careerLosses + seasonLosses,
            careerGoalsScored = sourceSave.careerGoalsScored + seasonGoalsScored,
            careerGoalsConceded = sourceSave.careerGoalsConceded + seasonGoalsConceded
        )

        val globalStandings = measuredStage("final-classification") {
            globalLeagueSimulationUseCase.buildSeasonStandings(
                season = currentSeason,
                teams = allTeams,
                detailedFixtures = seasonFixtures,
                detailedCountry = currentUserCountry
            )
        }
        measuredStage("persist-final-standings-snapshot") {
            repository.saveGlobalStandingsForSeason(currentSeason, globalStandings)
        }

        val originalTeamsById = allTeams.associateBy { it.id }
        val updatedTeamsMap = originalTeamsById.toMutableMap()
        measuredStage("promotion-relegation") {
            val teamsByCountry = allTeams
                .filter { CountryFootballRulesRegistry.isDomesticCompetitionEligible(it.country) }
                .groupBy { it.country }
            val snapshotRowsByCountryDivision = globalStandings.groupBy { it.country to it.division }

            for ((country, countryTeams) in teamsByCountry) {
                val isDetailedCountry = country.equals(currentUserCountry, ignoreCase = true)
                val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry(country)
                val divisions = hierarchy.divisions.sortedBy { it.divisionLevel }

                for ((upperRule, lowerRule) in divisions.zipWithNext()) {
                    val upperTeams = countryTeams.filter { it.division == upperRule.divisionLevel }
                    val lowerTeams = countryTeams.filter { it.division == lowerRule.divisionLevel }
                    val movementSpots = hierarchy.safeMovementSpotsBetween(
                        upperLevel = upperRule.divisionLevel,
                        lowerLevel = lowerRule.divisionLevel,
                        upperTeamCount = upperTeams.size,
                        lowerTeamCount = lowerTeams.size
                    )

                    if (movementSpots <= 0) continue

                    val relegatedIds: List<Long>
                    val promotedIds: List<Long>

                    if (isDetailedCountry) {
                        val upperRanking = resolveDetailedCountryMovementRanking(
                            season = currentSeason,
                            country = country,
                            division = upperRule.divisionLevel,
                            teams = upperTeams,
                            fixtures = seasonFixtures,
                            snapshotRows = snapshotRowsByCountryDivision[
                                country to upperRule.divisionLevel
                            ].orEmpty()
                        ) ?: continue
                        val lowerRanking = resolveDetailedCountryMovementRanking(
                            season = currentSeason,
                            country = country,
                            division = lowerRule.divisionLevel,
                            teams = lowerTeams,
                            fixtures = seasonFixtures,
                            snapshotRows = snapshotRowsByCountryDivision[
                                country to lowerRule.divisionLevel
                            ].orEmpty()
                        ) ?: continue

                        relegatedIds = upperRanking.takeLast(movementSpots)
                        promotedIds = lowerRanking.take(movementSpots)
                    } else {
                        val upperSnapshot = snapshotRowsByCountryDivision[
                            country to upperRule.divisionLevel
                        ].orEmpty()
                        val lowerSnapshot = snapshotRowsByCountryDivision[
                            country to lowerRule.divisionLevel
                        ].orEmpty()

                        if (!hasCompleteSnapshot(
                                season = currentSeason,
                                country = country,
                                division = upperRule.divisionLevel,
                                teams = upperTeams,
                                rows = upperSnapshot
                            ) ||
                            !hasCompleteSnapshot(
                                season = currentSeason,
                                country = country,
                                division = lowerRule.divisionLevel,
                                teams = lowerTeams,
                                rows = lowerSnapshot
                            )
                        ) {
                            continue
                        }

                        relegatedIds = upperSnapshot
                            .sortedBy { it.position }
                            .takeLast(movementSpots)
                            .map { it.teamId }
                        promotedIds = lowerSnapshot
                            .sortedBy { it.position }
                            .take(movementSpots)
                            .map { it.teamId }
                    }

                    for (teamId in relegatedIds) {
                        val currentTeam = updatedTeamsMap[teamId] ?: continue
                        updatedTeamsMap[teamId] = currentTeam.copy(
                            division = lowerRule.divisionLevel
                        )
                    }
                    for (teamId in promotedIds) {
                        val currentTeam = updatedTeamsMap[teamId] ?: continue
                        updatedTeamsMap[teamId] = currentTeam.copy(
                            division = upperRule.divisionLevel
                        )
                    }
                }
            }
        }

        val changedTeams = updatedTeamsMap.values.filter { updated ->
            originalTeamsById[updated.id]?.division != updated.division
        }
        measuredStage("persist-team-movements") {
            changedTeams.forEach { repository.updateTeam(it) }
        }

        val retiringPlayers = measuredStage("load-retiring-players") {
            repository.getRolloverRetiringPlayers(RETIREMENT_CURRENT_AGE)
        }
        val activeLoans = measuredStage("load-active-loans") {
            repository.getActiveLoans()
        }
        val activeLoansByPlayerId = activeLoans.associateBy { it.playerId }
        val retiringPlayerIds = retiringPlayers.map { it.id }
        val rand = Random(currentSeason * 31L + sourceSave.playerTeamId)
        val replacementPlayers = retiringPlayers.map { player ->
            val activeLoan = activeLoansByPlayerId[player.id]
            val replacementTeamId = when {
                activeLoan != null -> activeLoan.ownerTeamId
                player.isOnLoan && player.originalTeamId != null -> player.originalTeamId
                else -> player.teamId
            }
            val newForce = if (replacementTeamId == sourceSave.playerTeamId) {
                95
            } else {
                rand.nextInt(55, 75)
            }

            Player(
                teamId = replacementTeamId,
                name = "Novo Prospecto ${player.name.takeLast(6)}",
                age = 18,
                nationality = player.nationality,
                position = player.position,
                force = newForce,
                energy = 100,
                moral = 80,
                salary = 10_000L,
                contractDurationWeeks = 52,
                isFromAcademy = false,
                isStarter = false,
                isOnLoan = false,
                loanWeeksRemaining = 0,
                originalTeamId = null,
                potential = maxOf(80, newForce)
            )
        }

        measuredStage("retirement-and-loan-finalization") {
            repository.completeRolloverLoansForPlayers(retiringPlayerIds)
            repository.deleteRolloverPlayers(retiringPlayerIds)
        }
        measuredStage("player-age-and-season-reset") {
            repository.ageAndResetRolloverPlayers(RETIREMENT_CURRENT_AGE)
        }
        measuredStage("player-seasonal-statistics-reset") {
            repository.resetRolloverSeasonalStatistics()
        }
        measuredStage("persist-retirement-replacements") {
            if (replacementPlayers.isNotEmpty()) {
                repository.savePlayers(replacementPlayers)
            }
        }

        measuredStage("database-integrity-and-free-agents") {
            databaseIntegrityUseCase.repairDatabase()
        }

        measuredStage("previous-season-cleanup") {
            repository.purgeOldData(nextSeason)
            repository.deleteFixtures()
        }

        val updatedTeamsList = updatedTeamsMap.values.toList()
        val playerTeam = updatedTeamsMap[sourceSave.playerTeamId]
        val userCountry = playerTeam?.country ?: currentUserCountry

        val newFixtures = measuredStage("generate-new-season-fixtures") {
            generateCalendarUseCase.generateSeasonFixtures(
                season = nextSeason,
                teams = updatedTeamsList,
                userTeamId = sourceSave.playerTeamId,
                userCountry = userCountry,
                qualificationStandings = globalStandings
            )
        }
        measuredStage("persist-new-season-fixtures") {
            repository.saveFixtures(newFixtures)
        }

        val updatedSave = sourceSaveWithCareerStats.copy(
            currentSeason = nextSeason,
            currentWeek = 1
        )
        measuredStage("persist-canonical-save") {
            repository.saveGameSave(updatedSave)
        }

        updatedSave
    }

    private suspend fun <T> measuredStage(stage: String, block: suspend () -> T): T {
        observer.onStageStarted(stage)
        val started = System.nanoTime()
        return try {
            block()
        } finally {
            observer.onStageFinished(stage, System.nanoTime() - started)
        }
    }

    /**
     * Resolve a ordem esportiva usada em uma fronteira do país detalhado.
     *
     * Formatos detalhados suportados — round-robin direto ou grupos balanceados — dependem
     * estritamente dos resultados reais e completos. Apenas tamanhos ainda sem formato detalhado
     * válido em 40 semanas usam o snapshot compacto como fallback temporário.
     */
    private fun resolveDetailedCountryMovementRanking(
        season: Int,
        country: String,
        division: Int,
        teams: List<Team>,
        fixtures: List<Fixture>,
        snapshotRows: List<GlobalLeagueStanding>
    ): List<Long>? {
        if (LeagueSeasonFormat.supportsDetailedFormat(teams.size)) {
            if (!hasCompletedLeagueSeason(
                    teams = teams,
                    fixtures = fixtures,
                    division = division
                )
            ) {
                return null
            }
            return calculateSeasonStandings(
                teams = teams,
                fixtures = fixtures,
                division = division
            ).map { it.first.id }
        }

        if (!hasCompleteSnapshot(
                season = season,
                country = country,
                division = division,
                teams = teams,
                rows = snapshotRows
            )
        ) {
            return null
        }
        return snapshotRows.sortedBy { it.position }.map { it.teamId }
    }

    private fun hasCompleteSnapshot(
        season: Int,
        country: String,
        division: Int,
        teams: List<Team>,
        rows: List<GlobalLeagueStanding>
    ): Boolean {
        if (teams.isEmpty() || rows.size != teams.size) return false

        val expectedTeamIds = teams.map { it.id }.toSet()
        val rowTeamIds = rows.map { it.teamId }
        if (rowTeamIds.toSet() != expectedTeamIds || rowTeamIds.size != rowTeamIds.toSet().size) {
            return false
        }

        if (rows.any {
                it.season != season ||
                    !it.country.equals(country, ignoreCase = true) ||
                    it.division != division
            }
        ) {
            return false
        }

        return rows.map { it.position }.sorted() == (1..teams.size).toList()
    }

    private fun hasCompletedLeagueSeason(
        teams: List<Team>,
        fixtures: List<Fixture>,
        division: Int
    ): Boolean {
        if (teams.size < 2) return false
        val teamIds = teams.map { it.id }.toSet()
        if (teamIds.size != teams.size) return false

        val acceptedTypes = LeagueSeasonFormat.acceptedDetailedCompetitionTypes(division)
        val relevantFixtures = fixtures.filter { fixture ->
            fixture.competitionType in acceptedTypes &&
                fixture.homeTeamId in teamIds &&
                fixture.awayTeamId in teamIds
        }
        val legs = LeagueSeasonFormat.legsForDetailedLeague(teams.size)
        val expectedFixtureCount = LeagueSeasonFormat.expectedFixtureCount(teams.size)

        if (relevantFixtures.size != expectedFixtureCount || relevantFixtures.any {
                !it.isPlayed || it.homeScore == null || it.awayScore == null
            }
        ) {
            return false
        }

        return LeagueSeasonFormat.hasExpectedDetailedPairings(
            teamIds = teamIds,
            fixtures = relevantFixtures,
            legs = legs
        )
    }

    private fun calculateSeasonStandings(
        teams: List<Team>,
        fixtures: List<Fixture>,
        division: Int
    ): List<Pair<Team, SeasonStandingRow>> {
        val map = teams.associateWith { SeasonStandingRow(it.name) }.toMutableMap()
        val teamIds = teams.map { it.id }.toSet()
        val acceptedTypes = LeagueSeasonFormat.acceptedDetailedCompetitionTypes(division)
        val relevantFixtures = fixtures.filter {
            it.competitionType in acceptedTypes &&
                it.isPlayed &&
                it.homeTeamId in teamIds &&
                it.awayTeamId in teamIds
        }

        for (fixture in relevantFixtures) {
            val homeTeam = teams.find { it.id == fixture.homeTeamId }
            val awayTeam = teams.find { it.id == fixture.awayTeamId }
            val homeGoals = fixture.homeScore ?: 0
            val awayGoals = fixture.awayScore ?: 0

            if (homeTeam != null && awayTeam != null) {
                val homeRow = map[homeTeam] ?: continue
                val awayRow = map[awayTeam] ?: continue

                homeRow.gf += homeGoals
                homeRow.ga += awayGoals
                awayRow.gf += awayGoals
                awayRow.ga += homeGoals
                homeRow.gp += 1
                awayRow.gp += 1

                when {
                    homeGoals > awayGoals -> {
                        homeRow.pts += 3
                        homeRow.w += 1
                        awayRow.l += 1
                    }
                    awayGoals > homeGoals -> {
                        awayRow.pts += 3
                        awayRow.w += 1
                        homeRow.l += 1
                    }
                    else -> {
                        homeRow.pts += 1
                        awayRow.pts += 1
                        homeRow.d += 1
                        awayRow.d += 1
                    }
                }
            }
        }

        val teamsById = teams.associateBy { it.id }
        val sportingComparator = compareByDescending<Long> { teamId ->
            teamsById[teamId]?.let { team -> map[team]?.pts } ?: 0
        }.thenByDescending { teamId ->
            teamsById[teamId]?.let { team -> map[team]?.w } ?: 0
        }.thenByDescending { teamId ->
            teamsById[teamId]?.let { team -> map[team]?.gd } ?: 0
        }.thenByDescending { teamId ->
            teamsById[teamId]?.let { team -> map[team]?.gf } ?: 0
        }.thenByDescending { teamId ->
            teamsById[teamId]?.rating ?: Int.MIN_VALUE
        }.thenBy { it }

        val groupedOrder = DetailedGroupTopology.rankByGroupPosition(
            teamIds = teamIds,
            fixtures = relevantFixtures,
            sportingComparator = sportingComparator
        )
        if (groupedOrder != null) {
            return groupedOrder.mapNotNull { teamId ->
                val team = teamsById[teamId] ?: return@mapNotNull null
                val row = map[team] ?: return@mapNotNull null
                team to row
            }
        }

        return map.toList().sortedWith(
            compareByDescending<Pair<Team, SeasonStandingRow>> { it.second.pts }
                .thenByDescending { it.second.w }
                .thenByDescending { it.second.gd }
                .thenByDescending { it.second.gf }
        )
    }

    private companion object {
        const val RETIREMENT_CURRENT_AGE = 37
    }
}
