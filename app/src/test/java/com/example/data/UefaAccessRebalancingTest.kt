package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobertura da fatia 2 da 9.10B2.1: reequilíbrio por titleholders e European Performance Spots.
 *
 * Os cenários usam clubes fictícios; o que é verificado são as regras oficiais e o fato de o motor
 * rejeitar explicitamente qualquer situação sem evidência suficiente.
 */
class UefaAccessRebalancingTest {

    @Test
    fun `titleholder place is vacated only when the champion already qualified domestically`() {
        assertEquals(
            UefaTitleholderBerth.DEDICATED,
            UefaAccessRebalancer.berthForTitleholder(qualifiedThroughDomesticLeague = false)
        )
        assertEquals(
            UefaTitleholderBerth.VACATED,
            UefaAccessRebalancer.berthForTitleholder(qualifiedThroughDomesticLeague = true)
        )
    }

    @Test
    fun `champions league vacancy only considers the champions path`() {
        val entrants = listOf(
            champion(teamId = 900L, country = "Ucrânia", stage = UefaQualificationStage.Q2, coefficient = 40_000L),
            // Maior coeficiente, mas entraria pelo League Path: não pode ocupar a vaga do titleholder UCL.
            leaguePath(teamId = 901L, country = "Portugal", stage = UefaQualificationStage.Q3, coefficient = 80_000L)
        )

        val resolution = UefaAccessRebalancer.resolveVacantTitleholderBerth(
            vacatedCompetition = CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE,
            entrants = entrants
        )

        assertTrue(resolution is UefaVacancyResolution.Promoted)
        val promoted = resolution as UefaVacancyResolution.Promoted
        assertEquals(900L, promoted.teamId)
        assertEquals("Ucrânia", promoted.country)
        assertEquals(UefaQualificationStage.Q2, promoted.from.stage)
        assertEquals(UefaQualificationPath.CHAMPIONS, promoted.from.path)
        assertEquals(
            UefaEntryPoint(UefaCompetitionCode.UCL, UefaQualificationStage.LEAGUE_PHASE),
            promoted.to
        )
        assertEquals(UefaVacancyCandidatePool.CHAMPIONS_PATH_ONLY, promoted.candidatePool)
        assertEquals(40_000L, promoted.clubCoefficientMilli)
    }

    @Test
    fun `europa league vacancy considers both qualifying paths`() {
        val entrants = listOf(
            champion(teamId = 900L, country = "Ucrânia", stage = UefaQualificationStage.Q2, coefficient = 40_000L),
            leaguePath(teamId = 901L, country = "Portugal", stage = UefaQualificationStage.Q3, coefficient = 80_000L)
        )

        val resolution = UefaAccessRebalancer.resolveVacantTitleholderBerth(
            vacatedCompetition = CompetitionIdentity.UEFA_EUROPA_LEAGUE,
            entrants = entrants
        )

        assertTrue(resolution is UefaVacancyResolution.Promoted)
        val promoted = resolution as UefaVacancyResolution.Promoted
        assertEquals(901L, promoted.teamId)
        assertEquals(UefaQualificationPath.LEAGUE, promoted.from.path)
        assertEquals(UefaVacancyCandidatePool.ALL_QUALIFYING_PATHS, promoted.candidatePool)
    }

    @Test
    fun `vacancy resolution rejects ties missing candidates and invalid coefficients`() {
        val tied = listOf(
            champion(teamId = 910L, country = "Sérvia", stage = UefaQualificationStage.Q3, coefficient = 25_000L),
            champion(teamId = 911L, country = "Croácia", stage = UefaQualificationStage.Q1, coefficient = 25_000L)
        )
        assertEquals(
            UefaVacancyResolution.Rejected(UefaVacancyRejection.AMBIGUOUS_HIGHEST_COEFFICIENT),
            UefaAccessRebalancer.resolveVacantTitleholderBerth(
                CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE,
                tied
            )
        )

        assertEquals(
            UefaVacancyResolution.Rejected(UefaVacancyRejection.NO_ELIGIBLE_CANDIDATE),
            UefaAccessRebalancer.resolveVacantTitleholderBerth(
                CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE,
                emptyList()
            )
        )

        // Somente clubes do League Path não preenchem a vaga do titleholder da Champions.
        assertEquals(
            UefaVacancyResolution.Rejected(UefaVacancyRejection.NO_ELIGIBLE_CANDIDATE),
            UefaAccessRebalancer.resolveVacantTitleholderBerth(
                CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE,
                listOf(
                    leaguePath(920L, "Portugal", UefaQualificationStage.PLAYOFF, 70_000L)
                )
            )
        )

        // Clube já classificado não pode ocupar duas vagas.
        assertEquals(
            UefaVacancyResolution.Rejected(UefaVacancyRejection.NO_ELIGIBLE_CANDIDATE),
            UefaAccessRebalancer.resolveVacantTitleholderBerth(
                CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE,
                listOf(champion(930L, "Ucrânia", UefaQualificationStage.Q2, 40_000L)),
                alreadyInLeaguePhase = setOf(930L)
            )
        )

        assertEquals(
            UefaVacancyResolution.Rejected(UefaVacancyRejection.INVALID_COEFFICIENT),
            UefaAccessRebalancer.resolveVacantTitleholderBerth(
                CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE,
                listOf(champion(940L, "Ucrânia", UefaQualificationStage.Q2, -1L))
            )
        )

        // Entrada já na league phase nunca é candidata.
        assertEquals(
            UefaVacancyResolution.Rejected(UefaVacancyRejection.NO_ELIGIBLE_CANDIDATE),
            UefaAccessRebalancer.resolveVacantTitleholderBerth(
                CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE,
                listOf(
                    UefaQualifyingEntrant(
                        teamId = 950L,
                        country = "Ucrânia",
                        entryPoint = UefaEntryPoint(
                            UefaCompetitionCode.UCL,
                            UefaQualificationStage.LEAGUE_PHASE
                        ),
                        clubCoefficientMilli = 50_000L
                    )
                )
            )
        )
    }

    @Test
    fun `vacancy pool mapping rejects competitions without a modelled rebalancing flow`() {
        assertEquals(
            UefaVacancyCandidatePool.CHAMPIONS_PATH_ONLY,
            UefaAccessRebalancer.candidatePoolForVacancy(CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE)
        )
        assertEquals(
            UefaVacancyCandidatePool.ALL_QUALIFYING_PATHS,
            UefaAccessRebalancer.candidatePoolForVacancy(CompetitionIdentity.UEFA_EUROPA_LEAGUE)
        )
        val failures = listOf(
            CompetitionIdentity.UEFA_CONFERENCE_LEAGUE,
            CompetitionIdentity.DOMESTIC_LEAGUE_1
        ).map { competition ->
            runCatching { UefaAccessRebalancer.candidatePoolForVacancy(competition) }.isFailure
        }
        assertTrue(failures.all { it })
    }

    @Test
    fun `performance spots award the two best associations and reject boundary ties`() {
        assertEquals(
            UefaPerformanceSpotResolution.Awarded(listOf("Inglaterra", "Espanha")),
            UefaEuropeanPerformanceSpotAllocator.award(
                listOf(
                    association("Inglaterra", 30_000L),
                    association("Espanha", 28_000L),
                    association("Itália", 27_000L),
                    association("Alemanha", 20_000L)
                )
            )
        )

        assertEquals(
            UefaPerformanceSpotResolution.Rejected(UefaPerformanceSpotRejection.AMBIGUOUS_RANKING),
            UefaEuropeanPerformanceSpotAllocator.award(
                listOf(
                    association("Inglaterra", 30_000L),
                    association("Espanha", 28_000L),
                    association("Itália", 28_000L)
                )
            )
        )

        assertEquals(
            UefaPerformanceSpotResolution.Rejected(UefaPerformanceSpotRejection.INSUFFICIENT_ASSOCIATIONS),
            UefaEuropeanPerformanceSpotAllocator.award(
                listOf(
                    association("Inglaterra", 30_000L),
                    association("Espanha", 28_000L)
                )
            )
        )

        assertEquals(
            UefaPerformanceSpotResolution.Rejected(UefaPerformanceSpotRejection.UNKNOWN_ASSOCIATION),
            UefaEuropeanPerformanceSpotAllocator.award(
                listOf(
                    association("Inglaterra", 30_000L),
                    association("Espanha", 28_000L),
                    association("País inventado", 27_000L)
                )
            )
        )

        assertEquals(
            UefaPerformanceSpotResolution.Rejected(UefaPerformanceSpotRejection.NON_UEFA_ASSOCIATION),
            UefaEuropeanPerformanceSpotAllocator.award(
                listOf(
                    association("Inglaterra", 30_000L),
                    association("Espanha", 28_000L),
                    association("Brasil", 90_000L)
                )
            )
        )

        assertEquals(
            UefaPerformanceSpotResolution.Rejected(UefaPerformanceSpotRejection.INVALID_COEFFICIENT),
            UefaEuropeanPerformanceSpotAllocator.award(
                listOf(
                    association("Inglaterra", 30_000L),
                    association("Espanha", -1L),
                    association("Itália", 27_000L)
                )
            )
        )

        assertEquals(
            UefaPerformanceSpotResolution.Rejected(UefaPerformanceSpotRejection.AMBIGUOUS_RANKING),
            UefaEuropeanPerformanceSpotAllocator.award(
                listOf(
                    association("Inglaterra", 30_000L),
                    association("Inglaterra", 31_000L),
                    association("Espanha", 28_000L)
                )
            )
        )
    }

    @Test
    fun `performance spot berth sits right below the direct domestic slots`() {
        // Resultado oficial 2026/27: Inglaterra e Espanha têm 4 vagas diretas e o EPS caiu no 5º.
        assertEquals(5, UefaEuropeanPerformanceSpotAllocator.berthPosition(4))
        assertEquals(4, UefaEuropeanPerformanceSpotAllocator.berthPosition(3))
        assertEquals(2, UefaEuropeanPerformanceSpotAllocator.berthPosition(1))

        // Um clube que já entrou por outra vaga (titleholder) não pode receber o EPS.
        assertEquals(6, UefaEuropeanPerformanceSpotAllocator.berthPosition(4, setOf(5)))
        assertEquals(7, UefaEuropeanPerformanceSpotAllocator.berthPosition(4, setOf(5, 6)))

        val invalid = runCatching { UefaEuropeanPerformanceSpotAllocator.berthPosition(0) }.isFailure
        assertTrue(invalid)
    }

    @Test
    fun `vacancy resolution only promotes clubs entered in the champions league`() {
        val entrants = listOf(
            champion(960L, "Ucrânia", UefaQualificationStage.Q2, 40_000L),
            // Caminho CHAMPIONS existe também na UEL/UECL: não pode virar vaga da Champions.
            UefaQualifyingEntrant(
                teamId = 961L,
                country = "Áustria",
                entryPoint = UefaEntryPoint(
                    UefaCompetitionCode.UECL,
                    UefaQualificationStage.Q3,
                    UefaQualificationPath.CHAMPIONS
                ),
                clubCoefficientMilli = 90_000L
            )
        )

        val resolution = UefaAccessRebalancer.resolveVacantTitleholderBerth(
            CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE,
            entrants
        )

        assertTrue(resolution is UefaVacancyResolution.Promoted)
        assertEquals(960L, (resolution as UefaVacancyResolution.Promoted).teamId)
    }

    @Test
    fun `explicit eligibility filter falls through to the next coefficient ranked club`() {
        val entrants = listOf(
            // Melhor coeficiente, mas não é o clube elegível pela ordem doméstica.
            leaguePath(970L, "Portugal", UefaQualificationStage.Q3, 80_000L),
            leaguePath(971L, "Portugal", UefaQualificationStage.Q2, 30_000L)
        )

        val unrestricted = UefaAccessRebalancer.resolveVacantTitleholderBerth(
            CompetitionIdentity.UEFA_EUROPA_LEAGUE,
            entrants
        )
        assertEquals(970L, (unrestricted as UefaVacancyResolution.Promoted).teamId)

        val restricted = UefaAccessRebalancer.resolveVacantTitleholderBerth(
            CompetitionIdentity.UEFA_EUROPA_LEAGUE,
            entrants,
            eligibleTeamIds = setOf(971L)
        )
        assertTrue(restricted is UefaVacancyResolution.Promoted)
        assertEquals(971L, (restricted as UefaVacancyResolution.Promoted).teamId)

        assertEquals(
            UefaVacancyResolution.Rejected(UefaVacancyRejection.NO_ELIGIBLE_CANDIDATE),
            UefaAccessRebalancer.resolveVacantTitleholderBerth(
                CompetitionIdentity.UEFA_EUROPA_LEAGUE,
                entrants,
                eligibleTeamIds = emptySet()
            )
        )
    }

    @Test
    fun `performance spot ties are broken by the most recent season per Annex D 8`() {
        // Ranking oficial: Chequia e Polonia empatam em 45.725 e a Chequia fica a frente
        // porque tem 3.900 contra 3.600 na epoca mais recente.
        val czechia = UefaAssociationSeasonCoefficient(
            country = "Tchéquia",
            coefficientMilli = 45_725L,
            tiebreakSeasonCoefficientsMilli = listOf(3_900L, 11_025L, 10_550L, 13_500L, 6_750L)
        )
        val poland = UefaAssociationSeasonCoefficient(
            country = "Polônia",
            coefficientMilli = 45_725L,
            tiebreakSeasonCoefficientsMilli = listOf(3_600L, 15_750L, 11_750L, 6_875L, 7_750L)
        )
        assertEquals(
            UefaPerformanceSpotResolution.Awarded(listOf("Inglaterra", "Tchéquia")),
            UefaEuropeanPerformanceSpotAllocator.award(
                listOf(association("Inglaterra", 50_000L), poland, czechia)
            )
        )

        // A decisao nao depende da ordem de entrada.
        assertEquals(
            UefaPerformanceSpotResolution.Awarded(listOf("Inglaterra", "Tchéquia")),
            UefaEuropeanPerformanceSpotAllocator.award(
                listOf(association("Inglaterra", 50_000L), czechia, poland)
            )
        )

        // Sem desempate possivel (quebras identicas) continua rejeitando.
        assertEquals(
            UefaPerformanceSpotResolution.Rejected(UefaPerformanceSpotRejection.AMBIGUOUS_RANKING),
            UefaEuropeanPerformanceSpotAllocator.award(
                listOf(
                    association("Inglaterra", 30_000L),
                    association("Espanha", 28_000L).copy(tiebreakSeasonCoefficientsMilli = listOf(19_000L)),
                    association("Itália", 28_000L).copy(tiebreakSeasonCoefficientsMilli = listOf(19_000L))
                )
            )
        )

        // A epoca mais recente decide antes das anteriores.
        assertEquals(
            UefaPerformanceSpotResolution.Awarded(listOf("Inglaterra", "Espanha")),
            UefaEuropeanPerformanceSpotAllocator.award(
                listOf(
                    association("Inglaterra", 30_000L),
                    association("Espanha", 28_000L)
                        .copy(tiebreakSeasonCoefficientsMilli = listOf(22_093L, 19_000L)),
                    association("Itália", 28_000L)
                        .copy(tiebreakSeasonCoefficientsMilli = listOf(19_000L, 21_875L))
                )
            )
        )

        assertEquals(
            UefaPerformanceSpotResolution.Rejected(UefaPerformanceSpotRejection.INVALID_COEFFICIENT),
            UefaEuropeanPerformanceSpotAllocator.award(
                listOf(
                    association("Inglaterra", 30_000L),
                    association("Espanha", 28_000L)
                        .copy(tiebreakSeasonCoefficientsMilli = listOf(-1L)),
                    association("Itália", 27_000L)
                )
            )
        )
    }

    private fun champion(
        teamId: Long,
        country: String,
        stage: UefaQualificationStage,
        coefficient: Long
    ) = UefaQualifyingEntrant(
        teamId = teamId,
        country = country,
        entryPoint = UefaEntryPoint(UefaCompetitionCode.UCL, stage, UefaQualificationPath.CHAMPIONS),
        clubCoefficientMilli = coefficient
    )

    private fun leaguePath(
        teamId: Long,
        country: String,
        stage: UefaQualificationStage,
        coefficient: Long
    ) = UefaQualifyingEntrant(
        teamId = teamId,
        country = country,
        entryPoint = UefaEntryPoint(UefaCompetitionCode.UCL, stage, UefaQualificationPath.LEAGUE),
        clubCoefficientMilli = coefficient
    )

    private fun association(country: String, coefficient: Long) =
        UefaAssociationSeasonCoefficient(country = country, coefficientMilli = coefficient)
}
