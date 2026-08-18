package com.example.data

/**
 * Baseline doméstico europeu usado pela expansão 9.10B2.1A.
 *
 * Temporada factual de referência: 2026/27.
 * Data de verificação desta transcrição: 2026-08-18.
 *
 * `VERIFIED_TOP_FLIGHT` significa que a lista de participantes da primeira divisão foi conferida
 * em fonte oficial da própria liga para 2026/27. `STRUCTURE_ONLY` significa que a associação já
 * possui identidade/nome de competições e tamanho de primeira divisão, mas os clubes ainda não
 * foram transcritos para a base oficial desta fase. Esse estado explícito impede que fallback
 * procedural seja confundido com cobertura real completa.
 */
enum class EuropeanDomesticCoverage {
    VERIFIED_TOP_FLIGHT,
    STRUCTURE_ONLY
}

data class EuropeanDomesticAssociationBaseline(
    val country: String,
    val topDivisionName: String,
    val nationalCupName: String,
    val topDivisionClubCount: Int,
    val coverage: EuropeanDomesticCoverage,
    val verifiedTopFlightClubs: List<String> = emptyList()
) {
    init {
        require(topDivisionClubCount > 0)
        if (coverage == EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT) {
            require(verifiedTopFlightClubs.size == topDivisionClubCount) {
                "$country precisa ter exatamente $topDivisionClubCount clubes verificados."
            }
            require(verifiedTopFlightClubs.distinct().size == verifiedTopFlightClubs.size) {
                "$country contém clube duplicado no baseline 2026/27."
            }
        }
    }
}

object EuropeanDomesticBaseline2026_27 {
    const val SEASON = "2026/27"
    const val VERIFIED_AS_OF = "2026-08-18"

    private val englandClubs = listOf(
        "Arsenal FC",
        "Aston Villa",
        "AFC Bournemouth",
        "Brentford FC",
        "Brighton & Hove Albion",
        "Chelsea FC",
        "Coventry City",
        "Crystal Palace",
        "Everton FC",
        "Fulham FC",
        "Hull City",
        "Ipswich Town",
        "Leeds United",
        "Liverpool FC",
        "Manchester City",
        "Manchester United",
        "Newcastle United",
        "Nottingham Forest",
        "Sunderland AFC",
        "Tottenham Hotspur"
    )

    private val spainClubs = listOf(
        "Athletic Club",
        "Atlético de Madrid",
        "CA Osasuna",
        "Celta de Vigo",
        "Deportivo Alavés",
        "Elche CF",
        "FC Barcelona",
        "Getafe CF",
        "Levante UD",
        "Málaga CF",
        "Racing Santander",
        "Rayo Vallecano",
        "RC Deportivo",
        "RCD Espanyol de Barcelona",
        "Real Betis",
        "Real Madrid",
        "Real Sociedad",
        "Sevilla FC",
        "Valencia CF",
        "Villarreal CF"
    )

    private val italyClubs = listOf(
        "Atalanta",
        "Bologna",
        "Cagliari",
        "Como",
        "Fiorentina",
        "Frosinone",
        "Genoa",
        "Inter",
        "Juventus",
        "Lazio",
        "Lecce",
        "AC Milan",
        "Monza",
        "Napoli",
        "Parma",
        "Roma",
        "Sassuolo",
        "Torino",
        "Udinese",
        "Venezia"
    )

    private val germanyClubs = listOf(
        "FC Augsburg",
        "1. FC Union Berlin",
        "SV Werder Bremen",
        "Borussia Dortmund",
        "SV Elversberg",
        "Eintracht Frankfurt",
        "SC Freiburg",
        "Hamburger SV",
        "TSG Hoffenheim",
        "1. FC Köln",
        "RB Leipzig",
        "Bayer 04 Leverkusen",
        "1. FSV Mainz 05",
        "Borussia Mönchengladbach",
        "FC Bayern München",
        "SC Paderborn 07",
        "FC Schalke 04",
        "VfB Stuttgart"
    )

    private val franceClubs = listOf(
        "Angers SCO",
        "AJ Auxerre",
        "Stade Brestois 29",
        "Le Havre AC",
        "Le Mans FC",
        "RC Lens",
        "FC Lorient",
        "LOSC",
        "Olympique Lyonnais",
        "Olympique de Marseille",
        "AS Monaco",
        "OGC Nice",
        "Paris FC",
        "Paris Saint-Germain",
        "Stade Rennais FC",
        "RC Strasbourg Alsace",
        "Toulouse FC",
        "ESTAC Troyes"
    )

    val associations: List<EuropeanDomesticAssociationBaseline> = listOf(
        EuropeanDomesticAssociationBaseline(
            "Inglaterra", "Premier League", "FA Cup", 20,
            EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT, englandClubs
        ),
        EuropeanDomesticAssociationBaseline(
            "Espanha", "La Liga", "Copa del Rey", 20,
            EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT, spainClubs
        ),
        EuropeanDomesticAssociationBaseline(
            "Itália", "Serie A", "Coppa Italia", 20,
            EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT, italyClubs
        ),
        EuropeanDomesticAssociationBaseline(
            "Alemanha", "Bundesliga", "DFB-Pokal", 18,
            EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT, germanyClubs
        ),
        EuropeanDomesticAssociationBaseline(
            "França", "Ligue 1", "Coupe de France", 18,
            EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT, franceClubs
        ),
        EuropeanDomesticAssociationBaseline(
            "Portugal", "Primeira Liga", "Taça de Portugal", 18,
            EuropeanDomesticCoverage.STRUCTURE_ONLY
        ),
        EuropeanDomesticAssociationBaseline(
            "Países Baixos", "Eredivisie", "KNVB Beker", 18,
            EuropeanDomesticCoverage.STRUCTURE_ONLY
        ),
        EuropeanDomesticAssociationBaseline(
            "Bélgica", "Belgian Pro League", "Belgian Cup", 16,
            EuropeanDomesticCoverage.STRUCTURE_ONLY
        ),
        EuropeanDomesticAssociationBaseline(
            "Turquia", "Süper Lig", "Türkiye Kupası", 18,
            EuropeanDomesticCoverage.STRUCTURE_ONLY
        ),
        EuropeanDomesticAssociationBaseline(
            "Escócia", "Scottish Premiership", "Scottish Cup", 12,
            EuropeanDomesticCoverage.STRUCTURE_ONLY
        ),
        EuropeanDomesticAssociationBaseline(
            "Áustria", "Austrian Bundesliga", "ÖFB-Cup", 12,
            EuropeanDomesticCoverage.STRUCTURE_ONLY
        ),
        EuropeanDomesticAssociationBaseline(
            "Suíça", "Swiss Super League", "Swiss Cup", 12,
            EuropeanDomesticCoverage.STRUCTURE_ONLY
        ),
        EuropeanDomesticAssociationBaseline(
            "Dinamarca", "Danish Superliga", "DBU Pokalen", 12,
            EuropeanDomesticCoverage.STRUCTURE_ONLY
        ),
        EuropeanDomesticAssociationBaseline(
            "Noruega", "Eliteserien", "Norwegian Football Cup", 16,
            EuropeanDomesticCoverage.STRUCTURE_ONLY
        ),
        EuropeanDomesticAssociationBaseline(
            "Suécia", "Allsvenskan", "Svenska Cupen", 16,
            EuropeanDomesticCoverage.STRUCTURE_ONLY
        ),
        EuropeanDomesticAssociationBaseline(
            "Polônia", "Ekstraklasa", "Puchar Polski", 18,
            EuropeanDomesticCoverage.STRUCTURE_ONLY
        ),
        EuropeanDomesticAssociationBaseline(
            "Tchéquia", "Czech First League", "Czech Cup", 16,
            EuropeanDomesticCoverage.STRUCTURE_ONLY
        ),
        EuropeanDomesticAssociationBaseline(
            "Croácia", "Croatian Football League", "Croatian Football Cup", 10,
            EuropeanDomesticCoverage.STRUCTURE_ONLY
        ),
        EuropeanDomesticAssociationBaseline(
            "Sérvia", "Serbian SuperLiga", "Serbian Cup", 16,
            EuropeanDomesticCoverage.STRUCTURE_ONLY
        ),
        EuropeanDomesticAssociationBaseline(
            "Grécia", "Super League Greece", "Greek Cup", 14,
            EuropeanDomesticCoverage.STRUCTURE_ONLY
        )
    )

    private val byCountry = associations.associateBy { it.country }

    fun forCountry(country: String): EuropeanDomesticAssociationBaseline? =
        CountryFootballRulesRegistry.resolve(country)
            ?.canonicalCountry
            ?.let(byCountry::get)

    val verifiedTopFlightCountries: Set<String>
        get() = associations
            .filter { it.coverage == EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT }
            .mapTo(linkedSetOf()) { it.country }
}
