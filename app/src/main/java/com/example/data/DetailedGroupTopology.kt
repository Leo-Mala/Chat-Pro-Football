package com.example.data

/**
 * Topologia derivada dos fixtures detalhados de uma liga agrupada.
 *
 * Não persiste grupo em Room: a identidade de cada grupo é reconstruída a partir dos
 * componentes desconectados do calendário, depois de a topologia completa ser validada por
 * [LeagueSeasonFormat.hasExpectedDetailedPairings].
 */
object DetailedGroupTopology {

    /**
     * Retorna os grupos em ordem determinística (menor teamId do componente primeiro).
     * Retorna null quando a divisão não usa grupos ou quando a topologia está incompleta/corrompida.
     */
    fun components(
        teamIds: Set<Long>,
        fixtures: List<Fixture>
    ): List<Set<Long>>? {
        val plan = LeagueSeasonFormat.detailedGroupPlan(teamIds.size) ?: return null
        if (!LeagueSeasonFormat.hasExpectedDetailedPairings(teamIds, fixtures, plan.legs)) return null

        val adjacency = teamIds.associateWith { mutableSetOf<Long>() }.toMutableMap()
        fixtures.forEach { fixture ->
            adjacency.getValue(fixture.homeTeamId).add(fixture.awayTeamId)
            adjacency.getValue(fixture.awayTeamId).add(fixture.homeTeamId)
        }

        val remaining = teamIds.toMutableSet()
        val components = mutableListOf<Set<Long>>()
        while (remaining.isNotEmpty()) {
            val seed = remaining.minOrNull() ?: break
            val queue = ArrayDeque<Long>()
            val component = linkedSetOf<Long>()
            queue.add(seed)
            remaining.remove(seed)

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                component.add(current)
                adjacency.getValue(current).sorted().forEach { neighbor ->
                    if (remaining.remove(neighbor)) {
                        queue.add(neighbor)
                    }
                }
            }
            components.add(component)
        }

        if (components.size != plan.groupCount || components.any { it.size != plan.groupSize }) {
            return null
        }
        return components.sortedBy { it.minOrNull() ?: Long.MAX_VALUE }
    }

    /**
     * Produz uma ordem global justa para promoção/rebaixamento em ligas de grupos.
     *
     * Primeiro vêm todos os campeões de grupo, ordenados entre si pelo comparator esportivo;
     * depois todos os vice-campeões, depois terceiros, e assim por diante. Com isso, um vice de
     * um grupo nunca ultrapassa o campeão de outro apenas por ter feito mais pontos contra uma
     * chave diferente.
     */
    fun rankByGroupPosition(
        teamIds: Set<Long>,
        fixtures: List<Fixture>,
        sportingComparator: Comparator<Long>
    ): List<Long>? {
        val groups = components(teamIds, fixtures) ?: return null
        val rankedGroups = groups.map { group -> group.sortedWith(sportingComparator) }
        val maxGroupSize = rankedGroups.maxOfOrNull { it.size } ?: return emptyList()

        return buildList(teamIds.size) {
            for (positionInGroup in 0 until maxGroupSize) {
                val samePositionAcrossGroups = rankedGroups
                    .mapNotNull { group -> group.getOrNull(positionInGroup) }
                    .sortedWith(sportingComparator)
                addAll(samePositionAcrossGroups)
            }
        }
    }

    fun groupIndexByTeamId(
        teamIds: Set<Long>,
        fixtures: List<Fixture>
    ): Map<Long, Int> {
        val groups = components(teamIds, fixtures) ?: return emptyMap()
        return buildMap {
            groups.forEachIndexed { index, group ->
                group.forEach { teamId -> put(teamId, index) }
            }
        }
    }

    fun groupLabel(index: Int): String {
        var value = index
        val label = StringBuilder()
        do {
            val remainder = value % 26
            label.append(('A'.code + remainder).toChar())
            value = value / 26 - 1
        } while (value >= 0)
        return label.reverse().toString()
    }
}
