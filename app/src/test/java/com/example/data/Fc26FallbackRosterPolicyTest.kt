package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Fc26FallbackRosterPolicyTest {

    @Test
    fun `canonical procedural roster is reduced to balanced deterministic twenty without mutating players`() {
        val full = DefaultData.generateRosterForTeam(
            teamId = 88001L,
            teamRating = 67,
            teamName = "Fallback Town",
            country = "Inglaterra"
        )

        assertEquals(30, full.size)

        val first = Fc26FallbackRosterPolicy.select(full)
        val second = Fc26FallbackRosterPolicy.select(full)

        assertEquals(Fc26FallbackRosterPolicy.TARGET_SIZE, first.size)
        assertEquals(first, second)
        assertEquals(first.size, first.map { it.id }.distinct().size)
        assertTrue(first.all { retained -> full.single { it.id == retained.id } == retained })

        val byPosition = first.groupingBy { it.position }.eachCount()
        assertEquals(2, byPosition["GOL"])
        assertEquals(4, byPosition["ZAG"])
        assertEquals(3, byPosition["LAT"])
        assertEquals(3, byPosition["VOL"])
        assertEquals(4, byPosition["MEI"])
        assertEquals(4, byPosition["ATA"])

        // The supported 3-2-4-1 shape must be fillable with four natural midfielders.
        assertTrue((byPosition["MEI"] ?: 0) >= 4)
    }

    @Test
    fun `small custom fallback is preserved exactly`() {
        val small = listOf(
            Player(id = 1L, teamId = 99L, name = "A", age = 24, position = "GOL", force = 50),
            Player(id = 2L, teamId = 99L, name = "B", age = 25, position = "MEI", force = 51)
        )

        assertEquals(small, Fc26FallbackRosterPolicy.select(small))
    }
}
