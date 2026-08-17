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

        // Idempotência: uma chamada repetida com um snapshot da temporada anterior
        // nunca deve envelhecer atletas, mover divisões ou apagar fixtures novamente.
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

        // 1. Persistir primeiro o retrato global da temporada que terminou.
        // O país do usuário aproveita fixtures reais de cada divisão quando completos; todas as
        // demais divisões recebem uma classificação agregada determinística. Como esta função
        // já está dentro da transação de temporada, delete + insert permanecem atômicos sem
        // abrir um segundo withTransaction.
        val globalStandings = globalLeagueSimulationUseCase.buildSeasonStandings(
            season = currentSeason,
            teams = allTeams,
            detailedFixtures = seasonFixtures,
            detailedCountry = currentUserCountry
        )
        repository.saveGlobalStandingsForSeason(currentSeason, globalStandings)

        // 2. Promoção/rebaixamento.
        // - país do usuário: somente resultados detalhados realmente concluídos podem mover clubes;
        // - países CPU com hierarquia explícita: usam os snapshots compactos recém-persistidos;
        // - países sem hierarquia própria ficam imóveis em vez de herdar regras brasileiras.
        val updatedTeamsMap = allTeams.associateBy { it.id }.toMutableMap()
        val teamsByCountry = allTeams.groupBy { it.country }
        val snapshotRowsByCountryDivision = globalStandings.groupBy { it.country to it.division }

        for ((country, countryTeams) in teamsByCountry) {
            val isDetailedCountry = country.equals(currentUserCountry, ignoreCase = true)
            if (!isDetailedCountry && country !in LeagueHierarchyLoader.supportedCountries) {
                continue
            }

            val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry(country)
            val divisions = hierarchy.divisions.sortedBy { it.divisionLevel }

            for ((upperRule, lowerRule) in divisions.zipWithNext()) {
                val upperTeams = countryTeams.filter { it.division == upperRule.divisionLevel }
                val lowerTeams = countryTeams.filter { it.division == lowerRule.divisionLevel }
                val movementSpots = hierarchy.movementSpotsBetween(
                    upperRule.divisionLevel,
                    lowerRule.divisionLevel
                )

                if (movementSpots <= 0 ||
                    upperTeams.size < movementSpots ||
                    lowerTeams.size < movementSpots
                ) {
                    continue
                }

                val relegatedIds: List<Long>
                val promotedIds: List<Long>

                if (isDetailedCountry) {
                    if (!hasCompletedLeagueSeason(upperTeams, seasonFixtures, upperRule.code) ||
                        !hasCompletedLeagueSeason(lowerTeams, seasonFixtures, lowerRule.code)
                    ) {
                        continue
                    }

                    val upperStandings = calculateSeasonStandings(
                        upperTeams,
                        seasonFixtures,
                        upperRule.code
                    )
                    val lowerStandings = calculateSeasonStandings(
                        lowerTeams,
                        seasonFixtures,
                        lowerRule.code
                    )

                    relegatedIds = upperStandings.takeLast(movementSpots).map { it.first.id }
                    promotedIds = lowerStandings.take(movementSpots).map { it.first.id }
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

        // 3. Envelhecer atletas uma única vez por temporada. Aposentadoria cria uma
        // identidade nova de verdade: o atleta antigo é removido e o substituto recebe
        // novo ID, contrato/estatísticas zerados e nenhum vínculo de empréstimo herdado.
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

        // 4. Reparar integridade antes de gerar o novo calendário.
        databaseIntegrityUseCase.repairDatabase()

        // 5. Remover fixtures/dados antigos somente depois de todas as regras da semana 40
        // e do snapshot global terem sido processados.
        repository.purgeOldData(nextSeason)
        repository.deleteFixtures()

        // 6. A primeira temporada e todas as seguintes usam exatamente o mesmo gerador.
        // A liga detalhada permanece no país do usuário; a classificação global da temporada
        // encerrada passa a ordenar a qualificação continental da nova temporada.
        val updatedTeamsList = updatedTeamsMap.values.toList()
        val playerTeam = updatedTeamsMap[sourceSave.playerTeamId]
        val userCountry = playerTeam?.country
            ?: currentUserCountry

        val newFixtures = generateCalendarUseCase.generateSeasonFixtures(
            season = nextSeason,
            teams = updatedTeamsList,
            userTeamId = sourceSave.playerTeamId,
            userCountry = userCountry,
            qualificationStandings = globalStandings
        )
        repository.saveFixtures(newFixtures)

        // 7. Persistir a nova temporada por último. Esse write é o marcador atômico da transição concluída.
        val updatedSave = sourceSave.copy(
            currentSeason = nextSeason,
            currentWeek = 1
        )
        repository.saveGameSave(updatedSave)

        updatedSave
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
        compType: String
    ): Boolean {
        if (teams.size < 2) return false
        val teamIds = teams.map { it.id }.toSet()
        if (teamIds.size != teams.size) return false

        val acceptedTypes = setOf(compType, alternateCompetitionType(compType))
        val relevantFixtures = fixtures.filter { fixture ->
            fixture.competitionType in acceptedTypes &&
                fixture.homeTeamId in teamIds &&
                fixture.awayTeamId in teamIds
        }
        val legs = LeagueSeasonFormat.legsForDetailedLeague(teams.size)
        val expectedFixtureCount = LeagueSeasonFormat.expectedFixtureCount(teams.size)

        // Uma temporada só pode gerar promoção/rebaixamento quando todos os confrontos do
        // formato vigente estão concluídos e com placar. Para 2 turnos, cada direção do par
        // deve existir uma vez; para turno único, cada par não ordenado deve existir uma vez.
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
        compType: String
    ): List<Pair<Team, SeasonStandingRow>> {
        val map = teams.associateWith { SeasonStandingRow(it.name) }.toMutableMap()
        val teamIds = teams.map { it.id }.toSet()
        val acceptedTypes = setOf(compType, alternateCompetitionType(compType))
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

    private fun alternateCompetitionType(compType: String): String {
        return when (compType) {
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
    }
}
