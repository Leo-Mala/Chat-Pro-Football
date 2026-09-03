from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_exact(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"expected source block not found in {path}: {old[:180]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


game_vm = ROOT / "app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt"
replace_exact(
    game_vm,
    "@HiltViewModel\nclass GameViewModel @Inject constructor(",
    '''internal fun shouldStopSeasonSimulation(targetSeason: Int, currentSeason: Int): Boolean =
    currentSeason != targetSeason

@HiltViewModel
class GameViewModel @Inject constructor('''
)
replace_exact(
    game_vm,
    '''                        val initialSave = repo.getGameSave()
                        if (initialSave != null) {
                            cleanupDuplicateUnplayedFixtures(initialSave.currentSeason)
                        }
                        while (_isSimulatingSeason.value) {
                            val save = repo.getGameSave()
                            if (save == null) {
                                _isSimulatingSeason.value = false
                                break
                            }
''',
    '''                        val initialSave = repo.getGameSave()
                        if (initialSave == null) {
                            _isSimulatingSeason.value = false
                            return@withLock
                        }
                        val targetSeason = initialSave.currentSeason
                        cleanupDuplicateUnplayedFixtures(targetSeason)

                        while (_isSimulatingSeason.value) {
                            val save = repo.getGameSave()
                            if (save == null) {
                                _isSimulatingSeason.value = false
                                break
                            }
                            if (shouldStopSeasonSimulation(targetSeason, save.currentSeason)) {
                                break
                            }
'''
)
replace_exact(
    game_vm,
    '''                            val updatedSave = repo.getGameSave() ?: break
                            if (updatedSave.currentWeek == 1 && currentWeekNum >= GameCalendar.WEEKS_PER_SEASON) {
                                val nextLog = "🏆 Temporada ${save.currentSeason} finalizada com sucesso! Iniciando Temporada ${updatedSave.currentSeason}..."
                                _simulationLogs.value = (listOf(nextLog) + _simulationLogs.value).take(25)
                                delay(1500)
                            }
''',
    '''                            val updatedSave = repo.getGameSave() ?: break
                            if (shouldStopSeasonSimulation(targetSeason, updatedSave.currentSeason)) {
                                val nextLog = "🏆 Temporada $targetSeason finalizada com sucesso! Temporada ${updatedSave.currentSeason} preparada."
                                _simulationLogs.value = (listOf(nextLog) + _simulationLogs.value).take(25)
                                _simulationCompetitionName.value = "Temporada $targetSeason concluída"
                                _simulationMatchInfo.value = "Temporada ${updatedSave.currentSeason} preparada."
                                delay(600)
                                break
                            }
'''
)

dashboard = ROOT / "app/src/main/java/com/example/ui/components/dashboard/DashboardTabContent.kt"
replace_exact(
    dashboard,
    "@Composable\nfun DashboardTab(",
    '''internal fun simulationWeekProgressText(week: Int): String =
    "Semana $week de ${GameCalendar.WEEKS_PER_SEASON}"

@Composable
fun DashboardTab('''
)
replace_exact(dashboard, 'text = "Semana $simWeek de 45",', 'text = simulationWeekProgressText(simWeek),')
replace_exact(dashboard, 'text = "Simulando Semana $simWeek de 45...",', 'text = "Simulando ${simulationWeekProgressText(simWeek)}...",')

editor_vm = ROOT / "app/src/main/java/com/example/ui/viewmodel/GameViewModelEditor.kt"
replace_exact(
    editor_vm,
    '''private fun Flow<List<Player>>.asEditorPlayersLoadState(teamId: Long?): Flow<EditorPlayersLoadState> =
    map<List<Player>, EditorPlayersLoadState> { players ->
        EditorPlayersLoadState.Ready(teamId, players)
    }.onStart { emit(EditorPlayersLoadState.Loading(teamId)) }

fun GameViewModel.editorPlayersForEditorFlow(
    teamId: Long?,
    active: Boolean
): Flow<EditorPlayersLoadState> {
    if (!active) return flowOf(EditorPlayersLoadState.Inactive)
    if (teamId == null) return allPlayers.asEditorPlayersLoadState(null)
    return activeRepositoryFlow.flatMapLatest { repository ->
        if (repository == null) flowOf(EditorPlayersLoadState.Loading(teamId))
        else repository.getPlayersForTeamFlow(teamId).asEditorPlayersLoadState(teamId)
    }
}
''',
    '''private fun Flow<List<Player>>.asEditorPlayersLoadState(teamId: Long?): Flow<EditorPlayersLoadState> =
    map<List<Player>, EditorPlayersLoadState> { players ->
        EditorPlayersLoadState.Ready(teamId, players)
    }.onStart { emit(EditorPlayersLoadState.Loading(teamId)) }

internal fun editorPlayersLoadStateFlow(
    repositoryFlow: Flow<GameRepository?>,
    teamId: Long?
): Flow<EditorPlayersLoadState> =
    repositoryFlow.flatMapLatest { repository ->
        if (repository == null) {
            flowOf(EditorPlayersLoadState.Loading(teamId))
        } else {
            val playersFlow = if (teamId == null) repository.allPlayersFlow
            else repository.getPlayersForTeamFlow(teamId)
            playersFlow.asEditorPlayersLoadState(teamId)
        }
    }

fun GameViewModel.editorPlayersForEditorFlow(
    teamId: Long?,
    active: Boolean
): Flow<EditorPlayersLoadState> {
    if (!active) return flowOf(EditorPlayersLoadState.Inactive)
    return editorPlayersLoadStateFlow(activeRepositoryFlow, teamId)
}
'''
)

write_text(ROOT / "app/src/test/java/com/example/usecase/SeasonSimulationStopsAtTargetSeasonRegressionTest.kt", '''package com.example.usecase

import com.example.ui.viewmodel.shouldStopSeasonSimulation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SeasonSimulationStopsAtTargetSeasonRegressionTest {
    @Test fun `simulation stops immediately after rollover`() {
        assertFalse(shouldStopSeasonSimulation(2026, 2026))
        assertTrue(shouldStopSeasonSimulation(2026, 2027))
    }

    @Test fun `production loop captures target season and checks both sides of close`() {
        val source = File("src/main/java/com/example/ui/viewmodel/GameViewModel.kt").readText()
        val start = source.substringAfter("fun startSeasonSimulation()")
        assertTrue(start.contains("val targetSeason = initialSave.currentSeason"))
        assertTrue(start.contains("shouldStopSeasonSimulation(targetSeason, save.currentSeason)"))
        assertTrue(start.contains("shouldStopSeasonSimulation(targetSeason, updatedSave.currentSeason)"))
    }
}
''')

write_text(ROOT / "app/src/test/java/com/example/ui/screens/SeasonWeekBoundsRegressionTest.kt", '''package com.example.ui.screens

import com.example.data.GameCalendar
import com.example.ui.components.dashboard.simulationWeekProgressText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class SeasonWeekBoundsRegressionTest {
    @Test fun `simulation progress uses canonical season week count`() {
        assertEquals(48, GameCalendar.WEEKS_PER_SEASON)
        assertEquals("Semana 48 de 48", simulationWeekProgressText(GameCalendar.WEEKS_PER_SEASON))
    }

    @Test fun `dashboard contains no obsolete 45 week denominator`() {
        val source = File("src/main/java/com/example/ui/components/dashboard/DashboardTabContent.kt").readText()
        assertFalse(source.contains("simWeek de 45"))
    }
}
''')

write_text(ROOT / "app/src/test/java/com/example/ui/viewmodel/EditorExistingCareerRosterRegressionTest.kt", '''package com.example.ui.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.Player
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class EditorExistingCareerRosterRegressionTest {
    @Test fun `existing career never exposes synthetic ready zero before real roster`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = GameRepository(db)
            repository.savePlayers((1L..23L).map { id ->
                Player(id = id, teamId = 1L, name = "P$id", age = 24, position = "MEI", force = 99)
            })
            val states = editorPlayersLoadStateFlow(flowOf(repository), null).take(2).toList()
            assertTrue(states.first() is EditorPlayersLoadState.Loading)
            val ready = states.last() as EditorPlayersLoadState.Ready
            assertEquals(23, ready.players.size)
            assertEquals(setOf(99), ready.players.map { it.force }.toSet())
        } finally { db.close() }
    }
}
''')

print("core video regressions prepared")
