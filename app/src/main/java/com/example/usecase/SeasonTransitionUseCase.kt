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
    private val globalLeagueSimulationUseCase: GlobalLeagueSimulationUseCase = GlobalLeagueSimulationUseCase()
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
        val persistedSave = repository.getGameSave() ?: save

        if (persistedSave.currentSeason != save.currentSeason) {
            return@withTransaction persistedSave
        }

        require(persistedSave.currentWeek >= GameCalendar.WEEKS_PER_SEASON) {
            "Transição de temporada permitida somente após a semana ${GameCalendar.WEEKS_PER_SEASON}."
        }

        val sourceSave = persistedSave
        val currentSeason = sourceSave.currentSeason
        val nextSeason = currentSeason + 1

        val allTeams = repository.getAllTeams()
        val allPlayers = repository.getAllPlayers()
        val seasonFixtures = repository.getFixturesForSeason(currentSeason)
        val currentUserCountry = allTeams.firstOrNull { it.id == sourceSave.playerTeamId }?.country
            ?: "Brasil"

        val globalStandings = globalLeagueSimulationUseCase.buildSeasonStandings(
            season = currentSeason,
            teams = allTeams,
            detailedFixtures = seasonFixtures,
            detailedCountry = currentUserCountry
        )
        repository.saveGlobalStandingsForSeason(currentSeason, globalStandings)

        val updatedTeamsMap = allTeams.associateBy { it.id }.toMutableMap()
        val teamsByCountry = allTeams.groupBy { it.country }
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

        updatedTeamsMap.values.forEach { repository.updateTeam(it) }

        val rand = Random(currentSeason * 31L + sourceSave.playerTeamId)
        val playersToUpdate = mutableListOf<Player>()
        val replacementPlayers = mutableListOf<Player>()

        for (player in allPlayers) {
            val newAge = player.age + 1
            if (newAge >= 38) {
                val activeLoan = repository.getActiveLoanForPlayer(player.id)
                if (activeLoan != null) {
                    repository.updateLoan(
                        activeLoan.copy(
                            remainingWeeks = 0,
                            status = "COMPLETED"
                        )
                    )
                }

                val replacementTeamId = when {
                    activeLoan != null -> activeLoan.ownerTeamId
                    player.isOnLoan && player.originalTeamId != 0L -> player.originalTeamId
                    else -> player.teamId
                }
                val newForce = if (replacementTeamId == sourceSave.playerTeamId) {
                    95
                } else {
                    rand.nextInt(55, 75)
                }

                repository.deletePlayer(player.id)
                replacementPlayers += Player(
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
                    originalTeamId = 0L,
                    potential = maxOf(80, newForce)
                )
            } else {
                playersToUpdate += player.copy(
                    age = newAge,
                    energy = 100,
                    moral = 80.coerceAtLeast(player.moral),
                    injuryWeeksRemaining = 0,
                    suspensionWeeksRemaining = 0,
                    yellowCardsAccumulated = 0
                )
            }
        }

        if (playersToUpdate.isNotEmpty()) {
            repository.updatePlayers(playersToUpdate)
        }
        if (replacementPlayers.isNotEmpty()) {
            repository.savePlayers(replacementPlayers)
        }

        databaseIntegrityUseCase.repairDatabase()

        repository.purgeOldData(nextSeason)
        repository.deleteFixtures()

        val updatedTeamsList = updatedTeamsMap.values.toList()
        val playerTeam = updatedTeamsMap[sourceSave.playerTeamId]
        val userCountry = playerTeam?.country ?: currentUserCountry

        val newFixtures = generateCalendarUseCase.generateSeasonFixtures(
            season = nextSeason,
            teams = updatedTeamsList,
            userTeamId = sourceSave.playerTeamId,
            userCountry = userCountry,
            qualificationStandings = globalStandings
        )
        repository.saveFixtures(newFixtures)

        val updatedSave = sourceSave.copy(
            currentSeason = nextSeason,
            currentWeek = 1
        )
        repository.saveGameSave(updatedSave)

        updatedSave
    }

    /**
     * Resolve a ordem esportiva usada em uma fronteira do país detalhado.
     *
     * Ligas que cabem nas 40 semanas continuam estritamente dependentes dos resultados reais:
     * se o calendário estiver parcial ou corrompido, não há movimentação. Para uma divisão cujo
     * formato atual é estruturalmente impossível de concluir dentro das 40 semanas, usamos o
     * snapshot compacto validado como fallback temporário até a subfase de grupos/estágios.
     */
    private fun resolveDetailedCountryMovementRanking(
        season: Int,
        country: String,
        division: Int,
        teams: List<Team>,
        fixtures: List<Fixture>,
        snapshotRows: List<GlobalLeagueStanding>
    ): List<Long>? {
        if (LeagueSeasonFormat.fitsCurrentSeason(teams.size)) {
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

        val ids = teamIds.sorted()
        if (legs == 2) {
            val directedPairCounts = relevantFixtures
                .groupingBy { it.homeTeamId to it.awayTeamId }
                .eachCount()
            return ids.all { homeId ->
                ids.all { awayId ->
                    homeId == awayId || directedPairCounts[homeId to awayId] == 1
                }
            }
        }

        val unorderedPairCounts = relevantFixtures
            .groupingBy { minOf(it.homeTeamId, it.awayTeamId) to maxOf(it.homeTeamId, it.awayTeamId) }
            .eachCount()
        for (i in 0 until ids.lastIndex) {
            for (j in i + 1 until ids.size) {
                if (unorderedPairCounts[ids[i] to ids[j]] != 1) return false
            }
        }
        return true
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

        return map.toList().sortedWith(
            compareByDescending<Pair<Team, SeasonStandingRow>> { it.second.pts }
                .thenByDescending { it.second.w }
                .thenByDescending { it.second.gd }
                .thenByDescending { it.second.gf }
        )
    }
}
