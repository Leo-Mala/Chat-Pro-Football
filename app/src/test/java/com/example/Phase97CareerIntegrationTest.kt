package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CupCompetitionSystem
import com.example.data.DefaultData
import com.example.data.FixtureScheduleValidator
import com.example.data.GameCalendar
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.MatchSlot
import com.example.data.SuperMundialEditionPolicy
import com.example.data.SuperMundialSystem
import com.example.data.Team
import com.example.support.CareerInvariantAssertions
import com.example.usecase.CpuSquadManagementUseCase
import com.example.usecase.DatabaseIntegrityUseCase
import com.example.usecase.FinanceUseCase
import com.example.usecase.GenerateCalendarUseCase
import com.example.usecase.PlayerEvolutionUseCase
import com.example.usecase.ProcessTransfersUseCase
import com.example.usecase.SeasonTransitionUseCase
import com.example.usecase.SimulateWeekUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase97CareerIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var calendar: GenerateCalendarUseCase
    private lateinit var simulateWeek: SimulateWeekUseCase
    private lateinit var finance: FinanceUseCase
    private lateinit var transfers: ProcessTransfersUseCase
    private lateinit var cpuSquads: CpuSquadManagementUseCase
    private lateinit var evolution: PlayerEvolutionUseCase
    private lateinit var transition: SeasonTransitionUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        calendar = GenerateCalendarUseCase(repository)
        simulateWeek = SimulateWeekUseCase(repository)
        finance = FinanceUseCase(repository)
        transfers = ProcessTransfersUseCase(repository)
        cpuSquads = CpuSquadManagementUseCase(repository)
        evolution = PlayerEvolutionUseCase(repository)
        transition = SeasonTransitionUseCase(
            repository,
            calendar,
            DatabaseIntegrityUseCase(repository)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `five seasons preserve career invariants across every canonical week`() = runBlocking {
        val teams = qaTeams(8)
        val userTeamId = teams.first().id
        val storedTeamIds = teams.map { it.id }.toSet()
        val cpuTeamIds = storedTeamIds - userTeamId
        repository.saveTeams(teams)

        teams.forEach { team ->
            val roster = DefaultData.generateRosterForTeam(
                teamId = team.id,
                teamRating = team.rating,
                teamName = team.name,
                country = team.country
            ).map { player ->
                // O elenco do usuário fica sob controle do teste para que expiração contratual
                // não transforme a validação multitemporada em um teste de decisões do manager.
                if (team.id == userTeamId) {
                    player.copy(
                        age = 20 + (player.id % 8).toInt(),
                        contractDurationWeeks = 320
                    )
                } else {
                    player.copy(age = 20 + (player.id % 8).toInt())
                }
            }
            repository.savePlayers(roster)
        }

        var save = GameSave(
            coachName = "QA Fase 9.7/9.8",
            coachReputation = 75,
            currentWeek = 1,
            currentSeason = 2026,
            playerTeamId = userTeamId,
            bankBalance = 500_000_000L,
            stadiumCapacity = 50_000,
            ticketPrice = 40.0,
            sponsorWeekly = 1_000_000L,
            hasHiredCoach = true,
            hasHiredPhysio = true
        )
        repository.saveGameSave(save)

        repository.saveFixtures(
            calendar.generateSeasonFixtures(
                season = save.currentSeason,
                teams = teams,
                userTeamId = userTeamId,
                userCountry = "Brasil"
            )
        )

        val startSeason = save.currentSeason
        repeat(5) { seasonOffset ->
            val season = startSeason + seasonOffset
            assertEquals(season, save.currentSeason)
            assertEquals(1, save.currentWeek)
            assertEquals(storedTeamIds, repository.getAllTeams().map { it.id }.toSet())

            CareerInvariantAssertions.assertRepositorySeason(
                repository = repository,
                season = season,
                minimumRosterTeamIds = storedTeamIds
            )

            for (week in 1..GameCalendar.WEEKS_PER_SEASON) {
                save = (repository.getGameSave() ?: save).copy(
                    currentSeason = season,
                    currentWeek = week
                )
                repository.saveGameSave(save)

                simulateWeek.simulateCpuMatchesForWeek(season, week)

                val weekFixtures = repository.getFixturesForWeek(season, week)
                val homeMatchCount = weekFixtures.count {
                    it.isPlayed && it.homeTeamId == userTeamId
                }
                save = finance.processWeeklyFinances(
                    save = save,
                    homeMatchCount = homeMatchCount,
                    userPlayers = repository.getPlayersByTeam(userTeamId)
                )

                // Mesmo encadeamento do fluxo real de fim de semana da Fase 9.8.
                cpuSquads.renewCpuContractsBeforeWeeklyTick()
                transfers.processWeeklyContractsAndLoans()
                val cpuReport = cpuSquads.ensureCpuSquadIntegrity()
                assertTrue(cpuReport.minimumRosterSize >= CpuSquadManagementUseCase.MIN_SQUAD_SIZE)
                assertTrue(cpuReport.maximumRosterSize <= CpuSquadManagementUseCase.MAX_SQUAD_SIZE)
                assertEquals(0, cpuReport.teamsWithoutGoalkeeper)
                assertEquals(0, cpuReport.invalidActiveLoans)

                if (week % 4 == 0) {
                    evolution.executeMonthlyEvolution(save, "PHASE98_S${season}_W$week")
                }

                CupCompetitionSystem.processProgression(season, week, repository)
                SuperMundialSystem.processProgression(season, week, repository)

                CareerInvariantAssertions.assertRepositorySeason(
                    repository = repository,
                    season = season,
                    minimumRosterTeamIds = cpuTeamIds
                )

                val persisted = requireNotNull(repository.getGameSave())
                assertEquals("A temporada não pode mudar durante a semana", season, persisted.currentSeason)
                assertEquals("A semana persistida deve permanecer canônica", week, persisted.currentWeek)

                if (week == 40) {
                    try {
                        transition.advanceToNextSeason(persisted)
                        fail("Semana 40 não pode encerrar uma temporada de 48 semanas")
                    } catch (_: IllegalArgumentException) {
                        // esperado: a regra canônica continua 48 semanas.
                    }
                    val afterRejectedTransition = requireNotNull(repository.getGameSave())
                    assertEquals(season, afterRejectedTransition.currentSeason)
                    assertEquals(40, afterRejectedTransition.currentWeek)
                }
            }

            val completedSeasonFixtures = repository.getFixturesForSeason(season)
            FixtureScheduleValidator.requireValid(completedSeasonFixtures)
            assertTrue(
                "Ao final da temporada, todo fixture já iniciado deve ter placar bilateral",
                completedSeasonFixtures.all {
                    !it.isPlayed || (it.homeScore != null && it.awayScore != null)
                }
            )

            val worldFixtures = completedSeasonFixtures.filter {
                it.competitionType == "WORLD_CUP" ||
                    it.competitionType.startsWith("WORLD_CUP_GP_")
            }
            if (season == 2029) {
                assertTrue("A edição 2029 do Super Mundial deve existir", worldFixtures.isNotEmpty())
                assertTrue(worldFixtures.all { it.matchSlot == MatchSlot.MIDWEEK })
                assertTrue(
                    "Super Mundial deve ocupar grupos 42-44 e mata-mata 45-48",
                    (42..48).all { expectedWeek -> worldFixtures.any { it.week == expectedWeek } }
                )
                val edition = requireNotNull(SuperMundialEditionPolicy.editionForSeason(season, repository.getAllTeams()))
                assertEquals("Brasil", edition.hostCountry)
                assertTrue(
                    "O anfitrião da edição deve participar do Super Mundial",
                    worldFixtures.any { it.homeTeamId == edition.hostTeamId || it.awayTeamId == edition.hostTeamId }
                )
                assertEquals(
                    32,
                    worldFixtures.filter { it.competitionType.startsWith("WORLD_CUP_GP_") }
                        .flatMap { listOf(it.homeTeamId, it.awayTeamId) }
                        .toSet().size
                )
            } else {
                assertTrue("A temporada $season não pertence ao ciclo 2025 + 4n", worldFixtures.isEmpty())
            }

            save = transition.advanceToNextSeason(
                requireNotNull(repository.getGameSave()).copy(
                    currentSeason = season,
                    currentWeek = GameCalendar.WEEKS_PER_SEASON
                )
            )

            assertEquals(season + 1, save.currentSeason)
            assertEquals(1, save.currentWeek)
            assertEquals(storedTeamIds, repository.getAllTeams().map { it.id }.toSet())
            assertTrue("Saldo da carreira não pode ficar negativo", save.bankBalance >= 0L)
        }

        val finalSave = requireNotNull(repository.getGameSave())
        assertEquals(2031, finalSave.currentSeason)
        assertEquals(1, finalSave.currentWeek)
        assertEquals(storedTeamIds, repository.getAllTeams().map { it.id }.toSet())
        assertEquals(
            "IDs de jogadores devem continuar únicos após cinco temporadas reais",
            repository.getAllPlayers().size,
            repository.getAllPlayers().map { it.id }.toSet().size
        )
    }

    private fun qaTeams(count: Int): List<Team> = (1L..count.toLong()).map { id ->
        Team(
            id = id,
            name = "QA Brasil Clube $id",
            city = "Cidade $id",
            state = "BR",
            country = "Brasil",
            division = 1,
            rating = 72 + (id % 8).toInt(),
            isPlayerControlled = id == 1L,
            trainingCenterLevel = 3
        )
    }
}
