package com.example.data

/**
 * Projeção tipada de qualificação UEFA.
 *
 * O projeto ainda não persiste coeficientes UEFA nem a access list completa de qualifying. Por
 * isso não fingimos esses dados: quando há snapshot doméstico da temporada anterior, a ordem de
 * cada associação segue a posição esportiva real persistida. Na primeira temporada (ou para uma
 * associação sem snapshot), o fallback permanece determinístico por divisão/id.
 */
object UefaQualificationRules {
    const val FIELD_SIZE = 36

    data class QualifiedTeam(
        val team: Team,
        val slot: QualificationSlot
    )

    data class LeaguePhaseFields(
        val championsLeague: List<QualifiedTeam>,
        val europaLeague: List<QualifiedTeam>,
        val conferenceLeague: List<QualifiedTeam>
    ) {
        val all: List<QualifiedTeam>
            get() = championsLeague + europaLeague + conferenceLeague
    }

    fun selectLeaguePhaseFields(candidates: List<Team>): LeaguePhaseFields =
        selectLeaguePhaseFields(candidates, previousSeasonStandings = emptyList())

    fun selectLeaguePhaseFields(
        candidates: List<Team>,
        previousSeasonStandings: List<GlobalLeagueStanding>
    ): LeaguePhaseFields {
        val eligible = candidates
            .asSequence()
            .filter { CountryFootballRulesRegistry.isContinentalCompetitionEligible(it.country) }
            .filter { CountryFootballRulesRegistry.confederationFor(it.country) == FootballConfederation.UEFA }
            .distinctBy { it.id }
            .toList()

        val standingsByTeamId = previousSeasonStandings
            .asSequence()
            .filter { it.division == 1 }
            .associateBy { it.teamId }
        val countriesWithSnapshot = previousSeasonStandings
            .asSequence()
            .filter { it.division == 1 }
            .map { canonicalAssociation(it.country) }
            .toSet()

        val byAssociation = eligible
            .groupBy { canonicalAssociation(it.country) }
            .toSortedMap()
            .mapValues { (association, teams) ->
                teams.sortedWith(
                    compareBy<Team> { team ->
                        val row = standingsByTeamId[team.id]
                        when {
                            row != null -> row.position
                            association in countriesWithSnapshot && team.division == 1 -> Int.MAX_VALUE - 1
                            else -> Int.MAX_VALUE
                        }
                    }
                        .thenByDescending { standingsByTeamId[it.id]?.points ?: Int.MIN_VALUE }
                        .thenBy { it.division }
                        .thenBy { it.id }
                )
            }

        // Intercala associações: 1º de cada país, 2º de cada país etc. Isso evita preencher um
        // torneio inteiro com uma única liga quando não há coeficiente/access-list persistidos.
        val ordered = buildList {
            var associationSlot = 0
            while (size < eligible.size) {
                var added = false
                for (association in byAssociation.keys) {
                    val team = byAssociation.getValue(association).getOrNull(associationSlot) ?: continue
                    add(
                        QualifiedTeam(
                            team = team,
                            slot = QualificationSlot(
                                source = QualificationSource.AssociationSlot(
                                    association = association,
                                    slot = associationSlot + 1
                                ),
                                ordinal = associationSlot + 1
                            )
                        )
                    )
                    added = true
                }
                if (!added) break
                associationSlot++
            }
        }

        var offset = 0
        fun takeField(): List<QualifiedTeam> {
            if (ordered.size - offset < FIELD_SIZE) return emptyList()
            val field = ordered.subList(offset, offset + FIELD_SIZE).toList()
            offset += FIELD_SIZE
            return field
        }

        val champions = takeField()
        val europa = takeField()
        val conference = takeField()
        val allIds = (champions + europa + conference).map { it.team.id }
        require(allIds.size == allIds.toSet().size) {
            "Um clube não pode ocupar duas competições UEFA principais na mesma temporada."
        }

        return LeaguePhaseFields(
            championsLeague = champions,
            europaLeague = europa,
            conferenceLeague = conference
        )
    }

    private fun canonicalAssociation(country: String): String =
        CountryFootballRulesRegistry.resolve(country)?.canonicalCountry ?: country.trim()
}
