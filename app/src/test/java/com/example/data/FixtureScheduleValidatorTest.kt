package com.example.data

import org.junit.Assert.assertThrows
import org.junit.Test

class FixtureScheduleValidatorTest {

    @Test
    fun sameClubMayPlayMidweekAndWeekendInSameWeek() {
        FixtureScheduleValidator.requireValid(
            listOf(
                fixture(week = 10, slot = MatchSlot.MIDWEEK, home = 1L, away = 2L, competition = "COPA"),
                fixture(week = 10, slot = MatchSlot.WEEKEND, home = 1L, away = 3L, competition = "SERIE_A")
            )
        )
    }

    @Test
    fun sameClubCannotPlayTwiceInSameSlot() {
        assertThrows(IllegalArgumentException::class.java) {
            FixtureScheduleValidator.requireValid(
                listOf(
                    fixture(week = 10, slot = MatchSlot.MIDWEEK, home = 1L, away = 2L, competition = "COPA"),
                    fixture(week = 10, slot = MatchSlot.MIDWEEK, home = 1L, away = 3L, competition = "CONTINENTAL_T1")
                )
            )
        }
    }

    @Test
    fun fixtureOutsideFortyEightWeekSeasonIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            FixtureScheduleValidator.requireValid(
                listOf(
                    fixture(
                        week = GameCalendar.WEEKS_PER_SEASON + 1,
                        slot = MatchSlot.WEEKEND,
                        home = 1L,
                        away = 2L,
                        competition = "SERIE_A"
                    )
                )
            )
        }
    }

    @Test
    fun progressionCannotAddConflictAgainstPersistedSchedule() {
        val existing = listOf(
            fixture(week = 20, slot = MatchSlot.MIDWEEK, home = 7L, away = 8L, competition = "COPA")
        )
        val addition = listOf(
            fixture(week = 20, slot = MatchSlot.MIDWEEK, home = 7L, away = 9L, competition = "CONTINENTAL_T1")
        )

        assertThrows(IllegalArgumentException::class.java) {
            FixtureScheduleValidator.requireCanAdd(existing, addition)
        }
    }

    @Test
    fun exactDuplicateFixtureIsRejected() {
        val duplicate = fixture(
            week = 12,
            slot = MatchSlot.WEEKEND,
            home = 1L,
            away = 2L,
            competition = "SERIE_A"
        )
        assertThrows(IllegalArgumentException::class.java) {
            FixtureScheduleValidator.requireValid(listOf(duplicate, duplicate))
        }
    }

    private fun fixture(
        week: Int,
        slot: MatchSlot,
        home: Long,
        away: Long,
        competition: String
    ) = Fixture(
        season = 2026,
        week = week,
        matchSlot = slot,
        homeTeamId = home,
        awayTeamId = away,
        competitionType = competition
    )
}
