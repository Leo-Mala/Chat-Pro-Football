package com.example.data

import kotlin.random.Random

/**
 * Calendário e progressão das competições recorrentes que não são ligas nacionais.
 *
 * O jogo só simula integralmente as ligas do país do usuário. Por isso, enquanto não
 * existe uma tabela doméstica completa para todos os países da confederação, a seleção
 * continental usa uma classificação determinística por força entre clubes da 1ª divisão.
 * Isso é explícito e reproduzível, em vez de fabricar posições de ligas não simuladas.
 */
object SeasonCompetitionSystem {
    const val DOMESTIC_CUP = "COPA"
    const val CONTINENTAL_T1 = "CONTINENTAL_T1"
    const val CONTINENTAL_T2 = "CONTINENTAL_T2"
    const val CONTINENTAL_T3 = "CONTINENTAL_T3"

    const val CONTINENTAL_GROUP_WEEK_1 = 28
    const val CONTINENTAL_GROUP_WEEK_2 = 29
    const val CONTINENTAL_GROUP_WEEK_3 = 30
    const val CONTINENTAL_ROUND_OF_16_WEEK = 32
    const val CONTINENTAL_QUARTERFINAL_WEEK = 33
    const val CONTINENTAL_SEMIFINAL_WEEK = 34
    const val DOMESTIC_CUP_FINAL_WEEK = 35
    const val CONTINENTAL_FINAL_WEEK = 36

    private val groupLetters = ('A'..'H').map { it.toString() }

    fun generateInitialFixtures(
        season: Int,
        teams: List<Team>,
        userTeamId: Long,
        userCountry: String
    ): List<Fixture> {
        val fixtures = mutableListOf<Fixture>()
        fixtures += generateDomesticCupOpeningFixtures(season, teams, userTeamId, userCountry)
        fixtures += generateContinentalGroupStageFixtures(
            season = season,
            participants = selectContinentalTierParticipants(teams, userCountry, tier = 1),
            competitionType = CONTINENTAL_T1
        )
        fixtures += generateContinentalGroupStageFixtures(
            season = season,
            participants = selectContinentalTierParticipants(teams, userCountry, tier = 2),
            competitionType = CONTINENTAL_T2
        )
        fixtures += generateContinentalTier3OpeningFixtures(
            season = season,
            participants = selectContinentalTierParticipants(teams, userCountry, tier = 3)
        )
        return fixtures
    }

    fun selectDomesticCupParticipants(
        teams: List<Team>,
        userTeamId: Long,
        userCountry: String,
        maxParticipants: Int = 32
    ): List<Team> {
        val countryTeams = teams
            .filter { it.country == userCountry }
            .sortedWith(compareByDescending<Team> { it.rating }.thenBy { it.id })

        val bracketSize = largestPowerOfTwoAtMost(minOf(countryTeams.size, maxParticipants))
        if (bracketSize < 2) return emptyList()

        val selected = countryTeams.take(bracketSize).toMutableList()
        val userTeam = countryTeams.firstOrNull { it.id == userTeamId }
        if (userTeam != null && selected.none { it.id == userTeam.id }) {
            selected[selected.lastIndex] = userTeam
        }
        return selected.distinctBy { it.id }
    }

    fun selectContinentalTierParticipants(
        teams: List<Team>,
        userCountry: String,
        tier: Int
    ): List<Team> {
        val confederation = confederationForCountry(userCountry) ?: return emptyList()
        val ranked = teams.asSequence()
            .filter { it.division == 1 }
            .filter { team -> confederationForCountry(team.country) == confederation }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<Team> { it.rating }.thenBy { it.id })
            .toList()

        return when (tier) {
            1 -> ranked.take(32).takeIf { it.size == 32 } ?: emptyList()
            2 -> ranked.drop(32).take(32).takeIf { it.size == 32 } ?: emptyList()
            3 -> ranked.drop(64).take(16).takeIf { it.size == 16 } ?: emptyList()
            else -> emptyList()
        }
    }

    fun generateDomesticCupOpeningFixtures(
        season: Int,
        teams: List<Team>,
        userTeamId: Long,
        userCountry: String
    ): List<Fixture> {
        val participants = selectDomesticCupParticipants(teams, userTeamId, userCountry)
        if (participants.size < 2) return emptyList()

        val rounds = Integer.numberOfTrailingZeros(Integer.highestOneBit(participants.size))
        val openingWeek = DOMESTIC_CUP_FINAL_WEEK - rounds + 1
        val shuffled = participants.shuffled(
            Random(season * 10_007L + userCountry.hashCode().toLong())
        )
        return pairRound(
            season = season,
            week = openingWeek,
            teamIds = shuffled.map { it.id },
            competitionType = DOMESTIC_CUP
        )
    }

    fun generateContinentalGroupStageFixtures(
        season: Int,
        participants: List<Team>,
        competitionType: String
    ): List<Fixture> {
        if (competitionType !in setOf(CONTINENTAL_T1, CONTINENTAL_T2)) return emptyList()
        if (participants.size != 32) return emptyList()

        val tierSeed = if (competitionType == CONTINENTAL_T1) 1L else 2L
        val shuffled = participants.shuffled(Random(season * 1009L + tierSeed))
        val fixtures = mutableListOf<Fixture>()

        for (groupIndex in 0 until 8) {
            val group = shuffled.subList(groupIndex * 4, groupIndex * 4 + 4)
            val code = "${competitionType}_GP_${groupLetters[groupIndex]}"
            val a = group[0].id
            val b = group[1].id
            val c = group[2].id
            val d = group[3].id

            fixtures += Fixture(season = season, week = CONTINENTAL_GROUP_WEEK_1, homeTeamId = a, awayTeamId = d, competitionType = code)
            fixtures += Fixture(season = season, week = CONTINENTAL_GROUP_WEEK_1, homeTeamId = b, awayTeamId = c, competitionType = code)
            fixtures += Fixture(season = season, week = CONTINENTAL_GROUP_WEEK_2, homeTeamId = a, awayTeamId = c, competitionType = code)
            fixtures += Fixture(season = season, week = CONTINENTAL_GROUP_WEEK_2, homeTeamId = d, awayTeamId = b, competitionType = code)
            fixtures += Fixture(season = season, week = CONTINENTAL_GROUP_WEEK_3, homeTeamId = b, awayTeamId = a, competitionType = code)
            fixtures += Fixture(season = season, week = CONTINENTAL_GROUP_WEEK_3, homeTeamId = c, awayTeamId = d, competitionType = code)
        }
        return fixtures
    }

    fun generateContinentalTier3OpeningFixtures(
        season: Int,
        participants: List<Team>
    ): List<Fixture> {
        if (participants.size != 16) return emptyList()
        val shuffled = participants.shuffled(Random(season * 1013L + 3L))
        return pairRound(
            season = season,
            week = CONTINENTAL_ROUND_OF_16_WEEK,
            teamIds = shuffled.map { it.id },
            competitionType = CONTINENTAL_T3
        )
    }

    suspend fun processProgression(
        season: Int,
        currentWeek: Int,
        repository: GameRepository
    ) {
        val seasonFixtures = repository.getFixturesForSeason(season)

        when (currentWeek) {
            CONTINENTAL_GROUP_WEEK_3 -> {
                progressGroupsToRoundOf16(season, CONTINENTAL_T1, seasonFixtures, repository)
                progressGroupsToRoundOf16(season, CONTINENTAL_T2, seasonFixtures, repository)
            }

            31 -> advanceKnockoutRound(season, DOMESTIC_CUP, 31, 32, seasonFixtures, repository)

            CONTINENTAL_ROUND_OF_16_WEEK -> {
                advanceKnockoutRound(season, DOMESTIC_CUP, 32, 33, seasonFixtures, repository)
                advanceKnockoutRound(season, CONTINENTAL_T1, 32, 33, seasonFixtures, repository)
                advanceKnockoutRound(season, CONTINENTAL_T2, 32, 33, seasonFixtures, repository)
                advanceKnockoutRound(season, CONTINENTAL_T3, 32, 33, seasonFixtures, repository)
            }

            CONTINENTAL_QUARTERFINAL_WEEK -> {
                advanceKnockoutRound(season, DOMESTIC_CUP, 33, 34, seasonFixtures, repository)
                advanceKnockoutRound(season, CONTINENTAL_T1, 33, 34, seasonFixtures, repository)
                advanceKnockoutRound(season, CONTINENTAL_T2, 33, 34, seasonFixtures, repository)
                advanceKnockoutRound(season, CONTINENTAL_T3, 33, 34, seasonFixtures, repository)
            }

            CONTINENTAL_SEMIFINAL_WEEK -> {
                advanceKnockoutRound(season, DOMESTIC_CUP, 34, DOMESTIC_CUP_FINAL_WEEK, seasonFixtures, repository)
                advanceKnockoutRound(season, CONTINENTAL_T1, 34, CONTINENTAL_FINAL_WEEK, seasonFixtures, repository)
                advanceKnockoutRound(season, CONTINENTAL_T2, 34, CONTINENTAL_FINAL_WEEK, seasonFixtures, repository)
                advanceKnockoutRound(season, CONTINENTAL_T3, 34, CONTINENTAL_FINAL_WEEK, seasonFixtures, repository)
            }

            DOMESTIC_CUP_FINAL_WEEK -> {
                recordChampion(season, DOMESTIC_CUP, DOMESTIC_CUP_FINAL_WEEK, seasonFixtures, repository)
            }

            CONTINENTAL_FINAL_WEEK -> {
                recordChampion(season, CONTINENTAL_T1, CONTINENTAL_FINAL_WEEK, seasonFixtures, repository)
                recordChampion(season, CONTINENTAL_T2, CONTINENTAL_FINAL_WEEK, seasonFixtures, repository)
                recordChampion(season, CONTINENTAL_T3, CONTINENTAL_FINAL_WEEK, seasonFixtures, repository)
            }
        }
    }

    private suspend fun progressGroupsToRoundOf16(
        season: Int,
        competitionType: String,
        seasonFixtures: List<Fixture>,
        repository: GameRepository
    ) {
        if (seasonFixtures.any {
                it.competitionType == competitionType &&
                    it.week == CONTINENTAL_ROUND_OF_16_WEEK
            }
        ) return

        val qualifiers = mutableMapOf<String, Pair<Long, Long>>()
        for (letter in groupLetters) {
            val code = "${competitionType}_GP_$letter"
            val matches = seasonFixtures.filter { it.competitionType == code }
            if (matches.size != 6 || matches.any { !it.isPlayed }) return

            val standings = calculateGroupStandings(matches)
            if (standings.size < 2) return
            qualifiers[letter] = standings[0].teamId to standings[1].teamId
        }

        val pairings = listOf(
            "A" to "B", "B" to "A",
            "C" to "D", "D" to "C",
            "E" to "F", "F" to "E",
            "G" to "H", "H" to "G"
        )
        val fixtures = pairings.mapNotNull { (firstGroup, secondGroup) ->
            val home = qualifiers[firstGroup]?.first ?: return@mapNotNull null
            val away = qualifiers[secondGroup]?.second ?: return@mapNotNull null
            Fixture(
                season = season,
                week = CONTINENTAL_ROUND_OF_16_WEEK,
                homeTeamId = home,
                awayTeamId = away,
                competitionType = competitionType
            )
        }
        if (fixtures.size == 8) repository.saveFixtures(fixtures)
    }

    private data class GroupStanding(
        val teamId: Long,
        var points: Int = 0,
        var goalsFor: Int = 0,
        var goalsAgainst: Int = 0
    ) {
        val goalDifference: Int get() = goalsFor - goalsAgainst
    }

    private fun calculateGroupStandings(matches: List<Fixture>): List<GroupStanding> {
        val teamIds = matches.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.distinct()
        val table = teamIds.associateWith { GroupStanding(it) }.toMutableMap()

        matches.filter { it.isPlayed }.forEach { match ->
            val home = table[match.homeTeamId] ?: return@forEach
            val away = table[match.awayTeamId] ?: return@forEach
            val hg = match.homeScore ?: 0
            val ag = match.awayScore ?: 0

            home.goalsFor += hg
            home.goalsAgainst += ag
            away.goalsFor += ag
            away.goalsAgainst += hg

            when {
                hg > ag -> home.points += 3
                ag > hg -> away.points += 3
                else -> {
                    home.points += 1
                    away.points += 1
                }
            }
        }

        return table.values.sortedWith(
            compareByDescending<GroupStanding> { it.points }
                .thenByDescending { it.goalDifference }
                .thenByDescending { it.goalsFor }
                .thenBy { it.teamId }
        )
    }

    private suspend fun advanceKnockoutRound(
        season: Int,
        competitionType: String,
        currentWeek: Int,
        nextWeek: Int,
        seasonFixtures: List<Fixture>,
        repository: GameRepository
    ) {
        val currentRound = seasonFixtures
            .filter { it.competitionType == competitionType && it.week == currentWeek }
            .sortedBy { it.id }
        if (currentRound.isEmpty() || currentRound.any { !it.isPlayed }) return
        if (currentRound.size == 1) return
        if (seasonFixtures.any { it.competitionType == competitionType && it.week == nextWeek }) return

        val winners = currentRound.map { resolveWinner(it, repository) }
        if (winners.size < 2 || winners.size % 2 != 0) return

        repository.saveFixtures(
            pairRound(
                season = season,
                week = nextWeek,
                teamIds = winners,
                competitionType = competitionType
            )
        )
    }

    private suspend fun recordChampion(
        season: Int,
        competitionType: String,
        finalWeek: Int,
        seasonFixtures: List<Fixture>,
        repository: GameRepository
    ) {
        val final = seasonFixtures.singleOrNull {
            it.competitionType == competitionType && it.week == finalWeek
        } ?: return
        if (!final.isPlayed) return

        val winnerId = resolveWinner(final, repository)
        val runnerUpId = if (winnerId == final.homeTeamId) final.awayTeamId else final.homeTeamId
        val winner = repository.getTeam(winnerId) ?: GlobalFootballSystem.getVirtualTeam(winnerId)
        val runnerUp = repository.getTeam(runnerUpId) ?: GlobalFootballSystem.getVirtualTeam(runnerUpId)
        val referenceCountry = winner.country
        val competitionName = DefaultData.getCompetitionName(competitionType, referenceCountry)
        val alreadySaved = repository.getAllHistoricalRecords().any {
            it.season == season && it.competitionName == competitionName
        }
        if (alreadySaved) return

        repository.saveRecord(
            HistoricalRecord(
                season = season,
                competitionName = competitionName,
                championTeamName = winner.name,
                runnerUpTeamName = runnerUp.name,
                topScorerName = "Não registrado",
                topScorerGoals = 0,
                topScorerTeam = winner.name
            )
        )
    }

    private suspend fun resolveWinner(fixture: Fixture, repository: GameRepository): Long {
        val homeScore = fixture.homeScore ?: 0
        val awayScore = fixture.awayScore ?: 0
        if (homeScore > awayScore) return fixture.homeTeamId
        if (awayScore > homeScore) return fixture.awayTeamId

        var homePenalties = fixture.homePenalties
        var awayPenalties = fixture.awayPenalties
        if (homePenalties == null || awayPenalties == null || homePenalties == awayPenalties) {
            val seed = fixture.season * 100_003L +
                fixture.week * 1009L +
                fixture.id * 31L +
                fixture.homeTeamId * 17L +
                fixture.awayTeamId
            val random = Random(seed)
            val home = random.nextInt(3, 6)
            var away = random.nextInt(3, 6)
            if (home == away) away = if (away < 5) away + 1 else away - 1
            homePenalties = home
            awayPenalties = away
            repository.updateFixture(
                fixture.copy(homePenalties = homePenalties, awayPenalties = awayPenalties)
            )
        }

        return if (homePenalties > awayPenalties) fixture.homeTeamId else fixture.awayTeamId
    }

    private fun pairRound(
        season: Int,
        week: Int,
        teamIds: List<Long>,
        competitionType: String
    ): List<Fixture> {
        if (teamIds.size < 2 || teamIds.size % 2 != 0) return emptyList()
        return teamIds.chunked(2).map { pair ->
            Fixture(
                season = season,
                week = week,
                homeTeamId = pair[0],
                awayTeamId = pair[1],
                competitionType = competitionType
            )
        }
    }

    private fun largestPowerOfTwoAtMost(value: Int): Int {
        if (value < 2) return 0
        return Integer.highestOneBit(value)
    }

    private fun confederationForCountry(country: String): String? {
        return GlobalFootballSystem.countries.firstOrNull { it.name == country }?.confederation
    }
}
