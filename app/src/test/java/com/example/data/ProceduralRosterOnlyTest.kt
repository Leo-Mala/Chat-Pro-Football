package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProceduralRosterOnlyTest {
    @Test
    fun `new rosters are deterministic fictional 30-player squads`() {
        val first = DefaultData.generateRosterForTeam(990001L, 88, "Real Madrid", "Espanha")
        val second = DefaultData.generateRosterForTeam(990001L, 88, "Real Madrid", "Espanha")
        assertEquals(30, first.size)
        assertEquals(first, second)
        assertEquals(30, first.map { it.id }.distinct().size)
        val legacyRealNames = setOf("Kylian Mbappé", "Vinícius Júnior", "Jude Bellingham")
        assertTrue(first.none { it.name in legacyRealNames })
    }
}
