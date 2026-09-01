package com.example.usecase

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LeagueTableImmediateRefreshTest {
    @Test fun `post match FINISHED is published only after durable week close and standings is reactive`() {
        val match = source("src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt")
        val start = match.indexOf("suspend fun GameViewModel.runMatchSimulationLoop")
        val end = match.indexOf("fun GameViewModel.substitutePlayer", start)
        val body = match.substring(start, end)
        assertTrue(body.lastIndexOf("_matchState.value = GameViewModel.MatchState.FINISHED") > body.lastIndexOf("processWeekEndEconomicAndEvolution()"))
        val standings = source("src/main/java/com/example/ui/screens/StandingsScreen.kt")
        assertTrue(standings.contains("viewModel.allFixtures.collectAsStateWithLifecycle()"))
        assertTrue(standings.contains("viewModel.allTeams.collectAsStateWithLifecycle()"))
    }
    private fun source(path: String): String = listOf(File(path), File("app/$path"), File("../app/$path"))
        .firstOrNull { it.isFile }?.readText() ?: error("Missing $path")
}
