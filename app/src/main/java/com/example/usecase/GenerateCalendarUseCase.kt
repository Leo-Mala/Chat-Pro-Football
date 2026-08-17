package com.example.usecase

import com.example.data.ContinentalQualificationRules
import com.example.data.CupCompetitionSystem
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.GlobalFootballSystem
import com.example.data.GlobalLeagueStanding
import com.example.data.LeagueSeasonFormat
import com.example.data.SuperMundialSystem
import com.example.data.Team

/**
 * UseCase responsável por gerar o calendário de jogos, tabelas das divisões e mata-matas.
 */
class GenerateCalendarUseCase(private val repository: GameRepository) {

    /**
     * Gera partidas de pontos corridos para uma lista de times.
     * [legs] = 1 gera turno único; [legs] = 2 gera turno + returno.
     */
    fun generateRoundRobinFixtures(
        season: Int,
        teams: List<Team>,
        competitionType: String,
        startWeek: Int = 1,
        legs: Int = 2
    ): List<Fixture> {
        val fixtures = mutableListOf<Fixture>()
        if (teams.size < 2 || legs <= 0) return fixtures
        require(legs in 1..2) { "Quantidade de turnos suportada: 1 ou 2. Recebido: $legs" }

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

        // Returno (inverted home/away), somente quando o formato de 2 turnos cabe na temporada.
        if (legs == 2) {
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
     *
     * A liga de pontos corridos continua restrita ao país do usuário. A Copa nacional usa
     * clubes desse mesmo país; os torneios continentais usam clubes da confederação correta;
     * e o Super Mundial continua seguindo sua regra própria de temporadas elegíveis.
     *
     * [qualificationStandings] contém o snapshot da temporada anterior. Quando presente,
     * ele ajusta somente a lista transitória usada nos torneios continentais; a Copa nacional
     * e o Super Mundial continuam usando os [Team] reais, e nenhum rating persistido é alterado.
     * Na primeira temporada a lista é vazia e o comportamento legado por rating continua como
     * fallback dos continentais.
     */
    fun generateSeasonFixtures(
        season: Int,
        teams: List<Team>,
        userTeamId: Long,
        userCountry: String = "BRASIL",
        qualificationStandings: List<GlobalLeagueStanding> = emptyList()
    ): List<Fixture> {
        val allFixtures = mutableListOf<Fixture>()
        val userTeam = teams.find { it.id == userTeamId }
        val targetCountry = userTeam?.country ?: userCountry

        // Geração da liga de pontos corridos apenas para o país do time do usuário.
        // Formatos comuns usam o round-robin adaptativo; gigantes divisíveis em grupos iguais
        // jogam turno + returno dentro do grupo, em paralelo, sem ultrapassar 40 semanas.
        // Tamanhos gigantes ainda sem formato balanceado não persistem um calendário impossível:
        // ficam no fallback compacto até uma regra detalhada própria ser definida.
        val groupedTeams = teams.groupBy { Pair(it.country, it.division) }
        for ((key, teamGroup) in groupedTeams) {
            val (country, div) = key
            if (country == targetCountry && teamGroup.size >= 2) {
                val divCode = LeagueSeasonFormat.detailedCompetitionTypeForDivision(div)
                val groupPlan = LeagueSeasonFormat.detailedGroupPlan(teamGroup.size)

                if (groupPlan != null) {
                    LeagueSeasonFormat.buildDetailedGroups(teamGroup).forEach { detailedGroup ->
                        allFixtures.addAll(
                            generateRoundRobinFixtures(
                                season = season,
                                teams = detailedGroup,
                                competitionType = divCode,
                                startWeek = 1,
                                legs = groupPlan.legs
                            )
                        )
                    }
                } else if (LeagueSeasonFormat.fitsCurrentSeason(teamGroup.size)) {
                    val legs = LeagueSeasonFormat.legsForDetailedLeague(teamGroup.size)
                    allFixtures.addAll(
                        generateRoundRobinFixtures(
                            season = season,
                            teams = teamGroup,
                            competitionType = divCode,
                            startWeek = 1,
                            legs = legs
                        )
                    )
                }
            }
        }

        // Copa nacional usa sempre os times/ratings reais. Apenas os continentais recebem a
        // prioridade transitória derivada da temporada anterior.
        val qualificationAwareTeams = ContinentalQualificationRules.applyPreviousSeasonStandings(
            teams = teams,
            standings = qualificationStandings
        )
        allFixtures.addAll(
            CupCompetitionSystem.generateSeasonOpeningFixtures(
                season = season,
                teams = teams,
                userTeamId = userTeamId,
                userCountry = targetCountry,
                continentalTeams = qualificationAwareTeams
            )
        )

        // Super Mundial permanece independente, usa ratings reais e só existe nas temporadas elegíveis.
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
     * Mantido para compatibilidade com chamadas legadas; o calendário novo usa o seletor genérico
     * do CupCompetitionSystem para todas as confederações.
     */
    fun selectLibertadoresTeams(
        brasilSerieATeams: List<Team>,
        brasilCopaTeams: List<Team>,
        remainingCandidates: List<Team> = emptyList()
    ): List<Team> {
        val topSerieA = brasilSerieATeams.sortedByDescending { it.rating }.take(6)

        val topCopa = brasilCopaTeams
            .filter { it !in topSerieA }
            .maxByOrNull { it.rating }
            ?: brasilSerieATeams.filter { it !in topSerieA }.maxByOrNull { it.rating }

        val libBrasil = (topSerieA + listOfNotNull(topCopa)).distinct()

        val selectedTeams = libBrasil.toMutableList()
        val targetCount = 32

        while (selectedTeams.size < targetCount) {
            val candidate = remainingCandidates.firstOrNull { it !in selectedTeams }
            if (candidate != null) {
                selectedTeams.add(candidate)
            } else {
                break
            }
        }

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
