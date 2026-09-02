package com.example.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.GameEngine
import com.example.data.GamePreferencesRepository
import com.example.data.GameSave
import com.example.data.consumePristineCareerSeedTemplate
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.pristineCareerSeedTemplateOrNull
import com.example.data.repository.GameSaveRepository
import com.example.usecase.CpuWeekSimulationMetrics
import com.example.usecase.GenerateCalendarUseCase
import com.example.usecase.SimulateWeekUseCase
import com.example.usecase.TacticsUseCase
import com.example.usecase.YouthAcademyUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveFinalizationPerformanceBenchmarkTest {
    private lateinit var application: Application
    private lateinit var saveRepository: GameSaveRepository
    private val slotId = "5"

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        clearSlot()
        saveRepository = GameSaveRepository(application, SlotDatabaseFactory(application))
    }

    @After
    fun tearDown() {
        runBlocking { runCatching { saveRepository.closeAllDatabases() } }
        clearSlot()
    }

    @Test
    fun `canonical week eight finalization measures match cpu and weekly close separately`() = runBlocking {
        val repository = saveRepository.getRepositoryForSlot(slotId)
        val marker = requireNotNull(repository.pristineCareerSeedTemplateOrNull()) {
            "Benchmark precisa iniciar do career_seed_template.db canônico e intocado."
        }
        assertTrue(marker.playerCount >= 60_000)

        val teams = repository.getAllTeams().sortedBy { it.id }
        val leagueGroup = teams
            .groupBy { it.country to it.division }
            .values
            .filter { it.size >= 4 }
            .maxByOrNull { it.size }
            ?: error("Corpus canônico precisa de uma liga detalhada com pelo menos quatro clubes.")
        val userTeam = leagueGroup.first().copy(isPlayerControlled = true)
        repository.updateTeam(userTeam)
        repository.consumePristineCareerSeedTemplate()

        val generated = GenerateCalendarUseCase(repository).generateSeasonFixtures(
            season = 2026,
            teams = teams.map { if (it.id == userTeam.id) userTeam else it },
            userTeamId = userTeam.id,
            userCountry = userTeam.country
        )
        val generatedWeekEight = generated.filter { it.week == 8 }
        val userWeekFixtures = generatedWeekEight.filter {
            it.homeTeamId == userTeam.id || it.awayTeamId == userTeam.id
        }
        assertTrue("Semana 8 canônica precisa conter partida do clube controlado", userWeekFixtures.isNotEmpty())

        val targetUserFixture = userWeekFixtures.last()
        val stagedWeekFixtures = generatedWeekEight.map { fixture ->
            if (fixture.id != targetUserFixture.id &&
                (fixture.homeTeamId == userTeam.id || fixture.awayTeamId == userTeam.id)
            ) {
                fixture.copy(homeScore = 0, awayScore = 0, isPlayed = true)
            } else {
                fixture
            }
        }
        repository.saveFixtures(stagedWeekFixtures)
        repository.saveGameSave(
            GameSave(
                currentSeason = 2026,
                currentWeek = 8,
                playerTeamId = userTeam.id,
                bankBalance = 5_000_000L,
                sponsorName = "Benchmark Sponsor",
                sponsorWeekly = 100_000L,
                sponsorWeeksRemaining = 10,
                academyWeeklyInvestment = 0L
            )
        )

        val persistedWeekFixtures = repository.getFixturesForWeek(2026, 8)
        val persistedTarget = persistedWeekFixtures.firstOrNull {
            !it.isPlayed && (it.homeTeamId == userTeam.id || it.awayTeamId == userTeam.id)
        }
        assertNotNull("Deve restar exatamente a partida do usuário a finalizar", persistedTarget)
        assertEquals(
            1,
            persistedWeekFixtures.count {
                !it.isPlayed && (it.homeTeamId == userTeam.id || it.awayTeamId == userTeam.id)
            }
        )

        val viewModel = GameViewModel(
            application = application,
            saveRepository = saveRepository,
            preferencesRepo = GamePreferencesRepository(application.dataStore, application, saveRepository),
            youthAcademyUseCase = YouthAcademyUseCase(),
            tacticsUseCase = TacticsUseCase()
        )
        // Production resolves `repo` exclusively through the selected save id. The benchmark uses
        // the same session boundary instead of calling match-stat persistence on an unselected VM.
        viewModel._currentSaveId.value = slotId

        val totalStartedAtNs = System.nanoTime()
        val matchPersistStartedAtNs = System.nanoTime()
        val completedUserFixture = persistedTarget!!.copy(
            homeScore = 0,
            awayScore = 0,
            isPlayed = true
        )
        repository.withTransaction {
            val current = repository.getFixture(completedUserFixture.id)
            if (current?.isPlayed != true) {
                repository.updateFixture(completedUserFixture)
                viewModel.processMatchEventsAndStats(
                    completedUserFixture,
                    emptyList<GameEngine.MatchEventDetail>()
                )
            }
        }
        val matchPersistMillis = elapsedMillis(matchPersistStartedAtNs)

        var cpuMetrics: CpuWeekSimulationMetrics? = null
        SimulateWeekUseCase(repository) { metrics -> cpuMetrics = metrics }
            .simulateCpuMatchesForWeek(
                season = 2026,
                week = 8,
                excludedTeamId = userTeam.id
            )
        val measuredCpu = requireNotNull(cpuMetrics) { "Simulação CPU precisa publicar métricas." }

        val fixturesAfterCpu = repository.getFixturesForWeek(2026, 8)
        assertTrue("CPU e partida do usuário devem deixar a Semana 8 sem fixtures pendentes", fixturesAfterCpu.all { it.isPlayed })

        var weekCloseMetrics: WeekClosePerformanceMetrics? = null
        viewModel.processWeekEndEconomicAndEvolution(repository) { metrics -> weekCloseMetrics = metrics }
        val measuredWeekClose = requireNotNull(weekCloseMetrics) { "Fechamento semanal precisa publicar métricas." }
        val totalFinalizationMillis = elapsedMillis(totalStartedAtNs)

        assertEquals(9, repository.getGameSave()?.currentWeek)
        println(
            "PERF_LIVE_FINALIZATION " +
                "T_MATCH_PERSIST=$matchPersistMillis " +
                "T_CPU_FIXTURES=${measuredCpu.totalMillis} " +
                "T_TOTAL_WEEK_CLOSE=${measuredWeekClose.tTotalWeekCloseMillis} " +
                "T_MONTHLY_PREPARE=${measuredWeekClose.tMonthlyPrepareMillis} " +
                "T_MONTHLY_COMMIT=${measuredWeekClose.tMonthlyCommitMillis} " +
                "T_TOTAL_FINALIZATION=$totalFinalizationMillis " +
                "CPU_FIXTURES_COUNT=${measuredCpu.fixtureCount} " +
                "CPU_TEAM_COUNT=${measuredCpu.teamCount} " +
                "PLAYERS_READ_COUNT=${measuredCpu.playersReadCount} " +
                "CPU_ROSTER_QUERY_COUNT=${measuredCpu.rosterQueryCount} " +
                "MONTHLY_PLAYERS_COUNT=${measuredWeekClose.monthlyPlayersCount} " +
                "PLAYERS_UPDATED_COUNT=${measuredWeekClose.playersUpdatedCount}"
        )
    }

    private fun elapsedMillis(startedAtNs: Long): Long =
        (System.nanoTime() - startedAtNs) / 1_000_000L

    private fun clearSlot() {
        val name = SlotDatabaseFactory.databaseNameForSlot(slotId)
        application.deleteDatabase(name)
        val file = application.getDatabasePath(name)
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            java.io.File(file.path + suffix).delete()
        }
    }
}
