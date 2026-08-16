package com.example.ui.components.tactics

import com.example.data.Player

data class FormationSlot(
    val name: String,
    val role: String,
    val x: Float, // 0 to 1 (horizontal)
    val y: Float  // 0 to 1 (vertical)
)

fun getFormationSlots(formation: String): List<FormationSlot> {
    return when (formation) {
        "4-4-2" -> listOf(
            FormationSlot("LE", "LAT", 0.15f, 0.70f),
            FormationSlot("ZAG", "ZAG", 0.38f, 0.72f),
            FormationSlot("ZAG", "ZAG", 0.62f, 0.72f),
            FormationSlot("LD", "LAT", 0.85f, 0.70f),
            FormationSlot("ME", "MEI", 0.15f, 0.45f),
            FormationSlot("MC", "VOL", 0.38f, 0.48f),
            FormationSlot("MC", "VOL", 0.62f, 0.48f),
            FormationSlot("MD", "MEI", 0.85f, 0.45f),
            FormationSlot("ATA", "ATA", 0.35f, 0.20f),
            FormationSlot("ATA", "ATA", 0.65f, 0.20f)
        )
        "4-4-1-1" -> listOf(
            FormationSlot("LE", "LAT", 0.15f, 0.70f),
            FormationSlot("ZAG", "ZAG", 0.38f, 0.72f),
            FormationSlot("ZAG", "ZAG", 0.62f, 0.72f),
            FormationSlot("LD", "LAT", 0.85f, 0.70f),
            FormationSlot("ME", "MEI", 0.15f, 0.45f),
            FormationSlot("MC", "VOL", 0.38f, 0.48f),
            FormationSlot("MC", "VOL", 0.62f, 0.48f),
            FormationSlot("MD", "MEI", 0.85f, 0.45f),
            FormationSlot("MEI", "MEI", 0.50f, 0.28f),
            FormationSlot("ATA", "ATA", 0.50f, 0.18f)
        )
        "4-5-1" -> listOf(
            FormationSlot("LE", "LAT", 0.15f, 0.70f),
            FormationSlot("ZAG", "ZAG", 0.38f, 0.72f),
            FormationSlot("ZAG", "ZAG", 0.62f, 0.72f),
            FormationSlot("LD", "LAT", 0.85f, 0.70f),
            FormationSlot("ME", "MEI", 0.15f, 0.45f),
            FormationSlot("MC", "VOL", 0.32f, 0.48f),
            FormationSlot("MC", "VOL", 0.50f, 0.50f),
            FormationSlot("MC", "VOL", 0.68f, 0.48f),
            FormationSlot("MD", "MEI", 0.85f, 0.45f),
            FormationSlot("ATA", "ATA", 0.50f, 0.20f)
        )
        "4-3-3" -> listOf(
            FormationSlot("LE", "LAT", 0.15f, 0.70f),
            FormationSlot("ZAG", "ZAG", 0.38f, 0.72f),
            FormationSlot("ZAG", "ZAG", 0.62f, 0.72f),
            FormationSlot("LD", "LAT", 0.85f, 0.70f),
            FormationSlot("MC", "VOL", 0.28f, 0.48f),
            FormationSlot("MC", "VOL", 0.50f, 0.52f),
            FormationSlot("MC", "VOL", 0.72f, 0.48f),
            FormationSlot("PE", "ATA", 0.20f, 0.22f),
            FormationSlot("ATA", "ATA", 0.50f, 0.18f),
            FormationSlot("PD", "ATA", 0.80f, 0.22f)
        )
        "4-3-2-1" -> listOf(
            FormationSlot("LE", "LAT", 0.15f, 0.70f),
            FormationSlot("ZAG", "ZAG", 0.38f, 0.72f),
            FormationSlot("ZAG", "ZAG", 0.62f, 0.72f),
            FormationSlot("LD", "LAT", 0.85f, 0.70f),
            FormationSlot("MC", "VOL", 0.28f, 0.52f),
            FormationSlot("MC", "VOL", 0.50f, 0.55f),
            FormationSlot("MC", "VOL", 0.72f, 0.52f),
            FormationSlot("MEI", "MEI", 0.35f, 0.32f),
            FormationSlot("MEI", "MEI", 0.65f, 0.32f),
            FormationSlot("ATA", "ATA", 0.50f, 0.18f)
        )
        "4-1-3-2" -> listOf(
            FormationSlot("LE", "LAT", 0.15f, 0.70f),
            FormationSlot("ZAG", "ZAG", 0.38f, 0.72f),
            FormationSlot("ZAG", "ZAG", 0.62f, 0.72f),
            FormationSlot("LD", "LAT", 0.85f, 0.70f),
            FormationSlot("VOL", "VOL", 0.50f, 0.58f),
            FormationSlot("ME", "MEI", 0.18f, 0.42f),
            FormationSlot("MC", "MEI", 0.50f, 0.40f),
            FormationSlot("MD", "MEI", 0.82f, 0.42f),
            FormationSlot("ATA", "ATA", 0.35f, 0.20f),
            FormationSlot("ATA", "ATA", 0.65f, 0.20f)
        )
        "5-4-1" -> listOf(
            FormationSlot("ALA", "LAT", 0.12f, 0.65f),
            FormationSlot("ZAG", "ZAG", 0.30f, 0.72f),
            FormationSlot("ZAG", "ZAG", 0.50f, 0.74f),
            FormationSlot("ZAG", "ZAG", 0.70f, 0.72f),
            FormationSlot("ALA", "LAT", 0.88f, 0.65f),
            FormationSlot("ME", "MEI", 0.20f, 0.45f),
            FormationSlot("MC", "VOL", 0.40f, 0.48f),
            FormationSlot("MC", "VOL", 0.60f, 0.48f),
            FormationSlot("MD", "MEI", 0.80f, 0.45f),
            FormationSlot("ATA", "ATA", 0.50f, 0.20f)
        )
        "4-1-2-1-2 Diamond" -> listOf(
            FormationSlot("LE", "LAT", 0.15f, 0.70f),
            FormationSlot("ZAG", "ZAG", 0.38f, 0.72f),
            FormationSlot("ZAG", "ZAG", 0.62f, 0.72f),
            FormationSlot("LD", "LAT", 0.85f, 0.70f),
            FormationSlot("VOL", "VOL", 0.50f, 0.58f),
            FormationSlot("MC", "VOL", 0.30f, 0.46f),
            FormationSlot("MC", "VOL", 0.70f, 0.46f),
            FormationSlot("MEI", "MEI", 0.50f, 0.32f),
            FormationSlot("ATA", "ATA", 0.35f, 0.18f),
            FormationSlot("ATA", "ATA", 0.65f, 0.18f)
        )
        "3-5-2" -> listOf(
            FormationSlot("ZAG", "ZAG", 0.25f, 0.72f),
            FormationSlot("ZAG", "ZAG", 0.50f, 0.75f),
            FormationSlot("ZAG", "ZAG", 0.75f, 0.72f),
            FormationSlot("ALA", "LAT", 0.12f, 0.50f),
            FormationSlot("MC", "VOL", 0.35f, 0.48f),
            FormationSlot("MC", "VOL", 0.50f, 0.52f),
            FormationSlot("MC", "VOL", 0.65f, 0.48f),
            FormationSlot("ALA", "LAT", 0.88f, 0.50f),
            FormationSlot("ATA", "ATA", 0.35f, 0.20f),
            FormationSlot("ATA", "ATA", 0.65f, 0.20f)
        )
        "5-3-2" -> listOf(
            FormationSlot("ALA", "LAT", 0.12f, 0.65f),
            FormationSlot("ZAG", "ZAG", 0.30f, 0.72f),
            FormationSlot("ZAG", "ZAG", 0.50f, 0.74f),
            FormationSlot("ZAG", "ZAG", 0.70f, 0.72f),
            FormationSlot("ALA", "LAT", 0.88f, 0.65f),
            FormationSlot("MC", "VOL", 0.30f, 0.48f),
            FormationSlot("MC", "VOL", 0.50f, 0.50f),
            FormationSlot("MC", "VOL", 0.70f, 0.48f),
            FormationSlot("ATA", "ATA", 0.35f, 0.20f),
            FormationSlot("ATA", "ATA", 0.65f, 0.20f)
        )
        "4-2-3-1" -> listOf(
            FormationSlot("LE", "LAT", 0.15f, 0.70f),
            FormationSlot("ZAG", "ZAG", 0.38f, 0.72f),
            FormationSlot("ZAG", "ZAG", 0.62f, 0.72f),
            FormationSlot("LD", "LAT", 0.85f, 0.70f),
            FormationSlot("VOL", "VOL", 0.35f, 0.58f),
            FormationSlot("VOL", "VOL", 0.65f, 0.58f),
            FormationSlot("PE", "MEI", 0.20f, 0.35f),
            FormationSlot("MEI", "MEI", 0.50f, 0.32f),
            FormationSlot("PD", "MEI", 0.80f, 0.35f),
            FormationSlot("ATA", "ATA", 0.50f, 0.18f)
        )
        "3-4-3" -> listOf(
            FormationSlot("ZAG", "ZAG", 0.25f, 0.72f),
            FormationSlot("ZAG", "ZAG", 0.50f, 0.75f),
            FormationSlot("ZAG", "ZAG", 0.75f, 0.72f),
            FormationSlot("ME", "MEI", 0.15f, 0.48f),
            FormationSlot("MC", "VOL", 0.38f, 0.50f),
            FormationSlot("MC", "VOL", 0.62f, 0.50f),
            FormationSlot("MD", "MEI", 0.85f, 0.48f),
            FormationSlot("PE", "ATA", 0.20f, 0.22f),
            FormationSlot("ATA", "ATA", 0.50f, 0.18f),
            FormationSlot("PD", "ATA", 0.80f, 0.22f)
        )
        "3-2-4-1" -> listOf(
            FormationSlot("ZAG", "ZAG", 0.25f, 0.72f),
            FormationSlot("ZAG", "ZAG", 0.50f, 0.75f),
            FormationSlot("ZAG", "ZAG", 0.75f, 0.72f),
            FormationSlot("VOL", "VOL", 0.35f, 0.58f),
            FormationSlot("VOL", "VOL", 0.65f, 0.58f),
            FormationSlot("ME", "MEI", 0.15f, 0.38f),
            FormationSlot("MEI", "MEI", 0.38f, 0.36f),
            FormationSlot("MEI", "MEI", 0.62f, 0.36f),
            FormationSlot("MD", "MEI", 0.85f, 0.38f),
            FormationSlot("ATA", "ATA", 0.50f, 0.18f)
        )
        "3-2-5", "3-2-5 (W-M)" -> listOf(
            FormationSlot("ZAG", "ZAG", 0.25f, 0.72f),
            FormationSlot("ZAG", "ZAG", 0.50f, 0.75f),
            FormationSlot("ZAG", "ZAG", 0.75f, 0.72f),
            FormationSlot("VOL", "VOL", 0.38f, 0.58f),
            FormationSlot("VOL", "VOL", 0.62f, 0.58f),
            FormationSlot("MEI", "MEI", 0.35f, 0.40f),
            FormationSlot("MEI", "MEI", 0.65f, 0.40f),
            FormationSlot("PE", "ATA", 0.15f, 0.22f),
            FormationSlot("ATA", "ATA", 0.50f, 0.18f),
            FormationSlot("PD", "ATA", 0.85f, 0.22f)
        )
        "2-3-2-3" -> listOf(
            FormationSlot("ZAG", "ZAG", 0.35f, 0.72f),
            FormationSlot("ZAG", "ZAG", 0.65f, 0.72f),
            FormationSlot("ME", "LAT", 0.22f, 0.58f),
            FormationSlot("VOL", "VOL", 0.50f, 0.60f),
            FormationSlot("MD", "LAT", 0.78f, 0.58f),
            FormationSlot("MC", "MEI", 0.35f, 0.42f),
            FormationSlot("MC", "MEI", 0.65f, 0.42f),
            FormationSlot("PE", "ATA", 0.15f, 0.22f),
            FormationSlot("ATA", "ATA", 0.50f, 0.18f),
            FormationSlot("PD", "ATA", 0.85f, 0.22f)
        )
        "4-2-4" -> listOf(
            FormationSlot("LE", "LAT", 0.15f, 0.70f),
            FormationSlot("ZAG", "ZAG", 0.38f, 0.72f),
            FormationSlot("ZAG", "ZAG", 0.62f, 0.72f),
            FormationSlot("LD", "LAT", 0.85f, 0.70f),
            FormationSlot("MC", "VOL", 0.38f, 0.48f),
            FormationSlot("MC", "VOL", 0.62f, 0.48f),
            FormationSlot("PE", "ATA", 0.15f, 0.22f),
            FormationSlot("ATA", "ATA", 0.38f, 0.18f),
            FormationSlot("ATA", "ATA", 0.62f, 0.18f),
            FormationSlot("PD", "ATA", 0.85f, 0.22f)
        )
        else -> emptyList()
    }
}

fun calculatePlayerSlotScore(player: Player, slot: FormationSlot): Double {
    val baseScore = player.force.toDouble()
    val matchScore = when (slot.role) {
        "ZAG" -> when (player.position) {
            "ZAG" -> 100.0
            "VOL" -> 50.0
            "LAT" -> 40.0
            else -> 10.0
        }
        "LAT" -> when (player.position) {
            "LAT" -> 100.0
            "ZAG" -> 40.0
            "VOL" -> 40.0
            "MEI" -> 30.0
            else -> 10.0
        }
        "VOL" -> when (player.position) {
            "VOL" -> 100.0
            "ZAG" -> 60.0
            "MEI" -> 50.0
            else -> 10.0
        }
        "MEI" -> when (player.position) {
            "MEI" -> 100.0
            "VOL" -> 60.0
            "ATA" -> 50.0
            "LAT" -> 40.0
            else -> 10.0
        }
        "ATA" -> when (player.position) {
            "ATA" -> 100.0
            "MEI" -> 60.0
            else -> 10.0
        }
        else -> 10.0
    }
    return baseScore + matchScore
}

fun mapPlayersToFormation(
    players: List<Player>,
    slots: List<FormationSlot>
): List<Pair<Player, FormationSlot>> {
    val unassignedPlayers = players.toMutableList()
    val assignments = mutableListOf<Pair<Player, FormationSlot>>()
    
    val sortedSlots = slots.sortedBy { slot ->
        when (slot.role) {
            "ATA", "ZAG" -> 1
            "GOL" -> 0
            else -> 2
        }
    }
    
    for (slot in sortedSlots) {
        if (unassignedPlayers.isEmpty()) break
        val bestPlayer = unassignedPlayers.maxByOrNull { player ->
            calculatePlayerSlotScore(player, slot)
        }
        if (bestPlayer != null) {
            assignments.add(Pair(bestPlayer, slot))
            unassignedPlayers.remove(bestPlayer)
        }
    }
    return assignments
}

fun resolveMiniPitchOverlaps(
    mapped: List<Pair<Player, FormationSlot>>,
    minDistance: Float = 0.09f
): List<Pair<Player, FormationSlot>> {
    val adjusted = mapped.map { Pair(it.first, it.second.copy()) }.toMutableList()
    
    repeat(5) {
        for (i in adjusted.indices) {
            for (j in adjusted.indices) {
                if (i != j) {
                    val p1 = adjusted[i].second
                    val p2 = adjusted[j].second
                    val dx = p1.x - p2.x
                    val dy = p1.y - p2.y
                    val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (dist < minDistance && dist > 0.001f) {
                        val force = (minDistance - dist) / 2f
                        val rx = (dx / dist) * force
                        val ry = (dy / dist) * force
                        
                        adjusted[i] = Pair(adjusted[i].first, p1.copy(
                            x = (p1.x + rx).coerceIn(0.12f, 0.88f),
                            y = (p1.y + ry).coerceIn(0.15f, 0.80f)
                        ))
                        adjusted[j] = Pair(adjusted[j].first, p2.copy(
                            x = (p2.x - rx).coerceIn(0.12f, 0.88f),
                            y = (p2.y - ry).coerceIn(0.15f, 0.80f)
                        ))
                    }
                }
            }
        }
    }
    return adjusted
}
