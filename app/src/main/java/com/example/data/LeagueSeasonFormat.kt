package com.example.data

/**
 * Regra única para encaixar ligas de pontos corridos no calendário canônico de 40 semanas.
 *
 * - se turno + returno couberem, usamos 2 turnos;
 * - se apenas um turno couber, usamos turno único;
 * - divisões grandes demais até para um turno mantêm 2 turnos por compatibilidade legada e
 *   continuam fora do escopo desta fase; elas exigirão formato próprio por grupos/estágios.
 */
object LeagueSeasonFormat {

    fun roundsPerLeg(teamCount: Int): Int {
        if (teamCount < 2) return 0
        return if (teamCount % 2 == 0) teamCount - 1 else teamCount
    }

    fun legsForDetailedLeague(teamCount: Int): Int {
        val rounds = roundsPerLeg(teamCount)
        if (rounds == 0) return 0

        return when {
            rounds * 2 <= GameCalendar.WEEKS_PER_SEASON -> 2
            rounds <= GameCalendar.WEEKS_PER_SEASON -> 1
            else -> 2 // Formatos gigantes serão tratados em subfase dedicada.
        }
    }

    fun expectedFixtureCount(teamCount: Int): Int {
        if (teamCount < 2) return 0
        val legs = legsForDetailedLeague(teamCount)
        return teamCount * (teamCount - 1) / 2 * legs
    }

    fun fitsCurrentSeason(teamCount: Int): Boolean {
        val rounds = roundsPerLeg(teamCount)
        return rounds in 1..GameCalendar.WEEKS_PER_SEASON
    }
}
