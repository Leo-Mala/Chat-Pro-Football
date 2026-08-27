package com.example.data

import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerEvolutionRegressionTest {
    @Test
    fun `compact monthly engine does not retain no-op results for full world scale`() {
        val team = Team(
            id = 1L,
            name = "Scale Team",
            city = "Test",
            state = "TS",
            country = "Brasil",
            division = 1,
            rating = 50,
            trainingCenterLevel = 1
        )
        val players = List(60_000) { index ->
            Player(
                id = index.toLong() + 1L,
                teamId = team.id,
                name = "Scale Player $index",
                age = 16,
                position = "MEI",
                force = 50,
                potential = 50,
                finishing = 50,
                passing = 50,
                pace = 50,
                strength = 50,
                vision = 50,
                defense = 50,
                minutosJogados = 0,
                mediaNotas = 0.0
            )
        }

        val retained = PlayerEvolutionMonthlyEngine.processChanged(
            players = players,
            teamsMap = mapOf(team.id to team),
            periodDate = "S1_W4"
        )

        assertTrue(
            "No-op players must be processed but not retained as heavy monthly results",
            retained.isEmpty()
        )
    }
}
