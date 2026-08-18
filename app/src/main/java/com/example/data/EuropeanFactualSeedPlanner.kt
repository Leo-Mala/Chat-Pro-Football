package com.example.data

/**
 * Planeja a materialização de elencos factuais sem acoplar a source of truth ao ViewModel/Room.
 *
 * Clubes com snapshot factual `GAMEPLAY_READY` recebem o elenco factual. Clubes ainda sem cobertura
 * usam explicitamente o fallback procedural fornecido pelo chamador. Empréstimos só são
 * materializados quando proprietário e tomador existem no conjunto de times do novo save.
 *
 * Isso permite testar a migração progressiva antes de alterar o seed central de `DefaultData`.
 */
object EuropeanFactualSeedPlanner {

    data class BlockedLoan(
        val playerId: Long,
        val playerName: String,
        val reason: String
    )

    data class Plan(
        val players: List<Player>,
        val loans: List<PlayerLoan>,
        val factualSquadTeamIds: Set<Long>,
        val proceduralFallbackTeamIds: Set<Long>,
        val blockedLoans: List<BlockedLoan>
    ) {
        init {
            val playerIds = players.map { it.id }
            require(playerIds.distinct().size == playerIds.size) {
                "Seed planejado contém playerId duplicado."
            }
            require(factualSquadTeamIds.intersect(proceduralFallbackTeamIds).isEmpty()) {
                "Um clube não pode usar elenco factual e fallback procedural simultaneamente."
            }
        }
    }

    fun build(
        teams: List<Team>,
        squadCatalog: EuropeanRealSquadCatalog = EuropeanRealSquads.catalog,
        loanCatalog: EuropeanRealLoanCatalog = EuropeanRealLoans.catalog,
        proceduralRosterFactory: (Team) -> List<Player>
    ): Plan {
        val teamsById = teams.associateBy { it.id }
        require(teamsById.size == teams.size) { "Seed de clubes contém teamId duplicado." }

        val players = mutableListOf<Player>()
        val factualTeamIds = linkedSetOf<Long>()
        val proceduralTeamIds = linkedSetOf<Long>()

        teams.forEach { team ->
            val snapshot = squadCatalog.find(team.country, team.name)
            if (snapshot != null && snapshot.coverage() == EuropeanSquadCoverage.GAMEPLAY_READY_FACTUAL_SNAPSHOT) {
                require(snapshot.teamId == team.id) {
                    "Clube factual ${team.country}/${team.name} chegou com teamId divergente: ${team.id} != ${snapshot.teamId}"
                }
                players += snapshot.toGameplayPlayers(team.rating)
                factualTeamIds += team.id
            } else {
                players += proceduralRosterFactory(team)
                proceduralTeamIds += team.id
            }
        }

        val blockedLoans = mutableListOf<BlockedLoan>()
        val materializedLoans = mutableListOf<PlayerLoan>()
        loanCatalog.all().forEach { loan ->
            val ownerExists = teamsById.containsKey(loan.ownerTeamId)
            val borrower = teamsById[loan.borrowerTeamId]
            if (!ownerExists || borrower == null) {
                val missing = buildList {
                    if (!ownerExists) add("owner ${loan.ownerCountry}/${loan.ownerClubName}")
                    if (borrower == null) add("borrower ${loan.borrowerCountry}/${loan.borrowerClubName}")
                }.joinToString(" + ")
                blockedLoans += BlockedLoan(
                    playerId = loan.player.stableId,
                    playerName = loan.player.fullName,
                    reason = "missing $missing in team seed"
                )
                return@forEach
            }

            require(players.none { it.id == loan.player.stableId }) {
                "Jogador emprestado ${loan.player.fullName} já foi materializado em outro elenco."
            }
            players += loan.toBorrowerPlayer(borrower.rating)
            materializedLoans += loan.toPlayerLoan()
        }

        return Plan(
            players = players,
            loans = materializedLoans,
            factualSquadTeamIds = factualTeamIds,
            proceduralFallbackTeamIds = proceduralTeamIds,
            blockedLoans = blockedLoans
        )
    }
}
