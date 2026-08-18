package com.example.data

/**
 * Projeção de acesso UEFA compatível com o modelo atual da carreira.
 *
 * O formato 2026/27 das três competições é implementado pelo [UefaCompetitionSystem], porém o
 * projeto ainda não persiste coeficientes UEFA, campeões continentais tipados por temporada nem a
 * access list anual completa. Por isso esta camada NÃO usa [Team.rating] como falso coeficiente.
 * Em vez disso, distribui vagas de forma determinística entre associações nacionais UEFA e preserva
 * a origem de cada vaga por [QualificationSource.AssociationSlot].
 *
 * Quando coeficientes/access list forem persistidos no domínio, esta projeção pode ser substituída
 * sem alterar o motor de partidas, pois o contrato de saída já é tipado.
 */
object UefaQualificationRules {

    data class QualifiedClub(
        val team: Team,
        val slot: QualificationSlot
    )

    data class LeaguePhaseFields(
        val championsLeague: List<QualifiedClub>,
        val europaLeague: List<QualifiedClub>,
        val conferenceLeague: List<QualifiedClub>
    ) {
        val all: List<QualifiedClub>
            get() = championsLeague + europaLeague + conferenceLeague
    }

    fun selectLeaguePhaseFields(candidates: List<Team>): LeaguePhaseFields {
        val eligible = candidates
            .asSequence()
            .filter { CountryFootballRulesRegistry.isContinentalCompetitionEligible(it.country) }
            .filter { CountryFootballRulesRegistry.confederationFor(it.country) == FootballConfederation.UEFA }
            .distinctBy { it.id }
            .toList()

        val canonicalByTeamId = eligible.associate { team ->
            team.id to requireNotNull(CountryFootballRulesRegistry.resolve(team.country)).canonicalCountry
        }

        val countries = canonicalByTeamId.values.toSortedSet().toList()
        val teamsByCountry = countries.associateWith { country ->
            eligible
                .filter { canonicalByTeamId[it.id] == country }
                .sortedWith(compareBy<Team> { it.division }.thenBy { it.id })
        }

        // Round-robin por associação: primeiro slot de cada país, depois segundo etc. Isso impede
        // que uma única associação monopolize a seleção e mantém o resultado independente de rating.
        val ordered = mutableListOf<Pair<Team, Int>>()
        val largestAssociation = teamsByCountry.values.maxOfOrNull { it.size } ?: 0
        for (associationSlot in 1..largestAssociation) {
            countries.forEach { country ->
                teamsByCountry.getValue(country).getOrNull(associationSlot - 1)?.let { team ->
                    ordered += team to associationSlot
                }
            }
        }

        var offset = 0
        fun takeField(
            identity: CompetitionIdentity
        ): List<QualifiedClub> {
            val selected = ordered.drop(offset).take(UefaCompetitionSystem.FIELD_SIZE)
            offset += selected.size
            return selected.mapIndexed { index, (team, associationSlot) ->
                val association = canonicalByTeamId.getValue(team.id)
                QualifiedClub(
                    team = team,
                    slot = QualificationSlot(
                        source = QualificationSource.AssociationSlot(
                            association = association,
                            slot = associationSlot
                        ),
                        destinationCompetition = identity,
                        ordinal = index + 1
                    )
                )
            }
        }

        return LeaguePhaseFields(
            championsLeague = takeField(CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE),
            europaLeague = takeField(CompetitionIdentity.UEFA_EUROPA_LEAGUE),
            conferenceLeague = takeField(CompetitionIdentity.UEFA_CONFERENCE_LEAGUE)
        )
    }
}
