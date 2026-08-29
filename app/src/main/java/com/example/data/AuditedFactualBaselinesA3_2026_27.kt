package com.example.data

/**
 * Phase 9.11A3 factual lower-tier identities for the audited club source 2025-09-19 snapshot.
 *
 * Every record is keyed by immutable audited club source `club_team_id`. Membership in Ligue 2 BKT 2026/27 is
 * taken from the official LFP match calendar published on 2026-06-10; text similarity is never used
 * as identity evidence. Only identity/name/country/division are factual here. Slot city/stadium/rating
 * remain internal game metadata when materialized.
 */
object AuditedFactualBaselinesA3_2026_27 {
    const val AUDITED_SOURCE_VERSION = "2025-09-19"
    const val VERIFIED_AS_OF = "2026-08-19"
    const val LFP_LIGUE_2_2026_27 =
        "LFP / Ligue1.com — Saison 26/27 : Le calendrier des matchs de Ligue 2 BKT ! — published 2026-06-10"

    data class FactualTarget(
        val sourceClubTeamId: Long,
        val sourceName: String,
        val country: String,
        val canonicalName: String,
        val division: Int,
        val competitionName: String,
        val verificationBasis: String
    )

    val factualTargets: List<FactualTarget> = listOf(
        target(115494L, "FC Annecy", "FC Annecy"),
        target(1815L, "Clermont Foot 63", "Clermont Foot 63"),
        target(111659L, "Rodez Aveyron Football", "Rodez AF"),
        target(379L, "Stade de Reims", "Stade de Reims"),
        target(111273L, "Red Star FC", "Red Star FC"),
        target(111376L, "US Boulogne Cote d'Opale", "US Boulogne CO"),
        target(1819L, "AS Saint-Étienne", "AS Saint-Étienne"),
        target(68L, "FC Metz", "FC Metz"),
        target(71L, "FC Nantes", "FC Nantes"),
        target(70L, "Montpellier HSC", "Montpellier HSC"),
        target(62L, "En Avant Guingamp", "En Avant Guingamp"),
        target(1823L, "AS Nancy Lorraine", "AS Nancy Lorraine"),
        target(110321L, "Pau FC", "Pau FC"),
        target(1814L, "Stade Lavallois Mayenne FC", "Stade Lavallois"),
        target(111276L, "USL Dunkerque", "USL Dunkerque")
    )

    init {
        require(factualTargets.size == 15)
        require(factualTargets.map { it.sourceClubTeamId }.distinct().size == factualTargets.size)
        require(factualTargets.all { it.country == "França" && it.division == 2 })
        require(factualTargets.map { it.canonicalName }.distinct().size == factualTargets.size)
    }

    fun forCountry(country: String): List<FactualTarget> =
        factualTargets.filter { it.country == country }

    private fun target(
        sourceClubTeamId: Long,
        sourceName: String,
        canonicalName: String
    ) = FactualTarget(
        sourceClubTeamId = sourceClubTeamId,
        sourceName = sourceName,
        country = "França",
        canonicalName = canonicalName,
        division = 2,
        competitionName = "Ligue 2 BKT",
        verificationBasis = LFP_LIGUE_2_2026_27
    )
}
