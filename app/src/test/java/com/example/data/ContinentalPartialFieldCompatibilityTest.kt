package com.example.data

import org.junit.Assert.assertTrue
import org.junit.Test

class ContinentalPartialFieldCompatibilityTest {

    @Test
    fun twentyEligibleCONMEBOLClubsDoNotCreateReducedLibertadores() {
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

        assertTrue(fields.tier1.isEmpty())
        assertTrue(fields.tier2.isEmpty())
        assertTrue(fields.tier3.isEmpty())
        assertTrue(fields.allTeamIds.isEmpty())
    }
}
