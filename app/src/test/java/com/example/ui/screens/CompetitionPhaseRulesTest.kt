package com.example.ui.screens

import com.example.data.GameCalendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionPhaseRulesTest {

    @Test
    fun worldCupIsFinalOnlyOnCanonicalFinalWeek() {
        assertFalse(isCompetitionFinalWeek("WORLD", 40))
        assertFalse(isCompetitionFinalWeek("WORLD_CUP", GameCalendar.WEEKS_PER_SEASON - 1))
        assertTrue(isCompetitionFinalWeek("WORLD", GameCalendar.WEEKS_PER_SEASON))
        assertTrue(isCompetitionFinalWeek("WORLD_CUP", GameCalendar.WEEKS_PER_SEASON))
    }

    @Test
    fun continentalAndCupKeepTheirOwnFinalWeeks() {
        assertTrue(isCompetitionFinalWeek("CONTINENTAL_T1", 36))
        assertTrue(isCompetitionFinalWeek("CONTINENTAL_T2", 36))
        assertTrue(isCompetitionFinalWeek("CONTINENTAL_T3", 36))
        assertTrue(isCompetitionFinalWeek("LIBERTADORES", 36))
        assertTrue(isCompetitionFinalWeek("COPA", 35))
        assertFalse(isCompetitionFinalWeek("COPA", 36))
    }
}
