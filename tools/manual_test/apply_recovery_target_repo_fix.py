from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: anchor count={count}, expected=1")
    return text.replace(old, new, 1)


match_path = Path("app/src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt")
match = match_path.read_text(encoding="utf-8")
match = replace_once(
    match,
    "val cpuSquadManagement = com.example.usecase.CpuSquadManagementUseCase(repo)",
    "val cpuSquadManagement = com.example.usecase.CpuSquadManagementUseCase(targetRepo)",
    "cpu squad recovery repository",
)
match = replace_once(
    match,
    "CupCompetitionSystem.processProgression(save.currentSeason, save.currentWeek, repo)",
    "CupCompetitionSystem.processProgression(save.currentSeason, save.currentWeek, targetRepo)",
    "cup recovery repository",
)
match = replace_once(
    match,
    "SuperMundialSystem.processProgression(save.currentSeason, save.currentWeek, repo)",
    "SuperMundialSystem.processProgression(save.currentSeason, save.currentWeek, targetRepo)",
    "super mundial recovery repository",
)
match_path.write_text(match, encoding="utf-8")


test_path = Path("app/src/test/java/com/example/ui/viewmodel/RecoveryTargetRepositoryIsolationRegressionTest.kt")
test_path.write_text(r'''package com.example.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.Fixture
import com.example.data.GamePreferencesRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.usecase.TacticsUseCase
import com.example.usecase.YouthAcademyUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RecoveryTargetRepositoryIsolationRegressionTest {
    private lateinit var application: Application
    private lateinit var saveRepository: GameSaveRepository

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        clearSlot("1")
        clearSlot("2")
        saveRepository = GameSaveRepository(application, SlotDatabaseFactory(application))
    }

    @After
    fun tearDown() {
        runBlocking { saveRepository.closeAllDatabases() }
        clearSlot("1")
        clearSlot("2")
    }

    @Test
    fun `explicit recovery repository cannot mutate another slot selected during close`() = runBlocking {
        val targetRepo = saveRepository.getRepositoryForSlot("1")
        targetRepo.saveTeams(
            listOf(
                Team(1L, "Alvo", "BH", "MG", "Brasil", 1, true, 75),
                Team(2L, "CPU Alvo", "SP", "SP", "Brasil", 1, false, 70)
            )
        )
        targetRepo.savePlayers(
            testRoster(teamId = 1L, firstId = 1_000L, count = 16, contractWeeks = 52) +
                testRoster(teamId = 2L, firstId = 2_000L, count = 16, contractWeeks = 52)
        )
        targetRepo.saveGameSave(
            GameSave(
                currentSeason = 2026,
                currentWeek = 1,
                playerTeamId = 1L,
                bankBalance = 5_000_000L,
                academyWeeklyInvestment = 0L
            )
        )
        targetRepo.saveFixtures(
            listOf(
                Fixture(
                    id = 100L,
                    season = 2026,
                    week = 1,
                    homeTeamId = 1L,
                    awayTeamId = 2L,
                    homeScore = 1,
                    awayScore = 0,
                    competitionType = "SERIE_A",
                    isPlayed = true
                )
            )
        )

        val otherRepo = saveRepository.getRepositoryForSlot("2")
        otherRepo.saveTeams(
            listOf(
                Team(200L, "Outro usuário", "RJ", "RJ", "Brasil", 1, true, 75),
                Team(201L, "CPU Sentinela", "POA", "RS", "Brasil", 1, false, 70)
            )
        )
        val sentinel = Player(
            id = 20_001L,
            teamId = 201L,
            name = "Sentinela",
            age = 25,
            position = "ATA",
            force = 75,
            potential = 80,
            contractDurationWeeks = 1,
            salary = 10_000L
        )
        otherRepo.savePlayers(
            listOf(sentinel) + testRoster(
                teamId = 201L,
                firstId = 20_100L,
                count = 15,
                contractWeeks = 52
            )
        )
        otherRepo.saveGameSave(
            GameSave(
                currentSeason = 2026,
                currentWeek = 9,
                playerTeamId = 200L,
                bankBalance = 99_000_000L
            )
        )

        val vm = GameViewModel(
            application = application,
            saveRepository = saveRepository,
            preferencesRepo = GamePreferencesRepository(application.dataStore, application, saveRepository),
            youthAcademyUseCase = YouthAcademyUseCase(),
            tacticsUseCase = TacticsUseCase()
        )
        vm.getOrCreateSession("2")
        vm._currentSaveId.value = "2"

        vm.processWeekEndEconomicAndEvolution(targetRepo)

        assertEquals(2, targetRepo.getGameSave()?.currentWeek)
        assertEquals(9, otherRepo.getGameSave()?.currentWeek)
        assertEquals(99_000_000L, otherRepo.getGameSave()?.bankBalance)
        assertEquals(
            "Fechamento do slot 1 não pode renovar contratos do slot 2",
            1,
            otherRepo.getPlayer(sentinel.id)?.contractDurationWeeks
        )
    }

    private fun testRoster(
        teamId: Long,
        firstId: Long,
        count: Int,
        contractWeeks: Int
    ): List<Player> = List(count) { index ->
        Player(
            id = firstId + index,
            teamId = teamId,
            name = "Jogador $teamId-$index",
            age = 25,
            position = if (index == 0) "GOL" else "ATA",
            force = 70,
            potential = 80,
            contractDurationWeeks = contractWeeks,
            salary = 10_000L
        )
    }

    private fun clearSlot(slotId: String) {
        val name = SlotDatabaseFactory.databaseNameForSlot(slotId)
        application.deleteDatabase(name)
        val file = application.getDatabasePath(name)
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            java.io.File(file.path + suffix).delete()
        }
    }
}
''', encoding="utf-8")
