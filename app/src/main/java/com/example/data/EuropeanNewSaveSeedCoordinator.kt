package com.example.data

import android.content.res.AssetManager
import java.util.WeakHashMap

/**
 * Ponte mínima entre os assets canônicos e o fluxo de criação de uma carreira.
 *
 * O runtime europeu continua disponível como fallback. Quando o snapshot FC26 VALIDATED está
 * presente, ele assume o seed de jogadores do novo save, sem reimportar dados em saves existentes.
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
     * FC26 é a primeira opção. Se o asset não existir/estiver desativado, o seed factual europeu
     * anterior permanece como fallback; sem ambos, preserva 100% o fluxo procedural legado.
     */
    fun prepare(repository: GameRepository, teams: List<Team>) {
        val fc26 = Fc26FactualAssetRuntime.loadValidatedOrNull()
        if (fc26 != null) {
            prepareForFc26(repository, teams, fc26)
            return
        }

        val dataset = EuropeanFactualAssetRuntime.loadValidatedFactualOrNull()
        if (dataset == null) {
            clear(repository)
            return
        }
        prepareForDataset(repository, teams, dataset)
    }

    internal fun prepareForFc26(
        repositoryKey: Any,
        teams: List<Team>,
        dataset: Fc26Dataset
    ): Fc26SeedReport {
        val plan = Fc26SeedPlanner.build(
            teams = teams,
            dataset = dataset,
            proceduralRosterFactory = { team ->
                DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
            }
        )
        synchronized(lock) {
            pendingByRepository[repositoryKey] = PendingSeed(
                teams = teams,
                players = plan.players,
                loans = plan.loans
            )
        }
        return plan.report
    }

    internal fun prepareForDataset(
        repositoryKey: Any,
        teams: List<Team>,
        dataset: EuropeanCanonicalDataset
    ) {
        val existingIds = teams.mapTo(hashSetOf()) { it.id }
        val loanEndpointIds = dataset.loans
            .flatMap { listOf(it.ownerTeamId, it.borrowerTeamId) }
            .distinct()
        val externalLoanTeams = loanEndpointIds
            .filterNot(existingIds::contains)
            .map { teamId ->
                requireNotNull(GlobalFootballSystem.getTeamByGlobalId(teamId)) {
                    "Endpoint de empréstimo factual não pode ser materializado pelo resolvedor global: teamId=$teamId"
                }
            }

        val seedTeams = teams + externalLoanTeams
        require(seedTeams.map { it.id }.distinct().size == seedTeams.size) {
            "Seed de novo save contém teamId duplicado após materializar endpoints de empréstimo."
        }

        val factualTeams = dataset.applyClubFacts(seedTeams)
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
