package com.example.usecase

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketStrengthAlwaysVisibleTest {
    @Test fun `market shows persisted real strength and never observed placeholder`() {
        val market = source("src/main/java/com/example/ui/screens/TransfersScreen.kt")
        val dialog = source("src/main/java/com/example/ui/components/transfers/PurchaseNegotiationDialog.kt")
        assertTrue(market.contains("text = player.force.toString()"))
        assertTrue(dialog.contains("text = player.force.toString()"))
        assertTrue(dialog.contains("Força: ${'$'}{player.force}"))
        assertFalse(market.contains("getObservedForce("))
        assertFalse(dialog.contains("getObservedForce("))
    }
    private fun source(path: String): String = listOf(File(path), File("app/$path"), File("../app/$path"))
        .firstOrNull { it.isFile }?.readText() ?: error("Missing $path")
}
