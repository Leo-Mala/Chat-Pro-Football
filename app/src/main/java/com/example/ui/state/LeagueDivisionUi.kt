package com.example.ui.state

import com.example.data.LeagueHierarchy

data class LeagueDivisionTab(
    val key: String,
    val division: Int,
    val label: String
)

/**
 * Regra pura compartilhada pelas telas que navegam entre divisões nacionais.
 *
 * A quantidade de abas vem da hierarquia ativa do país, portanto uma carreira nunca fica
 * limitada artificialmente às quatro divisões históricas da UI. Chaves de UI são separadas dos
 * códigos de Fixture porque níveis 4+ ainda compartilham o código legado SERIE_D.
 */
object LeagueDivisionUi {
    private const val PREFIX = "DIVISION_"

    fun tabsForHierarchy(hierarchy: LeagueHierarchy): List<LeagueDivisionTab> {
        val ordered = hierarchy.divisions.sortedBy { it.divisionLevel }
        return ordered.map { division ->
            LeagueDivisionTab(
                key = keyForDivision(division.divisionLevel),
                division = division.divisionLevel,
                label = displayLabel(
                    hierarchy = hierarchy,
                    divisionLevel = division.divisionLevel,
                    rawName = division.name
                )
            )
        }
    }

    fun keyForDivision(division: Int): String {
        require(division > 0) { "Divisão deve ser positiva: $division" }
        return "$PREFIX$division"
    }

    fun divisionFromKey(key: String): Int? {
        if (!key.startsWith(PREFIX)) return null
        return key.removePrefix(PREFIX).toIntOrNull()?.takeIf { it > 0 }
    }

    private fun displayLabel(
        hierarchy: LeagueHierarchy,
        divisionLevel: Int,
        rawName: String
    ): String {
        if (rawName.isBlank()) return "${divisionLevel}ª Divisão"

        val duplicateName = hierarchy.divisions.count {
            it.name.equals(rawName, ignoreCase = true)
        } > 1
        return if (duplicateName && divisionLevel > 4) {
            "$rawName • ${divisionLevel}ª Div."
        } else {
            rawName
        }
    }
}
