package com.example.ui.screens

import com.example.data.GameCalendar
import com.example.ui.components.dashboard.simulationWeekProgressText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class SeasonWeekBoundsRegressionTest {
    @Test fun `simulation progress uses canonical season week count`() {
        assertEquals(48, GameCalendar.WEEKS_PER_SEASON)
        assertEquals("Semana 48 de 48", simulationWeekProgressText(GameCalendar.WEEKS_PER_SEASON))
    }

    @Test fun `dashboard contains no obsolete 45 week denominator`() {
        val source = File("src/main/java/com/example/ui/components/dashboard/DashboardTabContent.kt").readText()
        assertFalse(source.contains("simWeek de 45"))
    }
}
