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
        simulateWeekUseCase = SimulateWeekUseCase(repository)
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

        val leagueTeams = listOf(cruzeiro, rival1, rival2, rival3, rival4, rival5)
        val hostSupportTeams = listOf(
            Team(201L, "Argentina Host", "Buenos Aires", "AR", "Argentina", 1, rating = 84),
            Team(202L, "Espanha Host", "Madrid", "ES", "Espanha", 1, rating = 88),
            Team(203L, "França Host", "Paris", "FR", "França", 1, rating = 87),
            Team(204L, "Inglaterra Host", "London", "EN", "Inglaterra", 1, rating = 90)
        )
        val allTeams = leagueTeams + hostSupportTeams
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
                    name = "Cruzeirense $i",
                    age = 20 + (i % 12),
                    position = pos,
                    force = 99,
                    salary = 100000L,
                    contractDurationWeeks = 2_000
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
        var minimumCpuRosterObserved = Int.MAX_VALUE
        var maximumCpuRosterObserved = 0
        var teamsWithoutGoalkeeperObserved = 0
        var invalidLoansObserved = 0
        var expectedWorldEditions = 0
        val observedWorldHosts = mutableListOf<String>()

        for (seasonOffset in 0 until totalSeasons) {
            val currentSeason = startSeason + seasonOffset
            gameSave = gameSave.copy(currentSeason = currentSeason, currentWeek = 1)
            repository.saveGameSave(gameSave)

            val expectedWorld = currentSeason >= 2025 && (currentSeason - 2025) % 4 == 0
            assertEquals(expectedWorld, SuperMundialSystem.isSuperMundialSeason(currentSeason))
            if (expectedWorld) {
                expectedWorldEditions++
                observedWorldHosts += requireNotNull(
                    SuperMundialEditionPolicy.hostCountryForSeason(currentSeason, repository.getAllTeams())
                )
            }

            var currentRoster = repository.getPlayersByTeam(cruzeiro.id)
            val olderPlayers = currentRoster.filter { it.age >= 30 }
            for (player in olderPlayers) {
                val sellResult = processTransfersUseCase.sellPlayer(gameSave, player, player.calculateMarketValue())
                if (sellResult is ProcessTransfersUseCase.TransferResult.Success) {
                    gameSave = sellResult.updatedSave
                }
            }

            currentRoster = repository.getPlayersByTeam(cruzeiro.id)
            if (currentRoster.size < 20) {
                val needed = 20 - currentRoster.size
                val candidates = repository.getAllPlayers().filter { it.teamId != cruzeiro.id }
                for (candidate in candidates.take(needed)) {
                    val buyResult = processTransfersUseCase.buyPlayer(gameSave, candidate, candidate.calculateMarketValue())
                    if (buyResult is ProcessTransfersUseCase.TransferResult.Success) {
                        gameSave = buyResult.updatedSave
                    }
                }
            }
            repository.updatePlayers(
                repository.getPlayersByTeam(cruzeiro.id).map {
                    it.copy(contractDurationWeeks = 2_000)
                }
            )

            // Transferências do usuário podem retirar um atleta da CPU. Corrigimos o elenco antes
            // do primeiro jogo da temporada, não apenas depois da primeira rodada.
            val openingCpuReport = cpuSquadManagementUseCase.ensureCpuSquadIntegrity()
            assertTrue(openingCpuReport.minimumRosterSize >= 16)
            assertTrue(openingCpuReport.maximumRosterSize <= 35)
            assertEquals(0, openingCpuReport.teamsWithoutGoalkeeper)

            repository.updateTeam(cruzeiro.copy(trainingCenterLevel = 5))

            if (repository.getFixturesForSeason(currentSeason).isEmpty()) {
                val seasonFixtures = generateCalendarUseCase.generateSeasonFixtures(
                    season = currentSeason,
                    teams = allTeams,
                    userTeamId = cruzeiro.id,
                    userCountry = cruzeiro.country
                )
                repository.saveFixtures(seasonFixtures)
            }

            val seasonTransitionUseCase = SeasonTransitionUseCase(repository, generateCalendarUseCase, databaseIntegrityUseCase)

            for (week in 1..GameCalendar.WEEKS_PER_SEASON) {
                gameSave = gameSave.copy(currentWeek = week)
                repository.saveGameSave(gameSave)

                simulateWeekUseCase.simulateCpuMatchesForWeek(currentSeason, week)
                CupCompetitionSystem.processProgression(currentSeason, week, repository)
                SuperMundialSystem.processProgression(currentSeason, week, repository)

                val playedHomeMatches = repository.getFixturesForWeek(currentSeason, week)
                    .count { it.isPlayed && it.homeTeamId == cruzeiro.id }
                gameSave = financeUseCase.processWeeklyFinances(
                    save = gameSave,
                    homeMatchCount = playedHomeMatches,
                    userPlayers = repository.getPlayersByTeam(cruzeiro.id)
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
                assertEquals(
                    "Não pode haver IDs duplicados de jogadores",
                    allPlayers.size,
                    allPlayers.map { it.id }.toSet().size
                )
                val validTeamIds = repository.getAllTeams().map { it.id }.toSet() + 0L
                assertTrue(allPlayers.all { it.teamId in validTeamIds })
                assertTrue(allPlayers.all { it.contractDurationWeeks >= 0 })
                assertTrue(gameSave.bankBalance >= 0L)

                cpuTeamIds.forEach { teamId ->
                    val roster = repository.getPlayersByTeam(teamId)
                    assertTrue("CPU $teamId deve manter pelo menos 16 atletas na semana $week", roster.size >= 16)
                    assertTrue("CPU $teamId não pode ultrapassar 35 atletas", roster.size <= 35)
                    assertTrue("CPU $teamId deve manter goleiro", roster.any { it.position == "GOL" })
                }
            }

            FixtureScheduleValidator.requireValid(repository.getFixturesForSeason(currentSeason))
            titlesBySeason[currentSeason] = "Campeão Brasileiro"
            gameSave = seasonTransitionUseCase.advanceToNextSeason(gameSave)
        }

        val finalSave = repository.getGameSave()
        assertNotNull(finalSave)
        assertEquals("Season should reach 2046 after 20 season transitions", 2046, finalSave!!.currentSeason)
        assertTrue(repository.getPlayersByTeam(cruzeiro.id).size >= 16)
        assertTrue(finalSave.bankBalance >= 0L)
        assertEquals(20, titlesBySeason.size)
        assertEquals("2026-2045 deve conter cinco edições oficiais", 5, expectedWorldEditions)
        assertEquals("As cinco edições devem usar as cinco sedes elegíveis antes de repetir", 5, observedWorldHosts.toSet().size)
        observedWorldHosts.zipWithNext().forEach { (previous, next) ->
            assertNotEquals("Edições consecutivas não podem repetir sede", previous, next)
        }
        assertTrue(minimumCpuRosterObserved >= 16)
        assertTrue(maximumCpuRosterObserved <= 35)
        assertEquals(0, teamsWithoutGoalkeeperObserved)
        assertEquals(0, invalidLoansObserved)

        val finalPlayers = repository.getAllPlayers()
        assertEquals(finalPlayers.size, finalPlayers.map { it.id }.toSet().size)
        val validTeamIds = repository.getAllTeams().map { it.id }.toSet() + 0L
        assertTrue(finalPlayers.all { it.teamId in validTeamIds })
    }
}
