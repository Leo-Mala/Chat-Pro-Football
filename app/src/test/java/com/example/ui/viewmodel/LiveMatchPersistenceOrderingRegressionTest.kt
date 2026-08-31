package com.example.ui.viewmodel

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMatchPersistenceOrderingRegressionTest {
    @Test
    fun `natural full time is persisted before FINISHED is published to UI`() {
        val source = readProjectSource("src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt")
        val start = source.indexOf("suspend fun GameViewModel.runMatchSimulationLoop")
        val end = source.indexOf("fun GameViewModel.substitutePlayer", start)
        val body = source.substring(start, end)
        val fullTime = body.indexOf("if (m >= 90")
        val transaction = body.indexOf("repo.withTransaction", fullTime)
        val finishPublication = body.indexOf("_matchState.value = GameViewModel.MatchState.FINISHED", fullTime)
        assertTrue(fullTime >= 0)
        assertTrue(transaction > fullTime)
        assertTrue("FINISHED must not expose Back until isPlayed and stats are durable", finishPublication > transaction)
        assertTrue(body.substring(transaction, finishPublication).contains("repo.updateFixture(updatedFixture)"))
        assertTrue(body.substring(transaction, finishPublication).contains("processMatchEventsAndStats"))
    }

    @Test
    fun `skip of prepared match never starts a new engine or RNG stream`() {
        val source = readProjectSource("src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt")
        val start = source.indexOf("fun GameViewModel.skipLiveMatch(")
        val end = source.indexOf("suspend fun GameViewModel.processMatchEventsAndStats", start)
        val body = source.substring(start, end)
        assertTrue(body.contains("finishPreparedLiveFixture(targetFixture)"))
        check(!body.contains("simulateMatchDetailed"))
        check(!body.contains("Random.nextLong"))
    }

    private fun readProjectSource(relativeToApp: String): String {
        val candidates = listOf(File(relativeToApp), File("app/$relativeToApp"), File("../app/$relativeToApp"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relativeToApp; cwd=${File(".").absolutePath}")
    }
}
