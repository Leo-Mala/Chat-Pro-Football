package com.example.ui.screens

import com.example.data.ConmebolCompetitionSystem
import com.example.data.CupCompetitionSystem
import com.example.data.GameCalendar
import com.example.data.SuperMundialSystem
import org.junit.Assert.assertEquals
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
        assertEquals("Oitavas de Final", competitionPhaseTitle("WORLD_CUP", SuperMundialSystem.ROUND_OF_16_WEEK))
    }

    @Test
    fun legacyContinentalAndCupKeepCanonicalFinalWeeks() {
        assertTrue(isCompetitionFinalWeek("CONTINENTAL_T1", CupCompetitionSystem.CONTINENTAL_FINAL_WEEK))
        assertTrue(isCompetitionFinalWeek("CONTINENTAL_T2", CupCompetitionSystem.CONTINENTAL_FINAL_WEEK))
        assertTrue(isCompetitionFinalWeek("CONTINENTAL_T3", CupCompetitionSystem.CONTINENTAL_FINAL_WEEK))
        assertTrue(isCompetitionFinalWeek("COPA", CupCompetitionSystem.NATIONAL_CUP_FINAL_WEEK))
        assertFalse(isCompetitionFinalWeek("COPA", CupCompetitionSystem.NATIONAL_CUP_FINAL_WEEK + 1))
        assertEquals("Oitavas de Final", competitionPhaseTitle("CONTINENTAL_T1", 33, "UEFA"))
        assertEquals("🏆 GRANDE FINAL", competitionPhaseTitle("COPA", CupCompetitionSystem.NATIONAL_CUP_FINAL_WEEK))
    }

    @Test
    fun conmebolTierOneAndTwoFinishOnWeekFortyOne() {
        assertFalse(isCompetitionFinalWeek("CONTINENTAL_T1", 36, "CONMEBOL"))
        assertTrue(
            isCompetitionFinalWeek(
                "CONTINENTAL_T1",
                ConmebolCompetitionSystem.FINAL_WEEK,
                "CONMEBOL"
            )
        )
        assertTrue(
            isCompetitionFinalWeek(
                "CONTINENTAL_T2",
                ConmebolCompetitionSystem.FINAL_WEEK,
                "CONMEBOL"
            )
        )
        assertEquals(
            "Playoff das Oitavas — Jogo de Ida",
            competitionPhaseTitle(
                "CONTINENTAL_T2",
                ConmebolCompetitionSystem.SUD_PLAYOFF_LEG_1_WEEK,
                "CONMEBOL"
            )
        )
        assertEquals(
            "Oitavas de Final — Jogo de Volta",
            competitionPhaseTitle(
                "CONTINENTAL_T1",
                ConmebolCompetitionSystem.ROUND_OF_16_LEG_2_WEEK,
                "CONMEBOL"
            )
        )
        assertEquals(
            "🏆 GRANDE FINAL — Jogo Único",
            competitionPhaseTitle(
                "CONTINENTAL_T1",
                ConmebolCompetitionSystem.FINAL_WEEK,
                "CONMEBOL"
            )
        )
    }
}
