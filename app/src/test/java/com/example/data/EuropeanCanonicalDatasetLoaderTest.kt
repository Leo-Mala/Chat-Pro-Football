package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EuropeanCanonicalDatasetLoaderTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun readAsset(fileName: String): String = context.assets.open("${EuropeanCanonicalDatasetLoader.DEFAULT_BASE_PATH}/$fileName").bufferedReader().use { it.readText() }

    @Test fun `production loader accepts validated factual Premier League dataset`() {
        val dataset = EuropeanCanonicalDatasetLoader.loadValidatedFactualOrNull(context.assets)
        assertNotNull(dataset)
        assertEquals("VALIDATED", dataset?.manifest?.validationStatus)
        assertEquals("wikimedia-open-data", dataset?.manifest?.provider)
        assertEquals(20, dataset?.clubFacts?.size)
        assertEquals(1, dataset?.loans?.size)
    }

    @Test fun `test loader materializes checked-in Premier League factual dataset`() {
        val dataset = EuropeanCanonicalDatasetLoader.loadForTesting(context.assets)
        assertEquals("2026/27", dataset.manifest.season)
        assertEquals("VALIDATED", dataset.manifest.validationStatus)
        assertEquals(20, dataset.clubFacts.size)
        assertEquals(20, dataset.squads.size)
        assertEquals(1, dataset.loans.size)
        assertEquals(486, dataset.squads.sumOf { it.players.size } + dataset.loans.size)
        assertTrue(dataset.squads.all { it.coverage() == EuropeanSquadCoverage.GAMEPLAY_READY_FACTUAL_SNAPSHOT })
    }

    @Test fun `procedural stable alias cannot resolve a factual squad`() {
        val dataset = EuropeanCanonicalDatasetLoader.loadForTesting(context.assets)
        assertNotNull(dataset.squadCatalog.find("Inglaterra", "Liverpool FC"))
        assertNull(
            "A procedural club named Liverpool must not inherit Liverpool FC factual identity",
            dataset.squadCatalog.find("Inglaterra", "Liverpool")
        )
    }

    @Test fun `canonical loader rejects gameplay attributes even in fixture mode`() {
        val manifest = readAsset("dataset_manifest.json")
        val arsenal = "inglaterra__premier-league__arsenal-fc.json"
        val poisoned = readAsset(arsenal).replaceFirst("\"fullName\"", "\"force\":99,\"fullName\"")
        val files = listOf(arsenal, "inglaterra__premier-league__aston-villa.json", "inglaterra__premier-league__afc-bournemouth.json", "inglaterra__premier-league__brentford-fc.json", "inglaterra__premier-league__manchester-united.json")
            .associateWith { if (it == arsenal) poisoned else readAsset(it) }
        var rejected = false
        try {
            EuropeanCanonicalDatasetLoader.loadFromStrings(manifest, files, allowFixture = true)
        } catch (expected: IllegalArgumentException) {
            rejected = expected.message.orEmpty().contains("Campo proibido")
        }
        assertTrue("force must never cross the factual-data boundary", rejected)
    }
}
