package com.example.usecase

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
}
