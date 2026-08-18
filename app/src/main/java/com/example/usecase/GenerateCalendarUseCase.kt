package com.example.usecase

import com.example.data.ContinentalQualificationRules
import com.example.data.CupCompetitionSystem
import com.example.data.EuropeanNewSaveSeedCoordinator
import com.example.data.Fixture
import com.example.data.FixtureScheduleValidator
import com.example.data.GameCalendar
import com.example.data.GameRepository
import com.example.data.GlobalFootballSystem
import com.example.data.GlobalLeagueStanding
import com.example.data.LeagueSeasonFormat
import com.example.data.MatchSlot
import com.example.data.SuperMundialSystem
import com.example.data.Team

/**
 * UseCase responsável por gerar o calendário de jogos, tabelas das divisões e mata-matas.
 */
class GenerateCalendarUseCase(private val repository: GameRepository) {

    /**
     * Gera partidas de pontos corridos para uma lista de times.
     * [legs] = 1 gera turno único; [legs] = 2 gera turno + returno.
     * Ligas usam WEEKEND por padrão; copas podem informar outro [matchSlot].
     */
    fun generateRoundRobinFixtures(
        season: Int,
        teams: List<Team>,
        competitionType: String,
        startWeek: Int = 1,
        legs: Int = 2,
        matchSlot: MatchSlot = MatchSlot.WEEKEND
    ): List<Fixture> {
        val fixtures = mutableListOf<Fixture>()
        if (teams.size < 2 || legs <= 0) return fixtures
        require(legs in 1..2) { "Quantidade de turnos suportada: 1 ou 2. Recebido: $legs" }

        val n = if (teams.size % 2 == 0) teams.size else teams.size + 1
        val teamList = teams.map { it.id }.toMutableList()
        if (teams.size % 2 != 0) {
            teamList.add(-1L)
        }

        val totalRounds = n - 1
        val matchesPerRound = n / 2

        for (round in 0 until totalRounds) {
            val weekNum = startWeek + round
            GameCalendar.requireValidWeek(weekNum)
            for (i in 0 until matchesPerRound) {
                val home = teamList[i]
                val away = teamList[n - 1 - i]

                if (home != -1L && away != -1L && home != 0L && away != 0L) {
                    val (h, a) = if (round % 2 == 0) Pair(home, away) else Pair(away, home)
                    fixtures.add(
                        Fixture(
                            season = season,
                            week = weekNum,
                            matchSlot = matchSlot,
                            homeTeamId = h,
                            awayTeamId = a,
                            competitionType = competitionType,
                            isPlayed = false
                        )
                    )
                }
            }
            val last = teamList.removeAt(teamList.size - 1)
            teamList.add(1, last)
        }

        if (legs == 2) {
            val turnoFixtures = fixtures.toList()
            for (f in turnoFixtures) {
                val returnoWeek = f.week + totalRounds
                GameCalendar.requireValidWeek(returnoWeek)
                fixtures.add(
                    Fixture(
                        season = season,
                        week = returnoWeek,
                        matchSlot = matchSlot,
                        homeTeamId = f.awayTeamId,
                        awayTeamId = f.homeTeamId,
                        competitionType = competitionType,
                        isPlayed = false
                    )
                )
            }
        }

        FixtureScheduleValidator.requireValid(fixtures)
        return fixtures
    }

    suspend fun saveCalendarFixtures(fixtures: List<Fixture>) {
        if (fixtures.isNotEmpty()) repository.saveFixtures(fixtures)
    }

    fun generateSeasonFixtures(
        season: Int,
        teams: List<Team>,
        userTeamId: Long,
        userCountry: String = "BRASIL",
        qualificationStandings: List<GlobalLeagueStanding> = emptyList()
    ): List<Fixture> {
        // Este método é o checkpoint usado por performStartNewGameInternal imediatamente antes da
        // transação inicial. Se não existir dataset FACTUAL + VALIDATED, o coordenador não prepara
        // override algum e o seed procedural permanece exatamente como antes.
        EuropeanNewSaveSeedCoordinator.prepare(repository, teams)

        val allFixtures = mutableListOf<Fixture>()
        val userTeam = teams.find { it.id == userTeamId }
        val targetCountry = userTeam?.country ?: userCountry

        // A liga detalhada ocupa apenas WEEKEND e continua limitada a 40 rodadas, mesmo que a
        // temporada total possua 48 semanas. As datas excedentes são reservadas às copas.
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
                                legs = groupPlan.legs,
                                matchSlot = MatchSlot.WEEKEND
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
                            legs = legs,
                            matchSlot = MatchSlot.WEEKEND
                        )
                    )
                }
            }
        }

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

        allFixtures.addAll(
            SuperMundialSystem.generateGroupStageFixtures(season, teams, userTeamId)
        )

        FixtureScheduleValidator.requireValid(allFixtures)
        return allFixtures.sortedWith(FixtureScheduleValidator.chronologicalComparator())
    }

    fun generateKnockoutFixtures(
        season: Int,
        week: Int,
        teams: List<Team>,
        competitionType: String,
        matchSlot: MatchSlot = MatchSlot.MIDWEEK
    ): List<Fixture> {
        if (teams.isEmpty()) return emptyList()
        GameCalendar.requireValidWeek(week)

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
                    matchSlot = matchSlot,
                    homeTeamId = home.id,
                    awayTeamId = away.id,
                    competitionType = competitionType,
                    isPlayed = false
                )
            )
        }
        FixtureScheduleValidator.requireValid(fixtures)
        return fixtures
    }

    fun generateKnockoutFixturesFromIds(
        season: Int,
        week: Int,
        teamIds: List<Long>,
        competitionType: String,
        matchSlot: MatchSlot = MatchSlot.MIDWEEK
    ): List<Fixture> {
        if (teamIds.isEmpty()) return emptyList()
        GameCalendar.requireValidWeek(week)

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
                    matchSlot = matchSlot,
                    homeTeamId = idList[i * 2],
                    awayTeamId = idList[i * 2 + 1],
                    competitionType = competitionType,
                    isPlayed = false
                )
            )
        }
        FixtureScheduleValidator.requireValid(fixtures)
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
            if (candidate != null) selectedTeams.add(candidate) else break
        }

        var dummyCounter = 1
        while (selectedTeams.size < targetCount) {
            val virtualTeam = GlobalFootballSystem.getVirtualTeam(900_000L + dummyCounter)
            if (selectedTeams.none { it.id == virtualTeam.id }) selectedTeams.add(virtualTeam)
            dummyCounter++
        }

        return selectedTeams
    }
}
