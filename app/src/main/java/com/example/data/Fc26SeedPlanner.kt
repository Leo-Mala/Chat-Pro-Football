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
    /** Phase <=10.3 compatibility counter kept frozen so historical reports remain comparable. */
    val successfullyMappedLoans: Int,
    /** Phase <=10.3 compatibility counter kept frozen so historical reports remain comparable. */
    val unresolvedLoans: Int,
    /** Phase 10.4 authoritative materialization count. */
    val resolvedLoans: Int,
    /** Phase 10.4 authoritative fail-closed rejection count. */
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

    fun build(
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

        // Dois source clubs nunca podem ser silenciosamente associados ao mesmo Team.
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

        // Free agents factuais entram uma única vez e preservam o contrato canônico teamId=null.
        val freeAgents = dataset.freeAgents.map { Fc26PlayerMapper.toPlayer(it, null) }
        players += freeAgents

        // Jogadores cujo clube FC26 ainda não possui target seguro também entram no jogo. Eles NÃO
        // são reclassificados como free agents factuais: teamId=null significa apenas "unassigned"
        // no universo atual. A identidade do clube de origem continua preservada em atributosJson.
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

        // Resolve ownership only after borrower/current-club identities have been canonicalized.
        // Snapshot dates are unavailable, so resolved relations use the explicit open-ended FC26
        // sentinel defined by Fc26LoanPolicy and are never given a fabricated duration.
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

        // Preserve the historical A1/A2/A3 coverage counters so their audit reports remain
        // comparable. bulkImportedFc26Players is the actual number inserted.
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
            // Historical report compatibility only. Phase 10.4 consumers must use resolvedLoans/rejectedLoans.
            successfullyMappedLoans = 0,
            unresolvedLoans = audit.datasetLoanPlayers,
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
}
