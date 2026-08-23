package com.example

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.repository.SlotDatabaseState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase107ProcessRestartSeedInstrumentedTest {
    @Test
    fun seedCanonicalCareerWithoutMetadataForExternalProcessRestart() {
        Phase107TestSupport.seedCareer(
            slotId = "5",
            coachName = "Phase107 Process Coach",
            teamName = "Phase107 Process Club",
            teamId = 10_799L,
            writeMetadata = false,
            week = 23,
            balance = 23_107_000L
        )
        Phase107TestSupport.closeDatabases()

        val dependencies = Phase107TestSupport.entryPoint()
        val inspection = runBlocking { dependencies.gameSaveRepository().inspectSlot("5") }
        assertEquals(SlotDatabaseState.VALID_CAREER, inspection.state)
        assertTrue(dependencies.gameSaveRepository().databaseFileForSlot("5").isFile)
    }
}

class Phase107ProcessRestartUiInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun recoveredCareerSurvivesExternalForceStopAndOpensThroughUi() {
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodes(hasTestTag("open_saves_button")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("open_saves_button").performClick()

        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodes(hasTestTag("save_slot_5")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("save_slot_5").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Phase107 Process Coach").assertIsDisplayed()
        composeRule.onNodeWithText("Phase107 Process Club • Temp. 2026 (Sem. 23)").assertIsDisplayed()

        val dependencies = Phase107TestSupport.entryPoint()
        val slot = runBlocking {
            dependencies.gamePreferencesRepository().loadSaveSlots().single { it.id == "5" }
        }
        assertTrue(slot.exists)
        assertFalse(slot.recoveryRequired)
        assertEquals("Phase107 Process Coach", slot.coachName)

        composeRule.onNodeWithTag("save_slot_5").performClick()
        composeRule.waitUntil(timeoutMillis = 90_000) {
            composeRule.onAllNodes(hasTestTag("dashboard_tab")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard_tab").assertIsDisplayed()

        val inspection = runBlocking { dependencies.gameSaveRepository().inspectSlot("5") }
        assertEquals(SlotDatabaseState.VALID_CAREER, inspection.state)
        assertEquals("Phase107 Process Coach", inspection.save?.coachName)
        assertEquals(10_799L, inspection.save?.playerTeamId)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodes(hasTestTag("dashboard_tab")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard_tab").assertIsDisplayed()
    }
}
