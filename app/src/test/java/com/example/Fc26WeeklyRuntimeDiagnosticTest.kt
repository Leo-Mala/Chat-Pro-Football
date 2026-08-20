package com.example

import android.app.Application
import android.os.Looper
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.data.GamePreferencesRepository
import com.example.data.StableRealPlayerIdentity
import com.example.data.dataStore
import com.example.data.isFc26UnassignedSourceClub
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.ui.viewmodel.GameViewModel
import com.example.usecase.CpuSquadManagementUseCase
import com.example.usecase.ProcessTransfersUseCase
import com.example.usecase.TacticsUseCase
import com.example.usecase.YouthAcademyUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Temporary diagnostic for PR #41. Remove before the final validated head. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Fc26WeeklyRuntimeDiagnosticTest {
    private lateinit var application: Application
    private lateinit var saveRepository: GameSaveRepository

    @Before
    fun setup() = runBlocking {
        application = ApplicationProvider.getApplicationContext()
        idleMainLooper()
        cleanPersistentState()
        saveRepository = GameSaveRepository(application, SlotDatabaseFactory(application))
    }

    @After
    fun tearDown() = runBlocking {
        runCatching { saveRepository.closeAllDatabases() }
        cleanPersistentState()
        idleMainLooper()
    }

    @Test
    fun `measure fresh FC26 career weekly hot paths`() = runBlocking {
        val preferences = GamePreferencesRepository(application.dataStore, application)
        val viewModel = GameViewModel(
            application = application,
            saveRepository = saveRepository,
            preferencesRepo = preferences,
            youthAcademyUseCase = YouthAcademyUseCase(),
            tacticsUseCase = TacticsUseCase()
        )

        viewModel.selectSaveSlot("1")
        val repository = saveRepository.getRepositoryForSlot("1")
        awaitCondition(90_000L) { repository.getAllTeams().isNotEmpty() }
        val teams = repository.getAllTeams()
        val selectedTeam = teams.firstOrNull {
            it.country.equals("Brasil", ignoreCase = true) && it.division == 1
        } ?: teams.first()

        viewModel.startNewGame(selectedTeam.id, "QA FC26 weekly diagnostic")
        awaitCondition(180_000L) {
            repository.getGameSave()?.coachName == "QA FC26 weekly diagnostic" &&
                repository.getFixturesForSeason(2026).isNotEmpty()
        }

        val players = repository.getAllPlayers()
        val allTeams = repository.getAllTeams()
        val fc26Players = players.count { StableRealPlayerIdentity.isRealPlayerId(it.id) }
        val mappedPlayers = players.count { it.teamId != null }
        val unassigned = players.count { it.isFc26UnassignedSourceClub() }
        val trueFreeAgents = players.count {
            it.teamId == null && !it.isOnLoan && !it.isFc26UnassignedSourceClub()
        }
        val duplicatePlayerIds = players.size - players.map { it.id }.distinct().size
        val duplicateTeamIds = allTeams.size - allTeams.map { it.id }.distinct().size

        assertEquals(18_405, fc26Players)
        assertEquals(0, duplicatePlayerIds)
        assertEquals(0, duplicateTeamIds)
        assertTrue(unassigned > 0)

        val cpuManagement = CpuSquadManagementUseCase(repository)
        val transfers = ProcessTransfersUseCase(repository)

        val renewMs = elapsedMillis {
            cpuManagement.renewCpuContractsBeforeWeeklyTick()
        }
        val contractsMs = elapsedMillis {
            transfers.processWeeklyContractsAndLoans()
        }
        val integrityMs = elapsedMillis {
            cpuManagement.processWeeklyAfterContracts()
        }

        val report = """{
  "totalPlayers": ${players.size},
  "fc26Players": $fc26Players,
  "playersWithTeamId": $mappedPlayers,
  "fc26Unassigned": $unassigned,
  "trueFreeAgents": $trueFreeAgents,
  "teams": ${allTeams.size},
  "duplicatePlayerIds": $duplicatePlayerIds,
  "duplicateTeamIds": $duplicateTeamIds,
  "renewCpuContractsMillis": $renewMs,
  "processWeeklyContractsMillis": $contractsMs,
  "cpuSquadIntegrityMillis": $integrityMs,
  "measuredHotPathMillis": ${renewMs + contractsMs + integrityMs}
}
"""
        val output = File(findRepositoryRoot(), "reports/fc26_weekly_runtime_diagnostic.json")
        output.parentFile.mkdirs()
        output.writeText(report, Charsets.UTF_8)
    }

    private suspend fun elapsedMillis(block: suspend () -> Unit): Long {
        val started = System.nanoTime()
        block()
        return (System.nanoTime() - started) / 1_000_000L
    }

    private suspend fun awaitCondition(timeoutMs: Long, condition: suspend () -> Boolean) {
        withTimeout(timeoutMs) {
            while (true) {
                idleMainLooper()
                if (condition()) break
                delay(50L)
            }
        }
    }

    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun findRepositoryRoot(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile ?: return@repeat
        }
        error("Could not locate repository root")
    }

    private suspend fun cleanPersistentState() {
        runCatching { application.dataStore.edit { it.clear() } }
        application
            .getSharedPreferences("brasfut_retro_saves", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        for (slotNumber in 1..5) {
            val db = SlotDatabaseFactory.databaseNameForSlot(slotNumber.toString())
            application.deleteDatabase(db)
            application.deleteDatabase("${db}_backup")
        }
    }
}
