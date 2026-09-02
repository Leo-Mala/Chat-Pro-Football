from pathlib import Path

screen = Path("app/src/main/java/com/example/ui/screens/MatchSimulationScreen.kt")
text = screen.read_text(encoding="utf-8")

old_imports = """import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.LazyRow\nimport androidx.compose.foundation.lazy.items\n"""
new_imports = """import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.LazyRow\nimport androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.rememberLazyListState\n"""
if text.count(old_imports) != 1:
    raise SystemExit(f"lazy import anchor count={text.count(old_imports)}, expected=1")
text = text.replace(old_imports, new_imports, 1)

old_state = """        var selectedLiveTab by remember { mutableStateOf(\"LANCES\") }\n        LaunchedEffect(matchState) {\n"""
new_state = """        var selectedLiveTab by remember { mutableStateOf(\"LANCES\") }\n        val liveEventListState = rememberLazyListState()\n        LaunchedEffect(events.size, selectedLiveTab) {\n            if (selectedLiveTab == \"LANCES\" && events.isNotEmpty()) {\n                liveEventListState.animateScrollToItem(events.lastIndex)\n            }\n        }\n        LaunchedEffect(matchState) {\n"""
if text.count(old_state) != 1:
    raise SystemExit(f"live tab state anchor count={text.count(old_state)}, expected=1")
text = text.replace(old_state, new_state, 1)

old_list = """                LazyColumn(\n                    modifier = Modifier\n                        .fillMaxSize()\n                        .padding(12.dp),\n                    verticalArrangement = Arrangement.spacedBy(8.dp),\n                    reverseLayout = true // keeps latest events at top\n                ) {\n                    items(events.reversed(), key = { \"${it.minute}_${it.description.hashCode()}\" }) { event ->\n"""
new_list = """                LazyColumn(\n                    state = liveEventListState,\n                    modifier = Modifier\n                        .fillMaxSize()\n                        .padding(12.dp),\n                    verticalArrangement = Arrangement.spacedBy(8.dp)\n                ) {\n                    items(events, key = { \"${it.minute}_${it.description.hashCode()}\" }) { event ->\n"""
if text.count(old_list) != 1:
    raise SystemExit(f"live event list anchor count={text.count(old_list)}, expected=1")
text = text.replace(old_list, new_list, 1)
screen.write_text(text, encoding="utf-8")

test = Path("app/src/test/java/com/example/ui/screens/LiveMatchFeedOrderingRegressionTest.kt")
test.write_text(
    '''package com.example.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMatchFeedOrderingRegressionTest {
    @Test
    fun `live feed stays chronological and follows the latest event`() {
        val source = readProjectSource("src/main/java/com/example/ui/screens/MatchSimulationScreen.kt")
        val listStart = source.indexOf("LazyColumn(\\n                    state = liveEventListState")
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
''',
    encoding="utf-8",
)
