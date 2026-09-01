package com.example.ui.viewmodel

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PostGameReturnToCentralRegressionTest {
    @Test
    fun `finished match closes week before FINISHED and exit is immediate`() {
        val match = readSource("src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt")
        val loopStart = match.indexOf("suspend fun GameViewModel.runMatchSimulationLoop")
        val loopEnd = match.indexOf("fun GameViewModel.substitutePlayer", loopStart)
        val loop = match.substring(loopStart, loopEnd)
        val cpu = loop.lastIndexOf("simulateCpuMatchesForCurrentWeek()")
        val close = loop.lastIndexOf("processWeekEndEconomicAndEvolution()")
        val finished = loop.lastIndexOf("_matchState.value = GameViewModel.MatchState.FINISHED")
        assertTrue(cpu >= 0)
        assertTrue(close > cpu)
        assertTrue(finished > close)

        val vm = readSource("src/main/java/com/example/ui/viewmodel/GameViewModel.kt")
        val exitStart = vm.indexOf("fun exitLiveMatch()")
        val exitEnd = vm.indexOf("// Watchlist StateFlow", exitStart)
        val exit = vm.substring(exitStart, exitEnd)
        assertTrue(exit.contains("_matchState.value = MatchState.IDLE"))
        assertTrue(!exit.contains("viewModelScope.launch"))
        assertTrue(!exit.contains("simulateCpuMatchesForCurrentWeek"))
        assertTrue(!exit.contains("processWeekEndEconomicAndEvolution"))

        val preparedStart = match.indexOf("private suspend fun GameViewModel.finishPreparedLiveFixture")
        val preparedEnd = match.indexOf("fun GameViewModel.skipLiveMatch", preparedStart)
        val prepared = match.substring(preparedStart, preparedEnd)
        assertTrue(!prepared.contains("_matchState.value = GameViewModel.MatchState.FINISHED"))

        val skipStart = match.indexOf("fun GameViewModel.skipLiveMatch")
        val statsStart = match.indexOf("suspend fun GameViewModel.processMatchEventsAndStats", skipStart)
        val skip = match.substring(skipStart, statsStart)
        val skipCpu = skip.lastIndexOf("simulateCpuMatchesForCurrentWeek()")
        val skipClose = skip.lastIndexOf("processWeekEndEconomicAndEvolution()")
        val skipFinished = skip.lastIndexOf("_matchState.value = GameViewModel.MatchState.FINISHED")
        assertTrue(skipCpu >= 0)
        assertTrue(skipClose > skipCpu)
        assertTrue(skipFinished > skipClose)
    }

    private fun readSource(relativeToApp: String): String {
        val candidates = listOf(File(relativeToApp), File("app/$relativeToApp"), File("../app/$relativeToApp"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relativeToApp; cwd=${File(".").absolutePath}")
    }
}
