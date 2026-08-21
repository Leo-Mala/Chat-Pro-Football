package com.example.data

object SuperMundialSystem {

    const val GROUP_WEEK_1 = 42
    const val GROUP_WEEK_2 = 43
    const val GROUP_WEEK_3 = 44
    const val ROUND_OF_16_WEEK = 45
    const val QUARTERFINAL_WEEK = 46
    const val SEMIFINAL_WEEK = 47
    const val FINAL_WEEK = GameCalendar.WEEKS_PER_SEASON

    fun isSuperMundialSeason(season: Int): Boolean =
        SuperMundialEditionPolicy.isEditionSeason(season)

    /**
     * Compatibilidade de assinatura. O clube controlado não recebe mais promoção automática:
     * participar do Mundial depende exclusivamente do field esportivo.
     */
    @Suppress("UNUSED_PARAMETER")
    fun selectParticipants(season: Int, allTeams: List<Team>, userTeamId: Long): List<Team> =
        selectParticipants(season, allTeams, previousSeasonStandings = emptyList())

    fun selectParticipants(
        season: Int,
        allTeams: List<Team>,
        previousSeasonStandings: List<GlobalLeagueStanding>
    ): List<Team> =
        SuperMundialQualificationRules
            .selectField(season, allTeams, previousSeasonStandings)
            ?.teams
            .orEmpty()

    @Suppress("UNUSED_PARAMETER")
    fun generateGroupStageFixtures(season: Int, allTeams: List<Team>, userTeamId: Long): List<Fixture> =
        generateGroupStageFixtures(season, allTeams, previousSeasonStandings = emptyList())

    fun generateGroupStageFixtures(
        season: Int,
        allTeams: List<Team>,
        previousSeasonStandings: List<GlobalLeagueStanding>
    ): List<Fixture> {
        if (!isSuperMundialSeason(season)) return emptyList()

        val selectedTeams = selectParticipants(season, allTeams, previousSeasonStandings)
        if (selectedTeams.size != SuperMundialQualificationRules.FIELD_SIZE) return emptyList()

        val groups = WorldClubDrawEngine.drawGroups(season, selectedTeams)
        if (groups.size != 8 || groups.any { it.size != 4 }) return emptyList()

        val groupLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        val fixtures = mutableListOf<Fixture>()

        for (i in 0 until 8) {
            val groupLetter = groupLetters[i]
            val groupTeams = groups[i]
            val compCode = "WORLD_CUP_GP_$groupLetter"

            val t1 = groupTeams[0].id
            val t2 = groupTeams[1].id
            val t3 = groupTeams[2].id
            val t4 = groupTeams[3].id

            fixtures.add(Fixture(season = season, week = GROUP_WEEK_1, matchSlot = MatchSlot.MIDWEEK, homeTeamId = t1, awayTeamId = t4, competitionType = compCode))
            fixtures.add(Fixture(season = season, week = GROUP_WEEK_1, matchSlot = MatchSlot.MIDWEEK, homeTeamId = t2, awayTeamId = t3, competitionType = compCode))

            fixtures.add(Fixture(season = season, week = GROUP_WEEK_2, matchSlot = MatchSlot.MIDWEEK, homeTeamId = t1, awayTeamId = t3, competitionType = compCode))
            fixtures.add(Fixture(season = season, week = GROUP_WEEK_2, matchSlot = MatchSlot.MIDWEEK, homeTeamId = t4, awayTeamId = t2, competitionType = compCode))

            fixtures.add(Fixture(season = season, week = GROUP_WEEK_3, matchSlot = MatchSlot.MIDWEEK, homeTeamId = t2, awayTeamId = t1, competitionType = compCode))
            fixtures.add(Fixture(season = season, week = GROUP_WEEK_3, matchSlot = MatchSlot.MIDWEEK, homeTeamId = t3, awayTeamId = t4, competitionType = compCode))
        }

        FixtureScheduleValidator.requireValid(fixtures)
        return fixtures
    }

    suspend fun processProgression(season: Int, currentWeek: Int, repo: GameRepository) {
        if (!isSuperMundialSeason(season)) return

        val allSeasonFixtures = repo.getFixturesForSeason(season)

        when (currentWeek) {
            GROUP_WEEK_3 -> {
                val groupFixtures = allSeasonFixtures.filter { it.competitionType.startsWith("WORLD_CUP_GP_") }
                if (groupFixtures.size != 48 || groupFixtures.any { !it.isPlayed }) return

                val existingOitavas = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == ROUND_OF_16_WEEK }
                if (existingOitavas.isNotEmpty()) return

                val groupLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
                val groupQualifiers = mutableMapOf<String, Pair<Long, Long>>()

                for (letter in groupLetters) {
                    val compCode = "WORLD_CUP_GP_$letter"
                    val matches = groupFixtures.filter { it.competitionType == compCode }
                    if (matches.size != 6) return
                    val teamIds = matches.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.distinct()
                    if (teamIds.size != 4 || teamIds.any { id -> matches.count { it.homeTeamId == id || it.awayTeamId == id } != 3 }) {
                        return
                    }

                    val ranking = FifaClubWorldCupRules.groupRanking(matches)
                    if (ranking.size != 4) return
                    groupQualifiers[letter] = ranking[0] to ranking[1]
                }

                if (groupQualifiers.size == 8) {
                    val oitavasFixtures = mutableListOf<Fixture>()
                    val pairs = listOf(
                        Pair("A", "B"), Pair("C", "D"), Pair("E", "F"), Pair("G", "H"),
                        Pair("B", "A"), Pair("D", "C"), Pair("F", "E"), Pair("H", "G")
                    )
                    for ((g1, g2) in pairs) {
                        val firstTeam = groupQualifiers[g1]?.first
                        val secondTeam = groupQualifiers[g2]?.second
                        if (firstTeam != null && secondTeam != null) {
                            oitavasFixtures.add(
                                Fixture(
                                    season = season,
                                    week = ROUND_OF_16_WEEK,
                                    matchSlot = MatchSlot.MIDWEEK,
                                    homeTeamId = firstTeam,
                                    awayTeamId = secondTeam,
                                    competitionType = "WORLD_CUP"
                                )
                            )
                        }
                    }
                    if (oitavasFixtures.size == 8) repo.saveFixtures(oitavasFixtures)
                }
            }

            ROUND_OF_16_WEEK -> {
                val oitavas = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == ROUND_OF_16_WEEK }
                if (oitavas.size != 8 || oitavas.any { !it.isPlayed }) return

                val existingQuartas = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == QUARTERFINAL_WEEK }
                if (existingQuartas.isNotEmpty()) return

                val winners = oitavas.mapNotNull(::getWinner)
                if (winners.size == 8 && winners.toSet().size == 8) {
                    val quartasFixtures = mutableListOf<Fixture>()
                    for (i in 0 until 4) {
                        quartasFixtures.add(
                            Fixture(
                                season = season,
                                week = QUARTERFINAL_WEEK,
                                matchSlot = MatchSlot.MIDWEEK,
                                homeTeamId = winners[i * 2],
                                awayTeamId = winners[i * 2 + 1],
                                competitionType = "WORLD_CUP"
                            )
                        )
                    }
                    repo.saveFixtures(quartasFixtures)
                }
            }

            QUARTERFINAL_WEEK -> {
                val quartas = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == QUARTERFINAL_WEEK }
                if (quartas.size != 4 || quartas.any { !it.isPlayed }) return

                val existingSemis = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == SEMIFINAL_WEEK }
                if (existingSemis.isNotEmpty()) return

                val winners = quartas.mapNotNull(::getWinner)
                if (winners.size == 4 && winners.toSet().size == 4) {
                    repo.saveFixtures(
                        listOf(
                            Fixture(season = season, week = SEMIFINAL_WEEK, matchSlot = MatchSlot.MIDWEEK, homeTeamId = winners[0], awayTeamId = winners[1], competitionType = "WORLD_CUP"),
                            Fixture(season = season, week = SEMIFINAL_WEEK, matchSlot = MatchSlot.MIDWEEK, homeTeamId = winners[2], awayTeamId = winners[3], competitionType = "WORLD_CUP")
                        )
                    )
                }
            }

            SEMIFINAL_WEEK -> {
                val semis = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == SEMIFINAL_WEEK }
                if (semis.size != 2 || semis.any { !it.isPlayed }) return

                val existingFinal = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == FINAL_WEEK }
                if (existingFinal.isNotEmpty()) return

                val winners = semis.mapNotNull(::getWinner)
                if (winners.size == 2 && winners.toSet().size == 2) {
                    repo.saveFixtures(
                        listOf(
                            Fixture(
                                season = season,
                                week = FINAL_WEEK,
                                matchSlot = MatchSlot.MIDWEEK,
                                homeTeamId = winners[0],
                                awayTeamId = winners[1],
                                competitionType = "WORLD_CUP"
                            )
                        )
                    )
                }
            }

            FINAL_WEEK -> {
                val finalMatch = allSeasonFixtures.singleOrNull {
                    it.competitionType == "WORLD_CUP" && it.week == FINAL_WEEK
                } ?: return
                if (!finalMatch.isPlayed) return

                val decidedFinal = CompetitionRules.ensureKnockoutDecision(finalMatch)
                if (decidedFinal != finalMatch) repo.updateFixture(decidedFinal)
                val winnerId = CompetitionRules.winnerOf(decidedFinal) ?: return
                val runnerUpId = if (winnerId == decidedFinal.homeTeamId) decidedFinal.awayTeamId else decidedFinal.homeTeamId

                // Fail-closed: uma final com clube inexistente não pode materializar nome virtual.
                val winnerTeam = repo.getTeam(winnerId) ?: return
                val runnerUpTeam = repo.getTeam(runnerUpId) ?: return
                val eligibleTeams = SuperMundialQualificationRules.eligibleRealTeams(repo.getAllTeams())
                val hostCountry = SuperMundialEditionPolicy.hostCountryForSeason(
                    season,
                    eligibleTeams
                ) ?: return

                val existingRecords = repo.getAllHistoricalRecords()
                val alreadySaved = existingRecords.any {
                    it.season == season && it.competitionName.startsWith("Super Mundial de Clubes")
                }

                if (!alreadySaved) {
                    repo.saveRecord(
                        HistoricalRecord(
                            season = season,
                            competitionName = "Super Mundial de Clubes 🌍 — Sede: $hostCountry",
                            championTeamName = winnerTeam.name,
                            runnerUpTeamName = runnerUpTeam.name,
                            topScorerName = "",
                            topScorerGoals = 0,
                            topScorerTeam = ""
                        )
                    )
                }
            }
        }
    }

    private fun getWinner(fixture: Fixture): Long? =
        CompetitionRules.winnerOf(fixture)
}
