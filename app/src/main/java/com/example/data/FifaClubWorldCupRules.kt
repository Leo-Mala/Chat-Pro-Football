package com.example.data

/**
 * Critérios esportivos da fase de grupos do Mundial de Clubes suportados pelo domínio atual.
 *
 * Para clubes empatados em pontos, aplica a mini-tabela entre os envolvidos (pontos, saldo e gols),
 * reaplicando esses critérios ao subconjunto que permanecer empatado. Persistindo o empate, usa
 * saldo e gols da tabela geral. O modelo atual não persiste pontuação disciplinar nem sorteio FIFA;
 * por isso [teamId] é somente o último fallback determinístico e explicitamente não é tratado como
 * regra factual da competição.
 */
object FifaClubWorldCupRules {
    private data class Row(
        val teamId: Long,
        var points: Int = 0,
        var goalDifference: Int = 0,
        var goalsFor: Int = 0
    )

    fun groupRanking(fixtures: List<Fixture>): List<Long> {
        if (fixtures.isEmpty()) return emptyList()
        if (fixtures.any { !it.isPlayed || it.homeScore == null || it.awayScore == null }) {
            return emptyList()
        }

        val teamIds = fixtures
            .flatMap { listOf(it.homeTeamId, it.awayTeamId) }
            .distinct()
        if (teamIds.isEmpty()) return emptyList()

        val overall = buildTable(teamIds, fixtures)
        return teamIds
            .groupBy { overall.getValue(it).points }
            .toSortedMap(compareByDescending { it })
            .values
            .flatMap { tied -> rankPointsTiedTeams(tied, fixtures, overall) }
    }

    private fun rankPointsTiedTeams(
        teamIds: List<Long>,
        allFixtures: List<Fixture>,
        overall: Map<Long, Row>
    ): List<Long> {
        if (teamIds.size <= 1) return teamIds
        val tiedSet = teamIds.toSet()
        val headToHeadFixtures = allFixtures.filter {
            it.homeTeamId in tiedSet && it.awayTeamId in tiedSet
        }
        val miniTable = buildTable(teamIds, headToHeadFixtures)

        data class MiniKey(val points: Int, val goalDifference: Int, val goalsFor: Int)
        val partitions = teamIds
            .groupBy { id ->
                val row = miniTable.getValue(id)
                MiniKey(row.points, row.goalDifference, row.goalsFor)
            }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<MiniKey, List<Long>>> { it.key.points }
                    .thenByDescending { it.key.goalDifference }
                    .thenByDescending { it.key.goalsFor }
            )

        // Nenhum critério da mini-tabela separou o conjunto: a regra oficial passa aos critérios
        // gerais disponíveis. Disciplinar/sorteio não existem no schema atual, então teamId fecha
        // a ordenação apenas para manter o save reproduzível.
        if (partitions.size == 1) {
            return teamIds.sortedWith(
                compareByDescending<Long> { overall.getValue(it).goalDifference }
                    .thenByDescending { overall.getValue(it).goalsFor }
                    .thenBy { it }
            )
        }

        return partitions.flatMap { (_, partition) ->
            if (partition.size <= 1) partition
            else rankPointsTiedTeams(partition, allFixtures, overall)
        }
    }

    private fun buildTable(teamIds: List<Long>, fixtures: List<Fixture>): Map<Long, Row> {
        val table = teamIds.associateWith(::Row).toMutableMap()
        fixtures.forEach { fixture ->
            val home = table[fixture.homeTeamId] ?: return@forEach
            val away = table[fixture.awayTeamId] ?: return@forEach
            val homeGoals = fixture.homeScore ?: return@forEach
            val awayGoals = fixture.awayScore ?: return@forEach

            home.goalsFor += homeGoals
            away.goalsFor += awayGoals
            home.goalDifference += homeGoals - awayGoals
            away.goalDifference += awayGoals - homeGoals
            when {
                homeGoals > awayGoals -> home.points += 3
                awayGoals > homeGoals -> away.points += 3
                else -> {
                    home.points += 1
                    away.points += 1
                }
            }
        }
        return table
    }
}
