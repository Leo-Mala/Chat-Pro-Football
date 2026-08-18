package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EuropeanNewSaveSeedCoordinatorTest {
    @Test
    fun `factual seed is one-shot and cannot leak into a later repository operation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataset = EuropeanCanonicalDatasetLoader.loadForTesting(context.assets)
        val repositoryKey = Any()

        val proceduralTeams = dataset.clubFacts.map { fact ->
            Team(
                id = fact.teamId,
                name = fact.name,
                city = "Procedural City",
                state = "EU",
                country = fact.country,
                division = 1,
                rating = 75,
                stadiumName = "Procedural Stadium",
                logoUrl = null
            )
        }

        EuropeanNewSaveSeedCoordinator.prepareForDataset(repositoryKey, proceduralTeams, dataset)

        val factualTeams = EuropeanNewSaveSeedCoordinator.teamsForTesting(repositoryKey, proceduralTeams)
        val arsenal = factualTeams.single { it.name == "Arsenal FC" }
        assertEquals("London", arsenal.city)
        assertEquals("Fixture Stadium 01", arsenal.stadiumName)

        val firstConsume = EuropeanNewSaveSeedCoordinator.consumePlayersForKey(repositoryKey, emptyList())
        assertTrue(firstConsume.overridden)
        assertEquals(91, firstConsume.players.size)
        assertEquals(1, firstConsume.loans.size)

        val secondConsume = EuropeanNewSaveSeedCoordinator.consumePlayersForKey(repositoryKey, emptyList())
        assertFalse(secondConsume.overridden)
        assertTrue(secondConsume.players.isEmpty())
        assertTrue(secondConsume.loans.isEmpty())

        // O consumo remove também o overlay de Team; qualquer operação posterior volta ao fallback.
        assertEquals(
            proceduralTeams,
            EuropeanNewSaveSeedCoordinator.teamsForTesting(repositoryKey, proceduralTeams)
        )
    }
}
