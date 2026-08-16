package com.example.data

data class LeagueDivision(
    val code: String,            // "SERIE_A", "SERIE_B", "SERIE_C", "SERIE_D"
    val name: String,            // e.g. "Série A"
    val divisionLevel: Int,      // 1, 2, 3, 4
    val promotionSpots: Int,     // e.g., 2
    val relegationSpots: Int      // e.g., 2
)

data class LeagueHierarchy(
    val country: String,
    val divisions: List<LeagueDivision>,
    val cupName: String,
    val continentalName: String
) {
    fun getDivisionByLevel(level: Int): LeagueDivision? {
        return divisions.find { it.divisionLevel == level }
    }

    fun getDivisionByCode(code: String): LeagueDivision? {
        return divisions.find { it.code == code }
    }
}

object LeagueHierarchyLoader {
    private val staticHierarchies = mapOf(
        "Brasil" to listOf(
            LeagueDivision("SERIE_A", "", 1, promotionSpots = 0, relegationSpots = 4),
            LeagueDivision("SERIE_B", "", 2, promotionSpots = 4, relegationSpots = 4),
            LeagueDivision("SERIE_C", "", 3, promotionSpots = 4, relegationSpots = 2),
            LeagueDivision("SERIE_D", "", 4, promotionSpots = 6, relegationSpots = 0)
        ),
        "Inglaterra" to listOf(
            LeagueDivision("SERIE_A", "", 1, promotionSpots = 0, relegationSpots = 2),
            LeagueDivision("SERIE_B", "", 2, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_C", "", 3, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_D", "", 4, promotionSpots = 2, relegationSpots = 0)
        ),
        "Espanha" to listOf(
            LeagueDivision("SERIE_A", "", 1, promotionSpots = 0, relegationSpots = 2),
            LeagueDivision("SERIE_B", "", 2, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_C", "", 3, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_D", "", 4, promotionSpots = 2, relegationSpots = 0)
        ),
        "Argentina" to listOf(
            LeagueDivision("SERIE_A", "", 1, promotionSpots = 0, relegationSpots = 2),
            LeagueDivision("SERIE_B", "", 2, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_C", "", 3, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_D", "", 4, promotionSpots = 2, relegationSpots = 0)
        ),
        "Estados Unidos / México" to listOf(
            LeagueDivision("SERIE_A", "", 1, promotionSpots = 0, relegationSpots = 2),
            LeagueDivision("SERIE_B", "", 2, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_C", "", 3, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_D", "", 4, promotionSpots = 2, relegationSpots = 0)
        ),
        "América Central" to listOf(
            LeagueDivision("SERIE_A", "", 1, promotionSpots = 0, relegationSpots = 2),
            LeagueDivision("SERIE_B", "", 2, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_C", "", 3, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_D", "", 4, promotionSpots = 2, relegationSpots = 0)
        ),
        "África" to listOf(
            LeagueDivision("SERIE_A", "", 1, promotionSpots = 0, relegationSpots = 2),
            LeagueDivision("SERIE_B", "", 2, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_C", "", 3, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_D", "", 4, promotionSpots = 2, relegationSpots = 0)
        ),
        "Ásia" to listOf(
            LeagueDivision("SERIE_A", "", 1, promotionSpots = 0, relegationSpots = 2),
            LeagueDivision("SERIE_B", "", 2, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_C", "", 3, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_D", "", 4, promotionSpots = 2, relegationSpots = 0)
        ),
        "Oceania" to listOf(
            LeagueDivision("SERIE_A", "", 1, promotionSpots = 0, relegationSpots = 2),
            LeagueDivision("SERIE_B", "", 2, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_C", "", 3, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_D", "", 4, promotionSpots = 2, relegationSpots = 0)
        ),
        "África / Ásia / Oceania" to listOf(
            LeagueDivision("SERIE_A", "", 1, promotionSpots = 0, relegationSpots = 2),
            LeagueDivision("SERIE_B", "", 2, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_C", "", 3, promotionSpots = 2, relegationSpots = 2),
            LeagueDivision("SERIE_D", "", 4, promotionSpots = 2, relegationSpots = 0)
        )
    )

    fun getHierarchyForCountry(country: String): LeagueHierarchy {
        val staticDivs = staticHierarchies[country] ?: staticHierarchies["Brasil"]!!
        val resolvedDivisions = staticDivs.map { div ->
            val resolvedName = DefaultData.getCompetitionName(div.code, country)
            div.copy(name = resolvedName)
        }
        val resolvedCupName = DefaultData.getCompetitionName("ESTADUAL", country)
        val resolvedContinentalName = DefaultData.getCompetitionName("CONTINENTAL", country)

        return LeagueHierarchy(
            country = country,
            divisions = resolvedDivisions,
            cupName = resolvedCupName,
            continentalName = resolvedContinentalName
        )
    }
}
