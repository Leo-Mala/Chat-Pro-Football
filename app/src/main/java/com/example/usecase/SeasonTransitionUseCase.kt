package com.example.usecase

import com.example.data.*
import kotlin.random.Random

/**
 * UseCase responsável pela transição completa e atômica de temporada.
 * Processa promoção/rebaixamento, envelhecimento de atletas, reparo de integridade,
 * purga de dados antigos e geração do novo calendário de jogos.
 */
class SeasonTransitionUseCase(
    private val repository: GameRepository,
    private val generateCalendarUseCase: GenerateCalendarUseCase,
    private val databaseIntegrityUseCase: DatabaseIntegrityUseCase
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

        // 1. Calcular promoção e rebaixamento em memória antes de aplicar no banco.
        val updatedTeamsMap = allTeams.associateBy { it.id }.toMutableMap()
        val teamsByCountry = allTeams.groupBy { it.country }

        for ((_, countryTeams) in teamsByCountry) {
            val teamsByDiv = countryTeams.groupBy { it.division }
            for (div in 1..3) {
                val upperTeams = teamsByDiv[div] ?: emptyList()
                val lowerTeams = teamsByDiv[div + 1] ?: emptyList()

                if (upperTeams.size >= 4 && lowerTeams.size >= 4) {
                    val upperCode = when (div) {
                        1 -> "SERIE_A"
                        2 -> "SERIE_B"
                        3 -> "SERIE_C"
                        else -> "SERIE_D"
                    }
                    val lowerCode = when (div + 1) {
                        1 -> "SERIE_A"
                        2 -> "SERIE_B"
                        3 -> "SERIE_C"
                        else -> "SERIE_D"
                    }

                    val upperStandings = calculateSeasonStandings(upperTeams, seasonFixtures, upperCode)
                    val lowerStandings = calculateSeasonStandings(lowerTeams, seasonFixtures, lowerCode)

                    val relegated = upperStandings.takeLast(4).map { it.first }
                    val promoted = lowerStandings.take(4).map { it.first }

                    for (team in relegated) {
                        val currentTeam = updatedTeamsMap[team.id] ?: team
                        updatedTeamsMap[team.id] = currentTeam.copy(division = div + 1)
                    }
                    for (team in promoted) {
                        val currentTeam = updatedTeamsMap[team.id] ?: team
                        updatedTeamsMap[team.id] = currentTeam.copy(division = div)
                    }
                }
            }
        }

        updatedTeamsMap.values.forEach { repository.updateTeam(it) }

        // 2. Envelhecer atletas uma única vez por temporada e renovar aposentadorias.
        val rand = Random(currentSeason * 31L + sourceSave.playerTeamId)
        val updatedPlayers = allPlayers.map { player ->
            val newAge = player.age + 1
            if (newAge >= 38) {
                val newForce = if (player.teamId == sourceSave.playerTeamId) 95 else rand.nextInt(55, 75)
                player.copy(
                    age = 18,
                    force = newForce,
                    energy = 100,
                    moral = 80,
                    injuryWeeksRemaining = 0,
                    suspensionWeeksRemaining = 0,
                    yellowCardsAccumulated = 0,
                    name = "Novo Prospecto ${player.name.takeLast(6)}"
                )
            } else {
                player.copy(
                    age = newAge,
                    energy = 100,
                    moral = 80.coerceAtLeast(player.moral),
                    injuryWeeksRemaining = 0,
                    suspensionWeeksRemaining = 0,
                    yellowCardsAccumulated = 0
                )
            }
        }
        repository.updatePlayers(updatedPlayers)

        // 3. Reparar integridade antes de gerar o novo calendário.
        databaseIntegrityUseCase.repairDatabase()

        // 4. Remover fixtures/dados antigos somente depois de todas as regras da semana 40 terem sido processadas.
        repository.purgeOldData(nextSeason)
        repository.deleteFixtures()

        // 5. Gerar calendário oficial da temporada seguinte.
        val updatedTeamsList = updatedTeamsMap.values.toList()
        val newFixtures = mutableListOf<Fixture>()

        val countries = updatedTeamsList.map { it.country }.distinct()
        for (country in countries) {
            val countryTeams = updatedTeamsList.filter { it.country == country }
            val divisions = countryTeams.map { it.division }.distinct()
            for (division in divisions) {
                val divisionTeams = countryTeams.filter { it.division == division }
                if (divisionTeams.size >= 2) {
                    val competitionType = when (division) {
                        1 -> "SERIE_A"
                        2 -> "SERIE_B"
                        3 -> "SERIE_C"
                        else -> "SERIE_D"
                    }
                    newFixtures.addAll(
                        generateCalendarUseCase.generateRoundRobinFixtures(
                            nextSeason,
                            divisionTeams,
                            competitionType,
                            1
                        )
                    )
                }
            }
        }

        repository.saveFixtures(newFixtures)

        // 6. Persistir a nova temporada por último. Esse write é o marcador atômico da transição concluída.
        val updatedSave = sourceSave.copy(
            currentSeason = nextSeason,
            currentWeek = 1
        )
        repository.saveGameSave(updatedSave)

        updatedSave
    }

    private fun calculateSeasonStandings(
        teams: List<Team>,
        fixtures: List<Fixture>,
        compType: String
    ): List<Pair<Team, SeasonStandingRow>> {
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
        val relevantFixtures = fixtures.filter {
            (it.competitionType == compType || it.competitionType == altCompType) && it.isPlayed
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
