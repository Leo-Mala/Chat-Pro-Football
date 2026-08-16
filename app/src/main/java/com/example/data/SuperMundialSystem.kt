package com.example.data

import kotlin.random.Random

object SuperMundialSystem {

    fun isSuperMundialSeason(season: Int): Boolean {
        // Quadrennial: 2025 (real world), 2026 (inaugural in-game season), 2030, 2034, 2038...
        return season == 2025 || season == 2026 || (season - 2026) % 4 == 0
    }

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
        "Auckland City",
        // HOST (1)
        "Inter Miami"
    )

    fun generateGroupStageFixtures(season: Int, allTeams: List<Team>, userTeamId: Long): List<Fixture> {
        if (!isSuperMundialSeason(season)) return emptyList()

        val selectedTeams = mutableListOf<Team>()
        val teamMap = allTeams.associateBy { it.name }

        for (clubName in defaultSuperMundialClubs) {
            val team = teamMap[clubName] ?: allTeams.find { it.name.contains(clubName, ignoreCase = true) }
            if (team != null) {
                if (selectedTeams.none { it.id == team.id }) {
                    selectedTeams.add(team)
                }
            } else {
                val globalId = GlobalFootballSystem.getGlobalId("Mundial", clubName)
                if (selectedTeams.none { it.id == globalId }) {
                    selectedTeams.add(
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
            }
        }

        // Ensure user team is included in the 32 teams if valid
        val userTeam = allTeams.find { it.id == userTeamId }
        if (userTeam != null && selectedTeams.none { it.id == userTeamId }) {
            if (selectedTeams.size >= 32) {
                selectedTeams[31] = userTeam
            } else {
                selectedTeams.add(userTeam)
            }
        }

        val targetCount = 32
        // Padrão corrigido e protegido contra loop infinito
        while (selectedTeams.size < targetCount) {
            val candidate = allTeams.filter { t -> selectedTeams.none { it.id == t.id } }
                .maxByOrNull { it.rating }
            if (candidate != null) {
                selectedTeams.add(candidate)
            } else {
                break // Interrompe o laço imediatamente se não houver mais candidatos!
            }
        }

        // Preenchimento de segurança se a lista ainda não tiver atingido a meta
        var dummyCounter = 1
        while (selectedTeams.size < targetCount) {
            val virtualTeam = GlobalFootballSystem.getVirtualTeam(900_000L + dummyCounter)
            if (selectedTeams.none { it.id == virtualTeam.id }) {
                selectedTeams.add(virtualTeam)
            }
            dummyCounter++
        }

        val shuffled = selectedTeams.take(32).shuffled(Random(season.toLong()))
        val groupLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        val fixtures = mutableListOf<Fixture>()

        for (i in 0 until 8) {
            val groupLetter = groupLetters[i]
            val startIndex = i * 4
            val endIndex = (i + 1) * 4
            if (endIndex > shuffled.size) break

            val groupTeams = shuffled.subList(startIndex, endIndex)
            val compCode = "WORLD_CUP_GP_$groupLetter"

            val t1 = groupTeams[0].id
            val t2 = groupTeams[1].id
            val t3 = groupTeams[2].id
            val t4 = groupTeams[3].id

            // Round 1 - Week 34
            fixtures.add(Fixture(season = season, week = 34, homeTeamId = t1, awayTeamId = t4, competitionType = compCode))
            fixtures.add(Fixture(season = season, week = 34, homeTeamId = t2, awayTeamId = t3, competitionType = compCode))

            // Round 2 - Week 35
            fixtures.add(Fixture(season = season, week = 35, homeTeamId = t1, awayTeamId = t3, competitionType = compCode))
            fixtures.add(Fixture(season = season, week = 35, homeTeamId = t4, awayTeamId = t2, competitionType = compCode))

            // Round 3 - Week 36
            fixtures.add(Fixture(season = season, week = 36, homeTeamId = t2, awayTeamId = t1, competitionType = compCode))
            fixtures.add(Fixture(season = season, week = 36, homeTeamId = t3, awayTeamId = t4, competitionType = compCode))
        }

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
            36 -> {
                // Group stage completed -> Generate Oitavas de Final (Week 37)
                val groupFixtures = allSeasonFixtures.filter { it.competitionType.startsWith("WORLD_CUP_GP_") }
                if (groupFixtures.isEmpty() || groupFixtures.any { !it.isPlayed }) return

                val existingOitavas = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == 37 }
                if (existingOitavas.isNotEmpty()) return

                val groupLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
                val groupQualifiers = mutableMapOf<String, Pair<Long, Long>>() // Letter -> Pair(1st, 2nd)

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

                        if (hG > aG) {
                            h.points += 3
                        } else if (aG > hG) {
                            a.points += 3
                        } else {
                            h.points += 1
                            a.points += 1
                        }
                    }

                    val sorted = map.values.sortedWith(
                        compareByDescending<TempStanding> { it.points }
                            .thenByDescending { it.gd }
                            .thenByDescending { it.gf }
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
                                    week = 37,
                                    homeTeamId = firstTeam,
                                    awayTeamId = secondTeam,
                                    competitionType = "WORLD_CUP"
                                )
                            )
                        }
                    }
                    if (oitavasFixtures.isNotEmpty()) {
                        repo.saveFixtures(oitavasFixtures)
                    }
                }
            }

            37 -> {
                // Oitavas completed -> Generate Quartas de Final (Week 38)
                val oitavas = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == 37 }
                if (oitavas.isEmpty() || oitavas.any { !it.isPlayed }) return

                val existingQuartas = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == 38 }
                if (existingQuartas.isNotEmpty()) return

                val winners = oitavas.map { getWinner(it) }
                if (winners.size == 8) {
                    val quartasFixtures = mutableListOf<Fixture>()
                    for (i in 0 until 4) {
                        quartasFixtures.add(
                            Fixture(
                                season = season,
                                week = 38,
                                homeTeamId = winners[i * 2],
                                awayTeamId = winners[i * 2 + 1],
                                competitionType = "WORLD_CUP"
                            )
                        )
                    }
                    repo.saveFixtures(quartasFixtures)
                }
            }

            38 -> {
                // Quartas completed -> Generate Semifinais (Week 39)
                val quartas = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == 38 }
                if (quartas.isEmpty() || quartas.any { !it.isPlayed }) return

                val existingSemis = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == 39 }
                if (existingSemis.isNotEmpty()) return

                val winners = quartas.map { getWinner(it) }
                if (winners.size == 4) {
                    val semisFixtures = mutableListOf<Fixture>()
                    semisFixtures.add(
                        Fixture(season = season, week = 39, homeTeamId = winners[0], awayTeamId = winners[1], competitionType = "WORLD_CUP")
                    )
                    semisFixtures.add(
                        Fixture(season = season, week = 39, homeTeamId = winners[2], awayTeamId = winners[3], competitionType = "WORLD_CUP")
                    )
                    repo.saveFixtures(semisFixtures)
                }
            }

            39 -> {
                // Semifinais completed -> Generate Final (Week 40)
                val semis = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == 39 }
                if (semis.isEmpty() || semis.any { !it.isPlayed }) return

                val existingFinal = allSeasonFixtures.filter { it.competitionType == "WORLD_CUP" && it.week == 40 }
                if (existingFinal.isNotEmpty()) return

                val winners = semis.map { getWinner(it) }
                if (winners.size == 2) {
                    repo.saveFixtures(
                        listOf(
                            Fixture(season = season, week = 40, homeTeamId = winners[0], awayTeamId = winners[1], competitionType = "WORLD_CUP")
                        )
                    )
                }
            }

            40 -> {
                // Final completed -> Record Champion
                val finalMatch = allSeasonFixtures.find { it.competitionType == "WORLD_CUP" && it.week == 40 }
                if (finalMatch != null && finalMatch.isPlayed) {
                    val winnerId = getWinner(finalMatch)
                    val runnerUpId = if (winnerId == finalMatch.homeTeamId) finalMatch.awayTeamId else finalMatch.homeTeamId

                    val winnerTeam = repo.getTeam(winnerId) ?: GlobalFootballSystem.getVirtualTeam(winnerId)
                    val runnerUpTeam = repo.getTeam(runnerUpId) ?: GlobalFootballSystem.getVirtualTeam(runnerUpId)

                    val existingRecords = repo.getAllHistoricalRecords()
                    val alreadySaved = existingRecords.any { it.season == season && it.competitionName.contains("Mundial") }

                    if (!alreadySaved) {
                        repo.saveRecord(
                            HistoricalRecord(
                                season = season,
                                competitionName = "Super Mundial de Clubes 🌍",
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
            else -> if (Random.nextBoolean()) f.homeTeamId else f.awayTeamId
        }
    }
}
