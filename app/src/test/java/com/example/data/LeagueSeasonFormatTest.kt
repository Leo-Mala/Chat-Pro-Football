package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeagueSeasonFormatTest {

    @Test
    fun compactSingleLegHomeAssignmentsAreBalancedAndFlipNextSeason() {
        val teamCount = 24
        val homeCounts = IntArray(teamCount)

        for (i in 0 until teamCount - 1) {
            for (j in i + 1 until teamCount) {
                val firstHosts2026 = LeagueSeasonFormat.firstTeamHostsCompactSingleLeg(
                    firstIndex = i,
                    secondIndex = j,
                    season = 2026,
                    division = 2
                )
                val firstHosts2027 = LeagueSeasonFormat.firstTeamHostsCompactSingleLeg(
                    firstIndex = i,
                    secondIndex = j,
                    season = 2027,
                    division = 2
                )

                if (firstHosts2026) homeCounts[i]++ else homeCounts[j]++
                assertEquals(
                    "O mando do mesmo confronto deve inverter na temporada seguinte",
                    !firstHosts2026,
                    firstHosts2027
                )
            }
        }

        assertTrue(homeCounts.maxOrNull()!! - homeCounts.minOrNull()!! <= 1)
        assertTrue(homeCounts.all { it in 11..12 })
    }

    @Test
    fun compactSimulationUsesTwoLegsThroughTwentyAndOneAboveTwenty() {
        assertEquals(2, LeagueSeasonFormat.legsForCompactSimulation(20))
        assertEquals(1, LeagueSeasonFormat.legsForCompactSimulation(21))
        assertEquals(1, LeagueSeasonFormat.legsForCompactSimulation(96))
    }

    @Test
    fun balancedGiantLeaguesReceiveEqualGroupPlansInsideFortyWeeks() {
        val sixty = LeagueSeasonFormat.detailedGroupPlan(60)
        val ninetySix = LeagueSeasonFormat.detailedGroupPlan(96)

        assertEquals(3, sixty?.groupCount)
        assertEquals(20, sixty?.groupSize)
        assertEquals(38, sixty?.rounds)
        assertEquals(1_140, LeagueSeasonFormat.expectedFixtureCount(60))

        assertEquals(6, ninetySix?.groupCount)
        assertEquals(16, ninetySix?.groupSize)
        assertEquals(30, ninetySix?.rounds)
        assertEquals(1_440, LeagueSeasonFormat.expectedFixtureCount(96))

        assertTrue(LeagueSeasonFormat.supportsDetailedFormat(60))
        assertTrue(LeagueSeasonFormat.supportsDetailedFormat(96))
        assertFalse(LeagueSeasonFormat.fitsCurrentSeason(60))
        assertFalse(LeagueSeasonFormat.fitsCurrentSeason(96))
    }

    @Test
    fun irregularGiantSizeStaysOnFallbackUntilEqualGroupsAreDefined() {
        assertNull(LeagueSeasonFormat.detailedGroupPlan(41))
        assertFalse(LeagueSeasonFormat.supportsDetailedFormat(41))
    }

    @Test
    fun detailedCompetitionAliasesRemainDivisionAwareBeyondFourthLevel() {
        assertEquals(setOf("SERIE_A", "DIV_1"), LeagueSeasonFormat.acceptedDetailedCompetitionTypes(1))
        assertEquals(setOf("SERIE_D", "DIV_4"), LeagueSeasonFormat.acceptedDetailedCompetitionTypes(4))
        assertEquals(setOf("SERIE_D", "DIV_5"), LeagueSeasonFormat.acceptedDetailedCompetitionTypes(5))
        assertEquals(setOf("SERIE_D", "DIV_6"), LeagueSeasonFormat.acceptedDetailedCompetitionTypes(6))
    }
}
