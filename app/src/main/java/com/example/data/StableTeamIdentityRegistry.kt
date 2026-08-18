package com.example.data

/**
 * Congela IDs de clubes reais cuja posição de divisão/lista pode mudar entre temporadas-base.
 *
 * O sistema legado calculava o ID por `countryIndex * 200 + teamIndex + 1`. Isso torna promoção,
 * rebaixamento e simples reordenação de `DefaultData` capazes de trocar a identidade de um clube.
 * Este registry preserva os IDs já conhecidos dos clubes existentes e reserva slots do mesmo bloco
 * legado para novos clubes reais, sem alterar o schema Room.
 *
 * Nesta primeira fatia somente Inglaterra/Espanha são integradas ao seed real 2026/27. Os demais
 * países receberão IDs explícitos quando suas listas oficiais forem transcritas para DefaultData.
 */
data class StableTeamIdentity(
    val country: String,
    val canonicalName: String,
    val id: Long,
    val aliases: Set<String> = emptySet()
)

object StableTeamIdentityRegistry {
    private val identities: List<StableTeamIdentity> = listOf(
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

    private fun normalize(value: String): String = value.trim().lowercase()

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
        require(map.size == identities.size) { "Há IDs estáveis de clubes duplicados." }
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
