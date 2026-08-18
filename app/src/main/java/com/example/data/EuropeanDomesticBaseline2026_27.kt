package com.example.data

/**
 * Baseline doméstico europeu usado pela expansão 9.10B2.1A.
 *
 * Janela continental de referência: 2026/27.
 * Data de verificação desta transcrição: 2026-08-18.
 *
 * Algumas associações UEFA usam temporada por ano civil. Por isso [domesticSeasonLabel] pertence à
 * associação e não deve ser inferido da temporada UEFA. `VERIFIED_TOP_FLIGHT` significa que a lista
 * de participantes da primeira divisão foi conferida em fonte oficial/primária; `STRUCTURE_ONLY`
 * deixa explícito que a associação ainda não possui lista factual fechada nesta fatia.
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
    val verifiedTopFlightClubs: List<String> = emptyList(),
    val domesticSeasonLabel: String = EuropeanDomesticBaseline2026_27.UEFA_SEASON
) {
    init {
        require(topDivisionClubCount > 0)
        require(domesticSeasonLabel.isNotBlank())
        if (coverage == EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT) {
            require(verifiedTopFlightClubs.size == topDivisionClubCount) {
                "$country precisa ter exatamente $topDivisionClubCount clubes verificados."
            }
            require(verifiedTopFlightClubs.distinct().size == verifiedTopFlightClubs.size) {
                "$country contém clube duplicado no baseline $domesticSeasonLabel."
            }
        }
    }
}

object EuropeanDomesticBaseline2026_27 {
    const val UEFA_SEASON = "2026/27"
    const val VERIFIED_AS_OF = "2026-08-18"

    private val englandClubs = listOf(
        "Arsenal FC", "Aston Villa", "AFC Bournemouth", "Brentford FC",
        "Brighton & Hove Albion", "Chelsea FC", "Coventry City", "Crystal Palace",
        "Everton FC", "Fulham FC", "Hull City", "Ipswich Town", "Leeds United",
        "Liverpool FC", "Manchester City", "Manchester United", "Newcastle United",
        "Nottingham Forest", "Sunderland AFC", "Tottenham Hotspur"
    )

    private val spainClubs = listOf(
        "Athletic Club", "Atlético de Madrid", "CA Osasuna", "Celta de Vigo",
        "Deportivo Alavés", "Elche CF", "FC Barcelona", "Getafe CF", "Levante UD",
        "Málaga CF", "Racing Santander", "Rayo Vallecano", "RC Deportivo",
        "RCD Espanyol de Barcelona", "Real Betis", "Real Madrid", "Real Sociedad",
        "Sevilla FC", "Valencia CF", "Villarreal CF"
    )

    private val italyClubs = listOf(
        "Atalanta", "Bologna", "Cagliari", "Como", "Fiorentina", "Frosinone", "Genoa",
        "Inter", "Juventus", "Lazio", "Lecce", "AC Milan", "Monza", "Napoli", "Parma",
        "Roma", "Sassuolo", "Torino", "Udinese", "Venezia"
    )

    private val germanyClubs = listOf(
        "FC Augsburg", "1. FC Union Berlin", "SV Werder Bremen", "Borussia Dortmund",
        "SV Elversberg", "Eintracht Frankfurt", "SC Freiburg", "Hamburger SV",
        "TSG Hoffenheim", "1. FC Köln", "RB Leipzig", "Bayer 04 Leverkusen",
        "1. FSV Mainz 05", "Borussia Mönchengladbach", "FC Bayern München",
        "SC Paderborn 07", "FC Schalke 04", "VfB Stuttgart"
    )

    private val franceClubs = listOf(
        "Angers SCO", "AJ Auxerre", "Stade Brestois 29", "Le Havre AC", "Le Mans FC",
        "RC Lens", "FC Lorient", "LOSC", "Olympique Lyonnais", "Olympique de Marseille",
        "AS Monaco", "OGC Nice", "Paris FC", "Paris Saint-Germain", "Stade Rennais FC",
        "RC Strasbourg Alsace", "Toulouse FC", "ESTAC Troyes"
    )

    private val netherlandsClubs = listOf(
        "ADO Den Haag", "Ajax", "AZ", "Excelsior Rotterdam", "FC Groningen", "FC Twente",
        "FC Utrecht", "Feyenoord", "Fortuna Sittard", "Go Ahead Eagles", "N.E.C. Nijmegen",
        "PEC Zwolle", "PSV", "SC Cambuur", "sc Heerenveen", "Sparta Rotterdam", "Telstar",
        "Willem II"
    )

    private val belgiumClubs = listOf(
        "Club Brugge", "KV Kortrijk", "STVV", "Lommel SK", "Royal Antwerp FC", "SK Beveren",
        "Royale Union Saint-Gilloise", "KVC Westerlo", "Standard de Liège", "Cercle Brugge",
        "SV Zulte Waregem", "KRC Genk", "RSC Anderlecht", "RAAL La Louvière",
        "Sporting Charleroi", "OH Leuven", "KAA Gent", "KV Mechelen"
    )

    private val turkeyClubs = listOf(
        "Gençlerbirliği", "Fenerbahçe", "Kasımpaşa", "Trabzonspor", "Beşiktaş", "Eyüpspor",
        "Galatasaray", "Çorum FK", "Amed Sportif Faaliyetler", "Erzurumspor FK", "Konyaspor",
        "Çaykur Rizespor", "Samsunspor", "Göztepe", "Gaziantep FK", "Alanyaspor",
        "İstanbul Başakşehir FK", "Kocaelispor"
    )

    private val scotlandClubs = listOf(
        "Aberdeen", "Celtic", "Dundee", "Dundee United", "Falkirk", "Heart of Midlothian",
        "Hibernian", "Kilmarnock", "Motherwell", "Rangers", "St Johnstone", "St Mirren"
    )

    private val austriaClubs = listOf(
        "LASK", "Grazer AK 1902", "WSG Tirol", "SK Sturm Graz", "FC Red Bull Salzburg",
        "TSV Hartberg", "Wolfsberger AC", "FK Austria Wien", "SC Austria Lustenau", "SV Ried",
        "SK Rapid Wien", "SCR Altach"
    )

    private val switzerlandClubs = listOf(
        "FC Lausanne-Sport", "Grasshopper Club Zürich", "Servette FC", "FC Basel 1893",
        "FC Luzern", "FC Thun", "BSC Young Boys", "FC Sion", "FC Lugano", "FC Vaduz",
        "FC St.Gallen 1879", "FC Zürich"
    )

    private val denmarkClubs = listOf(
        "Viborg FF", "OB", "AGF", "Brøndby IF", "Sønderjyske Fodbold", "FC Midtjylland",
        "Randers FC", "Silkeborg IF", "AC Horsens", "FC Nordsjælland", "FC København",
        "Lyngby BK"
    )

    private val norwayClubs = listOf(
        "Vålerenga", "HamKam", "Bodø/Glimt", "Lillestrøm", "Fredrikstad",
        "Sandefjord Fotball", "Start", "Viking", "KFUM", "Kristiansund", "Molde",
        "Sarpsborg 08", "Aalesund", "Tromsø", "Brann", "Rosenborg"
    )

    private val swedenClubs = listOf(
        "AIK", "Halmstads BK", "Hammarby", "Mjällby AIF", "GAIS", "Djurgården",
        "Örgryte IS", "Malmö FF", "BK Häcken", "IF Brommapojkarna", "IF Elfsborg",
        "IFK Göteborg", "Degerfors IF", "IK Sirius", "Kalmar FF", "Västerås SK"
    )

    private val polandClubs = listOf(
        "Zagłębie Lubin", "Wisła Płock", "Wisła Kraków", "Górnik Zabrze", "Radomiak Radom",
        "Legia Warszawa", "Jagiellonia Białystok", "Motor Lublin", "Widzew Łódź", "Lech Poznań",
        "Cracovia", "Raków Częstochowa", "Śląsk Wrocław", "GKS Katowice", "Wieczysta Kraków",
        "Korona Kielce", "Pogoń Szczecin", "Piast Gliwice"
    )

    private val czechiaClubs = listOf(
        "Dukla Praha", "FC Hradec Králové", "FC Zlín", "FC Slovan Liberec",
        "FK Mladá Boleslav", "MFK Karviná", "FK Jablonec", "SK Slavia Praha",
        "FC Baník Ostrava", "FC Viktoria Plzeň", "AC Sparta Praha", "SK Sigma Olomouc",
        "FK Pardubice", "1. FC Slovácko", "FK Teplice", "Bohemians Praha 1905"
    )

    private val croatiaClubs = listOf(
        "Dinamo Zagreb", "Gorica", "Hajduk Split", "Rijeka", "Istra 1961",
        "Lokomotiva Zagreb", "Osijek", "Rudeš", "Slaven Belupo", "Varaždin"
    )

    private val serbiaClubs = listOf(
        "IMT", "Crvena zvezda", "Čukarički", "Partizan", "OFK Beograd", "Novi Pazar",
        "Vojvodina", "Železničar Pančevo", "Radnički Niš", "Mladost Lučani",
        "Radnik Surdulica", "Zemun", "Radnički 1923", "Mačva Šabac"
    )

    val associations: List<EuropeanDomesticAssociationBaseline> = listOf(
        verified("Inglaterra", "Premier League", "FA Cup", 20, englandClubs),
        verified("Espanha", "La Liga", "Copa del Rey", 20, spainClubs),
        verified("Itália", "Serie A", "Coppa Italia", 20, italyClubs),
        verified("Alemanha", "Bundesliga", "DFB-Pokal", 18, germanyClubs),
        verified("França", "Ligue 1", "Coupe de France", 18, franceClubs),
        structureOnly("Portugal", "Primeira Liga", "Taça de Portugal", 18),
        verified("Países Baixos", "Eredivisie", "KNVB Beker", 18, netherlandsClubs),
        verified("Bélgica", "Jupiler Pro League", "Croky Cup", 18, belgiumClubs),
        verified("Turquia", "Süper Lig", "Türkiye Kupası", 18, turkeyClubs),
        verified("Escócia", "Scottish Premiership", "Scottish Cup", 12, scotlandClubs),
        verified("Áustria", "ADMIRAL Bundesliga", "UNIQA ÖFB-Cup", 12, austriaClubs),
        verified("Suíça", "Brack Super League", "Swiss Cup", 12, switzerlandClubs),
        verified("Dinamarca", "3F Superliga", "DBU Pokalen", 12, denmarkClubs),
        verified("Noruega", "Eliteserien", "Norwegian Football Cup", 16, norwayClubs, "2026"),
        verified("Suécia", "Allsvenskan", "Svenska Cupen", 16, swedenClubs, "2026"),
        verified("Polônia", "Ekstraklasa", "Puchar Polski", 18, polandClubs),
        verified("Tchéquia", "Chance Liga", "MOL Cup", 16, czechiaClubs),
        verified("Croácia", "SuperSport HNL", "Croatian Football Cup", 10, croatiaClubs),
        verified("Sérvia", "Mozzart Bet SuperLiga", "Serbian Cup", 14, serbiaClubs),
        structureOnly("Grécia", "Super League Greece", "Greek Cup", 14)
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

    private fun verified(
        country: String,
        league: String,
        cup: String,
        count: Int,
        clubs: List<String>,
        domesticSeasonLabel: String = UEFA_SEASON
    ) = EuropeanDomesticAssociationBaseline(
        country = country,
        topDivisionName = league,
        nationalCupName = cup,
        topDivisionClubCount = count,
        coverage = EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT,
        verifiedTopFlightClubs = clubs,
        domesticSeasonLabel = domesticSeasonLabel
    )

    private fun structureOnly(
        country: String,
        league: String,
        cup: String,
        count: Int,
        domesticSeasonLabel: String = UEFA_SEASON
    ) = EuropeanDomesticAssociationBaseline(
        country = country,
        topDivisionName = league,
        nationalCupName = cup,
        topDivisionClubCount = count,
        coverage = EuropeanDomesticCoverage.STRUCTURE_ONLY,
        domesticSeasonLabel = domesticSeasonLabel
    )
}
