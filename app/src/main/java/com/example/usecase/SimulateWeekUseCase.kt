package com.example.usecase

import com.example.data.CompetitionRules
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.Player
import kotlin.math.max
import kotlin.random.Random

/** Medição do caminho quente de fixtures CPU sem introduzir logging na build final. */
data class CpuWeekSimulationMetrics(
    val totalMillis: Long,
    val fixtureCount: Int,
    val teamCount: Int,
    val playersReadCount: Int,
    val rosterQueryCount: Int
)

/**
 * UseCase responsável por gerenciar a simulação de rodadas e cálculo de partidas.
 */
class SimulateWeekUseCase(
    private val repository: GameRepository,
    private val metricsSink: (CpuWeekSimulationMetrics) -> Unit = {}
) {

    data class SimulationResult(
        val season: Int,
        val week: Int,
        val competitionName: String,
        val matchSummary: String,
        val isUserMatch: Boolean
    )

    private data class PlannedCpuFixture(
        val fixture: Fixture,
        val scorerIds: List<Long>,
        val participantTeamById: Map<Long, Long>
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
     * O placar, os gols e as aparições dos participantes são confirmados na mesma transação.
     * Uma chamada repetida ou concorrente só aplica estatísticas aos fixtures que continuarem
     * `isPlayed=false` quando a transação adquirir o banco, tornando a agregação idempotente.
     */
    suspend fun simulateCpuMatchesForWeek(
        season: Int,
        week: Int,
        excludedTeamId: Long? = null
    ): List<Fixture> {
        val startedNanos = System.nanoTime()
        val unplayedFixtures = repository.getFixturesForWeek(season, week).filter { fixture ->
            !fixture.isPlayed &&
                (excludedTeamId == null ||
                    (fixture.homeTeamId != excludedTeamId && fixture.awayTeamId != excludedTeamId))
        }
        if (unplayedFixtures.isEmpty()) {
            metricsSink(
                CpuWeekSimulationMetrics(
                    totalMillis = nanosToMillis(System.nanoTime() - startedNanos),
                    fixtureCount = 0,
                    teamCount = 0,
                    playersReadCount = 0,
                    rosterQueryCount = 0
                )
            )
            return emptyList()
        }

        val teamMap = repository.getAllTeams().associateBy { it.id }
        val participatingTeamIds = unplayedFixtures
            .flatMap { listOf(it.homeTeamId, it.awayTeamId) }
            .distinct()
        val plannedRosterLoad = loadRostersByTeamIds(participatingTeamIds)
        val rostersByTeam = plannedRosterLoad.first
        var playersReadCount = plannedRosterLoad.second
        var rosterQueryCount = plannedRosterLoad.third

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
            val homeRoster = rostersByTeam[fixture.homeTeamId].orEmpty()
            val awayRoster = rostersByTeam[fixture.awayTeamId].orEmpty()
            val homeParticipants = selectParticipants(homeRoster)
            val awayParticipants = selectParticipants(awayRoster)

            val isRivalry = homeTeam.rivalTeamId == awayTeam.id
            val seed = season * 1000L + week * 100L + fixture.id
            val (rawHomeScore, rawAwayScore) = com.example.data.GameEngine.simulateMatchInstant(
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                isRivalry = isRivalry,
                randomSeed = seed
            )

            val homeScore = if (homeParticipants.isEmpty()) 0 else rawHomeScore
            val awayScore = if (awayParticipants.isEmpty()) 0 else rawAwayScore

            val (homePenalties, awayPenalties) = CompetitionRules.resolvePenaltiesIfNeeded(
                fixture = fixture,
                homeScore = homeScore,
                awayScore = awayScore
            )

            val scorerIds = buildList {
                addAll(
                    selectScorers(
                        players = homeParticipants,
                        goals = homeScore,
                        random = Random(seed xor HOME_SCORER_SALT)
                    )
                )
                addAll(
                    selectScorers(
                        players = awayParticipants,
                        goals = awayScore,
                        random = Random(seed xor AWAY_SCORER_SALT)
                    )
                )
            }
            val participantTeamById = buildMap {
                homeParticipants.forEach { put(it.id, fixture.homeTeamId) }
                awayParticipants.forEach { put(it.id, fixture.awayTeamId) }
            }

            PlannedCpuFixture(
                fixture = fixture.copy(
                    homeScore = homeScore,
                    awayScore = awayScore,
                    homePenalties = homePenalties,
                    awayPenalties = awayPenalties,
                    isPlayed = true
                ),
                scorerIds = scorerIds,
                participantTeamById = participantTeamById
            )
        }

        val committedFixtures = repository.withTransaction {
            val currentById = repository.getFixturesForWeek(season, week).associateBy { it.id }
            val pendingPlans = plans.filter { plan ->
                currentById[plan.fixture.id]?.isPlayed == false
            }
            if (pendingPlans.isEmpty()) return@withTransaction emptyList()

            // Releia em lote os elencos já dentro da transação, depois de adquirir o banco. Um
            // atleta transferido, lesionado ou suspenso depois do planejamento invalida somente o
            // fixture que dependia daquele snapshot; assim nunca persistimos o placar sem seus
            // participantes. Antes este trecho fazia uma SELECT por clube dentro da transação.
            val committedTeamIds = pendingPlans
                .flatMap { listOf(it.fixture.homeTeamId, it.fixture.awayTeamId) }
                .distinct()
            val committedRosterLoad = loadRostersByTeamIds(committedTeamIds)
            val currentPlayersById = committedRosterLoad.first.values
                .asSequence()
                .flatten()
                .associateByTo(mutableMapOf()) { it.id }
            playersReadCount += committedRosterLoad.second
            rosterQueryCount += committedRosterLoad.third

            val committedPlans = pendingPlans.filter { plan ->
                plan.participantTeamById.all { (playerId, plannedTeamId) ->
                    currentPlayersById[playerId]?.let { current ->
                        current.teamId == plannedTeamId &&
                            current.injuryWeeksRemaining == 0 &&
                            current.suspensionWeeksRemaining == 0
                    } == true
                }
            }
            if (committedPlans.isEmpty()) return@withTransaction emptyList()

            val committedFixtures = committedPlans.map { it.fixture }
            repository.updateFixtures(committedFixtures)

            val goalCounts = committedPlans
                .flatMap { it.scorerIds }
                .groupingBy { it }
                .eachCount()
            val appearanceCounts = committedPlans
                .flatMap { plan -> plan.participantTeamById.keys }
                .groupingBy { it }
                .eachCount()
            val affectedPlayerIds = (goalCounts.keys + appearanceCounts.keys).toSet()

            if (affectedPlayerIds.isNotEmpty()) {
                val updatedPlayers = affectedPlayerIds.mapNotNull { playerId ->
                    currentPlayersById[playerId]?.let { persisted ->
                        val goals = goalCounts[playerId] ?: 0
                        val appearances = appearanceCounts[playerId] ?: 0
                        persisted.copy(
                            gols = persisted.gols + goals,
                            careerGoals = persisted.careerGoals + goals,
                            careerApps = persisted.careerApps + appearances,
                            partidasDisputadas = persisted.partidasDisputadas + appearances
                        )
                    }
                }
                if (updatedPlayers.isNotEmpty()) {
                    repository.updatePlayers(updatedPlayers)
                }
            }

            committedFixtures
        }

        metricsSink(
            CpuWeekSimulationMetrics(
                totalMillis = nanosToMillis(System.nanoTime() - startedNanos),
                fixtureCount = committedFixtures.size,
                teamCount = participatingTeamIds.size,
                playersReadCount = playersReadCount,
                rosterQueryCount = rosterQueryCount
            )
        )
        return committedFixtures
    }

    /**
     * Carrega todos os elencos relevantes com no máximo uma consulta por bloco de 800 clubes.
     * O limite fica abaixo dos 999 bind parameters presentes em builds SQLite Android antigas.
     */
    private suspend fun loadRostersByTeamIds(
        teamIds: Collection<Long>
    ): Triple<Map<Long, List<Player>>, Int, Int> {
        val distinctIds = teamIds.distinct()
        if (distinctIds.isEmpty()) return Triple(emptyMap(), 0, 0)

        val players = ArrayList<Player>()
        var queryCount = 0
        distinctIds.chunked(CPU_ROSTER_QUERY_CHUNK_SIZE).forEach { chunk ->
            players.addAll(repository.db.playerBatchDao().getPlayersByTeamIds(chunk))
            queryCount++
        }
        return Triple(
            players.groupBy { requireNotNull(it.teamId) },
            players.size,
            queryCount
        )
    }

    private fun selectParticipants(players: List<Player>): List<Player> =
        players
            .asSequence()
            .filter { it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0 }
            .sortedWith(
                compareByDescending<Player> { it.isStarter }
                    .thenByDescending { it.force }
                    .thenBy { it.id }
            )
            .take(11)
            .toList()

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

    private fun nanosToMillis(nanos: Long): Long = nanos / 1_000_000L

    private companion object {
        const val CPU_ROSTER_QUERY_CHUNK_SIZE = 800
        const val HOME_SCORER_SALT = 0x6A09E667F3BCC909L
        const val AWAY_SCORER_SALT = 0x3C6EF372FE94F82BL
    }
}
