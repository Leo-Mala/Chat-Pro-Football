package com.example

import android.app.Application
import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.data.Fixture
import com.example.data.GamePreferencesRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.ui.screens.CareerDashboardScreen
import com.example.ui.screens.LiveMatchScreen
import com.example.ui.screens.TeamSelectionScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.GameViewModel
import com.example.usecase.TacticsUseCase
import com.example.usecase.YouthAcademyUseCase
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden final das telas críticas da Fase 10.5.
 *
 * O slot é materializado antes de ser conectado ao ViewModel. O golden não chama selectSaveSlot(),
 * porque esse fluxo deliberadamente executa bootstrap/reparo e já é coberto pelos testes de
 * lifecycle; aqui o objetivo é proteger somente a renderização/navegação de uma carreira já
 * persistida. Capturas frescas são gravadas no build/ do módulo e comparadas por SHA-256 com o
 * manifesto versionado. Alteração visual não aprovada, baseline ausente ou captura diferente
 * falha o teste.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class Phase105CriticalUiGoldenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val moduleDir: File by lazy {
        when {
            File("app/build.gradle.kts").isFile -> File("app")
            File("build.gradle.kts").isFile && File("src/test").isDirectory -> File(".")
            else -> error(
                "Diretório do módulo app não encontrado a partir de ${File(".").absolutePath}"
            )
        }
    }

    private val expectedHashes: Map<String, String> by lazy {
        val manifest = File(moduleDir, "src/test/screenshots/phase_10_5_ui.sha256")
        require(manifest.isFile) {
            "Manifesto de golden Phase 10.5 não encontrado em ${manifest.absolutePath}"
        }

        manifest.readLines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                require(parts.size == 2) { "Entrada de golden inválida: $line" }
                parts[1] to parts[0]
            }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun recordingOptions(): RoborazziOptions {
        val previous = System.getProperty("roborazzi.test.record")
        System.setProperty("roborazzi.test.record", "true")
        return try {
            RoborazziOptions()
        } finally {
            if (previous == null) {
                System.clearProperty("roborazzi.test.record")
            } else {
                System.setProperty("roborazzi.test.record", previous)
            }
        }
    }

    @Test
    fun criticalProductScreens_matchPersistedCareerFixture() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val context: Context = application
        val slotId = "5"

        context.dataStore.edit { it.clear() }
        context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))

        val databaseFactory = SlotDatabaseFactory(context)
        val saveRepository = GameSaveRepository(context, databaseFactory)
        val repository = saveRepository.getRepositoryForSlot(slotId)

        val homeId = 8_800_000_001L
        val rivalId = 8_800_000_002L
        val thirdId = 8_800_000_003L
        val fourthId = 8_800_000_004L

        val teams = listOf(
            Team(
                id = homeId,
                name = "Atlético QA",
                city = "Belo Horizonte",
                state = "MG",
                country = "Brasil",
                division = 1,
                isPlayerControlled = true,
                rating = 82,
                stadiumName = "Arena QA",
                logoUrl = null,
                colorHex = "#111111"
            ),
            Team(
                id = rivalId,
                name = "Cruzeiro QA",
                city = "Belo Horizonte",
                state = "MG",
                country = "Brasil",
                division = 1,
                rating = 80,
                stadiumName = "Estádio QA Azul",
                logoUrl = null,
                colorHex = "#0033A0"
            ),
            Team(
                id = thirdId,
                name = "Palmeiras QA",
                city = "São Paulo",
                state = "SP",
                country = "Brasil",
                division = 1,
                rating = 83,
                logoUrl = null,
                colorHex = "#006437"
            ),
            Team(
                id = fourthId,
                name = "Flamengo QA",
                city = "Rio de Janeiro",
                state = "RJ",
                country = "Brasil",
                division = 1,
                rating = 84,
                logoUrl = null,
                colorHex = "#C4122C"
            )
        )
        repository.saveTeams(teams)

        val positions = listOf(
            "GOL", "LAT", "ZAG", "ZAG", "LAT", "VOL", "VOL", "MEI", "MEI", "ATA", "ATA",
            "GOL", "ZAG", "MEI", "ATA"
        )
        val players = teams.flatMapIndexed { teamIndex, team ->
            positions.mapIndexed { index, position ->
                Player(
                    id = 8_810_000_000L + teamIndex * 100L + index,
                    teamId = team.id,
                    name = "${team.name.substringBefore(' ')} QA ${index + 1}",
                    age = 20 + (index % 12),
                    nationality = "Brasil",
                    position = position,
                    force = (86 - index - teamIndex).coerceAtLeast(65),
                    energy = 88 + (index % 12),
                    moral = 76 + (index % 15),
                    salary = 40_000L + index * 2_500L,
                    contractDurationWeeks = 104,
                    isStarter = index < 11,
                    market_value = 2_000_000L + index * 250_000L,
                    min_price = 1_800_000L + index * 200_000L,
                    max_price = 2_600_000L + index * 300_000L,
                    finishing = 70 + (index % 15),
                    passing = 68 + (index % 16),
                    pace = 69 + (index % 14),
                    strength = 67 + (index % 15),
                    vision = 66 + (index % 17),
                    defense = 64 + (index % 18),
                    potential = (90 - index / 2).coerceAtLeast(78)
                )
            }
        }
        repository.savePlayers(players)
        val expectedHomeRoster = players.filter { it.teamId == homeId }
        val expectedAverageEnergy = expectedHomeRoster.sumOf { it.energy } / expectedHomeRoster.size

        val nextFixture = Fixture(
            id = 8_820_000_003L,
            season = 2026,
            week = 2,
            homeTeamId = homeId,
            awayTeamId = rivalId,
            competitionType = "SERIE_A"
        )
        repository.saveFixtures(
            listOf(
                Fixture(
                    id = 8_820_000_001L,
                    season = 2026,
                    week = 1,
                    homeTeamId = homeId,
                    awayTeamId = thirdId,
                    homeScore = 2,
                    awayScore = 1,
                    competitionType = "SERIE_A",
                    isPlayed = true
                ),
                Fixture(
                    id = 8_820_000_002L,
                    season = 2026,
                    week = 1,
                    homeTeamId = rivalId,
                    awayTeamId = fourthId,
                    homeScore = 0,
                    awayScore = 0,
                    competitionType = "SERIE_A",
                    isPlayed = true
                ),
                nextFixture,
                Fixture(
                    id = 8_820_000_004L,
                    season = 2026,
                    week = 2,
                    homeTeamId = thirdId,
                    awayTeamId = fourthId,
                    competitionType = "SERIE_A"
                )
            )
        )
        repository.saveGameSave(
            GameSave(
                coachName = "Técnico QA",
                coachReputation = 78,
                currentWeek = 2,
                currentSeason = 2026,
                playerTeamId = homeId,
                bankBalance = 42_500_000L,
                stadiumCapacity = 42_000,
                sponsorWeekly = 550_000L,
                sponsorName = "Patrocinador QA",
                sponsorWeeksRemaining = 24,
                academyLevel = 3,
                academyWeeklyInvestment = 120_000L,
                playerFormation = "4-3-3",
                playerStyle = "Equilibrado"
            )
        )

        val preferencesRepository = GamePreferencesRepository(context.dataStore, context)
        val viewModel = GameViewModel(
            application = application,
            saveRepository = saveRepository,
            preferencesRepo = preferencesRepository,
            youthAcademyUseCase = YouthAcademyUseCase(),
            tacticsUseCase = TacticsUseCase()
        )
        viewModel.getOrCreateSession(slotId)
        viewModel._currentSaveId.value = slotId

        // Prime the WhileSubscribed StateFlow before the composition exists. This guarantees
        // that the dashboard's first lifecycle-aware collection starts from the complete persisted
        // 15-player roster instead of a valid but transient 11-starter emission.
        withTimeout(8_000L) {
            viewModel.playerRoster.first { loadedRoster ->
                loadedRoster.size == expectedHomeRoster.size &&
                    loadedRoster.associate { it.id to it.energy } ==
                        expectedHomeRoster.associate { it.id to it.energy }
            }
        }

        val surface = mutableStateOf("career")
        composeTestRule.setContent {
            // Ripples/pressed indications are transient input feedback, not product layout. Under
            // Robolectric they can outlive a semantic click nondeterministically and contaminate
            // the next screen's pixels. Disable only that transient feedback inside this golden
            // composition; navigation still executes the real Tab onClick path.
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                MyApplicationTheme {
                    when (surface.value) {
                        "career" -> CareerDashboardScreen(viewModel)
                        "team_selection" -> TeamSelectionScreen(
                            viewModel = viewModel,
                            coachName = "Técnico QA",
                            onBack = {}
                        )
                        else -> LiveMatchScreen(viewModel)
                    }
                }
            }
        }

        fun settleUiForGolden() {
            composeTestRule.waitForIdle()
            composeTestRule.mainClock.advanceTimeBy(1_000L)
            composeTestRule.waitForIdle()
        }

        fun captureAndVerify(fileName: String) {
            settleUiForGolden()
            val expected = expectedHashes[fileName]
            assertNotNull("Golden SHA-256 ausente para $fileName", expected)
            val actualFile = File(moduleDir, "build/phase105-golden-actual/$fileName")
            actualFile.parentFile?.mkdirs()
            composeTestRule.onRoot().captureRoboImage(
                file = actualFile,
                roborazziOptions = recordingOptions()
            )
            require(actualFile.isFile) {
                "Captura Roborazzi não foi materializada em ${actualFile.absolutePath}"
            }
            assertEquals(
                "Regressão visual detectada em $fileName. Regrave o baseline somente após revisão explícita da mudança.",
                expected,
                sha256(actualFile)
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodesWithTag("dashboard_tab").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText("Atlético QA", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText(
                    "$expectedAverageEnergy% - Elenco Pronto",
                    substring = false
                ).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("dashboard_tab").assertIsDisplayed()
        captureAndVerify("dashboard.png")

        fun navigateTo(tag: String, fileName: String) {
            composeTestRule.onNodeWithTag(tag).performScrollTo().performClick()
            captureAndVerify(fileName)
        }

        navigateTo("squad_tab", "squad.png")
        navigateTo("tactics_tab", "tactics.png")
        navigateTo("market_tab", "transfers.png")
        navigateTo("finance_tab", "finances.png")
        navigateTo("standings_tab", "standings.png")

        composeTestRule.runOnIdle { surface.value = "team_selection" }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("coach_name_input").assertIsDisplayed()
        captureAndVerify("team_selection.png")

        viewModel.liveMatchFixture = nextFixture
        viewModel.liveMatchHomeTeam = teams[0]
        viewModel.liveMatchAwayTeam = teams[1]
        viewModel.liveMatchHomePlayers = players.filter { it.teamId == homeId && it.isStarter }.take(11)
        viewModel.liveMatchAwayPlayers = players.filter { it.teamId == rivalId && it.isStarter }.take(11)
        viewModel._matchMinute.value = 67
        viewModel._matchHomeScore.value = 2
        viewModel._matchAwayScore.value = 1
        viewModel._matchEvents.value = emptyList()
        viewModel._matchState.value = GameViewModel.MatchState.PLAYING

        composeTestRule.runOnIdle { surface.value = "match" }
        composeTestRule.waitForIdle()
        captureAndVerify("live_match.png")
    }
}
