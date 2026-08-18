package com.example.data

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
    val type: String,
    val level: Int,
    val confederation: String?,
    val startWeek: Int,
    val endWeek: Int
)

object GlobalFootballSystem {

    /**
     * IDs >= 200_000 pertencem ao namespace determinístico de clubes gerados/virtuais. IDs
     * persistidos faltantes abaixo deste limite são considerados corrupção e não são inventados em
     * runtime. A migration V20->V21 é a única exceção, pois materializa referências legadas para
     * preservar fixtures já existentes.
     */
    const val VIRTUAL_TEAM_ID_FLOOR = 200_000L

    fun isGeneratedVirtualTeamId(id: Long): Boolean = id >= VIRTUAL_TEAM_ID_FLOOR

    /** Metadados visuais legados. Regras esportivas usam [FootballConfederation]. */
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
        return VIRTUAL_TEAM_ID_FLOOR + positiveHash
    }

    fun getTeamByGlobalId(id: Long?): Team? {
        if (id == null) return null
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
            country = "Mundial",
            division = 1,
            rating = 50,
            stadiumName = "Arena Global",
            logoUrl = null
        )
    }

    /** API nova, tipada e fail-safe. País desconhecido e "Mundial" retornam null. */
    fun getTypedConfederationForCountry(country: String): FootballConfederation? =
        CountryFootballRulesRegistry.confederationFor(country)

    /**
     * Adaptador legado para consumidores que ainda esperam String.
     *
     * O fallback histórico para CONMEBOL foi removido. Entradas sem confederação retornam UNKNOWN;
     * o alias agregado MIXED continua explicitamente reconhecido para leitura de dados antigos.
     */
    fun getConfederationForCountry(country: String): String =
        CountryFootballRulesRegistry.legacyConfederationCodeFor(country) ?: "UNKNOWN"

    /** Catálogo legado agora derivado da source of truth de competições. */
    val competitions: List<Competition> = CompetitionRulesRegistry.catalogDefinitions.map { definition ->
        Competition(
            code = definition.code,
            name = definition.displayName,
            type = definition.category,
            level = definition.level,
            confederation = definition.confederation?.code,
            startWeek = requireNotNull(definition.startWeek),
            endWeek = requireNotNull(definition.endWeek)
        )
    }

    fun getCompetitionByCode(code: String): Competition? =
        competitions.find { it.code.equals(code, ignoreCase = true) }

    /**
     * Resolução tipada para código novo. Apenas associações nacionais elegíveis recebem conjunto
     * continental; agregados legados, Mundial e entradas desconhecidas retornam null.
     */
    fun getContinentalCompetitionSetForCountryOrNull(country: String): ContinentalCompetitionSet? {
        val rules = CountryFootballRulesRegistry.resolve(country) ?: return null
        if (!rules.continentalCompetitionsAllowed) return null
        val confederation = rules.confederation ?: return null
        return CompetitionRulesRegistry.continentalCompetitionSet(confederation)
    }

    /**
     * Adaptador legado de três strings. Como Triple não representa ausência de T2/T3, retorna null
     * quando a confederação não possui os três tiers. Novos consumidores devem usar
     * [getContinentalCompetitionSetForCountryOrNull].
     */
    @Deprecated("Use getContinentalCompetitionSetForCountryOrNull para preservar tiers ausentes.")
    fun getContinentalTournamentsForCountryOrNull(country: String): Triple<String, String, String>? {
        val rules = CountryFootballRulesRegistry.resolve(country) ?: return null
        if (!rules.continentalCompetitionsAllowed) return null
        val confederation = rules.confederation ?: return null
        return CompetitionRulesRegistry.continentalCatalogCodesOrNull(confederation)
    }

    /**
     * Adaptador legado estrito: nunca substitui uma associação ou tier ausente por outro torneio.
     */
    @Deprecated("Use getContinentalCompetitionSetForCountryOrNull para preservar tiers ausentes.")
    fun getContinentalTournamentsForCountry(country: String): Triple<String, String, String> =
        requireNotNull(getContinentalTournamentsForCountryOrNull(country)) {
            "País/região sem três tiers continentais representáveis no adaptador legado: '$country'."
        }
}
