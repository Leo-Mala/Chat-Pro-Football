package com.example.data

/**
 * Audited Phase 9.11A2 identities for the audited club source 2025-09-19 snapshot.
 *
 * Every entry is keyed by the immutable audited club source `club_team_id`. Text similarity is not evidence and
 * is never consulted here. Existing-target variants point only to already-materialized stable
 * identities. Lower-tier targets are included only after 2026/27 membership was verified from the
 * competition organizer/federation source documented in [verificationBasis].
 */
object AuditedLowerTierClubCoverage2026_27 {
    const val AUDITED_SOURCE_VERSION = "2025-09-19"
    const val VERIFIED_AS_OF = "2026-08-19"

    data class ExistingTargetNameVariant(
        val sourceClubTeamId: Long,
        val sourceName: String,
        val country: String,
        val targetCanonicalName: String
    )

    data class LowerTierFactualTarget(
        val sourceClubTeamId: Long,
        val sourceName: String,
        val country: String,
        val canonicalName: String,
        val division: Int,
        val competitionName: String,
        val verificationBasis: String
    )

    val existingTargetNameVariants: List<ExistingTargetNameVariant> = listOf(
        ExistingTargetNameVariant(10029L, "TSG 1899 Hoffenheim", "Alemanha", "TSG Hoffenheim"),
        ExistingTargetNameVariant(231L, "Club Brugge KV", "Bélgica", "Club Brugge"),
        ExistingTargetNameVariant(100087L, "Oud-Heverlee Leuven", "Bélgica", "OH Leuven"),
        ExistingTargetNameVariant(1750L, "Cercle Brugge KSV", "Bélgica", "Cercle Brugge"),
        ExistingTargetNameVariant(2014L, "Union Saint-Gilloise", "Bélgica", "Royale Union Saint-Gilloise"),
        ExistingTargetNameVariant(670L, "Royal Charleroi Sporting Club", "Bélgica", "Sporting Charleroi"),
        ExistingTargetNameVariant(680L, "Sint-Truidense VV", "Bélgica", "STVV"),
        ExistingTargetNameVariant(272L, "Odense Boldklub", "Dinamarca", "OB"),
        ExistingTargetNameVariant(271L, "Aarhus Gymnastikforening", "Dinamarca", "AGF"),
        ExistingTargetNameVariant(80L, "Hearts", "Escócia", "Heart of Midlothian"),
        ExistingTargetNameVariant(65L, "Lille OSC", "França", "LOSC"),
        ExistingTargetNameVariant(278L, "AEK Athens", "Grécia", "AEK"),
        ExistingTargetNameVariant(112199L, "Sarpsborg 08 FF", "Noruega", "Sarpsborg 08"),
        ExistingTargetNameVariant(1756L, "Hamarkameratene", "Noruega", "HamKam"),
        ExistingTargetNameVariant(113459L, "Kristiansund BK", "Noruega", "Kristiansund"),
        ExistingTargetNameVariant(417L, "Molde FK", "Noruega", "Molde"),
        ExistingTargetNameVariant(300L, "Viking FK", "Noruega", "Viking"),
        ExistingTargetNameVariant(918L, "FK Bodø/Glimt", "Noruega", "Bodø/Glimt"),
        ExistingTargetNameVariant(919L, "SK Brann", "Noruega", "Brann"),
        ExistingTargetNameVariant(2041L, "Fredrikstad FK", "Noruega", "Fredrikstad"),
        ExistingTargetNameVariant(131491L, "KFUM-Kameratene Oslo", "Noruega", "KFUM"),
        ExistingTargetNameVariant(418L, "Tromsø IL", "Noruega", "Tromsø"),
        ExistingTargetNameVariant(920L, "Vålerenga Fotball", "Noruega", "Vålerenga"),
        ExistingTargetNameVariant(298L, "Rosenborg BK", "Noruega", "Rosenborg"),
        ExistingTargetNameVariant(1910L, "NEC Nijmegen", "Países Baixos", "N.E.C. Nijmegen"),
        ExistingTargetNameVariant(1906L, "AZ Alkmaar", "Países Baixos", "AZ"),
        ExistingTargetNameVariant(1971L, "Excelsior", "Países Baixos", "Excelsior Rotterdam"),
        ExistingTargetNameVariant(10020L, "GD Estoril Praia", "Portugal", "Estoril Praia"),
        ExistingTargetNameVariant(718L, "Estrela da Amadora", "Portugal", "Estrela Amadora"),
        ExistingTargetNameVariant(1896L, "Sporting Clube de Braga", "Portugal", "SC Braga"),
        ExistingTargetNameVariant(710L, "Djurgårdens IF", "Suécia", "Djurgården"),
        ExistingTargetNameVariant(708L, "Hammarby Fotboll", "Suécia", "Hammarby"),
        ExistingTargetNameVariant(101025L, "Gençlerbirliği SK", "Turquia", "Gençlerbirliği"),
        ExistingTargetNameVariant(326L, "Fenerbahçe SK", "Turquia", "Fenerbahçe"),
        ExistingTargetNameVariant(101014L, "Medipol Başakşehir FK", "Turquia", "İstanbul Başakşehir FK"),
        ExistingTargetNameVariant(327L, "Beşiktaş JK", "Turquia", "Beşiktaş"),
        ExistingTargetNameVariant(325L, "Galatasaray SK", "Turquia", "Galatasaray"),
        ExistingTargetNameVariant(111339L, "Kasımpaşa SK", "Turquia", "Kasımpaşa"),
        ExistingTargetNameVariant(101026L, "Göztepe SK", "Turquia", "Göztepe"),
        ExistingTargetNameVariant(254L, "SK Rapid", "Áustria", "SK Rapid Wien"),
        ExistingTargetNameVariant(252L, "LASK Linz", "Áustria", "LASK"),
        ExistingTargetNameVariant(15009L, "SC Rheindorf Altach", "Áustria", "SCR Altach")
    )

    private const val DFL_2_BUNDESLIGA_2026_27 =
        "DFL Bundesliga 2 official clubs/standings, season 2026-2027"
    private const val DFB_3_LIGA_2026_27 =
        "DFB 3. Liga official 2026/27 participant field and Datencenter fixtures"
    private const val LEGA_B_2026_27 =
        "Lega B official Serie BKT 2026/2027 20-club field"

    val lowerTierFactualTargets: List<LowerTierFactualTarget> = listOf(
        // Germany — Bundesliga 2, 2026/27.
        lower(111235L, "1. FC Heidenheim 1846", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(29L, "1. FC Kaiserslautern", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(110588L, "1. FC Magdeburg", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(171L, "1. FC Nürnberg", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(159L, "DSC Arminia Bielefeld", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(503L, "Dynamo Dresden", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(110500L, "Eintracht Braunschweig", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(162L, "FC Energie Cottbus", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(110329L, "FC St. Pauli", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(485L, "Hannover 96", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(166L, "Hertha BSC", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(576L, "Holstein Kiel", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(1832L, "Karlsruher SC", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(110502L, "SV Darmstadt 98", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(165L, "SpVgg Greuther Fürth", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(160L, "VfL Bochum 1848", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(487L, "VfL Osnabrück", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),
        lower(175L, "VfL Wolfsburg", "Alemanha", 2, "2. Bundesliga", DFL_2_BUNDESLIGA_2026_27),

        // Germany — 3. Liga, 2026/27. Only audited club source clubs present in the official 20-club field.
        lower(523L, "1. FC Saarbrücken", "Alemanha", 3, "3. Liga", DFB_3_LIGA_2026_27),
        lower(1826L, "Alemannia Aachen", "Alemanha", 3, "3. Liga", DFB_3_LIGA_2026_27),
        lower(27L, "FC Hansa Rostock", "Alemanha", 3, "3. Liga", DFB_3_LIGA_2026_27),
        lower(111239L, "FC Ingolstadt 04", "Alemanha", 3, "3. Liga", DFB_3_LIGA_2026_27),
        lower(110636L, "Fortuna Düsseldorf", "Alemanha", 3, "3. Liga", DFB_3_LIGA_2026_27),
        lower(1825L, "MSV Duisburg", "Alemanha", 3, "3. Liga", DFB_3_LIGA_2026_27),
        lower(526L, "Rot-Weiss Essen", "Alemanha", 3, "3. Liga", DFB_3_LIGA_2026_27),
        lower(531L, "SC Preußen Münster", "Alemanha", 3, "3. Liga", DFB_3_LIGA_2026_27),
        lower(110501L, "SC Verl", "Alemanha", 3, "3. Liga", DFB_3_LIGA_2026_27),
        lower(543L, "SSV Jahn Regensburg", "Alemanha", 3, "3. Liga", DFB_3_LIGA_2026_27),
        lower(110532L, "SV Waldhof Mannheim", "Alemanha", 3, "3. Liga", DFB_3_LIGA_2026_27),
        lower(492L, "SV Wehen Wiesbaden", "Alemanha", 3, "3. Liga", DFB_3_LIGA_2026_27),
        lower(110685L, "TSG 1899 Hoffenheim II", "Alemanha", 3, "3. Liga", DFB_3_LIGA_2026_27),
        lower(110678L, "TSV Havelse", "Alemanha", 3, "3. Liga", DFB_3_LIGA_2026_27),
        lower(110697L, "VfB Stuttgart II", "Alemanha", 3, "3. Liga", DFB_3_LIGA_2026_27),
        lower(110645L, "Viktoria Köln", "Alemanha", 3, "3. Liga", DFB_3_LIGA_2026_27),

        // Italy — Serie BKT, 2026/27. Only audited club source clubs present in Lega B's official 20-club field.
        lower(2038L, "Avellino", "Itália", 2, "Serie BKT", LEGA_B_2026_27),
        lower(112493L, "Carrarese", "Itália", 2, "Serie BKT", LEGA_B_2026_27),
        lower(110908L, "Catanzaro", "Itália", 2, "Serie BKT", LEGA_B_2026_27),
        lower(110915L, "Cesena FC", "Itália", 2, "Serie BKT", LEGA_B_2026_27),
        lower(111434L, "Cremonese", "Itália", 2, "Serie BKT", LEGA_B_2026_27),
        lower(206L, "Hellas Verona FC", "Itália", 2, "Serie BKT", LEGA_B_2026_27),
        lower(111433L, "Mantova", "Itália", 2, "Serie BKT", LEGA_B_2026_27),
        lower(1744L, "Modena", "Itália", 2, "Serie BKT", LEGA_B_2026_27),
        lower(110738L, "Pisa", "Itália", 2, "Serie BKT", LEGA_B_2026_27),
        lower(112124L, "SS Juve Stabia", "Itália", 2, "Serie BKT", LEGA_B_2026_27),
        lower(1837L, "Sampdoria", "Itália", 2, "Serie BKT", LEGA_B_2026_27),
        lower(112494L, "Südtirol", "Itália", 2, "Serie BKT", LEGA_B_2026_27),
        lower(113147L, "Virtus Entella", "Itália", 2, "Serie BKT", LEGA_B_2026_27)
    )

    init {
        val ids = existingTargetNameVariants.map { it.sourceClubTeamId } +
            lowerTierFactualTargets.map { it.sourceClubTeamId }
        require(ids.size == ids.distinct().size) { "Phase 9.11A2 contains duplicate audited club source club_team_id entries." }
        require(existingTargetNameVariants.size == 42)
        require(lowerTierFactualTargets.size == 47)
        require(lowerTierFactualTargets.all { it.division > 1 })
    }

    fun lowerTierForCountry(country: String): List<LowerTierFactualTarget> =
        lowerTierFactualTargets.filter { it.country == country }

    private fun lower(
        sourceClubTeamId: Long,
        sourceName: String,
        country: String,
        division: Int,
        competitionName: String,
        verificationBasis: String
    ) = LowerTierFactualTarget(
        sourceClubTeamId = sourceClubTeamId,
        sourceName = sourceName,
        country = country,
        canonicalName = sourceName,
        division = division,
        competitionName = competitionName,
        verificationBasis = verificationBasis
    )
}
