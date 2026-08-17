package com.example.data

import org.junit.Assert.assertEquals
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
}
