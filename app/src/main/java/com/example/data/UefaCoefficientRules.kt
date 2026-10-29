package com.example.data

import kotlin.math.max

/**
 * Regras necessárias para reconstruir os rankings de coeficiente usados na temporada UEFA 2026/27,
 * sempre em milésimos de ponto.
 *
 * A access list 2026/27 usa os coeficientes de associação de 2020/21..2024/25 e o ranking de clubes
 * usa os coeficientes de clube de 2021/22..2025/26. Nos Regulamentos 2026/27, os valores de pontos
 * de clube de D.4.1..D.4.3 referem-se expressamente aos pontos atribuídos em 2025/26. Portanto esta
 * classe é a fundação do ranking de entrada/seeding 2026/27; uma temporada futura deve usar as
 * regras do regulamento daquela própria temporada em vez de assumir que estes pesos nunca mudam.
 *
 * Long evita erro binário de Double em valores como 0.125/0.250 e aplica naturalmente a regra
 * UEFA de calcular até o milésimo sem arredondar para cima.
 */
object UefaCoefficientRules {
    const val MILLI_PER_POINT = 1_000L

    val ACCESS_ASSOCIATION_REFERENCE_SEASONS: IntRange = 2021..2025 // 2020/21 .. 2024/25 pelo ano final
    val CLUB_REFERENCE_SEASONS: IntRange = 2022..2026 // 2021/22 .. 2025/26 pelo ano final

    enum class MatchResult { WIN, DRAW, LOSS }
    enum class KnockoutMilestone { ROUND_OF_16, QUARTERFINAL, SEMIFINAL, FINAL }

    /** Pontos que entram no coeficiente da associação. */
    fun associationMatchPoints(
        result: MatchResult,
        qualifyingOrPlayoff: Boolean
    ): Long = when (result) {
        MatchResult.WIN -> if (qualifyingOrPlayoff) 1_000L else 2_000L
        MatchResult.DRAW -> if (qualifyingOrPlayoff) 500L else 1_000L
        MatchResult.LOSS -> 0L
    }

    /** Pontos de clube da league phase em diante na época de referência; shoot-out não conta. */
    fun clubLeaguePhaseMatchPoints(resultBeforeShootout: MatchResult): Long = when (resultBeforeShootout) {
        MatchResult.WIN -> 2_000L
        MatchResult.DRAW -> 1_000L
        MatchResult.LOSS -> 0L
    }

    fun associationSeasonCoefficient(
        totalPointsMilli: Long,
        entitledEntrants: Int
    ): Long {
        require(totalPointsMilli >= 0L)
        require(entitledEntrants > 0)
        return totalPointsMilli / entitledEntrants
    }

    fun associationFiveSeasonCoefficient(seasonCoefficientsMilli: List<Long>): Long {
        require(seasonCoefficientsMilli.size == 5)
        require(seasonCoefficientsMilli.all { it >= 0L })
        return seasonCoefficientsMilli.sum()
    }

    /**
     * Coeficiente de clube em cinco épocas = maior entre soma própria e 20% do coeficiente da
     * associação. A divisão inteira preserva milésimos e trunca qualquer fração adicional.
     */
    fun clubFiveSeasonCoefficient(
        clubSeasonCoefficientsMilli: List<Long>,
        associationFiveSeasonCoefficientMilli: Long
    ): Long {
        require(clubSeasonCoefficientsMilli.size == 5)
        require(clubSeasonCoefficientsMilli.all { it >= 0L })
        require(associationFiveSeasonCoefficientMilli >= 0L)
        val own = clubSeasonCoefficientsMilli.sum()
        val associationFloor = associationFiveSeasonCoefficientMilli * 20L / 100L
        return max(own, associationFloor)
    }

    /** Pontuação fixa de clube quando eliminado nas qualificatórias da Conference League. */
    fun conferenceQualifyingEliminationPoints(stage: UefaQualificationStage): Long = when (stage) {
        UefaQualificationStage.Q1 -> 1_000L
        UefaQualificationStage.Q2 -> 1_500L
        UefaQualificationStage.Q3 -> 2_000L
        UefaQualificationStage.PLAYOFF -> 2_500L
        UefaQualificationStage.LEAGUE_PHASE -> error("League phase não é eliminação qualificatória.")
    }

    fun leaguePhaseGuaranteedMinimum(competition: UefaCompetitionCode): Long = when (competition) {
        UefaCompetitionCode.UCL -> 0L
        UefaCompetitionCode.UEL -> 3_000L
        UefaCompetitionCode.UECL -> 2_500L
    }

    /** Bônus pela posição final da league phase, Annex D.5. */
    fun leaguePositionBonus(competition: UefaCompetitionCode, position: Int): Long {
        require(position in 1..36)
        return when (competition) {
            UefaCompetitionCode.UCL -> if (position <= 24) {
                12_000L - (position - 1L) * 250L
            } else {
                6_000L
            }
            UefaCompetitionCode.UEL -> if (position <= 24) {
                6_000L - (position - 1L) * 250L
            } else {
                0L
            }
            UefaCompetitionCode.UECL -> when (position) {
                in 1..9 -> 4_000L - (position - 1L) * 250L
                in 10..24 -> 1_875L - (position - 10L) * 125L
                else -> 0L
            }
        }
    }

    /** Bônus por alcançar cada etapa listada; deve ser somado uma vez por etapa alcançada. */
    fun knockoutMilestoneBonus(
        competition: UefaCompetitionCode,
        milestone: KnockoutMilestone
    ): Long {
        // Todos os quatro milestones têm o mesmo valor dentro de cada competição no Annex D.5.
        check(milestone in KnockoutMilestone.entries)
        return when (competition) {
            UefaCompetitionCode.UCL -> 1_500L
            UefaCompetitionCode.UEL -> 1_000L
            UefaCompetitionCode.UECL -> 500L
        }
    }

    fun formatMilli(milli: Long): String {
        require(milli >= 0L)
        return "%d.%03d".format(milli / 1_000L, milli % 1_000L)
    }
}
