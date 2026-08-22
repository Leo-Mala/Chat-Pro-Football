package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.example.ui.screens.MainMenuContent
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
class MainMenuScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mainMenu_realProductScreen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MainMenuContent(
                    onNewGame = {},
                    onOpenSaves = {},
                    onOpenEditor = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("new_game_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_saves_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_editor_button").assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/main_menu.png")
    }
}
