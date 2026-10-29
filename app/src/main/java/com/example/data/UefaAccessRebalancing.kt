package com.example.data

/**
 * Reequilíbrio determinístico da access list UEFA 2026/27 quando a vaga de titleholder não é usada.
 *
 * Regras oficiais (Regulamentos UEFA 2026/27, Art. 3, e comunicados da UEFA):
 *
 * - a vaga de titleholder só existe se o campeão europeu NÃO se classificou para a league phase da
 *   Champions pela liga doméstica;
 * - se o campeão da Champions já está classificado pela liga doméstica, a vaga passa ao clube com
 *   maior coeficiente de clube entre os que entrariam no Champions Path (qualificatórias ou
 *   play-off). Em 2026/27 isso colocou o Shakhtar Donetsk na league phase;
 * - se o campeão da Europa League já está classificado pela liga doméstica, a vaga passa ao clube com
 *   maior coeficiente de clube entre TODOS os que entrariam no Champions Path ou no League Path. Em
 *   2026/27 isso colocou o Sporting CP na league phase.
 *
 * O motor não inventa desempate. Coeficiente máximo empatado é rejeitado, e o chamador precisa de
 * evidência oficial antes de materializar o clube promovido.
 *
 * Esta fatia é puramente de domínio: não persiste nada, não agenda partidas e não altera a access
 * list já confirmada em [UefaAccessList2026_27].
 */

/** Situação da vaga de titleholder de uma competição europeia. */
enum class UefaTitleholderBerth {
    /** O campeão não se classificou pela liga doméstica: a vaga é usada por ele mesmo. */
    DEDICATED,

    /** O campeão já se classificou pela liga doméstica: a vaga fica vaga e precisa de reequilíbrio. */
    VACATED
}

/** Pools oficiais de onde sai o clube que ocupa uma vaga de titleholder vaga. */
enum class UefaVacancyCandidatePool {
    /** Vaga do titleholder da Champions: somente clubes que entrariam no Champions Path. */
    CHAMPIONS_PATH_ONLY,

    /** Vaga do titleholder da Europa League: Champions Path e League Path. */
    ALL_QUALIFYING_PATHS;

    fun accepts(path: UefaQualificationPath?): Boolean = when (this) {
        CHAMPIONS_PATH_ONLY -> path == UefaQualificationPath.CHAMPIONS
        ALL_QUALIFYING_PATHS -> path != null
    }
}

/**
 * Clube que entraria na fase qualificatória e pode ser promovido a uma vaga vaga.
 *
 * [entryPoint] é o ponto de entrada original; [clubCoefficientMilli] vem de
 * [UefaCoefficientRules.clubFiveSeasonCoefficient], sempre em milésimos de ponto.
 */
data class UefaQualifyingEntrant(
    val teamId: Long,
    val country: String,
    val entryPoint: UefaEntryPoint,
    val clubCoefficientMilli: Long
)

/** Motivos de rejeição explícita, em vez de promoção inventada. */
enum class UefaVacancyRejection {
    NO_ELIGIBLE_CANDIDATE,
    AMBIGUOUS_HIGHEST_COEFFICIENT,
    INVALID_COEFFICIENT
}

sealed class UefaVacancyResolution {
    data class Promoted(
        val teamId: Long,
        val country: String,
        val from: UefaEntryPoint,
        val to: UefaEntryPoint,
        val candidatePool: UefaVacancyCandidatePool,
        val clubCoefficientMilli: Long
    ) : UefaVacancyResolution()

    data class Rejected(val reason: UefaVacancyRejection) : UefaVacancyResolution()
}

object UefaAccessRebalancer {

    /**
     * Pool que o regulamento usa para preencher a vaga de cada titleholder.
     *
     * A Conference League não é modelada aqui: o campeão da Conference entra na Europa League, e a
     * vaga vaga correspondente pertence a outro fluxo ainda não implementado.
     */
    fun candidatePoolForVacancy(vacatedCompetition: CompetitionIdentity): UefaVacancyCandidatePool =
        when (vacatedCompetition) {
            CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE -> UefaVacancyCandidatePool.CHAMPIONS_PATH_ONLY
            CompetitionIdentity.UEFA_EUROPA_LEAGUE -> UefaVacancyCandidatePool.ALL_QUALIFYING_PATHS
            else -> throw IllegalArgumentException(
                "Reequilíbrio de titleholder não modelado para $vacatedCompetition."
            )
        }

    fun berthForTitleholder(
        qualifiedThroughDomesticLeague: Boolean
    ): UefaTitleholderBerth = if (qualifiedThroughDomesticLeague) {
        UefaTitleholderBerth.VACATED
    } else {
        UefaTitleholderBerth.DEDICATED
    }

    /**
     * Resolve quem ocupa a vaga vaga de um titleholder.
     *
     * [alreadyInLeaguePhase] impede que um clube ocupe duas vagas. Clubes que já entram na league
     * phase também são descartados por [UefaEntryPoint.stage].
     */
    fun resolveVacantTitleholderBerth(
        vacatedCompetition: CompetitionIdentity,
        entrants: List<UefaQualifyingEntrant>,
        alreadyInLeaguePhase: Set<Long> = emptySet()
    ): UefaVacancyResolution {
        val pool = candidatePoolForVacancy(vacatedCompetition)
        if (entrants.any { it.clubCoefficientMilli < 0L }) {
            return UefaVacancyResolution.Rejected(UefaVacancyRejection.INVALID_COEFFICIENT)
        }
        val candidates = entrants.filter { entrant ->
            entrant.teamId !in alreadyInLeaguePhase &&
                entrant.entryPoint.stage != UefaQualificationStage.LEAGUE_PHASE &&
                pool.accepts(entrant.entryPoint.path)
        }
        val best = candidates.maxOfOrNull { it.clubCoefficientMilli }
            ?: return UefaVacancyResolution.Rejected(UefaVacancyRejection.NO_ELIGIBLE_CANDIDATE)
        val winner = candidates.filter { it.clubCoefficientMilli == best }.singleOrNull()
            ?: return UefaVacancyResolution.Rejected(UefaVacancyRejection.AMBIGUOUS_HIGHEST_COEFFICIENT)
        return UefaVacancyResolution.Promoted(
            teamId = winner.teamId,
            country = winner.country,
            from = winner.entryPoint,
            to = UefaEntryPoint(
                competition = UefaCompetitionCode.UCL,
                stage = UefaQualificationStage.LEAGUE_PHASE
            ),
            candidatePool = pool,
            clubCoefficientMilli = winner.clubCoefficientMilli
        )
    }
}

/** Coeficiente de associação de uma única época, em milésimos de ponto. */
data class UefaAssociationSeasonCoefficient(
    val country: String,
    val coefficientMilli: Long
)

enum class UefaPerformanceSpotRejection {
    INSUFFICIENT_ASSOCIATIONS,
    AMBIGUOUS_RANKING,
    INVALID_COEFFICIENT,
    UNKNOWN_ASSOCIATION
}

sealed class UefaPerformanceSpotResolution {
    /** Associações premiadas, na ordem do ranking. */
    data class Awarded(val countries: List<String>) : UefaPerformanceSpotResolution()

    data class Rejected(val reason: UefaPerformanceSpotRejection) : UefaPerformanceSpotResolution()
}

/**
 * European Performance Spots: as duas associações com melhor coeficiente da época anterior ganham
 * uma vaga extra na league phase da Champions.
 *
 * Ordem é sempre coeficiente decrescente. Empate exatamente na fronteira da segunda vaga é
 * rejeitado, porque o regulamento não define um desempate de associação que o motor possa aplicar
 * sozinho.
 */
object UefaEuropeanPerformanceSpotAllocator {

    fun award(
        coefficients: List<UefaAssociationSeasonCoefficient>,
        spots: Int = UefaAccessList2026_27.EUROPEAN_PERFORMANCE_SPOTS
    ): UefaPerformanceSpotResolution {
        require(spots > 0)
        if (coefficients.any { it.coefficientMilli < 0L }) {
            return UefaPerformanceSpotResolution.Rejected(UefaPerformanceSpotRejection.INVALID_COEFFICIENT)
        }
        val canonical = mutableListOf<UefaAssociationSeasonCoefficient>()
        coefficients.forEach { entry ->
            val country = CountryFootballRulesRegistry
                .resolve(entry.country)
                ?.canonicalCountry
                ?: return UefaPerformanceSpotResolution.Rejected(
                    UefaPerformanceSpotRejection.UNKNOWN_ASSOCIATION
                )
            val previous = canonical.singleOrNull { it.country == country }
            if (previous != null && previous.coefficientMilli != entry.coefficientMilli) {
                return UefaPerformanceSpotResolution.Rejected(
                    UefaPerformanceSpotRejection.AMBIGUOUS_RANKING
                )
            }
            if (previous == null) {
                canonical += UefaAssociationSeasonCoefficient(country, entry.coefficientMilli)
            }
        }
        if (canonical.size <= spots) {
            return UefaPerformanceSpotResolution.Rejected(
                UefaPerformanceSpotRejection.INSUFFICIENT_ASSOCIATIONS
            )
        }
        val ranked = canonical.sortedByDescending { it.coefficientMilli }
        if (ranked[spots - 1].coefficientMilli == ranked[spots].coefficientMilli) {
            return UefaPerformanceSpotResolution.Rejected(
                UefaPerformanceSpotRejection.AMBIGUOUS_RANKING
            )
        }
        return UefaPerformanceSpotResolution.Awarded(
            ranked.take(spots).map { it.country }
        )
    }

    /**
     * Posição doméstica que recebe o European Performance Spot.
     *
     * A vaga fica imediatamente abaixo das vagas domésticas diretas. [unavailablePositions] contém
     * posições que não podem receber o EPS - na prática, clubes que já entraram por outra vaga
     * (titleholder), porque um clube nunca ocupa duas vagas.
     */
    fun berthPosition(
        directLeagueSlots: Int,
        unavailablePositions: Set<Int> = emptySet()
    ): Int {
        require(directLeagueSlots > 0)
        var position = directLeagueSlots + 1
        while (position in unavailablePositions) position++
        return position
    }
}
