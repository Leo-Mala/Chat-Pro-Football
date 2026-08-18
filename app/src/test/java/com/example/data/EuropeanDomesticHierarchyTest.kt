package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EuropeanDomesticHierarchyTest {

    @Test
    fun `England exchanges three clubs across both modeled boundaries`() {
        val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry("Inglaterra")

        assertEquals(3, hierarchy.divisions.size)
        assertEquals(3, hierarchy.movementSpotsBetween(1, 2))
        assertEquals(3, hierarchy.movementSpotsBetween(2, 3))
        assertTrue(hierarchy.hasBalancedAdjacentMovementRules())
        assertEquals(3, hierarchy.safeMovementSpotsBetween(1, 2, upperTeamCount = 20, lowerTeamCount = 24))
        assertEquals(3, hierarchy.safeMovementSpotsBetween(2, 3, upperTeamCount = 24, lowerTeamCount = 24))
    }

    @Test
    fun `Spain exchanges three then four clubs across modeled boundaries`() {
        val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry("Espanha")

        assertEquals(3, hierarchy.divisions.size)
        assertEquals(3, hierarchy.movementSpotsBetween(1, 2))
        assertEquals(4, hierarchy.movementSpotsBetween(2, 3))
        assertTrue(hierarchy.hasBalancedAdjacentMovementRules())
        assertEquals(3, hierarchy.safeMovementSpotsBetween(1, 2, upperTeamCount = 20, lowerTeamCount = 22))
        assertEquals(4, hierarchy.safeMovementSpotsBetween(2, 3, upperTeamCount = 22, lowerTeamCount = 40))
    }
}
