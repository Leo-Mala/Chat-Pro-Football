package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinentalPartialFieldCompatibilityTest {

    // Final regression for reduced candidate universes: never persist an unfinishable 20-team field.
    @Test
    fun twentyEligibleCONMEBOLClubsDegradeToCompleteSixteenTeamTierOne() {
        val candidates = (1L..20L).map { id ->
            Team(
                id = id,
                name = "Brasil Clube $id",
                city = "Cidade",
                state = "BR",
                country = "Brasil",
                division = 1,
                rating = 100 - id.toInt()
            )
        }

        val fields = CupCompetitionSystem.selectContinentalFields(candidates, "CONMEBOL")

        assertEquals(16, fields.tier1.size)
        assertTrue(fields.tier2.isEmpty())
        assertTrue(fields.tier3.isEmpty())
        assertEquals(16, fields.allTeamIds.size)
    }
}
