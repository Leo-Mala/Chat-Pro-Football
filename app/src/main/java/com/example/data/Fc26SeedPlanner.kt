package com.example.data

data class Fc26SeedReport(
    val datasetPlayers: Int,
    /** Legacy club-coverage counter: mapped-club players + factual dataset free agents. */
    val importedFc26Players: Int,
    /** Legacy club-coverage counter: players whose source club is not safely resolved yet. */
    val skippedDatasetPlayers: Int,
    /** Actual bulk-import count: every FC26 player materialized into the game plan. */
    val bulkImportedFc26Players: Int,
    val datasetClubs: Int,
    val matchedClubs: Int,
    val unmatchedClubs: Int,
    val ambiguousClubs: Int,
    val playersWithMappedClub: Int,
    val importedFreeAgents: Int,
    val importedUnassignedClubPlayers: Int,
    val importedUnmatchedClubPlayers: Int,
    val importedAmbiguousClubPlayers: Int,
    val datasetLoanPlayers: Int,
    /** Compatibility name used by older audit reports; authoritative from Phase 10.4 onward. */
    val successfullyMappedLoans: Int,
    /** Compatibility name used by older audit reports; authoritative from Phase 10.4 onward. */
    val unresolvedLoans: Int,
    val resolvedLoans: Int,
    val rejectedLoans: Int,
    val ambiguousLoans: Int,
    val ownerNotFound: Int,
    val borrowerNotFound: Int,
    val selfLoansRejected: Int,
    val duplicateLoans: Int,
    val fallbackRostersRequired: Int,
    /** Phase 9.14: actual procedural players retained after the FC26 fallback roster policy. */
    val fallbackPlayersGenerated: Int,
    val clubMatches: List<Fc26ClubMatch>,
    val loanResolutions: List<Fc26LoanResolution>
)

object Fc26SeedPlanner {
    data class Plan(
        val players: List<Player>,
        val loans: List<PlayerLoan>,
        val report: Fc26SeedReport
    ) {
        init {
            require(players.map { it.id }.distinct().size == players.size) {
                "FC26 seed contém Player.id duplicado."
            }
            require(loans.map { it.playerId }.distinct().size == loans.size) {
                "FC26 seed contém múltiplos empréstimos ativos para o mesmo jogador."
            }
            require(loans.all { it.playerId > 0L && it.ownerTeamId > 0L && it.borrowerTeamId > 0L }) {
                "FC26 seed contém referência de empréstimo inválida."
            }
            require(loans.none { it.ownerTeamId == it.borrowerTeamId }) {
                "FC26 seed contém self-loan."
            }
        }
    }

    private data class ProductionCacheKey(
        val assetSha256: String,
        val teamsFingerprint: Long
    )

    private data class ProductionCache(
        val key: ProductionCacheKey,
        val plan: Plan
    )

    @Volatile
    private var productionCache: ProductionCache? = null
    private val productionCacheLock = Any()

    /**
     * O universo de produção possui milhares de clubes e é sempre materializado com o mesmo
     * factory determinístico. Esse plano contém ~60k jogadores e é caro o bastante para nunca ser
     * recalculado no clique de INICIAR CARREIRA. `isPlayerControlled` é propositalmente ignorado na
     * chave: ele altera somente Team, não o roster factual/procedural produzido aqui.
     */
    fun prewarmProduction(teams: List<Team>, dataset: Fc26Dataset): Plan {
        require(teams.size >= PRODUCTION_CACHE_MIN_TEAMS) {
            "Prewarm de produção exige universo completo de clubes."
        }
        return build(teams, dataset) { team ->
            DefaultData.generateFc26FallbackRosterForTeam(team.id, team.rating, team.name, team.country)
        }
    }

    fun build(
        teams: List<Team>,
        dataset: Fc26Dataset,
        proceduralRosterFactory: (Team) -> List<Player>
    ): Plan {
        val cacheKey = productionCacheKeyOrNull(teams, dataset)
        if (cacheKey != null) {
            productionCache?.takeIf { it.key == cacheKey }?.let { return it.plan }
            return synchronized(productionCacheLock) {
                productionCache?.takeIf { it.key == cacheKey }?.plan
                    ?: buildUncached(teams, dataset, proceduralRosterFactory).also { plan ->
                        productionCache = ProductionCache(cacheKey, plan)
                    }
            }
        }
        return buildUncached(teams, dataset, proceduralRosterFactory)
    }

    private fun productionCacheKeyOrNull(
        teams: List<Team>,
        dataset: Fc26Dataset
    ): ProductionCacheKey? {
        if (teams.size < PRODUCTION_CACHE_MIN_TEAMS) return null
        var fingerprint = 1125899906842597L
        teams.sortedBy { it.id }.forEach { team ->
            fingerprint = fingerprint * 31L + team.id
            fingerprint = fingerprint * 31L + team.name.hashCode().toLong()
            fingerprint = fingerprint * 31L + team.country.hashCode().toLong()
            fingerprint = fingerprint * 31L + team.division.toLong()
            fingerprint = fingerprint * 31L + team.rating.toLong()
        }
        return ProductionCacheKey(
            assetSha256 = dataset.manifest.assetSha256,
            teamsFingerprint = fingerprint
        )
    }

    internal fun clearProductionCacheForTesting() {
        synchronized(productionCacheLock) {
            productionCache = null
        }
    }

    private fun buildUncached(
        teams: List<Team>,
        dataset: Fc26Dataset,
        proceduralRosterFactory: (Team) -> List<Player>
    ): Plan {
        require(teams.all { it.id > 0L }) { "FC26 seed exige teamId persistível > 0." }
        require(teams.map { it.id }.distinct().size == teams.size) { "FC26 target teams contêm ID duplicado." }

        val matches = Fc26ClubMatcher.match(dataset, teams)
        val matchesBySourceId = matches.associateBy { it.sourceClubTeamId }
        val matchedByTargetId = matches
            .filter { it.status == Fc26ClubMatchStatus.MATCHED }
            .groupBy { requireNotNull(it.targetTeamId) }

        val conflictingTargets = matchedByTargetId.filterValues { it.size > 1 }
        require(conflictingTargets.isEmpty()) {
            "FC26 club matching associou múltiplos source clubs ao mesmo Team: ${conflictingTargets.keys.sorted()}"
        }
        val sourceClubById = dataset.sourceClubs.associateBy { it.sourceClubTeamId }
        val matchByTargetId = matchedByTargetId.mapValues { it.value.single() }

        val players = mutableListOf<Player>()
        var mappedClubPlayerCount = 0
        var fallbackCount = 0
        var fallbackPlayerCount = 0

        teams.forEach { team ->
            val match = matchByTargetId[team.id]
            if (match != null) {
                val sourceClub = sourceClubById.getValue(match.sourceClubTeamId)
                val mapped = sourceClub.players.map { Fc26PlayerMapper.toPlayer(it, team.id) }
                players += mapped
                mappedClubPlayerCount += mapped.size
            } else {
                val fallback = Fc26FallbackRosterPolicy.select(proceduralRosterFactory(team))
                players += fallback
                fallbackCount += 1
                fallbackPlayerCount += fallback.size
            }
        }

        val freeAgents = dataset.freeAgents.map { Fc26PlayerMapper.toPlayer(it, null) }
        players += freeAgents

        val unmatchedClubPlayers = dataset.sourceClubs
            .asSequence()
            .filter { matchesBySourceId.getValue(it.sourceClubTeamId).status == Fc26ClubMatchStatus.UNMATCHED }
            .flatMap { it.players.asSequence() }
            .map { Fc26PlayerMapper.toPlayer(it, null).markFc26UnassignedSourceClub() }
            .toList()
        val ambiguousClubPlayers = dataset.sourceClubs
            .asSequence()
            .filter { matchesBySourceId.getValue(it.sourceClubTeamId).status == Fc26ClubMatchStatus.AMBIGUOUS }
            .flatMap { it.players.asSequence() }
            .map { Fc26PlayerMapper.toPlayer(it, null).markFc26UnassignedSourceClub() }
            .toList()
        val unassignedClubPlayers = unmatchedClubPlayers + ambiguousClubPlayers
        players += unassignedClubPlayers

        val loanResolution = Fc26LoanResolver.resolve(dataset, matches)
        val resolutionByPlayerId = loanResolution.audit.resolutions.associateBy { it.playerId }
        val materializedLoansByPlayerId = loanResolution.loans.associateBy { it.playerId }
        val playersWithLoanState = players.map { player ->
            val resolution = resolutionByPlayerId[player.id] ?: return@map player
            val loan = materializedLoansByPlayerId[player.id]
            if (loan != null) {
                require(player.teamId == loan.borrowerTeamId) {
                    "FC26 roster/borrower divergence for player=${player.id}: roster=${player.teamId} borrower=${loan.borrowerTeamId}"
                }
            }
            player.markFc26LoanResolution(resolution)
        }

        val clubCoverageImportedPlayers = mappedClubPlayerCount + freeAgents.size
        val clubCoverageUnresolvedPlayers = dataset.players.size - clubCoverageImportedPlayers
        val bulkImportedPlayers = clubCoverageImportedPlayers + unassignedClubPlayers.size
        require(bulkImportedPlayers == dataset.players.size) {
            "FC26 bulk import incompleto: imported=$bulkImportedPlayers dataset=${dataset.players.size}"
        }
        require(clubCoverageUnresolvedPlayers == unassignedClubPlayers.size) {
            "FC26 unresolved coverage divergiu do pool unassigned."
        }
        require(playersWithLoanState.size == players.size) {
            "FC26 loan materialization alterou a quantidade de jogadores."
        }

        val audit = loanResolution.audit
        val report = Fc26SeedReport(
            datasetPlayers = dataset.players.size,
            importedFc26Players = clubCoverageImportedPlayers,
            skippedDatasetPlayers = clubCoverageUnresolvedPlayers,
            bulkImportedFc26Players = bulkImportedPlayers,
            datasetClubs = dataset.sourceClubs.size,
            matchedClubs = matches.count { it.status == Fc26ClubMatchStatus.MATCHED },
            unmatchedClubs = matches.count { it.status == Fc26ClubMatchStatus.UNMATCHED },
            ambiguousClubs = matches.count { it.status == Fc26ClubMatchStatus.AMBIGUOUS },
            playersWithMappedClub = mappedClubPlayerCount,
            importedFreeAgents = freeAgents.size,
            importedUnassignedClubPlayers = unassignedClubPlayers.size,
            importedUnmatchedClubPlayers = unmatchedClubPlayers.size,
            importedAmbiguousClubPlayers = ambiguousClubPlayers.size,
            datasetLoanPlayers = audit.datasetLoanPlayers,
            successfullyMappedLoans = audit.resolvedLoans,
            unresolvedLoans = audit.rejectedLoans,
            resolvedLoans = audit.resolvedLoans,
            rejectedLoans = audit.rejectedLoans,
            ambiguousLoans = audit.ambiguousLoans,
            ownerNotFound = audit.ownerNotFound,
            borrowerNotFound = audit.borrowerNotFound,
            selfLoansRejected = audit.selfLoansRejected,
            duplicateLoans = audit.duplicateLoans,
            fallbackRostersRequired = fallbackCount,
            fallbackPlayersGenerated = fallbackPlayerCount,
            clubMatches = matches,
            loanResolutions = audit.resolutions
        )
        return Plan(players = playersWithLoanState, loans = loanResolution.loans, report = report)
    }

    private const val PRODUCTION_CACHE_MIN_TEAMS = 1_000
}
