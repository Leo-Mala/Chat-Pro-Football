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
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity = controller.get()
            assertNotNull(activity)
            assertFalse(activity.isFinishing)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `activity can be created again after previous instance is destroyed`() {
        // Mantém ambos os controllers em CREATED: isso executa onCreate/setContent sem ligar o
        // frame-loop longo do Compose em Robolectric, e cobre a fronteira de recriação usada após
        // configuration/process restart sem depender de estado estático da Activity anterior.
        val firstController = Robolectric.buildActivity(MainActivity::class.java).create()
        firstController.destroy()

        val recreatedController = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val recreatedActivity = recreatedController.get()
            assertNotNull(recreatedActivity)
            assertFalse(recreatedActivity.isFinishing)
        } finally {
            recreatedController.destroy()
        }
    }
}
