package com.example.usecase

import com.example.data.Fixture
import com.example.data.GlobalLeagueStanding
import com.example.data.Team
import kotlin.random.Random

/**
 * Produz classificações globais compactas sem persistir milhares de partidas CPU.
 *
 * A liga do país do usuário usa os resultados detalhados já existentes em [Fixture].
 * As demais primeiras divisões são simuladas partida a partida apenas em memória, com
 * resultados determinísticos por temporada/país/confronto. Somente a tabela final é salva.
 */
class GlobalLeagueSimulationUseCase {

    fun buildSeasonStandings(
        season: Int,
        teams: List<Team>,
        detailedFixtures: List<Fixture>,
        detailedCountry: String
    ): List<GlobalLeagueStanding> {
        return teams
            .filter { it.division == 1 }
            .groupBy { it.country }
            .toSortedMap()
            .flatMap { (country, countryTeams) ->
                val actualRows = if (country.equals(detailedCountry, ignoreCase = true)) {
                    buildFromDetailedFixtures(season, countryTeams, detailedFixtures)
                } else {
                    emptyList()
                }

                if (actualRows.isNotEmpty()) {
                    actualRows
                } else {
                    simulateCompactLeague(season, country, countryTeams)
                }
            }
    }

    private data class MutableStats(
        var played: Int = 0,
        var wins: Int = 0,
        var draws: Int = 0,
        var losses: Int = 0,
        var goalsFor: Int = 0,
        var goalsAgainst: Int = 0
    ) {
        val points: Int get() = wins * 3 + draws
        val goalDifference: Int get() = goalsFor - goalsAgainst
    }

    private fun buildFromDetailedFixtures(
        season: Int,
        teams: List<Team>,
        fixtures: List<Fixture>
    ): List<GlobalLeagueStanding> {
        if (teams.size < 2) return emptyList()

        val teamIds = teams.map { it.id }.toSet()
        if (teamIds.size != teams.size) return emptyList()

        val relevant = fixtures.filter {
            it.season == season &&
                it.competitionType in setOf("SERIE_A", "DIV_1") &&
                it.homeTeamId in teamIds &&
                it.awayTeamId in teamIds
        }
        val expectedFixtureCount = teams.size * (teams.size - 1)

        // Nunca transforma uma tabela parcial ou estruturalmente corrompida em verdade
        // histórica. O calendário detalhado é turno + returno: além de N*(N-1) partidas,
        // cada par ordenado casa -> fora deve existir exatamente uma vez.
        if (relevant.size != expectedFixtureCount || relevant.any {
                !it.isPlayed || it.homeScore == null || it.awayScore == null
            } || !hasEveryDirectedPairExactlyOnce(teamIds, relevant)
        ) {
            return emptyList()
        }

        val stats = teams.associate { it.id to MutableStats() }.toMutableMap()
        for (fixture in relevant) {
            val home = stats[fixture.homeTeamId] ?: continue
            val away = stats[fixture.awayTeamId] ?: continue
            val hg = fixture.homeScore ?: continue
            val ag = fixture.awayScore ?: continue
            applyResult(home, away, hg, ag)
        }

        return toStandings(season, teams, stats)
    }

    private fun hasEveryDirectedPairExactlyOnce(
        teamIds: Set<Long>,
        fixtures: List<Fixture>
    ): Boolean {
        val directedPairCounts = fixtures
            .groupingBy { it.homeTeamId to it.awayTeamId }
            .eachCount()

        return teamIds.all { homeId ->
            teamIds.all { awayId ->
                homeId == awayId || directedPairCounts[homeId to awayId] == 1
            }
        }
    }

    private fun simulateCompactLeague(
        season: Int,
        country: String,
        teams: List<Team>
    ): List<GlobalLeagueStanding> {
        if (teams.isEmpty()) return emptyList()
        if (teams.size == 1) {
            return listOf(
                GlobalLeagueStanding(
                    season = season,
                    country = country,
                    division = 1,
                    teamId = teams.single().id,
                    position = 1,
                    points = 0,
                    played = 0,
                    wins = 0,
                    draws = 0,
                    losses = 0,
                    goalsFor = 0,
                    goalsAgainst = 0,
                    goalDifference = 0
                )
            )
        }

        val orderedTeams = teams.sortedBy { it.id }
        val stats = orderedTeams.associate { it.id to MutableStats() }.toMutableMap()

        // Até 20 clubes: turno e returno (máximo 38 jogos por clube).
        // Acima de 20: turno único. Nas primeiras divisões atualmente cadastradas isso chega
        // no máximo a 29 jogos por clube, mantendo o horizonte de 40 semanas.
        val legs = if ((orderedTeams.size - 1) * 2 <= 38) 2 else 1

        for (i in 0 until orderedTeams.lastIndex) {
            for (j in i + 1 until orderedTeams.size) {
                for (leg in 0 until legs) {
                    val homeTeam = if (leg == 0) orderedTeams[i] else orderedTeams[j]
                    val awayTeam = if (leg == 0) orderedTeams[j] else orderedTeams[i]
                    val (homeGoals, awayGoals) = simulateScore(
                        season = season,
                        country = country,
                        homeTeam = homeTeam,
                        awayTeam = awayTeam,
                        leg = leg
                    )
                    val home = stats.getValue(homeTeam.id)
                    val away = stats.getValue(awayTeam.id)
                    applyResult(home, away, homeGoals, awayGoals)
                }
            }
        }

        return toStandings(season, orderedTeams, stats)
    }

    private fun simulateScore(
        season: Int,
        country: String,
        homeTeam: Team,
        awayTeam: Team,
        leg: Int
    ): Pair<Int, Int> {
        val random = Random(
            stableSeed(
                "$season|$country|${homeTeam.id}|${awayTeam.id}|$leg"
            )
        )
        val ratingDiff = (homeTeam.rating + 5) - awayTeam.rating
        val homeExpected = (1.35 + ratingDiff / 28.0).coerceIn(0.25, 3.20)
        val awayExpected = (1.10 - ratingDiff / 32.0).coerceIn(0.20, 2.90)
        return generateGoals(homeExpected, random) to generateGoals(awayExpected, random)
    }

    private fun generateGoals(expected: Double, random: Random): Int {
        val guaranteed = expected.toInt().coerceIn(0, 4)
        var goals = guaranteed
        val fractional = (expected - guaranteed).coerceIn(0.0, 1.0)
        if (random.nextDouble() < fractional) goals++
        if (random.nextDouble() < 0.08) goals++
        return goals.coerceIn(0, 7)
    }

    private fun applyResult(
        home: MutableStats,
        away: MutableStats,
        homeGoals: Int,
        awayGoals: Int
    ) {
        home.played++
        away.played++
        home.goalsFor += homeGoals
        home.goalsAgainst += awayGoals
        away.goalsFor += awayGoals
        away.goalsAgainst += homeGoals

        when {
            homeGoals > awayGoals -> {
                home.wins++
                away.losses++
            }
            awayGoals > homeGoals -> {
                away.wins++
                home.losses++
            }
            else -> {
                home.draws++
                away.draws++
            }
        }
    }

    private fun toStandings(
        season: Int,
        teams: List<Team>,
        stats: Map<Long, MutableStats>
    ): List<GlobalLeagueStanding> {
        return teams
            .sortedWith(
                compareByDescending<Team> { stats[it.id]?.points ?: 0 }
                    .thenByDescending { stats[it.id]?.wins ?: 0 }
                    .thenByDescending { stats[it.id]?.goalDifference ?: 0 }
                    .thenByDescending { stats[it.id]?.goalsFor ?: 0 }
                    .thenByDescending { it.rating }
                    .thenBy { it.id }
            )
            .mapIndexed { index, team ->
                stats.getValue(team.id).toStanding(season, team, index + 1)
            }
    }

    private fun MutableStats.toStanding(
        season: Int,
        team: Team,
        position: Int
    ): GlobalLeagueStanding {
        return GlobalLeagueStanding(
            season = season,
            country = team.country,
            division = team.division,
            teamId = team.id,
            position = position,
            points = points,
            played = played,
            wins = wins,
            draws = draws,
            losses = losses,
            goalsFor = goalsFor,
            goalsAgainst = goalsAgainst,
            goalDifference = goalDifference
        )
    }

    private fun stableSeed(value: String): Long {
        var hash = 1125899906842597L
        value.forEach { ch ->
            hash = hash * 31L + ch.code
        }
        return hash
    }
}
