package com.example.ui.components.transfers

import com.example.data.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketNegotiationRangeRegressionTest {
    @Test
    fun `manual evidence player opens negotiation around the exact market card value`() {
        val player = Player(id = 1L, teamId = null, name = "Igor Almeida", age = 25, position = "GOL", force = 99, potential = 99)
        val range = purchaseOfferRange(player)

        assertEquals(29_700_000L, range.marketValue)
        assertEquals(14_850_000L, range.minimum)
        assertEquals(44_550_000L, range.maximum)
        assertTrue(range.marketValue in range.minimum..range.maximum)
    }
}
