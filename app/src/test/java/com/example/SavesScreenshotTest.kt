package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.example.data.model.SaveSlotMetadata
import com.example.ui.screens.SavesContent
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class SavesScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun saves_emptySlots_realProductState() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SavesContent(
                    saveSlots = (1..3).map { SaveSlotMetadata(id = it.toString()) },
                    onSelectSlot = {},
                    onDeleteSlot = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("save_slot_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_slot_2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("back_to_menu_button").assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/saves_empty.png")
    }

    @Test
    fun saves_existingCareer_realProductState() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SavesContent(
                    saveSlots = listOf(
                        SaveSlotMetadata(
                            id = "1",
                            exists = true,
                            coachName = "Técnico",
                            teamName = "Clube Teste",
                            season = 2028,
                            week = 14,
                            balance = 25_000_000L,
                            updatedAt = 1L
                        ),
                        SaveSlotMetadata(id = "2"),
                        SaveSlotMetadata(id = "3")
                    ),
                    onSelectSlot = {},
                    onDeleteSlot = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Técnico").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clube Teste • Temp. 2028 (Sem. 14)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("delete_slot_1").assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/saves_existing.png")
    }
}
