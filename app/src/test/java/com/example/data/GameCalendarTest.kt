package com.example.data

import com.example.usecase.FinanceUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

class GameCalendarTest {

    @Test
    fun advanceWeeks_keepsWeeks39And40InsideCurrentSeason() {
        assertEquals(Pair(2026, 39), GameCalendar.advanceWeeks(2026, 38, 1))
        assertEquals(Pair(2026, 40), GameCalendar.advanceWeeks(2026, 39, 1))
    }

    @Test
    fun advanceWeeks_rollsOnlyAfterWeek40() {
        assertEquals(Pair(2027, 1), GameCalendar.advanceWeeks(2026, 40, 1))
        assertEquals(Pair(2027, 2), GameCalendar.advanceWeeks(2026, 39, 3))
    }

    @Test
    fun financeDueDates_useSameCanonicalCalendar() {
        assertEquals(Pair(2026, 39), FinanceUseCase.calcNextDueDate(2026, 38, 1))
        assertEquals(Pair(2026, 40), FinanceUseCase.calcNextDueDate(2026, 39, 1))
        assertEquals(Pair(2027, 1), FinanceUseCase.calcNextDueDate(2026, 40, 1))
    }

    @Test
    fun clubWorldCup_metadata_spansActualTournamentWeeks() {
        val competition = requireNotNull(GlobalFootballSystem.getCompetitionByCode("WORLD_CUP"))
        assertEquals(34, competition.startWeek)
        assertEquals(GameCalendar.WEEKS_PER_SEASON, competition.endWeek)
    }
}
