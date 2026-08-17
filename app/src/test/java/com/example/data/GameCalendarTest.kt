package com.example.data

import com.example.usecase.FinanceUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

/** Checkpoint consolidado da Fase 9.6A: calendário, rollover e limites domésticos. */
class GameCalendarTest {

    @Test
    fun canonicalSeasonHasFortyEightWeeksAndSeparateDomesticRoundLimit() {
        assertEquals(48, GameCalendar.WEEKS_PER_SEASON)
        assertEquals(40, GameCalendar.MAX_DOMESTIC_LEAGUE_ROUNDS)
    }

    @Test
    fun advanceWeeks_keepsWeeks47And48InsideCurrentSeason() {
        assertEquals(Pair(2026, 47), GameCalendar.advanceWeeks(2026, 46, 1))
        assertEquals(Pair(2026, 48), GameCalendar.advanceWeeks(2026, 47, 1))
    }

    @Test
    fun advanceWeeks_rollsOnlyAfterWeek48() {
        assertEquals(Pair(2027, 1), GameCalendar.advanceWeeks(2026, 48, 1))
        assertEquals(Pair(2027, 2), GameCalendar.advanceWeeks(2026, 47, 3))
    }

    @Test
    fun financeDueDates_useSameCanonicalCalendar() {
        assertEquals(Pair(2026, 48), FinanceUseCase.calcNextDueDate(2026, 47, 1))
        assertEquals(Pair(2027, 1), FinanceUseCase.calcNextDueDate(2026, 48, 1))
    }

    @Test
    fun clubWorldCup_metadata_spansActualTournamentWeeks() {
        val competition = requireNotNull(GlobalFootballSystem.getCompetitionByCode("WORLD_CUP"))
        assertEquals(SuperMundialSystem.GROUP_WEEK_1, competition.startWeek)
        assertEquals(GameCalendar.WEEKS_PER_SEASON, competition.endWeek)
    }
}
