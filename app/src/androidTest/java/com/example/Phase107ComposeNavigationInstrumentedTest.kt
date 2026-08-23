package com.example

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.repository.SlotDatabaseState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Phase107ComposeNavigationInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainMenuSavesAndCriticalActionsExposeRealComposeSemantics() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodes(hasTestTag("new_game_button")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("new_game_button")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag("open_saves_button")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodes(hasTestTag("save_slot_1")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("save_slot_1").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("back_to_menu_button")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithTag("new_game_button").assertIsDisplayed()
    }

    @Test
    fun createsAndReopensARealCareerThroughTheInstalledUi() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodes(hasTestTag("new_game_button")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("new_game_button").performClick()

        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodes(hasTestTag("start_career_button")).fetchSemanticsNodes().isNotEmpty()
        }

        val visibleTeamCard = hasClickAction() and hasAnyDescendant(hasText("FORÇA"))
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodes(visibleTeamCard, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(visibleTeamCard, useUnmergedTree = true)[0].performClick()
        composeRule.onNodeWithTag("start_career_button").assertIsEnabled().performClick()

        composeRule.waitUntil(timeoutMillis = 180_000) {
            composeRule.onAllNodes(hasTestTag("dashboard_tab")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard_tab").assertIsDisplayed().assertHasClickAction()

        // Cada clique aguarda a árvore Compose ficar idle para garantir que a tela de destino
        // realmente foi composta no processo Android instalado, em vez de apenas enfileirar cliques.
        navigateTab("squad_tab")
        navigateTab("tactics_tab")
        navigateTab("market_tab")
        navigateTab("finance_tab")
        navigateTab("standings_tab")
        navigateTab("dashboard_tab")

        val dependencies = Phase107TestSupport.entryPoint()
        val occupiedSlots = runBlocking {
            dependencies.gamePreferencesRepository().loadSaveSlots().filter { it.exists && !it.recoveryRequired }
        }
        assertEquals("A fresh installed app must create exactly one career in this test", 1, occupiedSlots.size)
        val slotId = occupiedSlots.single().id
        val inspection = runBlocking { dependencies.gameSaveRepository().inspectSlot(slotId) }
        assertEquals(SlotDatabaseState.VALID_CAREER, inspection.state)
        assertTrue(dependencies.gameSaveRepository().databaseFileForSlot(slotId).isFile)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodes(hasTestTag("dashboard_tab")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard_tab").assertIsDisplayed()
    }

    private fun navigateTab(tag: String) {
        composeRule.onNodeWithTag(tag).assertHasClickAction().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }
}
