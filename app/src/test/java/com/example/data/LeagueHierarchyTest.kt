package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeagueHierarchyTest {

    @Test
    fun brazilKeepsFourMovementSpots() {
        val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry("Brasil")

        assertTrue(LeagueHierarchyLoader.hasExplicitHierarchy("Brasil"))
        assertEquals(4, hierarchy.movementSpotsBetween(1, 2))
        assertEquals(4, hierarchy.movementSpotsBetween(2, 3))
        assertEquals(4, hierarchy.movementSpotsBetween(3, 4))
        assertEquals(
            4,
            hierarchy.safeMovementSpotsBetween(
                upperLevel = 1,
                lowerLevel = 2,
                upperTeamCount = 20,
                lowerTeamCount = 20
            )
        )
        assertTrue(hierarchy.hasBalancedAdjacentMovementRules())
    }

    @Test
    fun countryWithoutExplicitHierarchyUsesGenericTwoSpots() {
        val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry("França")

        assertFalse(LeagueHierarchyLoader.hasExplicitHierarchy("França"))
        assertEquals("França", hierarchy.country)
        assertEquals(2, hierarchy.movementSpotsBetween(1, 2))
        assertEquals(2, hierarchy.movementSpotsBetween(2, 3))
        assertEquals(2, hierarchy.movementSpotsBetween(3, 4))
        assertTrue(hierarchy.hasBalancedAdjacentMovementRules())
    }

    @Test
    fun tinyMiddleDivisionCapsMovementSoPromotedAndRelegatedGroupsCannotOverlap() {
        val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry("França")

        // Série B com apenas 2 clubes precisa reservar um para cada direção.
        assertEquals(
            1,
            hierarchy.safeMovementSpotsBetween(
                upperLevel = 1,
                lowerLevel = 2,
                upperTeamCount = 4,
                lowerTeamCount = 2
            )
        )
        assertEquals(
            1,
            hierarchy.safeMovementSpotsBetween(
                upperLevel = 2,
                lowerLevel = 3,
                upperTeamCount = 2,
                lowerTeamCount = 4
            )
        )

        // Uma divisão intermediária com só 1 clube não pode mover o mesmo clube duas vezes.
        assertEquals(
            0,
            hierarchy.safeMovementSpotsBetween(
                upperLevel = 1,
                lowerLevel = 2,
                upperTeamCount = 4,
                lowerTeamCount = 1
            )
        )
    }

    @Test
    fun unitedStatesCanadaUsesCanonicalGlobalCountryKey() {
        val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry("Estados Unidos / Canadá")

        assertTrue(LeagueHierarchyLoader.hasExplicitHierarchy("Estados Unidos / Canadá"))
        assertEquals(2, hierarchy.movementSpotsBetween(1, 2))
        assertTrue(LeagueHierarchyLoader.supportedCountries.contains("Estados Unidos / Canadá"))
    }
}
