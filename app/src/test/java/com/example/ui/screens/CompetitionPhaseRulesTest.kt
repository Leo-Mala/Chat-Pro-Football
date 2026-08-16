package com.example.ui.screens

import org.junit.Assert.assertEquals
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
        assertTrue(isCompetitionFinalWeek("CONTINENTAL_T2", 36))
        assertTrue(isCompetitionFinalWeek("CONTINENTAL_T3", 36))
        assertTrue(isCompetitionFinalWeek("LIBERTADORES", 36))
        assertTrue(isCompetitionFinalWeek("COPA", 35))
        assertFalse(isCompetitionFinalWeek("COPA", 36))
        assertFalse(isCompetitionFinalWeek("CONTINENTAL_T3", 35))
    }

    @Test
    fun phaseTitlesMatchTheGeneratedSingleLegCalendar() {
        assertEquals("16 avos de Final", competitionPhaseTitle("COPA", 31))
        assertEquals("Oitavas de Final", competitionPhaseTitle("COPA", 32))
        assertEquals("Quartas de Final", competitionPhaseTitle("COPA", 33))
        assertEquals("Semifinais", competitionPhaseTitle("COPA", 34))
        assertEquals("🏆 GRANDE FINAL (Jogo Único)", competitionPhaseTitle("COPA", 35))

        assertEquals("Oitavas de Final", competitionPhaseTitle("CONTINENTAL_T1", 32))
        assertEquals("Quartas de Final", competitionPhaseTitle("CONTINENTAL_T2", 33))
        assertEquals("Semifinais", competitionPhaseTitle("CONTINENTAL_T3", 34))
        assertEquals("🏆 GRANDE FINAL (Jogo Único)", competitionPhaseTitle("CONTINENTAL_T1", 36))
    }
}
