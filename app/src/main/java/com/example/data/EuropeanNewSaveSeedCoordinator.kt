package com.example.data

import android.content.res.AssetManager
import java.util.WeakHashMap

/**
 * Ponte mínima entre os assets canônicos e o fluxo de criação de uma carreira.
 *
 * O runtime só expõe datasets FACTUAL + VALIDATED. O estado preparado é efêmero, associado à
 * instância do repositório do slot e consumido uma única vez pela transação inicial de Team/Player.
 * Reparos, carregamentos e saves existentes não preparam este estado.
 */
object EuropeanFactualAssetRuntime {
    @Volatile
    private var assetManager: AssetManager? = null

    fun initialize(assets: AssetManager) {
        assetManager = assets
    }

    fun loadValidatedFactualOrNull(): EuropeanCanonicalDataset? =
        assetManager?.let(EuropeanCanonicalDatasetLoader::loadValidatedFactualOrNull)

    internal fun clearForTesting() {
        assetManager = null
    }
}

object EuropeanNewSaveSeedCoordinator {
    data class PendingSeed(
        val teams: List<Team>,
        val players: List<Player>,
        val loans: List<PlayerLoan>
    )

    data class PlayerSeed(
        val players: List<Player>,
        val loans: List<PlayerLoan>,
        val overridden: Boolean
    )

    private val lock = Any()
    private val pendingByRepository = WeakHashMap<Any, PendingSeed>()

    /**
     * Chamado exclusivamente pelo gerador de temporada usado na criação inicial do novo save.
     * Sem dataset VALIDATED, limpa qualquer estado anterior e preserva 100% do fluxo procedural.
     */
    fun prepare(repository: GameRepository, teams: List<Team>) {
        val dataset = EuropeanFactualAssetRuntime.loadValidatedFactualOrNull()
        if (dataset == null) {
            clear(repository)
            return
        }
        prepareForDataset(repository, teams, dataset)
    }

    internal fun prepareForDataset(
        repositoryKey: Any,
        teams: List<Team>,
        dataset: EuropeanCanonicalDataset
    ) {
        val factualTeams = dataset.applyClubFacts(teams)
        val plan = dataset.buildSeedPlan(
            teams = factualTeams,
            proceduralRosterFactory = { team ->
                DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
            }
        )
        require(plan.blockedLoans.isEmpty()) {
            "Dataset factual contém empréstimos que não podem ser materializados no novo save: " +
                plan.blockedLoans.joinToString { it.reason }
        }
        synchronized(lock) {
            pendingByRepository[repositoryKey] = PendingSeed(
                teams = factualTeams,
                players = plan.players,
                loans = plan.loans
            )
        }
    }

    fun teamsFor(repository: GameRepository, fallback: List<Team>): List<Team> =
        synchronized(lock) { pendingByRepository[repository]?.teams ?: fallback }

    internal fun teamsForTesting(repositoryKey: Any, fallback: List<Team>): List<Team> =
        synchronized(lock) { pendingByRepository[repositoryKey]?.teams ?: fallback }

    fun consumePlayers(repository: GameRepository, fallback: List<Player>): PlayerSeed =
        consumePlayersForKey(repository, fallback)

    internal fun consumePlayersForKey(repositoryKey: Any, fallback: List<Player>): PlayerSeed =
        synchronized(lock) {
            val pending = pendingByRepository.remove(repositoryKey)
                ?: return@synchronized PlayerSeed(fallback, emptyList(), overridden = false)
            PlayerSeed(pending.players, pending.loans, overridden = true)
        }

    fun clear(repository: GameRepository) {
        clearForKey(repository)
    }

    internal fun clearForKey(repositoryKey: Any) {
        synchronized(lock) {
            pendingByRepository.remove(repositoryKey)
        }
    }
}
