package com.example.data

/**
 * Explicit, snapshot-scoped bridge between FC26 club identities and the Pro Football universe.
 *
 * `club_team_id` is never assumed to equal `Team.id`. Overrides exist only where both identities
 * have been audited. League -> country context is used for diagnostics/stable identity lookup,
 * never as sufficient evidence to match a club by itself.
 */
internal object Fc26ClubMappingRegistry {
    data class ExplicitMapping(
        val sourceClubTeamId: Long,
        val acceptedSourceNames: Set<String>,
        val targetTeamId: Long,
        val targetCanonicalName: String,
        val reason: String
    )

    private val explicitMappings = listOf(
        ExplicitMapping(9L, setOf("Liverpool"), 3L, "Liverpool FC", "FC26 source id + stable legacy identity"),
        ExplicitMapping(448L, setOf("Athletic Club"), 206L, "Athletic Club", "FC26 source id + stable legacy identity"),
        ExplicitMapping(449L, setOf("Real Betis Balompié"), 207L, "Real Betis", "FC26 source id + audited canonical variant"),
        ExplicitMapping(450L, setOf("RC Celta"), 212L, "Celta de Vigo", "FC26 source id + audited canonical variant"),
        ExplicitMapping(452L, setOf("RCD Espanyol"), 221L, "RCD Espanyol de Barcelona", "FC26 source id + audited canonical variant"),
        ExplicitMapping(459L, setOf("Real Sporting de Gijón"), 226L, "Sporting de Gijón", "FC26 source id + audited canonical variant"),
        ExplicitMapping(242L, setOf("RC Deportivo de La Coruña"), 243L, "RC Deportivo", "FC26 source id + audited canonical variant")
    )

    private val bySourceId = explicitMappings.associateBy { it.sourceClubTeamId }.also { map ->
        require(map.size == explicitMappings.size) { "Duplicate FC26 source club override." }
    }

    private val leagueCountries = mapOf(
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
        val mapping = bySourceId[source.sourceClubTeamId] ?: return null
        val normalizedSource = Fc26ClubMatcher.normalize(source.clubName)
        return mapping.takeIf { candidate ->
            candidate.acceptedSourceNames.any { Fc26ClubMatcher.normalize(it) == normalizedSource }
        }
    }

    fun allExplicitMappings(): List<ExplicitMapping> = explicitMappings.toList()
}
