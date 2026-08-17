package com.example.usecase

import com.example.data.Fixture
import com.example.data.GlobalLeagueStanding
import com.example.data.Team
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Produz classificações globais compactas sem persistir milhares de partidas CPU.
 *
 * A liga do país do usuário usa os resultados detalhados já existentes em [Fixture].
 * As demais primeiras divisões recebem uma simulação agregada determinística baseada
 * em força do clube + variação sazonal. O resultado final é estável para a mesma seed.
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
        val relevant = fixtures.filter {
            it.season == season &&
                it.isPlayed &&
                it.competitionType in setOf("SERIE_A", "DIV_1") &&
                it.homeTeamId in teamIds &&
                it.awayTeamId in teamIds
        }
        if (relevant.isEmpty()) return emptyList()

        val stats = teams.associate { it.id to MutableStats() }.toMutableMap()
        for (fixture in relevant) {
            val home = stats[fixture.homeTeamId] ?: continue
            val away = stats[fixture.awayTeamId] ?: continue
            val hg = fixture.homeScore ?: continue
            val ag = fixture.awayScore ?: continue

            home.played++
            away.played++
            home.goalsFor += hg
            home.goalsAgainst += ag
            away.goalsFor += ag
            away.goalsAgainst += hg

            when {
                hg > ag -> {
                    home.wins++
                    away.losses++
                }
                ag > hg -> {
                    away.wins++
                    home.losses++
                }
                else -> {
                    home.draws++
                    away.draws++
                }
            }
        }

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
                val row = stats.getValue(team.id)
                row.toStanding(season, team, index + 1)
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

        // Mantém o resumo barato mesmo em ligas muito grandes. O objetivo aqui é produzir
        // uma classificação sazonal coerente para o ecossistema global, não reproduzir
        // cada rodada CPU fora do país ativo.
        val played = minOf(38, (teams.size - 1) * 2)
        val simulated = teams.associateWith { team ->
            val random = Random(stableSeed(season, country, team.id))
            val strengthFactor = ((team.rating - 50) / 170.0).coerceIn(-0.18, 0.28)
            val winRate = (0.34 + strengthFactor + random.nextDouble(-0.08, 0.08))
                .coerceIn(0.12, 0.72)
            val drawRate = (0.24 + random.nextDouble(-0.05, 0.05))
                .coerceIn(0.12, 0.34)

            var wins = (played * winRate).roundToInt().coerceIn(0, played)
            var draws = (played * drawRate).roundToInt().coerceIn(0, played - wins)
            if (wins + draws > played) {
                draws = played - wins
            }
            val losses = played - wins - draws

            val attackingNoise = random.nextInt(0, maxOf(2, played / 3 + 1))
            val defensiveNoise = random.nextInt(0, maxOf(2, played / 3 + 1))
            val goalsFor = (wins * 2 + draws + attackingNoise + team.rating / 10)
                .coerceAtLeast(0)
            val goalsAgainst = (losses * 2 + draws + defensiveNoise + (100 - team.rating) / 12)
                .coerceAtLeast(0)

            MutableStats(
                played = played,
                wins = wins,
                draws = draws,
                losses = losses,
                goalsFor = goalsFor,
                goalsAgainst = goalsAgainst
            )
        }

        return teams
            .sortedWith(
                compareByDescending<Team> { simulated.getValue(it).points }
                    .thenByDescending { simulated.getValue(it).wins }
                    .thenByDescending { simulated.getValue(it).goalDifference }
                    .thenByDescending { simulated.getValue(it).goalsFor }
                    .thenByDescending { it.rating }
                    .thenBy { it.id }
            )
            .mapIndexed { index, team ->
                simulated.getValue(team).toStanding(season, team, index + 1)
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

    private fun stableSeed(season: Int, country: String, teamId: Long): Long {
        var hash = 1125899906842597L
        "$season|$country|$teamId".forEach { ch ->
            hash = hash * 31L + ch.code
        }
        return hash
    }
}
