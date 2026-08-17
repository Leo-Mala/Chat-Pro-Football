package com.example.data

/**
 * Política canônica das edições do Super Mundial.
 *
 * A primeira edição é 2025 e o torneio volta a cada quatro temporadas. A sede é derivada
 * deterministicamente do universo persistido de clubes, portanto o mesmo save/temporada produz
 * sempre a mesma sede sem exigir uma nova tabela Room nesta fase.
 */
object SuperMundialEditionPolicy {
    const val FIRST_EDITION_SEASON = 2025
    const val EDITION_INTERVAL_YEARS = 4

    data class Edition(
        val season: Int,
        val hostCountry: String,
        val hostTeamId: Long,
        val hostTeamName: String
    )

    fun isEditionSeason(season: Int): Boolean =
        season >= FIRST_EDITION_SEASON &&
            (season - FIRST_EDITION_SEASON) % EDITION_INTERVAL_YEARS == 0

    /**
     * Países elegíveis são os países reais presentes no save. Entradas virtuais do próprio
     * Mundial não participam do rodízio de sedes.
     */
    fun eligibleHostCountries(allTeams: List<Team>): List<String> =
        allTeams.asSequence()
            .map { it.country.trim() }
            .filter { it.isNotBlank() && !it.equals("Mundial", ignoreCase = true) }
            .distinct()
            .sorted()
            .toList()

    fun hostCountryForSeason(season: Int, allTeams: List<Team>): String? {
        if (!isEditionSeason(season)) return null
        val countries = eligibleHostCountries(allTeams)
        if (countries.isEmpty()) return null

        val editionIndex = (season - FIRST_EDITION_SEASON) / EDITION_INTERVAL_YEARS
        return countries[editionIndex % countries.size]
    }

    fun hostTeamForSeason(season: Int, allTeams: List<Team>): Team? {
        val hostCountry = hostCountryForSeason(season, allTeams) ?: return null
        return allTeams.asSequence()
            .filter { it.country.trim() == hostCountry }
            .sortedWith(compareByDescending<Team> { it.rating }.thenBy { it.id })
            .firstOrNull()
    }

    fun editionForSeason(season: Int, allTeams: List<Team>): Edition? {
        val hostCountry = hostCountryForSeason(season, allTeams) ?: return null
        val hostTeam = hostTeamForSeason(season, allTeams) ?: return null
        return Edition(
            season = season,
            hostCountry = hostCountry,
            hostTeamId = hostTeam.id,
            hostTeamName = hostTeam.name
        )
    }

    /**
     * Histórico derivável e estável. Enquanto houver países ainda não usados, o rodízio não
     * repete sede. Quando o conjunto se esgota, a sequência reinicia; com dois ou mais países,
     * duas edições consecutivas nunca compartilham a mesma sede.
     */
    fun editionsThrough(upToSeason: Int, allTeams: List<Team>): List<Edition> {
        if (upToSeason < FIRST_EDITION_SEASON) return emptyList()
        return generateSequence(FIRST_EDITION_SEASON) { it + EDITION_INTERVAL_YEARS }
            .takeWhile { it <= upToSeason }
            .mapNotNull { editionForSeason(it, allTeams) }
            .toList()
    }
}
