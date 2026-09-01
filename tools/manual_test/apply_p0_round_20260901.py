from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_exact(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"expected source block not found in {path}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# P0.1 — Leaving a finished live match must be a pure/idempotent UI transition.
game_vm = ROOT / "app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt"
replace_exact(
    game_vm,
    '''    fun exitLiveMatch() {
        liveMatchJob?.cancel()
        _matchState.value = MatchState.IDLE
        liveMatchFixture = null
        liveMatchHomeTeam = null
        liveMatchAwayTeam = null
        liveMatchHomePlayers = emptyList()
        liveMatchAwayPlayers = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            val save = repo.getGameSave() ?: return@launch
            val weekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek)
            val userFixture = weekFixtures.find { it.homeTeamId == save.playerTeamId || it.awayTeamId == save.playerTeamId }
            if (userFixture == null || userFixture.isPlayed) {
                simulateCpuMatchesForCurrentWeek()
                processWeekEndEconomicAndEvolution()
            }
        }
    }
''',
    '''    fun exitLiveMatch() {
        // All durable match/week work is completed before FINISHED is exposed.
        // Returning to Central is therefore immediate and safe to repeat.
        liveMatchJob?.cancel()
        liveMatchJob = null
        _matchState.value = MatchState.IDLE
        liveMatchFixture = null
        liveMatchHomeTeam = null
        liveMatchAwayTeam = null
        liveMatchHomePlayers = emptyList()
        liveMatchAwayPlayers = emptyList()
        currentMatchEvents = emptyList()
    }
'''
)

# P0.1 — Natural full time and Skip both close the current week before FINISHED is published.
match_vm = ROOT / "app/src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt"
replace_exact(
    match_vm,
    '''        // Only now can the UI expose "Voltar à Central".  exitLiveMatch() cancels
        // liveMatchJob, so publishing FINISHED before this durable commit could roll back
        // isPlayed=true and resurrect the exact same fixture on the Dashboard.
        _matchState.value = GameViewModel.MatchState.FINISHED
''',
    '''        // Complete the week lifecycle before exposing the post-match exit. This removes the
        // race where the button was clickable while CPU fixtures/evolution still owned the weekly
        // lifecycle and also guarantees that standings are already authoritative on return.
        val saveAfterMatch = repo.getGameSave()
        if (saveAfterMatch != null) {
            simulateCpuMatchesForCurrentWeek()
            val refreshedWeekFixtures = repo.getFixturesForWeek(
                saveAfterMatch.currentSeason,
                saveAfterMatch.currentWeek
            )
            if (!hasPendingUserFixtures(refreshedWeekFixtures, saveAfterMatch.playerTeamId)) {
                processWeekEndEconomicAndEvolution()
            }
        }
        _matchState.value = GameViewModel.MatchState.FINISHED
'''
)

old_skip = '''fun GameViewModel.skipLiveMatch(fixture: Fixture? = null) {
    liveMatchJob?.cancel()
    viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val targetFixture = fixture ?: liveMatchFixture ?: run {
            val weekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek)
            weekFixtures.find { !it.isPlayed && (it.homeTeamId == save.playerTeamId || it.awayTeamId == save.playerTeamId) }
        }

        if (targetFixture != null && !targetFixture.isPlayed) {
            val isPreparedLiveFixture = liveMatchFixture?.id == targetFixture.id &&
                _matchState.value != GameViewModel.MatchState.IDLE
            val finished = if (isPreparedLiveFixture) {
                finishPreparedLiveFixture(targetFixture)
            } else {
                simulateSingleUserFixtureSafely(targetFixture, save)
            }

            var updated = repo.getFixture(targetFixture.id) ?: finished
            val decided = CompetitionRules.ensureKnockoutDecision(updated)
            if (decided != updated) {
                repo.updateFixture(decided)
                updated = repo.getFixture(decided.id) ?: decided
            }
            _matchHomeScore.value = updated.homeScore ?: 0
            _matchAwayScore.value = updated.awayScore ?: 0
            _matchMinute.value = 90
            _matchState.value = GameViewModel.MatchState.FINISHED
        } else {
            _matchState.value = GameViewModel.MatchState.FINISHED
        }

        simulateCpuMatchesForCurrentWeek()

        val refreshedWeekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek)
        if (!hasPendingUserFixtures(refreshedWeekFixtures, save.playerTeamId)) {
            processWeekEndEconomicAndEvolution()
        }
    }
}
'''
new_skip = '''fun GameViewModel.skipLiveMatch(fixture: Fixture? = null) {
    liveMatchJob?.cancel()
    liveMatchJob = viewModelScope.launch(Dispatchers.IO) {
        val save = repo.getGameSave() ?: return@launch
        val targetFixture = fixture ?: liveMatchFixture ?: run {
            val weekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek)
            weekFixtures.find { !it.isPlayed && (it.homeTeamId == save.playerTeamId || it.awayTeamId == save.playerTeamId) }
        }

        if (targetFixture != null && !targetFixture.isPlayed) {
            val isPreparedLiveFixture = liveMatchFixture?.id == targetFixture.id &&
                _matchState.value != GameViewModel.MatchState.IDLE
            val finished = if (isPreparedLiveFixture) {
                finishPreparedLiveFixture(targetFixture)
            } else {
                simulateSingleUserFixtureSafely(targetFixture, save)
            }

            var updated = repo.getFixture(targetFixture.id) ?: finished
            val decided = CompetitionRules.ensureKnockoutDecision(updated)
            if (decided != updated) {
                repo.updateFixture(decided)
                updated = repo.getFixture(decided.id) ?: decided
            }
            _matchHomeScore.value = updated.homeScore ?: 0
            _matchAwayScore.value = updated.awayScore ?: 0
            _matchMinute.value = 90
        }

        // FINISHED is the durable boundary: CPU fixtures and the weekly close are complete first.
        simulateCpuMatchesForCurrentWeek()
        val refreshedWeekFixtures = repo.getFixturesForWeek(save.currentSeason, save.currentWeek)
        if (!hasPendingUserFixtures(refreshedWeekFixtures, save.playerTeamId)) {
            processWeekEndEconomicAndEvolution()
        }
        _matchState.value = GameViewModel.MatchState.FINISHED
    }
}
'''
replace_exact(match_vm, old_skip, new_skip)

# P0.2 — Keep deterministic ordering/RNG but reduce OFFSET scans from ~118 to ~15 at 60k players.
evolution = ROOT / "app/src/main/java/com/example/usecase/PlayerEvolutionUseCase.kt"
replace_exact(
    evolution,
    "private const val MONTHLY_EVOLUTION_BATCH_SIZE = 512",
    "// 4,096 preserves the exact global ORDER BY and sequential RNG stream while avoiding\\n// the pathological ~118 LIMIT/OFFSET scans observed at the week-four monthly boundary.\\nprivate const val MONTHLY_EVOLUTION_BATCH_SIZE = 4096"
)

# Regression tests are deliberately focused; no 20/100-season stress suite is introduced.
tests = {
    "app/src/test/java/com/example/ui/viewmodel/PostGameReturnToCentralRegressionTest.kt": '''package com.example.ui.viewmodel

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PostGameReturnToCentralRegressionTest {
    @Test
    fun `finished match closes week before FINISHED and exit is immediate`() {
        val match = readSource("src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt")
        val loopStart = match.indexOf("suspend fun GameViewModel.runMatchSimulationLoop")
        val loopEnd = match.indexOf("fun GameViewModel.substitutePlayer", loopStart)
        val loop = match.substring(loopStart, loopEnd)
        val cpu = loop.lastIndexOf("simulateCpuMatchesForCurrentWeek()")
        val close = loop.lastIndexOf("processWeekEndEconomicAndEvolution()")
        val finished = loop.lastIndexOf("_matchState.value = GameViewModel.MatchState.FINISHED")
        assertTrue(cpu >= 0)
        assertTrue(close > cpu)
        assertTrue(finished > close)

        val vm = readSource("src/main/java/com/example/ui/viewmodel/GameViewModel.kt")
        val exitStart = vm.indexOf("fun exitLiveMatch()")
        val exitEnd = vm.indexOf("// Watchlist StateFlow", exitStart)
        val exit = vm.substring(exitStart, exitEnd)
        assertTrue(exit.contains("_matchState.value = MatchState.IDLE"))
        assertTrue(!exit.contains("viewModelScope.launch"))
        assertTrue(!exit.contains("simulateCpuMatchesForCurrentWeek"))
        assertTrue(!exit.contains("processWeekEndEconomicAndEvolution"))
    }

    private fun readSource(relativeToApp: String): String {
        val candidates = listOf(File(relativeToApp), File("app/$relativeToApp"), File("../app/$relativeToApp"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relativeToApp; cwd=${File(".").absolutePath}")
    }
}
''',
    "app/src/test/java/com/example/usecase/SeasonSimulationCompletionRegressionTest.kt": '''package com.example.usecase

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonSimulationCompletionRegressionTest {
    @Test
    fun `season simulation weekly close reloads durable save before next iteration`() {
        val source = readSource("src/main/java/com/example/ui/viewmodel/GameViewModel.kt")
        val start = source.indexOf("fun startSeasonSimulation()")
        val end = source.indexOf("fun stopSeasonSimulation()", start)
        val body = source.substring(start, end)
        val cpu = body.indexOf("simulateCpuMatchesForCurrentWeek()")
        val close = body.indexOf("processWeekEndEconomicAndEvolution()", cpu)
        val reload = body.indexOf("val updatedSave = repo.getGameSave()", close)
        assertTrue(cpu >= 0)
        assertTrue(close > cpu)
        assertTrue(reload > close)
    }

    private fun readSource(relativeToApp: String): String {
        val candidates = listOf(File(relativeToApp), File("app/$relativeToApp"), File("../app/$relativeToApp"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relativeToApp")
    }
}
''',
    "app/src/test/java/com/example/usecase/FullSeasonSimulationDoesNotHangRegressionTest.kt": '''package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class FullSeasonSimulationDoesNotHangRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `week four monthly boundary completes inside a bounded execution`() = runTest {
        val team = Team(id = 1L, name = "Cruzeiro", city = "BH", state = "MG", division = 1, rating = 75)
        repository.saveTeams(listOf(team))
        val save = GameSave(currentSeason = 2026, currentWeek = 4, playerTeamId = team.id)
        repository.saveGameSave(save)
        val playerCount = 8_500
        repository.savePlayers(
            List(playerCount) { index ->
                Player(
                    id = index.toLong() + 1,
                    teamId = team.id,
                    name = "Jogador %05d".format(index),
                    age = 25,
                    position = "ATA",
                    force = 60,
                    potential = 80,
                    finishing = 60,
                    passing = 60,
                    pace = 60,
                    strength = 60,
                    vision = 60,
                    defense = 60
                )
            }
        )

        val plan = withTimeout(20_000) {
            PlayerEvolutionUseCase(repository).prepareMonthlyEvolution(save, "S2026_W4")
        }
        assertEquals(playerCount, plan.expectedPlayerCount)
        assertEquals(playerCount, plan.expectedInputs.size)
    }
}
'''
}
for relative, content in tests.items():
    path = ROOT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")

print("Applied P0 post-match lifecycle + week-four monthly evolution performance fixes.")
