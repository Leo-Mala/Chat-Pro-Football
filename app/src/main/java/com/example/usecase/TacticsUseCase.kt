package com.example.usecase

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
