package com.example.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionPhaseRulesTest {

    @Test
    fun worldCupIsFinalOnlyOnWeek40() {
        assertFalse(isCompetitionFinalWeek("WORLD", 36))
        assertFalse(isCompetitionFinalWeek("WORLD", 38))
        assertFalse(isCompetitionFinalWeek("WORLD_CUP", 39))
        assertTrue(isCompetitionFinalWeek("WORLD", 40))
        assertTrue(isCompetitionFinalWeek("WORLD_CUP", 40))
    }

    @Test
    fun continentalAndCupKeepTheirOwnFinalWeeks() {
        assertTrue(isCompetitionFinalWeek("CONTINENTAL_T1", 36))
        assertTrue(isCompetitionFinalWeek("LIBERTADORES", 36))
        assertTrue(isCompetitionFinalWeek("COPA", 35))
        assertFalse(isCompetitionFinalWeek("COPA", 36))
    }
}
