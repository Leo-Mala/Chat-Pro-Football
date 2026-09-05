package com.example.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.TeamBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledClubCrestsAndroidDecodeInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun certifiedRuntimeContainsAndroidDecodesAndActuallyDisplaysClubCrests() {
        val names = context.assets.list("club_crests")?.toList().orEmpty().sorted()
        assertEquals(2524, names.size)
        assertEquals(617, names.count { it.startsWith("factual_") && it.endsWith(".webp") })
        assertEquals(1907, names.count { it.startsWith("club_") && it.endsWith(".webp") })
        assertTrue("Certified crest runtime must contain only WebP files", names.all { it.endsWith(".webp") })

        val decodeFailures = mutableListOf<String>()
        for (name in names) {
            context.assets.open("club_crests/$name").use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                if (bitmap == null) {
                    decodeFailures += name
                } else {
                    bitmap.recycle()
                }
            }
        }

        assertTrue(
            "Android BitmapFactory failed to decode ${decodeFailures.size} certified crests: " +
                decodeFailures.take(20).joinToString(),
            decodeFailures.isEmpty(),
        )

        // Regression for the manual-test failure on 93bc36b: keeping Image out of
        // composition until Coil reports success creates a circular dependency and
        // leaves only the deterministic abbreviation fallback visible forever.
        BundledClubCrests.resetForTests()
        composeRule.setContent {
            TeamBadge(
                teamName = "Flamengo",
                logoUrl = null,
                teamId = 4009L,
                size = 64.dp,
            )
        }
        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithText("FL").fetchSemanticsNodes().isEmpty()
        }
    }
}
