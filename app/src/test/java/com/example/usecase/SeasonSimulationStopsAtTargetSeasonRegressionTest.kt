package com.example.usecase

import com.example.ui.viewmodel.shouldStopSeasonSimulation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SeasonSimulationStopsAtTargetSeasonRegressionTest {
    @Test fun `simulation stops immediately after rollover`() {
        assertFalse(shouldStopSeasonSimulation(2026, 2026))
        assertTrue(shouldStopSeasonSimulation(2026, 2027))
    }

    @Test fun `production loop captures target season and checks both sides of close`() {
        val source = File("src/main/java/com/example/ui/viewmodel/GameViewModel.kt").readText()
        val start = source.substringAfter("fun startSeasonSimulation()")
        assertTrue(start.contains("val targetSeason = initialSave.currentSeason"))
        assertTrue(start.contains("shouldStopSeasonSimulation(targetSeason, save.currentSeason)"))
        assertTrue(start.contains("shouldStopSeasonSimulation(targetSeason, updatedSave.currentSeason)"))
    }
}
