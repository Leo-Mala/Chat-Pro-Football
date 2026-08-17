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
    fun `activity onCreate completes successfully without finishing`() {
        // onCreate is the startup boundary we need to smoke-test. Advancing a Compose Activity
        // all the way to RESUMED under Robolectric starts lifecycle-aware Choreographer frames;
        // this application has long-lived Compose work that can keep the test JVM producing
        // synthetic frames until its heap is exhausted. Keeping the controller at CREATED still
        // executes MainActivity.onCreate/setContent and catches startup crashes without turning
        // this smoke test into an unbounded UI frame-loop test.
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity = controller.get()
            assertNotNull(activity)
            assertFalse(activity.isFinishing)
        } finally {
            controller.destroy()
        }
    }
}
