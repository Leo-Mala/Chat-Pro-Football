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
    fun everyCurrentDefaultDataGiantLeagueKeepsPhase95FormatInsideDomesticFortyRoundLimit() {
        assertEquals(48, GameCalendar.WEEKS_PER_SEASON)
        assertEquals(40, GameCalendar.MAX_DOMESTIC_LEAGUE_ROUNDS)

        val expectedGroupSizes = mapOf(
            48 to 16,
            56 to 14,
            57 to 19,
            60 to 20,
            96 to 16
        )
        val actualGiantSizes = DefaultData.countryDivisionSizes
            .values
            .flatten()
            .filterNot { teamCount -> LeagueSeasonFormat.fitsCurrentSeason(teamCount) }
            .toSet()

        assertEquals(expectedGroupSizes.keys, actualGiantSizes)

        actualGiantSizes.forEach { teamCount ->
            val expectedGroupSize = expectedGroupSizes.getValue(teamCount)
            val plan = LeagueSeasonFormat.detailedGroupPlan(teamCount)
            requireNotNull(plan)

            assertEquals(expectedGroupSize, plan.groupSize)
            assertEquals(teamCount / expectedGroupSize, plan.groupCount)
            assertTrue(plan.rounds <= GameCalendar.MAX_DOMESTIC_LEAGUE_ROUNDS)
            assertTrue(LeagueSeasonFormat.supportsDetailedFormat(teamCount))
            assertFalse(LeagueSeasonFormat.fitsCurrentSeason(teamCount))
        }

        val sixty = LeagueSeasonFormat.detailedGroupPlan(60)
        val ninetySix = LeagueSeasonFormat.detailedGroupPlan(96)
        assertEquals(38, sixty?.rounds)
        assertEquals(1_140, LeagueSeasonFormat.expectedFixtureCount(60))
        assertEquals(30, ninetySix?.rounds)
        assertEquals(1_440, LeagueSeasonFormat.expectedFixtureCount(96))
    }

    @Test
    fun fortyOneTeamLeagueDoesNotBecomeFortyOneRoundDetailedLeagueJustBecauseSeasonHasFortyEightWeeks() {
        assertNull(LeagueSeasonFormat.detailedGroupPlan(41))
        assertFalse(LeagueSeasonFormat.supportsDetailedFormat(41))
        assertFalse(LeagueSeasonFormat.fitsCurrentSeason(41))
    }

    @Test
    fun detailedCompetitionAliasesRemainDivisionAwareBeyondFourthLevel() {
        assertEquals(setOf("SERIE_A", "DIV_1"), LeagueSeasonFormat.acceptedDetailedCompetitionTypes(1))
        assertEquals(setOf("SERIE_D", "DIV_4"), LeagueSeasonFormat.acceptedDetailedCompetitionTypes(4))
        assertEquals(setOf("SERIE_D", "DIV_5"), LeagueSeasonFormat.acceptedDetailedCompetitionTypes(5))
        assertEquals(setOf("SERIE_D", "DIV_6"), LeagueSeasonFormat.acceptedDetailedCompetitionTypes(6))
    }
}
