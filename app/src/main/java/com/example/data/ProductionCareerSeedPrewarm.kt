package com.example.data

/**
 * Pré-calcula em background o plano factual imutável usado por uma nova carreira.
 *
 * O clube controlado não altera nenhum jogador do seed, por isso o cache ignora apenas
 * `isPlayerControlled`. Todo o restante da identidade esportiva continua fazendo parte da chave
 * do cache em [Fc26SeedPlanner]. Assim, o clique em INICIAR CARREIRA não precisa repetir o
 * matching FC26, o mapeamento de atletas, a resolução de empréstimos e os fallbacks.
 */
object ProductionCareerSeedPrewarm {
    fun prewarm() {
        val dataset = Fc26FactualAssetRuntime.loadValidatedOrNull() ?: return
        Fc26SeedPlanner.prewarmProduction(buildProductionTeamUniverse(), dataset)
    }

    internal fun buildProductionTeamUniverse(playerControlledTeamId: Long = 0L): List<Team> = buildList {
        for (countryKey in GlobalFootballSystem.keys) {
            val templates = DefaultData.getTeamsForCountry(countryKey)
            for (template in templates) {
                val globalId = GlobalFootballSystem.getGlobalId(countryKey, template.name)
                add(
                    Team(
                        id = globalId,
                        name = template.name,
                        city = template.city,
                        state = template.state,
                        country = countryKey,
                        division = template.division,
                        rating = template.rating,
                        stadiumName = template.stadium,
                        logoUrl = DefaultData.getLogoForTeam(template.name, countryKey),
                        isPlayerControlled = globalId == playerControlledTeamId
                    )
                )
            }
        }
    }
}
