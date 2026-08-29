package com.example.data

/**
 * Congela IDs de clubes reais cuja posição de divisão/lista pode mudar entre temporadas-base.
 *
 * O sistema legado calculava o ID por `countryIndex * 200 + teamIndex + 1`. Isso torna promoção,
 * rebaixamento e simples reordenação de `DefaultData` capazes de trocar a identidade de um clube.
 *
 * Inglaterra/Espanha preservam os IDs históricos já existentes. Para as outras associações UEFA
 * do baseline factual, os clubes recebem IDs em janelas determinísticas reservadas abaixo do piso
 * de clubes virtuais. O ID depende de país + nome canônico, nunca da posição na tabela/lista.
 *
 * Reservar o ID aqui NÃO significa que o clube já está materializado no seed. A integração ao
 * `DefaultData` acontece separadamente e pode avançar país a país sem trocar a identidade factual.
 */
data class StableTeamIdentity(
    val country: String,
    val canonicalName: String,
    val id: Long,
    val aliases: Set<String> = emptySet()
)

object StableTeamIdentityRegistry {
    const val BASELINE_REAL_TEAM_ID_FLOOR = 100_000L
    const val BASELINE_REAL_TEAM_ID_CEILING_EXCLUSIVE = 190_000L
    private const val COUNTRY_WINDOW_SIZE = 5_000L

    /**
     * Janelas fixas por associação. Nunca reorganizar esta tabela: o início de cada janela é parte
     * do contrato de identidade para novos saves. Se uma associação futura entrar no catálogo,
     * atribua uma nova janela explícita sem mover as existentes.
     */
    private val baselineCountryWindows = linkedMapOf(
        "Itália" to 100_000L,
        "Alemanha" to 105_000L,
        "França" to 110_000L,
        "Portugal" to 115_000L,
        "Países Baixos" to 120_000L,
        "Bélgica" to 125_000L,
        "Turquia" to 130_000L,
        "Escócia" to 135_000L,
        "Áustria" to 140_000L,
        "Suíça" to 145_000L,
        "Dinamarca" to 150_000L,
        "Noruega" to 155_000L,
        "Suécia" to 160_000L,
        "Polônia" to 165_000L,
        "Tchéquia" to 170_000L,
        "Croácia" to 175_000L,
        "Sérvia" to 180_000L,
        "Grécia" to 185_000L
    )

    /**
     * Overrides são raros e deliberadamente explícitos. O hash-base continua sendo a regra; quando
     * dois clubes caem no mesmo slot, um deles recebe um slot congelado documentado aqui.
     *
     * 2026-08-18: Ajax e Go Ahead Eagles produzem o mesmo hash-slot 122151 dentro da janela
     * neerlandesa. Ajax mantém o snapshot 122151 e Go Ahead Eagles fica congelado em 122152.
     *
     * 2026-08-19 / Phase 9.11A2: MSV Duisburg colide com Borussia Dortmund no slot alemão 105143;
     * Dortmund mantém o snapshot e MSV Duisburg fica em 105144. TSV Havelse e SSV Jahn Regensburg
     * colidem em 108961; Havelse mantém o hash e Jahn Regensburg fica em 108962.
     */
    private val baselineIdOverrides: Map<Pair<String, String>, Long> = mapOf(
        ("Países Baixos" to "Go Ahead Eagles") to 122_152L,
        ("Alemanha" to "MSV Duisburg") to 105_144L,
        ("Alemanha" to "SSV Jahn Regensburg") to 108_962L
    )

    private val legacyIdentities: List<StableTeamIdentity> = listOf(
        // Inglaterra — IDs preservados do baseline legado quando o clube já existia.
        StableTeamIdentity("Inglaterra", "Manchester City", 1L),
        StableTeamIdentity("Inglaterra", "Arsenal FC", 2L, setOf("Arsenal")),
        StableTeamIdentity("Inglaterra", "Liverpool FC", 3L, setOf("Liverpool")),
        StableTeamIdentity("Inglaterra", "Chelsea FC", 4L, setOf("Chelsea")),
        StableTeamIdentity("Inglaterra", "Manchester United", 5L),
        StableTeamIdentity("Inglaterra", "Tottenham Hotspur", 6L, setOf("Tottenham")),
        StableTeamIdentity("Inglaterra", "Aston Villa", 7L),
        StableTeamIdentity("Inglaterra", "Newcastle United", 8L),
        StableTeamIdentity("Inglaterra", "West Ham United", 9L, setOf("West Ham")),
        StableTeamIdentity("Inglaterra", "Everton FC", 10L, setOf("Everton")),
        StableTeamIdentity("Inglaterra", "AFC Bournemouth", 11L, setOf("Bournemouth")),
        StableTeamIdentity("Inglaterra", "Brentford FC", 12L, setOf("Brentford")),
        StableTeamIdentity("Inglaterra", "Brighton & Hove Albion", 13L, setOf("Brighton")),
        StableTeamIdentity("Inglaterra", "Crystal Palace", 14L),
        StableTeamIdentity("Inglaterra", "Fulham FC", 15L, setOf("Fulham")),
        StableTeamIdentity("Inglaterra", "Nottingham Forest", 16L),
        StableTeamIdentity("Inglaterra", "Burnley", 17L),
        StableTeamIdentity("Inglaterra", "Wolverhampton Wanderers", 18L, setOf("Wolves")),
        StableTeamIdentity("Inglaterra", "Leicester City", 21L),
        StableTeamIdentity("Inglaterra", "Leeds United", 22L),
        StableTeamIdentity("Inglaterra", "Southampton FC", 23L, setOf("Southampton")),
        StableTeamIdentity("Inglaterra", "Ipswich Town", 24L),
        StableTeamIdentity("Inglaterra", "West Bromwich Albion", 25L, setOf("West Bromwich", "West Brom")),
        StableTeamIdentity("Inglaterra", "Norwich City", 26L),
        StableTeamIdentity("Inglaterra", "Coventry City", 27L),
        StableTeamIdentity("Inglaterra", "Middlesbrough", 28L),
        StableTeamIdentity("Inglaterra", "Hull City", 29L, setOf("Hull")),
        StableTeamIdentity("Inglaterra", "Sunderland AFC", 30L, setOf("Sunderland")),
        StableTeamIdentity("Inglaterra", "Derby County", 45L),
        StableTeamIdentity("Inglaterra", "Portsmouth FC", 46L, setOf("Portsmouth")),
        StableTeamIdentity("Inglaterra", "Bolton Wanderers", 47L),
        StableTeamIdentity("Inglaterra", "Peterborough United", 48L, setOf("Peterborough Utd")),
        StableTeamIdentity("Inglaterra", "Barnsley FC", 49L, setOf("Barnsley")),
        StableTeamIdentity("Inglaterra", "Oxford United", 50L),
        StableTeamIdentity("Inglaterra", "Lincoln City", 51L),
        StableTeamIdentity("Inglaterra", "Blackpool FC", 52L, setOf("Blackpool")),
        StableTeamIdentity("Inglaterra", "Reading FC", 53L, setOf("Reading")),
        StableTeamIdentity("Inglaterra", "Wigan Athletic", 54L),

        // Espanha — bloco legado 201..; clubes promovidos mantêm o ID de sua divisão anterior.
        StableTeamIdentity("Espanha", "Real Madrid", 201L),
        StableTeamIdentity("Espanha", "FC Barcelona", 202L, setOf("Barcelona")),
        StableTeamIdentity("Espanha", "Atlético de Madrid", 203L),
        StableTeamIdentity("Espanha", "Girona FC", 204L, setOf("Girona")),
        StableTeamIdentity("Espanha", "Real Sociedad", 205L),
        StableTeamIdentity("Espanha", "Athletic Club", 206L, setOf("Athletic Bilbao")),
        StableTeamIdentity("Espanha", "Real Betis", 207L),
        StableTeamIdentity("Espanha", "Villarreal CF", 208L, setOf("Villarreal")),
        StableTeamIdentity("Espanha", "Sevilla FC", 209L),
        StableTeamIdentity("Espanha", "Valencia CF", 210L),
        StableTeamIdentity("Espanha", "CA Osasuna", 211L, setOf("Osasuna")),
        StableTeamIdentity("Espanha", "Celta de Vigo", 212L, setOf("Celta")),
        StableTeamIdentity("Espanha", "Deportivo Alavés", 213L, setOf("Alavés")),
        StableTeamIdentity("Espanha", "Getafe CF", 214L, setOf("Getafe")),
        StableTeamIdentity("Espanha", "Racing Santander", 215L, setOf("R. Racing Club", "Real Racing Club")),
        StableTeamIdentity("Espanha", "Rayo Vallecano", 216L),
        StableTeamIdentity("Espanha", "RCD Espanyol de Barcelona", 221L, setOf("Espanyol")),
        StableTeamIdentity("Espanha", "Real Zaragoza", 222L),
        StableTeamIdentity("Espanha", "Real Valladolid", 223L),
        StableTeamIdentity("Espanha", "SD Eibar", 224L, setOf("Eibar")),
        StableTeamIdentity("Espanha", "CD Leganés", 225L, setOf("Leganés")),
        StableTeamIdentity("Espanha", "Sporting de Gijón", 226L, setOf("Sporting Gijón")),
        StableTeamIdentity("Espanha", "Levante UD", 227L),
        StableTeamIdentity("Espanha", "Elche CF", 228L),
        StableTeamIdentity("Espanha", "CD Tenerife", 229L, setOf("Tenerife")),
        StableTeamIdentity("Espanha", "Real Oviedo", 230L),
        StableTeamIdentity("Espanha", "RC Deportivo", 243L, setOf("Deportivo La Coruña")),
        StableTeamIdentity("Espanha", "Málaga CF", 244L)
    )

    private val baselineGeneratedIdentities: List<StableTeamIdentity> = baselineCountryWindows.flatMap { (country, windowStart) ->
        val baseline = requireNotNull(EuropeanDomesticBaseline2026_27.forCountry(country)) {
            "Baseline UEFA ausente para $country"
        }
        require(baseline.coverage == EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT) {
            "IDs reais só podem ser reservados para baseline factual verificado: $country"
        }
        baseline.verifiedTopFlightClubs.map { canonicalName ->
            StableTeamIdentity(
                country = country,
                canonicalName = canonicalName,
                id = stableBaselineId(windowStart, country, canonicalName)
            )
        }
    }

    /**
     * Phase 9.11A2 reuses each country's already-reserved window. Only organizer-verified lower-tier
     * identities are appended; the deterministic hash and explicit collision policy stay unchanged.
     */
    private val auditedLowerTierIdentities: List<StableTeamIdentity> =
        AuditedLowerTierClubCoverage2026_27.lowerTierFactualTargets.map { target ->
            val windowStart = requireNotNull(baselineCountryWindows[target.country]) {
                "Lower-tier factual target has no stable country window: ${target.country}/${target.canonicalName}"
            }
            StableTeamIdentity(
                country = target.country,
                canonicalName = target.canonicalName,
                id = stableBaselineId(windowStart, target.country, target.canonicalName)
            )
        }

    /** Phase 9.11A3 extends the same frozen country windows; no legacy identity is moved. */
    private val auditedA3Identities: List<StableTeamIdentity> =
        AuditedFactualBaselinesA3_2026_27.factualTargets.map { target ->
            val windowStart = requireNotNull(baselineCountryWindows[target.country]) {
                "Phase 9.11A3 target has no stable country window: ${target.country}/${target.canonicalName}"
            }
            StableTeamIdentity(
                country = target.country,
                canonicalName = target.canonicalName,
                id = stableBaselineId(windowStart, target.country, target.canonicalName)
            )
        }

    private val identities: List<StableTeamIdentity> =
        legacyIdentities + baselineGeneratedIdentities + auditedLowerTierIdentities + auditedA3Identities

    private fun normalize(value: String): String = value.trim().lowercase()

    private fun stableBaselineId(windowStart: Long, country: String, canonicalName: String): Long {
        baselineIdOverrides[country to canonicalName]?.let { overridden ->
            require(overridden in windowStart + 1 until windowStart + COUNTRY_WINDOW_SIZE) {
                "Override fora da janela de $country: $canonicalName -> $overridden"
            }
            return overridden
        }

        var hash = 1469598103934665603L
        "${normalize(country)}|${normalize(canonicalName)}".forEach { ch ->
            hash = (hash xor ch.code.toLong()) * 1099511628211L
        }
        val positive = hash and Long.MAX_VALUE
        // Slot zero fica reservado para facilitar inspeção/debug do começo de cada janela.
        return windowStart + 1L + (positive % (COUNTRY_WINDOW_SIZE - 1L))
    }

    private val byCountryAndName: Map<Pair<String, String>, StableTeamIdentity> = buildMap {
        identities.forEach { identity ->
            require(identity.id in 1 until GlobalFootballSystem.VIRTUAL_TEAM_ID_FLOOR) {
                "ID estável fora do namespace persistido: ${identity.id}"
            }
            val names = identity.aliases + identity.canonicalName
            names.forEach { name ->
                val key = normalize(identity.country) to normalize(name)
                require(put(key, identity) == null) {
                    "Alias duplicado de clube estável: ${identity.country}/$name"
                }
            }
        }
    }

    private val byId: Map<Long, StableTeamIdentity> = identities.associateBy { it.id }.also { map ->
        require(map.size == identities.size) {
            val collisions = identities.groupBy { it.id }
                .filterValues { it.size > 1 }
                .entries
                .joinToString { (id, clubs) -> "$id=${clubs.joinToString { "${it.country}/${it.canonicalName}" }}" }
            "Há colisão de IDs estáveis: $collisions. Adicione override explícito; nunca resolva por ordem de lista."
        }
    }

    fun idFor(country: String, teamName: String): Long? {
        val canonicalCountry = CountryFootballRulesRegistry.resolve(country)?.canonicalCountry ?: country
        return byCountryAndName[normalize(canonicalCountry) to normalize(teamName)]?.id
    }

    fun identityForId(id: Long): StableTeamIdentity? = byId[id]

    fun canonicalNameFor(country: String, teamName: String): String? {
        val canonicalCountry = CountryFootballRulesRegistry.resolve(country)?.canonicalCountry ?: country
        return byCountryAndName[normalize(canonicalCountry) to normalize(teamName)]?.canonicalName
    }

    fun isStableRealClub(country: String, teamName: String): Boolean = idFor(country, teamName) != null

    val all: List<StableTeamIdentity>
        get() = identities
}
