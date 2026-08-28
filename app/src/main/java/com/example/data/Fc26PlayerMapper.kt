package com.example.data

import java.util.concurrent.ConcurrentHashMap

object Fc26PlayerMapper {
    private const val WEEKS_PER_CONTRACT_YEAR = 48

    private data class RuntimeTemplateEntry(
        val source: Fc26NormalizedPlayer,
        val player: Player
    )

    /**
     * O mapeamento factual cria um envelope JSON relativamente grande para cada jogador. Como o
     * snapshot FC26 é imutável durante o processo, esse Player-base também é imutável: somente
     * teamId muda conforme o matching do clube. Cacheá-lo elimina a repetição de conversão de
     * posição/data/moeda e, principalmente, serialização Gson durante o clique em Nova Carreira.
     *
     * A referência do source faz o cache continuar seguro em testes/reloads que reutilizem o mesmo
     * stableId com outro objeto factual. No dataset de produção os stableIds já são únicos.
     */
    private val runtimeTemplateCache = ConcurrentHashMap<Long, RuntimeTemplateEntry>()

    fun toPlayer(source: Fc26NormalizedPlayer, teamId: Long?): Player {
        val template = runtimeTemplate(source)
        return if (teamId == null) template else template.copy(teamId = teamId)
    }

    internal fun prewarmRuntimeTemplates(players: List<Fc26NormalizedPlayer>) {
        players.forEach { runtimeTemplate(it) }
    }

    internal fun clearRuntimeTemplateCache() {
        runtimeTemplateCache.clear()
    }

    private fun runtimeTemplate(source: Fc26NormalizedPlayer): Player {
        runtimeTemplateCache[source.stableId]?.takeIf { it.source === source }?.let { return it.player }

        val built = buildRuntimeTemplate(source)
        val candidate = RuntimeTemplateEntry(source, built)
        val existing = runtimeTemplateCache.putIfAbsent(source.stableId, candidate)
        if (existing == null) return built
        if (existing.source === source) return existing.player

        // Só é alcançado por reload/teste com outro objeto usando o mesmo stableId. A base oficial
        // rejeita colisões antes do planner, portanto substituir aqui não mascara dados de produção.
        runtimeTemplateCache[source.stableId] = candidate
        return built
    }

    private fun buildRuntimeTemplate(source: Fc26NormalizedPlayer): Player {
        val simplifiedPosition = Fc26PositionMapper.simplified(source.positions)
        val age = ageAt2026SeasonStart(source.birthDateIso)
        val marketValue = Fc26MoneyPolicy.eurToGameCurrency(source.valueEur)
        val salary = Fc26MoneyPolicy.eurToGameCurrency(source.wageEur)
        val releaseClause = Fc26MoneyPolicy.eurToGameCurrency(source.releaseClauseEur)
        val a = source.atributos

        return Player(
            id = source.stableId,
            teamId = null,
            name = source.fullName,
            age = age,
            nationality = source.nationality,
            position = simplifiedPosition,
            force = source.overall, // FC26 overall é a source of truth inicial; nunca recalcular aqui.
            energy = 100,
            moral = 75,
            salary = salary,
            contractDurationWeeks = contractWeeks(source.contractUntilYear),
            isFromAcademy = false,
            isStarter = false,
            isOnLoan = false, // FC26 não traz duração suficiente para materializar PlayerLoan sem inventar fatos.
            originalTeamId = null,
            market_value = marketValue,
            min_price = if (marketValue > 0L) (marketValue * 80L / 100L).coerceAtLeast(30_000L) else 0L,
            max_price = when {
                releaseClause > 0L -> releaseClause
                marketValue > 0L -> (marketValue * 130L / 100L).coerceAtLeast(50_000L)
                else -> 0L
            },
            demand_level = when {
                source.overall >= 86 -> "high"
                source.overall >= 72 -> "medium"
                else -> "low"
            },
            finishing = source.summaryShooting ?: a.finalizacao,
            passing = source.summaryPassing ?: a.passe,
            pace = source.summaryPace ?: a.velocidade,
            strength = source.summaryPhysic ?: a.forca,
            vision = a.visaoJogo,
            defense = source.summaryDefending ?: a.marcacao,
            atributosJson = Fc26ImportMetadata.toJson(source),
            atributos = a,
            potential = source.potential // FC26 potential é preservado diretamente.
        )
    }

    internal fun ageAt2026SeasonStart(birthDateIso: String): Int {
        val birth = parseStrictIsoDate(birthDateIso, "FC26 dob")
        val birthdayAlreadyOccurred = birth.month < 8 || (birth.month == 8 && birth.day <= 1)
        return (2026 - birth.year - if (birthdayAlreadyOccurred) 0 else 1).also { age ->
            require(age in 15..60) { "FC26 idade implausível em 2026-08-01: $age ($birthDateIso)" }
        }
    }

    internal fun contractWeeks(contractUntilYear: Int?): Int {
        if (contractUntilYear == null || contractUntilYear < 2026) return WEEKS_PER_CONTRACT_YEAR
        val seasonsIncludingExpiry = (contractUntilYear - 2026 + 1).coerceIn(1, 8)
        return seasonsIncludingExpiry * WEEKS_PER_CONTRACT_YEAR
    }
}
