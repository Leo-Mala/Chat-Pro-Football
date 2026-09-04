package com.example.ui.screens

import com.example.data.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketPriceFilterRegressionTest {
    @Test
    fun `price maximum filters by the exact value rendered in market cards`() {
        val cheap = Player(id = 1L, teamId = null, name = "Cheap", age = 25, position = "MEI", force = 60, potential = 60)
        val expensive = Player(id = 2L, teamId = null, name = "Expensive", age = 25, position = "MEI", force = 99, potential = 99)
        val cutoff = cheap.calculateMarketValue()
        val result = filterAndSortMarketPlayers(
            listOf(cheap, expensive),
            MarketSearchCriteria("", "TODOS", 0, 99, cutoff, "VALOR_ASC")
        )
        assertEquals(listOf(cheap.id), result.map { it.id })
        assertEquals(cutoff, result.single().calculateMarketValue())
    }
}
