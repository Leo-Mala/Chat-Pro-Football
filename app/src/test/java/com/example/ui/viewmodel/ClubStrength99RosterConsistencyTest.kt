package com.example.ui.viewmodel

import com.example.data.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class ClubStrength99RosterConsistencyTest {
    @Test fun `editor team strength 99 applies 99 to every roster member`() {
        val roster = (1L..30L).map { id ->
            Player(id = id, teamId = 1L, name = "P$id", age = 24, position = "MEI", force = (60 + id % 30).toInt())
        }
        val updated = applyEditedTeamStrength(roster, 99)
        assertEquals(roster.map { it.id }, updated.map { it.id })
        assertEquals(setOf(99), updated.map { it.force }.toSet())
    }
}
