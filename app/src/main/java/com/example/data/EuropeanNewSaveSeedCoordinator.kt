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

    private fun Team.matchesIdentity(id: Long, country: String, name: String): Boolean =
        this.id == id &&
            this.country.equals(country, ignoreCase = true) &&
            this.name.equals(name, ignoreCase = true)

    /**
     * Chamado exclusivamente pelo gerador de temporada usado na criação inicial do novo save.
     * Sem dataset VALIDATED, ou quando nenhum clube do dataset participa do seed recebido, limpa
     * qualquer estado anterior e preserva 100% do fluxo procedural.
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
        // Um ID numérico isolado não identifica um clube. Testes e fluxos legados podem usar IDs
        // locais que coincidem com IDs factuais de outra associação. O overlay só é ativado quando
        // pelo menos um clube recebido corresponde integralmente a uma identidade do dataset.
        if (teams.none(dataset::appliesTo)) {
            clearForKey(repositoryKey)
            return
        }

        val loanEndpoints = dataset.loans.flatMap { loan ->
            listOf(
                Triple(loan.ownerTeamId, loan.ownerCountry, loan.ownerClubName),
                Triple(loan.borrowerTeamId, loan.borrowerCountry, loan.borrowerClubName)
            )
        }.distinct()

        val externalLoanTeams = loanEndpoints.mapNotNull { (teamId, country, name) ->
            val exact = teams.firstOrNull { it.matchesIdentity(teamId, country, name) }
            if (exact != null) return@mapNotNull null

            val conflicting = teams.firstOrNull { it.id == teamId }
            require(conflicting == null) {
                "Endpoint factual $country/$name usa teamId=$teamId, mas o seed já contém " +
                    "${conflicting?.country}/${conflicting?.name} com o mesmo ID."
            }

            val materialized = requireNotNull(GlobalFootballSystem.getTeamByGlobalId(teamId)) {
                "Endpoint de empréstimo factual não pode ser materializado pelo resolvedor global: teamId=$teamId"
            }
            require(materialized.matchesIdentity(teamId, country, name)) {
                "Resolvedor global retornou identidade divergente para endpoint factual: " +
                    "$country/$name teamId=$teamId -> ${materialized.country}/${materialized.name}"
            }
            materialized
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
