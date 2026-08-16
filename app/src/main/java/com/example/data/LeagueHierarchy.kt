package com.example.data

data class LeagueDivision(
    val code: String,            // "SERIE_A", "SERIE_B", "SERIE_C", "SERIE_D"
    val name: String,            // e.g. "Série A"
    val divisionLevel: Int,      // 1, 2, 3, 4
    val promotionSpots: Int,
    val relegationSpots: Int
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

    /**
     * Returns the number of clubs that can safely exchange places across an
     * adjacent division boundary while preserving both division sizes.
     */
    fun movementSpotsBetween(upperLevel: Int, lowerLevel: Int = upperLevel + 1): Int {
        val upper = getDivisionByLevel(upperLevel) ?: return 0
        val lower = getDivisionByLevel(lowerLevel) ?: return 0
        if (upper.relegationSpots <= 0 || lower.promotionSpots <= 0) return 0
        return minOf(upper.relegationSpots, lower.promotionSpots)
    }

    fun hasBalancedAdjacentMovementRules(): Boolean {
        val ordered = divisions.sortedBy { it.divisionLevel }
        return ordered.zipWithNext().all { (upper, lower) ->
            upper.relegationSpots == lower.promotionSpots
        }
    }
}

object LeagueHierarchyLoader {
    private val staticHierarchies = mapOf(
        "Brasil" to listOf(
            LeagueDivision("SERIE_A", "", 1, promotionSpots = 0, relegationSpots = 4),
            LeagueDivision("SERIE_B", "", 2, promotionSpots = 4, relegationSpots = 4),
            LeagueDivision("SERIE_C", "", 3, promotionSpots = 4, relegationSpots = 4),
            LeagueDivision("SERIE_D", "", 4, promotionSpots = 4, relegationSpots = 0)
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

    val supportedCountries: Set<String>
        get() = staticHierarchies.keys

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
