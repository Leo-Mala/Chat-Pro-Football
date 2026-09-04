package com.example.ui.components.transfers

import com.example.data.Player
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPlayerValueRangeRegressionTest {
    @Test
    fun `editable market offer control never excludes the value shown before opening it`() {
        val samples = listOf(
            Player(id = 1L, teamId = null, name = "A", age = 19, position = "ATA", force = 99, potential = 99),
            Player(id = 2L, teamId = 2L, name = "B", age = 25, position = "ZAG", force = 80, potential = 80),
            Player(id = 3L, teamId = 3L, name = "C", age = 34, position = "MEI", force = 70, potential = 75)
        )
        samples.forEach { player ->
            val range = purchaseOfferRange(player)
            assertTrue(range.marketValue in range.minimum..range.maximum)
        }
    }
}
