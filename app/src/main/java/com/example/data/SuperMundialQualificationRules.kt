package com.example.data

/**
 * Projeção esportiva do field de 32 clubes do Mundial quadrienal.
 *
 * O banco ainda não persiste o ranking FIFA/coeficiente continental de quatro anos nem a
 * identidade tipada dos campeões continentais. Por isso esta fase não inventa esses dados:
 * usa snapshots domésticos persistidos como prioridade esportiva e a alocação estrutural do
 * torneio. Entradas agregadas legadas nunca recebem vaga nem sede.
 *
 * O universo factual atual não possui uma associação nacional OFC persistida. Nesse único data-gap
 * conhecido, a vaga OFC é convertida em uma vaga esportiva suplementar entre clubes nacionais reais
 * já persistidos, em vez de criar um clube virtual ou promover um agregado "Oceania". O fallback é
 * explicitamente marcado em [QualifiedField] e pode ser removido quando o dataset possuir OFC real.
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
        val usedSportingSnapshot: Boolean,
        val usedOfcDataGapFallback: Boolean
    ) {
        init {
            require(teams.size == FIELD_SIZE) { "Mundial exige exatamente $FIELD_SIZE clubes." }
            require(teams.map { it.id }.toSet().size == FIELD_SIZE) {
                "Mundial não aceita teamId duplicado."
            }
            require(teams.any { it.id == host.id }) { "A vaga do anfitrião deve estar no field." }
            require(teams.all { CountryFootballRulesRegistry.isContinentalCompetitionEligible(it.country) }) {
                "Mundial aceita somente clubes de associações nacionais tipadas."
            }
        }
    }

    internal fun eligibleRealTeams(allTeams: List<Team>): List<Team> =
        allTeams
            .asSequence()
            .filter { it.id > 0L }
            .filter { CountryFootballRulesRegistry.isContinentalCompetitionEligible(it.country) }
            .filter { CountryFootballRulesRegistry.confederationFor(it.country) != null }
            .distinctBy { it.id }
            .toList()

    fun selectField(
        season: Int,
        allTeams: List<Team>,
        previousSeasonStandings: List<GlobalLeagueStanding> = emptyList()
    ): QualifiedField? {
        if (!SuperMundialEditionPolicy.isEditionSeason(season)) return null

        val realTeams = eligibleRealTeams(allTeams)
        val host = SuperMundialEditionPolicy.hostTeamForSeason(season, realTeams) ?: return null
        val standingsByTeamId = previousSeasonStandings
            .asSequence()
            .filter { it.division == 1 }
            .associateBy { it.teamId }
        val hasSportingSnapshot = standingsByTeamId.keys.any { id -> realTeams.any { it.id == id } }

        val selected = mutableListOf<Team>()
        var supplementalSlots = 0
        var usedOfcDataGapFallback = false

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
            if (confederationField.size != quota) {
                // O registry factual ainda não possui associação nacional OFC. Não materializamos
                // clube fictício: convertemos apenas esse slot ausente em supplemental real.
                if (confederation == FootballConfederation.OFC && confederationCandidates.isEmpty()) {
                    supplementalSlots += quota
                    usedOfcDataGapFallback = true
                    return@forEach
                }
                return null
            }
            selected += confederationField
        }

        if (supplementalSlots > 0) {
            val excluded = selected.map { it.id }.toSet() + host.id
            val supplemental = sportingOrder(
                realTeams.filterNot { it.id in excluded },
                standingsByTeamId
            ).take(supplementalSlots)
            if (supplemental.size != supplementalSlots) return null
            selected += supplemental
        }

        selected += host
        val finalField = selected.distinctBy { it.id }
        if (finalField.size != FIELD_SIZE) return null

        return QualifiedField(
            season = season,
            host = host,
            teams = finalField,
            usedSportingSnapshot = hasSportingSnapshot,
            usedOfcDataGapFallback = usedOfcDataGapFallback
        )
    }

    private fun associationRoundRobin(
        candidates: List<Team>,
        standingsByTeamId: Map<Long, GlobalLeagueStanding>,
        target: Int
    ): List<Team> {
        if (target <= 0) return emptyList()

        data class AssociationCandidate(val team: Team, val associationSlot: Int)

        val ordered = candidates
            .groupBy(::associationFor)
            .flatMap { (_, associationTeams) ->
                associationTeams
                    .sortedWith(sportingComparator(standingsByTeamId))
                    .mapIndexed { index, team -> AssociationCandidate(team, index + 1) }
            }
            .sortedWith(
                compareBy<AssociationCandidate> { it.associationSlot }
                    .thenByDescending { standingsByTeamId[it.team.id]?.points ?: Int.MIN_VALUE }
                    .thenBy { it.team.division }
                    .thenBy { it.team.id }
            )

        return ordered.take(target).map { it.team }
    }

    private fun sportingOrder(
        candidates: List<Team>,
        standingsByTeamId: Map<Long, GlobalLeagueStanding>
    ): List<Team> = candidates.sortedWith(sportingComparator(standingsByTeamId))

    private fun sportingComparator(
        standingsByTeamId: Map<Long, GlobalLeagueStanding>
    ): Comparator<Team> =
        compareBy<Team> { team ->
            val row = standingsByTeamId[team.id]
            if (row != null && team.division == 1) row.position else Int.MAX_VALUE
        }
            .thenByDescending { team ->
                if (team.division == 1) standingsByTeamId[team.id]?.points ?: Int.MIN_VALUE
                else Int.MIN_VALUE
            }
            .thenBy { it.division }
            .thenBy { it.id }

    private fun associationFor(team: Team): String =
        requireNotNull(CountryFootballRulesRegistry.resolve(team.country)).canonicalCountry
}
