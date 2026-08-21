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
            .filter { it.division == 1 }
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
            .mapValues { (association, teams) ->
                teams.sortedWith(
                    compareBy<Team> { team ->
                        val row = standingsByTeamId[team.id]
                        when {
                            row != null -> row.position
                            association in countriesWithSnapshot -> Int.MAX_VALUE - 1
                            else -> Int.MAX_VALUE
                        }
                    }
                        .thenByDescending { team -> standingsByTeamId[team.id]?.points ?: Int.MIN_VALUE }
                        .thenBy { it.id }
                )
            }

        data class OrderedCandidate(
            val team: Team,
            val associationSlot: Int
        )

        // O slot doméstico continua sendo intercalado (campeões antes de vices etc.), mas países
        // não são mais favorecidos por ordem alfabética. Dentro do mesmo slot, o snapshot esportivo
        // e um fallback estável por id definem a ordem até existir uma access list/coeficiente real.
        val ordered = byAssociation
            .flatMap { (_, teams) ->
                teams.mapIndexed { index, team -> OrderedCandidate(team, index + 1) }
            }
            .sortedWith(
                compareBy<OrderedCandidate> { it.associationSlot }
                    .thenByDescending { candidate -> standingsByTeamId[candidate.team.id]?.points ?: Int.MIN_VALUE }
                    .thenBy { it.team.id }
            )

        var offset = 0
        fun takeField(destinationCompetition: CompetitionIdentity): List<QualifiedTeam> {
            if (ordered.size - offset < FIELD_SIZE) return emptyList()
            val field = ordered.subList(offset, offset + FIELD_SIZE).toList()
            offset += FIELD_SIZE
            val qualified = field.mapIndexed { index, candidate ->
                QualifiedTeam(
                    team = candidate.team,
                    slot = QualificationSlot(
                        source = QualificationSource.AssociationSlot(
                            association = canonicalAssociation(candidate.team.country),
                            slot = candidate.associationSlot
                        ),
                        destinationCompetition = destinationCompetition,
                        ordinal = index + 1
                    )
                )
            }

            // A ordem esportiva define os potes e o ordinal de acesso. O passe de sorteio apenas
            // permuta clubes dentro do mesmo pote para impedir confrontos entre a mesma associação.
            // Se a combinação for impossível, a competição falha fechada em vez de gerar fixtures
            // inválidos ou mover um clube para outro tier europeu.
            return UefaLeaguePhaseAssociationDraw.arrange(qualified, destinationCompetition)
        }

        val champions = takeField(CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE)
        val europa = takeField(CompetitionIdentity.UEFA_EUROPA_LEAGUE)
        val conference = takeField(CompetitionIdentity.UEFA_CONFERENCE_LEAGUE)
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
