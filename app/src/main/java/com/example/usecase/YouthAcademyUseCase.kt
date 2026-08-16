package com.example.usecase

import com.example.data.DefaultData
import com.example.ui.viewmodel.GameViewModel.AcademyProspect
import kotlin.random.Random

class YouthAcademyUseCase {

    fun generateInitialProspects(country: String): String {
        return try {
            val countryInfo = DefaultData.getCountryInfo(country)
            val positions = listOf("GOL", "ZAG", "LAT", "VOL", "MEI", "ATA")

            val p1Name = "${countryInfo.firstNames.random()} ${countryInfo.lastNames.random()}"
            val p1Pos = positions.random()
            val p1Age = Random.nextInt(15, 17)
            val p1Force = Random.nextInt(38, 48)
            val p1Pot = Random.nextInt(68, 78)

            val p2Name = "${countryInfo.firstNames.random()} ${countryInfo.lastNames.random()}"
            val p2Pos = positions.random()
            val p2Age = Random.nextInt(15, 17)
            val p2Force = Random.nextInt(40, 50)
            val p2Pot = Random.nextInt(72, 82)

            "$p1Name;$p1Age;$p1Pos;$p1Force;$p1Pot|$p2Name;$p2Age;$p2Pos;$p2Force;$p2Pot"
        } catch (e: Exception) {
            "Bruno Mendes;15;ZAG;40;75|Rodrigo Silva;16;ATA;44;82"
        }
    }

    fun parseProspects(rawString: String): List<AcademyProspect> {
        if (rawString.isBlank()) return emptyList()
        return rawString.split("|").mapNotNull { entry ->
            val parts = entry.split(";")
            if (parts.size >= 5) {
                AcademyProspect(
                    name = parts[0],
                    age = parts[1].toIntOrNull() ?: 16,
                    position = parts[2],
                    force = parts[3].toIntOrNull() ?: 40,
                    potential = parts[4].toIntOrNull() ?: 70
                )
            } else null
        }
    }
}
