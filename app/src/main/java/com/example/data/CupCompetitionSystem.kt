package com.example.data

import kotlin.random.Random

/**
 * Geração e progressão das copas da carreira.
 *
 * - Copa nacional: mata-mata em jogo único, MIDWEEK, com final na semana 27;
 * - UEFA: delegada ao [UefaCompetitionSystem] usando códigos concretos UEFA_CL/EL/ECL;
 * - CONMEBOL: delegada ao [ConmebolCompetitionSystem], com Libertadores/Sudamericana completas;
 * - demais confederações: preservam o formato continental legado desta fase do projeto;
 * - Continental T3 legado: mata-mata em jogo único até a semana 36, quando habilitado.
 */
object CupCompetitionSystem {
    const val NATIONAL_CUP_FINAL_WEEK = 27
    const val CONTINENTAL_FINAL_WEEK = 36
    private const val LEGACY_GROUP_WEEK_1 = 29
    private const val LEGACY_GROUP_WEEK_2 = 30
    private const val LEGACY_GROUP_WEEK_3 = 31

    private val continentalGroupTypes = listOf("CONTINENTAL_T1", "CONTINENTAL_T2")

    internal data class ContinentalFields(
        val tier1: List<Team>,
        val tier2: List<Team>,
        val tier3: List<Team>
    ) {
        val allTeamIds: Set<Long>
            get() = (tier1 + tier2 + tier3).map { it.id }.toSet()
    }

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
        val countryRules = CountryFootballRulesRegistry.resolve(actualUserCountry)

        // País desconhecido ou identidade virtual não recebe uma competição doméstica inventada.
        if (countryRules?.domesticCompetitionsAllowed == true) {
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
        }

        // A ausência de confederação é um estado válido e fail-safe: não gera continental.
        val userConfederation = countryRules?.confederation
        if (userConfederation == null) {
            FixtureScheduleValidator.requireValid(fixtures)
            return fixtures
        }

        val continentalCandidates = continentalTeams
            .asSequence()
            .filter {
                CountryFootballRulesRegistry.isContinentalCompetitionEligible(it.country) &&
                    CountryFootballRulesRegistry.confederationFor(it.country) == userConfederation
            }
            .distinctBy { it.id }
            .sortedWith(
                compareBy<Team> { it.division }
                    .thenByDescending { it.rating }
                    .thenBy { it.id }
            )
            .toList()

        // UEFA usa a nova projeção tipada de vagas. Ela reordena internamente por associação,
        // divisão e id e deliberadamente ignora rating como falso coeficiente continental.
        if (userConfederation == FootballConfederation.UEFA) {
            val uefaFields = UefaQualificationRules.selectLeaguePhaseFields(continentalCandidates)
            fixtures += UefaCompetitionSystem.generateOpeningFixtures(
                season = season,
                fields = uefaFields
            )
            FixtureScheduleValidator.requireValid(fixtures)
            return fixtures
        }

        val fields = selectContinentalFields(
            candidates = continentalCandidates,
            confederation = userConfederation.code
        )

        if (userConfederation == FootballConfederation.CONMEBOL) {
            fixtures += ConmebolCompetitionSystem.generateOpeningFixtures(
                season = season,
                libertadoresTeams = fields.tier1,
                sudamericanaTeams = fields.tier2
            )
        } else {
            if (fields.tier1.size >= 8 && fields.tier1.size % 4 == 0) {
                fixtures += generateLegacyGroupStage(
                    season = season,
                    teams = fields.tier1.shuffled(Random(stableSeed(season, "CONTINENTAL_T1"))),
                    competitionType = "CONTINENTAL_T1"
                )
            }
            if (fields.tier2.size >= 8 && fields.tier2.size % 4 == 0) {
                fixtures += generateLegacyGroupStage(
                    season = season,
                    teams = fields.tier2.shuffled(Random(stableSeed(season, "CONTINENTAL_T2"))),
                    competitionType = "CONTINENTAL_T2"
                )
            }
        }

        if (fields.tier3.size >= 2) {
            val selected = fields.tier3.shuffled(Random(stableSeed(season, "CONTINENTAL_T3")))
            val startWeek = CONTINENTAL_FINAL_WEEK - roundsToChampion(selected.size) + 1
            fixtures += generateKnockoutRound(
                season = season,
                week = startWeek,
                teamIds = selected.map { it.id },
                competitionType = "CONTINENTAL_T3"
            )
        }

        FixtureScheduleValidator.requireValid(fixtures)
        return fixtures
    }

    internal fun selectContinentalFields(
        candidates: List<Team>,
        confederation: String
    ): ContinentalFields {
        if (ContinentalQualificationQuotaPolicy.hasExplicitPolicy(confederation)) {
            val tier1Plan = ContinentalQualificationQuotaPolicy.planFor(confederation, "CONTINENTAL_T1")
            val tier2Plan = ContinentalQualificationQuotaPolicy.planFor(confederation, "CONTINENTAL_T2")
            val tier3Plan = ContinentalQualificationQuotaPolicy.planFor(confederation, "CONTINENTAL_T3")

            val tier1 = tier1Plan
                ?.let { ContinentalQualificationQuotaPolicy.selectField(candidates, it).teams }
                .orEmpty()
            val tier1Ids = tier1.map { it.id }.toSet()

            val tier2 = tier2Plan
                ?.let {
                    ContinentalQualificationQuotaPolicy.selectField(
                        candidates = candidates,
                        plan = it,
                        excludedTeamIds = tier1Ids
                    ).teams
                }
                .orEmpty()
            val tier2Ids = tier2.map { it.id }.toSet()

            val tier3 = tier3Plan
                ?.let {
                    ContinentalQualificationQuotaPolicy.selectField(
                        candidates = candidates,
                        plan = it,
                        excludedTeamIds = tier1Ids + tier2Ids
                    ).teams
                }
                .orEmpty()

            return ContinentalFields(tier1 = tier1, tier2 = tier2, tier3 = tier3)
        }

        return selectLegacyContinentalFields(candidates)
    }

    private fun selectLegacyContinentalFields(candidates: List<Team>): ContinentalFields {
        var offset = 0
        val groupFields = mutableListOf<List<Team>>()

        repeat(2) {
            val available = (candidates.size - offset).coerceAtLeast(0)
            val participantCount = supportedGroupTeamCount(available)
            if (participantCount == 0) {
                groupFields.add(emptyList())
            } else {
                groupFields.add(candidates.subList(offset, offset + participantCount))
                offset += participantCount
            }
        }

        val remaining = (candidates.size - offset).coerceAtLeast(0)
        val tier3Count = largestPowerOfTwoAtMost(minOf(16, remaining))
        val tier3 = if (tier3Count >= 2) {
            candidates.subList(offset, offset + tier3Count)
        } else {
            emptyList()
        }

        return ContinentalFields(
            tier1 = groupFields.getOrElse(0) { emptyList() },
            tier2 = groupFields.getOrElse(1) { emptyList() },
            tier3 = tier3
        )
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

        // Saves antigos UEFA com apenas CONTINENTAL_T1/T2/T3 continuam no processador legado.
        // Uma temporada nova é detectada pelos códigos concretos e delegada integralmente ao engine.
        if (UefaCompetitionSystem.isUefaSeason(seasonFixtures)) {
            UefaCompetitionSystem.processProgression(
                season = season,
                currentWeek = currentWeek,
                repository = repository
            )
            return
        }

        // Uma temporada nova CONMEBOL usa ida/volta e transferência Libertadores -> Sudamericana.
        // O processador legado jamais deve enxergar esses T1/T2, pois trataria uma perna isolada
        // como rodada eliminatória completa e criaria fixtures incorretos.
        if (ConmebolCompetitionSystem.isConmebolSeason(seasonFixtures)) {
            ConmebolCompetitionSystem.processProgression(
                season = season,
                currentWeek = currentWeek,
                repository = repository
            )

            // Compatibilidade: se um save antigo ainda trouxer T3, permita que ele termine.
            processKnockoutRoundIfPresent(
                season = season,
                currentWeek = currentWeek,
                competitionType = "CONTINENTAL_T3",
                finalWeek = CONTINENTAL_FINAL_WEEK,
                seasonFixtures = repository.getFixturesForSeason(season),
                repository = repository
            )
            return
        }

        if (currentWeek == LEGACY_GROUP_WEEK_3) {
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

    private fun generateLegacyGroupStage(
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

            fixtures += Fixture(season = season, week = LEGACY_GROUP_WEEK_1, matchSlot = MatchSlot.MIDWEEK, homeTeamId = t1, awayTeamId = t4, competitionType = groupCode)
            fixtures += Fixture(season = season, week = LEGACY_GROUP_WEEK_1, matchSlot = MatchSlot.MIDWEEK, homeTeamId = t2, awayTeamId = t3, competitionType = groupCode)
            fixtures += Fixture(season = season, week = LEGACY_GROUP_WEEK_2, matchSlot = MatchSlot.MIDWEEK, homeTeamId = t1, awayTeamId = t3, competitionType = groupCode)
            fixtures += Fixture(season = season, week = LEGACY_GROUP_WEEK_2, matchSlot = MatchSlot.MIDWEEK, homeTeamId = t4, awayTeamId = t2, competitionType = groupCode)
            fixtures += Fixture(season = season, week = LEGACY_GROUP_WEEK_3, matchSlot = MatchSlot.MIDWEEK, homeTeamId = t2, awayTeamId = t1, competitionType = groupCode)
            fixtures += Fixture(season = season, week = LEGACY_GROUP_WEEK_3, matchSlot = MatchSlot.MIDWEEK, homeTeamId = t3, awayTeamId = t4, competitionType = groupCode)
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
                matchSlot = MatchSlot.MIDWEEK,
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
        val runnerUpId = if (winnerId == finalFixture.homeTeamId) finalFixture.awayTeamId else finalFixture.homeTeamId

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
        while (result * 2 <= value) result *= 2
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
