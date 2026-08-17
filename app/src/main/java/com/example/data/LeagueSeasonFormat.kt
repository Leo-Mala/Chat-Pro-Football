package com.example.data

/**
 * Regra única para encaixar ligas de pontos corridos no calendário canônico de 40 semanas.
 *
 * - se turno + returno couberem, usamos 2 turnos;
 * - se apenas um turno couber, usamos turno único;
 * - divisões grandes demais até para um turno mantêm 2 turnos por compatibilidade legada no
 *   calendário detalhado e continuam exigindo formato próprio por grupos/estágios;
 * - a simulação global compacta não depende das 40 semanas: usa 2 turnos até 20 clubes e
 *   turno único acima disso para manter custo previsível sem persistir fixtures CPU.
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

    /**
     * Política exclusiva da simulação CPU em memória.
     *
     * Como essas partidas não ocupam semanas e nunca são persistidas, não precisamos forçar
     * o calendário detalhado de 40 semanas. Mesmo assim, ligas muito grandes ficam em turno
     * único para evitar duplicar milhares de confrontos sem benefício proporcional.
     */
    fun legsForCompactSimulation(teamCount: Int): Int {
        if (teamCount < 2) return 0
        return if (teamCount <= 20) 2 else 1
    }

    /**
     * Decide o mando em ligas compactas de turno único sem favorecer IDs baixos ou altos.
     *
     * Para um campeonato completo, a paridade dos índices distribui os mandos de modo que a
     * diferença entre quaisquer clubes seja no máximo um jogo. Temporada e divisão entram na
     * paridade para inverter a orientação ao longo dos anos sem introduzir aleatoriedade.
     */
    fun firstTeamHostsCompactSingleLeg(
        firstIndex: Int,
        secondIndex: Int,
        season: Int,
        division: Int
    ): Boolean {
        require(firstIndex >= 0 && secondIndex > firstIndex) {
            "Índices de confronto inválidos: $firstIndex x $secondIndex"
        }
        return (firstIndex + secondIndex + season + division) % 2 == 0
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
