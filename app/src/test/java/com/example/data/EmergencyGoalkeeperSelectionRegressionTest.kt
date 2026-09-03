package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyGoalkeeperSelectionRegressionTest {
    @Test
    fun `natural eligible goalkeeper is always preferred`() {
        val field = Player(id = 1L, teamId = 1L, name = "Linha", age = 25, position = "ZAG", force = 99, defense = 99, pace = 99)
        val goalkeeper = Player(id = 2L, teamId = 1L, name = "Goleiro", age = 29, position = "GOL", force = 70)
        assertEquals(goalkeeper.id, GameEngine.selectMatchGoalkeeper(listOf(field, goalkeeper))?.id)
    }

    @Test
    fun `suspended goalkeeper falls back to best deterministic emergency candidate`() {
        val suspendedGoalkeeper = Player(
            id = 10L, teamId = 1L, name = "Suspenso", age = 30, position = "GOL", force = 95,
            suspensionWeeksRemaining = 1
        )
        val arbitraryFirst = Player(
            id = 11L, teamId = 1L, name = "Primeiro", age = 25, position = "ATA", force = 90,
            defense = 20, pace = 40
        )
        val bestEmergency = Player(
            id = 12L, teamId = 1L, name = "Emergência", age = 25, position = "ZAG", force = 78,
            defense = 92, pace = 70
        )

        assertEquals(
            bestEmergency.id,
            GameEngine.selectMatchGoalkeeper(listOf(arbitraryFirst, bestEmergency, suspendedGoalkeeper))?.id
        )
        assertEquals(
            bestEmergency.id,
            GameEngine.selectMatchGoalkeeper(listOf(bestEmergency, arbitraryFirst, suspendedGoalkeeper))?.id
        )
    }
}
