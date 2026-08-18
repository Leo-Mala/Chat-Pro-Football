package com.example.data

/**
 * Templates factuais que ainda não pertencem ao `DefaultData.originalMap` legado.
 *
 * O catálogo cresce país a país e é deliberadamente separado do seed procedural. Inserir um clube
 * aqui NÃO o torna automaticamente `READY`: o readiness gate ainda exige que o resolvedor global
 * devolva o mesmo ID do `StableTeamIdentityRegistry`.
 *
 * `rating` permanece atributo interno do Pro Football. Cidade, estádio, temporada e fontes são
 * dados de proveniência factual do snapshot.
 */
data class EuropeanAdditionalClubTemplateRecord(
    val country: String,
    val domesticSeasonLabel: String,
    val verifiedAsOfIso: String,
    val sourceRefs: List<String>,
    val template: DefaultData.TeamTemplate
) {
    init {
        val baseline = requireNotNull(EuropeanDomesticBaseline2026_27.forCountry(country)) {
            "Associação UEFA fora do baseline: $country"
        }
        require(baseline.coverage == EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT)
        require(domesticSeasonLabel == baseline.domesticSeasonLabel)
        require(template.division == 1) {
            "Template adicional factual deve representar a primeira divisão: $country/${template.name}"
        }
        require(baseline.verifiedTopFlightClubs.any { it.equals(template.name, ignoreCase = true) }) {
            "Clube adicional fora do baseline factual: $country/${template.name}"
        }
        requireNotNull(StableTeamIdentityRegistry.idFor(country, template.name)) {
            "Clube adicional sem identidade estável: $country/${template.name}"
        }
        require(template.city.isNotBlank())
        require(template.state.isNotBlank())
        require(template.stadium.isNotBlank())
        require(template.rating in 1..100)
        parseStrictIsoDate(verifiedAsOfIso, "verifiedAsOfIso")
        require(sourceRefs.isNotEmpty() && sourceRefs.none { it.isBlank() })
    }

    val stableTeamId: Long
        get() = requireNotNull(StableTeamIdentityRegistry.idFor(country, template.name))
}

object EuropeanAdditionalClubTemplates2026_27 {
    private val records: List<EuropeanAdditionalClubTemplateRecord> = listOf(
        EuropeanAdditionalClubTemplateRecord(
            country = "Turquia",
            domesticSeasonLabel = "2026/27",
            verifiedAsOfIso = "2026-08-18",
            sourceRefs = listOf(
                "https://www.trabzonspor.org.tr/en/club/stadium",
                "https://www.trabzonspor.org.tr/tr/haberler/2026-2027-sezonu-kombinelerimiz-satisa-cikti-24-04-2026"
            ),
            template = DefaultData.TeamTemplate(
                name = "Trabzonspor",
                city = "Trabzon",
                state = "TRA",
                division = 1,
                rating = 78,
                stadium = "Papara Park"
            )
        )
    )

    private fun key(country: String, clubName: String): Pair<String, String> {
        val canonicalCountry = CountryFootballRulesRegistry.resolve(country)?.canonicalCountry ?: country
        val canonicalName = StableTeamIdentityRegistry.canonicalNameFor(canonicalCountry, clubName) ?: clubName
        return canonicalCountry.trim().lowercase() to canonicalName.trim().lowercase()
    }

    private val byClub: Map<Pair<String, String>, EuropeanAdditionalClubTemplateRecord> =
        records.associateBy { key(it.country, it.template.name) }.also { map ->
            require(map.size == records.size) { "Template factual adicional duplicado." }
            require(records.map { it.stableTeamId }.distinct().size == records.size) {
                "Templates factuais adicionais possuem teamId duplicado."
            }
        }

    fun find(country: String, clubName: String): EuropeanAdditionalClubTemplateRecord? =
        byClub[key(country, clubName)]

    fun all(): List<EuropeanAdditionalClubTemplateRecord> = records.toList()
}
