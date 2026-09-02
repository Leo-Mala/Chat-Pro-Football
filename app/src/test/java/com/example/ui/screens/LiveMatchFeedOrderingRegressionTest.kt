package com.example.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMatchFeedOrderingRegressionTest {
    @Test
    fun `live feed stays chronological and follows the latest event`() {
        val source = readProjectSource("src/main/java/com/example/ui/screens/MatchSimulationScreen.kt")
        val listStart = source.indexOf("LazyColumn(\n                    state = liveEventListState")
        assertTrue("live event LazyColumn must use the dedicated list state", listStart >= 0)
        val listBody = source.substring(listStart, minOf(source.length, listStart + 1200))

        assertTrue(source.contains("val liveEventListState = rememberLazyListState()"))
        assertTrue(source.contains("LaunchedEffect(events.size, selectedLiveTab)"))
        assertTrue(source.contains("liveEventListState.animateScrollToItem(events.lastIndex)"))
        assertTrue(listBody.contains("state = liveEventListState"))
        assertTrue(listBody.contains("items(events,"))
        check(!listBody.contains("events.reversed()"))
        check(!listBody.contains("reverseLayout = true"))
    }

    private fun readProjectSource(relativeToApp: String): String {
        val candidates = listOf(File(relativeToApp), File("app/$relativeToApp"), File("../app/$relativeToApp"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relativeToApp; cwd=${File(".").absolutePath}")
    }
}
