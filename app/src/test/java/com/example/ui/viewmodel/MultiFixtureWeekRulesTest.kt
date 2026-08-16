package com.example.ui.viewmodel

import com.example.data.Fixture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiFixtureWeekRulesTest {

    @Test
    fun week_stays_open_while_second_user_fixture_is_unplayed() {
        val fixtures = listOf(
            Fixture(
                id = 1L,
                season = 2026,
                week = 31,
                homeTeamId = 10L,
                awayTeamId = 20L,
                competitionType = "SERIE_A",
                homeScore = 2,
                awayScore = 1,
                isPlayed = true
            ),
            Fixture(
                id = 2L,
                season = 2026,
                week = 31,
                homeTeamId = 30L,
                awayTeamId = 10L,
                competitionType = "COPA",
                isPlayed = false
            )
        )

        assertTrue(hasUnplayedUserFixture(fixtures, playerTeamId = 10L))
    }

    @Test
    fun week_can_close_after_all_user_fixtures_are_played() {
        val fixtures = listOf(
            Fixture(
                id = 1L,
                season = 2026,
                week = 31,
                homeTeamId = 10L,
                awayTeamId = 20L,
                competitionType = "SERIE_A",
                homeScore = 2,
                awayScore = 1,
                isPlayed = true
            ),
            Fixture(
                id = 2L,
                season = 2026,
                week = 31,
                homeTeamId = 30L,
                awayTeamId = 10L,
                competitionType = "COPA",
                homeScore = 0,
                awayScore = 1,
                isPlayed = true
            ),
            Fixture(
                id = 3L,
                season = 2026,
                week = 31,
                homeTeamId = 40L,
                awayTeamId = 50L,
                competitionType = "SERIE_A",
                isPlayed = false
            )
        )

        assertFalse(hasUnplayedUserFixture(fixtures, playerTeamId = 10L))
    }
}
