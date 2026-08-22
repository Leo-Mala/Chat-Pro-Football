package com.example

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden fixture de produto da Fase 10.5.
 *
 * Usa os composables reais conectados a um GameViewModel e a um banco Room de slot real em
 * Robolectric. A sessão é ativada sem disparar bootstrap/reparo assíncrono antes da instalação
 * da fixture determinística; lifecycle/bootstrap possuem regressões próprias.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class Phase105CriticalUiScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun criticalProductScreens_renderAndNavigateFromPersistedCareer() = runBlocking {
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
        val preferencesRepository = GamePreferencesRepository(context.dataStore, context)
        val viewModel = GameViewModel(
            application = application,
            saveRepository = saveRepository,
            preferencesRepo = preferencesRepository,
            youthAcademyUseCase = YouthAcademyUseCase(),
            tacticsUseCase = TacticsUseCase()
        )

        val session = viewModel.getOrCreateSession(slotId)
        viewModel._currentSaveId.value = slotId
        val repository = session.repository

        val teams = listOf(
            Team(
                id = 10_001L,
                name = "Atlético Teste",
                city = "Belo Horizonte",
                state = "MG",
                country = "Brasil",
                division = 1,
                isPlayerControlled = true,
                rating = 82,
                stadiumName = "Arena Teste",
                logoUrl = null,
                colorHex = "#111111"
            ),
            Team(
                id = 10_002L,
                name = "Cruzeiro Teste",
                city = "Belo Horizonte",
                state = "MG",
                country = "Brasil",
                division = 1,
                rating = 80,
                stadiumName = "Estádio Azul",
                logoUrl = null,
                colorHex = "#0033A0"
            ),
            Team(
                id = 10_003L,
                name = "Palmeiras Teste",
                city = "São Paulo",
                state = "SP",
                country = "Brasil",
                division = 1,
                rating = 83,
                logoUrl = null,
                colorHex = "#006437"
            ),
            Team(
                id = 10_004L,
                name = "Flamengo Teste",
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
                    id = 2_000_000L + teamIndex * 100L + index,
                    teamId = team.id,
                    name = "${team.name.substringBefore(' ')} Jogador ${index + 1}",
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

        repository.saveFixtures(
            listOf(
                Fixture(
                    id = 30_001L,
                    season = 2026,
                    week = 1,
                    homeTeamId = 10_001L,
                    awayTeamId = 10_003L,
                    homeScore = 2,
                    awayScore = 1,
                    competitionType = "SERIE_A",
                    isPlayed = true
                ),
                Fixture(
                    id = 30_002L,
                    season = 2026,
                    week = 1,
                    homeTeamId = 10_002L,
                    awayTeamId = 10_004L,
                    homeScore = 0,
                    awayScore = 0,
                    competitionType = "SERIE_A",
                    isPlayed = true
                ),
                Fixture(
                    id = 30_003L,
                    season = 2026,
                    week = 2,
                    homeTeamId = 10_001L,
                    awayTeamId = 10_002L,
                    competitionType = "SERIE_A"
                ),
                Fixture(
                    id = 30_004L,
                    season = 2026,
                    week = 2,
                    homeTeamId = 10_003L,
                    awayTeamId = 10_004L,
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
                playerTeamId = 10_001L,
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

        val surface = mutableStateOf("career")
        composeTestRule.setContent {
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

        fun capture(fileName: String) {
            composeTestRule.waitForIdle()
            composeTestRule.onRoot().captureRoboImage(
                filePath = "src/test/screenshots/$fileName"
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodesWithTag("dashboard_tab").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("dashboard_tab").assertIsDisplayed()
        capture("dashboard.png")

        fun navigateTo(tag: String, fileName: String) {
            composeTestRule.onNodeWithTag(tag).performScrollTo().performClick()
            composeTestRule.waitForIdle()
            capture(fileName)
        }

        navigateTo("squad_tab", "squad.png")
        navigateTo("tactics_tab", "tactics.png")
        navigateTo("market_tab", "transfers.png")
        navigateTo("finance_tab", "finances.png")
        navigateTo("standings_tab", "standings.png")

        composeTestRule.runOnIdle { surface.value = "team_selection" }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("coach_name_input").assertIsDisplayed()
        capture("team_selection.png")

        viewModel.liveMatchFixture = repository.getFixturesForWeek(2026, 2)
            .first { it.homeTeamId == 10_001L }
        viewModel.liveMatchHomeTeam = teams[0]
        viewModel.liveMatchAwayTeam = teams[1]
        viewModel.liveMatchHomePlayers = players.filter { it.teamId == 10_001L && it.isStarter }.take(11)
        viewModel.liveMatchAwayPlayers = players.filter { it.teamId == 10_002L && it.isStarter }.take(11)
        viewModel._matchMinute.value = 67
        viewModel._matchHomeScore.value = 2
        viewModel._matchAwayScore.value = 1
        viewModel._matchEvents.value = emptyList()
        viewModel._matchState.value = GameViewModel.MatchState.PLAYING

        composeTestRule.runOnIdle { surface.value = "match" }
        composeTestRule.waitForIdle()
        capture("live_match.png")
    }
}