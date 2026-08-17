package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CupCompetitionSystem
import com.example.data.DefaultData
import com.example.data.GameCalendar
import com.example.data.GameRepository
import com.example.data.GameSave
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase98CpuSquadSeasonIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var calendar: GenerateCalendarUseCase
    private lateinit var simulateWeek: SimulateWeekUseCase
    private lateinit var finance: FinanceUseCase
    private lateinit var transfers: ProcessTransfersUseCase
    private lateinit var cpuSquads: CpuSquadManagementUseCase
    private lateinit var evolution: PlayerEvolutionUseCase

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
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `cpu squads remain playable throughout every week of a complete season`() = runBlocking {
        val teams = (1L..6L).map { id ->
            Team(
                id = id,
                name = "Fase 9.8 Clube $id",
                city = "Cidade $id",
                state = "BR",
                country = "Brasil",
                division = 1,
                rating = 70 + id.toInt(),
                isPlayerControlled = id == 1L
            )
        }
        val userTeam = teams.first()
        val cpuTeamIds = teams.drop(1).map { it.id }.toSet()
        repository.saveTeams(teams)

        teams.forEach { team ->
            val roster = DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
                .mapIndexed { index, player ->
                    if (team.id == userTeam.id) {
                        player.copy(contractDurationWeeks = 200)
                    } else {
                        // Força churn contratual já no primeiro mês: alguns serão renovados,
                        // outros virarão agentes livres e precisarão ser repostos pela CPU.
                        player.copy(
                            age = if (index % 3 == 0) 35 else 25 + (index % 5),
                            force = if (index % 4 == 0) 50 else player.force,
                            potential = if (index % 4 == 0) 60 else player.potential,
                            contractDurationWeeks = 1 + (index % 4)
                        )
                    }
                }
            repository.savePlayers(roster)
        }

        var save = GameSave(
            coachName = "QA CPU 48 Semanas",
            currentSeason = 2026,
            currentWeek = 1,
            playerTeamId = userTeam.id,
            bankBalance = 100_000_000L,
            stadiumCapacity = 40_000,
            ticketPrice = 40.0,
            sponsorWeekly = 500_000L
        )
        repository.saveGameSave(save)
        repository.saveFixtures(
            calendar.generateSeasonFixtures(
                season = 2026,
                teams = teams,
                userTeamId = userTeam.id,
                userCountry = "Brasil"
            )
        )

        var minimumObserved = Int.MAX_VALUE
        var maximumObserved = 0
        var freeAgentReuseEvents = 0
        var emergencyGenerationEvents = 0

        for (week in 1..GameCalendar.WEEKS_PER_SEASON) {
            save = requireNotNull(repository.getGameSave()).copy(currentWeek = week)
            repository.saveGameSave(save)

            simulateWeek.simulateCpuMatchesForWeek(2026, week)
            val homeMatches = repository.getFixturesForWeek(2026, week)
                .count { it.isPlayed && it.homeTeamId == userTeam.id }
            save = finance.processWeeklyFinances(
                save = save,
                homeMatchCount = homeMatches,
                userPlayers = repository.getPlayersByTeam(userTeam.id)
            )

            cpuSquads.renewCpuContractsBeforeWeeklyTick()
            transfers.processWeeklyContractsAndLoans()
            val report = cpuSquads.ensureCpuSquadIntegrity()
            minimumObserved = minOf(minimumObserved, report.minimumRosterSize)
            maximumObserved = maxOf(maximumObserved, report.maximumRosterSize)
            freeAgentReuseEvents += report.freeAgentsSigned
            emergencyGenerationEvents += report.emergencyPlayersGenerated

            if (week % 4 == 0) {
                evolution.executeMonthlyEvolution(save, "PHASE98_CPU_W$week")
            }
            CupCompetitionSystem.processProgression(2026, week, repository)
            SuperMundialSystem.processProgression(2026, week, repository)

            assertTrue(report.minimumRosterSize >= 16)
            assertTrue(report.maximumRosterSize <= 35)
            assertEquals(0, report.teamsWithoutGoalkeeper)
            assertEquals(0, report.invalidActiveLoans)
            CareerInvariantAssertions.assertRepositorySeason(
                repository = repository,
                season = 2026,
                minimumRosterTeamIds = cpuTeamIds
            )
        }

        assertTrue(minimumObserved >= 16)
        assertTrue(maximumObserved <= 35)
        assertTrue(
            "O cenário agressivo deve exercitar reaproveitamento ou geração de reposição",
            freeAgentReuseEvents + emergencyGenerationEvents > 0
        )

        val transition = SeasonTransitionUseCase(
            repository,
            calendar,
            DatabaseIntegrityUseCase(repository)
        )
        val advanced = transition.advanceToNextSeason(
            requireNotNull(repository.getGameSave()).copy(currentWeek = GameCalendar.WEEKS_PER_SEASON)
        )
        assertEquals(2027, advanced.currentSeason)
        assertEquals(1, advanced.currentWeek)

        cpuTeamIds.forEach { teamId ->
            val roster = repository.getPlayersByTeam(teamId)
            assertTrue(roster.size in 16..35)
            assertTrue(roster.any { it.position == "GOL" })
        }
    }
}
