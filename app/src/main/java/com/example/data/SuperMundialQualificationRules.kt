package com.example.data

/**
 * Projeção esportiva e fail-closed do field de 32 clubes do Mundial quadrienal.
 *
 * O banco ainda não persiste o ranking FIFA/coeficiente continental de quatro anos nem a
 * identidade tipada dos campeões continentais. Por isso esta fase não inventa esses dados:
 * usa os snapshots domésticos persistidos como prioridade esportiva e distribui as vagas
 * regulares pela alocação estrutural do torneio (31 vagas + anfitrião). Quando uma confederação
 * não possui clubes reais suficientes no save, o field é recusado em vez de criar fillers.
 */
object SuperMundialQualificationRules {
    const val FIELD_SIZE = 32

    /** Vagas regulares; a 32ª vaga é a do anfitrião. */
    val regularSlots: Map<FootballConfederation, Int> = linkedMapOf(
        FootballConfederation.UEFA to 12,
        FootballConfederation.CONMEBOL to 6,
        FootballConfederation.AFC to 4,
        FootballConfederation.CAF to 4,
        FootballConfederation.CONCACAF to 4,
        FootballConfederation.OFC to 1
    )

    data class QualifiedField(
        val season: Int,
        val host: Team,
        val teams: List<Team>,
        val usedSportingSnapshot: Boolean
    ) {
        init {
            require(teams.size == FIELD_SIZE) { "Mundial exige exatamente $FIELD_SIZE clubes." }
            require(teams.map { it.id }.toSet().size == FIELD_SIZE) {
                "Mundial não aceita teamId duplicado."
            }
            require(teams.any { it.id == host.id }) { "A vaga do anfitrião deve estar no field." }
        }
    }

    fun selectField(
        season: Int,
        allTeams: List<Team>,
        previousSeasonStandings: List<GlobalLeagueStanding> = emptyList()
    ): QualifiedField? {
        if (!SuperMundialEditionPolicy.isEditionSeason(season)) return null

        val realTeams = allTeams
            .asSequence()
            .filter { it.id > 0L }
            .filter { !it.country.equals("Mundial", ignoreCase = true) }
            .filter { CountryFootballRulesRegistry.confederationFor(it.country) != null }
            .distinctBy { it.id }
            .toList()

        val host = SuperMundialEditionPolicy.hostTeamForSeason(season, realTeams) ?: return null
        val standingsByTeamId = previousSeasonStandings
            .asSequence()
            .filter { it.division == 1 }
            .associateBy { it.teamId }
        val hasSportingSnapshot = standingsByTeamId.isNotEmpty()

        val selected = mutableListOf<Team>()
        regularSlots.forEach { (confederation, quota) ->
            val confederationCandidates = realTeams.filter { team ->
                team.id != host.id &&
                    CountryFootballRulesRegistry.confederationFor(team.country) == confederation
            }
            val confederationField = associationRoundRobin(
                candidates = confederationCandidates,
                standingsByTeamId = standingsByTeamId,
                target = quota
            )
            if (confederationField.size != quota) return null
            selected += confederationField
        }

        selected += host
        val finalField = selected.distinctBy { it.id }
        if (finalField.size != FIELD_SIZE) return null

        return QualifiedField(
            season = season,
            host = host,
            teams = finalField,
            usedSportingSnapshot = hasSportingSnapshot
        )
    }

    private fun associationRoundRobin(
        candidates: List<Team>,
        standingsByTeamId: Map<Long, GlobalLeagueStanding>,
        target: Int
    ): List<Team> {
        if (target <= 0) return emptyList()

        val byAssociation = candidates
            .groupBy(::associationFor)
            .toSortedMap()
            .mapValues { (_, teams) ->
                teams.sortedWith(
                    compareBy<Team> { standingsByTeamId[it.id]?.position ?: Int.MAX_VALUE }
                        .thenByDescending { standingsByTeamId[it.id]?.points ?: Int.MIN_VALUE }
                        .thenBy { it.division }
                        .thenBy { it.id }
                )
            }

        val result = mutableListOf<Team>()
        var slot = 0
        while (result.size < target) {
            var addedInPass = false
            for (association in byAssociation.keys) {
                val candidate = byAssociation.getValue(association).getOrNull(slot) ?: continue
                result += candidate
                addedInPass = true
                if (result.size == target) break
            }
            if (!addedInPass) break
            slot++
        }
        return result
    }

    private fun associationFor(team: Team): String =
        CountryFootballRulesRegistry.resolve(team.country)?.canonicalCountry
            ?: team.country.trim()
}
