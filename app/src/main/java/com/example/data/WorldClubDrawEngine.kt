package com.example.data

import kotlin.random.Random

/** Sorteio determinístico dos oito grupos do Mundial de Clubes. */
object WorldClubDrawEngine {
    private const val GROUP_COUNT = 8
    private const val GROUP_SIZE = 4

    fun drawGroups(season: Int, teams: List<Team>): List<List<Team>> {
        require(teams.size == SuperMundialQualificationRules.FIELD_SIZE) {
            "Sorteio mundial exige 32 clubes qualificados."
        }
        require(teams.map { it.id }.toSet().size == teams.size) {
            "Sorteio mundial não aceita clubes duplicados."
        }

        val byConfederation = teams.groupBy { team ->
            CountryFootballRulesRegistry.confederationFor(team.country)
                ?: error("Clube ${team.id} não possui confederação válida para o Mundial.")
        }

        // Confederações mais numerosas são alocadas primeiro. Dentro de cada conjunto a seed
        // season+confederação produz o mesmo draw para o mesmo save/input, sem depender do relógio.
        val ordered = byConfederation.entries
            .sortedWith(compareByDescending<Map.Entry<FootballConfederation, List<Team>>> { it.value.size }
                .thenBy { it.key.code })
            .flatMap { (confederation, confederationTeams) ->
                confederationTeams
                    .sortedBy { it.id }
                    .shuffled(Random(stableSeed(season, confederation.code)))
            }

        val groups = MutableList(GROUP_COUNT) { mutableListOf<Team>() }
        val assigned = backtrackAssign(
            index = 0,
            ordered = ordered,
            groups = groups,
            season = season
        )
        require(assigned) { "Não foi possível produzir grupos mundiais válidos para o field informado." }
        require(groups.all { it.size == GROUP_SIZE }) { "Sorteio mundial terminou com grupo incompleto." }
        return groups.map { it.toList() }
    }

    private fun backtrackAssign(
        index: Int,
        ordered: List<Team>,
        groups: MutableList<MutableList<Team>>,
        season: Int
    ): Boolean {
        if (index == ordered.size) return groups.all { it.size == GROUP_SIZE }
        val team = ordered[index]
        val confederation = CountryFootballRulesRegistry.confederationFor(team.country) ?: return false

        val groupOrder = (0 until GROUP_COUNT)
            .shuffled(Random(stableSeed(season, "${team.id}:$index")))
            .sortedBy { groups[it].size }

        for (groupIndex in groupOrder) {
            val group = groups[groupIndex]
            if (!canJoin(group, team, confederation)) continue
            group += team
            if (backtrackAssign(index + 1, ordered, groups, season)) return true
            group.removeAt(group.lastIndex)
        }
        return false
    }

    private fun canJoin(
        group: List<Team>,
        team: Team,
        confederation: FootballConfederation
    ): Boolean {
        if (group.size >= GROUP_SIZE) return false
        if (group.any { it.country.equals(team.country, ignoreCase = true) }) return false

        val sameConfederation = group.count {
            CountryFootballRulesRegistry.confederationFor(it.country) == confederation
        }
        val confederationLimit = if (confederation == FootballConfederation.UEFA) 2 else 1
        return sameConfederation < confederationLimit
    }

    private fun stableSeed(season: Int, key: String): Long =
        season.toLong() * 1_000_003L xor key.hashCode().toLong()
}
