package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.support.RelationalIntegrityAssertions
import com.example.usecase.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OneHundredSeasonMatchByMatchStressTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var generateCalendarUseCase: GenerateCalendarUseCase
    private lateinit var databaseIntegrityUseCase: DatabaseIntegrityUseCase
    private lateinit var processTransfersUseCase: ProcessTransfersUseCase
    private lateinit var cpuSquadManagementUseCase: CpuSquadManagementUseCase
    private lateinit var financeUseCase: FinanceUseCase
    private lateinit var playerEvolutionUseCase: PlayerEvolutionUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = GameRepository(db)
        generateCalendarUseCase = GenerateCalendarUseCase(repository)
        databaseIntegrityUseCase = DatabaseIntegrityUseCase(repository)
        processTransfersUseCase = ProcessTransfersUseCase(repository)
        cpuSquadManagementUseCase = CpuSquadManagementUseCase(repository)
        financeUseCase = FinanceUseCase(repository)
        playerEvolutionUseCase = PlayerEvolutionUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun execute100SeasonMatchByMatchStressTest() = runBlocking {
        val cruzeiro = Team(
            id = 100L,
            name = "Cruzeiro",
            city = "Belo Horizonte",
            state = "MG",
            division = 1,
            country = "Brasil",
            rating = 99,
            trainingCenterLevel = 5
        )

        val teamNames = listOf(
            "Atlético-MG", "Flamengo", "Palmeiras", "São Paulo", "Fluminense",
            "Grêmio", "Internacional", "Botafogo", "Vasco", "Corinthians",
            "Bahia", "Athletico-PR", "Santos", "Bragantino", "Vitória",
            "Coritiba", "Chapecoense", "Mirassol", "Remo"
        )

        val leagueTeams = mutableListOf(cruzeiro)
        teamNames.forEachIndexed { idx, name ->
            leagueTeams.add(
                Team(
                    id = 101L + idx,
                    name = name,
                    city = "Brasil",
                    state = "BR",
                    division = 1,
                    country = "Brasil",
                    rating = 75 + (idx % 12)
                )
            )
        }

        // O stress precisa de um universo mundial estruturalmente válido para exercer as 25
        // edições quadrienais. Estes clubes são fixtures sintéticos de teste, distribuídos somente
        // entre associações nacionais tipadas; não são fillers de produção nem alteram o dataset FC26.
        // Há pelo menos quota+1 clubes em cada confederação que pode receber a sede, para que a vaga
        // do anfitrião não torne o field de 32 insolúvel ao longo do rodízio centenário.
        val worldSupportTeams = listOf(
            Team(301L, "Argentina Host", "Buenos Aires", "AR", "Argentina", 1, rating = 84),
            Team(302L, "Espanha Host", "Madrid", "ES", "Espanha", 1, rating = 88),
            Team(303L, "França Host", "Paris", "FR", "França", 1, rating = 87),
            Team(304L, "Inglaterra Host", "London", "EN", "Inglaterra", 1, rating = 91),
            Team(305L, "Japão Host", "Tokyo", "JP", "Japão", 1, rating = 82),
            Team(306L, "Itália Support", "Roma", "IT", "Itália", 1, rating = 86),
            Team(307L, "Alemanha Support", "Berlin", "DE", "Alemanha", 1, rating = 89),
            Team(308L, "Portugal Support", "Lisboa", "PT", "Portugal", 1, rating = 85),
            Team(309L, "Países Baixos Support", "Amsterdam", "NL", "Países Baixos", 1, rating = 84),
            Team(310L, "Bélgica Support", "Bruxelas", "BE", "Bélgica", 1, rating = 83),
            Team(311L, "Turquia Support", "Istambul", "TR", "Turquia", 1, rating = 82),
            Team(312L, "Escócia Support", "Glasgow", "SC", "Escócia", 1, rating = 81),
            Team(313L, "Áustria Support", "Viena", "AT", "Áustria", 1, rating = 80),
            Team(314L, "Suíça Support", "Zurique", "CH", "Suíça", 1, rating = 80),
            Team(315L, "Dinamarca Support", "Copenhague", "DK", "Dinamarca", 1, rating = 79),
            Team(316L, "Coreia do Sul Support", "Seul", "KR", "Coreia do Sul", 1, rating = 81),
            Team(317L, "Arábia Saudita Support", "Riad", "SA", "Arábia Saudita", 1, rating = 80),
            Team(318L, "Emirados Árabes Unidos Support", "Abu Dhabi", "AE", "Emirados Árabes Unidos", 1, rating = 79),
            Team(319L, "Catar Support", "Doha", "QA", "Catar", 1, rating = 78),
            Team(320L, "Egito Support A", "Cairo", "EG", "Egito", 1, rating = 83),
            Team(321L, "Egito Support B", "Alexandria", "EG", "Egito", 1, rating = 81),
            Team(322L, "Marrocos Support", "Casablanca", "MA", "Marrocos", 1, rating = 82),
            Team(323L, "Tunísia Support", "Túnis", "TN", "Tunísia", 1, rating = 79),
            Team(324L, "África do Sul Support", "Joanesburgo", "ZA", "África do Sul", 1, rating = 78),
            Team(325L, "México Support", "Cidade do México", "MX", "México", 1, rating = 83),
            Team(326L, "Estados Unidos / Canadá Support", "Toronto", "NA", "Estados Unidos / Canadá", 1, rating = 82),
            Team(327L, "Costa Rica Support", "San José", "CR", "Costa Rica", 1, rating = 79),
            Team(328L, "Guatemala Support", "Cidade da Guatemala", "GT", "Guatemala", 1, rating = 77),
            Team(329L, "Honduras Support", "Tegucigalpa", "HN", "Honduras", 1, rating = 76)
        )
        val allTeams = leagueTeams + worldSupportTeams
        val cpuTeamIds = allTeams.filter { it.id != cruzeiro.id }.map { it.id }.toSet()
        repository.saveTeams(allTeams)

        val cruzeiroPlayers = mutableListOf<Player>()
        for (i in 1..25) {
            val pos = when (i) {
                1, 2 -> "GOL"
                in 3..8 -> "ZAG"
                in 9..12 -> "LAT"
                in 13..18 -> "MEI"
                else -> "ATA"
            }
            cruzeiroPlayers.add(
                Player(
                    id = 1000L + i,
                    teamId = cruzeiro.id,
                    name = "Craque $i",
                    age = 20 + (i % 10),
                    position = pos,
                    force = 99,
                    salary = 150000L,
                    contractDurationWeeks = 6_000
                )
            )
        }
        repository.savePlayers(cruzeiroPlayers)

        for (team in allTeams.filter { it.id != cruzeiro.id }) {
            repository.savePlayers(
                DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
            )
        }

        var gameSave = GameSave(
            coachName = "Técnico Centenário",
            coachReputation = 99,
            currentWeek = 1,
            currentSeason = 2026,
            playerTeamId = cruzeiro.id,
            bankBalance = 500000000L,
            stadiumCapacity = 62000,
            ticketPrice = 60.0,
            sponsorWeekly = 3000000L,
            hasHiredCoach = true,
            hasHiredPhysio = true
        )
        repository.saveGameSave(gameSave)

        var totalMatchesSimulated = 0
        var totalLeagueMatchesSimulated = 0
        var totalDomesticCupMatchesSimulated = 0
        var totalContinentalMatchesSimulated = 0
        var totalWorldGroupMatchesSimulated = 0
        var totalWorldKnockoutMatchesSimulated = 0
        var minimumCpuRosterObserved = Int.MAX_VALUE
        var maximumCpuRosterObserved = 0
        var teamsWithoutGoalkeeperObserved = 0
        var invalidLoansObserved = 0
        val observedWorldSeasons = mutableListOf<Int>()
        val observedHostCountries = mutableListOf<String>()
        val startSeason = 2026
        val totalSeasons = 100

        for (seasonOffset in 0 until totalSeasons) {
            val currentSeason = startSeason + seasonOffset
            gameSave = gameSave.copy(currentSeason = currentSeason, currentWeek = 1)
            repository.saveGameSave(gameSave)

            val expectedWorld = currentSeason >= 2025 && (currentSeason - 2025) % 4 == 0
            assertEquals(expectedWorld, SuperMundialSystem.isSuperMundialSeason(currentSeason))
            val expectedHost = if (expectedWorld) {
                observedWorldSeasons.add(currentSeason)
                requireNotNull(
                    SuperMundialEditionPolicy.editionForSeason(currentSeason, repository.getAllTeams())
                ).also { observedHostCountries.add(it.hostCountry) }
            } else {
                null
            }

            val roster = repository.getPlayersByTeam(cruzeiro.id)
            val olderPlayers = roster.filter { it.age >= 30 }
            for (player in olderPlayers) {
                val sellResult = processTransfersUseCase.sellPlayer(gameSave, player, player.calculateMarketValue())
                if (sellResult is ProcessTransfersUseCase.TransferResult.Success) {
                    gameSave = sellResult.updatedSave
                }
            }

            val currentRoster = repository.getPlayersByTeam(cruzeiro.id)
            if (currentRoster.size < 20) {
                val needed = 20 - currentRoster.size
                val updatedList = currentRoster.toMutableList()
                for (i in 0 until needed) {
                    updatedList.add(
                        Player(
                            id = 1000000L + seasonOffset * 100 + i,
                            teamId = cruzeiro.id,
                            name = "Craque Cruzeiro ${seasonOffset}_$i",
                            age = 20,
                            position = if (i % 4 == 0) "ATA" else if (i % 4 == 1) "MEI" else if (i % 4 == 2) "ZAG" else "GOL",
                            force = 95,
                            energy = 100,
                            moral = 100,
                            contractDurationWeeks = 6_000
                        )
                    )
                }
                repository.savePlayers(updatedList)
            } else {
                repository.savePlayers(
                    currentRoster.map {
                        it.copy(force = 95, energy = 100, contractDurationWeeks = 6_000)
                    }
                )
            }

            val openingCpuReport = cpuSquadManagementUseCase.ensureCpuSquadIntegrity()
            assertTrue(openingCpuReport.minimumRosterSize >= 16)
            assertTrue(openingCpuReport.maximumRosterSize <= 35)
            assertEquals(0, openingCpuReport.teamsWithoutGoalkeeper)
            assertEquals(0, openingCpuReport.invalidActiveLoans)

            val seasonTransitionUseCase = SeasonTransitionUseCase(repository, generateCalendarUseCase, databaseIntegrityUseCase)
            if (repository.getFixturesForSeason(currentSeason).isEmpty()) {
                repository.saveFixtures(
                    generateCalendarUseCase.generateSeasonFixtures(
                        season = currentSeason,
                        teams = repository.getAllTeams(),
                        userTeamId = cruzeiro.id,
                        userCountry = cruzeiro.country
                    )
                )
            }

            val openingWorldGroups = repository.getFixturesForSeason(currentSeason)
                .filter { it.competitionType.startsWith("WORLD_CUP_GP_") }
            if (expectedWorld) {
                assertEquals(48, openingWorldGroups.size)
                val participants = openingWorldGroups
                    .flatMap { listOf(it.homeTeamId, it.awayTeamId) }
                    .toSet()
                assertEquals(32, participants.size)
                assertTrue(requireNotNull(expectedHost).hostTeamId in participants)
            } else {
                assertTrue(openingWorldGroups.isEmpty())
            }

            for (week in 1..GameCalendar.WEEKS_PER_SEASON) {
                gameSave = gameSave.copy(currentWeek = week)
                repository.saveGameSave(gameSave)

                val weekFixtures = repository.getFixturesForWeek(currentSeason, week)
                    .sortedWith(FixtureScheduleValidator.chronologicalComparator())
                val unplayed = weekFixtures.filter { !it.isPlayed }

                for (fixture in unplayed) {
                    val homeTeam = repository.getTeam(fixture.homeTeamId)
                        ?: GlobalFootballSystem.getVirtualTeam(fixture.homeTeamId)
                    val awayTeam = repository.getTeam(fixture.awayTeamId)
                        ?: GlobalFootballSystem.getVirtualTeam(fixture.awayTeamId)
                    val homePlayers = repository.getPlayersByTeam(homeTeam.id)
                    val awayPlayers = repository.getPlayersByTeam(awayTeam.id)

                    val (homeScore, awayScore) = GameEngine.simulateMatchInstant(
                        homeTeam = homeTeam,
                        awayTeam = awayTeam,
                        homePlayers = homePlayers,
                        awayPlayers = awayPlayers
                    )

                    var updatedFixture = fixture.copy(
                        isPlayed = true,
                        homeScore = homeScore,
                        awayScore = awayScore
                    )
                    updatedFixture = CompetitionRules.ensureKnockoutDecision(updatedFixture)
                    repository.updateFixture(updatedFixture)

                    totalMatchesSimulated++
                    when {
                        fixture.competitionType in setOf("SERIE_A", "DIV_1") -> totalLeagueMatchesSimulated++
                        fixture.competitionType == "COPA" -> totalDomesticCupMatchesSimulated++
                        fixture.competitionType.startsWith("CONTINENTAL_") -> totalContinentalMatchesSimulated++
                        fixture.competitionType.startsWith("WORLD_CUP_GP_") -> totalWorldGroupMatchesSimulated++
                        fixture.competitionType == "WORLD_CUP" -> totalWorldKnockoutMatchesSimulated++
                    }
                }

                CupCompetitionSystem.processProgression(currentSeason, week, repository)
                SuperMundialSystem.processProgression(currentSeason, week, repository)

                val userPlayers = repository.getPlayersByTeam(cruzeiro.id)
                playerEvolutionUseCase.processPostMatchRecovery(gameSave, userPlayers, 5)
                val playedHomeMatches = repository.getFixturesForWeek(currentSeason, week)
                    .count { it.isPlayed && it.homeTeamId == cruzeiro.id }
                gameSave = financeUseCase.processWeeklyFinances(
                    save = gameSave,
                    homeMatchCount = playedHomeMatches,
                    userPlayers = userPlayers
                )

                cpuSquadManagementUseCase.renewCpuContractsBeforeWeeklyTick()
                processTransfersUseCase.processWeeklyContractsAndLoans()
                val cpuReport = cpuSquadManagementUseCase.ensureCpuSquadIntegrity()
                minimumCpuRosterObserved = minOf(minimumCpuRosterObserved, cpuReport.minimumRosterSize)
                maximumCpuRosterObserved = maxOf(maximumCpuRosterObserved, cpuReport.maximumRosterSize)
                teamsWithoutGoalkeeperObserved += cpuReport.teamsWithoutGoalkeeper
                invalidLoansObserved += cpuReport.invalidActiveLoans

                if (week % 4 == 0) {
                    playerEvolutionUseCase.executeMonthlyEvolution(gameSave, "S${currentSeason}_W${week}")
                }

                val allPlayers = repository.getAllPlayers()
                assertEquals(allPlayers.size, allPlayers.map { it.id }.toSet().size)
                val validTeamIds = repository.getAllTeams().map { it.id }.toSet()
                assertTrue(allPlayers.all { it.teamId == null || it.teamId in validTeamIds })
                assertTrue(allPlayers.all { it.contractDurationWeeks >= 0 })
                assertTrue(gameSave.bankBalance >= 0L)
                assertTrue(cpuReport.minimumRosterSize >= 16)
                assertTrue(cpuReport.maximumRosterSize <= 35)
                assertEquals(0, cpuReport.teamsWithoutGoalkeeper)
                assertEquals(0, cpuReport.invalidActiveLoans)
            }

            val seasonFixtures = repository.getFixturesForSeason(currentSeason)
            FixtureScheduleValidator.requireValid(seasonFixtures)
            RelationalIntegrityAssertions.assertRepositoryReferences(repository)
            // PRAGMAs em checkpoints estratégicos para não multiplicar 4.800 verificações SQLite.
            if (seasonOffset % 5 == 0 || seasonOffset == totalSeasons - 1) {
                RelationalIntegrityAssertions.assertDatabasePragmas(db)
            }
            val leagueFixtures = seasonFixtures.filter {
                it.competitionType in setOf("SERIE_A", "DIV_1")
            }
            val standings = leagueTeams.map { team ->
                val homeMatches = leagueFixtures.filter { it.homeTeamId == team.id && it.isPlayed }
                val awayMatches = leagueFixtures.filter { it.awayTeamId == team.id && it.isPlayed }
                val wins = homeMatches.count { (it.homeScore ?: 0) > (it.awayScore ?: 0) } +
                    awayMatches.count { (it.awayScore ?: 0) > (it.homeScore ?: 0) }
                val draws = homeMatches.count { (it.homeScore ?: 0) == (it.awayScore ?: 0) } +
                    awayMatches.count { (it.awayScore ?: 0) == (it.homeScore ?: 0) }
                Pair(team, wins * 3 + draws)
            }.sortedByDescending { it.second }

            val championTeam = standings.first().first
            val runnerUpTeam = standings.getOrNull(1)?.first ?: standings.first().first

            if (championTeam.id == cruzeiro.id) {
                gameSave = financeUseCase.awardCompetitionPrizeMoney(gameSave, "Campeonato Brasileiro (Série A)", 1)
            }

            repository.saveRecord(
                HistoricalRecord(
                    season = currentSeason,
                    competitionName = "Campeonato Brasileiro (Série A)",
                    championTeamName = championTeam.name,
                    runnerUpTeamName = runnerUpTeam.name,
                    topScorerName = "Craque ${15 + (seasonOffset % 10)}",
                    topScorerGoals = 20 + (seasonOffset % 12),
                    topScorerTeam = championTeam.name
                )
            )

            gameSave = seasonTransitionUseCase.advanceToNextSeason(gameSave)
        }

        val finalSave = repository.getGameSave()
        assertNotNull(finalSave)

        val records = repository.getAllHistoricalRecords()
        val serieARecords = records.filter { it.competitionName == "Campeonato Brasileiro (Série A)" }
        val expectedWorldSeasonList = (startSeason until startSeason + totalSeasons)
            .filter { it >= 2025 && (it - 2025) % 4 == 0 }
        val expectedWorldSeasons = expectedWorldSeasonList.size
        val expectedWorldGroupMatches = expectedWorldSeasons * 48
        val expectedWorldKnockoutMatches = expectedWorldSeasons * 15
        val expectedDomesticCupMatches = totalSeasons * 15
        val expectedContinentalMatches = 0

        assertEquals("Expected 2126 current season", 2126, finalSave!!.currentSeason)
        assertEquals("Expected 38000 Série A matches across 100 seasons", 38000, totalLeagueMatchesSimulated)
        assertEquals(expectedDomesticCupMatches, totalDomesticCupMatchesSimulated)
        assertEquals(expectedContinentalMatches, totalContinentalMatchesSimulated)
        assertEquals(expectedWorldGroupMatches, totalWorldGroupMatchesSimulated)
        assertEquals(expectedWorldKnockoutMatches, totalWorldKnockoutMatchesSimulated)
        assertEquals(
            totalLeagueMatchesSimulated +
                totalDomesticCupMatchesSimulated +
                totalContinentalMatchesSimulated +
                totalWorldGroupMatchesSimulated +
                totalWorldKnockoutMatchesSimulated,
            totalMatchesSimulated
        )
        assertEquals(100, serieARecords.size)
        assertTrue(finalSave.bankBalance >= 0L)
        assertEquals(expectedWorldSeasonList, observedWorldSeasons)
        assertEquals(25, observedWorldSeasons.size)
        assertEquals(
            SuperMundialEditionPolicy.editionsThrough(2125, repository.getAllTeams())
                .filter { it.season >= startSeason }
                .map { it.hostCountry },
            observedHostCountries
        )
        observedHostCountries.zipWithNext().forEach { (previous, next) ->
            assertNotEquals("Duas edições consecutivas não podem repetir sede", previous, next)
        }
        assertTrue("O rodízio deve usar todos os seis países elegíveis", observedHostCountries.toSet().size >= 6)
        assertTrue(minimumCpuRosterObserved >= 16)
        assertTrue(maximumCpuRosterObserved <= 35)
        assertEquals(0, teamsWithoutGoalkeeperObserved)
        assertEquals(0, invalidLoansObserved)

        val finalPlayers = repository.getAllPlayers()
        assertEquals(finalPlayers.size, finalPlayers.map { it.id }.toSet().size)
        val validTeamIds = repository.getAllTeams().map { it.id }.toSet()
        assertTrue(finalPlayers.all { it.teamId == null || it.teamId in validTeamIds })

        cpuTeamIds.forEach { teamId ->
            val roster = repository.getPlayersByTeam(teamId)
            assertTrue(roster.size in 16..35)
            assertTrue(roster.any { it.position == "GOL" })
        }
        RelationalIntegrityAssertions.assertDatabasePragmas(db)
    }
}
