package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
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

        val teams = mutableListOf(cruzeiro)
        teamNames.forEachIndexed { idx, name ->
            teams.add(
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
        val cpuTeamIds = teams.filter { it.id != cruzeiro.id }.map { it.id }.toSet()
        repository.saveTeams(teams)

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

        for (t in teams.filter { it.id != cruzeiro.id }) {
            val roster = DefaultData.generateRosterForTeam(t.id, t.rating, t.name, t.country)
            repository.savePlayers(roster)
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
            if (expectedWorld) {
                observedWorldSeasons.add(currentSeason)
                val host = requireNotNull(
                    SuperMundialEditionPolicy.hostCountryForSeason(currentSeason, repository.getAllTeams())
                )
                observedHostCountries.add(host)
            }

            val roster = repository.getPlayersByTeam(cruzeiro.id)
            val olderPlayers = roster.filter { it.age >= 30 }
            for (p in olderPlayers) {
                val sellOffer = p.calculateMarketValue()
                val sellResult = processTransfersUseCase.sellPlayer(gameSave, p, sellOffer)
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
                            position = if (i % 4 == 0) "ATA" else if (i % 4 == 1) "MEI" else if (i % 4 == 2) "DEF" else "GOL",
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

            val seasonTransitionUseCase = SeasonTransitionUseCase(repository, generateCalendarUseCase, databaseIntegrityUseCase)
            if (repository.getFixturesForSeason(currentSeason).isEmpty()) {
                val fixtures = generateCalendarUseCase.generateSeasonFixtures(
                    season = currentSeason,
                    teams = repository.getAllTeams(),
                    userTeamId = cruzeiro.id,
                    userCountry = cruzeiro.country
                )
                repository.saveFixtures(fixtures)
            }

            for (week in 1..GameCalendar.WEEKS_PER_SEASON) {
                gameSave = gameSave.copy(currentWeek = week)
                repository.saveGameSave(gameSave)

                val weekFixtures = repository.getFixturesForWeek(currentSeason, week)
                    .sortedWith(FixtureScheduleValidator.chronologicalComparator())
                val unplayed = weekFixtures.filter { !it.isPlayed }

                for (f in unplayed) {
                    val homeTeam = repository.getTeam(f.homeTeamId)
                        ?: GlobalFootballSystem.getVirtualTeam(f.homeTeamId)
                    val awayTeam = repository.getTeam(f.awayTeamId)
                        ?: GlobalFootballSystem.getVirtualTeam(f.awayTeamId)
                    val homePlayers = repository.getPlayersByTeam(homeTeam.id)
                    val awayPlayers = repository.getPlayersByTeam(awayTeam.id)

                    val (homeScore, awayScore) = GameEngine.simulateMatchInstant(
                        homeTeam = homeTeam,
                        awayTeam = awayTeam,
                        homePlayers = homePlayers,
                        awayPlayers = awayPlayers
                    )

                    var updatedFixture = f.copy(
                        isPlayed = true,
                        homeScore = homeScore,
                        awayScore = awayScore
                    )
                    updatedFixture = CompetitionRules.ensureKnockoutDecision(updatedFixture)
                    repository.updateFixture(updatedFixture)

                    totalMatchesSimulated++
                    when {
                        f.competitionType in setOf("SERIE_A", "DIV_1") -> totalLeagueMatchesSimulated++
                        f.competitionType == "COPA" -> totalDomesticCupMatchesSimulated++
                        f.competitionType.startsWith("CONTINENTAL_") -> totalContinentalMatchesSimulated++
                        f.competitionType.startsWith("WORLD_CUP_GP_") -> totalWorldGroupMatchesSimulated++
                        f.competitionType == "WORLD_CUP" -> totalWorldKnockoutMatchesSimulated++
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

                val allP = repository.getAllPlayers()
                val playerIds = allP.map { it.id }
                assertEquals("Não pode haver IDs duplicados de jogadores", playerIds.size, playerIds.toSet().size)

                val validTeamIds = repository.getAllTeams().map { it.id }.toSet() + 0L
                assertTrue("Todos os jogadores devem ter time válido ou ser Agente Livre (0L)", allP.all { it.teamId in validTeamIds })
                assertTrue("Contratos não podem ser negativos", allP.all { it.contractDurationWeeks >= 0 })
                assertTrue("O saldo deve ser válido", gameSave.bankBalance >= 0L)

                assertTrue("CPU mínima semanal deve ser >=16", cpuReport.minimumRosterSize >= 16)
                assertTrue("CPU máxima semanal deve ser <=35", cpuReport.maximumRosterSize <= 35)
                assertEquals("Nenhum clube CPU pode ficar sem goleiro", 0, cpuReport.teamsWithoutGoalkeeper)
                assertEquals("Nenhum empréstimo ativo pode ficar inconsistente", 0, cpuReport.invalidActiveLoans)
            }

            val seasonFixtures = repository.getFixturesForSeason(currentSeason)
            FixtureScheduleValidator.requireValid(seasonFixtures)
            val leagueFixtures = seasonFixtures.filter {
                it.competitionType in setOf("SERIE_A", "DIV_1")
            }
            val standings = teams.map { team ->
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
        assertEquals(
            "Expected complete domestic cup brackets in every season",
            expectedDomesticCupMatches,
            totalDomesticCupMatchesSimulated
        )
        assertEquals(
            "Synthetic 20-club universe must not create a reduced Libertadores",
            expectedContinentalMatches,
            totalContinentalMatchesSimulated
        )
        assertEquals(
            "Expected every eligible Super Mundial to contribute 48 group matches",
            expectedWorldGroupMatches,
            totalWorldGroupMatchesSimulated
        )
        assertEquals(
            "Expected every eligible Super Mundial to complete its 15-match knockout bracket",
            expectedWorldKnockoutMatches,
            totalWorldKnockoutMatchesSimulated
        )
        assertEquals(
            "Total matches must equal the sum of every generated competition family",
            totalLeagueMatchesSimulated +
                totalDomesticCupMatchesSimulated +
                totalContinentalMatchesSimulated +
                totalWorldGroupMatchesSimulated +
                totalWorldKnockoutMatchesSimulated,
            totalMatchesSimulated
        )
        assertEquals("Expected one Série A historical record per simulated season", 100, serieARecords.size)
        assertTrue("Bank balance must remain non-negative and non-overflowing", finalSave.bankBalance >= 0L)
        assertEquals(expectedWorldSeasonList, observedWorldSeasons)
        assertEquals(25, observedWorldSeasons.size)
        assertEquals("Universo sintético contém somente Brasil como sede elegível", setOf("Brasil"), observedHostCountries.toSet())
        assertTrue("Menor elenco CPU observado deve ser >=16", minimumCpuRosterObserved >= 16)
        assertTrue("Maior elenco CPU observado deve ser <=35", maximumCpuRosterObserved <= 35)
        assertEquals("Nenhum clube CPU pode ficar sem goleiro nas 4.800 semanas", 0, teamsWithoutGoalkeeperObserved)
        assertEquals("Nenhum empréstimo ativo pode ficar inconsistente", 0, invalidLoansObserved)

        val finalPlayers = repository.getAllPlayers()
        assertEquals("Player IDs must remain unique after 100 seasons", finalPlayers.size, finalPlayers.map { it.id }.toSet().size)
        val validTeamIds = repository.getAllTeams().map { it.id }.toSet() + 0L
        assertTrue("Every player must reference a valid team or Free Agent after 100 seasons", finalPlayers.all { it.teamId in validTeamIds })

        cpuTeamIds.forEach { teamId ->
            val roster = repository.getPlayersByTeam(teamId)
            assertTrue(roster.size in 16..35)
            assertTrue(roster.any { it.position == "GOL" })
        }
    }
}
