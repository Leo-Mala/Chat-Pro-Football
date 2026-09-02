from pathlib import Path

TACTICS_USE_CASE = '''package com.example.usecase

import com.example.data.Player

class TacticsUseCase {

    fun getFormationRoles(formation: String): List<String> {
        return when (formation) {
            "4-4-2" -> listOf("LAT", "ZAG", "ZAG", "LAT", "MEI", "VOL", "VOL", "MEI", "ATA", "ATA")
            "4-4-1-1" -> listOf("LAT", "ZAG", "ZAG", "LAT", "MEI", "VOL", "VOL", "MEI", "MEI", "ATA")
            "4-5-1" -> listOf("LAT", "ZAG", "ZAG", "LAT", "MEI", "VOL", "VOL", "VOL", "MEI", "ATA")
            "4-3-3" -> listOf("LAT", "ZAG", "ZAG", "LAT", "VOL", "VOL", "VOL", "ATA", "ATA", "ATA")
            "4-3-2-1" -> listOf("LAT", "ZAG", "ZAG", "LAT", "VOL", "VOL", "VOL", "MEI", "MEI", "ATA")
            "4-1-3-2" -> listOf("LAT", "ZAG", "ZAG", "LAT", "VOL", "MEI", "MEI", "MEI", "ATA", "ATA")
            "5-4-1" -> listOf("LAT", "ZAG", "ZAG", "ZAG", "LAT", "MEI", "VOL", "VOL", "MEI", "ATA")
            "4-1-2-1-2 Diamond" -> listOf("LAT", "ZAG", "ZAG", "LAT", "VOL", "VOL", "VOL", "MEI", "ATA", "ATA")
            "3-5-2" -> listOf("ZAG", "ZAG", "ZAG", "LAT", "VOL", "VOL", "VOL", "LAT", "ATA", "ATA")
            "5-3-2" -> listOf("LAT", "ZAG", "ZAG", "ZAG", "LAT", "VOL", "VOL", "VOL", "ATA", "ATA")
            "4-2-3-1" -> listOf("LAT", "ZAG", "ZAG", "LAT", "VOL", "VOL", "MEI", "MEI", "MEI", "ATA")
            "3-4-3" -> listOf("ZAG", "ZAG", "ZAG", "MEI", "VOL", "VOL", "MEI", "ATA", "ATA", "ATA")
            "3-2-4-1" -> listOf("ZAG", "ZAG", "ZAG", "VOL", "VOL", "MEI", "MEI", "MEI", "MEI", "ATA")
            "3-2-5", "3-2-5 (W-M)" -> listOf("ZAG", "ZAG", "ZAG", "VOL", "VOL", "MEI", "MEI", "ATA", "ATA", "ATA")
            "2-3-2-3" -> listOf("ZAG", "ZAG", "LAT", "VOL", "LAT", "MEI", "MEI", "ATA", "ATA", "ATA")
            "4-2-4" -> listOf("LAT", "ZAG", "ZAG", "LAT", "VOL", "VOL", "ATA", "ATA", "ATA", "ATA")
            else -> listOf("LAT", "ZAG", "ZAG", "LAT", "MEI", "VOL", "VOL", "MEI", "ATA", "ATA")
        }
    }

    /**
     * Builds the automatic XI while preserving formation sectors. Natural-position players are
     * reserved first for every slot. Only unresolved slots may borrow a compatible position from
     * the same tactical sector before the generic emergency fallback is used.
     */
    fun selectAutoLineup(players: List<Player>, formation: String): List<Player> {
        val eligible = players.filter {
            it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0
        }
        if (eligible.isEmpty()) return emptyList()

        val selected = mutableListOf<Player>()
        val selectedIds = mutableSetOf<Long>()
        val roles = getFormationRoles(formation)

        bestPlayer(eligible.filter { it.position == "GOL" })?.let {
            selected += it
            selectedIds += it.id
        }

        val unfilledRoles = mutableListOf<String>()

        // Pass 1: reserve every natural-position player before borrowing another position.
        for (role in roles) {
            val exact = bestPlayer(
                eligible.filter { it.id !in selectedIds && it.position == role }
            )
            if (exact != null) {
                selected += exact
                selectedIds += exact.id
            } else {
                unfilledRoles += role
            }
        }

        // Pass 2: fill unresolved slots with the nearest compatible position in the same sector.
        for (role in unfilledRoles) {
            val candidate = compatibleFallbackPositions(role).asSequence()
                .mapNotNull { fallbackPosition ->
                    bestPlayer(
                        eligible.filter {
                            it.id !in selectedIds && it.position == fallbackPosition
                        }
                    )
                }
                .firstOrNull()

            if (candidate != null) {
                selected += candidate
                selectedIds += candidate.id
            }
        }

        // Emergency fallback keeps the historical guarantee of producing 11 players when the
        // roster genuinely lacks enough compatible positions.
        val remainingField = eligible
            .filter { it.id !in selectedIds && it.position != "GOL" }
            .sortedWith(playerStrengthComparator)
        for (player in remainingField) {
            if (selected.size >= 11) break
            selected += player
            selectedIds += player.id
        }

        val remainingAny = eligible
            .filter { it.id !in selectedIds }
            .sortedWith(playerStrengthComparator)
        for (player in remainingAny) {
            if (selected.size >= 11) break
            selected += player
            selectedIds += player.id
        }

        return selected.take(11)
    }

    private fun compatibleFallbackPositions(role: String): List<String> = when (role) {
        "VOL" -> listOf("MEI")
        "MEI" -> listOf("VOL")
        "LAT" -> listOf("ZAG", "VOL")
        "ZAG" -> listOf("LAT", "VOL")
        "ATA" -> listOf("MEI")
        else -> emptyList()
    }

    private fun bestPlayer(players: List<Player>): Player? =
        players.sortedWith(playerStrengthComparator).firstOrNull()

    private val playerStrengthComparator =
        compareByDescending<Player> { effectiveStrength(it) }.thenBy { it.id }

    private fun effectiveStrength(player: Player): Double =
        player.force.toDouble() * (player.energy.toDouble() / 100.0)
}
'''

TEST = '''package com.example.usecase

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
'''


def main() -> None:
    tactics_path = Path('app/src/main/java/com/example/usecase/TacticsUseCase.kt')
    tactics_path.write_text(TACTICS_USE_CASE, encoding='utf-8')

    vm_path = Path('app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt')
    vm = vm_path.read_text(encoding='utf-8')
    start_marker = '            val selectedStarters = mutableSetOf<Player>()\n'
    end_marker = '            // 5. Update database: all unselected players in roster are bench (isStarter = false), selected are starters\n'
    if vm.count(start_marker) != 1:
        raise SystemExit(f'Expected exactly one auto-lineup start marker, found {vm.count(start_marker)}')
    if vm.count(end_marker) != 1:
        raise SystemExit(f'Expected exactly one auto-lineup update marker, found {vm.count(end_marker)}')
    start = vm.index(start_marker)
    end = vm.index(end_marker, start)
    replacement = '            val selectedStarters = tacticsUseCase.selectAutoLineup(available, formation).toSet()\n\n'
    vm_path.write_text(vm[:start] + replacement + vm[end:], encoding='utf-8')

    test_path = Path('app/src/test/java/com/example/usecase/TacticsUseCaseAutoLineupTest.kt')
    test_path.write_text(TEST, encoding='utf-8')


if __name__ == '__main__':
    main()
