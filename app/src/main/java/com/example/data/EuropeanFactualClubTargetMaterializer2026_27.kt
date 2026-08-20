package com.example.data

import java.util.Random

/**
 * Materializa a identidade factual dos clubes de primeira divisão já verificados para 2026/27.
 *
 * Esta camada NÃO declara cidade, estádio ou rating como fatos quando essas informações ainda não
 * possuem proveniência própria. Nesses casos, preserva metadados internos do slot procedural
 * determinístico (ou cria um slot interno determinístico quando a liga factual possui mais clubes
 * que o catálogo legado). Nome, divisão e identidade estável vêm do baseline factual.
 *
 * A instalação preserva uma fotografia da ordem do catálogo anterior. O GlobalFootballSystem usa
 * essa fotografia apenas para continuar atribuindo os mesmos IDs aos clubes não estáveis das
 * divisões inferiores; os clubes factuais materializados passam a usar exclusivamente os IDs do
 * StableTeamIdentityRegistry.
 */
object EuropeanFactualClubTargetMaterializer2026_27 {
    enum class MetadataOrigin {
        EXISTING_EXPLICIT_TEMPLATE,
        ADDITIONAL_FACTUAL_TEMPLATE,
        INTERNAL_SLOT_METADATA,
        INTERNAL_SYNTHETIC_SLOT_METADATA
    }

    data class MaterializedTarget(
        val country: String,
        val stableTeamId: Long,
        val canonicalName: String,
        val template: DefaultData.TeamTemplate,
        val metadataOrigin: MetadataOrigin
    )

    data class InstallationReport(
        val countries: Int,
        val factualTopFlightClubs: Int,
        val targetTeamsBefore: Int,
        val targetTeamsAfter: Int,
        val metadataOrigins: Map<MetadataOrigin, Int>
    )

    private val installationLock = Any()
    @Volatile private var installed = false
    @Volatile private var installationReport: InstallationReport? = null
    private val legacyTeamsByCountry = linkedMapOf<String, List<DefaultData.TeamTemplate>>()

    /**
     * Instala o catálogo factual no backing map já construído pelo DefaultData.
     *
     * `countriesMap` é deliberadamente exposto como Map, mas sua construção retorna o MutableMap
     * interno. Fazemos cast fail-fast aqui para não depender de reflexão nem substituir a API pública
     * de DefaultData nesta fase.
     */
    fun installIntoDefaultData(): InstallationReport {
        installationReport?.let { return it }
        synchronized(installationLock) {
            installationReport?.let { return it }

            val mutableCatalog = DefaultData.countriesMap as? MutableMap<String, DefaultData.CountryData>
                ?: error("DefaultData.countriesMap precisa manter backing MutableMap para materialização factual.")
            val totalBefore = mutableCatalog.values.sumOf { it.teams.size }
            val origins = linkedMapOf<MetadataOrigin, Int>().apply {
                MetadataOrigin.entries.forEach { put(it, 0) }
            }
            var factualClubCount = 0
            var countryCount = 0

            EuropeanDomesticBaseline2026_27.associations
                .filter { it.coverage == EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT }
                .forEach { baseline ->
                    val countryData = mutableCatalog[baseline.country]
                        ?: error("País verificado ausente do DefaultData: ${baseline.country}")
                    val legacyTeams = countryData.teams.toList()
                    legacyTeamsByCountry[baseline.country] = legacyTeams
                    val targets = materializedTargets(baseline.country, legacyTeams)
                    targets.forEach { target ->
                        origins[target.metadataOrigin] = origins.getValue(target.metadataOrigin) + 1
                    }
                    factualClubCount += targets.size
                    countryCount += 1
                    mutableCatalog[baseline.country] = countryData.copy(
                        teams = materialize(baseline.country, legacyTeams)
                    )
                }

            val report = InstallationReport(
                countries = countryCount,
                factualTopFlightClubs = factualClubCount,
                targetTeamsBefore = totalBefore,
                targetTeamsAfter = mutableCatalog.values.sumOf { it.teams.size },
                metadataOrigins = origins.toMap()
            )
            installed = true
            installationReport = report
            return report
        }
    }

    fun isInstalled(): Boolean = installed

    /** Ordem anterior à instalação, usada somente para preservar IDs de clubes não estáveis. */
    fun legacyTeamsForIdAllocation(country: String): List<DefaultData.TeamTemplate>? =
        synchronized(installationLock) { legacyTeamsByCountry[country]?.toList() }

    fun currentInstallationReport(): InstallationReport? = installationReport

    /** Restaura o catálogo legado depois de testes JVM para não vazar estado entre classes de teste. */
    internal fun resetForTests() {
        synchronized(installationLock) {
            if (!installed && legacyTeamsByCountry.isEmpty()) return
            val mutableCatalog = DefaultData.countriesMap as? MutableMap<String, DefaultData.CountryData>
                ?: error("DefaultData.countriesMap deixou de possuir backing MutableMap.")
            legacyTeamsByCountry.forEach { (country, legacyTeams) ->
                val current = mutableCatalog[country] ?: return@forEach
                mutableCatalog[country] = current.copy(teams = legacyTeams)
            }
            legacyTeamsByCountry.clear()
            installationReport = null
            installed = false
        }
    }

    fun materialize(
        country: String,
        legacyTeams: List<DefaultData.TeamTemplate>
    ): List<DefaultData.TeamTemplate> {
        val baseline = EuropeanDomesticBaseline2026_27.forCountry(country)
            ?.takeIf { it.coverage == EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT }
            ?: return legacyTeams

        val targets = materializedTargets(country, legacyTeams)
        require(targets.size == baseline.topDivisionClubCount) {
            "$country deveria materializar ${baseline.topDivisionClubCount} clubes factuais, mas gerou ${targets.size}."
        }
        require(targets.map { it.stableTeamId }.distinct().size == targets.size) {
            "$country materializou IDs estáveis duplicados."
        }

        val promotedStableIds = targets.mapTo(hashSetOf()) { it.stableTeamId }
        val explicitTemplates = DefaultData.originalMap[country]?.teams.orEmpty()
        val lowerDivisions = legacyTeams.filter { template ->
            if (template.division == 1) return@filter false
            if (isVerifiedCanonicalTarget(country, template.name)) return@filter false
            val isExplicitTemplate = explicitTemplates.any { it == template }
            if (!isExplicitTemplate) return@filter true
            val stableId = StableTeamIdentityRegistry.idFor(country, template.name)
            stableId == null || stableId !in promotedStableIds
        }

        return targets.map { it.template } + lowerDivisions
    }

    fun materializedTargets(
        country: String,
        legacyTeams: List<DefaultData.TeamTemplate>
    ): List<MaterializedTarget> {
        val baseline = EuropeanDomesticBaseline2026_27.forCountry(country)
            ?.takeIf { it.coverage == EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT }
            ?: return emptyList()

        val firstDivisionSlots = legacyTeams.filter { it.division == 1 }
        val consumedSlots = hashSetOf<Int>()
        val explicitTemplates = DefaultData.originalMap[country]?.teams.orEmpty()

        return baseline.verifiedTopFlightClubs.mapIndexed { factualIndex, canonicalName ->
            val stableTeamId = requireNotNull(StableTeamIdentityRegistry.idFor(country, canonicalName)) {
                "Baseline factual sem identidade estável: $country / $canonicalName"
            }

            val existingExplicit = explicitTemplates.firstOrNull { template ->
                StableTeamIdentityRegistry.idFor(country, template.name) == stableTeamId
            }
            if (existingExplicit != null) {
                val firstDivisionIndex = firstDivisionSlots.indexOf(existingExplicit)
                if (firstDivisionIndex >= 0) consumedSlots += firstDivisionIndex
                return@mapIndexed MaterializedTarget(
                    country = country,
                    stableTeamId = stableTeamId,
                    canonicalName = canonicalName,
                    template = existingExplicit.copy(name = canonicalName, division = 1),
                    metadataOrigin = MetadataOrigin.EXISTING_EXPLICIT_TEMPLATE
                )
            }

            EuropeanAdditionalClubTemplates2026_27.find(country, canonicalName)?.let { explicit ->
                return@mapIndexed MaterializedTarget(
                    country = country,
                    stableTeamId = stableTeamId,
                    canonicalName = canonicalName,
                    template = explicit.template.copy(name = canonicalName, division = 1),
                    metadataOrigin = MetadataOrigin.ADDITIONAL_FACTUAL_TEMPLATE
                )
            }

            val slotIndex = firstDivisionSlots.indices.firstOrNull { it !in consumedSlots }
            if (slotIndex != null) {
                consumedSlots += slotIndex
                return@mapIndexed MaterializedTarget(
                    country = country,
                    stableTeamId = stableTeamId,
                    canonicalName = canonicalName,
                    template = firstDivisionSlots[slotIndex].copy(name = canonicalName, division = 1),
                    metadataOrigin = MetadataOrigin.INTERNAL_SLOT_METADATA
                )
            }

            MaterializedTarget(
                country = country,
                stableTeamId = stableTeamId,
                canonicalName = canonicalName,
                template = syntheticInternalSlot(country, canonicalName, stableTeamId, factualIndex),
                metadataOrigin = MetadataOrigin.INTERNAL_SYNTHETIC_SLOT_METADATA
            )
        }
    }

    /**
     * Installed stable targets exposed to the global ID resolver. Phase 9.11A2/A3 lower-tier targets
     * join this surface only while their dedicated materializers are installed.
     */
    fun contains(country: String, teamName: String): Boolean =
        installed && (
            isVerifiedCanonicalTarget(country, teamName) ||
                EuropeanAuditedLowerTierClubTargetMaterializer2026_27.contains(country, teamName) ||
                EuropeanAuditedFactualBaselinesA3Materializer2026_27.contains(country, teamName)
        )

    fun stableIdForMaterializedTarget(country: String, teamName: String): Long? =
        if (contains(country, teamName)) StableTeamIdentityRegistry.idFor(country, teamName) else null

    private fun isVerifiedCanonicalTarget(country: String, teamName: String): Boolean {
        val baseline = EuropeanDomesticBaseline2026_27.forCountry(country)
            ?.takeIf { it.coverage == EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT }
            ?: return false
        return baseline.verifiedTopFlightClubs.any { it.equals(teamName, ignoreCase = true) }
    }

    private fun syntheticInternalSlot(
        country: String,
        canonicalName: String,
        stableTeamId: Long,
        factualIndex: Int
    ): DefaultData.TeamTemplate {
        val cities = DefaultData.countryCities[country].orEmpty().ifEmpty { listOf(country) }
        val city = cities[factualIndex % cities.size]
        val random = Random(stableTeamId)
        val state = DefaultData.countryStateCode(country, random)
        val rating = 75 + ((stableTeamId % 11L).toInt())
        return DefaultData.TeamTemplate(
            name = canonicalName,
            city = city,
            state = state,
            division = 1,
            rating = rating,
            stadium = "Arena $city"
        )
    }
}
