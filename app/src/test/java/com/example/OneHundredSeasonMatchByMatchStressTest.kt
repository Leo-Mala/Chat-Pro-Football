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
    private lateinit var simulateWeekUseCase: SimulateWeekUseCase
    private lateinit var generateCalendarUseCase: GenerateCalendarUseCase
    private lateinit var databaseIntegrityUseCase: DatabaseIntegrityUseCase
    private lateinit var processTransfersUseCase: ProcessTransfersUseCase
    private lateinit var financeUseCase: FinanceUseCase
    private lateinit var playerEvolutionUseCase: PlayerEvolutionUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = GameRepository(db)
        simulateWeekUseCase = SimulateWeekUseCase(repository)
        generateCalendarUseCase = GenerateCalendarUseCase(repository)
        databaseIntegrityUseCase = DatabaseIntegrityUseCase(repository)
        processTransfersUseCase = ProcessTransfersUseCase(repository)
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
                    salary = 150000L
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
        val startSeason = 2026
        val totalSeasons = 100

        for (seasonOffset in 0 until totalSeasons) {
            val currentSeason = startSeason + seasonOffset
            gameSave = gameSave.copy(currentSeason = currentSeason, currentWeek = 1)
            repository.saveGameSave(gameSave)

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
                            moral = 100
                        )
                    )
                }
                repository.savePlayers(updatedList)
            } else {
                repository.savePlayers(currentRoster.map { it.copy(force = 95, energy = 100) })
            }

            val seasonTransitionUseCase = SeasonTransitionUseCase(repository, generateCalendarUseCase, databaseIntegrityUseCase)
            if (repository.getFixturesForSeason(currentSeason).isEmpty()) {
                val fixtures = generateCalendarUseCase.generateRoundRobinFixtures(currentSeason, teams, "SERIE_A", 1)
                repository.saveFixtures(fixtures)
            }

            // The game season reaches week 40. Série A still contributes 38 rounds,
            // so the expected match count remains 38,000 while weeks 39–40 exercise
            // finances, evolution, recovery and integrity at the real season boundary.
            for (week in 1..40) {
                gameSave = gameSave.copy(currentWeek = week)
                repository.saveGameSave(gameSave)

                val weekFixtures = repository.getFixturesForWeek(currentSeason, week)
                val unplayed = weekFixtures.filter { !it.isPlayed }

                for (f in unplayed) {
                    val homeTeam = repository.getTeam(f.homeTeamId) ?: cruzeiro
                    val awayTeam = repository.getTeam(f.awayTeamId) ?: teams[1]
                    val homePlayers = repository.getPlayersByTeam(homeTeam.id)
                    val awayPlayers = repository.getPlayersByTeam(awayTeam.id)

                    val (homeScore, awayScore) = GameEngine.simulateMatchInstant(
                        homeTeam = homeTeam,
                        awayTeam = awayTeam,
                        homePlayers = homePlayers,
                        awayPlayers = awayPlayers
                    )

                    repository.updateFixture(
                        f.copy(
                            isPlayed = true,
                            homeScore = homeScore,
                            awayScore = awayScore
                        )
                    )
                    totalMatchesSimulated++
                }

                val userPlayers = repository.getPlayersByTeam(cruzeiro.id)
                playerEvolutionUseCase.processPostMatchRecovery(gameSave, userPlayers, 5)
                gameSave = financeUseCase.processWeeklyFinances(gameSave, week % 2 == 1)

                if (week % 4 == 0) {
                    playerEvolutionUseCase.executeMonthlyEvolution(gameSave, "S${currentSeason}_W${week}")
                }

                val allP = repository.getAllPlayers()
                val playerIds = allP.map { it.id }
                assertEquals("Não pode haver IDs duplicados de jogadores", playerIds.size, playerIds.toSet().size)

                val validTeamIds = repository.getAllTeams().map { it.id }.toSet() + 0L
                assertTrue("Todos os jogadores devem ter time válido ou ser Agente Livre (0L)", allP.all { it.teamId in validTeamIds })
                assertTrue("O saldo deve ser válido", gameSave.bankBalance >= 0L)
            }

            val seasonFixtures = repository.getFixturesForSeason(currentSeason)
            val standings = teams.map { team ->
                val homeMatches = seasonFixtures.filter { it.homeTeamId == team.id && it.isPlayed }
                val awayMatches = seasonFixtures.filter { it.awayTeamId == team.id && it.isPlayed }
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

        assertEquals("Expected 2126 current season", 2126, finalSave!!.currentSeason)
        assertEquals("Expected 38000 total matches simulated in Série A", 38000, totalMatchesSimulated)
        assertEquals("Expected one Série A historical record per simulated season", 100, serieARecords.size)
        assertTrue("Bank balance must remain non-negative and non-overflowing", finalSave.bankBalance >= 0L)

        val finalPlayers = repository.getAllPlayers()
        assertEquals("Player IDs must remain unique after 100 seasons", finalPlayers.size, finalPlayers.map { it.id }.toSet().size)
        val validTeamIds = repository.getAllTeams().map { it.id }.toSet() + 0L
        assertTrue("Every player must reference a valid team or Free Agent after 100 seasons", finalPlayers.all { it.teamId in validTeamIds })
    }
}
