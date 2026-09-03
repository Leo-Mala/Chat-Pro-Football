package com.example

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import com.example.data.local.SlotDatabaseFactory
import com.example.data.model.SaveSlotMetadata
import com.example.data.repository.GameSaveRepository
import com.example.ui.screens.MainMenuContent
import com.example.ui.screens.SavesContent
import com.example.ui.screens.TeamBadge
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Gates de acessibilidade e resiliência da Fase 10.5.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [34])
class Phase105AccessibilityAndResilienceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mainMenu_largeFontOnShortScreen_keepsEveryPrimaryActionReachable() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 1.6f)) {
                MyApplicationTheme {
                    MainMenuContent(
                        onNewGame = {},
                        onOpenSaves = {},
                        onOpenEditor = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("new_game_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_saves_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("open_editor_button").assertCountEquals(0)
    }

    @Test
    fun saves_largeFontOnShortScreen_keepsBackNavigationReachable() {
        val slots = (1..3).map { index ->
            SaveSlotMetadata(
                id = index.toString(),
                exists = index == 1,
                coachName = if (index == 1) "Técnico de Acessibilidade" else "",
                teamName = if (index == 1) "Atlético Teste" else "",
                season = 2026,
                week = 12,
                balance = 42_500_000L,
                updatedAt = 0L
            )
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 1.6f)) {
                MyApplicationTheme {
                    SavesContent(
                        saveSlots = slots,
                        onSelectSlot = {},
                        onDeleteSlot = {},
                        onBack = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("save_slot_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("back_to_menu_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun teamBadge_remoteImageFailure_usesLocalFallbackWithoutBlockingUi() {
        composeTestRule.setContent {
            MyApplicationTheme {
                TeamBadge(
                    teamName = "Atlético Teste",
                    logoUrl = "malformed://offline-logo",
                    size = 64.dp
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("AT").assertIsDisplayed()
    }

    @Test
    fun persistedSlot_reopensAfterDatabaseFactoryRestart() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val slotId = "4"
            context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))

            val firstFactory = SlotDatabaseFactory(context)
            val firstSaveRepository = GameSaveRepository(context, firstFactory)
            val firstRepository = firstSaveRepository.getRepositoryForSlot(slotId)
            val team = Team(
                id = 44_001L,
                name = "Clube Persistente",
                city = "Belo Horizonte",
                state = "MG",
                country = "Brasil",
                division = 1,
                isPlayerControlled = true,
                rating = 77
            )
            val player = Player(
                id = 44_101L,
                teamId = team.id,
                name = "Jogador Persistente",
                age = 24,
                position = "MEI",
                force = 76,
                isStarter = true
            )
            firstRepository.saveTeams(listOf(team))
            firstRepository.savePlayers(listOf(player))
            firstRepository.saveGameSave(
                GameSave(
                    coachName = "Reopen QA",
                    currentSeason = 2029,
                    currentWeek = 17,
                    playerTeamId = team.id,
                    bankBalance = 19_500_000L
                )
            )
            firstSaveRepository.checkpointSlot(slotId)
            firstSaveRepository.closeAllDatabases()

            val reopenedFactory = SlotDatabaseFactory(context)
            val reopenedSaveRepository = GameSaveRepository(context, reopenedFactory)
            val reopenedRepository = reopenedSaveRepository.getRepositoryForSlot(slotId)

            val reopenedSave = reopenedRepository.getGameSave()
            assertNotNull(reopenedSave)
            assertEquals("Reopen QA", reopenedSave?.coachName)
            assertEquals(2029, reopenedSave?.currentSeason)
            assertEquals(17, reopenedSave?.currentWeek)
            assertEquals(19_500_000L, reopenedSave?.bankBalance)
            assertEquals("Clube Persistente", reopenedRepository.getTeam(team.id)?.name)
            assertEquals("Jogador Persistente", reopenedRepository.getPlayer(player.id)?.name)

            reopenedSaveRepository.closeAllDatabases()
            context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))
        }
    }
}
