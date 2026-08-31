from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def edit(path: str, transform):
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    updated = transform(text)
    if updated == text:
        raise RuntimeError(f"No change produced for {path}")
    target.write_text(updated, encoding="utf-8")


def replace_exact(path: str, old: str, new: str, expected: int = 1):
    def transform(text: str) -> str:
        count = text.count(old)
        if count != expected:
            raise RuntimeError(f"{path}: expected {expected} occurrence(s), found {count}: {old[:100]!r}")
        return text.replace(old, new)
    edit(path, transform)


# ---------------------------------------------------------------------------
# 1) One crest identity rule everywhere: every TeamBadge call must receive ID.
# ---------------------------------------------------------------------------
replace_exact(
    "app/src/main/java/com/example/ui/screens/EditorScreen.kt",
    """                                    TeamBadge(\n                                        teamName = currentSelectedTeam.name,\n                                        logoUrl = currentSelectedTeam.logoUrl,\n                                        size = 46.dp,""",
    """                                    TeamBadge(\n                                        teamName = currentSelectedTeam.name,\n                                        logoUrl = currentSelectedTeam.logoUrl,\n                                        teamId = currentSelectedTeam.id,\n                                        size = 46.dp,""",
)
replace_exact(
    "app/src/main/java/com/example/ui/components/editor/EditorCards.kt",
    """            TeamBadge(\n                teamName = team.name,\n                logoUrl = team.logoUrl,\n                size = 46.dp,""",
    """            TeamBadge(\n                teamName = team.name,\n                logoUrl = team.logoUrl,\n                teamId = team.id,\n                size = 46.dp,""",
)
replace_exact(
    "app/src/main/java/com/example/ui/screens/MatchSimulationScreen.kt",
    """                        TeamBadge(\n                            teamName = hTeam?.name ?: \"Home\",\n                            logoUrl = hTeam?.logoUrl,\n                            size = 48.dp\n                        )""",
    """                        TeamBadge(\n                            teamName = hTeam?.name ?: \"Home\",\n                            logoUrl = hTeam?.logoUrl,\n                            teamId = hTeam?.id,\n                            size = 48.dp\n                        )""",
)
replace_exact(
    "app/src/main/java/com/example/ui/screens/MatchSimulationScreen.kt",
    """                        TeamBadge(\n                            teamName = aTeam?.name ?: \"Away\",\n                            logoUrl = aTeam?.logoUrl,\n                            size = 48.dp\n                        )""",
    """                        TeamBadge(\n                            teamName = aTeam?.name ?: \"Away\",\n                            logoUrl = aTeam?.logoUrl,\n                            teamId = aTeam?.id,\n                            size = 48.dp\n                        )""",
)
replace_exact(
    "app/src/main/java/com/example/ui/screens/StandingsScreen.kt",
    "TeamBadge(logoUrl = team.logoUrl, teamName = team.name, size = 16.dp)",
    "TeamBadge(logoUrl = team.logoUrl, teamName = team.name, teamId = team.id, size = 16.dp)",
    expected=2,
)
replace_exact(
    "app/src/main/java/com/example/ui/screens/StandingsScreen.kt",
    "TeamBadge(logoUrl = homeTeam.logoUrl, teamName = homeTeam.name, size = 24.dp)",
    "TeamBadge(logoUrl = homeTeam.logoUrl, teamName = homeTeam.name, teamId = homeTeam.id, size = 24.dp)",
    expected=2,
)
replace_exact(
    "app/src/main/java/com/example/ui/screens/StandingsScreen.kt",
    "TeamBadge(logoUrl = awayTeam.logoUrl, teamName = awayTeam.name, size = 24.dp)",
    "TeamBadge(logoUrl = awayTeam.logoUrl, teamName = awayTeam.name, teamId = awayTeam.id, size = 24.dp)",
    expected=2,
)
replace_exact(
    "app/src/main/java/com/example/ui/components/standings/TopScorersView.kt",
    "TeamBadge(logoUrl = team.logoUrl, teamName = team.name, size = 28.dp)",
    "TeamBadge(logoUrl = team.logoUrl, teamName = team.name, teamId = team.id, size = 28.dp)",
)
replace_exact(
    "app/src/main/java/com/example/ui/components/standings/TopScorersView.kt",
    "TeamBadge(logoUrl = team.logoUrl, teamName = team.name, size = if (rank == 1) 28.dp else 22.dp)",
    "TeamBadge(logoUrl = team.logoUrl, teamName = team.name, teamId = team.id, size = if (rank == 1) 28.dp else 22.dp)",
)

# The team-selection and Dashboard call sites were already ID-based in the baseline.
# Make the baseline invariant explicit so a future partial UI route cannot silently regress.
def assert_team_badges_have_ids():
    failures = []
    for target in (ROOT / "app/src/main/java").rglob("*.kt"):
        text = target.read_text(encoding="utf-8")
        search_from = 0
        while True:
            start = text.find("TeamBadge(", search_from)
            if start < 0:
                break
            # Ignore the composable declaration itself: "fun TeamBadge(".
            prefix = text[max(0, start - 8):start]
            if "fun " in prefix:
                search_from = start + 10
                continue
            depth = 0
            end = start
            while end < len(text):
                char = text[end]
                if char == "(":
                    depth += 1
                elif char == ")":
                    depth -= 1
                    if depth == 0:
                        end += 1
                        break
                end += 1
            call = text[start:end]
            if "teamId" not in call:
                line = text.count("\n", 0, start) + 1
                failures.append(f"{target.relative_to(ROOT)}:{line}: {call[:160]}")
            search_from = max(end, start + 10)
    if failures:
        raise RuntimeError("TeamBadge calls without stable teamId:\n" + "\n".join(failures))


assert_team_badges_have_ids()

# ---------------------------------------------------------------------------
# 2) Match completion: never expose FINISHED before isPlayed+stats commit.
#    This closes the race where Back cancelled the liveMatchJob mid-transaction.
# ---------------------------------------------------------------------------
match_vm = "app/src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt"
replace_exact(
    match_vm,
    """    if (m >= 90 && _matchState.value == GameViewModel.MatchState.PLAYING) {\n        _matchState.value = GameViewModel.MatchState.FINISHED\n        val fix = liveMatchFixture""",
    """    if (m >= 90 && _matchState.value == GameViewModel.MatchState.PLAYING) {\n        val fix = liveMatchFixture""",
)
replace_exact(
    match_vm,
    """            repo.withTransaction {\n                val persistedFixture = repo.getFixture(updatedFixture.id)\n                if (persistedFixture?.isPlayed != true) {\n                    repo.updateFixture(updatedFixture)\n                    processMatchEventsAndStats(updatedFixture, currentMatchEvents)\n                }\n            }\n        }\n    }\n}\n\nfun GameViewModel.substitutePlayer""",
    """            repo.withTransaction {\n                val persistedFixture = repo.getFixture(updatedFixture.id)\n                if (persistedFixture?.isPlayed != true) {\n                    repo.updateFixture(updatedFixture)\n                    processMatchEventsAndStats(updatedFixture, currentMatchEvents)\n                }\n            }\n        }\n        // Only now can the UI expose \"Voltar à Central\".  exitLiveMatch() cancels\n        // liveMatchJob, so publishing FINISHED before this durable commit could roll back\n        // isPlayed=true and resurrect the exact same fixture on the Dashboard.\n        _matchState.value = GameViewModel.MatchState.FINISHED\n    }\n}\n\nfun GameViewModel.substitutePlayer""",
)

# ---------------------------------------------------------------------------
# 3) Week-4 memory spike: keep exact player order/Random call sequence, but load the
#    world in bounded batches instead of retaining ~60k full Player entities while
#    also retaining the lightweight stale-plan snapshots.
# ---------------------------------------------------------------------------
replace_exact(
    "app/src/main/java/com/example/data/daos.kt",
    """    @Query(\"SELECT * FROM players ORDER BY force DESC, name ASC\")\n    suspend fun getAllPlayers(): List<Player>\n""",
    """    @Query(\"SELECT * FROM players ORDER BY force DESC, name ASC\")\n    suspend fun getAllPlayers(): List<Player>\n\n    /** Monthly evolution keeps the exact canonical getAllPlayers ordering but bounds heap usage. */\n    @Query(\"SELECT * FROM players ORDER BY force DESC, name ASC LIMIT :limit OFFSET :offset\")\n    suspend fun getAllPlayersBatch(limit: Int, offset: Int): List<Player>\n""",
)
replace_exact(
    "app/src/main/java/com/example/data/repository.kt",
    """    suspend fun getAllPlayers(): List<Player> = db.playerDao().getAllPlayers()\n    suspend fun getPlayersByTeam(teamId: Long?): List<Player> = db.playerDao().getPlayersByTeam(teamId)""",
    """    suspend fun getAllPlayers(): List<Player> = db.playerDao().getAllPlayers()\n    suspend fun getAllPlayersBatch(limit: Int, offset: Int): List<Player> =\n        db.playerDao().getAllPlayersBatch(limit, offset)\n    suspend fun getPlayersByTeam(teamId: Long?): List<Player> = db.playerDao().getPlayersByTeam(teamId)""",
)

p = ROOT / "app/src/main/java/com/example/usecase/PlayerEvolutionUseCase.kt"
text = p.read_text(encoding="utf-8")
start_marker = "        val allPlayers = repository.getAllPlayers()\n"
end_marker = "\n        return MonthlyEvolutionPlan(\n"
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise RuntimeError("PlayerEvolutionUseCase prepareMonthlyEvolution block markers not found")
new_block = """        val expectedPlayerCount = repository.getMonthlyEvolutionPlayerCount()\n        val allTeams = repository.getAllTeams().associateBy { it.id }\n        val evolutionResults = ArrayList<PlayerEvolutionResult>(\n            if (retainDetailedResults) expectedPlayerCount else minOf(expectedPlayerCount, 4096)\n        )\n        val changedPlayers = ArrayList<Player>()\n        val historyLogs = ArrayList<HistoricoEvolucao>()\n        val expectedInputs = ArrayList<MonthlyEvolutionInputSnapshot>(expectedPlayerCount)\n        val referencedTeamIds = HashSet<Long>()\n\n        // Keep the exact ORDER BY force DESC, name ASC and call the same evolution engine in\n        // sequence. Kotlin Random.Default therefore sees the same uninterrupted call sequence;\n        // only the lifetime of each full Player batch changes.\n        var offset = 0\n        while (offset < expectedPlayerCount) {\n            val batch = repository.getAllPlayersBatch(MONTHLY_EVOLUTION_BATCH_SIZE, offset)\n            check(batch.isNotEmpty()) {\n                \"Monthly evolution player scan ended at $offset of $expectedPlayerCount rows.\"\n            }\n            val batchResults = if (retainDetailedResults) {\n                PlayerEvolutionMonthlyEngine.process(batch, allTeams, periodDate)\n            } else {\n                PlayerEvolutionMonthlyEngine.processChanged(batch, allTeams, periodDate)\n            }\n            evolutionResults.addAll(batchResults)\n\n            for (result in batchResults) {\n                if (result.historyLogs.isNotEmpty() || result.netChange != 0.0) changedPlayers.add(result.player)\n                if (result.historyLogs.isNotEmpty()) historyLogs.addAll(result.historyLogs)\n            }\n            for (player in batch) {\n                expectedInputs.add(player.toMonthlyEvolutionInputSnapshot())\n                player.teamId?.let(referencedTeamIds::add)\n            }\n            offset += batch.size\n        }\n        check(expectedInputs.size == expectedPlayerCount) {\n            \"Monthly evolution expected $expectedPlayerCount inputs but captured ${expectedInputs.size}.\"\n        }\n\n        val expectedTrainingLevels = referencedTeamIds.associateWith { teamId ->\n            allTeams[teamId]?.trainingCenterLevel ?: 1\n        }\n"""
text = text[:start] + new_block + text[end:]
text = text.replace("            expectedPlayerCount = allPlayers.size,", "            expectedPlayerCount = expectedPlayerCount,", 1)
if "allPlayers.size" in text[text.find("suspend fun prepareMonthlyEvolution"):text.find("suspend fun commitMonthlyEvolution")]:
    raise RuntimeError("prepareMonthlyEvolution still retains allPlayers")
# Add a deliberately small batch constant near the use case class.
needle = "class PlayerEvolutionUseCase(private val repository: GameRepository) {\n"
if text.count(needle) != 1:
    raise RuntimeError("PlayerEvolutionUseCase class marker mismatch")
text = text.replace(needle, "private const val MONTHLY_EVOLUTION_BATCH_SIZE = 512\n\n" + needle, 1)
p.write_text(text, encoding="utf-8")

# ---------------------------------------------------------------------------
# 4) Focused regression tests: real UI identity path, durable next-fixture query,
#    live-match persistence ordering, week-4 monthly production path.
# ---------------------------------------------------------------------------
tests = {
"app/src/test/java/com/example/ui/screens/ClubCrestUiIdentityRegressionTest.kt": r'''package com.example.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ClubCrestUiIdentityRegressionTest {
    @Test
    fun `every production TeamBadge call carries stable team id`() {
        val files = listOf(
            "src/main/java/com/example/ui/screens/TeamSelectionScreen.kt",
            "src/main/java/com/example/ui/components/dashboard/DashboardTabContent.kt",
            "src/main/java/com/example/ui/screens/EditorScreen.kt",
            "src/main/java/com/example/ui/components/editor/EditorCards.kt",
            "src/main/java/com/example/ui/screens/MatchSimulationScreen.kt",
            "src/main/java/com/example/ui/screens/StandingsScreen.kt",
            "src/main/java/com/example/ui/components/standings/TopScorersView.kt"
        )
        val calls = files.flatMap { file -> extractCalls(readProjectSource(file), file) }
        assertTrue("Expected multiple real UI badge routes", calls.size >= 15)
        calls.forEach { (file, call) ->
            assertTrue("$file must pass Team.id into TeamBadge: $call", call.contains("teamId"))
        }
    }

    @Test
    fun `mandatory editor match standings and dashboard routes are id based`() {
        val editor = readProjectSource("src/main/java/com/example/ui/components/editor/EditorCards.kt")
        val match = readProjectSource("src/main/java/com/example/ui/screens/MatchSimulationScreen.kt")
        val standings = readProjectSource("src/main/java/com/example/ui/screens/StandingsScreen.kt")
        val dashboard = readProjectSource("src/main/java/com/example/ui/components/dashboard/DashboardTabContent.kt")
        assertTrue(editor.contains("teamId = team.id"))
        assertTrue(match.contains("teamId = hTeam?.id"))
        assertTrue(match.contains("teamId = aTeam?.id"))
        assertTrue(standings.contains("teamId = team.id"))
        assertTrue(standings.contains("teamId = homeTeam.id"))
        assertTrue(standings.contains("teamId = awayTeam.id"))
        assertTrue(dashboard.contains("teamId = pTeam.id"))
        assertTrue(dashboard.contains("teamId = opponentId"))
    }

    private fun extractCalls(source: String, file: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        var cursor = 0
        while (true) {
            val start = source.indexOf("TeamBadge(", cursor)
            if (start < 0) break
            if (source.substring(maxOf(0, start - 8), start).contains("fun ")) {
                cursor = start + 10
                continue
            }
            var depth = 0
            var end = start
            while (end < source.length) {
                when (source[end]) {
                    '(' -> depth++
                    ')' -> if (--depth == 0) { end++; break }
                }
                end++
            }
            out += file to source.substring(start, end)
            cursor = end
        }
        return out
    }

    private fun readProjectSource(relativeToApp: String): String {
        val candidates = listOf(File(relativeToApp), File("app/$relativeToApp"), File("../app/$relativeToApp"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relativeToApp; cwd=${File(".").absolutePath}")
    }
}
''',
"app/src/test/java/com/example/ui/viewmodel/LiveMatchPersistenceOrderingRegressionTest.kt": r'''package com.example.ui.viewmodel

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMatchPersistenceOrderingRegressionTest {
    @Test
    fun `natural full time is persisted before FINISHED is published to UI`() {
        val source = readProjectSource("src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt")
        val start = source.indexOf("suspend fun GameViewModel.runMatchSimulationLoop")
        val end = source.indexOf("fun GameViewModel.substitutePlayer", start)
        val body = source.substring(start, end)
        val fullTime = body.indexOf("if (m >= 90")
        val transaction = body.indexOf("repo.withTransaction", fullTime)
        val finishPublication = body.indexOf("_matchState.value = GameViewModel.MatchState.FINISHED", fullTime)
        assertTrue(fullTime >= 0)
        assertTrue(transaction > fullTime)
        assertTrue("FINISHED must not expose Back until isPlayed and stats are durable", finishPublication > transaction)
        assertTrue(body.substring(transaction, finishPublication).contains("repo.updateFixture(updatedFixture)"))
        assertTrue(body.substring(transaction, finishPublication).contains("processMatchEventsAndStats"))
    }

    @Test
    fun `skip of prepared match never starts a new engine or RNG stream`() {
        val source = readProjectSource("src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt")
        val start = source.indexOf("fun GameViewModel.skipLiveMatch(")
        val end = source.indexOf("suspend fun GameViewModel.processMatchEventsAndStats", start)
        val body = source.substring(start, end)
        assertTrue(body.contains("finishPreparedLiveFixture(targetFixture)"))
        check(!body.contains("simulateMatchDetailed"))
        check(!body.contains("Random.nextLong"))
    }

    private fun readProjectSource(relativeToApp: String): String {
        val candidates = listOf(File(relativeToApp), File("app/$relativeToApp"), File("../app/$relativeToApp"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relativeToApp; cwd=${File(".").absolutePath}")
    }
}
''',
"app/src/test/java/com/example/usecase/CompletedFixtureDashboardRegressionTest.kt": r'''package com.example.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Team
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class CompletedFixtureDashboardRegressionTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private val dbName = "completed-fixture-dashboard-regression.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        openDatabase()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `finish A then dashboard and reopen both keep A completed and expose B`() = runTest {
        repository.saveTeams(
            listOf(
                Team(id = 1L, name = "Cruzeiro", city = "BH", state = "MG", division = 1, rating = 75),
                Team(id = 2L, name = "São Paulo", city = "SP", state = "SP", division = 1, rating = 75),
                Team(id = 3L, name = "Santos", city = "Santos", state = "SP", division = 1, rating = 72)
            )
        )
        repository.saveGameSave(GameSave(currentSeason = 2026, currentWeek = 1, playerTeamId = 1L))
        repository.saveFixtures(
            listOf(
                Fixture(id = 101L, season = 2026, week = 1, homeTeamId = 1L, awayTeamId = 2L, competitionType = "SERIE_A"),
                Fixture(id = 102L, season = 2026, week = 2, homeTeamId = 3L, awayTeamId = 1L, competitionType = "SERIE_A")
            )
        )

        val fixtureA = requireNotNull(repository.getFixture(101L))
        repository.updateFixture(fixtureA.copy(homeScore = 1, awayScore = 0, isPlayed = true))

        val immediateNext = repository.getNextFixtureForTeamFlow(2026, 1, 1L).first()
        assertEquals(102L, immediateNext?.id)
        assertTrue(requireNotNull(repository.getFixture(101L)).isPlayed)

        db.close()
        openDatabase()

        val reopenedA = requireNotNull(repository.getFixture(101L))
        val reopenedNext = repository.getNextFixtureForTeamFlow(2026, 1, 1L).first()
        assertTrue(reopenedA.isPlayed)
        assertEquals(1, reopenedA.homeScore)
        assertEquals(0, reopenedA.awayScore)
        assertEquals(102L, reopenedNext?.id)
        assertEquals(1L, repository.getGameSave()?.playerTeamId)
    }

    private fun openDatabase() {
        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        repository = GameRepository(db)
    }
}
''',
"app/src/test/java/com/example/usecase/MonthlyEvolutionWeekFourRegressionTest.kt": r'''package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class MonthlyEvolutionWeekFourRegressionTest {
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
    fun `week four production preparation crosses several bounded batches and remains committable`() = runTest {
        val team = Team(id = 1L, name = "Cruzeiro", city = "BH", state = "MG", division = 1, rating = 75)
        repository.saveTeams(listOf(team))
        val save = GameSave(currentSeason = 2026, currentWeek = 4, playerTeamId = team.id)
        repository.saveGameSave(save)

        val playerCount = 2_100 // > 4 production batches; focused, not a long stress test.
        repository.savePlayers(
            List(playerCount) { index ->
                Player(
                    id = index.toLong() + 1L,
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

        val useCase = PlayerEvolutionUseCase(repository)
        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W4")
        assertEquals(playerCount, plan.expectedPlayerCount)
        assertEquals(playerCount, plan.expectedInputs.size)
        assertTrue(useCase.commitMonthlyEvolution(plan))

        // The observed crash happened before the calendar could move beyond week 4.
        repository.saveGameSave(requireNotNull(repository.getGameSave()).copy(currentWeek = 5))
        assertEquals(5, repository.getGameSave()?.currentWeek)
        assertEquals(playerCount, repository.getAllPlayers().size)
    }
}
'''
}
for relative, content in tests.items():
    target = ROOT / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")

# Strengthen the existing skip regression without altering the legitimate possibility of later goals.
replace_exact(
    "app/src/test/java/com/example/ui/viewmodel/LiveMatchSkipRegressionTest.kt",
    """        assertTrue(skipBody.contains(\"finishPreparedLiveFixture(targetFixture)\"))\n        assertTrue(skipBody.contains(\"else {\\n                simulateSingleUserFixtureSafely\"))""",
    """        assertTrue(skipBody.contains(\"finishPreparedLiveFixture(targetFixture)\"))\n        assertFalse(skipBody.contains(\"simulateMatchDetailed\"))\n        assertFalse(skipBody.contains(\"Random.nextLong\"))\n        assertTrue(skipBody.contains(\"else {\\n                simulateSingleUserFixtureSafely\"))""",
)

# Persist the focused suite in the branch's normal manual-test workflow.
workflow = " .github/workflows/manual-test-bugfix-apk.yml".strip()
replace_exact(
    workflow,
    """            --tests com.example.ui.viewmodel.LiveMatchSkipRegressionTest \\\n            --tests com.example.data.PreCareerEditorOverridesTest \\\""",
    """            --tests com.example.ui.viewmodel.LiveMatchSkipRegressionTest \\\n            --tests com.example.ui.viewmodel.LiveMatchPersistenceOrderingRegressionTest \\\n            --tests com.example.ui.screens.ClubCrestUiIdentityRegressionTest \\\n            --tests com.example.usecase.CompletedFixtureDashboardRegressionTest \\\n            --tests com.example.usecase.MonthlyEvolutionWeekFourRegressionTest \\\n            --tests com.example.usecase.SeasonSimulationRegressionTest \\\n            --tests com.example.data.PreCareerEditorOverridesTest \\\""",
)

print("Focused runtime bugfix patch applied successfully.")
