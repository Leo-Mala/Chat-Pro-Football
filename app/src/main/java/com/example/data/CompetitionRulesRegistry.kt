package com.example.data

/** Identidade estrutural independente do código legado persistido em Fixture. */
enum class CompetitionIdentity {
    DOMESTIC_LEAGUE_1,
    DOMESTIC_LEAGUE_2,
    DOMESTIC_LEAGUE_3,
    DOMESTIC_LEAGUE_4,
    NATIONAL_CUP,
    STATE_COMPETITION,
    LEGACY_CONTINENTAL_T1,
    LEGACY_CONTINENTAL_T2,
    LEGACY_CONTINENTAL_T3,
    SUPER_MUNDIAL,
    WORLD_INTERCONTINENTAL,
    UEFA_CHAMPIONS_LEAGUE,
    UEFA_EUROPA_LEAGUE,
    UEFA_CONFERENCE_LEAGUE,
    CONMEBOL_LIBERTADORES,
    CONMEBOL_SUDAMERICANA,
    CONCACAF_CHAMPIONS_CUP,
    CONCACAF_CENTRAL_AMERICAN_CUP,
    CONCACAF_CARIBBEAN_CUP,
    CAF_CHAMPIONS_LEAGUE,
    CAF_CONFEDERATION_CUP,
    AFC_CHAMPIONS_LEAGUE_ELITE,
    AFC_CHAMPIONS_LEAGUE_TWO,
    AFC_CHALLENGE_LEAGUE,
    OFC_CHAMPIONS_LEAGUE
}

enum class CompetitionImplementationStatus {
    STRUCTURAL,
    DEDICATED,
    LEGACY_GENERIC,
    REAL_RULES_NOT_IMPLEMENTED,
    CATALOG_ONLY
}

enum class ConfederationEngineKind {
    DEDICATED_UEFA,
    DEDICATED_CONMEBOL,
    LEGACY_GENERIC,
    NONE
}

data class CompetitionRuleDefinition(
    val identity: CompetitionIdentity,
    val code: String,
    val displayName: String,
    val category: String,
    val level: Int,
    val confederation: FootballConfederation? = null,
    val startWeek: Int? = null,
    val endWeek: Int? = null,
    val implementationStatus: CompetitionImplementationStatus,
    val aliases: Set<String> = emptySet()
)

/**
 * Representação tipada dos tiers realmente registrados por uma confederação.
 * Ausência permanece null; nenhum tier é substituído silenciosamente por outro torneio.
 */
data class ContinentalCompetitionSet(
    val tier1: CompetitionRuleDefinition?,
    val tier2: CompetitionRuleDefinition?,
    val tier3: CompetitionRuleDefinition?
) {
    val all: List<CompetitionRuleDefinition>
        get() = listOfNotNull(tier1, tier2, tier3)

    init {
        val levels = all.map { it.level }
        require(levels.size == levels.toSet().size) {
            "Conjunto continental contém níveis duplicados: $levels"
        }
    }
}

/** Registry canônico para identidade/metadados de competições. */
object CompetitionRulesRegistry {

    private val compatibilityDefinitions = listOf(
        CompetitionRuleDefinition(
            CompetitionIdentity.DOMESTIC_LEAGUE_1,
            "SERIE_A",
            "Primeira Divisão",
            "LEAGUE",
            1,
            implementationStatus = CompetitionImplementationStatus.STRUCTURAL,
            aliases = setOf("DIV_1")
        ),
        CompetitionRuleDefinition(
            CompetitionIdentity.DOMESTIC_LEAGUE_2,
            "SERIE_B",
            "Segunda Divisão",
            "LEAGUE",
            2,
            implementationStatus = CompetitionImplementationStatus.STRUCTURAL,
            aliases = setOf("DIV_2")
        ),
        CompetitionRuleDefinition(
            CompetitionIdentity.DOMESTIC_LEAGUE_3,
            "SERIE_C",
            "Terceira Divisão",
            "LEAGUE",
            3,
            implementationStatus = CompetitionImplementationStatus.STRUCTURAL,
            aliases = setOf("DIV_3")
        ),
        CompetitionRuleDefinition(
            CompetitionIdentity.DOMESTIC_LEAGUE_4,
            "SERIE_D",
            "Quarta Divisão ou inferior",
            "LEAGUE",
            4,
            implementationStatus = CompetitionImplementationStatus.STRUCTURAL,
            aliases = setOf("DIV_4")
        ),
        CompetitionRuleDefinition(
            CompetitionIdentity.NATIONAL_CUP,
            "COPA",
            "Copa Nacional",
            "CUP",
            0,
            implementationStatus = CompetitionImplementationStatus.LEGACY_GENERIC,
            aliases = setOf("CUP")
        ),
        CompetitionRuleDefinition(
            CompetitionIdentity.STATE_COMPETITION,
            "STATE",
            "Competição Estadual",
            "CUP",
            0,
            implementationStatus = CompetitionImplementationStatus.CATALOG_ONLY,
            aliases = setOf("ESTADUAL")
        ),
        CompetitionRuleDefinition(
            CompetitionIdentity.LEGACY_CONTINENTAL_T1,
            "CONTINENTAL_T1",
            "Continental Tier 1 (código legado)",
            "CONTINENTAL",
            1,
            implementationStatus = CompetitionImplementationStatus.LEGACY_GENERIC,
            aliases = setOf("LIBERTADORES")
        ),
        CompetitionRuleDefinition(
            CompetitionIdentity.LEGACY_CONTINENTAL_T2,
            "CONTINENTAL_T2",
            "Continental Tier 2 (código legado)",
            "CONTINENTAL",
            2,
            implementationStatus = CompetitionImplementationStatus.LEGACY_GENERIC,
            aliases = setOf("SULAMERICANA")
        ),
        CompetitionRuleDefinition(
            CompetitionIdentity.LEGACY_CONTINENTAL_T3,
            "CONTINENTAL_T3",
            "Continental Tier 3 (código legado)",
            "CONTINENTAL",
            3,
            implementationStatus = CompetitionImplementationStatus.LEGACY_GENERIC
        )
    )

    /** Catálogo exposto pelo GlobalFootballSystem, derivado de uma única definição. */
    val catalogDefinitions: List<CompetitionRuleDefinition> = listOf(
        CompetitionRuleDefinition(
            CompetitionIdentity.SUPER_MUNDIAL,
            "WORLD_CUP",
            "FIFA Club World Cup 🌍",
            "WORLD",
            1,
            startWeek = SuperMundialSystem.GROUP_WEEK_1,
            endWeek = GameCalendar.WEEKS_PER_SEASON,
            implementationStatus = CompetitionImplementationStatus.DEDICATED,
            aliases = setOf("WORLD")
        ),
        CompetitionRuleDefinition(
            CompetitionIdentity.WORLD_INTERCONTINENTAL,
            "WORLD_INTERCONTINENTAL",
            "FIFA Intercontinental Cup 🏆",
            "WORLD",
            2,
            startWeek = 40,
            endWeek = 41,
            implementationStatus = CompetitionImplementationStatus.CATALOG_ONLY
        ),
        continental(
            CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE,
            UefaCompetitionSystem.CHAMPIONS_LEAGUE,
            "UEFA Champions League 🇪🇺",
            1,
            FootballConfederation.UEFA,
            CompetitionImplementationStatus.DEDICATED,
            startWeek = UefaCompetitionSystem.CHAMPIONS_EUROPA_LEAGUE_WEEKS.first(),
            endWeek = UefaCompetitionSystem.FINAL_WEEK
        ),
        continental(
            CompetitionIdentity.UEFA_EUROPA_LEAGUE,
            UefaCompetitionSystem.EUROPA_LEAGUE,
            "UEFA Europa League 🇪🇺",
            2,
            FootballConfederation.UEFA,
            CompetitionImplementationStatus.DEDICATED,
            startWeek = UefaCompetitionSystem.CHAMPIONS_EUROPA_LEAGUE_WEEKS.first(),
            endWeek = UefaCompetitionSystem.FINAL_WEEK
        ),
        continental(
            CompetitionIdentity.UEFA_CONFERENCE_LEAGUE,
            UefaCompetitionSystem.CONFERENCE_LEAGUE,
            "UEFA Conference League 🇪🇺",
            3,
            FootballConfederation.UEFA,
            CompetitionImplementationStatus.DEDICATED,
            startWeek = UefaCompetitionSystem.CONFERENCE_LEAGUE_WEEKS.first(),
            endWeek = UefaCompetitionSystem.FINAL_WEEK
        ),
        continental(
            CompetitionIdentity.CONMEBOL_LIBERTADORES,
            "CONMEBOL_CL",
            "Copa Libertadores 🏆",
            1,
            FootballConfederation.CONMEBOL,
            CompetitionImplementationStatus.DEDICATED,
            startWeek = ConmebolCompetitionSystem.GROUP_WEEKS.first(),
            endWeek = ConmebolCompetitionSystem.FINAL_WEEK
        ),
        continental(
            CompetitionIdentity.CONMEBOL_SUDAMERICANA,
            "CONMEBOL_CS",
            "Copa Sudamericana 🥈",
            2,
            FootballConfederation.CONMEBOL,
            CompetitionImplementationStatus.DEDICATED,
            startWeek = ConmebolCompetitionSystem.GROUP_WEEKS.first(),
            endWeek = ConmebolCompetitionSystem.FINAL_WEEK
        ),
        continental(
            CompetitionIdentity.CONCACAF_CHAMPIONS_CUP,
            "CONCACAF_CL",
            "CONCACAF Champions Cup 🏆",
            1,
            FootballConfederation.CONCACAF,
            CompetitionImplementationStatus.REAL_RULES_NOT_IMPLEMENTED
        ),
        continental(
            CompetitionIdentity.CONCACAF_CENTRAL_AMERICAN_CUP,
            "CONCACAF_CAC",
            "CONCACAF Central American Cup 🌎",
            2,
            FootballConfederation.CONCACAF,
            CompetitionImplementationStatus.REAL_RULES_NOT_IMPLEMENTED
        ),
        continental(
            CompetitionIdentity.CONCACAF_CARIBBEAN_CUP,
            "CONCACAF_CC",
            "CONCACAF Caribbean Cup 🏝️",
            3,
            FootballConfederation.CONCACAF,
            CompetitionImplementationStatus.REAL_RULES_NOT_IMPLEMENTED
        ),
        continental(
            CompetitionIdentity.CAF_CHAMPIONS_LEAGUE,
            "CAF_CL",
            "CAF Champions League 🏆",
            1,
            FootballConfederation.CAF,
            CompetitionImplementationStatus.REAL_RULES_NOT_IMPLEMENTED
        ),
        continental(
            CompetitionIdentity.CAF_CONFEDERATION_CUP,
            "CAF_CC",
            "CAF Confederation Cup 🥈",
            2,
            FootballConfederation.CAF,
            CompetitionImplementationStatus.REAL_RULES_NOT_IMPLEMENTED
        ),
        continental(
            CompetitionIdentity.AFC_CHAMPIONS_LEAGUE_ELITE,
            "AFC_CLE",
            "AFC Champions League Elite 🏆",
            1,
            FootballConfederation.AFC,
            CompetitionImplementationStatus.REAL_RULES_NOT_IMPLEMENTED
        ),
        continental(
            CompetitionIdentity.AFC_CHAMPIONS_LEAGUE_TWO,
            "AFC_CL2",
            "AFC Champions League Two 🥈",
            2,
            FootballConfederation.AFC,
            CompetitionImplementationStatus.REAL_RULES_NOT_IMPLEMENTED
        ),
        continental(
            CompetitionIdentity.AFC_CHALLENGE_LEAGUE,
            "AFC_CHL",
            "AFC Challenge League 🥉",
            3,
            FootballConfederation.AFC,
            CompetitionImplementationStatus.REAL_RULES_NOT_IMPLEMENTED
        ),
        continental(
            CompetitionIdentity.OFC_CHAMPIONS_LEAGUE,
            "OFC_CL",
            "OFC Champions League 🏆",
            1,
            FootballConfederation.OFC,
            CompetitionImplementationStatus.REAL_RULES_NOT_IMPLEMENTED
        )
    )

    private val allDefinitions = compatibilityDefinitions + catalogDefinitions

    private val byCodeOrAlias: Map<String, CompetitionRuleDefinition> = buildMap {
        allDefinitions.forEach { definition ->
            putUnique(normalize(definition.code), definition)
            definition.aliases.forEach { alias -> putUnique(normalize(alias), definition) }
        }
    }

    val allCanonicalCodes: Set<String>
        get() = allDefinitions.map { it.code }.toSet()

    fun resolve(code: String): CompetitionRuleDefinition? {
        val normalized = normalize(code)
        byCodeOrAlias[normalized]?.let { return it }

        return when {
            normalized.startsWith("continental_t1_gp_") -> byCodeOrAlias["continental_t1"]
            normalized.startsWith("continental_t2_gp_") -> byCodeOrAlias["continental_t2"]
            normalized.startsWith("world_cup_gp_") -> byCodeOrAlias["world_cup"]
            else -> null
        }
    }

    fun continentalCompetitionSet(confederation: FootballConfederation): ContinentalCompetitionSet {
        val byLevel = catalogDefinitions
            .filter { it.confederation == confederation }
            .associateBy { it.level }
        return ContinentalCompetitionSet(
            tier1 = byLevel[1],
            tier2 = byLevel[2],
            tier3 = byLevel[3]
        )
    }

    fun continentalCatalogFor(confederation: FootballConfederation): List<CompetitionRuleDefinition> =
        continentalCompetitionSet(confederation).all

    /**
     * Adaptador legado de três tiers. Retorna null quando a confederação não possui exatamente os
     * três níveis; ausência nunca é substituída pelo Tier 1.
     */
    fun continentalCatalogCodesOrNull(
        confederation: FootballConfederation
    ): Triple<String, String, String>? {
        val set = continentalCompetitionSet(confederation)
        val tier1 = set.tier1?.code ?: return null
        val tier2 = set.tier2?.code ?: return null
        val tier3 = set.tier3?.code ?: return null
        return Triple(tier1, tier2, tier3)
    }

    /** Adaptador legado estrito. Novos consumidores devem usar [continentalCompetitionSet]. */
    fun continentalCatalogCodes(confederation: FootballConfederation): Triple<String, String, String> =
        requireNotNull(continentalCatalogCodesOrNull(confederation)) {
            "Confederação ${confederation.code} não possui três tiers continentais registrados."
        }

    fun engineForConfederation(confederation: FootballConfederation): ConfederationEngineKind =
        when (confederation) {
            FootballConfederation.UEFA -> ConfederationEngineKind.DEDICATED_UEFA
            FootballConfederation.CONMEBOL -> ConfederationEngineKind.DEDICATED_CONMEBOL
            else -> ConfederationEngineKind.LEGACY_GENERIC
        }

    fun hasRealDedicatedRules(confederation: FootballConfederation): Boolean =
        engineForConfederation(confederation) in setOf(
            ConfederationEngineKind.DEDICATED_UEFA,
            ConfederationEngineKind.DEDICATED_CONMEBOL
        )

    private fun continental(
        identity: CompetitionIdentity,
        code: String,
        name: String,
        level: Int,
        confederation: FootballConfederation,
        status: CompetitionImplementationStatus,
        startWeek: Int = 33,
        endWeek: Int = 36
    ): CompetitionRuleDefinition = CompetitionRuleDefinition(
        identity = identity,
        code = code,
        displayName = name,
        category = "CONTINENTAL",
        level = level,
        confederation = confederation,
        startWeek = startWeek,
        endWeek = endWeek,
        implementationStatus = status
    )

    private fun normalize(value: String): String = value.trim().lowercase()

    private fun MutableMap<String, CompetitionRuleDefinition>.putUnique(
        key: String,
        definition: CompetitionRuleDefinition
    ) {
        val previous = put(key, definition)
        require(previous == null || previous.identity == definition.identity) {
            "Código/alias de competição duplicado: $key (${previous?.identity} x ${definition.identity})"
        }
    }
}
