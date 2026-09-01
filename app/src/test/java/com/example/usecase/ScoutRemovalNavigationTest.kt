package com.example.usecase

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class ScoutRemovalNavigationTest {
    @Test fun `market exposes no scout navigation or scout dialog`() {
        val market = source("src/main/java/com/example/ui/screens/TransfersScreen.kt")
        val dialog = source("src/main/java/com/example/ui/components/transfers/PurchaseNegotiationDialog.kt")
        assertFalse(market.contains("\"OLHEIRO\" to \"OLHEIRO\""))
        assertFalse(market.contains("selectedPlayerForScouting"))
        assertFalse(market.contains("ScoutSelectionDialog("))
        assertFalse(dialog.contains("ScoutSelectionDialog("))
        assertFalse(dialog.contains("FICAR DE OLHO (OLHEIRO)"))
        assertFalse(dialog.contains("CONTRATAR OLHEIRO"))
    }
    private fun source(path: String): String = listOf(File(path), File("app/$path"), File("../app/$path"))
        .firstOrNull { it.isFile }?.readText() ?: error("Missing $path")
}
