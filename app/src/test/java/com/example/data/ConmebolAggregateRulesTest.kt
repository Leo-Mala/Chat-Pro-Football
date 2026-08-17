package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConmebolAggregateRulesTest {

    @Test
    fun `individual aggregate leg draw has no penalties before aggregate is known`() {
        val leg = Fixture(
            id = 1L,
            season = 2026,
            week = ConmebolCompetitionSystem.ROUND_OF_16_LEG_1_WEEK,
            matchSlot = MatchSlot.MIDWEEK,
            homeTeamId = 10L,
            awayTeamId = 20L,
            competitionType = ConmebolCompetitionSystem.LIBERTADORES,
            homeScore = 1,
            awayScore = 1,
            isPlayed = true
        )

        val decided = CompetitionRules.ensureKnockoutDecision(leg)

        assertNull(decided.homePenalties)
        assertNull(decided.awayPenalties)
    }

    @Test
    fun `aggregate tie is decided deterministically on second leg penalties`() {
        val firstLeg = Fixture(
            id = 10L,
            season = 2026,
            week = ConmebolCompetitionSystem.ROUND_OF_16_LEG_1_WEEK,
            matchSlot = MatchSlot.MIDWEEK,
            homeTeamId = 100L,
            awayTeamId = 200L,
            competitionType = ConmebolCompetitionSystem.LIBERTADORES,
            homeScore = 1,
            awayScore = 0,
            isPlayed = true
        )
        val secondLeg = Fixture(
            id = 11L,
            season = 2026,
            week = ConmebolCompetitionSystem.ROUND_OF_16_LEG_2_WEEK,
            matchSlot = MatchSlot.MIDWEEK,
            homeTeamId = 200L,
            awayTeamId = 100L,
            competitionType = ConmebolCompetitionSystem.LIBERTADORES,
            homeScore = 1,
            awayScore = 0,
            isPlayed = true
        )

        val first = ConmebolCompetitionSystem.ensureAggregatePenaltyDecision(firstLeg, secondLeg)
        val second = ConmebolCompetitionSystem.ensureAggregatePenaltyDecision(firstLeg, secondLeg)

        assertEquals(first.homePenalties, second.homePenalties)
        assertEquals(first.awayPenalties, second.awayPenalties)
        assertNotEquals(first.homePenalties, first.awayPenalties)
    }
}
