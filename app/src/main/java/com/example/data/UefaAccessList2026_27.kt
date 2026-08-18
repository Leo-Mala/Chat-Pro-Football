package com.example.data

/**
 * Parte da access list UEFA 2026/27 que já pode ser representada com segurança no domínio atual.
 *
 * Esta fase codifica apenas fatos oficiais confirmados para a league phase da Champions e os dois
 * European Performance Spots. A tabela completa de entrada Q1/Q2/Q3/play-off por associação será
 * adicionada somente quando cada linha do Annex A estiver transcrita e testada; associações ainda
 * não modeladas retornam null em vez de receber fallback inventado.
 */
object UefaAccessList2026_27 {
    const val CHAMPIONS_LEAGUE_PHASE_SIZE = 36
    const val CHAMPIONS_TITLEHOLDER_SLOTS = 1
    const val EUROPA_TITLEHOLDER_SLOTS = 1
    const val EUROPEAN_PERFORMANCE_SPOTS = 2
    const val CHAMPIONS_PATH_QUALIFIER_SLOTS = 5
    const val LEAGUE_PATH_QUALIFIER_SLOTS = 2

    /** Vagas domésticas diretas à league phase, antes dos European Performance Spots. */
    private val championsDirectLeagueSlotsByCountry: Map<String, Int> = mapOf(
        "Inglaterra" to 4,
        "Itália" to 4,
        "Espanha" to 4,
        "Alemanha" to 4,
        "França" to 3,
        "Países Baixos" to 2,
        "Portugal" to 1,
        "Bélgica" to 1,
        "Tchéquia" to 1,
        "Turquia" to 1
    )

    /** Para 2026/27, os dois EPS foram conquistados por Inglaterra e Espanha. */
    val europeanPerformanceSpotCountries: Set<String> = setOf("Inglaterra", "Espanha")

    fun championsDirectLeagueSlots(country: String): Int? {
        val canonical = CountryFootballRulesRegistry.resolve(country)?.canonicalCountry ?: return null
        return championsDirectLeagueSlotsByCountry[canonical]
    }

    fun europeanPerformanceSpots(country: String): Int {
        val canonical = CountryFootballRulesRegistry.resolve(country)?.canonicalCountry ?: return 0
        return if (canonical in europeanPerformanceSpotCountries) 1 else 0
    }

    fun championsKnownAutomaticSlotTotal(): Int =
        championsDirectLeagueSlotsByCountry.values.sum() +
            CHAMPIONS_TITLEHOLDER_SLOTS +
            EUROPA_TITLEHOLDER_SLOTS +
            EUROPEAN_PERFORMANCE_SPOTS +
            CHAMPIONS_PATH_QUALIFIER_SLOTS +
            LEAGUE_PATH_QUALIFIER_SLOTS
}

/** Candidato doméstico tipado para futura materialização da access list. */
data class UefaDomesticEntryCandidate(
    val teamId: Long,
    val country: String,
    val source: QualificationSource
)

object UefaDomesticAccessPlanner {

    fun fromLeaguePosition(
        standings: List<GlobalLeagueStanding>,
        country: String,
        position: Int,
        division: Int = 1
    ): UefaDomesticEntryCandidate? {
        require(position > 0)
        val canonical = CountryFootballRulesRegistry.resolve(country)?.canonicalCountry ?: return null
        if (CountryFootballRulesRegistry.confederationFor(canonical) != FootballConfederation.UEFA) return null
        val row = standings.singleOrNull {
            CountryFootballRulesRegistry.resolve(it.country)?.canonicalCountry == canonical &&
                it.division == division &&
                it.position == position
        } ?: return null
        return UefaDomesticEntryCandidate(
            teamId = row.teamId,
            country = canonical,
            source = QualificationSource.LeaguePosition(position)
        )
    }

    fun fromNationalCupWinner(teamId: Long, country: String): UefaDomesticEntryCandidate? {
        if (teamId <= 0L) return null
        val rules = CountryFootballRulesRegistry.resolve(country) ?: return null
        if (rules.confederation != FootballConfederation.UEFA || rules.kind != CountryRuleKind.NATIONAL_ASSOCIATION) {
            return null
        }
        return UefaDomesticEntryCandidate(
            teamId = teamId,
            country = rules.canonicalCountry,
            source = QualificationSource.NationalCupWinner(rules.canonicalCountry)
        )
    }

    fun fromTitleholder(
        teamId: Long,
        country: String,
        competition: CompetitionIdentity
    ): UefaDomesticEntryCandidate? {
        if (teamId <= 0L) return null
        require(competition in setOf(CompetitionIdentity.UEFA_CL, CompetitionIdentity.UEFA_EL, CompetitionIdentity.UEFA_ECL))
        val rules = CountryFootballRulesRegistry.resolve(country) ?: return null
        if (rules.confederation != FootballConfederation.UEFA || rules.kind != CountryRuleKind.NATIONAL_ASSOCIATION) {
            return null
        }
        return UefaDomesticEntryCandidate(
            teamId = teamId,
            country = rules.canonicalCountry,
            source = QualificationSource.ContinentalChampion(competition)
        )
    }
}
