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
class TwentySeasonStressTest {

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
    fun execute20SeasonCareerStressTest() = runBlocking {
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
        val rival1 = Team(id = 101L, name = "Atlético-MG", city = "Belo Horizonte", state = "MG", division = 1, country = "Brasil", rating = 82)
        val rival2 = Team(id = 102L, name = "Flamengo", city = "Rio de Janeiro", state = "RJ", division = 1, country = "Brasil", rating = 85)
        val rival3 = Team(id = 103L, name = "Palmeiras", city = "São Paulo", state = "SP", division = 1, country = "Brasil", rating = 86)
        val rival4 = Team(id = 104L, name = "Grêmio", city = "Porto Alegre", state = "RS", division = 1, country = "Brasil", rating = 80)
        val rival5 = Team(id = 105L, name = "Internacional", city = "Porto Alegre", state = "RS", division = 1, country = "Brasil", rating = 81)

        val teams = listOf(cruzeiro, rival1, rival2, rival3, rival4, rival5)
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
                    name = "Cruzeirense $i",
                    age = 20 + (i % 12),
                    position = pos,
                    force = 99,
                    salary = 100000L
                )
            )
        }
        repository.savePlayers(cruzeiroPlayers)

        for (t in teams.filter { it.id != cruzeiro.id }) {
            val roster = DefaultData.generateRosterForTeam(t.id, t.rating, t.name, t.country)
            repository.savePlayers(roster)
        }

        var gameSave = GameSave(
            coachName = "Técnico Teste 20 Anos",
            coachReputation = 99,
            currentWeek = 1,
            currentSeason = 2026,
            playerTeamId = cruzeiro.id,
            bankBalance = 200000000L,
            stadiumCapacity = 62000,
            ticketPrice = 50.0,
            sponsorWeekly = 2000000L,
            hasHiredCoach = true,
            hasHiredPhysio = true
        )
        repository.saveGameSave(gameSave)

        val titlesBySeason = mutableMapOf<Int, String>()
        val startSeason = 2026
        val totalSeasons = 20

        for (seasonOffset in 0 until totalSeasons) {
            val currentSeason = startSeason + seasonOffset
            gameSave = gameSave.copy(currentSeason = currentSeason, currentWeek = 1)
            repository.saveGameSave(gameSave)

            var currentRoster = repository.getPlayersByTeam(cruzeiro.id)
            val olderPlayers = currentRoster.filter { it.age >= 30 }
            for (p in olderPlayers) {
                val sellOffer = p.calculateMarketValue()
                val sellResult = processTransfersUseCase.sellPlayer(gameSave, p, sellOffer)
                if (sellResult is ProcessTransfersUseCase.TransferResult.Success) {
                    gameSave = sellResult.updatedSave
                }
            }

            currentRoster = repository.getPlayersByTeam(cruzeiro.id)
            if (currentRoster.size < 20) {
                val needed = 20 - currentRoster.size
                val freeAgents = repository.getAllPlayers().filter { it.teamId != cruzeiro.id }
                for (fa in freeAgents.take(needed)) {
                    val price = fa.calculateMarketValue()
                    val buyResult = processTransfersUseCase.buyPlayer(gameSave, fa, price)
                    if (buyResult is ProcessTransfersUseCase.TransferResult.Success) {
                        gameSave = buyResult.updatedSave
                    }
                }
            }

            repository.updateTeam(cruzeiro.copy(trainingCenterLevel = 5))

            if (repository.getFixturesForSeason(currentSeason).isEmpty()) {
                val seasonFixtures = generateCalendarUseCase.generateRoundRobinFixtures(currentSeason, teams, "SERIE_A", 1)
                repository.saveFixtures(seasonFixtures)
            }

            val seasonTransitionUseCase = SeasonTransitionUseCase(repository, generateCalendarUseCase, databaseIntegrityUseCase)

            // The game season runs through week 40. Weeks 39–40 must be covered too,
            // even when there are no Série A round-robin fixtures in those slots.
            for (week in 1..40) {
                gameSave = gameSave.copy(currentWeek = week)
                repository.saveGameSave(gameSave)

                gameSave = financeUseCase.processWeeklyFinances(gameSave, week % 2 == 1)

                if (week % 4 == 0) {
                    playerEvolutionUseCase.executeMonthlyEvolution(gameSave, "S${currentSeason}_W${week}")
                }

                simulateWeekUseCase.simulateCpuMatchesForWeek(currentSeason, week)

                val allP = repository.getAllPlayers()
                val playerIds = allP.map { it.id }
                assertEquals("Não pode haver IDs duplicados de jogadores", playerIds.size, playerIds.toSet().size)

                val validTeamIds = repository.getAllTeams().map { it.id }.toSet() + 0L
                assertTrue("Todos os jogadores devem ter time válido ou ser Agente Livre (0L)", allP.all { it.teamId in validTeamIds })
                assertTrue("O saldo deve ser não-negativo e válido", gameSave.bankBalance >= 0L)
            }

            titlesBySeason[currentSeason] = "Campeão Brasileiro"
            gameSave = seasonTransitionUseCase.advanceToNextSeason(gameSave)
        }

        val finalSave = repository.getGameSave()
        assertNotNull(finalSave)
        assertEquals("Season should reach 2046 after 20 season transitions", 2046, finalSave!!.currentSeason)

        val finalRoster = repository.getPlayersByTeam(cruzeiro.id)
        assertTrue("Roster must have at least 16 players after 20 seasons", finalRoster.size >= 16)
        assertTrue("Final bank balance must remain non-negative", finalSave.bankBalance >= 0L)
        assertEquals("Should complete 20 simulated seasons", 20, titlesBySeason.size)

        val finalPlayers = repository.getAllPlayers()
        assertEquals("Player IDs must remain unique after 20 seasons", finalPlayers.size, finalPlayers.map { it.id }.toSet().size)
        val validTeamIds = repository.getAllTeams().map { it.id }.toSet() + 0L
        assertTrue("Every player must reference a valid team or Free Agent after 20 seasons", finalPlayers.all { it.teamId in validTeamIds })
    }
}
