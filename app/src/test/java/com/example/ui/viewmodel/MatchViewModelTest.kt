package com.example.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.Team
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class MatchViewModelTest {

    @Test
    fun testLiveMatchEventData() {
        val event = LiveMatchEvent(
            minute = 25,
            description = "GOOOOOOL DA SELEÇÃO! Chute de fora da área!",
            isGoal = true,
            isCard = false,
            isInjury = false,
            isWoodwork = false,
            isRedCard = false,
            homeScore = 1,
            awayScore = 0
        )

        assertEquals(25, event.minute)
        assertTrue(event.isGoal)
        assertFalse(event.isCard)
        assertEquals(1, event.homeScore)
        assertEquals(0, event.awayScore)
    }

    @Test
    fun testMatchStatsCalculation() {
        val homeTeam = Team(id = 1, name = "Flamengo", city = "Rio de Janeiro", state = "RJ", division = 1, isPlayerControlled = true)
        val awayTeam = Team(id = 2, name = "Palmeiras", city = "São Paulo", state = "SP", division = 1, isPlayerControlled = false)

        val stats = MatchStats(
            homeShots = 12,
            awayShots = 8,
            homePossession = 55,
            awayPossession = 45,
            homeFouls = 10,
            awayFouls = 14,
            homeYellowCards = 2,
            awayYellowCards = 3,
            homeRedCards = 0,
            awayRedCards = 1
        )

        assertEquals(12, stats.homeShots)
        assertEquals(8, stats.awayShots)
        assertEquals(55, stats.homePossession)
        assertEquals(1, stats.awayRedCards)
    }
}
