package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeagueHierarchyTest {

    @Test
    fun brazilKeepsFourMovementSpotsAcrossAllFiveConfiguredLevels() {
        val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry("Brasil")

        assertTrue(LeagueHierarchyLoader.hasExplicitHierarchy("Brasil"))
        assertEquals(listOf(1, 2, 3, 4, 5), hierarchy.divisions.map { it.divisionLevel })
        assertEquals(4, hierarchy.movementSpotsBetween(1, 2))
        assertEquals(4, hierarchy.movementSpotsBetween(2, 3))
        assertEquals(4, hierarchy.movementSpotsBetween(3, 4))
        assertEquals(4, hierarchy.movementSpotsBetween(4, 5))
        assertEquals(4, hierarchy.getDivisionByLevel(4)?.relegationSpots)
        assertEquals(4, hierarchy.getDivisionByLevel(5)?.promotionSpots)
        assertEquals(0, hierarchy.getDivisionByLevel(5)?.relegationSpots)
        assertEquals(
            4,
            hierarchy.safeMovementSpotsBetween(
                upperLevel = 4,
                lowerLevel = 5,
                upperTeamCount = 96,
                lowerTeamCount = 15
            )
        )
        assertTrue(hierarchy.hasBalancedAdjacentMovementRules())
    }

    @Test
    fun countryWithoutExplicitHierarchyUsesGenericTwoSpotsOnlyAcrossRealLevels() {
        val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry("França")

        assertFalse(LeagueHierarchyLoader.hasExplicitHierarchy("França"))
        assertEquals("França", hierarchy.country)
        assertEquals(listOf(1, 2, 3), hierarchy.divisions.map { it.divisionLevel })
        assertEquals(2, hierarchy.movementSpotsBetween(1, 2))
        assertEquals(2, hierarchy.movementSpotsBetween(2, 3))
        assertEquals(0, hierarchy.movementSpotsBetween(3, 4))
        assertNull(hierarchy.getDivisionByLevel(4))
        assertTrue(hierarchy.hasBalancedAdjacentMovementRules())
    }

    @Test
    fun twoDivisionCountryDoesNotExposeOrReserveForEmptyThirdLevel() {
        val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry("Suíça")

        assertEquals(listOf(1, 2), hierarchy.divisions.map { it.divisionLevel })
        assertNull(hierarchy.getDivisionByLevel(3))
        assertEquals(
            2,
            hierarchy.safeMovementSpotsBetween(
                upperLevel = 1,
                lowerLevel = 2,
                upperTeamCount = 12,
                lowerTeamCount = 10
            )
        )
    }

    @Test
    fun oneDivisionCountryHasNoSyntheticPromotionBoundary() {
        val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry("Bolívia")

        assertEquals(listOf(1), hierarchy.divisions.map { it.divisionLevel })
        assertEquals(0, hierarchy.movementSpotsBetween(1, 2))
        assertTrue(hierarchy.hasBalancedAdjacentMovementRules())
    }

    @Test
    fun tinyMiddleDivisionCapsMovementSoPromotedAndRelegatedGroupsCannotOverlap() {
        val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry("França")

        // França possui três divisões ativas; uma Série B artificialmente reduzida a 2 clubes
        // precisa reservar um para cada direção.
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
    fun unitedStatesCanadaUsesCanonicalGlobalCountryKeyAndThreeRealLevels() {
        val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry("Estados Unidos / Canadá")

        assertTrue(LeagueHierarchyLoader.hasExplicitHierarchy("Estados Unidos / Canadá"))
        assertEquals(listOf(1, 2, 3), hierarchy.divisions.map { it.divisionLevel })
        assertEquals(2, hierarchy.movementSpotsBetween(1, 2))
        assertEquals(2, hierarchy.movementSpotsBetween(2, 3))
        assertTrue(LeagueHierarchyLoader.supportedCountries.contains("Estados Unidos / Canadá"))
    }
}
