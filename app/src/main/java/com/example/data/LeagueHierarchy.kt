package com.example.data

data class LeagueDivision(
    val code: String,
    val name: String,
    val divisionLevel: Int,
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
     * The hierarchy already contains only the active levels for this country. Middle divisions
     * participate in two boundaries in the same season, so at most half of their clubs can move
     * through either boundary. This guarantees that promoted and relegated groups cannot overlap.
     */
    fun safeMovementSpotsBetween(
        upperLevel: Int,
        lowerLevel: Int = upperLevel + 1,
        upperTeamCount: Int,
        lowerTeamCount: Int
    ): Int {
        if (lowerLevel != upperLevel + 1) return 0

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

        if (upperIndex > 0) {
            spots = minOf(spots, upperTeamCount / 2)
        }
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

    /**
     * Inglaterra 2026/27 na granularidade suportada pelo jogo: três clubes trocam entre Premier
     * League/Championship e três entre Championship/League One. O jogo ainda não representa o
     * playoff de acesso como competição separada; esta regra preserva o número factual de clubes
     * que mudam de nível ao final da temporada.
     */
    private val englandDivisions = listOf(
        LeagueDivision("SERIE_A", "", 1, promotionSpots = 0, relegationSpots = 3),
        LeagueDivision("SERIE_B", "", 2, promotionSpots = 3, relegationSpots = 3),
        LeagueDivision("SERIE_C", "", 3, promotionSpots = 3, relegationSpots = 0)
    )

    /**
     * Espanha 2026/27 na granularidade suportada pelo jogo: três trocas entre La Liga/Segunda e
     * quatro entre Segunda/terceiro nível. Playoffs ainda não são materializados separadamente;
     * a fronteira aplica o total factual de promovidos/rebaixados.
     */
    private val spainDivisions = listOf(
        LeagueDivision("SERIE_A", "", 1, promotionSpots = 0, relegationSpots = 3),
        LeagueDivision("SERIE_B", "", 2, promotionSpots = 3, relegationSpots = 4),
        LeagueDivision("SERIE_C", "", 3, promotionSpots = 4, relegationSpots = 0)
    )

    private val staticHierarchies = mapOf(
        "Brasil" to listOf(
            LeagueDivision("SERIE_A", "", 1, promotionSpots = 0, relegationSpots = 4),
            LeagueDivision("SERIE_B", "", 2, promotionSpots = 4, relegationSpots = 4),
            LeagueDivision("SERIE_C", "", 3, promotionSpots = 4, relegationSpots = 4),
            LeagueDivision("SERIE_D", "", 4, promotionSpots = 4, relegationSpots = 0)
        ),
        "Inglaterra" to englandDivisions,
        "Espanha" to spainDivisions,
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
        val baseDivisions = staticHierarchies[country] ?: genericDivisions
        val activeDivisions = adaptToConfiguredDivisionCount(country, baseDivisions)
        val europeanBaseline = EuropeanDomesticBaseline2026_27.forCountry(country)
        val resolvedDivisions = activeDivisions.map { div ->
            val resolvedName = if (div.divisionLevel == 1 && europeanBaseline != null) {
                europeanBaseline.topDivisionName
            } else {
                DefaultData.getCompetitionName(div.code, country)
            }
            div.copy(name = resolvedName)
        }
        val resolvedCupName = europeanBaseline?.nationalCupName
            ?: DefaultData.getCompetitionName("COPA", country)
        val resolvedContinentalName = DefaultData.getCompetitionName("CONTINENTAL", country)

        return LeagueHierarchy(
            country = country,
            divisions = resolvedDivisions,
            cupName = resolvedCupName,
            continentalName = resolvedContinentalName
        )
    }

    private fun adaptToConfiguredDivisionCount(
        country: String,
        baseDivisions: List<LeagueDivision>
    ): List<LeagueDivision> {
        val configuredCount = configuredDivisionCount(country, baseDivisions.size)
        if (configuredCount <= 0) return emptyList()

        val movementSpots = baseDivisions
            .firstOrNull { it.relegationSpots > 0 }
            ?.relegationSpots
            ?: baseDivisions.firstOrNull { it.promotionSpots > 0 }?.promotionSpots
            ?: 2

        return (1..configuredCount).map { level ->
            val existing = baseDivisions.find { it.divisionLevel == level }
            val code = existing?.code ?: competitionCodeForLevel(level)
            val promotionSpots = when {
                level == 1 -> 0
                existing != null && existing.promotionSpots > 0 -> existing.promotionSpots
                else -> movementSpots
            }
            val relegationSpots = when {
                level == configuredCount -> 0
                existing != null && existing.relegationSpots > 0 -> existing.relegationSpots
                else -> movementSpots
            }

            LeagueDivision(
                code = code,
                name = existing?.name.orEmpty(),
                divisionLevel = level,
                promotionSpots = promotionSpots,
                relegationSpots = relegationSpots
            )
        }
    }

    private fun configuredDivisionCount(country: String, fallback: Int): Int {
        DefaultData.countryDivisionSizes[country]
            ?.size
            ?.takeIf { it > 0 }
            ?.let { return it }

        DefaultData.originalMap[country]
            ?.teams
            ?.maxOfOrNull { it.division }
            ?.takeIf { it > 0 }
            ?.let { return it }

        return fallback
    }

    private fun competitionCodeForLevel(level: Int): String {
        return when (level) {
            1 -> "SERIE_A"
            2 -> "SERIE_B"
            3 -> "SERIE_C"
            else -> "SERIE_D"
        }
    }
}
