package com.example.usecase

import com.example.data.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TacticsUseCaseAutoLineupTest {
    private val useCase = TacticsUseCase()

    @Test
    fun fourFourTwo_withoutNaturalVolantes_usesExtraMidfieldersBeforeExtraCenterBacks() {
        val lineup = useCase.selectAutoLineup(rosterWithNoVolantes(), "4-4-2")

        assertEquals(11, lineup.size)
        assertEquals(1, lineup.count { it.position == "GOL" })
        assertEquals(2, lineup.count { it.position == "LAT" })
        assertEquals(2, lineup.count { it.position == "ZAG" })
        assertEquals(4, lineup.count { it.position == "MEI" })
        assertEquals(2, lineup.count { it.position == "ATA" })
    }

    @Test
    fun fourThreeThree_withoutNaturalVolantes_usesThreeMidfieldersInsteadOfExtraDefenders() {
        val lineup = useCase.selectAutoLineup(rosterWithNoVolantes(), "4-3-3")

        assertEquals(11, lineup.size)
        assertEquals(1, lineup.count { it.position == "GOL" })
        assertEquals(2, lineup.count { it.position == "LAT" })
        assertEquals(2, lineup.count { it.position == "ZAG" })
        assertEquals(3, lineup.count { it.position == "MEI" })
        assertEquals(3, lineup.count { it.position == "ATA" })
    }

    @Test
    fun fourFourTwo_withNaturalVolantes_preservesExactMidfieldRoles() {
        val roster = listOf(
            player(1, "GOL", 90),
            player(2, "LAT", 90), player(3, "LAT", 89),
            player(4, "ZAG", 99), player(5, "ZAG", 98), player(6, "ZAG", 97),
            player(7, "VOL", 88), player(8, "VOL", 87),
            player(9, "MEI", 86), player(10, "MEI", 85),
            player(11, "ATA", 94), player(12, "ATA", 93), player(13, "ATA", 92)
        )

        val lineup = useCase.selectAutoLineup(roster, "4-4-2")

        assertEquals(11, lineup.size)
        assertEquals(2, lineup.count { it.position == "VOL" })
        assertEquals(2, lineup.count { it.position == "MEI" })
        assertTrue(lineup.none { it.id == 6L })
    }

    private fun rosterWithNoVolantes(): List<Player> = listOf(
        player(1, "GOL", 99),
        player(2, "LAT", 99), player(3, "LAT", 99),
        player(4, "ZAG", 99), player(5, "ZAG", 99),
        player(6, "ZAG", 99), player(7, "ZAG", 99),
        player(8, "MEI", 95), player(9, "MEI", 94), player(10, "MEI", 93),
        player(11, "MEI", 92), player(12, "MEI", 91),
        player(13, "ATA", 99), player(14, "ATA", 98), player(15, "ATA", 97)
    )

    private fun player(id: Long, position: String, force: Int) = Player(
        id = id,
        teamId = 10L,
        name = "Player $id",
        age = 24,
        position = position,
        force = force
    )
}
