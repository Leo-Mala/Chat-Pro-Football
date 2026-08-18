package com.example.data

import kotlin.random.Random

/**
 * Motor dedicado das competições UEFA no formato 2026/27.
 *
 * Regras estruturais implementadas:
 * - Champions League / Europa League: 36 clubes, oito adversários, quatro jogos em casa e quatro fora;
 * - Conference League: 36 clubes, seis adversários, três jogos em casa e três fora;
 * - liga única: 1..8 -> oitavas, 9..24 -> playoff, 25..36 -> eliminados;
 * - playoff, oitavas, quartas e semifinais em ida/volta; final em jogo único;
 * - desempates da liga até os critérios suportados pelo domínio atual;
 * - sorteio/potes determinístico e calendário compatível com as 48 semanas da carreira.
 *
 * Disciplinary points e club coefficient ainda não são persistidos no save. Portanto os nove
 * primeiros critérios esportivos disponíveis são aplicados e [teamId] é usado somente como último
 * fallback determinístico. [Team.rating] nunca é usado como coeficiente UEFA neste motor.
 */
object UefaCompetitionSystem {
    const val CHAMPIONS_LEAGUE = "UEFA_CL"
    const val EUROPA_LEAGUE = "UEFA_EL"
    const val CONFERENCE_LEAGUE = "UEFA_ECL"

    const val FIELD_SIZE = 36
    const val MATCHES_PER_MATCHDAY = 18

    val CHAMPIONS_EUROPA_LEAGUE_WEEKS: List<Int> = listOf(2, 5, 8, 11, 14, 17, 19, 21)
    val CONFERENCE_LEAGUE_WEEKS: List<Int> = listOf(2, 5, 8, 11, 14, 17)

    const val PLAYOFF_LEG_1_WEEK = 28
    const val PLAYOFF_LEG_2_WEEK = 29
    const val ROUND_OF_16_LEG_1_WEEK = 30
    const val ROUND_OF_16_LEG_2_WEEK = 31
    const val QUARTERFINAL_LEG_1_WEEK = 34
    const val QUARTERFINAL_LEG_2_WEEK = 35
    const val SEMIFINAL_LEG_1_WEEK = 37
    const val SEMIFINAL_LEG_2_WEEK = 38
    const val FINAL_WEEK = 40

    enum class Phase {
        LEAGUE_PHASE,
        KNOCKOUT_PLAYOFF,
        ROUND_OF_16,
        QUARTERFINAL,
        SEMIFINAL,
        FINAL
    }

    data class LeagueRow(
        val teamId: Long,
        var points: Int = 0,
        var goalDifference: Int = 0,
        var goalsFor: Int = 0,
        var awayGoals: Int = 0,
        var wins: Int = 0,
        var awayWins: Int = 0,
        var opponentPoints: Int = 0,
        var opponentGoalDifference: Int = 0,
        var opponentGoalsFor: Int = 0
    )

    private data class NodeEdge(val a: Int, val b: Int) {
        init { require(a != b) }
        val key: EdgeKey = EdgeKey.of(a, b)
        fun other(node: Int): Int = if (node == a) b else a
    }

    private data class EdgeKey(val low: Int, val high: Int) {
        companion object {
            fun of(a: Int, b: Int): EdgeKey =
                if (a < b) EdgeKey(a, b) else EdgeKey(b, a)
        }
    }

    fun isUefaSeason(fixtures: List<Fixture>): Boolean =
        fixtures.any { it.competitionType in competitionCodes }

    fun phaseFor(competitionType: String, week: Int): Phase? {
        if (competitionType !in competitionCodes) return null
        return when {
            week in leagueWeeksFor(competitionType) -> Phase.LEAGUE_PHASE
            week in PLAYOFF_LEG_1_WEEK..PLAYOFF_LEG_2_WEEK -> Phase.KNOCKOUT_PLAYOFF
            week in ROUND_OF_16_LEG_1_WEEK..ROUND_OF_16_LEG_2_WEEK -> Phase.ROUND_OF_16
            week in QUARTERFINAL_LEG_1_WEEK..QUARTERFINAL_LEG_2_WEEK -> Phase.QUARTERFINAL
            week in SEMIFINAL_LEG_1_WEEK..SEMIFINAL_LEG_2_WEEK -> Phase.SEMIFINAL
            week == FINAL_WEEK -> Phase.FINAL
            else -> null
        }
    }

    fun generateOpeningFixtures(
        season: Int,
        fields: UefaQualificationRules.LeaguePhaseFields
    ): List<Fixture> {
        val fixtures = mutableListOf<Fixture>()
        if (fields.championsLeague.size == FIELD_SIZE) {
            fixtures += generateLeaguePhase(
                season,
                fields.championsLeague.map { it.team },
                CHAMPIONS_LEAGUE
            )
        }
        if (fields.europaLeague.size == FIELD_SIZE) {
            fixtures += generateLeaguePhase(
                season,
                fields.europaLeague.map { it.team },
                EUROPA_LEAGUE
            )
        }
        if (fields.conferenceLeague.size == FIELD_SIZE) {
            fixtures += generateLeaguePhase(
                season,
                fields.conferenceLeague.map { it.team },
                CONFERENCE_LEAGUE
            )
        }
        FixtureScheduleValidator.requireValid(fixtures)
        return fixtures
    }

    internal fun drawPots(teams: List<Team>, competitionType: String): List<List<Team>> {
        if (teams.distinctBy { it.id }.size != FIELD_SIZE) return emptyList()
        val potCount = if (competitionType == CONFERENCE_LEAGUE) 6 else 4
        val potSize = FIELD_SIZE / potCount

        // A ordem chega da camada tipada de acesso. Enquanto club coefficient não existir no save,
        // não inventamos um coeficiente a partir de rating. A mecânica dos potes/oponentes permanece
        // isolada para receber o ranking oficial assim que ele passar a ser dado persistido.
        return teams.distinctBy { it.id }.chunked(potSize)
    }

    internal fun generateLeaguePhase(
        season: Int,
        teams: List<Team>,
        competitionType: String
    ): List<Fixture> {
        require(competitionType in competitionCodes)
        val pots = drawPots(teams, competitionType)
        if (pots.isEmpty()) return emptyList()

        val flattened = pots.flatten()
        val edges = if (competitionType == CONFERENCE_LEAGUE) {
            buildConferenceGraph()
        } else {
            buildChampionsEuropaGraph()
        }
        val weeks = leagueWeeksFor(competitionType)
        val matchdays = factorIntoMatchdays(edges, weeks.size)
        val orientation = orientMatchdays(matchdays, competitionType)

        val fixtures = mutableListOf<Fixture>()
        matchdays.forEachIndexed { matchdayIndex, matchday ->
            val week = weeks[matchdayIndex]
            matchday.forEach { edge ->
                val direction = requireNotNull(orientation[edge.key])
                fixtures += Fixture(
                    season = season,
                    week = week,
                    matchSlot = MatchSlot.MIDWEEK,
                    homeTeamId = flattened[direction.first].id,
                    awayTeamId = flattened[direction.second].id,
                    competitionType = competitionType
                )
            }
        }

        val expected = FIELD_SIZE * weeks.size / 2
        check(fixtures.size == expected) {
            "$competitionType deveria gerar $expected jogos de liga, gerou ${fixtures.size}."
        }
        validateLeaguePhaseShape(fixtures, flattened, competitionType)
        FixtureScheduleValidator.requireValid(fixtures)
        return fixtures
    }

    private fun buildChampionsEuropaGraph(): List<NodeEdge> {
        val potSize = 9
        val edges = linkedMapOf<EdgeKey, NodeEdge>()
        fun add(a: Int, b: Int) {
            val edge = NodeEdge(a, b)
            edges.putIfAbsent(edge.key, edge)
        }

        // Dois adversários dentro do próprio pote. Os passos abaixo formam ciclos de grau 2 e,
        // combinados com os offsets entre potes, resultam em um grafo 8-regular fatorável em oito
        // jornadas completas de 18 partidas.
        val ownSteps = listOf(2, 2, 2, 4)
        ownSteps.forEachIndexed { pot, step ->
            repeat(potSize) { index ->
                add(pot * potSize + index, pot * potSize + ((index + step) % potSize))
            }
        }

        val offsets = mapOf(
            (0 to 1) to listOf(0, 5),
            (0 to 2) to listOf(5, 6),
            (0 to 3) to listOf(4, 3),
            (1 to 2) to listOf(8, 0),
            (1 to 3) to listOf(8, 7),
            (2 to 3) to listOf(7, 4)
        )
        offsets.forEach { (potPair, pairOffsets) ->
            val (firstPot, secondPot) = potPair
            pairOffsets.forEach { offset ->
                repeat(potSize) { index ->
                    add(
                        firstPot * potSize + index,
                        secondPot * potSize + ((index + offset) % potSize)
                    )
                }
            }
        }

        check(edges.size == 144)
        requireRegularGraph(edges.values.toList(), degree = 8)
        return edges.values.sortedWith(compareBy<NodeEdge> { it.key.low }.thenBy { it.key.high })
    }

    private fun buildConferenceGraph(): List<NodeEdge> {
        val potCount = 6
        val potSize = 6
        val edges = linkedMapOf<EdgeKey, NodeEdge>()
        fun add(a: Int, b: Int) {
            val edge = NodeEdge(a, b)
            edges.putIfAbsent(edge.key, edge)
        }

        // Um adversário do próprio pote.
        repeat(potCount) { pot ->
            repeat(potSize / 2) { index ->
                add(pot * potSize + index, pot * potSize + index + potSize / 2)
            }
        }

        // Um adversário de cada um dos outros cinco potes.
        for (firstPot in 0 until potCount) {
            for (secondPot in firstPot + 1 until potCount) {
                repeat(potSize) { index ->
                    add(firstPot * potSize + index, secondPot * potSize + index)
                }
            }
        }

        check(edges.size == 108)
        requireRegularGraph(edges.values.toList(), degree = 6)
        return edges.values.sortedWith(compareBy<NodeEdge> { it.key.low }.thenBy { it.key.high })
    }

    private fun requireRegularGraph(edges: List<NodeEdge>, degree: Int) {
        repeat(FIELD_SIZE) { node ->
            check(edges.count { it.a == node || it.b == node } == degree) {
                "Nó UEFA $node não possui grau $degree."
            }
        }
    }

    private fun factorIntoMatchdays(
        edges: List<NodeEdge>,
        roundCount: Int
    ): List<List<NodeEdge>> {
        val remaining = edges.toMutableList()
        val result = mutableListOf<List<NodeEdge>>()

        repeat(roundCount) {
            val matching = findPerfectMatching(remaining)
                ?: error("Não foi possível fatorar o sorteio UEFA em jornadas sem conflito.")
            check(matching.size == MATCHES_PER_MATCHDAY)
            result += matching
            val used = matching.map { it.key }.toSet()
            remaining.removeAll { it.key in used }
        }
        check(remaining.isEmpty()) { "Restaram ${remaining.size} jogos UEFA sem jornada." }
        return result
    }

    private fun findPerfectMatching(edges: List<NodeEdge>): List<NodeEdge>? {
        val unmatched = (0 until FIELD_SIZE).toMutableSet()
        val selected = mutableListOf<NodeEdge>()

        fun recurse(): Boolean {
            if (unmatched.isEmpty()) return true

            val node = unmatched.minWithOrNull(
                compareBy<Int> { candidate ->
                    edges.count { edge ->
                        (edge.a == candidate || edge.b == candidate) &&
                            edge.a in unmatched && edge.b in unmatched
                    }
                }.thenBy { it }
            ) ?: return false

            val candidates = edges
                .asSequence()
                .filter { (it.a == node || it.b == node) && it.a in unmatched && it.b in unmatched }
                .sortedWith(compareBy<NodeEdge> { it.key.low }.thenBy { it.key.high })
                .toList()

            for (edge in candidates) {
                unmatched.remove(edge.a)
                unmatched.remove(edge.b)
                selected += edge
                if (recurse()) return true
                selected.removeAt(selected.lastIndex)
                unmatched += edge.a
                unmatched += edge.b
            }
            return false
        }

        return if (recurse()) selected.toList() else null
    }

    private fun orientMatchdays(
        matchdays: List<List<NodeEdge>>,
        competitionType: String
    ): Map<EdgeKey, Pair<Int, Int>> {
        val blocks: List<List<Int>> = if (competitionType == CONFERENCE_LEAGUE) {
            listOf(listOf(0, 1), listOf(2, 3), listOf(4, 5))
        } else {
            listOf(listOf(0, 1), listOf(2, 3, 4, 5), listOf(6, 7))
        }

        return buildMap {
            blocks.forEach { roundIndexes ->
                val blockEdges = roundIndexes.flatMap { matchdays[it] }
                putAll(orientEvenDegreeGraph(blockEdges))
            }
        }
    }

    /** Orienta um grafo de grau par por circuitos de Euler, dando metade casa/metade fora. */
    private fun orientEvenDegreeGraph(edges: List<NodeEdge>): Map<EdgeKey, Pair<Int, Int>> {
        val byKey = edges.associateBy { it.key }
        val adjacency = Array(FIELD_SIZE) { mutableListOf<EdgeKey>() }
        edges.forEach { edge ->
            adjacency[edge.a] += edge.key
            adjacency[edge.b] += edge.key
        }
        adjacency.forEach { it.sortWith(compareBy<EdgeKey> { key -> key.low }.thenBy { it.high }) }

        val unused = byKey.keys.toMutableSet()
        val directions = mutableMapOf<EdgeKey, Pair<Int, Int>>()

        while (unused.isNotEmpty()) {
            val start = (0 until FIELD_SIZE).first { node -> adjacency[node].any { it in unused } }
            val stack = mutableListOf(start)
            val circuit = mutableListOf<Int>()

            while (stack.isNotEmpty()) {
                val node = stack.last()
                val key = adjacency[node].firstOrNull { it in unused }
                if (key == null) {
                    circuit += stack.removeAt(stack.lastIndex)
                } else {
                    unused.remove(key)
                    val edge = requireNotNull(byKey[key])
                    stack += edge.other(node)
                }
            }

            circuit.reverse()
            for (index in 0 until circuit.lastIndex) {
                val from = circuit[index]
                val to = circuit[index + 1]
                directions[EdgeKey.of(from, to)] = from to to
            }
        }

        check(directions.size == edges.size)
        return directions
    }

    private fun validateLeaguePhaseShape(
        fixtures: List<Fixture>,
        teams: List<Team>,
        competitionType: String
    ) {
        val expectedMatches = if (competitionType == CONFERENCE_LEAGUE) 6 else 8
        val expectedHome = expectedMatches / 2
        val weeks = leagueWeeksFor(competitionType)

        teams.forEach { team ->
            val appearances = fixtures.filter { it.homeTeamId == team.id || it.awayTeamId == team.id }
            check(appearances.size == expectedMatches)
            check(appearances.flatMap { listOf(it.homeTeamId, it.awayTeamId) }
                .filter { it != team.id }.distinct().size == expectedMatches)
            check(appearances.count { it.homeTeamId == team.id } == expectedHome)
            check(appearances.count { it.awayTeamId == team.id } == expectedHome)

            val firstTwo = appearances.filter { it.week in weeks.take(2) }
            val lastTwo = appearances.filter { it.week in weeks.takeLast(2) }
            check(firstTwo.count { it.homeTeamId == team.id } == 1)
            check(lastTwo.count { it.homeTeamId == team.id } == 1)
        }
    }

    internal fun leagueRanking(fixtures: List<Fixture>, competitionType: String): List<LeagueRow> {
        require(competitionType in competitionCodes)
        val leagueFixtures = fixtures.filter {
            it.competitionType == competitionType && it.week in leagueWeeksFor(competitionType)
        }
        val teamIds = leagueFixtures.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.distinct()
        val table = teamIds.associateWith { LeagueRow(it) }.toMutableMap()

        leagueFixtures.filter { it.isPlayed }.forEach { fixture ->
            val home = table[fixture.homeTeamId] ?: return@forEach
            val away = table[fixture.awayTeamId] ?: return@forEach
            val homeGoals = fixture.homeScore ?: 0
            val awayGoals = fixture.awayScore ?: 0

            home.goalsFor += homeGoals
            away.goalsFor += awayGoals
            away.awayGoals += awayGoals
            home.goalDifference += homeGoals - awayGoals
            away.goalDifference += awayGoals - homeGoals

            when {
                homeGoals > awayGoals -> {
                    home.points += 3
                    home.wins += 1
                }
                awayGoals > homeGoals -> {
                    away.points += 3
                    away.wins += 1
                    away.awayWins += 1
                }
                else -> {
                    home.points += 1
                    away.points += 1
                }
            }
        }

        table.values.forEach { row ->
            val opponents = leagueFixtures.mapNotNull { fixture ->
                when (row.teamId) {
                    fixture.homeTeamId -> fixture.awayTeamId
                    fixture.awayTeamId -> fixture.homeTeamId
                    else -> null
                }
            }.distinct()
            row.opponentPoints = opponents.sumOf { table[it]?.points ?: 0 }
            row.opponentGoalDifference = opponents.sumOf { table[it]?.goalDifference ?: 0 }
            row.opponentGoalsFor = opponents.sumOf { table[it]?.goalsFor ?: 0 }
        }

        return table.values.sortedWith(
            compareByDescending<LeagueRow> { it.points }
                .thenByDescending { it.goalDifference }
                .thenByDescending { it.goalsFor }
                .thenByDescending { it.awayGoals }
                .thenByDescending { it.wins }
                .thenByDescending { it.awayWins }
                .thenByDescending { it.opponentPoints }
                .thenByDescending { it.opponentGoalDifference }
                .thenByDescending { it.opponentGoalsFor }
                .thenBy { it.teamId }
        )
    }

    suspend fun processProgression(
        season: Int,
        currentWeek: Int,
        repository: GameRepository
    ) {
        when (currentWeek) {
            CONFERENCE_LEAGUE_WEEKS.last() -> progressLeagueToPlayoff(season, CONFERENCE_LEAGUE, repository)
            CHAMPIONS_EUROPA_LEAGUE_WEEKS.last() -> {
                progressLeagueToPlayoff(season, CHAMPIONS_LEAGUE, repository)
                progressLeagueToPlayoff(season, EUROPA_LEAGUE, repository)
            }
            PLAYOFF_LEG_2_WEEK -> competitionCodes.forEach {
                progressPlayoffToRoundOf16(season, it, repository)
            }
            ROUND_OF_16_LEG_2_WEEK -> competitionCodes.forEach {
                progressAggregateRound(
                    season, it,
                    ROUND_OF_16_LEG_1_WEEK, ROUND_OF_16_LEG_2_WEEK,
                    QUARTERFINAL_LEG_1_WEEK, QUARTERFINAL_LEG_2_WEEK,
                    repository
                )
            }
            QUARTERFINAL_LEG_2_WEEK -> competitionCodes.forEach {
                progressAggregateRound(
                    season, it,
                    QUARTERFINAL_LEG_1_WEEK, QUARTERFINAL_LEG_2_WEEK,
                    SEMIFINAL_LEG_1_WEEK, SEMIFINAL_LEG_2_WEEK,
                    repository
                )
            }
            SEMIFINAL_LEG_2_WEEK -> competitionCodes.forEach {
                progressSemifinalToFinal(season, it, repository)
            }
            FINAL_WEEK -> competitionCodes.forEach {
                recordFinalChampion(season, it, repository)
            }
        }
    }

    private suspend fun progressLeagueToPlayoff(
        season: Int,
        competitionType: String,
        repository: GameRepository
    ) {
        val fixtures = repository.getFixturesForSeason(season)
        if (fixtures.any { it.competitionType == competitionType && it.week == PLAYOFF_LEG_1_WEEK }) return

        val leagueFixtures = fixtures.filter {
            it.competitionType == competitionType && it.week in leagueWeeksFor(competitionType)
        }
        val expectedCount = FIELD_SIZE * leagueWeeksFor(competitionType).size / 2
        if (leagueFixtures.size != expectedCount || leagueFixtures.any { !it.isPlayed }) return

        val ranking = leagueRanking(fixtures, competitionType)
        if (ranking.size != FIELD_SIZE) return

        val pairs = (0 until 8).map { index ->
            // Não-cabeça recebe a ida; posição 9..16 decide a volta em casa.
            ranking[23 - index].teamId to ranking[8 + index].teamId
        }
        repository.saveFixtures(
            generateTwoLegRound(
                season,
                competitionType,
                PLAYOFF_LEG_1_WEEK,
                PLAYOFF_LEG_2_WEEK,
                pairs
            )
        )
    }

    private suspend fun progressPlayoffToRoundOf16(
        season: Int,
        competitionType: String,
        repository: GameRepository
    ) {
        val fixtures = repository.getFixturesForSeason(season)
        if (fixtures.none { it.competitionType == competitionType && it.week == PLAYOFF_LEG_1_WEEK }) return
        if (fixtures.any { it.competitionType == competitionType && it.week == ROUND_OF_16_LEG_1_WEEK }) return

        val winners = aggregateWinners(
            fixtures, competitionType, PLAYOFF_LEG_1_WEEK, PLAYOFF_LEG_2_WEEK, repository
        ) ?: return
        if (winners.size != 8) return

        val ranking = leagueRanking(fixtures, competitionType)
        if (ranking.size != FIELD_SIZE) return
        val topEight = ranking.take(8).map { it.teamId }

        // Bandas oficiais: 1/2 recebem vencedores do caminho 15/16 x 17/18; 3/4 do caminho
        // 13/14 x 19/20; 5/6 do caminho 11/12 x 21/22; 7/8 do caminho 9/10 x 23/24.
        val winnerIndexes = listOf(7, 6, 5, 4, 3, 2, 1, 0)
        val pairs = topEight.indices.map { index ->
            winners[winnerIndexes[index]] to topEight[index]
        }

        repository.saveFixtures(
            generateTwoLegRound(
                season,
                competitionType,
                ROUND_OF_16_LEG_1_WEEK,
                ROUND_OF_16_LEG_2_WEEK,
                pairs
            )
        )
    }

    private suspend fun progressAggregateRound(
        season: Int,
        competitionType: String,
        firstLegWeek: Int,
        secondLegWeek: Int,
        nextFirstLegWeek: Int,
        nextSecondLegWeek: Int,
        repository: GameRepository
    ) {
        val fixtures = repository.getFixturesForSeason(season)
        if (fixtures.none { it.competitionType == competitionType && it.week == firstLegWeek }) return
        if (fixtures.any { it.competitionType == competitionType && it.week == nextFirstLegWeek }) return

        val winners = aggregateWinners(
            fixtures, competitionType, firstLegWeek, secondLegWeek, repository
        ) ?: return
        if (winners.size < 2 || winners.size % 2 != 0) return

        // O primeiro caminho do par é o caminho de maior seed e recebe a volta; se o seed original
        // foi eliminado, o vencedor herda essa posição conforme o regulamento UEFA.
        val pairs = winners.chunked(2).map { pair -> pair[1] to pair[0] }
        repository.saveFixtures(
            generateTwoLegRound(
                season,
                competitionType,
                nextFirstLegWeek,
                nextSecondLegWeek,
                pairs
            )
        )
    }

    private suspend fun progressSemifinalToFinal(
        season: Int,
        competitionType: String,
        repository: GameRepository
    ) {
        val fixtures = repository.getFixturesForSeason(season)
        if (fixtures.none { it.competitionType == competitionType && it.week == SEMIFINAL_LEG_1_WEEK }) return
        if (fixtures.any { it.competitionType == competitionType && it.week == FINAL_WEEK }) return

        val winners = aggregateWinners(
            fixtures, competitionType, SEMIFINAL_LEG_1_WEEK, SEMIFINAL_LEG_2_WEEK, repository
        ) ?: return
        if (winners.size != 2) return

        repository.saveFixtures(
            listOf(
                Fixture(
                    season = season,
                    week = FINAL_WEEK,
                    matchSlot = MatchSlot.MIDWEEK,
                    homeTeamId = winners[0],
                    awayTeamId = winners[1],
                    competitionType = competitionType
                )
            )
        )
    }

    internal fun generateTwoLegRound(
        season: Int,
        competitionType: String,
        firstLegWeek: Int,
        secondLegWeek: Int,
        pairs: List<Pair<Long, Long>>
    ): List<Fixture> {
        GameCalendar.requireValidWeek(firstLegWeek)
        GameCalendar.requireValidWeek(secondLegWeek)
        require(firstLegWeek < secondLegWeek)

        return buildList {
            pairs.forEach { (firstLegHome, secondLegHome) ->
                require(firstLegHome != secondLegHome)
                add(
                    Fixture(
                        season = season,
                        week = firstLegWeek,
                        matchSlot = MatchSlot.MIDWEEK,
                        homeTeamId = firstLegHome,
                        awayTeamId = secondLegHome,
                        competitionType = competitionType
                    )
                )
                add(
                    Fixture(
                        season = season,
                        week = secondLegWeek,
                        matchSlot = MatchSlot.MIDWEEK,
                        homeTeamId = secondLegHome,
                        awayTeamId = firstLegHome,
                        competitionType = competitionType
                    )
                )
            }
        }.also { FixtureScheduleValidator.requireValid(it) }
    }

    private suspend fun aggregateWinners(
        fixtures: List<Fixture>,
        competitionType: String,
        firstLegWeek: Int,
        secondLegWeek: Int,
        repository: GameRepository
    ): List<Long>? {
        val firstLegs = fixtures
            .filter { it.competitionType == competitionType && it.week == firstLegWeek }
            .sortedWith(compareBy<Fixture> { it.id }.thenBy { it.homeTeamId }.thenBy { it.awayTeamId })
        val secondLegs = fixtures.filter {
            it.competitionType == competitionType && it.week == secondLegWeek
        }
        if (firstLegs.isEmpty() || firstLegs.any { !it.isPlayed }) return null
        if (secondLegs.size != firstLegs.size || secondLegs.any { !it.isPlayed }) return null

        val winners = mutableListOf<Long>()
        val updates = mutableListOf<Fixture>()
        firstLegs.forEach { first ->
            val second = secondLegs.singleOrNull {
                it.homeTeamId == first.awayTeamId && it.awayTeamId == first.homeTeamId
            } ?: return null

            val firstTeamGoals = (first.homeScore ?: 0) + (second.awayScore ?: 0)
            val secondTeamGoals = (first.awayScore ?: 0) + (second.homeScore ?: 0)
            when {
                firstTeamGoals > secondTeamGoals -> winners += first.homeTeamId
                secondTeamGoals > firstTeamGoals -> winners += first.awayTeamId
                else -> {
                    val decided = ensurePenaltyDecision(
                        second,
                        "$competitionType:AGG:${first.homeTeamId}:${first.awayTeamId}:$firstLegWeek:$secondLegWeek"
                    )
                    if (decided != second) updates += decided
                    val homePens = decided.homePenalties ?: return null
                    val awayPens = decided.awayPenalties ?: return null
                    winners += if (homePens > awayPens) second.homeTeamId else second.awayTeamId
                }
            }
        }
        if (updates.isNotEmpty()) repository.updateFixtures(updates)
        return winners
    }

    private fun ensurePenaltyDecision(fixture: Fixture, key: String): Fixture {
        if (
            fixture.homePenalties != null && fixture.awayPenalties != null &&
            fixture.homePenalties != fixture.awayPenalties
        ) return fixture

        val random = Random(stableSeed(fixture.season, key))
        var home = random.nextInt(3, 6)
        var away = random.nextInt(3, 6)
        if (home == away) {
            if (random.nextBoolean()) home += 1 else away += 1
        }
        return fixture.copy(homePenalties = home, awayPenalties = away)
    }

    private suspend fun recordFinalChampion(
        season: Int,
        competitionType: String,
        repository: GameRepository
    ) {
        val final = repository.getFixturesForWeek(season, FINAL_WEEK)
            .singleOrNull { it.competitionType == competitionType }
            ?: return
        if (!final.isPlayed) return

        var decided = final
        val homeGoals = final.homeScore ?: 0
        val awayGoals = final.awayScore ?: 0
        if (homeGoals == awayGoals) {
            decided = ensurePenaltyDecision(final, "$competitionType:FINAL:${final.homeTeamId}:${final.awayTeamId}")
            if (decided != final) repository.updateFixture(decided)
        }

        val winnerId = when {
            homeGoals > awayGoals -> decided.homeTeamId
            awayGoals > homeGoals -> decided.awayTeamId
            (decided.homePenalties ?: 0) > (decided.awayPenalties ?: 0) -> decided.homeTeamId
            else -> decided.awayTeamId
        }
        val runnerUpId = if (winnerId == decided.homeTeamId) decided.awayTeamId else decided.homeTeamId
        val winner = repository.getTeam(winnerId) ?: GlobalFootballSystem.getVirtualTeam(winnerId)
        val runnerUp = repository.getTeam(runnerUpId) ?: GlobalFootballSystem.getVirtualTeam(runnerUpId)
        val competitionName = displayNameFor(competitionType)

        if (repository.getAllHistoricalRecords().any {
                it.season == season && it.competitionName == competitionName
            }
        ) return

        repository.saveRecord(
            HistoricalRecord(
                season = season,
                competitionName = competitionName,
                championTeamName = winner.name,
                runnerUpTeamName = runnerUp.name,
                topScorerName = "Destaque da Competição",
                topScorerGoals = 0,
                topScorerTeam = winner.name
            )
        )
    }

    private fun displayNameFor(competitionType: String): String = when (competitionType) {
        CHAMPIONS_LEAGUE -> "UEFA Champions League"
        EUROPA_LEAGUE -> "UEFA Europa League"
        CONFERENCE_LEAGUE -> "UEFA Conference League"
        else -> competitionType
    }

    private fun leagueWeeksFor(competitionType: String): List<Int> =
        if (competitionType == CONFERENCE_LEAGUE) CONFERENCE_LEAGUE_WEEKS
        else CHAMPIONS_EUROPA_LEAGUE_WEEKS

    private fun stableSeed(season: Int, key: String): Long =
        season.toLong() * 1_000_003L + key.hashCode().toLong()

    private val competitionCodes = setOf(CHAMPIONS_LEAGUE, EUROPA_LEAGUE, CONFERENCE_LEAGUE)
}
