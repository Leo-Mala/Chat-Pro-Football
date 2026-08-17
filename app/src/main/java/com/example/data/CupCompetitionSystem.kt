package com.example.data

import kotlin.random.Random

/**
 * Geração e progressão das copas da carreira.
 *
 * Formatos usados pelo jogo:
 * - Copa nacional: mata-mata em jogo único com final na semana 35.
 * - Continental T1/T2: fase de grupos nas semanas 29, 30 e 31; mata-mata termina na semana 36.
 * - Continental T3: mata-mata em jogo único com final na semana 36.
 *
 * A quantidade de participantes se adapta ao universo de clubes disponível, sempre usando
 * tamanhos de chave suportados (potências de 2) e mantendo a geração determinística.
 */
object CupCompetitionSystem {
    const val NATIONAL_CUP_FINAL_WEEK = 35
    const val CONTINENTAL_FINAL_WEEK = 36
    private const val GROUP_WEEK_1 = 29
    private const val GROUP_WEEK_2 = 30
    private const val GROUP_WEEK_3 = 31

    private val continentalGroupTypes = listOf("CONTINENTAL_T1", "CONTINENTAL_T2")

    /**
     * Gera a abertura de Copa nacional e torneios continentais.
     *
     * [teams] é sempre a fonte canônica para a Copa nacional. [continentalTeams] pode ser uma
     * visão transitória dos mesmos clubes com prioridade de qualificação aplicada, sem permitir
     * que essa prioridade altere o corte ou a ordenação da Copa nacional.
     */
    fun generateSeasonOpeningFixtures(
        season: Int,
        teams: List<Team>,
        userTeamId: Long,
        userCountry: String,
        continentalTeams: List<Team> = teams
    ): List<Fixture> {
        if (teams.isEmpty()) return emptyList()

        val fixtures = mutableListOf<Fixture>()
        val actualUserCountry = teams.find { it.id == userTeamId }?.country ?: userCountry

        val nationalCupTeams = selectNationalCupTeams(
            teams = teams,
            userTeamId = userTeamId,
            userCountry = actualUserCountry
        )
        if (nationalCupTeams.size >= 2) {
            val startWeek = NATIONAL_CUP_FINAL_WEEK - roundsToChampion(nationalCupTeams.size) + 1
            fixtures += generateKnockoutRound(
                season = season,
                week = startWeek,
                teamIds = nationalCupTeams.map { it.id }
                    .shuffled(Random(stableSeed(season, "COPA"))),
                competitionType = "COPA"
            )
        }

        val userConfederation = GlobalFootballSystem.getConfederationForCountry(actualUserCountry)
        val continentalCandidates = continentalTeams
            .asSequence()
            .filter { GlobalFootballSystem.getConfederationForCountry(it.country) == userConfederation }
            .distinctBy { it.id }
            .sortedWith(
                compareBy<Team> { it.division }
                    .thenByDescending { it.rating }
                    .thenBy { it.id }
            )
            .toList()

        var offset = 0
        for (competitionType in continentalGroupTypes) {
            val available = (continentalCandidates.size - offset).coerceAtLeast(0)
            val participantCount = supportedGroupTeamCount(available)
            if (participantCount == 0) continue

            val selected = continentalCandidates.subList(offset, offset + participantCount)
                .shuffled(Random(stableSeed(season, competitionType)))
            offset += participantCount
            fixtures += generateGroupStage(season, selected, competitionType)
        }

        val remaining = (continentalCandidates.size - offset).coerceAtLeast(0)
        val tier3Count = largestPowerOfTwoAtMost(minOf(16, remaining))
        if (tier3Count >= 2) {
            val selected = continentalCandidates.subList(offset, offset + tier3Count)
                .shuffled(Random(stableSeed(season, "CONTINENTAL_T3")))
            val startWeek = CONTINENTAL_FINAL_WEEK - roundsToChampion(selected.size) + 1
            fixtures += generateKnockoutRound(
                season = season,
                week = startWeek,
                teamIds = selected.map { it.id },
                competitionType = "CONTINENTAL_T3"
            )
        }

        return fixtures
    }

    suspend fun processProgression(
        season: Int,
        currentWeek: Int,
        repository: GameRepository
    ) {
        val seasonFixtures = repository.getFixturesForSeason(season)

        processKnockoutRoundIfPresent(
            season = season,
            currentWeek = currentWeek,
            competitionType = "COPA",
            finalWeek = NATIONAL_CUP_FINAL_WEEK,
            seasonFixtures = seasonFixtures,
            repository = repository
        )

        if (currentWeek == GROUP_WEEK_3) {
            for (competitionType in continentalGroupTypes) {
                progressContinentalGroupsToKnockout(
                    season = season,
                    competitionType = competitionType,
                    seasonFixtures = seasonFixtures,
                    repository = repository
                )
            }
        }

        for (competitionType in listOf("CONTINENTAL_T1", "CONTINENTAL_T2", "CONTINENTAL_T3")) {
            processKnockoutRoundIfPresent(
                season = season,
                currentWeek = currentWeek,
                competitionType = competitionType,
                finalWeek = CONTINENTAL_FINAL_WEEK,
                seasonFixtures = repository.getFixturesForSeason(season),
                repository = repository
            )
        }
    }

    private fun selectNationalCupTeams(
        teams: List<Team>,
        userTeamId: Long,
        userCountry: String
    ): List<Team> {
        val countryTeams = teams
            .filter { it.country.equals(userCountry, ignoreCase = true) }
            .distinctBy { it.id }
            .sortedWith(
                compareBy<Team> { it.division }
                    .thenByDescending { it.rating }
                    .thenBy { it.id }
            )

        val participantCount = largestPowerOfTwoAtMost(minOf(32, countryTeams.size))
        if (participantCount < 2) return emptyList()

        val selected = countryTeams.take(participantCount).toMutableList()
        val userTeam = countryTeams.find { it.id == userTeamId }
        if (userTeam != null && selected.none { it.id == userTeamId }) {
            selected[selected.lastIndex] = userTeam
        }
        return selected.distinctBy { it.id }
    }

    private fun generateGroupStage(
        season: Int,
        teams: List<Team>,
        competitionType: String
    ): List<Fixture> {
        if (teams.size < 8 || teams.size % 4 != 0) return emptyList()

        val fixtures = mutableListOf<Fixture>()
        val groupCount = teams.size / 4

        for (groupIndex in 0 until groupCount) {
            val start = groupIndex * 4
            val groupTeams = teams.subList(start, start + 4)
            val groupLetter = ('A'.code + groupIndex).toChar()
            val groupCode = "${competitionType}_GP_$groupLetter"

            val t1 = groupTeams[0].id
            val t2 = groupTeams[1].id
            val t3 = groupTeams[2].id
            val t4 = groupTeams[3].id

            fixtures += Fixture(
                season = season,
                week = GROUP_WEEK_1,
                homeTeamId = t1,
                awayTeamId = t4,
                competitionType = groupCode
            )
            fixtures += Fixture(
                season = season,
                week = GROUP_WEEK_1,
                homeTeamId = t2,
                awayTeamId = t3,
                competitionType = groupCode
            )
            fixtures += Fixture(
                season = season,
                week = GROUP_WEEK_2,
                homeTeamId = t1,
                awayTeamId = t3,
                competitionType = groupCode
            )
            fixtures += Fixture(
                season = season,
                week = GROUP_WEEK_2,
                homeTeamId = t4,
                awayTeamId = t2,
                competitionType = groupCode
            )
            fixtures += Fixture(
                season = season,
                week = GROUP_WEEK_3,
                homeTeamId = t2,
                awayTeamId = t1,
                competitionType = groupCode
            )
            fixtures += Fixture(
                season = season,
                week = GROUP_WEEK_3,
                homeTeamId = t3,
                awayTeamId = t4,
                competitionType = groupCode
            )
        }

        return fixtures
    }

    private suspend fun progressContinentalGroupsToKnockout(
        season: Int,
        competitionType: String,
        seasonFixtures: List<Fixture>,
        repository: GameRepository
    ) {
        val groupFixtures = seasonFixtures.filter {
            it.competitionType.startsWith("${competitionType}_GP_")
        }
        if (groupFixtures.isEmpty() || groupFixtures.any { !it.isPlayed }) return
        if (seasonFixtures.any { it.competitionType == competitionType }) return

        val qualifiersByGroup = groupFixtures
            .groupBy { it.competitionType }
            .toSortedMap()
            .mapValues { (_, fixtures) -> calculateGroupQualifiers(fixtures) }

        if (qualifiersByGroup.values.any { it.size < 2 }) return

        val groupCodes = qualifiersByGroup.keys.toList()
        if (groupCodes.size < 2 || groupCodes.size % 2 != 0) return

        val bracket = mutableListOf<Long>()
        for (index in groupCodes.indices step 2) {
            val firstGroup = qualifiersByGroup[groupCodes[index]] ?: return
            val secondGroup = qualifiersByGroup[groupCodes[index + 1]] ?: return
            bracket += firstGroup[0]
            bracket += secondGroup[1]
            bracket += secondGroup[0]
            bracket += firstGroup[1]
        }

        val firstKnockoutWeek = CONTINENTAL_FINAL_WEEK - roundsToChampion(bracket.size) + 1
        repository.saveFixtures(
            generateKnockoutRound(
                season = season,
                week = firstKnockoutWeek,
                teamIds = bracket,
                competitionType = competitionType
            )
        )
    }

    private suspend fun processKnockoutRoundIfPresent(
        season: Int,
        currentWeek: Int,
        competitionType: String,
        finalWeek: Int,
        seasonFixtures: List<Fixture>,
        repository: GameRepository
    ) {
        val currentRound = seasonFixtures.filter {
            it.competitionType == competitionType && it.week == currentWeek
        }
        if (currentRound.isEmpty() || currentRound.any { !it.isPlayed }) return

        val decidedFixtures = currentRound.map { CompetitionRules.ensureKnockoutDecision(it) }
        val changed = decidedFixtures.filterIndexed { index, fixture -> fixture != currentRound[index] }
        if (changed.isNotEmpty()) {
            repository.updateFixtures(changed)
        }

        val winners = decidedFixtures.mapNotNull { CompetitionRules.winnerOf(it) }
        if (winners.size != decidedFixtures.size) return

        if (decidedFixtures.size == 1) {
            recordChampion(
                season = season,
                competitionType = competitionType,
                finalFixture = decidedFixtures.single(),
                repository = repository
            )
            return
        }

        // A semana reservada para a final deve conter exatamente uma partida. Se um save
        // estiver malformado e chegar aqui com múltiplos confrontos, não inventamos um campeão
        // nem lançamos uma exceção; a integridade pode ser reparada sem derrubar a carreira.
        if (currentWeek >= finalWeek) return

        val nextWeek = currentWeek + 1
        val refreshed = repository.getFixturesForSeason(season)
        if (refreshed.any { it.competitionType == competitionType && it.week == nextWeek }) return

        repository.saveFixtures(
            generateKnockoutRound(
                season = season,
                week = nextWeek,
                teamIds = winners,
                competitionType = competitionType
            )
        )
    }

    internal fun calculateGroupQualifiers(fixtures: List<Fixture>): List<Long> {
        data class Row(
            val teamId: Long,
            var points: Int = 0,
            var wins: Int = 0,
            var goalDifference: Int = 0,
            var goalsFor: Int = 0
        )

        val teamIds = fixtures.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.distinct()
        val table = teamIds.associateWith { Row(it) }.toMutableMap()

        for (fixture in fixtures.filter { it.isPlayed }) {
            val home = table[fixture.homeTeamId] ?: continue
            val away = table[fixture.awayTeamId] ?: continue
            val homeGoals = fixture.homeScore ?: 0
            val awayGoals = fixture.awayScore ?: 0

            home.goalsFor += homeGoals
            away.goalsFor += awayGoals
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
                }
                else -> {
                    home.points += 1
                    away.points += 1
                }
            }
        }

        return table.values
            .sortedWith(
                compareByDescending<Row> { it.points }
                    .thenByDescending { it.wins }
                    .thenByDescending { it.goalDifference }
                    .thenByDescending { it.goalsFor }
                    .thenBy { it.teamId }
            )
            .take(2)
            .map { it.teamId }
    }

    private fun generateKnockoutRound(
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

    private suspend fun recordChampion(
        season: Int,
        competitionType: String,
        finalFixture: Fixture,
        repository: GameRepository
    ) {
        val winnerId = CompetitionRules.winnerOf(finalFixture) ?: return
        val runnerUpId = if (winnerId == finalFixture.homeTeamId) {
            finalFixture.awayTeamId
        } else {
            finalFixture.homeTeamId
        }

        val winner = repository.getTeam(winnerId) ?: GlobalFootballSystem.getVirtualTeam(winnerId)
        val runnerUp = repository.getTeam(runnerUpId) ?: GlobalFootballSystem.getVirtualTeam(runnerUpId)
        val competitionName = DefaultData.getCompetitionName(competitionType, winner.country)

        val alreadyRecorded = repository.getAllHistoricalRecords().any {
            it.season == season && it.competitionName == competitionName
        }
        if (alreadyRecorded) return

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

    private fun supportedGroupTeamCount(available: Int): Int = when {
        available >= 32 -> 32
        available >= 16 -> 16
        available >= 8 -> 8
        else -> 0
    }

    private fun largestPowerOfTwoAtMost(value: Int): Int {
        if (value < 2) return 0
        var result = 1
        while (result * 2 <= value) {
            result *= 2
        }
        return result
    }

    private fun roundsToChampion(teamCount: Int): Int {
        require(teamCount >= 2 && teamCount and (teamCount - 1) == 0) {
            "Quantidade de participantes deve ser potência de 2: $teamCount"
        }
        var teams = teamCount
        var rounds = 0
        while (teams > 1) {
            teams /= 2
            rounds += 1
        }
        return rounds
    }

    private fun stableSeed(season: Int, competitionType: String): Long =
        season.toLong() * 1_000_003L + competitionType.hashCode().toLong()
}
