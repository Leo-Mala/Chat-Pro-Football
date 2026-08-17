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
     * Returns the configured number of clubs that exchange places across an
     * adjacent division boundary while preserving both division sizes.
     */
    fun movementSpotsBetween(upperLevel: Int, lowerLevel: Int = upperLevel + 1): Int {
        val upper = getDivisionByLevel(upperLevel) ?: return 0
        val lower = getDivisionByLevel(lowerLevel) ?: return 0
        if (upper.relegationSpots <= 0 || lower.promotionSpots <= 0) return 0
        return minOf(upper.relegationSpots, lower.promotionSpots)
    }

    /**
     * Applies the configured movement rule to the actual division sizes.
     *
     * Middle divisions participate in two boundaries in the same season. Reserving at least
     * half of their clubs for the opposite boundary guarantees that the promoted group and the
     * relegated group cannot overlap, so no club can jump two divisions in one transition.
     */
    fun safeMovementSpotsBetween(
        upperLevel: Int,
        lowerLevel: Int = upperLevel + 1,
        upperTeamCount: Int,
        lowerTeamCount: Int
    ): Int {
        val ordered = divisions.sortedBy { it.divisionLevel }
        val upperIndex = ordered.indexOfFirst { it.divisionLevel == upperLevel }
        val lowerIndex = ordered.indexOfFirst { it.divisionLevel == lowerLevel }
        if (upperIndex < 0 || lowerIndex != upperIndex + 1) return 0

        var spots = minOf(
            movementSpotsBetween(upperLevel, lowerLevel),
            upperTeamCount,
            lowerTeamCount
        )
        if (spots <= 0) return 0

        // A divisão superior desta fronteira também pode promover clubes para uma divisão acima.
        if (upperIndex > 0) {
            spots = minOf(spots, upperTeamCount / 2)
        }
        // A divisão inferior desta fronteira também pode rebaixar clubes para uma divisão abaixo.
        if (lowerIndex < ordered.lastIndex) {
            spots = minOf(spots, lowerTeamCount / 2)
        }
        return spots
    }

    fun hasBalancedAdjacentMovementRules(): Boolean {
        val ordered = divisions.sortedBy { it.divisionLevel }
        return ordered.zipWithNext().all { (upper, lower) ->
            upper.relegationSpots == lower.promotionSpots
        }
    }
}

object LeagueHierarchyLoader {
    /**
     * Fallback neutro para países que ainda não possuem regras nacionais detalhadas.
     *
     * O comportamento antigo herdava a hierarquia brasileira (4 vagas) para qualquer país
     * desconhecido. Para a simulação mundial isso distorcia França, Alemanha, Itália etc.
     * Duas vagas preservam tamanhos das divisões e são compatíveis com a modelagem simplificada
     * atual até a futura normalização específica de cada federação.
     */
    private val genericDivisions = listOf(
        LeagueDivision("SERIE_A", "", 1, promotionSpots = 0, relegationSpots = 2),
        LeagueDivision("SERIE_B", "", 2, promotionSpots = 2, relegationSpots = 2),
        LeagueDivision("SERIE_C", "", 3, promotionSpots = 2, relegationSpots = 2),
        LeagueDivision("SERIE_D", "", 4, promotionSpots = 2, relegationSpots = 0)
    )

    private val staticHierarchies = mapOf(
        "Brasil" to listOf(
            LeagueDivision("SERIE_A", "", 1, promotionSpots = 0, relegationSpots = 4),
            LeagueDivision("SERIE_B", "", 2, promotionSpots = 4, relegationSpots = 4),
            LeagueDivision("SERIE_C", "", 3, promotionSpots = 4, relegationSpots = 4),
            LeagueDivision("SERIE_D", "", 4, promotionSpots = 4, relegationSpots = 0)
        ),
        "Inglaterra" to genericDivisions,
        "Espanha" to genericDivisions,
        "Argentina" to genericDivisions,
        "Estados Unidos / Canadá" to genericDivisions,
        // Alias legado mantido para saves/dados antigos que ainda usem a denominação anterior.
        "Estados Unidos / México" to genericDivisions,
        "América Central" to genericDivisions,
        "África" to genericDivisions,
        "Ásia" to genericDivisions,
        "Oceania" to genericDivisions,
        "África / Ásia / Oceania" to genericDivisions
    )

    val supportedCountries: Set<String>
        get() = staticHierarchies.keys

    fun hasExplicitHierarchy(country: String): Boolean = country in staticHierarchies

    fun getHierarchyForCountry(country: String): LeagueHierarchy {
        val staticDivs = staticHierarchies[country] ?: genericDivisions
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
