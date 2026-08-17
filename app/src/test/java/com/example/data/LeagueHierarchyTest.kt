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
    fun unitedStatesCanadaUsesCanonicalGlobalCountryKey() {
        val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry("Estados Unidos / Canadá")

        assertTrue(LeagueHierarchyLoader.hasExplicitHierarchy("Estados Unidos / Canadá"))
        assertEquals(2, hierarchy.movementSpotsBetween(1, 2))
        assertTrue(LeagueHierarchyLoader.supportedCountries.contains("Estados Unidos / Canadá"))
    }
}
