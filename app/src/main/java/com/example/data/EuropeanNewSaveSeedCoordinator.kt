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
    private val pendingProceduralFallbackByRepository = WeakHashMap<Any, Boolean>()
    private val preCareerEditorOverridesByRepository = WeakHashMap<Any, PreCareerEditorOverrides>()

    /**
     * Registra somente uma intenção de seed factual. O asset pesado continua lazy.
     *
     * A opção de Editor de Clubes/Jogadores foi removida da tela inicial, portanto a criação de
     * carreira não deve mais materializar toda a tabela Player/Team apenas para procurar overlays
     * pré-carreira. Isso eliminava uma leitura completa e síncrona imediatamente antes do seed.
     * As estruturas antigas de overlay continuam isoladas para compatibilidade/testes internos.
     */
    fun prepare(repository: GameRepository, teams: List<Team>) {
        synchronized(lock) {
            pendingRequestByRepository[repository] = teams
            pendingSeedByRepository.remove(repository)
            pendingProceduralFallbackByRepository.remove(repository)
            preCareerEditorOverridesByRepository.remove(repository)
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

    private fun withPreCareerOverrides(repositoryKey: Any, seed: PendingSeed): PendingSeed {
        val overrides = synchronized(lock) { preCareerEditorOverridesByRepository[repositoryKey] }
        val (teams, players, loans) = applyPreCareerEditorOverrides(
            seedTeams = seed.teams,
            seedPlayers = seed.players,
            seedLoans = seed.loans,
            overrides = overrides
        )
        return PendingSeed(teams = teams, players = players, loans = loans)
    }

    private fun materializeRequestedSeed(repositoryKey: Any): PendingSeed? {
        synchronized(lock) {
            pendingSeedByRepository[repositoryKey]?.let { return it }
        }
        val requestedTeams = synchronized(lock) {
            pendingRequestByRepository.remove(repositoryKey)
        } ?: return null

        val startedAtNs = System.nanoTime()
        val rawSeed = buildPendingSeed(requestedTeams)
        val seed = rawSeed?.let { withPreCareerOverrides(repositoryKey, it) }
        val materializationMs = (System.nanoTime() - startedAtNs) / 1_000_000L
        if (seed != null) {
            CareerCreationPerformanceMonitor.noteFactualSeedMaterialization(materializationMs)
        }
        synchronized(lock) {
            if (seed == null) {
                pendingSeedByRepository.remove(repositoryKey)
                // Marca apenas o fallback procedural pertencente ao fluxo canônico de Novo Jogo.
                // Chamadas comuns de savePlayers (Editor, testes, saves existentes) não passam por
                // este marcador e, portanto, nunca têm histórico cumulativo reescrito aqui.
                pendingProceduralFallbackByRepository[repositoryKey] = true
            } else {
                pendingProceduralFallbackByRepository.remove(repositoryKey)
                pendingSeedByRepository[repositoryKey] = seed
            }
        }
        return seed
    }

    private fun applyTeamOverridesOnly(repositoryKey: Any, fallback: List<Team>): List<Team> {
        val overrides = synchronized(lock) { preCareerEditorOverridesByRepository[repositoryKey] }
            ?: return fallback
        val (teams, _, _) = applyPreCareerEditorOverrides(
            seedTeams = fallback,
            seedPlayers = emptyList(),
            seedLoans = emptyList(),
            overrides = overrides.copy(rostersByTeamId = emptyMap())
        )
        return teams
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
                pendingProceduralFallbackByRepository.remove(repositoryKey)
                preCareerEditorOverridesByRepository.remove(repositoryKey)
                return fallback
            }
        }
        return materializeRequestedSeed(repositoryKey)?.teams
            ?: applyTeamOverridesOnly(repositoryKey, fallback)
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
            pendingProceduralFallbackByRepository.remove(repositoryKey)
            preCareerEditorOverridesByRepository.remove(repositoryKey)
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
            pendingProceduralFallbackByRepository.remove(repositoryKey)
            preCareerEditorOverridesByRepository.remove(repositoryKey)
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

    /**
     * Materializa antecipadamente a requisição preparada pelo calendário, sem consumi-la.
     * O novo save usa isso para descobrir se existe seed factual antes de gastar CPU gerando um
     * roster procedural global que seria descartado logo depois por savePlayers().
     */
    fun materializePreparedSeed(repository: GameRepository): Boolean =
        materializeRequestedSeed(repository) != null

    internal fun materializePreparedSeedForTesting(repositoryKey: Any): Boolean =
        materializeRequestedSeed(repositoryKey) != null

    fun consumePlayers(repository: GameRepository, fallback: List<Player>): PlayerSeed =
        consumePlayersForKey(repository, fallback)

    private fun normalizeNewSaveProceduralHistory(players: List<Player>): List<Player> =
        players.map { player ->
            if (player.careerApps == 0 && player.careerGoals != 0) {
                player.copy(careerGoals = 0)
            } else {
                player
            }
        }

    internal fun consumePlayersForKey(repositoryKey: Any, fallback: List<Player>): PlayerSeed {
        val seed = synchronized(lock) {
            // O seed pode ter sido materializado por saveTeams ou antecipadamente pelo Novo Jogo.
            // Em ambos os casos, savePlayers continua sendo o único consumidor que remove o seed.
            pendingRequestByRepository.remove(repositoryKey)
            val pending = pendingSeedByRepository.remove(repositoryKey)
            val isNewSaveProceduralFallback =
                pendingProceduralFallbackByRepository.remove(repositoryKey) == true
            val overrides = preCareerEditorOverridesByRepository.remove(repositoryKey)
            if (pending == null) {
                val normalizedFallback = if (isNewSaveProceduralFallback) {
                    // O restart protegido considera careerApps=0 + careerGoals>0 um legado
                    // inconsistente. Novo Jogo procedural não deve mais fabricar esse estado.
                    normalizeNewSaveProceduralHistory(fallback)
                } else {
                    fallback
                }
                val (_, players, loans) = applyPreCareerEditorOverrides(
                    seedTeams = emptyList(),
                    seedPlayers = normalizedFallback,
                    seedLoans = emptyList(),
                    overrides = overrides?.copy(teamsById = emptyMap())
                )
                PlayerSeed(players, loans, overridden = overrides != null)
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
            pendingProceduralFallbackByRepository.remove(repositoryKey)
            preCareerEditorOverridesByRepository.remove(repositoryKey)
        }
    }
}
