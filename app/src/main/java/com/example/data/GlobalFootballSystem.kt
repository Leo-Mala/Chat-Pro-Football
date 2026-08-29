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

    private const val TEAM_IDS_PER_COUNTRY = 200

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

    /**
     * Resolve o ID global de clube.
     *
     * O registry estável só é aplicado a templates factuais explicitamente cadastrados em
     * [DefaultData.originalMap], [EuropeanAdditionalClubTemplates2026_27] ou materializados pelo
     * baseline factual 2026/27. Um clube procedural que, por coincidência, receba um alias histórico
     * nunca herda a identidade do clube real.
     *
     * Para clubes não estáveis preservamos a ordem E as regras de elegibilidade do catálogo anterior
     * à materialização factual. Isso evita renumerar clubes das divisões inferiores apenas porque
     * outros clubes passaram a usar IDs estáveis reservados.
     */
    fun getGlobalId(country: String, teamName: String): Long {
        stableSeedIdFor(country, teamName)?.let { return it }

        val countryIndex = keys.indexOf(country)
        if (countryIndex != -1) {
            val teams = EuropeanFactualClubTargetMaterializer2026_27.legacyTeamsForIdAllocation(country)
                ?: DefaultData.countriesMap[country]?.teams
            if (teams != null) {
                val nonStableTeams = teams.filter { legacyExplicitStableSeedIdFor(country, it.name) == null }
                val allocationName = BrasfootRealClubIdentity.legacySlotNameFor(country, teamName) ?: teamName
                val teamIndex = nonStableTeams.indexOfFirst { it.name.equals(allocationName, ignoreCase = true) }
                if (teamIndex != -1) {
                    val blockStart = countryIndex * TEAM_IDS_PER_COUNTRY + 1L
                    val blockEndInclusive = blockStart + TEAM_IDS_PER_COUNTRY - 1L
                    val reservedIds = StableTeamIdentityRegistry.all
                        .asSequence()
                        .filter { it.country == country }
                        .map { it.id }
                        .filter { it in blockStart..blockEndInclusive }
                        .toSet()
                    val freeIds = (blockStart..blockEndInclusive).filterNot(reservedIds::contains)
                    if (teamIndex < freeIds.size) {
                        return freeIds[teamIndex]
                    }
                }
            }
        }
        return legacyVirtualTeamId(country, teamName)
    }

    /**
     * Materializa um clube a partir do ID global.
     *
     * A resolução reversa procura primeiro um template factual explícito e depois o alvo factual
     * materializado no catálogo público do DefaultData. Assim, identidade estável e Team persistido
     * continuam reversíveis mesmo quando cidade/estádio/rating ainda usam metadados internos.
     */
    fun getTeamByGlobalId(id: Long?): Team? {
        if (id == null) return null

        StableTeamIdentityRegistry.identityForId(id)?.let { identity ->
            val template = DefaultData.originalMap[identity.country]
                ?.teams
                ?.firstOrNull { stableSeedIdFor(identity.country, it.name) == id }
                ?: EuropeanAdditionalClubTemplates2026_27
                    .find(identity.country, identity.canonicalName)
                    ?.template
                ?: DefaultData.countriesMap[identity.country]
                    ?.teams
                    ?.firstOrNull { template ->
                        EuropeanFactualClubTargetMaterializer2026_27
                            .stableIdForMaterializedTarget(identity.country, template.name) == id
                    }
                ?: return@let
            return template.toPersistedTeam(id = id, country = identity.country)
        }

        if (!isGeneratedVirtualTeamId(id)) {
            val countryIndex = ((id - 1) / TEAM_IDS_PER_COUNTRY).toInt()
            if (countryIndex in keys.indices) {
                val country = keys[countryIndex]
                val template = DefaultData.countriesMap[country]
                    ?.teams
                    ?.firstOrNull { getGlobalId(country, it.name) == id }
                if (template != null) {
                    return template.toPersistedTeam(id = id, country = country)
                }
            }
            return null
        }

        for (country in keys) {
            val template = DefaultData.countriesMap[country]
                ?.teams
                ?.firstOrNull { getGlobalId(country, it.name) == id }
            if (template != null) {
                return template.toPersistedTeam(id = id, country = country)
            }
        }
        return null
    }

    private fun stableSeedIdFor(country: String, teamName: String): Long? {
        val isOriginalExplicitSeed = DefaultData.originalMap[country]
            ?.teams
            ?.any { it.name.equals(teamName, ignoreCase = true) }
            ?: false
        val isAdditionalExplicitSeed = EuropeanAdditionalClubTemplates2026_27.find(country, teamName) != null
        val isMaterializedFactualTarget = EuropeanFactualClubTargetMaterializer2026_27.contains(country, teamName)
        if (!isOriginalExplicitSeed && !isAdditionalExplicitSeed && !isMaterializedFactualTarget) return null
        return StableTeamIdentityRegistry.idFor(country, teamName)
    }

    /** Exatamente a regra de estabilidade que existia antes da fase 9.11A1. */
    private fun legacyExplicitStableSeedIdFor(country: String, teamName: String): Long? {
        val isOriginalExplicitSeed = DefaultData.originalMap[country]
            ?.teams
            ?.any { it.name.equals(teamName, ignoreCase = true) }
            ?: false
        val isAdditionalExplicitSeed = EuropeanAdditionalClubTemplates2026_27.find(country, teamName) != null
        if (!isOriginalExplicitSeed && !isAdditionalExplicitSeed) return null
        return StableTeamIdentityRegistry.idFor(country, teamName)
    }

    private fun DefaultData.TeamTemplate.toPersistedTeam(id: Long, country: String): Team = Team(
        id = id,
        name = name,
        city = city,
        state = state,
        country = country,
        division = division,
        rating = rating,
        stadiumName = stadium,
        logoUrl = DefaultData.getLogoForTeam(name, country),
        isPlayerControlled = false
    )

    /** Mantém o algoritmo virtual legado para entradas que nunca tiveram posição no catálogo. */
    private fun legacyVirtualTeamId(country: String, teamName: String): Long {
        val h1 = country.hashCode().toLong()
        val h2 = teamName.hashCode().toLong()
        val combined = (h1 shl 16) xor h2
        val positiveHash = (combined and 0x7FFFFFFF_FFFFFFFFL) % 800_000L
        return VIRTUAL_TEAM_ID_FLOOR + positiveHash
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
