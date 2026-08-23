package com.example

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ui.viewmodel.GameViewModel
import com.example.ui.viewmodel.selectSaveSlotSafely
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

        val dependencies = Phase107TestSupport.entryPoint()
        assertNotNull(dependencies.gameSaveRepository())
        assertNotNull(dependencies.gamePreferencesRepository())

        val slotId = "5"
        try {
            Phase107TestSupport.resetSlot(slotId)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertFalse(activity.isFinishing)
                    assertFalse(activity.isDestroyed)
                    // Obtaining this @HiltViewModel through the real Activity factory exercises the
                    // production Hilt component; there is no test Application or replacement module.
                    assertNotNull(ViewModelProvider(activity)[GameViewModel::class.java])
                }
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
            }

            dependencies.gameSaveRepository().getDatabaseForSlot(slotId)
            val databaseFile = dependencies.gameSaveRepository().databaseFileForSlot(slotId)
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

    @Test
    fun rapidSlotSwitchKeepsTheLastRealCareerSessionAndDoesNotLeakThePreviousSlot() {
        val slotA = "2"
        val slotB = "3"
        try {
            Phase107TestSupport.seedCareer(slotA, "Rapid Coach A", "Rapid Club A", 10_772L)
            Phase107TestSupport.seedCareer(slotB, "Rapid Coach B", "Rapid Club B", 10_773L)
            Phase107TestSupport.closeDatabases()

            lateinit var viewModel: GameViewModel
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    viewModel = ViewModelProvider(activity)[GameViewModel::class.java]
                }

                viewModel.selectSaveSlotSafely(slotA)
                waitUntil("slot A selection") {
                    viewModel.currentSaveId.value == slotA &&
                        viewModel.activeSaveSession.value?.slotId == slotA
                }

                // Two user selections in close succession exercise the serialized IO entrypoint.
                viewModel.selectSaveSlotSafely(slotB)
                SystemClock.sleep(50)
                viewModel.selectSaveSlotSafely(slotA)
                waitUntil("rapid B -> A selection") {
                    viewModel.currentSaveId.value == slotA &&
                        viewModel.activeSaveSession.value?.slotId == slotA
                }

                val current = viewModel.getActiveRepository()?.let { repository ->
                    kotlinx.coroutines.runBlocking { repository.getGameSave() }
                }
                assertEquals("Rapid Coach A", current?.coachName)
                assertEquals(10_772L, current?.playerTeamId)

                viewModel.selectSaveSlotSafely(slotB)
                waitUntil("final slot B selection") {
                    viewModel.currentSaveId.value == slotB &&
                        viewModel.activeSaveSession.value?.slotId == slotB
                }
                val final = viewModel.getActiveRepository()?.let { repository ->
                    kotlinx.coroutines.runBlocking { repository.getGameSave() }
                }
                assertEquals("Rapid Coach B", final?.coachName)
                assertEquals(10_773L, final?.playerTeamId)
            }
        } finally {
            Phase107TestSupport.resetSlot(slotA)
            Phase107TestSupport.resetSlot(slotB)
        }
    }

    private fun waitUntil(label: String, timeoutMillis: Long = 30_000, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for $label")
    }
}
