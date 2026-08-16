package com.example.usecase

import com.example.data.DefaultData
import com.example.ui.viewmodel.GameViewModel.AcademyProspect
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

class YouthAcademyUseCase {

    fun generateInitialProspects(country: String): String {
        val prospects = try {
            val countryInfo = DefaultData.getCountryInfo(country)
            val positions = listOf("GOL", "ZAG", "LAT", "VOL", "MEI", "ATA")

            listOf(
                AcademyProspect(
                    name = "${countryInfo.firstNames.random()} ${countryInfo.lastNames.random()}",
                    age = Random.nextInt(15, 17),
                    position = positions.random(),
                    force = Random.nextInt(38, 48),
                    potential = Random.nextInt(68, 78)
                ),
                AcademyProspect(
                    name = "${countryInfo.firstNames.random()} ${countryInfo.lastNames.random()}",
                    age = Random.nextInt(15, 17),
                    position = positions.random(),
                    force = Random.nextInt(40, 50),
                    potential = Random.nextInt(72, 82)
                )
            )
        } catch (_: Exception) {
            listOf(
                AcademyProspect("Bruno Mendes", 15, "ZAG", 40, 75),
                AcademyProspect("Rodrigo Silva", 16, "ATA", 44, 82)
            )
        }

        return serializeProspects(prospects)
    }

    /**
     * Reads the canonical JSON representation and the pre-Phase-7 legacy
     * `name;age;position;force;potential|...` representation.
     */
    fun parseProspects(rawString: String): List<AcademyProspect> {
        if (rawString.isBlank()) return emptyList()

        val trimmed = rawString.trim()
        return if (trimmed.startsWith("[")) {
            parseJsonProspects(trimmed)
        } else {
            parseLegacyProspects(trimmed)
        }
    }

    fun serializeProspects(prospects: List<AcademyProspect>): String {
        val array = JSONArray()
        prospects.forEach { prospect ->
            array.put(
                JSONObject()
                    .put("name", prospect.name)
                    .put("age", prospect.age)
                    .put("position", prospect.position)
                    .put("force", prospect.force)
                    .put("potential", prospect.potential)
            )
        }
        return array.toString()
    }

    private fun parseJsonProspects(rawString: String): List<AcademyProspect> {
        return try {
            val array = JSONArray(rawString)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    add(
                        AcademyProspect(
                            name = obj.optString("name", "Jovem"),
                            age = obj.optInt("age", 16),
                            position = obj.optString("position", "MEI"),
                            force = obj.optInt("force", 40),
                            potential = obj.optInt("potential", 70)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseLegacyProspects(rawString: String): List<AcademyProspect> {
        return rawString.split("|").mapNotNull { entry ->
            val parts = entry.split(";")
            if (parts.size < 5) return@mapNotNull null

            AcademyProspect(
                name = parts[0],
                age = parts[1].toIntOrNull() ?: 16,
                position = parts[2],
                force = parts[3].toIntOrNull() ?: 40,
                potential = parts[4].toIntOrNull() ?: 70
            )
        }
    }
}
