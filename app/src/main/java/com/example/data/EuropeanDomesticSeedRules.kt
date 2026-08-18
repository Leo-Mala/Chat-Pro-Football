package com.example.data

/**
 * Ponte entre o baseline factual europeu e o gerador legado de divisões.
 *
 * A lista de níveis inferiores ainda vem de `DefaultData.countryDivisionSizes`, mas a quantidade da
 * primeira divisão UEFA deve obedecer ao baseline factual da temporada doméstica. Isso evita manter
 * números conflitantes em duas fontes de verdade durante a migração progressiva dos clubes reais.
 *
 * Este objeto não muta `DefaultData` sozinho. O gerador legado deve chamar [resolveDivisionSizes]
 * quando a integração do seed for habilitada.
 */
object EuropeanDomesticSeedRules {

    fun resolveDivisionSizes(country: String, legacySizes: List<Int>): List<Int> {
        val baseline = EuropeanDomesticBaseline2026_27.forCountry(country) ?: return legacySizes
        if (legacySizes.isEmpty()) return listOf(baseline.topDivisionClubCount)
        return listOf(baseline.topDivisionClubCount) + legacySizes.drop(1)
    }

    fun topFlightClubNames(country: String): List<String>? {
        val baseline = EuropeanDomesticBaseline2026_27.forCountry(country) ?: return null
        if (baseline.coverage != EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT) return null
        return baseline.verifiedTopFlightClubs
    }

    fun hasCompleteTopFlight(country: String): Boolean =
        topFlightClubNames(country)?.size == EuropeanDomesticBaseline2026_27.forCountry(country)?.topDivisionClubCount
}
