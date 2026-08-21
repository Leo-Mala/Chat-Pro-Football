package com.example.data

/**
 * Deterministic association-separation pass for UEFA league-phase fields.
 *
 * Qualification order determines pot membership. This helper only permutes clubs inside the same
 * pot so the fixed league-phase graph never pairs two clubs from the same canonical association.
 * If the constraints cannot be satisfied with the supplied field, it fails closed with an empty
 * result instead of silently creating an invalid draw.
 */
object UefaLeaguePhaseAssociationDraw {
    private data class Edge(val low: Int, val high: Int) {
        companion object {
            fun of(a: Int, b: Int): Edge = if (a < b) Edge(a, b) else Edge(b, a)
        }
    }

    fun arrange(
        qualified: List<UefaQualificationRules.QualifiedTeam>,
        destinationCompetition: CompetitionIdentity
    ): List<UefaQualificationRules.QualifiedTeam> {
        if (qualified.size != UefaQualificationRules.FIELD_SIZE) return emptyList()
        if (qualified.map { it.team.id }.toSet().size != qualified.size) return emptyList()

        val potCount = when (destinationCompetition) {
            CompetitionIdentity.UEFA_CONFERENCE_LEAGUE -> 6
            CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE,
            CompetitionIdentity.UEFA_EUROPA_LEAGUE -> 4
            else -> return emptyList()
        }
        val potSize = UefaQualificationRules.FIELD_SIZE / potCount
        val edges = when (destinationCompetition) {
            CompetitionIdentity.UEFA_CONFERENCE_LEAGUE -> conferenceEdges()
            else -> championsEuropaEdges()
        }
        val adjacency = Array(UefaQualificationRules.FIELD_SIZE) { mutableSetOf<Int>() }
        edges.forEach { edge ->
            adjacency[edge.low] += edge.high
            adjacency[edge.high] += edge.low
        }

        val originalIndex = qualified.mapIndexed { index, team -> team.team.id to index }.toMap()
        val remainingByPot = Array(potCount) { pot ->
            qualified.subList(pot * potSize, (pot + 1) * potSize).toMutableList()
        }
        val assigned = arrayOfNulls<UefaQualificationRules.QualifiedTeam>(qualified.size)

        fun canonicalAssociation(item: UefaQualificationRules.QualifiedTeam): String =
            CountryFootballRulesRegistry.resolve(item.team.country)?.canonicalCountry
                ?: item.team.country.trim()

        fun validCandidates(position: Int): List<UefaQualificationRules.QualifiedTeam> {
            val pot = position / potSize
            return remainingByPot[pot]
                .asSequence()
                .filter { candidate ->
                    val association = canonicalAssociation(candidate)
                    adjacency[position].all { neighbour ->
                        val other = assigned[neighbour]
                        other == null || canonicalAssociation(other) != association
                    }
                }
                .sortedBy { originalIndex.getValue(it.team.id) }
                .toList()
        }

        fun chooseNextPosition(): Pair<Int, List<UefaQualificationRules.QualifiedTeam>>? {
            var bestPosition = -1
            var bestCandidates: List<UefaQualificationRules.QualifiedTeam>? = null
            var bestAssignedNeighbours = -1

            assigned.indices.forEach { position ->
                if (assigned[position] != null) return@forEach
                val candidates = validCandidates(position)
                if (candidates.isEmpty()) return position to emptyList()
                val assignedNeighbours = adjacency[position].count { assigned[it] != null }
                if (
                    bestCandidates == null ||
                    candidates.size < bestCandidates!!.size ||
                    (candidates.size == bestCandidates!!.size && assignedNeighbours > bestAssignedNeighbours) ||
                    (candidates.size == bestCandidates!!.size && assignedNeighbours == bestAssignedNeighbours && position < bestPosition)
                ) {
                    bestPosition = position
                    bestCandidates = candidates
                    bestAssignedNeighbours = assignedNeighbours
                }
            }

            return if (bestPosition < 0) null else bestPosition to requireNotNull(bestCandidates)
        }

        fun solve(): Boolean {
            val choice = chooseNextPosition() ?: return true
            val (position, candidates) = choice
            if (candidates.isEmpty()) return false
            val pot = position / potSize

            candidates.forEach { candidate ->
                assigned[position] = candidate
                remainingByPot[pot].remove(candidate)
                if (solve()) return true
                remainingByPot[pot].add(candidate)
                remainingByPot[pot].sortBy { originalIndex.getValue(it.team.id) }
                assigned[position] = null
            }
            return false
        }

        if (!solve()) return emptyList()
        val result = assigned.map { requireNotNull(it) }
        val associations = result.map(::canonicalAssociation)
        if (edges.any { associations[it.low] == associations[it.high] }) return emptyList()
        return result
    }

    private fun championsEuropaEdges(): List<Edge> {
        val potSize = 9
        val edges = linkedSetOf<Edge>()
        fun add(a: Int, b: Int) { edges += Edge.of(a, b) }

        val ownSteps = listOf(2, 2, 2, 4)
        ownSteps.forEachIndexed { pot, step ->
            repeat(potSize) { index ->
                add(pot * potSize + index, pot * potSize + ((index + step) % potSize))
            }
        }

        val offsets = listOf(
            Triple(0, 1, listOf(0, 5)),
            Triple(0, 2, listOf(5, 6)),
            Triple(0, 3, listOf(4, 3)),
            Triple(1, 2, listOf(8, 0)),
            Triple(1, 3, listOf(8, 7)),
            Triple(2, 3, listOf(7, 4))
        )
        offsets.forEach { (firstPot, secondPot, pairOffsets) ->
            pairOffsets.forEach { offset ->
                repeat(potSize) { index ->
                    add(
                        firstPot * potSize + index,
                        secondPot * potSize + ((index + offset) % potSize)
                    )
                }
            }
        }
        return edges.toList()
    }

    private fun conferenceEdges(): List<Edge> {
        val potCount = 6
        val potSize = 6
        val edges = linkedSetOf<Edge>()
        fun add(a: Int, b: Int) { edges += Edge.of(a, b) }

        repeat(potCount) { pot ->
            repeat(potSize / 2) { index ->
                add(pot * potSize + index, pot * potSize + index + potSize / 2)
            }
        }
        for (firstPot in 0 until potCount) {
            for (secondPot in firstPot + 1 until potCount) {
                repeat(potSize) { index ->
                    add(firstPot * potSize + index, secondPot * potSize + index)
                }
            }
        }
        return edges.toList()
    }
}
