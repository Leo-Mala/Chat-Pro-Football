package com.example.data

/**
 * Regra única para encaixar ligas de pontos corridos no calendário canônico de 40 semanas.
 *
 * - se turno + returno couberem, usamos 2 turnos;
 * - se apenas um turno couber, usamos turno único;
 * - divisões gigantes com partição exata em grupos de 4..20 clubes usam grupos balanceados,
 *   todos com o mesmo tamanho e turno + returno em paralelo;
 * - tamanhos gigantes que ainda não admitem grupos iguais continuam explicitamente fora do
 *   formato detalhado e podem usar o fallback compacto até uma regra específica ser definida;
 * - a simulação global compacta não depende das 40 semanas: usa 2 turnos até 20 clubes e
 *   turno único acima disso para manter custo previsível sem persistir fixtures CPU.
 */
object LeagueSeasonFormat {

    const val MAX_DETAILED_GROUP_SIZE = 20
    const val MIN_DETAILED_GROUP_SIZE = 4

    data class DetailedGroupPlan(
        val groupCount: Int,
        val groupSize: Int,
        val legs: Int = 2
    ) {
        val rounds: Int
            get() = roundsPerLegStatic(groupSize) * legs

        companion object {
            private fun roundsPerLegStatic(teamCount: Int): Int {
                if (teamCount < 2) return 0
                return if (teamCount % 2 == 0) teamCount - 1 else teamCount
            }
        }
    }

    fun roundsPerLeg(teamCount: Int): Int {
        if (teamCount < 2) return 0
        return if (teamCount % 2 == 0) teamCount - 1 else teamCount
    }

    /**
     * Retorna um plano de grupos somente quando o round-robin direto não cabe e é possível
     * particionar todos os clubes em grupos de tamanho idêntico. Priorizamos o maior grupo
     * possível (até 20) para preservar variedade de adversários sem ultrapassar 40 semanas.
     */
    fun detailedGroupPlan(teamCount: Int): DetailedGroupPlan? {
        if (teamCount < 2 || fitsCurrentSeason(teamCount)) return null

        val groupSize = (MAX_DETAILED_GROUP_SIZE downTo MIN_DETAILED_GROUP_SIZE)
            .firstOrNull { candidate ->
                teamCount % candidate == 0 && roundsPerLeg(candidate) * 2 <= GameCalendar.WEEKS_PER_SEASON
            }
            ?: return null

        return DetailedGroupPlan(
            groupCount = teamCount / groupSize,
            groupSize = groupSize
        )
    }

    fun supportsDetailedFormat(teamCount: Int): Boolean {
        return fitsCurrentSeason(teamCount) || detailedGroupPlan(teamCount) != null
    }

    /**
     * Distribui clubes em grupos iguais por serpentina de força, com desempate por ID.
     * A associação fica determinística sem exigir nova coluna/tabela Room.
     */
    fun buildDetailedGroups(teams: List<Team>): List<List<Team>> {
        val plan = detailedGroupPlan(teams.size) ?: return listOf(teams)
        val orderedTeams = teams.sortedWith(
            compareByDescending<Team> { it.rating }
                .thenBy { it.id }
        )
        val groups = MutableList(plan.groupCount) { mutableListOf<Team>() }

        orderedTeams.forEachIndexed { index, team ->
            val row = index / plan.groupCount
            val column = index % plan.groupCount
            val groupIndex = if (row % 2 == 0) {
                column
            } else {
                plan.groupCount - 1 - column
            }
            groups[groupIndex] += team
        }

        check(groups.all { it.size == plan.groupSize }) {
            "Falha ao distribuir ${teams.size} clubes em ${plan.groupCount} grupos de ${plan.groupSize}."
        }
        return groups
    }

    fun legsForDetailedLeague(teamCount: Int): Int {
        detailedGroupPlan(teamCount)?.let { return it.legs }

        val rounds = roundsPerLeg(teamCount)
        if (rounds == 0) return 0

        return when {
            rounds * 2 <= GameCalendar.WEEKS_PER_SEASON -> 2
            rounds <= GameCalendar.WEEKS_PER_SEASON -> 1
            else -> 2 // Mantém compatibilidade para tamanhos ainda sem partição balanceada.
        }
    }

    /** Código canônico hoje persistido pelo calendário detalhado para cada nível. */
    fun detailedCompetitionTypeForDivision(division: Int): String {
        require(division > 0) { "Divisão deve ser positiva: $division" }
        return when (division) {
            1 -> "SERIE_A"
            2 -> "SERIE_B"
            3 -> "SERIE_C"
            else -> "SERIE_D"
        }
    }

    /**
     * Códigos aceitos ao ler fixtures detalhados.
     *
     * Mantém compatibilidade tanto com o código legado SERIE_* gravado pelo calendário quanto
     * com aliases division-aware `DIV_n`. Para níveis 5+, isso significa `SERIE_D` + `DIV_5`,
     * `SERIE_D` + `DIV_6` etc., evitando que snapshot e promoção usem regras divergentes.
     */
    fun acceptedDetailedCompetitionTypes(division: Int): Set<String> {
        return setOf(detailedCompetitionTypeForDivision(division), "DIV_$division")
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

        detailedGroupPlan(teamCount)?.let { plan ->
            return plan.groupCount * plan.groupSize * (plan.groupSize - 1) / 2 * plan.legs
        }

        val legs = legsForDetailedLeague(teamCount)
        return teamCount * (teamCount - 1) / 2 * legs
    }

    /**
     * Valida a topologia do calendário detalhado já filtrado para uma divisão.
     * Para grupos, os próprios confrontos definem componentes desconectados; cada componente
     * precisa conter exatamente o tamanho planejado e um turno + returno completo.
     */
    fun hasExpectedDetailedPairings(
        teamIds: Set<Long>,
        fixtures: List<Fixture>,
        legs: Int = legsForDetailedLeague(teamIds.size)
    ): Boolean {
        if (teamIds.size < 2 || fixtures.any {
                it.homeTeamId !in teamIds ||
                    it.awayTeamId !in teamIds ||
                    it.homeTeamId == it.awayTeamId
            }
        ) {
            return false
        }

        val plan = detailedGroupPlan(teamIds.size)
        if (plan != null) {
            if (fixtures.size != expectedFixtureCount(teamIds.size)) return false

            val adjacency = teamIds.associateWith { mutableSetOf<Long>() }.toMutableMap()
            fixtures.forEach { fixture ->
                adjacency.getValue(fixture.homeTeamId) += fixture.awayTeamId
                adjacency.getValue(fixture.awayTeamId) += fixture.homeTeamId
            }

            val remaining = teamIds.toMutableSet()
            val components = mutableListOf<Set<Long>>()
            while (remaining.isNotEmpty()) {
                val seed = remaining.first()
                val queue = ArrayDeque<Long>()
                val component = mutableSetOf<Long>()
                queue.add(seed)
                remaining.remove(seed)

                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    component += current
                    adjacency.getValue(current).forEach { neighbor ->
                        if (remaining.remove(neighbor)) {
                            queue.add(neighbor)
                        }
                    }
                }
                components += component
            }

            if (components.size != plan.groupCount || components.any { it.size != plan.groupSize }) {
                return false
            }

            val directedPairCounts = fixtures
                .groupingBy { it.homeTeamId to it.awayTeamId }
                .eachCount()

            return components.all { component ->
                component.all { homeId ->
                    component.all { awayId ->
                        homeId == awayId || directedPairCounts[homeId to awayId] == 1
                    }
                }
            }
        }

        val ids = teamIds.sorted()
        if (legs == 2) {
            val directedPairCounts = fixtures
                .groupingBy { it.homeTeamId to it.awayTeamId }
                .eachCount()
            return ids.all { homeId ->
                ids.all { awayId ->
                    homeId == awayId || directedPairCounts[homeId to awayId] == 1
                }
            }
        }

        val unorderedPairCounts = fixtures
            .groupingBy { minOf(it.homeTeamId, it.awayTeamId) to maxOf(it.homeTeamId, it.awayTeamId) }
            .eachCount()
        for (i in 0 until ids.lastIndex) {
            for (j in i + 1 until ids.size) {
                if (unorderedPairCounts[ids[i] to ids[j]] != 1) return false
            }
        }
        return true
    }

    /** Round-robin direto cabe sem recorrer a grupos. */
    fun fitsCurrentSeason(teamCount: Int): Boolean {
        val rounds = roundsPerLeg(teamCount)
        return rounds in 1..GameCalendar.WEEKS_PER_SEASON
    }
}
