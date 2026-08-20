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
    val successfullyMappedLoans: Int,
    val unresolvedLoans: Int,
    val fallbackRostersRequired: Int,
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

        teams.forEach { team ->
            val match = matchByTargetId[team.id]
            if (match != null) {
                val sourceClub = sourceClubById.getValue(match.sourceClubTeamId)
                val mapped = sourceClub.players.map { Fc26PlayerMapper.toPlayer(it, team.id) }
                players += mapped
                mappedClubPlayerCount += mapped.size
            } else {
                players += proceduralRosterFactory(team)
                fallbackCount += 1
            }
        }

        // Free agents factuais entram uma única vez e preservam o contrato canônico teamId=null.
        val freeAgents = dataset.freeAgents.map { Fc26PlayerMapper.toPlayer(it, null) }
        players += freeAgents

        // Jogadores cujo clube FC26 ainda não possui target seguro também entram no jogo. Eles NÃO
        // são reclassificados como free agents factuais: teamId=null significa apenas "unassigned"
        // no universo atual. A identidade do clube de origem continua preservada em atributosJson
        // (sourceClubTeamId/sourceClubName/league), permitindo associação futura sem reimportação.
        val unmatchedClubPlayers = dataset.sourceClubs
            .asSequence()
            .filter { matchesBySourceId.getValue(it.sourceClubTeamId).status == Fc26ClubMatchStatus.UNMATCHED }
            .flatMap { it.players.asSequence() }
            .map { Fc26PlayerMapper.toPlayer(it, null) }
            .toList()
        val ambiguousClubPlayers = dataset.sourceClubs
            .asSequence()
            .filter { matchesBySourceId.getValue(it.sourceClubTeamId).status == Fc26ClubMatchStatus.AMBIGUOUS }
            .flatMap { it.players.asSequence() }
            .map { Fc26PlayerMapper.toPlayer(it, null) }
            .toList()
        val unassignedClubPlayers = unmatchedClubPlayers + ambiguousClubPlayers
        players += unassignedClubPlayers

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
            // O snapshot não informa duração suficiente para reconstruir PlayerLoan com segurança.
            // Portanto todos os empréstimos de origem permanecem pendentes nesta fase, inclusive os
            // jogadores cujo clube atual ainda não existe no universo Pro Football.
            unresolvedLoans = dataset.manifest.loanedPlayerCount,
            fallbackRostersRequired = fallbackCount,
            clubMatches = matches
        )
        return Plan(players = players, loans = emptyList(), report = report)
    }
}
