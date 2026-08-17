package com.example.data

/**
 * Converte a classificação da temporada anterior em prioridade de qualificação continental.
 *
 * O [CupCompetitionSystem] já ordena candidatos por divisão e rating. Para preservar esse
 * contrato sem acoplar Room ao gerador de copas, aplicamos um rating transitório somente à
 * lista usada para gerar as competições. Os [Team] persistidos não são modificados.
 *
 * Campeões de cada país recebem prioridade 100, vice 99, terceiro 98 etc. Assim os campeões
 * de uma confederação entram antes dos vices, depois terceiros e assim por diante. Países sem
 * snapshot continuam usando o rating normal como fallback, necessário na primeira temporada.
 */
object ContinentalQualificationRules {

    fun applyPreviousSeasonStandings(
        teams: List<Team>,
        standings: List<GlobalLeagueStanding>
    ): List<Team> {
        if (standings.isEmpty()) return teams

        val topDivisionRows = standings.filter { it.division == 1 }
        if (topDivisionRows.isEmpty()) return teams

        val rowByTeamId = topDivisionRows.associateBy { it.teamId }
        val countriesWithStandings = topDivisionRows.map { it.country }.toSet()

        return teams.map { team ->
            val row = rowByTeamId[team.id]
            when {
                row != null && team.division == 1 -> team.copy(
                    rating = (101 - row.position).coerceIn(1, 100)
                )

                team.division == 1 && team.country in countriesWithStandings -> {
                    // Clube recém-promovido: não herda vaga continental da temporada em que
                    // ainda não disputava a primeira divisão.
                    team.copy(rating = 1)
                }

                // Rebaixados e demais divisões mantêm o rating real. Isso impede que a
                // prioridade continental altere indiretamente o corte da Copa nacional.
                else -> team
            }
        }
    }
}
