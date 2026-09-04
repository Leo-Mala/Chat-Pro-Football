package com.example.ui.screens

import com.example.data.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketPositionFilterRegressionTest {
    @Test
    fun `selected position never returns rows from a previous position`() {
        val players = listOf(
            Player(id = 1L, teamId = null, name = "Zagueiro", age = 25, position = "ZAG", force = 80),
            Player(id = 2L, teamId = null, name = "Lateral", age = 25, position = "LAT", force = 80),
            Player(id = 3L, teamId = null, name = "Atacante", age = 25, position = "ATA", force = 80)
        )
        val criteria = MarketSearchCriteria("", "ZAG", 0, 99, 500_000_000L, "FORCA_DESC")
        val result = filterAndSortMarketPlayers(players, criteria)
        assertEquals(listOf(1L), result.map { it.id })
        assertTrue(result.all { it.position == "ZAG" })
    }

    @Test
    fun `result key distinguishes an old LAT result from a newly selected ZAG filter`() {
        val oldKey = MarketSearchKey(MarketSearchCriteria("", "LAT", 0, 99, 500_000_000L, "FORCA_DESC"), 10L, emptySet())
        val newKey = MarketSearchKey(MarketSearchCriteria("", "ZAG", 0, 99, 500_000_000L, "FORCA_DESC"), 10L, emptySet())
        assertTrue(oldKey != newKey)
    }
}
