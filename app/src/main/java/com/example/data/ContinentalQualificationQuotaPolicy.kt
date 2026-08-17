package com.example.data

/**
 * Política de distribuição das vagas continentais por associação nacional.
 *
 * A política separa as vagas nacionais-base das vagas suplementares. Isso é importante porque
 * o jogo hoje começa T1/T2 diretamente na fase de grupos e ainda não simula, como competições
 * próprias, os caminhos preliminares e as vagas automáticas dos campeões continentais.
 *
 * CONMEBOL 2026, traduzida para o formato atual do jogo:
 * - T1 (Libertadores): 26 vagas nacionais-base + 6 suplementares = 32 na fase de grupos.
 *   As suplementares representam, no modelo atual, as 2 vagas dos campeões continentais e as
 *   4 vagas provenientes da Fase 3.
 * - T2 (Sudamericana): 28 vagas nacionais-base + 4 suplementares = 32 na fase de grupos.
 *   As suplementares representam os quatro clubes transferidos da Fase 3 da Libertadores.
 * - T3: desativado na CONMEBOL; a confederação é representada por T1 e T2.
 *
 * As vagas suplementares são preenchidas pela prioridade esportiva transitória já calculada em
 * [ContinentalQualificationRules], sem alterar rating persistido nem criar schema Room.
 */
object ContinentalQualificationQuotaPolicy {

    data class TierPlan(
        val competitionType: String,
        val targetFieldSize: Int,
        val directCountryQuotas: Map<String, Int>,
        val supplementalSlots: Int,
        val enabled: Boolean = true
    ) {
        init {
            require(targetFieldSize >= 0)
            require(supplementalSlots >= 0)
            require(directCountryQuotas.values.all { it >= 0 })
            require(directCountryQuotas.values.sum() + supplementalSlots == targetFieldSize) {
                "Plano $competitionType inconsistente: quotas diretas + suplementares devem somar $targetFieldSize"
            }
        }
    }

    data class SelectionResult(
        val teams: List<Team>,
        val directSelectedByCountry: Map<String, Int>,
        val supplementalTeamIds: Set<Long>
    )

    private val southAmericanCountries = listOf(
        "Brasil",
        "Argentina",
        "Bolívia",
        "Chile",
        "Colômbia",
        "Equador",
        "Paraguai",
        "Peru",
        "Uruguai",
        "Venezuela"
    )

    private val libertadoresBaseQuotas = buildMap {
        southAmericanCountries.forEach { country ->
            put(
                country,
                when (country) {
                    "Brasil", "Argentina" -> 5
                    else -> 2
                }
            )
        }
    }

    private val sudamericanaBaseQuotas = buildMap {
        southAmericanCountries.forEach { country ->
            put(
                country,
                when (country) {
                    "Brasil", "Argentina" -> 6
                    else -> 2
                }
            )
        }
    }

    private val conmebolPlans = mapOf(
        "CONTINENTAL_T1" to TierPlan(
            competitionType = "CONTINENTAL_T1",
            targetFieldSize = 32,
            directCountryQuotas = libertadoresBaseQuotas,
            supplementalSlots = 6
        ),
        "CONTINENTAL_T2" to TierPlan(
            competitionType = "CONTINENTAL_T2",
            targetFieldSize = 32,
            directCountryQuotas = sudamericanaBaseQuotas,
            supplementalSlots = 4
        ),
        "CONTINENTAL_T3" to TierPlan(
            competitionType = "CONTINENTAL_T3",
            targetFieldSize = 0,
            directCountryQuotas = emptyMap(),
            supplementalSlots = 0,
            enabled = false
        )
    )

    fun planFor(confederation: String, competitionType: String): TierPlan? {
        return if (confederation.equals("CONMEBOL", ignoreCase = true)) {
            conmebolPlans[competitionType]
        } else {
            null
        }
    }

    fun hasExplicitPolicy(confederation: String): Boolean =
        confederation.equals("CONMEBOL", ignoreCase = true)

    /**
     * Seleciona um campo preservando as quotas mínimas de cada país quando há clubes elegíveis.
     *
     * A lista [candidates] já deve estar em prioridade esportiva decrescente. Primeiro são
     * consumidas as quotas nacionais-base; depois as vagas suplementares e eventuais faltas de
     * uma associação são redistribuídas aos melhores clubes ainda disponíveis. [excludedTeamIds]
     * garante campos continentais disjuntos.
     */
    fun selectField(
        candidates: List<Team>,
        plan: TierPlan,
        excludedTeamIds: Set<Long> = emptySet()
    ): SelectionResult {
        if (!plan.enabled || plan.targetFieldSize == 0) {
            return SelectionResult(emptyList(), emptyMap(), emptySet())
        }

        val eligible = candidates
            .asSequence()
            .filter { it.id !in excludedTeamIds }
            .distinctBy { it.id }
            .toList()

        val selected = mutableListOf<Team>()
        val selectedIds = mutableSetOf<Long>()
        val directCounts = linkedMapOf<String, Int>()

        for ((country, quota) in plan.directCountryQuotas) {
            if (quota == 0 || selected.size >= plan.targetFieldSize) continue

            val countrySelected = eligible
                .asSequence()
                .filter { it.country.equals(country, ignoreCase = true) }
                .filter { selectedIds.add(it.id) }
                .take(quota)
                .toList()

            selected += countrySelected
            directCounts[country] = countrySelected.size
        }

        val directSelectedIds = selectedIds.toSet()
        val remainingSlots = (plan.targetFieldSize - selected.size).coerceAtLeast(0)
        if (remainingSlots > 0) {
            eligible
                .asSequence()
                .filter { selectedIds.add(it.id) }
                .take(remainingSlots)
                .forEach(selected::add)
        }

        val supplementalIds = selected
            .asSequence()
            .map { it.id }
            .filter { it !in directSelectedIds }
            .toSet()

        return SelectionResult(
            teams = selected.take(plan.targetFieldSize),
            directSelectedByCountry = directCounts,
            supplementalTeamIds = supplementalIds
        )
    }
}
