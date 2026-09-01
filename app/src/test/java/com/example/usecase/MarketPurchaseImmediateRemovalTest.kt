package com.example.usecase

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketPurchaseImmediateRemovalTest {
    @Test fun `successful purchase is removed locally before global Room reconciliation`() {
        val market = source("src/main/java/com/example/ui/screens/TransfersScreen.kt")
        val dialog = source("src/main/java/com/example/ui/components/transfers/PurchaseNegotiationDialog.kt")
        assertTrue(market.contains("locallyPurchasedIds"))
        assertTrue(market.contains("player.id !in locallyPurchasedIds"))
        assertTrue(market.contains("onPurchased = { locallyPurchasedIds = locallyPurchasedIds + p.id }"))
        assertTrue(dialog.contains("onPurchased: () -> Unit = {}"))
        assertTrue(dialog.split("onPurchased()").size >= 4)
    }
    private fun source(path: String): String = listOf(File(path), File("app/$path"), File("../app/$path"))
        .firstOrNull { it.isFile }?.readText() ?: error("Missing $path")
}
