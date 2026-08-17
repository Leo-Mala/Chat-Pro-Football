package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
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
            // Compose owns a lifecycle-aware Recomposer. Destroy the Activity explicitly so
            // its frame callbacks are cancelled, but do not drain the main looper afterwards:
            // forcing Robolectric to process queued Choreographer frames can keep scheduling
            // additional Compose frames and exhaust the shared unit-test JVM heap.
            controller.pause().stop().destroy()
        }
    }
}
