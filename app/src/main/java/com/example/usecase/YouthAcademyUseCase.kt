package com.example.usecase

import com.example.data.DefaultData
import com.example.ui.viewmodel.GameViewModel.AcademyProspect
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlin.random.Random

class YouthAcademyUseCase {

    private val gson = Gson()
    private val prospectListType = object : TypeToken<List<AcademyProspect>>() {}.type

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
        return gson.toJson(prospects, prospectListType)
    }

    private fun parseJsonProspects(rawString: String): List<AcademyProspect> {
        return try {
            gson.fromJson<List<AcademyProspect>>(rawString, prospectListType)
                ?.filter { prospect ->
                    prospect.name.isNotBlank() &&
                        prospect.position.isNotBlank() &&
                        prospect.age > 0 &&
                        prospect.force > 0 &&
                        prospect.potential > 0
                }
                ?: emptyList()
        } catch (_: JsonSyntaxException) {
            emptyList()
        } catch (_: RuntimeException) {
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
        }.filter { prospect ->
            prospect.name.isNotBlank() && prospect.position.isNotBlank()
        }
    }
}
