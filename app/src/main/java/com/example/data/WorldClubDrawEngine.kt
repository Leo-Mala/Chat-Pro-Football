package com.example.data

import kotlin.random.Random

/** Sorteio determinístico dos oito grupos do Mundial de Clubes. */
object WorldClubDrawEngine {
    private const val GROUP_COUNT = 8
    private const val GROUP_SIZE = 4

    fun drawGroups(season: Int, teams: List<Team>): List<List<Team>> {
        if (teams.size != SuperMundialQualificationRules.FIELD_SIZE) return emptyList()
        if (teams.map { it.id }.toSet().size != teams.size) return emptyList()
        if (teams.any { !CountryFootballRulesRegistry.isContinentalCompetitionEligible(it.country) }) {
            return emptyList()
        }
        if (teams.any { CountryFootballRulesRegistry.confederationFor(it.country) == null }) {
            return emptyList()
        }

        val associationCounts = teams.groupingBy(::canonicalAssociation).eachCount()
        if (associationCounts.values.any { it > GROUP_COUNT }) return emptyList()

        val byConfederation = teams.groupBy { team ->
            requireNotNull(CountryFootballRulesRegistry.confederationFor(team.country))
        }
        if (byConfederation.any { (confederation, clubs) ->
                val capacity = if (confederation == FootballConfederation.UEFA) {
                    GROUP_COUNT * 2
                } else {
                    GROUP_COUNT
                }
                clubs.size > capacity
            }
        ) {
            return emptyList()
        }

        // Confederações mais numerosas são alocadas primeiro. Dentro de cada conjunto a seed
        // season+confederação produz o mesmo draw para o mesmo save/input, sem depender do relógio.
        val ordered = byConfederation.entries
            .sortedWith(
                compareByDescending<Map.Entry<FootballConfederation, List<Team>>> { it.value.size }
                    .thenBy { it.key.code }
            )
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
        if (!assigned || groups.any { it.size != GROUP_SIZE }) return emptyList()
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
        val association = canonicalAssociation(team)
        if (group.any { canonicalAssociation(it) == association }) return false

        val sameConfederation = group.count {
            CountryFootballRulesRegistry.confederationFor(it.country) == confederation
        }
        val confederationLimit = if (confederation == FootballConfederation.UEFA) 2 else 1
        return sameConfederation < confederationLimit
    }

    private fun canonicalAssociation(team: Team): String =
        requireNotNull(CountryFootballRulesRegistry.resolve(team.country)).canonicalCountry

    private fun stableSeed(season: Int, key: String): Long =
        season.toLong() * 1_000_003L xor key.hashCode().toLong()
}
