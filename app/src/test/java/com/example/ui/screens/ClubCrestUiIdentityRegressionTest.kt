package com.example.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ClubCrestUiIdentityRegressionTest {
    @Test
    fun `every production TeamBadge call carries stable team id`() {
        val files = listOf(
            "src/main/java/com/example/ui/screens/TeamSelectionScreen.kt",
            "src/main/java/com/example/ui/components/dashboard/DashboardTabContent.kt",
            "src/main/java/com/example/ui/screens/EditorScreen.kt",
            "src/main/java/com/example/ui/components/editor/EditorCards.kt",
            "src/main/java/com/example/ui/screens/MatchSimulationScreen.kt",
            "src/main/java/com/example/ui/screens/StandingsScreen.kt",
            "src/main/java/com/example/ui/components/standings/TopScorersView.kt"
        )
        val calls = files.flatMap { file -> extractCalls(readProjectSource(file), file) }
        assertTrue("Expected multiple real UI badge routes", calls.size >= 15)
        calls.forEach { (file, call) ->
            assertTrue("$file must pass Team.id into TeamBadge: $call", call.contains("teamId"))
        }
    }

    @Test
    fun `mandatory editor match standings and dashboard routes are id based`() {
        val editor = readProjectSource("src/main/java/com/example/ui/components/editor/EditorCards.kt")
        val match = readProjectSource("src/main/java/com/example/ui/screens/MatchSimulationScreen.kt")
        val standings = readProjectSource("src/main/java/com/example/ui/screens/StandingsScreen.kt")
        val dashboard = readProjectSource("src/main/java/com/example/ui/components/dashboard/DashboardTabContent.kt")
        assertTrue(editor.contains("teamId = team.id"))
        assertTrue(match.contains("teamId = hTeam?.id"))
        assertTrue(match.contains("teamId = aTeam?.id"))
        assertTrue(standings.contains("teamId = team.id"))
        assertTrue(standings.contains("teamId = homeTeam.id"))
        assertTrue(standings.contains("teamId = awayTeam.id"))
        assertTrue(dashboard.contains("teamId = pTeam.id"))
        assertTrue(dashboard.contains("teamId = opponentId"))
    }

    private fun extractCalls(source: String, file: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        var cursor = 0
        while (true) {
            val start = source.indexOf("TeamBadge(", cursor)
            if (start < 0) break
            if (source.substring(maxOf(0, start - 8), start).contains("fun ")) {
                cursor = start + 10
                continue
            }
            var depth = 0
            var end = start
            while (end < source.length) {
                when (source[end]) {
                    '(' -> depth++
                    ')' -> if (--depth == 0) { end++; break }
                }
                end++
            }
            out += file to source.substring(start, end)
            cursor = end
        }
        return out
    }

    private fun readProjectSource(relativeToApp: String): String {
        val candidates = listOf(File(relativeToApp), File("app/$relativeToApp"), File("../app/$relativeToApp"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relativeToApp; cwd=${File(".").absolutePath}")
    }
}
