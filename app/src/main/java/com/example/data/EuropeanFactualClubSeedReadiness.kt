package com.example.data

/**
 * Gate entre o catálogo factual de clubes e o seed legado.
 *
 * Ter um `StableTeamIdentityRegistry` não basta para tornar um clube jogável como entidade factual:
 * o clube também precisa existir como template explícito, estar na primeira divisão atual, possuir
 * metadados mínimos e resolver exatamente para o seu teamId canônico.
 *
 * O gate é somente leitura. Ele não altera `DefaultData`, não inventa metadados e não promove
 * placeholders procedurais para identidades reais por coincidência de nome.
 */
enum class EuropeanFactualClubSeedStatus {
    READY,
    MISSING_EXPLICIT_TEMPLATE,
    NON_TOP_FLIGHT_TEMPLATE,
    INVALID_TEMPLATE_METADATA,
    GLOBAL_ID_MISMATCH
}

data class EuropeanFactualClubSeedAssessment(
    val country: String,
    val clubName: String,
    val stableTeamId: Long,
    val status: EuropeanFactualClubSeedStatus,
    val template: DefaultData.TeamTemplate? = null,
    val resolvedGlobalId: Long? = null
)

object EuropeanFactualClubSeedReadiness {

    fun assessAll(): List<EuropeanFactualClubSeedAssessment> =
        EuropeanDomesticBaseline2026_27.associations.flatMap { baseline ->
            baseline.verifiedTopFlightClubs.map { club -> assess(baseline.country, club) }
        }

    fun assess(country: String, clubName: String): EuropeanFactualClubSeedAssessment {
        val stableId = requireNotNull(StableTeamIdentityRegistry.idFor(country, clubName)) {
            "Clube factual sem identidade estável: $country/$clubName"
        }
        val template = DefaultData.originalMap[country]
            ?.teams
            ?.firstOrNull { it.name.equals(clubName, ignoreCase = true) }

        if (template == null) {
            return EuropeanFactualClubSeedAssessment(
                country = country,
                clubName = clubName,
                stableTeamId = stableId,
                status = EuropeanFactualClubSeedStatus.MISSING_EXPLICIT_TEMPLATE
            )
        }

        if (template.division != 1) {
            return EuropeanFactualClubSeedAssessment(
                country = country,
                clubName = clubName,
                stableTeamId = stableId,
                status = EuropeanFactualClubSeedStatus.NON_TOP_FLIGHT_TEMPLATE,
                template = template
            )
        }

        if (
            template.name.isBlank() ||
            template.city.isBlank() ||
            template.state.isBlank() ||
            template.stadium.isBlank() ||
            template.rating !in 1..100
        ) {
            return EuropeanFactualClubSeedAssessment(
                country = country,
                clubName = clubName,
                stableTeamId = stableId,
                status = EuropeanFactualClubSeedStatus.INVALID_TEMPLATE_METADATA,
                template = template
            )
        }

        val resolvedGlobalId = GlobalFootballSystem.getGlobalId(country, template.name)
        val status = if (resolvedGlobalId == stableId) {
            EuropeanFactualClubSeedStatus.READY
        } else {
            EuropeanFactualClubSeedStatus.GLOBAL_ID_MISMATCH
        }

        return EuropeanFactualClubSeedAssessment(
            country = country,
            clubName = clubName,
            stableTeamId = stableId,
            status = status,
            template = template,
            resolvedGlobalId = resolvedGlobalId
        )
    }

    fun readyAssessments(): List<EuropeanFactualClubSeedAssessment> =
        assessAll().filter { it.status == EuropeanFactualClubSeedStatus.READY }

    fun notReadyAssessments(): List<EuropeanFactualClubSeedAssessment> =
        assessAll().filterNot { it.status == EuropeanFactualClubSeedStatus.READY }

    fun readyTopFlightTeams(): List<Team> = readyAssessments().map { assessment ->
        val template = requireNotNull(assessment.template)
        Team(
            id = assessment.stableTeamId,
            name = template.name,
            city = template.city,
            state = template.state,
            country = assessment.country,
            division = template.division,
            rating = template.rating,
            stadiumName = template.stadium,
            logoUrl = DefaultData.getLogoForTeam(template.name, assessment.country),
            isPlayerControlled = false
        )
    }
}
