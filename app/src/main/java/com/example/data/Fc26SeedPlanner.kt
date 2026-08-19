package com.example.data

data class Fc26SeedReport(
    val datasetPlayers: Int,
    val importedFc26Players: Int,
    val skippedDatasetPlayers: Int,
    val datasetClubs: Int,
    val matchedClubs: Int,
    val unmatchedClubs: Int,
    val ambiguousClubs: Int,
    val playersWithMappedClub: Int,
    val importedFreeAgents: Int,
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

        val importedStableIds = players.asSequence().filter { StableRealPlayerIdentity.isRealPlayerId(it.id) }.map { it.id }.toSet()
        val importedDatasetPlayers = mappedClubPlayerCount + freeAgents.size
        val unresolvedImportedLoans = dataset.players.count { it.stableId in importedStableIds && !it.clubLoanedFrom.isNullOrBlank() }

        val report = Fc26SeedReport(
            datasetPlayers = dataset.players.size,
            importedFc26Players = importedDatasetPlayers,
            skippedDatasetPlayers = dataset.players.size - importedDatasetPlayers,
            datasetClubs = dataset.sourceClubs.size,
            matchedClubs = matches.count { it.status == Fc26ClubMatchStatus.MATCHED },
            unmatchedClubs = matches.count { it.status == Fc26ClubMatchStatus.UNMATCHED },
            ambiguousClubs = matches.count { it.status == Fc26ClubMatchStatus.AMBIGUOUS },
            playersWithMappedClub = mappedClubPlayerCount,
            importedFreeAgents = freeAgents.size,
            datasetLoanPlayers = dataset.manifest.loanedPlayerCount,
            successfullyMappedLoans = 0,
            unresolvedLoans = unresolvedImportedLoans,
            fallbackRostersRequired = fallbackCount,
            clubMatches = matches
        )
        return Plan(players = players, loans = emptyList(), report = report)
    }
}
