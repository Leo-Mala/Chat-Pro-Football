package com.example.ui.viewmodel

import com.example.data.Fixture
import com.example.data.GameEngine
import com.example.data.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchStatsRulesTest {

    private fun player(id: Long, teamId: Long) = Player(
        id = id,
        teamId = teamId,
        name = "Jogador $id",
        age = 24,
        position = if (id % 11L == 0L) "GOL" else "MEI",
        force = 70
    )

    @Test
    fun startersCountAppearanceEvenWithoutAnyMatchEvent() {
        val home = (1L..11L).map { player(it, 1L) }
        val away = (101L..111L).map { player(it, 2L) }

        val appearances = collectAppearancePlayerIds(home, away, emptyList())

        assertEquals(22, appearances.size)
        assertTrue(home.all { it.id in appearances })
        assertTrue(away.all { it.id in appearances })
    }

    @Test
    fun substituteWhoEntersCountsAppearance() {
        val home = (1L..11L).map { player(it, 1L) }
        val substitute = player(50L, 1L)
        val substitution = GameEngine.MatchEventDetail(
            minute = 65,
            type = "SUBSTITUTION",
            teamId = 1L,
            description = "Entra reserva",
            isHomeEvent = true,
            playerId = substitute.id
        )

        val appearances = collectAppearancePlayerIds(home, emptyList(), listOf(substitution))

        assertTrue(substitute.id in appearances)
        assertEquals(12, appearances.size)
    }

    @Test
    fun eventAloneDoesNotCreateAppearanceForNonParticipant() {
        val home = (1L..11L).map { player(it, 1L) }
        val unrelatedEvent = GameEngine.MatchEventDetail(
            minute = 10,
            type = "GOAL",
            teamId = 1L,
            description = "Evento inconsistente de teste",
            isHomeEvent = true,
            scorerId = 999L,
            playerId = 999L
        )

        val appearances = collectAppearancePlayerIds(home, emptyList(), listOf(unrelatedEvent))

        assertFalse(999L in appearances)
    }

    @Test
    fun duplicateParticipationIsCountedOnlyOnce() {
        val starter = player(1L, 1L)
        val substitution = GameEngine.MatchEventDetail(
            minute = 70,
            type = "SUBSTITUTION",
            teamId = 1L,
            description = "Duplicado propositalmente",
            isHomeEvent = true,
            playerId = starter.id
        )

        val appearances = collectAppearancePlayerIds(listOf(starter), emptyList(), listOf(substitution))

        assertEquals(setOf(1L), appearances)
    }

    @Test
    fun playedLeagueMatchDoesNotHidePendingCupMatchInSameWeek() {
        val userTeamId = 1L
        val fixtures = listOf(
            Fixture(
                id = 1L,
                season = 2026,
                week = 31,
                homeTeamId = userTeamId,
                awayTeamId = 2L,
                competitionType = "SERIE_A",
                homeScore = 2,
                awayScore = 0,
                isPlayed = true
            ),
            Fixture(
                id = 2L,
                season = 2026,
                week = 31,
                homeTeamId = 3L,
                awayTeamId = userTeamId,
                competitionType = "COPA",
                isPlayed = false
            )
        )

        assertTrue(hasPendingUserFixtures(fixtures, userTeamId))
    }

    @Test
    fun weekCanCloseOnlyAfterEveryUserFixtureIsPlayed() {
        val userTeamId = 1L
        val fixtures = listOf(
            Fixture(
                id = 1L,
                season = 2026,
                week = 31,
                homeTeamId = userTeamId,
                awayTeamId = 2L,
                competitionType = "SERIE_A",
                homeScore = 2,
                awayScore = 0,
                isPlayed = true
            ),
            Fixture(
                id = 2L,
                season = 2026,
                week = 31,
                homeTeamId = 3L,
                awayTeamId = userTeamId,
                competitionType = "COPA",
                homeScore = 0,
                awayScore = 1,
                isPlayed = true
            )
        )

        assertFalse(hasPendingUserFixtures(fixtures, userTeamId))
    }
}
