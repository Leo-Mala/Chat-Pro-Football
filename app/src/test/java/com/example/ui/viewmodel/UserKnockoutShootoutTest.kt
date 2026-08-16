package com.example.ui.viewmodel

import com.example.data.Fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserKnockoutShootoutTest {

    @Test
    fun drawnCupFixtureReceivesDeterministicNonTiedShootout() {
        val fixture = Fixture(
            id = 99L,
            season = 2026,
            week = 35,
            homeTeamId = 1L,
            awayTeamId = 2L,
            homeScore = 1,
            awayScore = 1,
            competitionType = "COPA",
            isPlayed = true
        )

        val first = applyUserKnockoutShootout(fixture)
        val second = applyUserKnockoutShootout(fixture)

        assertEquals(first.homePenalties, second.homePenalties)
        assertEquals(first.awayPenalties, second.awayPenalties)
        assertTrue(first.homePenalties != null)
        assertTrue(first.awayPenalties != null)
        assertTrue(first.homePenalties != first.awayPenalties)
    }

    @Test
    fun leagueDrawDoesNotReceiveShootout() {
        val fixture = Fixture(
            id = 100L,
            season = 2026,
            week = 20,
            homeTeamId = 1L,
            awayTeamId = 2L,
            homeScore = 0,
            awayScore = 0,
            competitionType = "SERIE_A",
            isPlayed = true
        )

        val result = applyUserKnockoutShootout(fixture)

        assertNull(result.homePenalties)
        assertNull(result.awayPenalties)
    }

    @Test
    fun existingValidShootoutIsPreserved() {
        val fixture = Fixture(
            id = 101L,
            season = 2026,
            week = 36,
            homeTeamId = 1L,
            awayTeamId = 2L,
            homeScore = 2,
            awayScore = 2,
            homePenalties = 5,
            awayPenalties = 4,
            competitionType = "CONTINENTAL_T1",
            isPlayed = true
        )

        val result = applyUserKnockoutShootout(fixture)

        assertEquals(5, result.homePenalties)
        assertEquals(4, result.awayPenalties)
    }
}
