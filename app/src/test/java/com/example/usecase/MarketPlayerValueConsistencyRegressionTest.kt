package com.example.usecase

import com.example.data.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketPlayerValueConsistencyRegressionTest {
    @Test
    fun `Igor Almeida uses the same canonical value in player and negotiation policy`() {
        val player = Player(
            id = 9001L,
            teamId = null,
            name = "Igor Almeida",
            age = 25,
            position = "GOL",
            force = 99,
            potential = 99,
            market_value = 110_430_000L
        )

        val canonical = player.calculateMarketValue()

        assertEquals(29_700_000L, canonical)
        assertEquals(canonical, TransferNegotiationUseCase.calculateDynamicPlayerPrice(player))
        // A legacy persisted field must never silently override the value shown/charged by the Market.
        assertEquals(29_700_000L, player.calculateMarketValue())
    }

    @Test
    fun `same policy remains deterministic for a lower force player`() {
        val player = Player(
            id = 9002L,
            teamId = 2L,
            name = "Declan White",
            age = 25,
            position = "ZAG",
            force = 80,
            potential = 80
        )
        assertEquals(player.calculateMarketValue(), TransferNegotiationUseCase.calculateDynamicPlayerPrice(player))
    }
}
