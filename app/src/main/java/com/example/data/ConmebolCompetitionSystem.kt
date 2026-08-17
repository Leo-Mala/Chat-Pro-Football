package com.example.data

import kotlin.random.Random

/**
 * Formato detalhado da CONMEBOL a partir da fase de grupos.
 *
 * Libertadores (T1): 32 clubes, 8x4, turno/returno, dois classificados por grupo,
 * oitavas/quartas/semifinais em ida e volta e final única.
 *
 * Sudamericana (T2): 32 clubes, 8x4, turno/returno, líderes direto às oitavas,
 * vice-líderes em playoff de ida/volta contra os terceiros da Libertadores,
 * oitavas/quartas/semifinais em ida e volta e final única.
 *
 * As preliminares continentais ainda não são competições próprias no jogo. Os 32 participantes
 * de cada fase de grupos são entregues pela política de qualificação da carreira.
 */
object ConmebolCompetitionSystem {
    const val LIBERTADORES = "CONTINENTAL_T1"
    const val SUDAMERICANA = "CONTINENTAL_T2"

    const val FIELD_SIZE = 32
    const val GROUP_COUNT = 8
    const val GROUP_SIZE = 4
    const val GROUP_MATCHES_PER_TEAM = 6
    const val GROUP_MATCH_COUNT = 96

    /** Seis datas continentais antes da copa nacional detalhada (semanas 23..27). */
    val GROUP_WEEKS: List<Int> = listOf(2, 5, 8, 11, 14, 17)

    const val SUD_PLAYOFF_LEG_1_WEEK = 28
    const val SUD_PLAYOFF_LEG_2_WEEK = 29
    const val ROUND_OF_16_LEG_1_WEEK = 30
    const val ROUND_OF_16_LEG_2_WEEK = 31
    const val QUARTERFINAL_LEG_1_WEEK = 37
    const val QUARTERFINAL_LEG_2_WEEK = 38
    const val SEMIFINAL_LEG_1_WEEK = 39
    const val SEMIFINAL_LEG_2_WEEK = 40
    const val FINAL_WEEK = 41

    private val aggregateLegWeeks = setOf(
        SUD_PLAYOFF_LEG_1_WEEK,
        SUD_PLAYOFF_LEG_2_WEEK,
        ROUND_OF_16_LEG_1_WEEK,
        ROUND_OF_16_LEG_2_WEEK,
        QUARTERFINAL_LEG_1_WEEK,
        QUARTERFINAL_LEG_2_WEEK,
        SEMIFINAL_LEG_1_WEEK,
        SEMIFINAL_LEG_2_WEEK
    )

    internal data class GroupStanding(
        val teamId: Long,
        var points: Int = 0,
        var goalDifference: Int = 0,
        var goalsFor: Int = 0,
        var awayGoals: Int = 0,
        var wins: Int = 0
    )

    fun isAggregateLeg(fixture: Fixture): Boolean =
        fixture.competitionType in setOf(LIBERTADORES, SUDAMERICANA) &&
            fixture.week in aggregateLegWeeks

    fun isConmebolSeason(fixtures: List<Fixture>): Boolean {
        val grouped = fixtures
            .filter {
                it.competitionType.startsWith("${LIBERTADORES}_GP_") ||
                    it.competitionType.startsWith("${SUDAMERICANA}_GP_")
            }
            .groupBy { it.competitionType }
        return grouped.values.any { it.size == 12 }
    }

    fun generateOpeningFixtures(
        season: Int,
        libertadoresTeams: List<Team>,
        sudamericanaTeams: List<Team>
    ): List<Fixture> {
        val fixtures = mutableListOf<Fixture>()
        if (libertadoresTeams.size == FIELD_SIZE) {
            fixtures += generateGroupStage(
                season = season,
                teams = libertadoresTeams,
                competitionType = LIBERTADORES
            )
        }
        if (sudamericanaTeams.size == FIELD_SIZE) {
            fixtures += generateGroupStage(
                season = season,
                teams = sudamericanaTeams,
                competitionType = SUDAMERICANA
            )
        }
        FixtureScheduleValidator.requireValid(fixtures)
        return fixtures
    }

    internal fun drawGroups(
        season: Int,
        teams: List<Team>,
        competitionType: String
    ): List<List<Team>> {
        if (teams.size != FIELD_SIZE) return emptyList()

        // Sem uma tabela CONMEBOL histórica persistida no save, rating + id funcionam como proxy
        // determinístico do ranking para formar quatro potes de oito. A regra estrutural de um
        // clube de cada pote por grupo é preservada.
        val ranked = teams
            .distinctBy { it.id }
            .sortedWith(compareByDescending<Team> { it.rating }.thenBy { it.id })
        if (ranked.size != FIELD_SIZE) return emptyList()

        val pots = ranked.chunked(GROUP_COUNT)
        val groups = MutableList(GROUP_COUNT) { mutableListOf<Team>() }

        pots.forEachIndexed { potIndex, pot ->
            val shuffledPot = pot.shuffled(Random(stableSeed(season, "$competitionType:POT:$potIndex")))

            // Tenta todas as rotações do pote. Primeiro maximiza o número de alocações sem
            // repetir país; em empate usa a menor rotação para manter determinismo.
            val bestOffset = (0 until GROUP_COUNT)
                .maxWithOrNull(
                    compareBy<Int> { offset ->
                        (0 until GROUP_COUNT).count { groupIndex ->
                            val team = shuffledPot[(groupIndex + offset) % GROUP_COUNT]
                            groups[groupIndex].none {
                                it.country.equals(team.country, ignoreCase = true)
                            }
                        }
                    }.thenByDescending { -it }
                ) ?: 0

            for (groupIndex in 0 until GROUP_COUNT) {
                groups[groupIndex] += shuffledPot[(groupIndex + bestOffset) % GROUP_COUNT]
            }
        }

        return groups.map { it.toList() }
    }

    internal fun generateGroupStage(
        season: Int,
        teams: List<Team>,
        competitionType: String
    ): List<Fixture> {
        val groups = drawGroups(season, teams, competitionType)
        if (groups.size != GROUP_COUNT) return emptyList()

        val fixtures = mutableListOf<Fixture>()
        groups.forEachIndexed { groupIndex, group ->
            if (group.size != GROUP_SIZE) return emptyList()
            val letter = ('A'.code + groupIndex).toChar()
            val groupCode = "${competitionType}_GP_$letter"
            val t1 = group[0].id
            val t2 = group[1].id
            val t3 = group[2].id
            val t4 = group[3].id

            val rounds = listOf(
                listOf(t1 to t4, t2 to t3),
                listOf(t3 to t1, t4 to t2),
                listOf(t1 to t2, t3 to t4),
                listOf(t4 to t1, t3 to t2),
                listOf(t1 to t3, t2 to t4),
                listOf(t2 to t1, t4 to t3)
            )

            rounds.forEachIndexed { roundIndex, pairings ->
                val week = GROUP_WEEKS[roundIndex]
                pairings.forEach { (home, away) ->
                    fixtures += Fixture(
                        season = season,
                        week = week,
                        matchSlot = MatchSlot.MIDWEEK,
                        homeTeamId = home,
                        awayTeamId = away,
                        competitionType = groupCode
                    )
                }
            }
        }

        check(fixtures.size == GROUP_MATCH_COUNT) {
            "Fase de grupos $competitionType deveria gerar $GROUP_MATCH_COUNT partidas, gerou ${fixtures.size}."
        }
        FixtureScheduleValidator.requireValid(fixtures)
        return fixtures
    }

    internal fun calculateGroupRanking(fixtures: List<Fixture>): List<Long> {
        val teamIds = fixtures.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.distinct()
        val table = teamIds.associateWith { GroupStanding(it) }.toMutableMap()

        fixtures.filter { it.isPlayed }.forEach { fixture ->
            val home = table[fixture.homeTeamId] ?: return@forEach
            val away = table[fixture.awayTeamId] ?: return@forEach
            val hg = fixture.homeScore ?: 0
            val ag = fixture.awayScore ?: 0

            home.goalsFor += hg
            away.goalsFor += ag
            away.awayGoals += ag
            home.goalDifference += hg - ag
            away.goalDifference += ag - hg

            when {
                hg > ag -> {
                    home.points += 3
                    home.wins += 1
                }
                ag > hg -> {
                    away.points += 3
                    away.wins += 1
                }
                else -> {
                    home.points += 1
                    away.points += 1
                }
            }
        }

        return table.values
            .sortedWith(
                compareByDescending<GroupStanding> { it.points }
                    .thenByDescending { it.goalDifference }
                    .thenByDescending { it.goalsFor }
                    .thenByDescending { it.awayGoals }
                    .thenByDescending { it.wins }
                    .thenBy { it.teamId }
            )
            .map { it.teamId }
    }

    suspend fun processProgression(
        season: Int,
        currentWeek: Int,
        repository: GameRepository
    ) {
        when (currentWeek) {
            GROUP_WEEKS.last() -> progressGroups(season, repository)
            SUD_PLAYOFF_LEG_2_WEEK -> progressSudamericanaPlayoff(season, repository)
            ROUND_OF_16_LEG_2_WEEK -> {
                progressAggregateRound(
                    season,
                    LIBERTADORES,
                    ROUND_OF_16_LEG_1_WEEK,
                    ROUND_OF_16_LEG_2_WEEK,
                    QUARTERFINAL_LEG_1_WEEK,
                    QUARTERFINAL_LEG_2_WEEK,
                    repository
                )
                progressAggregateRound(
                    season,
                    SUDAMERICANA,
                    ROUND_OF_16_LEG_1_WEEK,
                    ROUND_OF_16_LEG_2_WEEK,
                    QUARTERFINAL_LEG_1_WEEK,
                    QUARTERFINAL_LEG_2_WEEK,
                    repository
                )
            }
            QUARTERFINAL_LEG_2_WEEK -> {
                progressAggregateRound(
                    season,
                    LIBERTADORES,
                    QUARTERFINAL_LEG_1_WEEK,
                    QUARTERFINAL_LEG_2_WEEK,
                    SEMIFINAL_LEG_1_WEEK,
                    SEMIFINAL_LEG_2_WEEK,
                    repository
                )
                progressAggregateRound(
                    season,
                    SUDAMERICANA,
                    QUARTERFINAL_LEG_1_WEEK,
                    QUARTERFINAL_LEG_2_WEEK,
                    SEMIFINAL_LEG_1_WEEK,
                    SEMIFINAL_LEG_2_WEEK,
                    repository
                )
            }
            SEMIFINAL_LEG_2_WEEK -> {
                progressAggregateRoundToFinal(season, LIBERTADORES, repository)
                progressAggregateRoundToFinal(season, SUDAMERICANA, repository)
            }
            FINAL_WEEK -> {
                recordFinalChampion(season, LIBERTADORES, repository)
                recordFinalChampion(season, SUDAMERICANA, repository)
            }
        }
    }

    private suspend fun progressGroups(season: Int, repository: GameRepository) {
        val fixtures = repository.getFixturesForSeason(season)
        val libGroups = groupRankings(fixtures, LIBERTADORES)
        val sudGroups = groupRankings(fixtures, SUDAMERICANA)

        if (libGroups.size == GROUP_COUNT && libGroups.values.all { it.size == GROUP_SIZE }) {
            val existingLibKnockout = fixtures.any {
                it.competitionType == LIBERTADORES && it.week == ROUND_OF_16_LEG_1_WEEK
            }
            if (!existingLibKnockout) {
                val pairs = mutableListOf<Pair<Long, Long>>()
                val codes = libGroups.keys.sorted()
                for (index in codes.indices step 2) {
                    val a = libGroups.getValue(codes[index])
                    val b = libGroups.getValue(codes[index + 1])
                    // Vice recebe a ida; líder decide a volta em casa.
                    pairs += b[1] to a[0]
                    pairs += a[1] to b[0]
                }
                repository.saveFixtures(
                    generateTwoLegRound(
                        season,
                        LIBERTADORES,
                        ROUND_OF_16_LEG_1_WEEK,
                        ROUND_OF_16_LEG_2_WEEK,
                        pairs
                    )
                )
            }
        }

        if (
            libGroups.size == GROUP_COUNT && libGroups.values.all { it.size == GROUP_SIZE } &&
            sudGroups.size == GROUP_COUNT && sudGroups.values.all { it.size == GROUP_SIZE }
        ) {
            val existingPlayoff = fixtures.any {
                it.competitionType == SUDAMERICANA && it.week == SUD_PLAYOFF_LEG_1_WEEK
            }
            if (!existingPlayoff) {
                val libThirds = libGroups.keys.sorted().map { libGroups.getValue(it)[2] }
                val sudRunners = sudGroups.keys.sorted().map { sudGroups.getValue(it)[1] }
                val shuffledThirds = libThirds.shuffled(
                    Random(stableSeed(season, "SUD_PLAYOFF_LIB_THIRDS"))
                )
                val pairs = sudRunners.indices.map { index ->
                    // Terceiro da Libertadores recebe a ida; vice da Sudamericana decide em casa.
                    shuffledThirds[index] to sudRunners[index]
                }
                repository.saveFixtures(
                    generateTwoLegRound(
                        season,
                        SUDAMERICANA,
                        SUD_PLAYOFF_LEG_1_WEEK,
                        SUD_PLAYOFF_LEG_2_WEEK,
                        pairs
                    )
                )
            }
        }
    }

    private suspend fun progressSudamericanaPlayoff(
        season: Int,
        repository: GameRepository
    ) {
        val fixtures = repository.getFixturesForSeason(season)
        if (fixtures.any {
                it.competitionType == SUDAMERICANA && it.week == ROUND_OF_16_LEG_1_WEEK
            }
        ) return

        val playoffWinners = aggregateWinners(
            fixtures = fixtures,
            competitionType = SUDAMERICANA,
            firstLegWeek = SUD_PLAYOFF_LEG_1_WEEK,
            secondLegWeek = SUD_PLAYOFF_LEG_2_WEEK,
            repository = repository
        ) ?: return
        if (playoffWinners.size != 8) return

        val sudGroups = groupRankings(fixtures, SUDAMERICANA)
        if (sudGroups.size != GROUP_COUNT || sudGroups.values.any { it.size < GROUP_SIZE }) return
        val leaders = sudGroups.keys.sorted().map { sudGroups.getValue(it)[0] }
        val winners = playoffWinners.shuffled(Random(stableSeed(season, "SUD_R16_WINNERS")))

        // Vencedor do playoff recebe a ida; líder de grupo decide a volta em casa.
        val pairs = leaders.indices.map { index -> winners[index] to leaders[index] }
        repository.saveFixtures(
            generateTwoLegRound(
                season,
                SUDAMERICANA,
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
        if (fixtures.any {
                it.competitionType == competitionType && it.week == nextFirstLegWeek
            }
        ) return

        val winners = aggregateWinners(
            fixtures,
            competitionType,
            firstLegWeek,
            secondLegWeek,
            repository
        ) ?: return
        if (winners.size < 2 || winners.size % 2 != 0) return

        val pairs = winners.chunked(2).map { it[0] to it[1] }
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

    private suspend fun progressAggregateRoundToFinal(
        season: Int,
        competitionType: String,
        repository: GameRepository
    ) {
        val fixtures = repository.getFixturesForSeason(season)
        if (fixtures.any {
                it.competitionType == competitionType && it.week == FINAL_WEEK
            }
        ) return

        val winners = aggregateWinners(
            fixtures,
            competitionType,
            SEMIFINAL_LEG_1_WEEK,
            SEMIFINAL_LEG_2_WEEK,
            repository
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

    private fun groupRankings(
        fixtures: List<Fixture>,
        competitionType: String
    ): Map<String, List<Long>> {
        val groupFixtures = fixtures
            .filter { it.competitionType.startsWith("${competitionType}_GP_") }
            .groupBy { it.competitionType }
            .toSortedMap()
        if (groupFixtures.isEmpty()) return emptyMap()
        if (groupFixtures.values.any { group -> group.size != 12 || group.any { !it.isPlayed } }) {
            return emptyMap()
        }
        return groupFixtures.mapValues { (_, group) -> calculateGroupRanking(group) }
    }

    internal fun generateTwoLegRound(
        season: Int,
        competitionType: String,
        firstLegWeek: Int,
        secondLegWeek: Int,
        pairs: List<Pair<Long, Long>>
    ): List<Fixture> {
        if (pairs.isEmpty()) return emptyList()
        GameCalendar.requireValidWeek(firstLegWeek)
        GameCalendar.requireValidWeek(secondLegWeek)
        require(firstLegWeek < secondLegWeek)

        val fixtures = mutableListOf<Fixture>()
        pairs.forEach { (firstLegHome, secondLegHome) ->
            require(firstLegHome != secondLegHome)
            fixtures += Fixture(
                season = season,
                week = firstLegWeek,
                matchSlot = MatchSlot.MIDWEEK,
                homeTeamId = firstLegHome,
                awayTeamId = secondLegHome,
                competitionType = competitionType
            )
            fixtures += Fixture(
                season = season,
                week = secondLegWeek,
                matchSlot = MatchSlot.MIDWEEK,
                homeTeamId = secondLegHome,
                awayTeamId = firstLegHome,
                competitionType = competitionType
            )
        }
        FixtureScheduleValidator.requireValid(fixtures)
        return fixtures
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
            .sortedBy { it.id }
        val secondLegs = fixtures
            .filter { it.competitionType == competitionType && it.week == secondLegWeek }
        if (firstLegs.isEmpty() || firstLegs.any { !it.isPlayed }) return null
        if (secondLegs.size != firstLegs.size || secondLegs.any { !it.isPlayed }) return null

        val winners = mutableListOf<Long>()
        val decisionsToPersist = mutableListOf<Fixture>()

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
                    val decidedSecond = ensureAggregatePenaltyDecision(first, second)
                    if (decidedSecond != second) decisionsToPersist += decidedSecond
                    val secondHomePens = decidedSecond.homePenalties ?: return null
                    val secondAwayPens = decidedSecond.awayPenalties ?: return null
                    winners += if (secondHomePens > secondAwayPens) {
                        second.homeTeamId
                    } else {
                        second.awayTeamId
                    }
                }
            }
        }

        if (decisionsToPersist.isNotEmpty()) repository.updateFixtures(decisionsToPersist)
        return winners
    }

    internal fun ensureAggregatePenaltyDecision(firstLeg: Fixture, secondLeg: Fixture): Fixture {
        if (
            secondLeg.homePenalties != null && secondLeg.awayPenalties != null &&
            secondLeg.homePenalties != secondLeg.awayPenalties
        ) return secondLeg

        val seed = stableSeed(
            firstLeg.season,
            "AGG:${firstLeg.homeTeamId}:${firstLeg.awayTeamId}:${firstLeg.week}:${secondLeg.week}"
        )
        val random = Random(seed)
        var home = random.nextInt(3, 6)
        var away = random.nextInt(3, 6)
        if (home == away) {
            if (random.nextBoolean()) home += 1 else away += 1
        }
        return secondLeg.copy(homePenalties = home, awayPenalties = away)
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

        val decided = CompetitionRules.ensureKnockoutDecision(final)
        if (decided != final) repository.updateFixture(decided)
        val winnerId = CompetitionRules.winnerOf(decided) ?: return
        val runnerUpId = if (winnerId == decided.homeTeamId) decided.awayTeamId else decided.homeTeamId
        val winner = repository.getTeam(winnerId) ?: GlobalFootballSystem.getVirtualTeam(winnerId)
        val runnerUp = repository.getTeam(runnerUpId) ?: GlobalFootballSystem.getVirtualTeam(runnerUpId)
        val competitionName = DefaultData.getCompetitionName(competitionType, winner.country)

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

    private fun stableSeed(season: Int, key: String): Long =
        season.toLong() * 1_000_003L + key.hashCode().toLong()
}
