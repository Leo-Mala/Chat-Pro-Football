package com.example.data

/**
 * Materializes the organizer-verified Phase 9.11A3 Ligue 2 BKT identities in-place.
 *
 * Identity/name/division are factual. City/stadium/rating are inherited internal slot metadata and
 * are not represented as factual provenance. No team is appended and no division size is changed.
 */
object EuropeanAuditedFactualBaselinesA3Materializer2026_27 {
    enum class MetadataOrigin {
        EXISTING_EXACT_SLOT,
        INTERNAL_SLOT_METADATA
    }

    data class MaterializedTarget(
        val sourceClubTeamId: Long,
        val country: String,
        val stableTeamId: Long,
        val canonicalName: String,
        val division: Int,
        val competitionName: String,
        val slotIndex: Int,
        val template: DefaultData.TeamTemplate,
        val metadataOrigin: MetadataOrigin
    )

    data class InstallationReport(
        val countries: Int,
        val factualClubs: Int,
        val targetTeamsBefore: Int,
        val targetTeamsAfter: Int,
        val metadataOrigins: Map<MetadataOrigin, Int>
    )

    private val installationLock = Any()
    @Volatile private var installed = false
    @Volatile private var installationReport: InstallationReport? = null
    private val preInstallationTeamsByCountry = linkedMapOf<String, List<DefaultData.TeamTemplate>>()

    fun installIntoDefaultData(): InstallationReport {
        installationReport?.let { return it }
        synchronized(installationLock) {
            installationReport?.let { return it }
            require(EuropeanAuditedLowerTierClubTargetMaterializer2026_27.isInstalled()) {
                "Phase 9.11A2 must be installed before Phase 9.11A3."
            }

            val mutableCatalog = DefaultData.countriesMap as? MutableMap<String, DefaultData.CountryData>
                ?: error("DefaultData.countriesMap must retain a MutableMap backing store.")
            val totalBefore = mutableCatalog.values.sumOf { it.teams.size }
            val origins = linkedMapOf<MetadataOrigin, Int>().apply {
                MetadataOrigin.entries.forEach { put(it, 0) }
            }
            val countries = Fc26RemainingFactualBaselinesA3_2026_27.factualTargets
                .map { it.country }
                .distinct()

            countries.forEach { country ->
                val current = mutableCatalog[country]
                    ?: error("Phase 9.11A3 country missing from DefaultData: $country")
                preInstallationTeamsByCountry[country] = current.teams.toList()
                val targets = materializedTargets(country, current.teams)
                targets.forEach { target ->
                    origins[target.metadataOrigin] = origins.getValue(target.metadataOrigin) + 1
                }
                val materialized = current.teams.toMutableList()
                targets.forEach { target -> materialized[target.slotIndex] = target.template }
                mutableCatalog[country] = current.copy(teams = materialized.toList())
            }

            val report = InstallationReport(
                countries = countries.size,
                factualClubs = Fc26RemainingFactualBaselinesA3_2026_27.factualTargets.size,
                targetTeamsBefore = totalBefore,
                targetTeamsAfter = mutableCatalog.values.sumOf { it.teams.size },
                metadataOrigins = origins.toMap()
            )
            require(report.targetTeamsBefore == report.targetTeamsAfter) {
                "Phase 9.11A3 must replace slots in-place, never change team count."
            }
            installed = true
            installationReport = report
            return report
        }
    }

    fun isInstalled(): Boolean = installed

    fun currentInstallationReport(): InstallationReport? = installationReport

    fun contains(country: String, teamName: String): Boolean =
        installed && Fc26RemainingFactualBaselinesA3_2026_27.factualTargets.any { target ->
            target.country == country && target.canonicalName.equals(teamName, ignoreCase = true)
        }

    fun stableIdForMaterializedTarget(country: String, teamName: String): Long? =
        if (contains(country, teamName)) StableTeamIdentityRegistry.idFor(country, teamName) else null

    fun materializedTargets(
        country: String,
        currentTeams: List<DefaultData.TeamTemplate>
    ): List<MaterializedTarget> {
        val audited = Fc26RemainingFactualBaselinesA3_2026_27.forCountry(country)
        if (audited.isEmpty()) return emptyList()

        val mutableView = currentTeams.toMutableList()
        val consumed = hashSetOf<Int>()
        return audited.map { target ->
            val stableId = requireNotNull(StableTeamIdentityRegistry.idFor(country, target.canonicalName)) {
                "Phase 9.11A3 target missing stable identity: $country/${target.canonicalName}"
            }

            val exactIndex = mutableView.indices.firstOrNull { index ->
                index !in consumed &&
                    mutableView[index].division == target.division &&
                    mutableView[index].name.equals(target.canonicalName, ignoreCase = true)
            }
            if (exactIndex != null) {
                consumed += exactIndex
                val exact = mutableView[exactIndex].copy(name = target.canonicalName, division = target.division)
                mutableView[exactIndex] = exact
                return@map target.toMaterialized(
                    stableId = stableId,
                    slotIndex = exactIndex,
                    template = exact,
                    origin = MetadataOrigin.EXISTING_EXACT_SLOT
                )
            }

            val slotIndex = mutableView.indices.firstOrNull { index ->
                val template = mutableView[index]
                index !in consumed &&
                    template.division == target.division &&
                    StableTeamIdentityRegistry.idFor(country, template.name) == null
            } ?: error(
                "No safe procedural slot available for $country/${target.canonicalName} " +
                    "in division ${target.division}; refusing synthetic expansion."
            )

            consumed += slotIndex
            val factualIdentityOnInternalMetadata = mutableView[slotIndex].copy(
                name = target.canonicalName,
                division = target.division
            )
            mutableView[slotIndex] = factualIdentityOnInternalMetadata
            target.toMaterialized(
                stableId = stableId,
                slotIndex = slotIndex,
                template = factualIdentityOnInternalMetadata,
                origin = MetadataOrigin.INTERNAL_SLOT_METADATA
            )
        }
    }

    internal fun resetForTests() {
        synchronized(installationLock) {
            if (!installed && preInstallationTeamsByCountry.isEmpty()) return
            val mutableCatalog = DefaultData.countriesMap as? MutableMap<String, DefaultData.CountryData>
                ?: error("DefaultData.countriesMap lost its MutableMap backing store.")
            preInstallationTeamsByCountry.forEach { (country, teams) ->
                val current = mutableCatalog[country] ?: return@forEach
                mutableCatalog[country] = current.copy(teams = teams)
            }
            preInstallationTeamsByCountry.clear()
            installationReport = null
            installed = false
        }
    }

    private fun Fc26RemainingFactualBaselinesA3_2026_27.FactualTarget.toMaterialized(
        stableId: Long,
        slotIndex: Int,
        template: DefaultData.TeamTemplate,
        origin: MetadataOrigin
    ) = MaterializedTarget(
        sourceClubTeamId = sourceClubTeamId,
        country = country,
        stableTeamId = stableId,
        canonicalName = canonicalName,
        division = division,
        competitionName = competitionName,
        slotIndex = slotIndex,
        template = template,
        metadataOrigin = origin
    )
}
