package com.example.usecase

import com.example.data.ContinentalQualificationRules
import com.example.data.CupCompetitionSystem
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.GlobalFootballSystem
import com.example.data.GlobalLeagueStanding
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
     *
     * A liga de pontos corridos continua restrita ao país do usuário. A Copa nacional usa
     * clubes desse mesmo país; os torneios continentais usam clubes da confederação correta;
     * e o Super Mundial continua seguindo sua regra própria de temporadas elegíveis.
     *
     * [qualificationStandings] contém o snapshot da temporada anterior. Quando presente,
     * ele ajusta somente a lista transitória usada na abertura de Copa/continentais; os ratings
     * reais dos clubes persistidos permanecem intactos. Na primeira temporada a lista é vazia
     * e o comportamento legado por rating continua funcionando como fallback.
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

        // Copa nacional e continentais. Somente a fase inicial é criada aqui;
        // as fases seguintes são geradas após os resultados pelo CupCompetitionSystem.
        val qualificationAwareTeams = ContinentalQualificationRules.applyPreviousSeasonStandings(
            teams = teams,
            standings = qualificationStandings
        )
        allFixtures.addAll(
            CupCompetitionSystem.generateSeasonOpeningFixtures(
                season = season,
                teams = qualificationAwareTeams,
                userTeamId = userTeamId,
                userCountry = targetCountry
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
