package com.example

import android.os.Looper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupSmokeTest {

    @Test
    fun `activity starts successfully without finishing`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertNotNull(activity)
            assertFalse(activity.isFinishing)
        } finally {
            // Compose owns a lifecycle-aware Recomposer. Leaving the Activity in RESUMED
            // state after this smoke assertion keeps frame callbacks alive in Robolectric,
            // consumes the test JVM heap and can starve later coroutine-based tests.
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
