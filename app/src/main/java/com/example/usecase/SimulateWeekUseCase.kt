package com.example.usecase

import com.example.data.CompetitionRules
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.Player
import kotlin.math.max
import kotlin.random.Random

/**
 * UseCase responsável por gerenciar a simulação de rodadas e cálculo de partidas.
 */
class SimulateWeekUseCase(private val repository: GameRepository) {

    data class SimulationResult(
        val season: Int,
        val week: Int,
        val competitionName: String,
        val matchSummary: String,
        val isUserMatch: Boolean
    )

    private data class PlannedCpuFixture(
        val fixture: Fixture,
        val scorerIds: List<Long>
    )

    /**
     * Simula o resultado de uma partida CPU versus CPU com variação estocástica e vantagem de casa.
     */
    fun calculateCpuMatchScore(homeRating: Int, awayRating: Int): Pair<Int, Int> {
        val homeAdvantage = 5
        val adjustedHomeRating = homeRating + homeAdvantage

        val ratingDiff = adjustedHomeRating - awayRating
        val baseHomeExp = max(0.5, 1.4 + (ratingDiff / 25.0))
        val baseAwayExp = max(0.3, 1.1 - (ratingDiff / 25.0))

        val homeGoals = generateGoals(baseHomeExp)
        val awayGoals = generateGoals(baseAwayExp)

        return Pair(homeGoals, awayGoals)
    }

    private fun generateGoals(lambda: Double): Int {
        val rand = Random.nextDouble()
        return when {
            rand < 0.35 -> (lambda * 0.8).toInt().coerceIn(0, 5)
            rand < 0.70 -> (lambda * 1.1).toInt().coerceIn(0, 5)
            rand < 0.90 -> (lambda * 1.4 + 1).toInt().coerceIn(0, 6)
            else -> (lambda * 1.8 + 2).toInt().coerceIn(0, 7)
        }
    }

    /**
     * Executa a simulação em lote das partidas da CPU para a semana corrente.
     *
     * O placar e os gols sazonais/cumulativos dos artilheiros são confirmados na mesma transação.
     * Uma chamada repetida ou concorrente só aplica estatísticas aos fixtures que continuarem
     * `isPlayed=false` quando a transação adquirir o banco, tornando a agregação idempotente.
     */
    suspend fun simulateCpuMatchesForWeek(
        season: Int,
        week: Int,
        excludedTeamId: Long? = null
    ): List<Fixture> {
        val unplayedFixtures = repository.getFixturesForWeek(season, week).filter { fixture ->
            !fixture.isPlayed &&
                (excludedTeamId == null ||
                    (fixture.homeTeamId != excludedTeamId && fixture.awayTeamId != excludedTeamId))
        }
        if (unplayedFixtures.isEmpty()) return emptyList()

        val teamMap = repository.getAllTeams().associateBy { it.id }
        // Uma leitura global evita N+1 por fixture; o mesmo snapshot alimenta apenas a escolha
        // determinística dos artilheiros. A confirmação dos contadores é relida por id na transação.
        val rostersByTeam = repository.getAllPlayers().groupBy { it.teamId }

        val plans = unplayedFixtures.map { fixture ->
            val homeTeam = teamMap[fixture.homeTeamId] ?: com.example.data.Team(
                id = fixture.homeTeamId,
                name = "Time A",
                city = "Cidade",
                state = "BR",
                division = 1,
                rating = 50
            )
            val awayTeam = teamMap[fixture.awayTeamId] ?: com.example.data.Team(
                id = fixture.awayTeamId,
                name = "Time B",
                city = "Cidade",
                state = "BR",
                division = 1,
                rating = 50
            )

            val isRivalry = homeTeam.rivalTeamId == awayTeam.id
            val seed = season * 1000L + week * 100L + fixture.id
            val (homeScore, awayScore) = com.example.data.GameEngine.simulateMatchInstant(
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                isRivalry = isRivalry,
                randomSeed = seed
            )

            val (homePenalties, awayPenalties) = CompetitionRules.resolvePenaltiesIfNeeded(
                fixture = fixture,
                homeScore = homeScore,
                awayScore = awayScore
            )

            val scorerIds = buildList {
                addAll(
                    selectScorers(
                        players = rostersByTeam[fixture.homeTeamId].orEmpty(),
                        goals = homeScore,
                        random = Random(seed xor HOME_SCORER_SALT)
                    )
                )
                addAll(
                    selectScorers(
                        players = rostersByTeam[fixture.awayTeamId].orEmpty(),
                        goals = awayScore,
                        random = Random(seed xor AWAY_SCORER_SALT)
                    )
                )
            }

            PlannedCpuFixture(
                fixture = fixture.copy(
                    homeScore = homeScore,
                    awayScore = awayScore,
                    homePenalties = homePenalties,
                    awayPenalties = awayPenalties,
                    isPlayed = true
                ),
                scorerIds = scorerIds
            )
        }

        return repository.withTransaction {
            val currentById = repository.getFixturesForWeek(season, week).associateBy { it.id }
            val committedPlans = plans.filter { plan ->
                currentById[plan.fixture.id]?.isPlayed == false
            }
            if (committedPlans.isEmpty()) return@withTransaction emptyList()

            val committedFixtures = committedPlans.map { it.fixture }
            repository.updateFixtures(committedFixtures)

            val goalCounts = committedPlans
                .flatMap { it.scorerIds }
                .groupingBy { it }
                .eachCount()

            if (goalCounts.isNotEmpty()) {
                val updatedScorers = goalCounts.mapNotNull { (playerId, goals) ->
                    repository.getPlayer(playerId)?.let { persisted ->
                        persisted.copy(
                            gols = persisted.gols + goals,
                            careerGoals = persisted.careerGoals + goals
                        )
                    }
                }
                if (updatedScorers.isNotEmpty()) {
                    repository.updatePlayers(updatedScorers)
                }
            }

            committedFixtures
        }
    }

    /** Usa a mesma ponderação por posição/força do motor detalhado, mas com RNG derivado do fixture. */
    private fun selectScorers(players: List<Player>, goals: Int, random: Random): List<Long> {
        if (goals <= 0) return emptyList()
        val active = players.filter { it.position != "GOL" }.ifEmpty { players }
        if (active.isEmpty()) return emptyList()

        val weights = active.map { player ->
            when (player.position) {
                "ATA" -> player.force * 5.0
                "MEI" -> player.force * 3.0
                "VOL" -> player.force * 1.2
                "LAT" -> player.force * 0.8
                "ZAG" -> player.force * 0.5
                else -> 1.0
            }.coerceAtLeast(1.0)
        }
        val totalWeight = weights.sum()

        return List(goals) {
            var roll = random.nextDouble() * totalWeight
            var selected = active.last()
            for (index in active.indices) {
                roll -= weights[index]
                if (roll <= 0.0) {
                    selected = active[index]
                    break
                }
            }
            selected.id
        }
    }

    private companion object {
        const val HOME_SCORER_SALT = 0x6A09E667F3BCC909L
        const val AWAY_SCORER_SALT = 0x3C6EF372FE94F82BL
    }
}
