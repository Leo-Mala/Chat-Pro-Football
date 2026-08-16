package com.example.data

import kotlin.random.Random

/**
 * Regras comuns para partidas eliminatórias.
 *
 * Mantém a decisão de empates determinística para que simulação automática,
 * partida pulada e progressão de chave produzam o mesmo vencedor para o mesmo fixture.
 */
object CompetitionRules {
    private val knockoutTypes = setOf(
        "COPA",
        "CONTINENTAL_T1",
        "CONTINENTAL_T2",
        "CONTINENTAL_T3",
        "WORLD_CUP"
    )

    fun isKnockoutCompetition(competitionType: String): Boolean =
        competitionType in knockoutTypes

    fun resolvePenaltiesIfNeeded(
        fixture: Fixture,
        homeScore: Int,
        awayScore: Int
    ): Pair<Int?, Int?> {
        if (!isKnockoutCompetition(fixture.competitionType) || homeScore != awayScore) {
            return Pair(null, null)
        }

        val seed = stableSeed(fixture)
        val random = Random(seed)
        var homePenalties = random.nextInt(3, 6)
        var awayPenalties = random.nextInt(3, 6)

        if (homePenalties == awayPenalties) {
            if (random.nextBoolean()) {
                homePenalties += 1
            } else {
                awayPenalties += 1
            }
        }

        return Pair(homePenalties, awayPenalties)
    }

    fun ensureKnockoutDecision(fixture: Fixture): Fixture {
        if (!fixture.isPlayed) return fixture
        val homeScore = fixture.homeScore ?: return fixture
        val awayScore = fixture.awayScore ?: return fixture
        if (!isKnockoutCompetition(fixture.competitionType) || homeScore != awayScore) {
            return fixture
        }
        if (fixture.homePenalties != null && fixture.awayPenalties != null &&
            fixture.homePenalties != fixture.awayPenalties
        ) {
            return fixture
        }

        val (homePenalties, awayPenalties) = resolvePenaltiesIfNeeded(
            fixture,
            homeScore,
            awayScore
        )
        return fixture.copy(
            homePenalties = homePenalties,
            awayPenalties = awayPenalties
        )
    }

    fun winnerOf(fixture: Fixture): Long? {
        if (!fixture.isPlayed) return null
        val decided = ensureKnockoutDecision(fixture)
        val homeScore = decided.homeScore ?: return null
        val awayScore = decided.awayScore ?: return null

        return when {
            homeScore > awayScore -> decided.homeTeamId
            awayScore > homeScore -> decided.awayTeamId
            (decided.homePenalties ?: -1) > (decided.awayPenalties ?: -1) -> decided.homeTeamId
            (decided.awayPenalties ?: -1) > (decided.homePenalties ?: -1) -> decided.awayTeamId
            else -> if ((stableSeed(decided) and 1L) == 0L) decided.homeTeamId else decided.awayTeamId
        }
    }

    private fun stableSeed(fixture: Fixture): Long {
        var seed = fixture.season.toLong() * 1_000_003L
        seed = seed xor (fixture.week.toLong() * 10_007L)
        seed = seed xor (fixture.homeTeamId * 97L)
        seed = seed xor (fixture.awayTeamId * 193L)
        seed = seed xor fixture.competitionType.hashCode().toLong()
        if (fixture.id != 0L) seed = seed xor (fixture.id * 389L)
        return seed
    }
}
