package com.example.usecase

import com.example.data.GameRepository
import com.example.data.Fixture
import com.example.data.GameSave
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
     * Executa a simulação em lote de todas as partidas da CPU para a semana corrente.
     */
    suspend fun simulateCpuMatchesForWeek(season: Int, week: Int): List<Fixture> {
        val unplayedFixtures = repository.getFixturesForWeek(season, week).filter { !it.isPlayed }
        val allTeams = repository.getAllTeams()
        val teamMap = allTeams.associateBy { it.id }

        val updatedFixtures = mutableListOf<Fixture>()

        for (fixture in unplayedFixtures) {
            val homeTeam = teamMap[fixture.homeTeamId] ?: com.example.data.Team(id = fixture.homeTeamId, name = "Time A", city = "Cidade", state = "BR", division = 1, rating = 50)
            val awayTeam = teamMap[fixture.awayTeamId] ?: com.example.data.Team(id = fixture.awayTeamId, name = "Time B", city = "Cidade", state = "BR", division = 1, rating = 50)

            val isRivalry = (homeTeam.rivalTeamId == awayTeam.id)
            val seed = (season * 1000L + week * 100L + fixture.id)
            val (homeScore, awayScore) = com.example.data.GameEngine.simulateMatchInstant(
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                isRivalry = isRivalry,
                randomSeed = seed
            )

            var homePen: Int? = null
            var awayPen: Int? = null
            if (homeScore == awayScore && (fixture.competitionType == "CUP" || fixture.competitionType == "CONTINENTAL_T1" || fixture.competitionType == "CONTINENTAL_T2" || fixture.competitionType == "WORLD_CUP")) {
                val hP = Random.nextInt(3, 6)
                var aP = Random.nextInt(3, 6)
                if (hP == aP) aP += 1
                homePen = hP
                awayPen = aP
            }

            val updated = fixture.copy(
                homeScore = homeScore,
                awayScore = awayScore,
                homePenalties = homePen,
                awayPenalties = awayPen,
                isPlayed = true
            )
            updatedFixtures.add(updated)
        }

        if (updatedFixtures.isNotEmpty()) {
            repository.updateFixtures(updatedFixtures)
        }

        return updatedFixtures
    }
}
