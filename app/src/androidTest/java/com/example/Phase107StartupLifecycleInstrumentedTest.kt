package com.example

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase107StartupLifecycleInstrumentedTest {

    @Test
    fun productionApplicationHiltAndMainActivityStartOnRealAndroid() {
        val context = Phase107TestSupport.targetContext()
        assertEquals(Phase107TestSupport.TARGET_PACKAGE, context.packageName)
        assertTrue(context.applicationContext is MainApplication)

        val entryPoint = Phase107TestSupport.entryPoint()
        assertNotNull(entryPoint.gameSaveRepository())
        assertNotNull(entryPoint.gamePreferencesRepository())

        val slotId = "5"
        try {
            Phase107TestSupport.resetSlot(slotId)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertFalse(activity.isFinishing)
                    assertFalse(activity.isDestroyed)
                }
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
            }

            entryPoint.gameSaveRepository().getDatabaseForSlot(slotId)
            val databaseFile = entryPoint.gameSaveRepository().databaseFileForSlot(slotId)
            assertTrue(databaseFile.isFile)
            assertTrue(databaseFile.length() > 0L)
        } finally {
            Phase107TestSupport.resetSlot(slotId)
        }
    }

    @Test
    fun activityRecreationRotationAndBackgroundForegroundDoNotLoseRuntime() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)

            scenario.recreate()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity -> assertFalse(activity.isFinishing) }

            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(Configuration.ORIENTATION_LANDSCAPE, activity.resources.configuration.orientation)
            }

            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(Configuration.ORIENTATION_PORTRAIT, activity.resources.configuration.orientation)
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            assertEquals(Lifecycle.State.CREATED, scenario.state)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity -> assertFalse(activity.isFinishing) }
        }
    }
}
