package com.example.data

/**
 * Explicit, snapshot-scoped bridge between FC26 club identities and the Pro Football universe.
 *
 * `club_team_id` is never assumed to equal `Team.id`. Overrides exist only where both identities
 * have been audited. Stable clubs may pin the immutable Team.id; legacy explicit templates use
 * country + canonical template name so a harmless list reorder cannot silently remap a club.
 * League -> country context is diagnostic evidence only and is never sufficient to match by itself.
 */
internal object Fc26ClubMappingRegistry {
    data class ExplicitMapping(
        val sourceClubTeamId: Long,
        val acceptedSourceNames: Set<String>,
        val targetCountry: String,
        val targetCanonicalName: String,
        val targetTeamId: Long? = null,
        val reason: String
    )

    private val baseExplicitMappings = listOf(
        // Stable England/Spain identities.
        ExplicitMapping(9L, setOf("Liverpool"), "Inglaterra", "Liverpool FC", 3L, "FC26 source id + stable legacy identity"),
        ExplicitMapping(448L, setOf("Athletic Club"), "Espanha", "Athletic Club", 206L, "FC26 source id + stable legacy identity"),
        ExplicitMapping(449L, setOf("Real Betis Balompié"), "Espanha", "Real Betis", 207L, "FC26 source id + audited canonical variant"),
        ExplicitMapping(450L, setOf("RC Celta"), "Espanha", "Celta de Vigo", 212L, "FC26 source id + audited canonical variant"),
        ExplicitMapping(452L, setOf("RCD Espanyol"), "Espanha", "RCD Espanyol de Barcelona", 221L, "FC26 source id + audited canonical variant"),
        ExplicitMapping(459L, setOf("Real Sporting de Gijón"), "Espanha", "Sporting de Gijón", 226L, "FC26 source id + audited canonical variant"),
        ExplicitMapping(242L, setOf("RC Deportivo de La Coruña"), "Espanha", "RC Deportivo", 243L, "FC26 source id + audited canonical variant"),

        // Explicit (non-procedural) templates already materialized in DefaultData.
        ExplicitMapping(100852L, setOf("CD Castellón"), "Espanha", "Castellón", reason = "FC26 source id + explicit Spain TeamTemplate"),
        ExplicitMapping(569L, setOf("Vasco da Gama"), "Brasil", "Vasco", reason = "FC26 source id + explicit Brazil TeamTemplate"),
        ExplicitMapping(1035L, setOf("Atlético Mineiro"), "Brasil", "Atlético-MG", reason = "FC26 source id + explicit Brazil TeamTemplate"),
        ExplicitMapping(101084L, setOf("Gimnasia y Esgrima La Plata"), "Argentina", "Gimnasia LP", reason = "FC26 source id + explicit Argentina TeamTemplate"),
        ExplicitMapping(112965L, setOf("Central Cordoba SdE"), "Argentina", "Central Córdoba", reason = "FC26 source id + explicit Argentina TeamTemplate"),
        ExplicitMapping(111020L, setOf("Independiente Rivadavia"), "Argentina", "Independiente Riv.", reason = "FC26 source id + explicit Argentina TeamTemplate"),
        ExplicitMapping(111022L, setOf("Belgrano de Córdoba"), "Argentina", "Belgrano", reason = "FC26 source id + explicit Argentina TeamTemplate"),
        ExplicitMapping(112713L, setOf("Club Atlético Sarmiento"), "Argentina", "Sarmiento Junín", reason = "FC26 source id + explicit Argentina TeamTemplate"),
        ExplicitMapping(110404L, setOf("CA Banfield"), "Argentina", "Banfield", reason = "FC26 source id + explicit Argentina TeamTemplate"),
        ExplicitMapping(101083L, setOf("Estudiantes de La Plata"), "Argentina", "Estudiantes LP", reason = "FC26 source id + explicit Argentina TeamTemplate"),
        ExplicitMapping(110953L, setOf("Instituto Atlético Central Córdoba"), "Argentina", "Instituto ACC", reason = "FC26 source id + explicit Argentina TeamTemplate"),
        ExplicitMapping(1013L, setOf("San Lorenzo de Almagro"), "Argentina", "San Lorenzo", reason = "FC26 source id + explicit Argentina TeamTemplate"),
        ExplicitMapping(111716L, setOf("Club Atlético Unión"), "Argentina", "Unión Santa Fe", reason = "FC26 source id + explicit Argentina TeamTemplate"),
        ExplicitMapping(112670L, setOf("Talleres"), "Argentina", "Talleres Córdoba", reason = "FC26 source id + explicit Argentina TeamTemplate")
    )

    /**
     * Phase 9.11A2 mappings are activated only while its materializer is installed. This makes the
     * 9.11A1 baseline reproducible in tests and guarantees that a report can compare before/after
     * without the new mappings leaking into the historical side of the comparison.
     */
    private val phaseA2Mappings: List<ExplicitMapping> by lazy {
        val variants = Fc26RemainingClubCoverage2026_27.existingTargetNameVariants.map { variant ->
            ExplicitMapping(
                sourceClubTeamId = variant.sourceClubTeamId,
                acceptedSourceNames = setOf(variant.sourceName),
                targetCountry = variant.country,
                targetCanonicalName = variant.targetCanonicalName,
                targetTeamId = requireNotNull(
                    StableTeamIdentityRegistry.idFor(variant.country, variant.targetCanonicalName)
                ) { "Missing stable Phase 9.11A2 target: ${variant.country}/${variant.targetCanonicalName}" },
                reason = "FC26 source id + audited 2026/27 stable target name variant"
            )
        }
        val lowerTier = Fc26RemainingClubCoverage2026_27.lowerTierFactualTargets.map { target ->
            ExplicitMapping(
                sourceClubTeamId = target.sourceClubTeamId,
                acceptedSourceNames = setOf(target.sourceName),
                targetCountry = target.country,
                targetCanonicalName = target.canonicalName,
                targetTeamId = requireNotNull(
                    StableTeamIdentityRegistry.idFor(target.country, target.canonicalName)
                ) { "Missing stable Phase 9.11A2 lower-tier target: ${target.country}/${target.canonicalName}" },
                reason = "FC26 source id + organizer-verified ${target.competitionName} 2026/27 identity"
            )
        }
        (variants + lowerTier).also { mappings ->
            require(mappings.size == 89)
            require(mappings.map { it.sourceClubTeamId }.distinct().size == mappings.size)
        }
    }

    private val baseBySourceId = baseExplicitMappings.associateBy { it.sourceClubTeamId }.also { map ->
        require(map.size == baseExplicitMappings.size) { "Duplicate FC26 source club override." }
    }

    private val phaseA2BySourceId: Map<Long, ExplicitMapping> by lazy {
        phaseA2Mappings.associateBy { it.sourceClubTeamId }
    }

    private val leagueCountries = mapOf(
        7L to "Brasil",
        13L to "Inglaterra",
        14L to "Inglaterra",
        60L to "Inglaterra",
        61L to "Inglaterra",
        53L to "Espanha",
        54L to "Espanha",
        31L to "Itália",
        32L to "Itália",
        19L to "Alemanha",
        20L to "Alemanha",
        2076L to "Alemanha",
        16L to "França",
        17L to "França",
        308L to "Portugal",
        10L to "Países Baixos",
        4L to "Bélgica",
        68L to "Turquia",
        50L to "Escócia",
        80L to "Áustria",
        189L to "Suíça",
        1L to "Dinamarca",
        41L to "Noruega",
        56L to "Suécia",
        66L to "Polônia",
        319L to "Tchéquia",
        317L to "Croácia",
        63L to "Grécia",
        353L to "Argentina",
        336L to "Colômbia",
        335L to "Chile",
        338L to "Uruguai",
        337L to "Paraguai",
        2018L to "Equador",
        2020L to "Peru",
        2017L to "Bolívia",
        2019L to "Venezuela",
        39L to "Estados Unidos / Canadá",
        83L to "Coreia do Sul",
        350L to "Arábia Saudita",
        2013L to "Emirados Árabes Unidos",
        2012L to "China",
        351L to "Austrália"
    )

    fun countryFor(source: Fc26SourceClub): String? = source.leagueId?.let(leagueCountries::get)

    fun explicitMappingFor(source: Fc26SourceClub): ExplicitMapping? {
        val mapping = baseBySourceId[source.sourceClubTeamId]
            ?: if (EuropeanAuditedLowerTierClubTargetMaterializer2026_27.isInstalled()) {
                phaseA2BySourceId[source.sourceClubTeamId]
            } else {
                null
            }
            ?: return null
        val normalizedSource = Fc26ClubMatcher.normalize(source.clubName)
        return mapping.takeIf { candidate ->
            candidate.acceptedSourceNames.any { Fc26ClubMatcher.normalize(it) == normalizedSource }
        }
    }

    fun allExplicitMappings(): List<ExplicitMapping> =
        if (EuropeanAuditedLowerTierClubTargetMaterializer2026_27.isInstalled()) {
            baseExplicitMappings + phaseA2Mappings
        } else {
            baseExplicitMappings.toList()
        }
}
