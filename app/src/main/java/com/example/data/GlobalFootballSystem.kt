package com.example.data

import kotlin.random.Random

data class Confederation(
    val acronym: String,
    val name: String,
    val flag: String
)

data class Country(
    val name: String,
    val confederation: String,
    val flag: String,
    val federation: String
)

data class Competition(
    val code: String,
    val name: String,
    val type: String, // "LEAGUE", "CUP", "CONTINENTAL", "WORLD"
    val level: Int,   // 1 = Tier 1, 2 = Tier 2, 3 = Tier 3
    val confederation: String?,
    val startWeek: Int,
    val endWeek: Int
)

object GlobalFootballSystem {

    val confederations = listOf(
        Confederation("UEFA", "Union of European Football Associations", "🇪🇺"),
        Confederation("CONMEBOL", "Confederación Sudamericana de Fútbol", "🌎"),
        Confederation("CONCACAF", "Confederation of North, Central American and Caribbean Association Football", "🌎"),
        Confederation("CAF", "Confédération Africaine de Football", "🌍"),
        Confederation("AFC", "Asian Football Confederation", "🌏"),
        Confederation("OFC", "Oceania Football Confederation", "🏝️")
    )

    val countries = listOf(
        Country("Inglaterra", "UEFA", "🏴󠁧󠁢󠁥󠁮󠁧󠁿", "FA"),
        Country("Espanha", "UEFA", "🇪🇸", "RFEF"),
        Country("Itália", "UEFA", "🇮🇹", "FIGC"),
        Country("Alemanha", "UEFA", "🇩🇪", "DFB"),
        Country("França", "UEFA", "🇫🇷", "FFF"),
        Country("Portugal", "UEFA", "🇵🇹", "FPF"),
        Country("Países Baixos", "UEFA", "🇳🇱", "KNVB"),
        Country("Bélgica", "UEFA", "🇧🇪", "KBVB"),
        Country("Turquia", "UEFA", "🇹🇷", "TFF"),
        Country("Escócia", "UEFA", "🏴󠁧󠁢󠁳󠁣󠁴󠁿", "SFA"),
        Country("Áustria", "UEFA", "🇦🇹", "ÖFB"),
        Country("Suíça", "UEFA", "🇨🇭", "SFV"),
        Country("Dinamarca", "UEFA", "🇩🇰", "DBU"),
        Country("Noruega", "UEFA", "🇳🇴", "NFF"),
        Country("Suécia", "UEFA", "🇸🇪", "SvFF"),
        Country("Polônia", "UEFA", "🇵🇱", "PZPN"),
        Country("Tchéquia", "UEFA", "🇨🇿", "FAČR"),
        Country("Croácia", "UEFA", "🇭🇷", "HNS"),
        Country("Sérvia", "UEFA", "🇷🇸", "FSS"),
        Country("Grécia", "UEFA", "🇬🇷", "HFF"),
        Country("Brasil", "CONMEBOL", "🇧🇷", "CBF"),
        Country("Argentina", "CONMEBOL", "🇦🇷", "AFA"),
        Country("Colômbia", "CONMEBOL", "🇨🇴", "FCF"),
        Country("Chile", "CONMEBOL", "🇨🇱", "FFCh"),
        Country("Uruguai", "CONMEBOL", "🇺🇾", "AUF"),
        Country("Paraguai", "CONMEBOL", "🇵🇾", "APF"),
        Country("Equador", "CONMEBOL", "🇪🇨", "FEF"),
        Country("Peru", "CONMEBOL", "🇵🇪", "FPF"),
        Country("Bolívia", "CONMEBOL", "🇧🇴", "FBF"),
        Country("Venezuela", "CONMEBOL", "🇻🇪", "FVF"),
        Country("México", "CONCACAF", "🇲🇽", "FMF"),
        Country("Estados Unidos / Canadá", "CONCACAF", "🇺🇸🇨🇦", "USSF/CSA"),
        Country("Costa Rica", "CONCACAF", "🇨🇷", "FCRF"),
        Country("Guatemala", "CONCACAF", "🇬🇹", "FEDEFUT"),
        Country("Honduras", "CONCACAF", "🇭🇳", "FENAFUTH"),
        Country("Panamá", "CONCACAF", "🇵🇦", "FEPAFUT"),
        Country("El Salvador", "CONCACAF", "🇸🇻", "FESFUT"),
        Country("Jamaica", "CONCACAF", "🇯🇲", "JFF"),
        Country("República Dominicana", "CONCACAF", "🇩🇴", "FEDOFUTBOL"),
        Country("Trinidad e Tobago", "CONCACAF", "🇹🇹", "TTFA"),
        Country("Japão", "AFC", "🇯🇵", "JFA"),
        Country("Coreia do Sul", "AFC", "🇰🇷", "KFA"),
        Country("Arábia Saudita", "AFC", "🇸🇦", "SAFF"),
        Country("Emirados Árabes Unidos", "AFC", "🇦🇪", "UAEFA"),
        Country("Catar", "AFC", "🇶🇦", "QFA"),
        Country("Irã", "AFC", "🇮🇷", "FFIRI"),
        Country("China", "AFC", "🇨🇳", "CFA"),
        Country("Austrália", "AFC", "🇦🇺", "FA"),
        Country("Egito", "CAF", "🇪🇬", "EFA"),
        Country("Marrocos", "CAF", "🇲🇦", "FRMF"),
        Country("Tunísia", "CAF", "🇹🇳", "FTF"),
        Country("África do Sul", "CAF", "🇿🇦", "SAFA")
    )

    val keys = countries.map { it.name }

    fun getGlobalId(country: String, teamName: String): Long {
        val countryIndex = keys.indexOf(country)
        if (countryIndex != -1) {
            val teams = DefaultData.countriesMap[country]?.teams
            if (teams != null) {
                val teamIndex = teams.indexOfFirst { it.name.equals(teamName, ignoreCase = true) }
                if (teamIndex != -1) {
                    return (countryIndex * 200 + teamIndex + 1).toLong()
                }
            }
        }
        val h1 = country.hashCode().toLong()
        val h2 = teamName.hashCode().toLong()
        val combined = (h1 shl 16) xor h2
        val positiveHash = (combined and 0x7FFFFFFF_FFFFFFFFL) % 800_000L
        return 200_000L + positiveHash
    }

    fun getTeamByGlobalId(id: Long): Team? {
        val countryIndex = ((id - 1) / 200).toInt()
        val teamIndex = ((id - 1) % 200).toInt()
        if (countryIndex < 0 || countryIndex >= keys.size) return null
        val country = keys[countryIndex]
        val teams = DefaultData.countriesMap[country]?.teams ?: return null
        if (teamIndex < 0 || teamIndex >= teams.size) return null
        val t = teams[teamIndex]
        return Team(
            id = id,
            name = t.name,
            city = t.city,
            state = t.state,
            country = country,
            division = t.division,
            rating = t.rating,
            stadiumName = t.stadium,
            logoUrl = DefaultData.getLogoForTeam(t.name, country),
            isPlayerControlled = false
        )
    }

    fun getVirtualTeam(id: Long): Team {
        return getTeamByGlobalId(id) ?: Team(
            id = id,
            name = "Clube Virtual $id",
            city = "Global",
            state = "GL",
            country = "Brasil",
            division = 1,
            rating = 50,
            stadiumName = "Arena Global",
            logoUrl = null
        )
    }

    // Comprehensive real-world competition definitions
    val competitions = listOf(
        // World
        Competition("WORLD_CUP", "FIFA Club World Cup 🌍", "WORLD", 1, null, 34, GameCalendar.WEEKS_PER_SEASON),
        Competition("WORLD_INTERCONTINENTAL", "FIFA Intercontinental Cup 🏆", "WORLD", 2, null, 37, 38),

        // UEFA (Europe)
        Competition("UEFA_CL", "UEFA Champions League 🇪🇺", "CONTINENTAL", 1, "UEFA", 33, 36),
        Competition("UEFA_EL", "UEFA Europa League 🇪🇺", "CONTINENTAL", 2, "UEFA", 33, 36),
        Competition("UEFA_ECL", "UEFA Conference League 🇪🇺", "CONTINENTAL", 3, "UEFA", 33, 36),

        // CONMEBOL (South America)
        Competition("CONMEBOL_CL", "Copa Libertadores 🏆", "CONTINENTAL", 1, "CONMEBOL", 33, 36),
        Competition("CONMEBOL_CS", "Copa Sudamericana 🥈", "CONTINENTAL", 2, "CONMEBOL", 33, 36),

        // CONCACAF (North/Central America)
        Competition("CONCACAF_CL", "CONCACAF Champions Cup 🏆", "CONTINENTAL", 1, "CONCACAF", 33, 36),
        Competition("CONCACAF_CAC", "CONCACAF Central American Cup 🌎", "CONTINENTAL", 2, "CONCACAF", 33, 36),
        Competition("CONCACAF_CC", "CONCACAF Caribbean Cup 🏝️", "CONTINENTAL", 3, "CONCACAF", 33, 36),

        // CAF (Africa)
        Competition("CAF_CL", "CAF Champions League 🏆", "CONTINENTAL", 1, "CAF", 33, 36),
        Competition("CAF_CC", "CAF Confederation Cup 🥈", "CONTINENTAL", 2, "CAF", 33, 36),

        // AFC (Asia)
        Competition("AFC_CLE", "AFC Champions League Elite 🏆", "CONTINENTAL", 1, "AFC", 33, 36),
        Competition("AFC_CL2", "AFC Champions League Two 🥈", "CONTINENTAL", 2, "AFC", 33, 36),
        Competition("AFC_CHL", "AFC Challenge League 🥉", "CONTINENTAL", 3, "AFC", 33, 36),

        // OFC (Oceania)
        Competition("OFC_CL", "OFC Champions League 🏆", "CONTINENTAL", 1, "OFC", 33, 36)
    )

    fun getCompetitionByCode(code: String): Competition? {
        return competitions.find { it.code == code }
    }

    /**
     * Resolves the continental tournament for a country's top division based on confederation.
     * Returns Triple(Tier 1 Code, Tier 2 Code, Tier 3 Code)
     */
    fun getContinentalTournamentsForCountry(country: String): Triple<String, String, String> {
        val conf = countries.find { it.name == country }?.confederation ?: "CONMEBOL"
        return when (conf) {
            "UEFA" -> Triple("UEFA_CL", "UEFA_EL", "UEFA_ECL")
            "CONMEBOL" -> Triple("CONMEBOL_CL", "CONMEBOL_CS", "CONMEBOL_CL") // Sudamerica uses T1 / T2
            "CONCACAF" -> Triple("CONCACAF_CL", "CONCACAF_CAC", "CONCACAF_CC")
            "CAF" -> Triple("CAF_CL", "CAF_CC", "CAF_CL")
            "AFC" -> Triple("AFC_CLE", "AFC_CL2", "AFC_CHL")
            "OFC" -> Triple("OFC_CL", "OFC_CL", "OFC_CL")
            else -> Triple("CONMEBOL_CL", "CONMEBOL_CS", "CONMEBOL_CL")
        }
    }
}
