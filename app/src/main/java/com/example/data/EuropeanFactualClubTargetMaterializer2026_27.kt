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
 * O objetivo é permitir que o FC26 encontre o Team real correto sem fuzzy matching e sem criar uma
 * segunda fonte de verdade para ratings/atributos de jogadores.
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
        val lowerDivisions = legacyTeams.filter { template ->
            if (template.division == 1) return@filter false
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

        return baseline.verifiedTopFlightClubs.mapIndexed { factualIndex, canonicalName ->
            val stableTeamId = requireNotNull(StableTeamIdentityRegistry.idFor(country, canonicalName)) {
                "Baseline factual sem identidade estável: $country / $canonicalName"
            }

            val existingExplicit = legacyTeams.withIndex().firstOrNull { (_, template) ->
                StableTeamIdentityRegistry.idFor(country, template.name) == stableTeamId
            }
            if (existingExplicit != null) {
                val firstDivisionIndex = firstDivisionSlots.indexOf(existingExplicit.value)
                if (firstDivisionIndex >= 0) consumedSlots += firstDivisionIndex
                return@mapIndexed MaterializedTarget(
                    country = country,
                    stableTeamId = stableTeamId,
                    canonicalName = canonicalName,
                    template = existingExplicit.value.copy(name = canonicalName, division = 1),
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

    /** Somente nomes canônicos efetivamente materializados são elegíveis ao ID estável no seed. */
    fun contains(country: String, teamName: String): Boolean {
        val baseline = EuropeanDomesticBaseline2026_27.forCountry(country)
            ?.takeIf { it.coverage == EuropeanDomesticCoverage.VERIFIED_TOP_FLIGHT }
            ?: return false
        return baseline.verifiedTopFlightClubs.any { it.equals(teamName, ignoreCase = true) }
    }

    fun stableIdForMaterializedTarget(country: String, teamName: String): Long? =
        if (contains(country, teamName)) StableTeamIdentityRegistry.idFor(country, teamName) else null

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
