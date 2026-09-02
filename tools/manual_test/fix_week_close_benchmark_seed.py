from pathlib import Path

path = Path("app/src/test/java/com/example/usecase/WeekEightClosePerformanceBenchmarkTest.kt")
path.write_text(r'''package com.example.usecase

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.Fixture
import com.example.data.GamePreferencesRepository
import com.example.data.GameSave
import com.example.data.consumePristineCareerSeedTemplate
import com.example.data.getMonthlyEvolutionPlayerCount
import com.example.data.local.SlotDatabaseFactory
import com.example.data.pristineCareerSeedTemplateOrNull
import com.example.data.repository.GameSaveRepository
import com.example.ui.viewmodel.GameViewModel
import com.example.ui.viewmodel.WeekClosePerformanceMetrics
import com.example.ui.viewmodel.processWeekEndEconomicAndEvolution
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
class WeekEightClosePerformanceBenchmarkTest {
    private lateinit var application: Application
    private lateinit var saveRepository: GameSaveRepository
    private val slotId = "3"

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
    fun `week eight close measures complete canonical world player universe`() = runBlocking {
        val repository = saveRepository.getRepositoryForSlot(slotId)
        val marker = requireNotNull(repository.pristineCareerSeedTemplateOrNull()) {
            "Benchmark precisa iniciar do career_seed_template.db canônico e intocado."
        }
        assertTrue(
            "O corpus canônico deve permanecer em escala mundial >= 60k jogadores",
            marker.playerCount >= 60_000
        )
        assertEquals(marker.playerCount, repository.getMonthlyEvolutionPlayerCount())

        val teams = repository.getAllTeams().sortedBy { it.id }
        require(teams.size >= 2) { "Benchmark precisa de pelo menos dois clubes canônicos." }
        val userTeam = teams[0].copy(isPlayerControlled = true)
        val cpuTeam = teams[1]
        repository.updateTeam(userTeam)
        repository.consumePristineCareerSeedTemplate()

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
        repository.saveFixtures(
            listOf(
                Fixture(
                    id = 808L,
                    season = 2026,
                    week = 8,
                    homeTeamId = userTeam.id,
                    awayTeamId = cpuTeam.id,
                    homeScore = 1,
                    awayScore = 0,
                    competitionType = "SERIE_A",
                    isPlayed = true
                )
            )
        )

        val viewModel = GameViewModel(
            application = application,
            saveRepository = saveRepository,
            preferencesRepo = GamePreferencesRepository(application.dataStore, application, saveRepository),
            youthAcademyUseCase = YouthAcademyUseCase(),
            tacticsUseCase = TacticsUseCase()
        )

        var measured: WeekClosePerformanceMetrics? = null
        viewModel.processWeekEndEconomicAndEvolution(repository) { metrics -> measured = metrics }

        val metrics = measured
        assertNotNull("Fechamento confirmado precisa publicar métricas", metrics)
        metrics!!
        val persistedPlayersAfter = repository.getMonthlyEvolutionPlayerCount()
        println(
            metrics.asDiagnosticLine(prefix = "PERF_BASELINE_WEEK8") +
                " PERSISTED_PLAYERS_AFTER=$persistedPlayersAfter"
        )

        assertEquals(marker.playerCount, metrics.monthlyPlayersCount)
        assertEquals(marker.playerCount, persistedPlayersAfter)
        assertEquals(9, repository.getGameSave()?.currentWeek)
    }

    private fun clearSlot() {
        val name = SlotDatabaseFactory.databaseNameForSlot(slotId)
        application.deleteDatabase(name)
        val file = application.getDatabasePath(name)
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            java.io.File(file.path + suffix).delete()
        }
    }
}
''', encoding="utf-8")
