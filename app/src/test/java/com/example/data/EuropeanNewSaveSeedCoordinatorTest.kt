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
        assertEquals("Emirates Stadium", arsenal.stadiumName)

        val firstConsume = EuropeanNewSaveSeedCoordinator.consumePlayersForKey(repositoryKey, emptyList())
        assertTrue(firstConsume.overridden)
        assertEquals(486, firstConsume.players.size)
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

    @Test
    fun `unrelated team with colliding numeric id never receives Premier League overlay`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataset = EuropeanCanonicalDatasetLoader.loadForTesting(context.assets)
        val repositoryKey = Any()
        val unrelated = Team(
            id = 2L,
            name = "Clube Brasileiro Sintético",
            city = "Cidade Original",
            state = "MG",
            country = "Brasil",
            division = 1,
            rating = 70,
            stadiumName = "Estádio Original",
            logoUrl = null
        )

        assertFalse(dataset.appliesTo(unrelated))
        assertEquals(listOf(unrelated), dataset.applyClubFacts(listOf(unrelated)))

        EuropeanNewSaveSeedCoordinator.prepareForDataset(repositoryKey, listOf(unrelated), dataset)
        assertEquals(
            listOf(unrelated),
            EuropeanNewSaveSeedCoordinator.teamsForTesting(repositoryKey, listOf(unrelated))
        )

        val fallbackPlayer = Player(
            id = 999_001L,
            teamId = unrelated.id,
            name = "Fallback Player",
            age = 24,
            nationality = "Brasil",
            position = "GOL",
            force = 60
        )
        val seed = EuropeanNewSaveSeedCoordinator.consumePlayersForKey(repositoryKey, listOf(fallbackPlayer))
        assertFalse(seed.overridden)
        assertEquals(listOf(fallbackPlayer), seed.players)
        assertTrue(seed.loans.isEmpty())
    }
}
