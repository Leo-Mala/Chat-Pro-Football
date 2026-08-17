package com.example.data

import kotlin.random.Random

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
     * As 31 vagas regulares mantêm a composição histórica usada pelo projeto. A 32ª vaga é
     * reservada ao anfitrião determinado pela política da edição, e portanto não fica presa a um
     * clube fixo.
     */
    val defaultSuperMundialClubs = listOf(
        // UEFA (12)
        "Real Madrid", "Manchester City", "Chelsea", "Bayern München", "Paris Saint-Germain",
        "Inter de Milão", "Borussia Dortmund", "FC Porto", "Benfica", "Juventus",
        "Atlético de Madrid", "Red Bull Salzburg",
        // CONMEBOL (6)
        "Palmeiras", "Flamengo", "Fluminense", "River Plate", "Boca Juniors", "Cruzeiro",
        // CONCACAF (4)
        "Monterrey", "Seattle Sounders", "Pachuca", "Club León",
        // CAF (4)
        "Al Ahly", "Wydad Casablanca", "Esperança de Tunis", "Mamelodi Sundowns",
        // AFC (4)
        "Al Hilal", "Urawa Red Diamonds", "Al Ain", "Ulsan HD",
        // OFC (1)
        "Auckland City"
    )

    fun selectParticipants(season: Int, allTeams: List<Team>, userTeamId: Long): List<Team> {
        if (!isSuperMundialSeason(season)) return emptyList()

        val hostTeam = SuperMundialEditionPolicy.hostTeamForSeason(season, allTeams)
        val hostTeamId = hostTeam?.id
        val regularTarget = if (hostTeam != null) 31 else 32
        val selectedTeams = mutableListOf<Team>()
        val realTeamsByName = allTeams.associateBy { it.name }

        fun addUnique(team: Team) {
            if (team.id != hostTeamId && selectedTeams.none { it.id == team.id }) {
                selectedTeams.add(team)
            }
        }

        // Primeiro reaproveita clubes persistidos que correspondem à lista histórica.
        for (clubName in defaultSuperMundialClubs) {
            if (selectedTeams.size >= regularTarget) break
            val exact = realTeamsByName[clubName]
            val fuzzy = allTeams.find {
                it.id != hostTeamId && it.name.contains(clubName, ignoreCase = true)
            }
            (exact ?: fuzzy)?.let(::addUnique)
        }

        // Se algum nome histórico não existir no save, clubes reais persistidos ocupam a vaga
        // antes de qualquer fallback virtual.
        allTeams.asSequence()
            .filter { it.id != hostTeamId && selectedTeams.none { selected -> selected.id == it.id } }
            .sortedWith(compareByDescending<Team> { it.rating }.thenBy { it.id })
            .forEach { candidate ->
                if (selectedTeams.size < regularTarget) addUnique(candidate)
            }

        // Compatibilidade para universos sintéticos/legados pequenos: somente agora recorremos aos
        // clubes virtuais históricos ainda ausentes.
        for (clubName in defaultSuperMundialClubs) {
            if (selectedTeams.size >= regularTarget) break
            if (selectedTeams.any { it.name.equals(clubName, ignoreCase = true) }) continue
            val globalId = GlobalFootballSystem.getGlobalId("Mundial", clubName)
            if (globalId == hostTeamId || selectedTeams.any { it.id == globalId }) continue
            addUnique(
                Team(
                    id = globalId,
                    name = clubName,
                    city = "Mundial",
                    state = "FIFA",
                    country = "Mundial",
                    division = 1,
                    rating = when {
                        clubName in listOf("Real Madrid", "Manchester City", "Bayern München") -> 88
                        clubName in listOf("Paris Saint-Germain", "Inter de Milão", "Chelsea") -> 85
                        clubName in listOf("Palmeiras", "Flamengo", "River Plate", "Al Hilal", "Cruzeiro") -> 82
                        else -> 77
                    },
                    stadiumName = "Estádio Mundial",
                    logoUrl = DefaultData.getLogoForTeam(clubName, "Mundial")
                )
            )
        }

        var dummyCounter = 1
        while (selectedTeams.size < regularTarget) {
            val virtualTeam = GlobalFootballSystem.getVirtualTeam(900_000L + dummyCounter)
            if (virtualTeam.id != hostTeamId && selectedTeams.none { it.id == virtualTeam.id }) {
                selectedTeams.add(virtualTeam)
            }
            dummyCounter++
        }

        // Mantém a compatibilidade histórica do projeto em que o clube do usuário pode participar,
        // mas nunca ocupa nem duplica a vaga do anfitrião.
        val userTeam = allTeams.find { it.id == userTeamId }
        if (userTeam != null && userTeam.id != hostTeamId && selectedTeams.none { it.id == userTeam.id }) {
            if (selectedTeams.size < regularTarget) {
                selectedTeams.add(userTeam)
            } else if (selectedTeams.isNotEmpty()) {
                selectedTeams[selectedTeams.lastIndex] = userTeam
            }
        }

        if (hostTeam != null && selectedTeams.none { it.id == hostTeam.id }) {
            selectedTeams.add(hostTeam)
        }

        return selectedTeams
            .distinctBy { it.id }
            .take(32)
    }

    fun generateGroupStageFixtures(season: Int, allTeams: List<Team>, userTeamId: Long): List<Fixture> {
        if (!isSuperMundialSeason(season)) return emptyList()

        val selectedTeams = selectParticipants(season, allTeams, userTeamId)
        if (selectedTeams.size != 32) return emptyList()

        val shuffled = selectedTeams.shuffled(Random(season.toLong()))
        val groupLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        val fixtures = mutableListOf<Fixture>()

        for (i in 0 until 8) {
            val groupLetter = groupLetters[i]
            val startIndex = i * 4
            val groupTeams = shuffled.subList(startIndex, startIndex + 4)
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

    private data class TempStanding(
        val teamId: Long,
        var points: Int = 0,
        var gd: Int = 0,
        var gf: Int = 0
    )

    suspend fun processProgression(season: Int, currentWeek: Int, repo: GameRepository) {
        if (!isSuperMundialSeason(season)) return

        val allSeasonFixtures = repo.getFixturesForSeason(season)

        when (currentWeek) {
            GROUP_WEEK_3 -> {
                val groupFixtures = allSeasonFixtures.filter { it.competitionType.startsWith("WORLD_CUP_GP_") }
                if (groupFixtures.isEmpty() || groupFixtures.any { !it.isPlayed }) return

                val existingOitavas = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == ROUND_OF_16_WEEK }
                if (existingOitavas.isNotEmpty()) return

                val groupLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
                val groupQualifiers = mutableMapOf<String, Pair<Long, Long>>()

                for (letter in groupLetters) {
                    val compCode = "WORLD_CUP_GP_$letter"
                    val matches = groupFixtures.filter { it.competitionType == compCode }
                    val teamIds = matches.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.distinct()
                    val map = teamIds.associateWith { TempStanding(it) }.toMutableMap()

                    for (m in matches) {
                        val h = map[m.homeTeamId] ?: continue
                        val a = map[m.awayTeamId] ?: continue
                        val hG = m.homeScore ?: 0
                        val aG = m.awayScore ?: 0

                        h.gf += hG
                        a.gf += aG
                        h.gd += (hG - aG)
                        a.gd += (aG - hG)

                        if (hG > aG) h.points += 3
                        else if (aG > hG) a.points += 3
                        else {
                            h.points += 1
                            a.points += 1
                        }
                    }

                    val sorted = map.values.sortedWith(
                        compareByDescending<TempStanding> { it.points }
                            .thenByDescending { it.gd }
                            .thenByDescending { it.gf }
                            .thenBy { it.teamId }
                    )

                    if (sorted.size >= 2) {
                        groupQualifiers[letter] = Pair(sorted[0].teamId, sorted[1].teamId)
                    }
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
                    if (oitavasFixtures.isNotEmpty()) repo.saveFixtures(oitavasFixtures)
                }
            }

            ROUND_OF_16_WEEK -> {
                val oitavas = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == ROUND_OF_16_WEEK }
                if (oitavas.isEmpty() || oitavas.any { !it.isPlayed }) return

                val existingQuartas = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == QUARTERFINAL_WEEK }
                if (existingQuartas.isNotEmpty()) return

                val winners = oitavas.map { getWinner(it) }
                if (winners.size == 8) {
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
                if (quartas.isEmpty() || quartas.any { !it.isPlayed }) return

                val existingSemis = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == SEMIFINAL_WEEK }
                if (existingSemis.isNotEmpty()) return

                val winners = quartas.map { getWinner(it) }
                if (winners.size == 4) {
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
                if (semis.isEmpty() || semis.any { !it.isPlayed }) return

                val existingFinal = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == FINAL_WEEK }
                if (existingFinal.isNotEmpty()) return

                val winners = semis.map { getWinner(it) }
                if (winners.size == 2) {
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
                val finalMatch = allSeasonFixtures.find { it.competitionType == "WORLD_CUP" && it.week == FINAL_WEEK }
                if (finalMatch != null && finalMatch.isPlayed) {
                    val winnerId = getWinner(finalMatch)
                    val runnerUpId = if (winnerId == finalMatch.homeTeamId) finalMatch.awayTeamId else finalMatch.homeTeamId

                    val winnerTeam = repo.getTeam(winnerId) ?: GlobalFootballSystem.getVirtualTeam(winnerId)
                    val runnerUpTeam = repo.getTeam(runnerUpId) ?: GlobalFootballSystem.getVirtualTeam(runnerUpId)
                    val hostCountry = SuperMundialEditionPolicy.hostCountryForSeason(
                        season,
                        repo.getAllTeams()
                    ) ?: "Sede não definida"

                    val existingRecords = repo.getAllHistoricalRecords()
                    val alreadySaved = existingRecords.any { it.season == season && it.competitionName.contains("Mundial") }

                    if (!alreadySaved) {
                        repo.saveRecord(
                            HistoricalRecord(
                                season = season,
                                competitionName = "Super Mundial de Clubes 🌍 — Sede: $hostCountry",
                                championTeamName = winnerTeam.name,
                                runnerUpTeamName = runnerUpTeam.name,
                                topScorerName = "Destaque Mundial",
                                topScorerGoals = 5,
                                topScorerTeam = winnerTeam.name
                            )
                        )
                    }
                }
            }
        }
    }

    private fun getWinner(f: Fixture): Long {
        val hS = f.homeScore ?: 0
        val aS = f.awayScore ?: 0
        return when {
            hS > aS -> f.homeTeamId
            aS > hS -> f.awayTeamId
            (f.homePenalties ?: 0) > (f.awayPenalties ?: 0) -> f.homeTeamId
            (f.awayPenalties ?: 0) > (f.homePenalties ?: 0) -> f.awayTeamId
            else -> minOf(f.homeTeamId, f.awayTeamId)
        }
    }
}
