from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_exact(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"expected source block not found in {path}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# ---------------------------------------------------------------------------
# P1: resolve persisted legacy-slot names through the same legacyTeamId that
# already selects the certified crest. IDs and all factual/sporting fields stay
# untouched; only the displayed name is canonicalized at the repository read boundary.
# ---------------------------------------------------------------------------
repo = ROOT / "app/src/main/java/com/example/data/repository.kt"
replace_exact(repo, "import kotlinx.coroutines.flow.flowOf\n", "import kotlinx.coroutines.flow.flowOf\nimport kotlinx.coroutines.flow.map\n")
replace_exact(
    repo,
    "class GameRepository(internal val db: AppDatabase) {",
    '''private fun Team.withCanonicalRealClubIdentity(): Team {
    val replacement = BrasfootRealClubIdentity.replacementForLegacyTeamId(id) ?: return this
    return if (name == replacement.realClubName) this else copy(name = replacement.realClubName)
}

private fun List<Team>.withCanonicalRealClubIdentities(): List<Team> =
    map { it.withCanonicalRealClubIdentity() }

class GameRepository(internal val db: AppDatabase) {'''
)
replace_exact(repo, "    val allTeamsFlow: Flow<List<Team>> = db.teamDao().getAllTeamsFlow()\n", "    val allTeamsFlow: Flow<List<Team>> = db.teamDao().getAllTeamsFlow().map { it.withCanonicalRealClubIdentities() }\n")
replace_exact(repo, "    fun getTeamsByLeagueFlow(leagueCountry: String): Flow<List<Team>> = db.teamDao().getTeamsByLeagueFlow(leagueCountry)\n", "    fun getTeamsByLeagueFlow(leagueCountry: String): Flow<List<Team>> = db.teamDao().getTeamsByLeagueFlow(leagueCountry).map { it.withCanonicalRealClubIdentities() }\n")
replace_exact(repo, "        db.teamDao().getTeamsByCountryDivisionFlow(country, division)\n", "        db.teamDao().getTeamsByCountryDivisionFlow(country, division).map { it.withCanonicalRealClubIdentities() }\n")
replace_exact(repo, "    fun getTeamFlow(teamId: Long): Flow<Team?> = db.teamDao().getTeamFlow(teamId)\n", "    fun getTeamFlow(teamId: Long): Flow<Team?> = db.teamDao().getTeamFlow(teamId).map { it?.withCanonicalRealClubIdentity() }\n")
replace_exact(repo, "            .map { chunk -> db.teamDao().getTeamsByIdsFlow(chunk) }\n", "            .map { chunk -> db.teamDao().getTeamsByIdsFlow(chunk).map { it.withCanonicalRealClubIdentities() } }\n")
replace_exact(repo, "    suspend fun getAllTeams(): List<Team> = db.teamDao().getAllTeams()\n    suspend fun getTeam(id: Long): Team? = db.teamDao().getTeam(id)\n", "    suspend fun getAllTeams(): List<Team> = db.teamDao().getAllTeams().withCanonicalRealClubIdentities()\n    suspend fun getTeam(id: Long): Team? = db.teamDao().getTeam(id)?.withCanonicalRealClubIdentity()\n")

# ---------------------------------------------------------------------------
# P1: strength-99 consistency + targeted reactive roster flow for Editor.
# ---------------------------------------------------------------------------
editor_vm = ROOT / "app/src/main/java/com/example/ui/viewmodel/GameViewModelEditor.kt"
replace_exact(editor_vm, "import kotlinx.coroutines.Dispatchers\n", "import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.flow.Flow\nimport kotlinx.coroutines.flow.flatMapLatest\nimport kotlinx.coroutines.flow.flowOf\n")
replace_exact(
    editor_vm,
    "fun GameViewModel.ensureRosterForTeam(teamId: Long) {",
    '''fun GameViewModel.editorPlayersForTeamFlow(teamId: Long?): Flow<List<Player>> =
    activeRepositoryFlow.flatMapLatest { repository ->
        if (repository == null || teamId == null) flowOf(emptyList())
        else repository.getPlayersForTeamFlow(teamId)
    }

internal fun applyEditedTeamStrength(players: List<Player>, newRating: Int): List<Player> =
    players.map { player ->
        val currentAttr = player.getAtributosObject()
        val oldForce = player.force.coerceAtLeast(1)
        val ratio = newRating.toDouble() / oldForce.toDouble()
        val scaledAttr = currentAttr.copy(
            finalizacao = (currentAttr.finalizacao * ratio).roundToInt().coerceIn(10, 99),
            passe = (currentAttr.passe * ratio).roundToInt().coerceIn(10, 99),
            velocidade = (currentAttr.velocidade * ratio).roundToInt().coerceIn(10, 99),
            forca = (currentAttr.forca * ratio).roundToInt().coerceIn(10, 99),
            visaoJogo = (currentAttr.visaoJogo * ratio).roundToInt().coerceIn(10, 99),
            desarme = (currentAttr.desarme * ratio).roundToInt().coerceIn(10, 99)
        )
        player.copy(
            force = newRating,
            potential = maxOf(player.potential, newRating + 3).coerceIn(15, 99),
            atributosJson = AtributosConverter.atributosToJson(scaledAttr),
            finishing = scaledAttr.finalizacao,
            passing = scaledAttr.passe,
            pace = scaledAttr.velocidade,
            strength = scaledAttr.forca,
            vision = scaledAttr.visaoJogo,
            defense = scaledAttr.desarme
        )
    }

fun GameViewModel.ensureRosterForTeam(teamId: Long) {'''
)
old_strength_map = '''        val players = editorRepository.getPlayersByTeam(teamId)
        val updatedPlayers = players.map { player ->
            val currentAttr = player.getAtributosObject()
            val oldForce = player.force.coerceAtLeast(1)
            val ratio = newRating.toDouble() / oldForce.toDouble()

            val scaledAttr = currentAttr.copy(
                finalizacao = (currentAttr.finalizacao * ratio).roundToInt().coerceIn(10, 99),
                passe = (currentAttr.passe * ratio).roundToInt().coerceIn(10, 99),
                velocidade = (currentAttr.velocidade * ratio).roundToInt().coerceIn(10, 99),
                forca = (currentAttr.forca * ratio).roundToInt().coerceIn(10, 99),
                visaoJogo = (currentAttr.visaoJogo * ratio).roundToInt().coerceIn(10, 99),
                desarme = (currentAttr.desarme * ratio).roundToInt().coerceIn(10, 99)
            )

            val newJson = AtributosConverter.atributosToJson(scaledAttr)
            player.copy(
                force = newRating,
                potential = maxOf(player.potential, newRating + 3).coerceIn(15, 99),
                atributosJson = newJson,
                finishing = scaledAttr.finalizacao,
                passing = scaledAttr.passe,
                pace = scaledAttr.velocidade,
                strength = scaledAttr.forca,
                vision = scaledAttr.visaoJogo,
                defense = scaledAttr.desarme
            )
        }
        editorRepository.updatePlayers(updatedPlayers)
'''
replace_exact(editor_vm, old_strength_map, '''        val players = editorRepository.getPlayersByTeam(teamId)
        editorRepository.updatePlayers(applyEditedTeamStrength(players, newRating))
''')

editor_ui = ROOT / "app/src/main/java/com/example/ui/screens/EditorScreen.kt"
replace_exact(
    editor_ui,
    '''    var selectedPlayerTeamFilter by remember { mutableStateOf<Long?>(null) } // null = Todos
    var selectedPositionFilter by remember { mutableStateOf("Todas") }
''',
    '''    var selectedPlayerTeamFilter by remember { mutableStateOf<Long?>(null) } // null = Todos
    val selectedTeamPlayers by remember(selectedPlayerTeamFilter) {
        viewModel.editorPlayersForTeamFlow(selectedPlayerTeamFilter)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedPositionFilter by remember { mutableStateOf("Todas") }
'''
)
replace_exact(
    editor_ui,
    '''    val filteredPlayers = remember(allPlayers, selectedTab, playerSearchQuery, selectedPlayerTeamFilter, selectedPositionFilter) {
        if (selectedTab != 1) {
            emptyList()
        } else {
            var seq = allPlayers.asSequence()
''',
    '''    val filteredPlayers = remember(allPlayers, selectedTeamPlayers, selectedTab, playerSearchQuery, selectedPlayerTeamFilter, selectedPositionFilter) {
        if (selectedTab != 1) {
            emptyList()
        } else {
            val playerSource = if (selectedPlayerTeamFilter != null) selectedTeamPlayers else allPlayers
            var seq = playerSource.asSequence()
'''
)

# ---------------------------------------------------------------------------
# P1/P2: Market owns an immediate local removal set; scouting UI is removed and
# the real persisted Player.force is always shown.
# ---------------------------------------------------------------------------
market = ROOT / "app/src/main/java/com/example/ui/screens/TransfersScreen.kt"
replace_exact(market, "    var selectedPlayerForScouting by remember { mutableStateOf<Player?>(null) }\n", "    var locallyPurchasedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }\n")
replace_exact(market, '    var currentSubTab by remember { mutableStateOf("MERCADO") } // "MERCADO", "OLHEIRO", "STAFF"\n', '    var currentSubTab by remember { mutableStateOf("MERCADO") } // "MERCADO", "STAFF"\n')
replace_exact(market, "        searchSortBy\n    ) {", "        searchSortBy,\n        locallyPurchasedIds\n    ) {")
replace_exact(market, "                player.isTransferMarketCandidateFor(save?.playerTeamId) &&\n", "                player.id !in locallyPurchasedIds &&\n                player.isTransferMarketCandidateFor(save?.playerTeamId) &&\n")
replace_exact(market, '''            val subTabs = listOf(
                "MERCADO" to "MERCADO",
                "OLHEIRO" to "OLHEIRO",
                "STAFF" to "STAFF"
            )
''', '''            val subTabs = listOf(
                "MERCADO" to "MERCADO",
                "STAFF" to "STAFF"
            )
''')
replace_exact(market, '''                        val isGlobalReveal = save?.globalScoutRevealWeeksRemaining ?: 0 > 0
                        val scoutedLevel = player.scoutedLevel
                        val observedForce = player.getObservedForce(isGlobalReveal, player.teamId == save?.playerTeamId)

''', '')
replace_exact(market, '''                                .fillMaxWidth()
                                .clickable { if (scoutedLevel >= 0) selectedPlayerForPurchase = player },
''', '''                                .fillMaxWidth()
                                .clickable { if (isWindowOpen) selectedPlayerForPurchase = player },
''')
replace_exact(market, "                                    text = observedForce,\n", "                                    text = player.force.toString(),\n")
old_action = '''                                Box(modifier = Modifier.weight(1.0f), contentAlignment = Alignment.Center) {
                                    if (scoutedLevel <= 0 && !isGlobalReveal) {
                                        if (scoutedLevel < 0) {
                                            Text("OBSERVANDO", color = AccentGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        } else {
                                            Button(
                                                onClick = { selectedPlayerForScouting = player },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = TurfDeepGreen),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.height(26.dp)
                                            ) {
                                                Text("OBSERVAR", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = { if (isWindowOpen) selectedPlayerForPurchase = player },
                                            enabled = isWindowOpen,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentLime,
                                                contentColor = TurfDeepGreen,
                                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                                disabledContentColor = Color.Gray
                                            ),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text(if (isWindowOpen) "NEGOCIAR" else "FECHADA", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
'''
new_action = '''                                Box(modifier = Modifier.weight(1.0f), contentAlignment = Alignment.Center) {
                                    Button(
                                        onClick = { if (isWindowOpen) selectedPlayerForPurchase = player },
                                        enabled = isWindowOpen,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentLime,
                                            contentColor = TurfDeepGreen,
                                            disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                            disabledContentColor = Color.Gray
                                        ),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text(if (isWindowOpen) "NEGOCIAR" else "FECHADA", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                }
'''
replace_exact(market, old_action, new_action)
# Remove the complete Olheiro sub-tab branch while keeping Staff.
text = market.read_text(encoding="utf-8")
start = text.index('        } else if (currentSubTab == "OLHEIRO") {')
end_marker = '''        } else {
            Box(modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)) {
                StaffPanel(viewModel, save)
            }
        }
'''
end = text.index(end_marker, start)
text = text[:start] + end_marker + text[end + len(end_marker):]
market.write_text(text, encoding="utf-8")
# Remove scouting dialog invocation and wire immediate successful-purchase removal.
text = market.read_text(encoding="utf-8")
scout_start = text.find('    selectedPlayerForScouting?.let { p ->')
if scout_start >= 0:
    purchase_start = text.index('    selectedPlayerForPurchase?.let { p ->', scout_start)
    text = text[:scout_start] + text[purchase_start:]
market.write_text(text, encoding="utf-8")
replace_exact(market, '''        PurchaseNegotiationDialog(
            player = p,
            viewModel = viewModel,
            onDismiss = { selectedPlayerForPurchase = null }
        )
''', '''        PurchaseNegotiationDialog(
            player = p,
            viewModel = viewModel,
            onPurchased = { locallyPurchasedIds = locallyPurchasedIds + p.id },
            onDismiss = { selectedPlayerForPurchase = null }
        )
''')

purchase = ROOT / "app/src/main/java/com/example/ui/components/transfers/PurchaseNegotiationDialog.kt"
replace_exact(purchase, "import com.example.ui.components.finances.ScoutSelectionDialog\n", "")
replace_exact(purchase, '''    viewModel: GameViewModel,
    onDismiss: () -> Unit
''', '''    viewModel: GameViewModel,
    onPurchased: () -> Unit = {},
    onDismiss: () -> Unit
''')
replace_exact(purchase, '''    val isGlobalReveal = save?.globalScoutRevealWeeksRemaining ?: 0 > 0
    val isUserTeam = player.teamId == save?.playerTeamId
    val observedForce = player.getObservedForce(isGlobalReveal, isUserTeam)

    var showScoutDialog by remember { mutableStateOf(false) }

''', '')
text = purchase.read_text(encoding="utf-8")
scout_block = '''    if (showScoutDialog) {
        ScoutSelectionDialog(
            player = player,
            viewModel = viewModel,
            onDismiss = { showScoutDialog = false }
        )
    }

'''
if scout_block not in text:
    raise SystemExit("purchase scout dialog block not found")
text = text.replace(scout_block, '', 1)
purchase.write_text(text, encoding="utf-8")
replace_exact(purchase, "                                    text = observedForce,\n", "                                    text = player.force.toString(),\n")
replace_exact(purchase, '                                text = "Força: $observedForce • Idade: ${player.age} anos • Nal: ${player.nationality ?: "Sem clube"}",\n', '                                text = "Força: ${player.force} • Idade: ${player.age} anos • Nal: ${player.nationality ?: "Sem clube"}",\n')
# Remove watchlist + scouting visibility block between player card and market-value row.
text = purchase.read_text(encoding="utf-8")
block_start = text.index('                val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()')
block_end = text.index('                Row(\n                    modifier = Modifier.fillMaxWidth(),\n                    horizontalArrangement = Arrangement.SpaceBetween,\n                    verticalAlignment = Alignment.CenterVertically\n                ) {\n                    Text("Valor de Mercado:"', block_start)
text = text[:block_start] + text[block_end:]
purchase.write_text(text, encoding="utf-8")
# Every successful purchase path publishes immediate UI removal before dismissing.
replace_exact(purchase, '''                                if (success) {
                                    onDismiss()
''', '''                                if (success) {
                                    onPurchased()
                                    onDismiss()
''')
replace_exact(purchase, '''                                                    is com.example.usecase.ProcessTransfersUseCase.TransferResult.Success -> onDismiss()
''', '''                                                    is com.example.usecase.ProcessTransfersUseCase.TransferResult.Success -> {
                                                        onPurchased()
                                                        onDismiss()
                                                    }
''')
replace_exact(purchase, '''                                                            is com.example.usecase.ProcessTransfersUseCase.TransferResult.Success -> onDismiss()
''', '''                                                            is com.example.usecase.ProcessTransfersUseCase.TransferResult.Success -> {
                                                                onPurchased()
                                                                onDismiss()
                                                            }
''')

# ---------------------------------------------------------------------------
# Focused regressions.
# ---------------------------------------------------------------------------
tests = {
"app/src/test/java/com/example/ui/viewmodel/ClubStrengthImmediateRefreshTest.kt": r'''package com.example.ui.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class ClubStrengthImmediateRefreshTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = GameRepository(db)
    }
    @After fun tearDown() = db.close()

    @Test fun `targeted roster flow publishes strength edit without global player reload`() = runTest {
        repository.saveTeams(listOf(Team(id = 7L, name = "Club", city = "A", state = "AA", division = 1)))
        val roster = (1L..18L).map { Player(id = it, teamId = 7L, name = "P$it", age = 24, position = "MEI", force = 70) }
        repository.savePlayers(roster)
        val emitted = async {
            repository.getPlayersForTeamFlow(7L)
                .dropWhile { players -> players.size != 18 || players.any { it.force != 99 } }
                .first()
        }
        repository.updatePlayers(roster.map { it.copy(force = 99) })
        assertEquals(18, emitted.await().size)
        assertEquals(setOf(99), emitted.await().map { it.force }.toSet())
    }
}
''',
"app/src/test/java/com/example/ui/viewmodel/ClubStrength99RosterConsistencyTest.kt": r'''package com.example.ui.viewmodel

import com.example.data.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class ClubStrength99RosterConsistencyTest {
    @Test fun `editor team strength 99 applies 99 to every roster member`() {
        val roster = (1L..30L).map { id ->
            Player(id = id, teamId = 1L, name = "P$id", age = 24, position = "MEI", force = (60 + id % 30).toInt())
        }
        val updated = applyEditedTeamStrength(roster, 99)
        assertEquals(roster.map { it.id }, updated.map { it.id })
        assertEquals(setOf(99), updated.map { it.force }.toSet())
    }
}
''',
"app/src/test/java/com/example/usecase/MarketPurchaseImmediateRemovalTest.kt": r'''package com.example.usecase

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketPurchaseImmediateRemovalTest {
    @Test fun `successful purchase is removed locally before global Room reconciliation`() {
        val market = source("src/main/java/com/example/ui/screens/TransfersScreen.kt")
        val dialog = source("src/main/java/com/example/ui/components/transfers/PurchaseNegotiationDialog.kt")
        assertTrue(market.contains("locallyPurchasedIds"))
        assertTrue(market.contains("player.id !in locallyPurchasedIds"))
        assertTrue(market.contains("onPurchased = { locallyPurchasedIds = locallyPurchasedIds + p.id }"))
        assertTrue(dialog.contains("onPurchased: () -> Unit = {}"))
        assertTrue(dialog.split("onPurchased()").size >= 4)
    }
    private fun source(path: String): String = listOf(File(path), File("app/$path"), File("../app/$path"))
        .firstOrNull { it.isFile }?.readText() ?: error("Missing $path")
}
''',
"app/src/test/java/com/example/usecase/MarketStrengthAlwaysVisibleTest.kt": r'''package com.example.usecase

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketStrengthAlwaysVisibleTest {
    @Test fun `market shows persisted real strength and never observed placeholder`() {
        val market = source("src/main/java/com/example/ui/screens/TransfersScreen.kt")
        val dialog = source("src/main/java/com/example/ui/components/transfers/PurchaseNegotiationDialog.kt")
        assertTrue(market.contains("text = player.force.toString()"))
        assertTrue(dialog.contains("text = player.force.toString()"))
        assertTrue(dialog.contains("Força: ${'$'}{player.force}"))
        assertFalse(market.contains("getObservedForce("))
        assertFalse(dialog.contains("getObservedForce("))
    }
    private fun source(path: String): String = listOf(File(path), File("app/$path"), File("../app/$path"))
        .firstOrNull { it.isFile }?.readText() ?: error("Missing $path")
}
''',
"app/src/test/java/com/example/usecase/ScoutRemovalNavigationTest.kt": r'''package com.example.usecase

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class ScoutRemovalNavigationTest {
    @Test fun `market exposes no scout navigation or scout dialog`() {
        val market = source("src/main/java/com/example/ui/screens/TransfersScreen.kt")
        val dialog = source("src/main/java/com/example/ui/components/transfers/PurchaseNegotiationDialog.kt")
        assertFalse(market.contains("\"OLHEIRO\" to \"OLHEIRO\""))
        assertFalse(market.contains("selectedPlayerForScouting"))
        assertFalse(market.contains("ScoutSelectionDialog("))
        assertFalse(dialog.contains("ScoutSelectionDialog("))
        assertFalse(dialog.contains("FICAR DE OLHO (OLHEIRO)"))
        assertFalse(dialog.contains("CONTRATAR OLHEIRO"))
    }
    private fun source(path: String): String = listOf(File(path), File("app/$path"), File("../app/$path"))
        .firstOrNull { it.isFile }?.readText() ?: error("Missing $path")
}
''',
"app/src/test/java/com/example/usecase/LeagueTableImmediateRefreshTest.kt": r'''package com.example.usecase

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LeagueTableImmediateRefreshTest {
    @Test fun `post match FINISHED is published only after durable week close and standings is reactive`() {
        val match = source("src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt")
        val start = match.indexOf("suspend fun GameViewModel.runMatchSimulationLoop")
        val end = match.indexOf("fun GameViewModel.substitutePlayer", start)
        val body = match.substring(start, end)
        assertTrue(body.lastIndexOf("_matchState.value = GameViewModel.MatchState.FINISHED") > body.lastIndexOf("processWeekEndEconomicAndEvolution()"))
        val standings = source("src/main/java/com/example/ui/screens/StandingsScreen.kt")
        assertTrue(standings.contains("viewModel.allFixtures.collectAsStateWithLifecycle()"))
        assertTrue(standings.contains("viewModel.allTeams.collectAsStateWithLifecycle()"))
    }
    private fun source(path: String): String = listOf(File(path), File("app/$path"), File("../app/$path"))
        .firstOrNull { it.isFile }?.readText() ?: error("Missing $path")
}
''',
"app/src/test/java/com/example/data/FixtureRealClubIdentityCrestRegressionTest.kt": r'''package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class FixtureRealClubIdentityCrestRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository

    @Before fun setup() {
        BrasfootRealClubIdentity.resetForTests()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = GameRepository(db)
    }
    @After fun tearDown() { db.close(); BrasfootRealClubIdentity.resetForTests() }

    @Test fun `legacy fixture id resolves canonical name and crest from same identity`() = runTest {
        val replacement = BrasfootRealClubIdentity.Replacement(
            legacyTeamId = 4242L, country = "Argentina", division = 1,
            legacySlotName = "Quilmes 1", realClubName = "Rosario Test",
            crestFileName = "rosario_test.png"
        )
        BrasfootRealClubIdentity.install(listOf(replacement))
        repository.saveTeams(listOf(Team(id = 4242L, name = "Quilmes 1", city = "Rosario", state = "SF", country = "Argentina", division = 1)))
        val direct = requireNotNull(repository.getTeam(4242L))
        val reactive = requireNotNull(repository.getTeamFlow(4242L).first())
        assertEquals(4242L, direct.id)
        assertEquals("Rosario Test", direct.name)
        assertEquals("Rosario Test", reactive.name)
        assertEquals("file:///android_asset/club_crests/rosario_test.png", BrasfootRealClubIdentity.crestAssetUriFor("Argentina", direct.name))
    }
}
'''
}
for relative, content in tests.items():
    path = ROOT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")

print("Applied P1/P2 identity, editor reactivity, market/scout and standings fixes.")
