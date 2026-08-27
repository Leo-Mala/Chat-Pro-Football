package com.example.ui.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMatchSkipRegressionTest {

    @Test
    fun `skip of active live match finishes prepared event stream instead of resimulating`() {
        val source = readProjectSource("src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt")
        val skipStart = source.indexOf("fun GameViewModel.skipLiveMatch(")
        val skipEnd = source.indexOf("suspend fun GameViewModel.processMatchEventsAndStats", skipStart)
        assertTrue(skipStart >= 0)
        assertTrue(skipEnd > skipStart)
        val skipBody = source.substring(skipStart, skipEnd)

        assertTrue(skipBody.contains("liveMatchFixture?.id == targetFixture.id"))
        assertTrue(skipBody.contains("finishPreparedLiveFixture(targetFixture)"))
        assertTrue(skipBody.contains("else {\n                simulateSingleUserFixtureSafely"))
        assertFalse(
            "An active prepared live match must never be replaced by a fresh RNG simulation",
            skipBody.contains("if (isPreparedLiveFixture) {\n                simulateSingleUserFixtureSafely")
        )
    }

    @Test
    fun `prepared live final score is derived from the same goal events published to UI`() {
        val source = readProjectSource("src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt")
        val helperStart = source.indexOf("private suspend fun GameViewModel.finishPreparedLiveFixture")
        val helperEnd = source.indexOf("fun GameViewModel.skipLiveMatch", helperStart)
        assertTrue(helperStart >= 0)
        assertTrue(helperEnd > helperStart)
        val helper = source.substring(helperStart, helperEnd)

        assertTrue(helper.contains("val preparedEvents = currentMatchEvents.toList().sortedBy"))
        assertTrue(helper.contains("preparedEvents.count { it.type == \"GOAL\" && it.isHomeEvent }"))
        assertTrue(helper.contains("preparedEvents.count { it.type == \"GOAL\" && !it.isHomeEvent }"))
        assertTrue(helper.contains("_matchEvents.value = preparedEvents"))
        assertTrue(helper.contains("processMatchEventsAndStats(finishedFixture, preparedEvents)"))
    }

    private fun readProjectSource(relativeToApp: String): String {
        val candidates = listOf(
            File(relativeToApp),
            File("app/$relativeToApp"),
            File("../app/$relativeToApp")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Source file not found: $relativeToApp; cwd=${File(".").absolutePath}")
        return file.readText()
    }
}
