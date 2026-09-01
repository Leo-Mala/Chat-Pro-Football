package com.example.usecase

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonSimulationCompletionRegressionTest {
    @Test
    fun `season simulation weekly close reloads durable save before next iteration`() {
        val source = readSource("src/main/java/com/example/ui/viewmodel/GameViewModel.kt")
        val start = source.indexOf("fun startSeasonSimulation()")
        val end = source.indexOf("fun stopSeasonSimulation()", start)
        val body = source.substring(start, end)
        val cpu = body.indexOf("simulateCpuMatchesForCurrentWeek()")
        val close = body.indexOf("processWeekEndEconomicAndEvolution()", cpu)
        val reload = body.indexOf("val updatedSave = repo.getGameSave()", close)
        assertTrue(cpu >= 0)
        assertTrue(close > cpu)
        assertTrue(reload > close)
    }

    private fun readSource(relativeToApp: String): String {
        val candidates = listOf(File(relativeToApp), File("app/$relativeToApp"), File("../app/$relativeToApp"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relativeToApp")
    }
}
