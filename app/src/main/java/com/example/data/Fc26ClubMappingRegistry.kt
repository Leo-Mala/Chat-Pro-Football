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

    private val explicitMappings = listOf(
        // Stable England/Spain identities.
        ExplicitMapping(9L, setOf("Liverpool"), "Inglaterra", "Liverpool FC", 3L, "FC26 source id + stable legacy identity"),
        ExplicitMapping(448L, setOf("Athletic Club"), "Espanha", "Athletic Club", 206L, "FC26 source id + stable legacy identity"),
        ExplicitMapping(449L, setOf("Real Betis Balompié"), "Espanha", "Real Betis", 207L, "FC26 source id + audited canonical variant"),
        ExplicitMapping(450L, setOf("RC Celta"), "Espanha", "Celta de Vigo", 212L, "FC26 source id + audited canonical variant"),
        ExplicitMapping(452L, setOf("RCD Espanyol"), "Espanha", "RCD Espanyol de Barcelona", 221L, "FC26 source id + audited canonical variant"),
        ExplicitMapping(459L, setOf("Real Sporting de Gijón"), "Espanha", "Sporting de Gijón", 226L, "FC26 source id + audited canonical variant"),
        ExplicitMapping(242L, setOf("RC Deportivo de La Coruña"), "Espanha", "RC Deportivo", 243L, "FC26 source id + audited canonical variant"),

        // Phase 9.11A2 — audited FC26 spelling/name variants for already-materialized stable targets.
        // These entries do not create aliases globally: the bridge is scoped to exact FC26 source id
        // plus an accepted source spelling, country, canonical target and immutable stable Team.id.
        ExplicitMapping(10029L, setOf("TSG 1899 Hoffenheim"), "Alemanha", "TSG Hoffenheim", 109159L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(231L, setOf("Club Brugge KV"), "Bélgica", "Club Brugge", 125386L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(100087L, setOf("Oud-Heverlee Leuven"), "Bélgica", "OH Leuven", 126961L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(1750L, setOf("Cercle Brugge KSV"), "Bélgica", "Cercle Brugge", 129618L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(2014L, setOf("Union Saint-Gilloise"), "Bélgica", "Royale Union Saint-Gilloise", 129911L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(670L, setOf("Royal Charleroi Sporting Club"), "Bélgica", "Sporting Charleroi", 125962L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(680L, setOf("Sint-Truidense VV"), "Bélgica", "STVV", 125175L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(272L, setOf("Odense Boldklub"), "Dinamarca", "OB", 152058L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(271L, setOf("Aarhus Gymnastikforening"), "Dinamarca", "AGF", 153818L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(80L, setOf("Hearts"), "Escócia", "Heart of Midlothian", 138407L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(65L, setOf("Lille OSC"), "França", "LOSC", 110617L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(278L, setOf("AEK Athens"), "Grécia", "AEK", 187739L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(112199L, setOf("Sarpsborg 08 FF"), "Noruega", "Sarpsborg 08", 157447L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(1756L, setOf("Hamarkameratene"), "Noruega", "HamKam", 155363L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(113459L, setOf("Kristiansund BK"), "Noruega", "Kristiansund", 158592L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(417L, setOf("Molde FK"), "Noruega", "Molde", 159335L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(300L, setOf("Viking FK"), "Noruega", "Viking", 158076L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(918L, setOf("FK Bodø/Glimt"), "Noruega", "Bodø/Glimt", 159807L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(919L, setOf("SK Brann"), "Noruega", "Brann", 158664L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(2041L, setOf("Fredrikstad FK"), "Noruega", "Fredrikstad", 155816L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(131491L, setOf("KFUM-Kameratene Oslo"), "Noruega", "KFUM", 155493L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(418L, setOf("Tromsø IL"), "Noruega", "Tromsø", 157255L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(920L, setOf("Vålerenga Fotball"), "Noruega", "Vålerenga", 155648L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(298L, setOf("Rosenborg BK"), "Noruega", "Rosenborg", 157083L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(1910L, setOf("NEC Nijmegen"), "Países Baixos", "N.E.C. Nijmegen", 124287L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(1906L, setOf("AZ Alkmaar"), "Países Baixos", "AZ", 121607L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(1971L, setOf("Excelsior"), "Países Baixos", "Excelsior Rotterdam", 124898L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(10020L, setOf("GD Estoril Praia"), "Portugal", "Estoril Praia", 115416L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(718L, setOf("Estrela da Amadora"), "Portugal", "Estrela Amadora", 116785L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(1896L, setOf("Sporting Clube de Braga"), "Portugal", "SC Braga", 119084L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(710L, setOf("Djurgårdens IF"), "Suécia", "Djurgården", 163519L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(708L, setOf("Hammarby Fotboll"), "Suécia", "Hammarby", 160477L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(101025L, setOf("Gençlerbirliği SK"), "Turquia", "Gençlerbirliği", 133808L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(326L, setOf("Fenerbahçe SK"), "Turquia", "Fenerbahçe", 133151L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(101014L, setOf("Medipol Başakşehir FK"), "Turquia", "İstanbul Başakşehir FK", 133903L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(327L, setOf("Beşiktaş JK"), "Turquia", "Beşiktaş", 133360L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(325L, setOf("Galatasaray SK"), "Turquia", "Galatasaray", 133729L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(111339L, setOf("Kasımpaşa SK"), "Turquia", "Kasımpaşa", 134751L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(101026L, setOf("Göztepe SK"), "Turquia", "Göztepe", 131924L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(254L, setOf("SK Rapid"), "Áustria", "SK Rapid Wien", 142359L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(252L, setOf("LASK Linz"), "Áustria", "LASK", 140300L, "FC26 source id + audited 2026/27 stable target variant"),
        ExplicitMapping(15009L, setOf("SC Rheindorf Altach"), "Áustria", "SCR Altach", 140821L, "FC26 source id + audited 2026/27 stable target variant"),

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

    private val bySourceId = explicitMappings.associateBy { it.sourceClubTeamId }.also { map ->
        require(map.size == explicitMappings.size) { "Duplicate FC26 source club override." }
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
        val mapping = bySourceId[source.sourceClubTeamId] ?: return null
        val normalizedSource = Fc26ClubMatcher.normalize(source.clubName)
        return mapping.takeIf { candidate ->
            candidate.acceptedSourceNames.any { Fc26ClubMatcher.normalize(it) == normalizedSource }
        }
    }

    fun allExplicitMappings(): List<ExplicitMapping> = explicitMappings.toList()
}
