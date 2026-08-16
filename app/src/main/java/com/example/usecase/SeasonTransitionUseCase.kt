package com.example.usecase

import com.example.data.*
import kotlin.random.Random

/**
 * UseCase responsável pela transição completa e atômica de temporada.
 * Processa promoção/rebaixamento, envelhecimento de atletas, aposentadorias com substituição collision-safe,
 * reparo de integridade, purga de logs antigos e geração do novo calendário de jogos.
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
        val currentSeason = save.currentSeason
        val nextSeason = currentSeason + 1

        val allTeams = repository.getAllTeams()
        val allPlayers = repository.getAllPlayers().toMutableList()
        val existingPlayerIds = allPlayers.map { it.id }.toMutableSet()
        val seasonFixtures = repository.getFixturesForSeason(currentSeason)

        // 1. Calcular Promoção e Rebaixamento de forma isolada em memória antes de aplicar no banco
        val updatedTeamsMap = allTeams.associateBy { it.id }.toMutableMap()
        val teamsByCountry = allTeams.groupBy { it.country }

        for ((country, countryTeams) in teamsByCountry) {
            val teamsByDiv = countryTeams.groupBy { it.division }
            for (div in 1..3) {
                val upperTeams = teamsByDiv[div] ?: emptyList()
                val lowerTeams = teamsByDiv[div + 1] ?: emptyList()

                if (upperTeams.size >= 4 && lowerTeams.size >= 4) {
                    val upperCode = when (div) { 1 -> "SERIE_A"; 2 -> "SERIE_B"; 3 -> "SERIE_C"; else -> "SERIE_D" }
                    val lowerCode = when (div + 1) { 1 -> "SERIE_A"; 2 -> "SERIE_B"; 3 -> "SERIE_C"; else -> "SERIE_D" }

                    val upperStandings = calculateSeasonStandings(upperTeams, seasonFixtures, upperCode)
                    val lowerStandings = calculateSeasonStandings(lowerTeams, seasonFixtures, lowerCode)

                    val relegated = upperStandings.takeLast(4).map { it.first }
                    val promoted = lowerStandings.take(4).map { it.first }

                    for (t in relegated) {
                        val currentT = updatedTeamsMap[t.id] ?: t
                        updatedTeamsMap[t.id] = currentT.copy(division = div + 1)
                    }
                    for (t in promoted) {
                        val currentT = updatedTeamsMap[t.id] ?: t
                        updatedTeamsMap[t.id] = currentT.copy(division = div)
                    }
                }
            }
        }

        // Aplicar alterações de divisão em lote
        updatedTeamsMap.values.forEach { repository.updateTeam(it) }

        // Helper para ID de jogador collision-safe
        fun getCollisionSafePlayerId(desiredId: Long): Long {
            var candidate = if (desiredId <= 0L) 100000L else desiredId
            while (candidate in existingPlayerIds) {
                candidate++
            }
            existingPlayerIds.add(candidate)
            return candidate
        }

        // 2. Incrementar idade dos atletas e renovar idades / aposentadorias de forma collision-safe
        val rand = Random(currentSeason * 31L + save.playerTeamId)
        val updatedPlayers = allPlayers.map { p ->
            val newAge = p.age + 1
            if (newAge >= 38) {
                // Aposentadoria -> renovar como promessa jovem
                val newForce = if (p.teamId == save.playerTeamId) 95 else rand.nextInt(55, 75)
                p.copy(
                    age = 18,
                    force = newForce,
                    energy = 100,
                    moral = 80,
                    injuryWeeksRemaining = 0,
                    suspensionWeeksRemaining = 0,
                    yellowCardsAccumulated = 0,
                    name = "Novo Prospecto ${p.name.takeLast(6)}"
                )
            } else {
                p.copy(
                    age = newAge,
                    energy = 100,
                    moral = 80.coerceAtLeast(p.moral),
                    injuryWeeksRemaining = 0,
                    suspensionWeeksRemaining = 0,
                    yellowCardsAccumulated = 0
                )
            }
        }
        repository.updatePlayers(updatedPlayers)

        // 3. Executar reparo de integridade no banco de dados ativo
        databaseIntegrityUseCase.repairDatabase()

        // 4. Excluir fixtures antigas e purgar registros legados
        repository.purgeOldData(nextSeason)
        repository.deleteFixtures()

        // 5. Gerar novo calendário oficial para a temporada vindoura
        val updatedTeamsList = updatedTeamsMap.values.toList()
        val newFixtures = mutableListOf<Fixture>()

        val countries = updatedTeamsList.map { it.country }.distinct()
        for (country in countries) {
            val cTeams = updatedTeamsList.filter { it.country == country }
            val cDivs = cTeams.map { it.division }.distinct()
            for (div in cDivs) {
                val divTeams = cTeams.filter { it.division == div }
                if (divTeams.size >= 2) {
                    val compType = when (div) { 1 -> "SERIE_A"; 2 -> "SERIE_B"; 3 -> "SERIE_C"; else -> "SERIE_D" }
                    val divFixtures = generateCalendarUseCase.generateRoundRobinFixtures(nextSeason, divTeams, compType, 1)
                    newFixtures.addAll(divFixtures)
                }
            }
        }

        repository.saveFixtures(newFixtures)

        // 6. Atualizar GameSave com nova temporada e semana 1
        val updatedSave = save.copy(
            currentSeason = nextSeason,
            currentWeek = 1
        )
        repository.saveGameSave(updatedSave)

        updatedSave
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

        return map.toList().sortedWith(
            compareByDescending<Pair<Team, SeasonStandingRow>> { it.second.pts }
                .thenByDescending { it.second.w }
                .thenByDescending { it.second.gd }
                .thenByDescending { it.second.gf }
        )
    }
}
