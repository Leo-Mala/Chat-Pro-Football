package com.example.data

/** Classifica a natureza da entrada resolvida pelo registry mundial. */
enum class CountryRuleKind {
    NATIONAL_ASSOCIATION,
    LEGACY_AGGREGATE,
    VIRTUAL_WORLD
}

/**
 * Regra mínima de identidade futebolística de um país/região nesta fase.
 *
 * Regras de liga/copa específicas serão expandidas nas próximas subfases; 9.10B1 mantém somente
 * o necessário para resolver identidade, elegibilidade e confederação sem fallback silencioso.
 */
data class CountryFootballRules(
    val canonicalCountry: String,
    val confederation: FootballConfederation?,
    val kind: CountryRuleKind,
    val aliases: Set<String> = emptySet(),
    val domesticCompetitionsAllowed: Boolean = kind == CountryRuleKind.NATIONAL_ASSOCIATION,
    val legacyConfederationCode: String? = confederation?.code
) {
    val continentalCompetitionsAllowed: Boolean
        get() = confederation != null && kind == CountryRuleKind.NATIONAL_ASSOCIATION
}

/**
 * Source of truth de país/região -> identidade futebolística -> confederação.
 *
 * País desconhecido retorna null. "Mundial" é conhecido, mas explicitamente não é associação
 * nacional nem pertence a uma confederação continental.
 */
object CountryFootballRulesRegistry {

    private val nationalCountriesByConfederation: Map<FootballConfederation, List<String>> = mapOf(
        FootballConfederation.UEFA to listOf(
            "Inglaterra", "Espanha", "Itália", "Alemanha", "França", "Portugal",
            "Países Baixos", "Bélgica", "Turquia", "Escócia", "Áustria", "Suíça",
            "Dinamarca", "Noruega", "Suécia", "Polônia", "Tchéquia", "Croácia",
            "Sérvia", "Grécia"
        ),
        FootballConfederation.CONMEBOL to listOf(
            "Brasil", "Argentina", "Colômbia", "Chile", "Uruguai", "Paraguai", "Equador",
            "Peru", "Bolívia", "Venezuela"
        ),
        FootballConfederation.CONCACAF to listOf(
            "México", "Estados Unidos / Canadá", "Costa Rica", "Guatemala", "Honduras",
            "Panamá", "El Salvador", "Jamaica", "República Dominicana", "Trinidad e Tobago"
        ),
        FootballConfederation.AFC to listOf(
            "Japão", "Coreia do Sul", "Arábia Saudita", "Emirados Árabes Unidos", "Catar",
            "Irã", "China", "Austrália"
        ),
        FootballConfederation.CAF to listOf(
            "Egito", "Marrocos", "Tunísia", "África do Sul"
        ),
        // O dataset normal ainda não possui associações nacionais OFC na Fase 9.10B1.
        FootballConfederation.OFC to emptyList()
    )

    private val canonicalRules: List<CountryFootballRules> = buildList {
        nationalCountriesByConfederation.forEach { (confederation, countries) ->
            countries.forEach { country ->
                val aliases = when (country) {
                    "Estados Unidos / Canadá" -> setOf("Estados Unidos / México")
                    else -> emptySet()
                }
                add(
                    CountryFootballRules(
                        canonicalCountry = country,
                        confederation = confederation,
                        kind = CountryRuleKind.NATIONAL_ASSOCIATION,
                        aliases = aliases
                    )
                )
            }
        }

        add(
            CountryFootballRules(
                canonicalCountry = "América Central",
                confederation = FootballConfederation.CONCACAF,
                kind = CountryRuleKind.LEGACY_AGGREGATE
            )
        )
        add(
            CountryFootballRules(
                canonicalCountry = "África",
                confederation = FootballConfederation.CAF,
                kind = CountryRuleKind.LEGACY_AGGREGATE
            )
        )
        add(
            CountryFootballRules(
                canonicalCountry = "Ásia",
                confederation = FootballConfederation.AFC,
                kind = CountryRuleKind.LEGACY_AGGREGATE
            )
        )
        add(
            CountryFootballRules(
                canonicalCountry = "Oceania",
                confederation = FootballConfederation.OFC,
                kind = CountryRuleKind.LEGACY_AGGREGATE
            )
        )
        add(
            CountryFootballRules(
                canonicalCountry = "África / Ásia / Oceania",
                confederation = null,
                kind = CountryRuleKind.LEGACY_AGGREGATE,
                legacyConfederationCode = "MIXED"
            )
        )
        add(
            CountryFootballRules(
                canonicalCountry = "Mundial",
                confederation = null,
                kind = CountryRuleKind.VIRTUAL_WORLD,
                domesticCompetitionsAllowed = false,
                legacyConfederationCode = null
            )
        )
    }

    private val byNormalizedName: Map<String, CountryFootballRules> = buildMap {
        canonicalRules.forEach { rules ->
            put(normalize(rules.canonicalCountry), rules)
            rules.aliases.forEach { alias -> put(normalize(alias), rules) }
        }
    }

    val knownCanonicalCountries: Set<String>
        get() = nationalCountriesByConfederation.values.flatten().toSet()

    fun resolve(country: String): CountryFootballRules? = byNormalizedName[normalize(country)]

    fun confederationFor(country: String): FootballConfederation? = resolve(country)?.confederation

    fun legacyConfederationCodeFor(country: String): String? = resolve(country)?.legacyConfederationCode

    fun isDomesticCompetitionEligible(country: String): Boolean =
        resolve(country)?.domesticCompetitionsAllowed == true

    fun isContinentalCompetitionEligible(country: String): Boolean =
        resolve(country)?.continentalCompetitionsAllowed == true

    private fun normalize(value: String): String = value.trim().lowercase()
}
