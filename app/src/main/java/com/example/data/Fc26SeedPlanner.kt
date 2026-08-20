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
    /** ACTIVE gameplay loans. Remains zero while the source snapshot has no duration/end field. */
    val successfullyMappedLoans: Int,
    /** Loan-marked source players that still cannot be represented as an ACTIVE timed PlayerLoan. */
    val unresolvedLoans: Int,
    /** Phase 9.14B: borrower + owner identity are safely known, but duration is absent from source. */
    val resolvedLoanIdentitiesUndated: Int,
    /** Phase 9.14B: borrower and/or owner identity is still unresolved. */
    val unresolvedLoanIdentities: Int,
    /** ACTIVE PlayerLoan rows created from the FC26 snapshot. Must remain zero without duration. */
    val materializedActiveLoans: Int,
    val fallbackRostersRequired: Int,
    /** Phase 9.14: actual procedural players retained after the FC26 fallback roster policy. */
    val fallbackPlayersGenerated: Int,
    val clubMatches: List<Fc26ClubMatch>
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
        // no universo atual. A identidade do clube de origem continua preservada em atributosJson
        // (sourceClubTeamId/sourceClubName/league), permitindo associação futura sem reimportação.
        // O marcador assignmentStatus separa esse estado de um free agent verdadeiro sem exigir
        // coluna/migração Room e sem tocar em overall, potential ou Atributos.
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

        // Phase 9.14B intentionally separates factual loan identity from timed gameplay lifecycle.
        // Every source loan marker receives a persistent metadata envelope with borrower/owner
        // resolution and unresolved reason. Because FC26 exposes no loan duration/end date, no
        // Player.isOnLoan/originalTeamId/loanWeeksRemaining state and no ACTIVE PlayerLoan are set.
        val loanAudit = Fc26LoanIdentityResolver.audit(dataset, teams)
        val loanResolutionByPlayerId = loanAudit.resolutions.associateBy { it.stablePlayerId }
        require(loanResolutionByPlayerId.size == dataset.manifest.loanedPlayerCount)
        val finalizedPlayers = players.map { player ->
            loanResolutionByPlayerId[player.id]?.let { resolution ->
                player.markFc26LoanIdentity(resolution)
            } ?: player
        }
        require(finalizedPlayers.count { it.fc26LoanIdentityMetadataOrNull() != null } == dataset.manifest.loanedPlayerCount) {
            "FC26 loan identity metadata incompleto no seed."
        }
        require(finalizedPlayers.none { player ->
            player.fc26LoanIdentityMetadataOrNull() != null &&
                (player.isOnLoan || player.originalTeamId != null || player.loanWeeksRemaining != 0)
        }) {
            "FC26 undated loan metadata não pode ativar lifecycle de empréstimo sem duração factual."
        }

        // Preserve the historical A1/A2/A3 coverage counters so their audit reports remain
        // comparable. The new bulkImportedFc26Players field is the actual number inserted.
        val clubCoverageImportedPlayers = mappedClubPlayerCount + freeAgents.size
        val clubCoverageUnresolvedPlayers = dataset.players.size - clubCoverageImportedPlayers
        val bulkImportedPlayers = clubCoverageImportedPlayers + unassignedClubPlayers.size
        require(bulkImportedPlayers == dataset.players.size) {
            "FC26 bulk import incompleto: imported=$bulkImportedPlayers dataset=${dataset.players.size}"
        }
        require(clubCoverageUnresolvedPlayers == unassignedClubPlayers.size) {
            "FC26 unresolved coverage divergiu do pool unassigned."
        }

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
            datasetLoanPlayers = dataset.manifest.loanedPlayerCount,
            successfullyMappedLoans = 0,
            // Identity can be partially/fully audited, but the source still cannot create a timed
            // ACTIVE PlayerLoan because duration/end date is absent for all 1,325 loan markers.
            unresolvedLoans = dataset.manifest.loanedPlayerCount,
            resolvedLoanIdentitiesUndated = loanAudit.identityResolvedUndated,
            unresolvedLoanIdentities = loanAudit.unresolvedIdentity,
            materializedActiveLoans = 0,
            fallbackRostersRequired = fallbackCount,
            fallbackPlayersGenerated = fallbackPlayerCount,
            clubMatches = matches
        )
        return Plan(players = finalizedPlayers, loans = emptyList(), report = report)
    }
}
