package com.example.ui.components.standings

import com.example.data.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScorerAggregationIntegrityTest {
    @Test
    fun `season ranking ignores historical career goals`() {
        val historicalLeader = player(id = 1L, name = "Histórico", seasonGoals = 0, careerGoals = 55)
        val seasonLeader = player(id = 2L, name = "Temporada", seasonGoals = 4, careerGoals = 4)
        val second = player(id = 3L, name = "Vice", seasonGoals = 2, careerGoals = 30)

        val result = seasonTopScorers(listOf(historicalLeader, second, seasonLeader))

        assertEquals(listOf(2L, 3L), result.map { it.id })
        assertFalse(result.any { it.id == historicalLeader.id })
    }

    @Test
    fun `season ranking is descending by current season goals`() {
        val result = seasonTopScorers(
            listOf(
                player(id = 1L, name = "A", seasonGoals = 1, careerGoals = 80),
                player(id = 2L, name = "B", seasonGoals = 7, careerGoals = 7),
                player(id = 3L, name = "C", seasonGoals = 3, careerGoals = 40)
            )
        )

        assertEquals(listOf(7, 3, 1), result.map { it.gols })
    }

    private fun player(
        id: Long,
        name: String,
        seasonGoals: Int,
        careerGoals: Int
    ) = Player(
        id = id,
        teamId = 10L,
        name = name,
        age = 25,
        position = "ATA",
        force = 70,
        gols = seasonGoals,
        careerGoals = careerGoals
    )
}
