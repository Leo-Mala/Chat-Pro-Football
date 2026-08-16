package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinentalTierAvailabilityTest {

    @Test
    fun conmebolAndCafDoNotCreateDuplicateThirdTier() {
        assertTrue(SeasonCompetitionSystem.isContinentalTierAvailable("Brasil", 1))
        assertTrue(SeasonCompetitionSystem.isContinentalTierAvailable("Brasil", 2))
        assertFalse(SeasonCompetitionSystem.isContinentalTierAvailable("Brasil", 3))

        assertTrue(SeasonCompetitionSystem.isContinentalTierAvailable("Marrocos", 1))
        assertTrue(SeasonCompetitionSystem.isContinentalTierAvailable("Marrocos", 2))
        assertFalse(SeasonCompetitionSystem.isContinentalTierAvailable("Marrocos", 3))
    }

    @Test
    fun uefaConcacafAndAfcKeepThreeDistinctTiers() {
        assertTrue(SeasonCompetitionSystem.isContinentalTierAvailable("Inglaterra", 3))
        assertTrue(SeasonCompetitionSystem.isContinentalTierAvailable("México", 3))
        assertTrue(SeasonCompetitionSystem.isContinentalTierAvailable("Japão", 3))
    }

    @Test
    fun unknownCountryDoesNotFallbackToAnotherConfederation() {
        assertFalse(SeasonCompetitionSystem.isContinentalTierAvailable("País Inexistente", 1))
        assertFalse(SeasonCompetitionSystem.isContinentalTierAvailable("País Inexistente", 2))
        assertFalse(SeasonCompetitionSystem.isContinentalTierAvailable("País Inexistente", 3))
    }
}
