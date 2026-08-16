package com.example.usecase

import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.GlobalFootballSystem
import com.example.data.SuperMundialSystem
import com.example.data.Team

/**
 * UseCase responsável por gerar o calendário de jogos, tabelas das divisões e mata-matas.
 */
class GenerateCalendarUseCase(private val repository: GameRepository) {

    /**
     * Gera todas as partidas em turno e returno para uma lista de times de uma divisão.
     */
    fun generateRoundRobinFixtures(
        season: Int,
        teams: List<Team>,
        competitionType: String,
        startWeek: Int = 1
    ): List<Fixture> {
        val fixtures = mutableListOf<Fixture>()
        if (teams.size < 2) return fixtures

        val n = if (teams.size % 2 == 0) teams.size else teams.size + 1
        val teamList = teams.map { it.id }.toMutableList()
        if (teams.size % 2 != 0) {
            teamList.add(-1L) // Bye team placeholder
        }

        val totalRounds = n - 1
        val matchesPerRound = n / 2

        // Turno
        for (round in 0 until totalRounds) {
            val weekNum = startWeek + round
            for (i in 0 until matchesPerRound) {
                val home = teamList[i]
                val away = teamList[n - 1 - i]

                if (home != -1L && away != -1L && home != 0L && away != 0L) {
                    val (h, a) = if (round % 2 == 0) Pair(home, away) else Pair(away, home)
                    fixtures.add(
                        Fixture(
                            season = season,
                            week = weekNum,
                            homeTeamId = h,
                            awayTeamId = a,
                            competitionType = competitionType,
                            isPlayed = false
                        )
                    )
                }
            }
            // Rotate list elements excluding index 0
            val last = teamList.removeAt(teamList.size - 1)
            teamList.add(1, last)
        }

        // Returno (inverted home/away)
        val returnoStartWeek = startWeek + totalRounds
        val turnoFixtures = fixtures.toList()
        for (f in turnoFixtures) {
            val returnoWeek = f.week + totalRounds
            fixtures.add(
                Fixture(
                    season = season,
                    week = returnoWeek,
                    homeTeamId = f.awayTeamId,
                    awayTeamId = f.homeTeamId,
                    competitionType = competitionType,
                    isPlayed = false
                )
            )
        }

        return fixtures
    }

    /**
     * Salva em lote a lista de partidas no repositório.
     */
    suspend fun saveCalendarFixtures(fixtures: List<Fixture>) {
        if (fixtures.isNotEmpty()) {
            repository.saveFixtures(fixtures)
        }
    }

    /**
     * Gera os jogos da temporada escopados ao país do time do usuário.
     * Limita a geração das 38 rodadas de liga de pontos corridos para o país do time do usuário,
     * gerando para os demais países apenas os participantes dos torneios continentais e mundial.
     */
    fun generateSeasonFixtures(
        season: Int,
        teams: List<Team>,
        userTeamId: Long,
        userCountry: String = "BRASIL"
    ): List<Fixture> {
        val allFixtures = mutableListOf<Fixture>()
        val userTeam = teams.find { it.id == userTeamId }
        val targetCountry = userTeam?.country ?: userCountry

        // Geração da liga de pontos corridos (38 rodadas) apenas para o país do time do usuário
        val groupedTeams = teams.groupBy { Pair(it.country, it.division) }
        for ((key, teamGroup) in groupedTeams) {
            val (country, div) = key
            if (country == targetCountry && teamGroup.size >= 2) {
                val divCode = when (div) {
                    1 -> "SERIE_A"
                    2 -> "SERIE_B"
                    3 -> "SERIE_C"
                    else -> "SERIE_D"
                }
                val groupFixtures = generateRoundRobinFixtures(season, teamGroup, divCode, 1)
                allFixtures.addAll(groupFixtures)
            }
        }

        // Torneios continentais e mundial para times elegíveis de todos os países
        val worldCupFixtures = SuperMundialSystem.generateGroupStageFixtures(season, teams, userTeamId)
        allFixtures.addAll(worldCupFixtures)

        return allFixtures
    }

    /**
     * Gera os confrontos para uma rodada de mata-mata (Copas / torneios de chaveamento).
     * Trata listas ímpares adicionando um time bye/virtual antes de parear os confrontos.
     */
    fun generateKnockoutFixtures(
        season: Int,
        week: Int,
        teams: List<Team>,
        competitionType: String
    ): List<Fixture> {
        if (teams.isEmpty()) return emptyList()

        val teamList = teams.toMutableList()
        if (teamList.size % 2 != 0) {
            val virtualTeam = GlobalFootballSystem.getVirtualTeam(990_000L + teamList.size)
            teamList.add(virtualTeam)
        }

        val fixtures = mutableListOf<Fixture>()
        for (i in 0 until teamList.size / 2) {
            val home = teamList[i * 2]
            val away = teamList[i * 2 + 1]
            fixtures.add(
                Fixture(
                    season = season,
                    week = week,
                    homeTeamId = home.id,
                    awayTeamId = away.id,
                    competitionType = competitionType,
                    isPlayed = false
                )
            )
        }
        return fixtures
    }

    /**
     * Overload para parear chaves de mata-mata a partir de lista de IDs de times.
     * Trata listas ímpares adicionando ID de time virtual antes de parear.
     */
    fun generateKnockoutFixturesFromIds(
        season: Int,
        week: Int,
        teamIds: List<Long>,
        competitionType: String
    ): List<Fixture> {
        if (teamIds.isEmpty()) return emptyList()

        val idList = teamIds.toMutableList()
        if (idList.size % 2 != 0) {
            val virtualTeam = GlobalFootballSystem.getVirtualTeam(990_000L + idList.size)
            idList.add(virtualTeam.id)
        }

        val fixtures = mutableListOf<Fixture>()
        for (i in 0 until idList.size / 2) {
            fixtures.add(
                Fixture(
                    season = season,
                    week = week,
                    homeTeamId = idList[i * 2],
                    awayTeamId = idList[i * 2 + 1],
                    competitionType = competitionType,
                    isPlayed = false
                )
            )
        }
        return fixtures
    }

    /**
     * Seleciona os times classificados para a Libertadores garantindo vagas únicas e sem conflitos.
     */
    fun selectLibertadoresTeams(
        brasilSerieATeams: List<Team>,
        brasilCopaTeams: List<Team>,
        remainingCandidates: List<Team> = emptyList()
    ): List<Team> {
        val topSerieA = brasilSerieATeams.sortedByDescending { it.rating }.take(6)

        // Garante que o slot da Copa não seja ocupado por um time que já está na Libertadores via Série A
        val topCopa = brasilCopaTeams
            .filter { it !in topSerieA }
            .maxByOrNull { it.rating }
            ?: brasilSerieATeams.filter { it !in topSerieA }.maxByOrNull { it.rating }

        val libBrasil = (topSerieA + listOfNotNull(topCopa)).distinct()

        val selectedTeams = libBrasil.toMutableList()
        val targetCount = 32

        // Padrão corrigido e protegido contra loop infinito
        while (selectedTeams.size < targetCount) {
            val candidate = remainingCandidates.firstOrNull { it !in selectedTeams }
            if (candidate != null) {
                selectedTeams.add(candidate)
            } else {
                break // Interrompe o laço imediatamente se não houver mais candidatos!
            }
        }

        // Preenchimento de segurança se a lista ainda não tiver atingido a meta
        var dummyCounter = 1
        while (selectedTeams.size < targetCount) {
            val virtualTeam = GlobalFootballSystem.getVirtualTeam(900_000L + dummyCounter)
            if (selectedTeams.none { it.id == virtualTeam.id }) {
                selectedTeams.add(virtualTeam)
            }
            dummyCounter++
        }

        return selectedTeams
    }
}
