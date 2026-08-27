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
    private val pendingRequestByRepository = WeakHashMap<Any, List<Team>>()
    private val pendingSeedByRepository = WeakHashMap<Any, PendingSeed>()

    /**
     * Registra somente uma intenção de seed. Nenhum asset é lido aqui.
     *
     * `generateSeasonFixtures()` também é reutilizado por viradas/reinícios de temporada. Carregar
     * 18 mil jogadores nesse ponto faria o snapshot inicial contaminar a carreira e os testes.
     * O materialization acontece apenas quando o fluxo de novo save persiste a MESMA lista em
     * `saveTeams` e, em seguida, consome o resultado em `savePlayers`.
     */
    fun prepare(repository: GameRepository, teams: List<Team>) {
        synchronized(lock) {
            pendingRequestByRepository[repository] = teams
            pendingSeedByRepository.remove(repository)
        }
    }

    private fun buildPendingSeed(teams: List<Team>): PendingSeed? {
        val fc26 = Fc26FactualAssetRuntime.loadValidatedOrNull()
        if (fc26 != null) {
            val plan = Fc26SeedPlanner.build(
                teams = teams,
                dataset = fc26,
                proceduralRosterFactory = { team ->
                    DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
                }
            )
            return PendingSeed(teams = teams, players = plan.players, loans = plan.loans)
        }

        val dataset = EuropeanFactualAssetRuntime.loadValidatedFactualOrNull() ?: return null
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
        return PendingSeed(teams = factualTeams, players = plan.players, loans = plan.loans)
    }

    private fun materializeRequestedSeed(repositoryKey: Any): PendingSeed? {
        synchronized(lock) {
            pendingSeedByRepository[repositoryKey]?.let { return it }
        }
        val requestedTeams = synchronized(lock) {
            pendingRequestByRepository.remove(repositoryKey)
        } ?: return null

        val seed = buildPendingSeed(requestedTeams)
        synchronized(lock) {
            if (seed == null) {
                pendingSeedByRepository.remove(repositoryKey)
            } else {
                pendingSeedByRepository[repositoryKey] = seed
            }
        }
        return seed
    }

    /**
     * A requisição lazy só é válida se `saveTeams` receber exatamente a mesma instância de lista
     * registrada pelo checkpoint de novo save. Uma geração de calendário antiga não pode, portanto,
     * ser consumida por um saveTeams futuro e não relacionado, ainda que contenha os mesmos clubes.
     */
    private fun teamsForKey(repositoryKey: Any, fallback: List<Team>): List<Team> {
        synchronized(lock) {
            pendingSeedByRepository[repositoryKey]?.let { return it.teams }
            val requested = pendingRequestByRepository[repositoryKey] ?: return fallback
            if (requested !== fallback) {
                pendingRequestByRepository.remove(repositoryKey)
                pendingSeedByRepository.remove(repositoryKey)
                return fallback
            }
        }
        return materializeRequestedSeed(repositoryKey)?.teams ?: fallback
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
            pendingRequestByRepository.remove(repositoryKey)
            pendingSeedByRepository[repositoryKey] = PendingSeed(
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
            pendingRequestByRepository.remove(repositoryKey)
            pendingSeedByRepository[repositoryKey] = PendingSeed(
                teams = factualTeams,
                players = plan.players,
                loans = plan.loans
            )
        }
    }

    fun teamsFor(repository: GameRepository, fallback: List<Team>): List<Team> =
        teamsForKey(repository, fallback)

    internal fun teamsForTesting(repositoryKey: Any, fallback: List<Team>): List<Team> =
        teamsForKey(repositoryKey, fallback)

    fun consumePlayers(repository: GameRepository, fallback: List<Player>): PlayerSeed =
        consumePlayersForKey(repository, fallback)

    internal fun consumePlayersForKey(repositoryKey: Any, fallback: List<Player>): PlayerSeed {
        val seed = synchronized(lock) {
            // Não materializa a partir de uma requisição crua: somente saveTeams pode fazê-lo.
            // Isso congela a ordem canônica do novo save: prepare -> saveTeams -> savePlayers.
            pendingRequestByRepository.remove(repositoryKey)
            val pending = pendingSeedByRepository.remove(repositoryKey)
            if (pending == null) {
                PlayerSeed(fallback, emptyList(), overridden = false)
            } else {
                PlayerSeed(pending.players, pending.loans, overridden = true)
            }
        }
        CareerCreationPerformanceMonitor.notePersistedPlayerCount(seed.players.size)
        return seed
    }

    fun clear(repository: GameRepository) {
        clearForKey(repository)
    }

    internal fun clearForKey(repositoryKey: Any) {
        synchronized(lock) {
            pendingRequestByRepository.remove(repositoryKey)
            pendingSeedByRepository.remove(repositoryKey)
        }
    }
}
