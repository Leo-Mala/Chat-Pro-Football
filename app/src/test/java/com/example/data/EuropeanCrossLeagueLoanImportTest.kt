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
class EuropeanCrossLeagueLoanImportTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun dataset(): EuropeanCanonicalDataset {
        val original = context.assets
            .open("${EuropeanCanonicalDatasetLoader.DEFAULT_BASE_PATH}/inglaterra__premier-league__manchester-united.json")
            .bufferedReader()
            .use { it.readText() }
        val loan = """[{"player":{"fullName":"Fixture Cross League Loan","birthDateIso":"2001-06-15","nationality":"Fixture","position":"GOL","shirtNumber":99,"identityDisambiguator":""},"ownerCountry":"Inglaterra","ownerClubName":"Manchester United","borrowerCountry":"Turquia","borrowerClubName":"Trabzonspor","season":2026,"startWeek":1,"durationWeeks":48,"verifiedAsOfIso":"2026-08-18","sourceRefs":["fixture://cross-league-loan"]}]"""
        val canonical = original.replace("\"loans\":[]", "\"loans\":$loan")
        val manifest = """{"provider":"wikimedia-open-data","season":"2026/27","generatedAt":"2026-08-18T17:36:22Z","countries":["Inglaterra"],"leagues":["Premier League"],"clubCount":1,"playerCount":22,"loanCount":1,"validationStatus":"FIXTURE_ONLY","datasetFiles":["mu.json"]}"""
        return EuropeanCanonicalDatasetLoader.loadFromStrings(
            manifestJson = manifest,
            datasetJsonByFile = mapOf("mu.json" to canonical),
            allowFixture = true
        )
    }

    @Test
    fun `loader accepts stable borrower outside imported league without inventing a club fact`() {
        val dataset = dataset()
        assertEquals(1, dataset.clubFacts.size)
        assertEquals(1, dataset.loans.size)
        val loan = dataset.loans.single()
        assertEquals(5L, loan.ownerTeamId)
        assertEquals(130_395L, loan.borrowerTeamId)
        assertEquals("Trabzonspor", loan.borrowerClubName)
        assertEquals("Turquia", loan.borrowerCountry)
    }

    @Test
    fun `new save coordinator materializes external borrower then planner persists PlayerLoan`() {
        val dataset = dataset()
        val repositoryKey = Any()
        val united = Team(
            id = 5L,
            name = "Manchester United",
            city = "Procedural Manchester",
            state = "MNC",
            country = "Inglaterra",
            division = 1,
            rating = 83,
            stadiumName = "Procedural Stadium",
            logoUrl = null
        )

        EuropeanNewSaveSeedCoordinator.prepareForDataset(repositoryKey, listOf(united), dataset)

        val seededTeams = EuropeanNewSaveSeedCoordinator.teamsForTesting(repositoryKey, listOf(united))
        assertEquals(2, seededTeams.size)
        val trabzonspor = seededTeams.single { it.id == 130_395L }
        assertEquals("Trabzonspor", trabzonspor.name)
        assertEquals("Turquia", trabzonspor.country)
        assertEquals("Papara Park", trabzonspor.stadiumName)

        val seed = EuropeanNewSaveSeedCoordinator.consumePlayersForKey(repositoryKey, emptyList())
        assertTrue(seed.overridden)
        assertEquals(1, seed.loans.size)
        val loan = seed.loans.single()
        assertEquals(5L, loan.ownerTeamId)
        assertEquals(130_395L, loan.borrowerTeamId)
        val player = seed.players.single { it.id == loan.playerId }
        assertTrue(player.isOnLoan)
        assertEquals(130_395L, player.teamId)
        assertEquals(5L, player.originalTeamId)

        val second = EuropeanNewSaveSeedCoordinator.consumePlayersForKey(repositoryKey, emptyList())
        assertFalse(second.overridden)
        assertTrue(second.loans.isEmpty())
    }
}
