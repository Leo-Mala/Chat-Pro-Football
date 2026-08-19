package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Fc26DatasetAssetTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun `checked in FC26 asset validates and preserves all source players`() {
        val loaded = Fc26NormalizedDatasetLoader.loadValidatedOrNull(context.assets)
        assertNotNull(loaded)
        val dataset = requireNotNull(loaded)
        assertEquals(18_405, dataset.players.size)
        assertEquals(662, dataset.sourceClubs.size)
        assertEquals(89, dataset.freeAgents.size)
        assertEquals(18_405, dataset.players.map { it.sourcePlayerId }.distinct().size)
        assertEquals(18_405, dataset.players.map { it.stableId }.distinct().size)
        assertTrue(dataset.players.all { it.overall in 1..99 && it.potential in 1..99 })
        assertTrue(dataset.players.all { it.atributos.let { a -> listOf(a.finalizacao, a.passe, a.velocidade, a.reflexos).all { it in 1..99 } } })
    }
}
