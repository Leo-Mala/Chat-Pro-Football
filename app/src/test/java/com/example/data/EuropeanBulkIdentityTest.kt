package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EuropeanBulkIdentityTest {
    @Test fun `bulk dataset resolves internal team and player identities instead of provider ids`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataset = EuropeanCanonicalDatasetLoader.loadForTesting(context.assets)
        val arsenalFact = dataset.clubFacts.single { it.name == "Arsenal FC" }
        val arsenal = requireNotNull(dataset.squadCatalog.find("Inglaterra", "Arsenal FC"))
        val firstPlayer = arsenal.players.first()
        assertEquals(requireNotNull(StableTeamIdentityRegistry.idFor("Inglaterra", "Arsenal FC")), arsenalFact.teamId)
        assertEquals(2L, arsenalFact.teamId)
        assertNotEquals(9001L, arsenalFact.teamId)
        assertTrue(StableRealPlayerIdentity.isRealPlayerId(firstPlayer.stableId))
        assertNotEquals(100101L, firstPlayer.stableId)
        assertEquals(firstPlayer.stableId, firstPlayer.toGameplayPlayer(arsenalFact.teamId, 80).id)
    }
}
