package com.example.usecase

import com.example.data.DefaultData
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.Team
import com.example.data.WeeklyRenewalCandidate
import com.example.data.WeeklyRenewalDecision
import com.example.data.applyWeeklyRenewals
import com.example.data.getMaxPersistedPlayerId
import com.example.data.getWeeklyRenewalCandidates
import com.example.data.getWeeklyRosterAggregates
import com.example.data.isFc26UnassignedSourceClub
import kotlin.math.abs

/**
 * Mantém os clubes controlados pela CPU estruturalmente jogáveis durante todas as 48 semanas.
 *
 * Phase 9.13 removes full-table Player materialization from the canonical weekly path. Renewal uses
 * lightweight scalar SQL projections; squad integrity uses roster aggregates and only loads full
 * Player entities for clubs that are actually unhealthy after the contract tick.
 */
class CpuSquadManagementUseCase(private val repository: GameRepository) {

    companion object {
        const val MIN_SQUAD_SIZE = 16
        const val MAX_SQUAD_SIZE = 35
        private const val RENEWAL_WINDOW_WEEKS = 1
    }

    data class ManagementReport(
        val teamsChecked: Int,
        val freeAgentsSigned: Int,
        val emergencyPlayersGenerated: Int,
        val excessPlayersReleased: Int,
        val minimumRosterSize: Int,
        val maximumRosterSize: Int,
        val teamsWithoutGoalkeeper: Int,
        val invalidActiveLoans: Int
    )

    private fun Team.isManagedCpuClub(playerTeamId: Long?): Boolean =
        id != playerTeamId &&
            !isPlayerControlled &&
            !country.equals("Mundial", ignoreCase = true)

    suspend fun renewCpuContractsBeforeWeeklyTick(): Int = repository.withTransaction {
        val save = repository.getGameSave()
        val cpuTeams = repository.getAllTeams()
            .asSequence()
            .filter { it.isManagedCpuClub(save?.playerTeamId) }
            .sortedBy { it.id }
            .toList()
        if (cpuTeams.isEmpty()) return@withTransaction 0

        val aggregateByTeam = repository.getWeeklyRosterAggregates()
        val expiringByTeam = repository.getWeeklyRenewalCandidates(RENEWAL_WINDOW_WEEKS)
            .groupBy { it.teamId }
        val decisions = ArrayList<WeeklyRenewalDecision>()

        for (team in cpuTeams) {
            val expiring = expiringByTeam[team.id].orEmpty()
                .sortedWith(
                    compareByDescending<WeeklyRenewalCandidate> { retentionScore(it, team) }
                        .thenBy { it.id }
                )
            if (expiring.isEmpty()) continue

            val aggregate = aggregateByTeam[team.id]
            val rosterSize = aggregate?.rosterSize ?: 0
            val survivorsWithoutRenewal = rosterSize - expiring.size
            val mandatoryForSize = (MIN_SQUAD_SIZE - survivorsWithoutRenewal).coerceAtLeast(0)
            val onlyGoalkeeper = if (aggregate?.goalkeeperCount == 1) {
                expiring.singleOrNull { it.position == "GOL" }
            } else {
                null
            }

            val mustRenewIds = expiring.take(mandatoryForSize).mapTo(mutableSetOf()) { it.id }
            onlyGoalkeeper?.let { mustRenewIds.add(it.id) }

            for (player in expiring) {
                val sportingKeep = player.age <= 32 &&
                    (player.force >= team.rating - 5 || player.potential >= 85)
                if (player.id in mustRenewIds || sportingKeep) {
                    decisions += WeeklyRenewalDecision(
                        playerId = player.id,
                        contractDurationWeeks = renewalDuration(player.age),
                        salary = calculateSalary(player.force, team.rating)
                    )
                }
            }
        }

        repository.applyWeeklyRenewals(decisions)
    }

    suspend fun ensureCpuSquadIntegrity(): ManagementReport = repository.withTransaction {
        val save = repository.getGameSave()
        val allTeams = repository.getAllTeams()
        val cpuTeams = allTeams
            .filter { it.isManagedCpuClub(save?.playerTeamId) }
            .sortedBy { it.id }
        val aggregateByTeam = repository.getWeeklyRosterAggregates()
        val activeLoans = repository.getActiveLoans()

        if (cpuTeams.isEmpty()) {
            return@withTransaction ManagementReport(0, 0, 0, 0, 0, 0, 0, validateActiveLoans(activeLoans, allTeams))
        }

        val unhealthyTeamIds = cpuTeams.asSequence()
            .filter { team ->
                val aggregate = aggregateByTeam[team.id]
                val size = aggregate?.rosterSize ?: 0
                val goalkeepers = aggregate?.goalkeeperCount ?: 0
                size < MIN_SQUAD_SIZE || size > MAX_SQUAD_SIZE || goalkeepers == 0
            }
            .map { it.id }
            .toSet()

        val rosters = unhealthyTeamIds.associateWith { teamId ->
            repository.getPlayersByTeam(teamId).toMutableList()
        }.toMutableMap()

        val freeAgents = if (unhealthyTeamIds.isNotEmpty()) {
            repository.getFreeAgents()
                .asSequence()
                .filter { !it.isOnLoan && !it.isFc26UnassignedSourceClub() }
                .sortedBy { it.id }
                .toMutableList()
        } else {
            mutableListOf()
        }

        val pendingUpdates = linkedMapOf<Long, Player>()
        val generatedPlayers = mutableListOf<Player>()
        var nextPlayerId = if (unhealthyTeamIds.isEmpty()) 100_000L else
            (repository.getMaxPersistedPlayerId() + 1L).coerceAtLeast(100_000L)
        val occupiedGeneratedIds = mutableSetOf<Long>()

        fun nextCollisionSafeId(): Long {
            while (nextPlayerId <= 0L || nextPlayerId in occupiedGeneratedIds) nextPlayerId++
            return nextPlayerId.also {
                occupiedGeneratedIds.add(it)
                nextPlayerId++
            }
        }

        var signed = 0
        var generated = 0
        var released = 0
        var minRoster = Int.MAX_VALUE
        var maxRoster = 0
        var teamsWithoutGoalkeeper = 0

        for (team in cpuTeams) {
            if (team.id !in unhealthyTeamIds) {
                val aggregate = aggregateByTeam[team.id]
                val size = aggregate?.rosterSize ?: 0
                minRoster = minOf(minRoster, size)
                maxRoster = maxOf(maxRoster, size)
                if ((aggregate?.goalkeeperCount ?: 0) == 0) teamsWithoutGoalkeeper++
                continue
            }

            val roster = rosters.getOrPut(team.id) { mutableListOf() }

            if (roster.size > MAX_SQUAD_SIZE) {
                val releaseCount = roster.size - MAX_SQUAD_SIZE
                val releasable = roster
                    .filter { !it.isOnLoan }
                    .sortedWith(
                        compareBy<Player> { retentionScore(it, team) }
                            .thenByDescending { it.age }
                            .thenBy { it.id }
                    )
                val toRelease = releasable.take(releaseCount)
                if (toRelease.isNotEmpty()) {
                    val releaseIds = toRelease.map { it.id }.toSet()
                    roster.removeAll { it.id in releaseIds }
                    toRelease.forEach { player ->
                        val freeAgent = player.copy(
                            teamId = null,
                            originalTeamId = null,
                            contractDurationWeeks = 0,
                            salary = 0L,
                            isStarter = false,
                            isOnLoan = false,
                            loanWeeksRemaining = 0
                        )
                        pendingUpdates[player.id] = freeAgent
                        freeAgents.add(freeAgent)
                    }
                    released += toRelease.size
                }
            }

            fun signFreeAgent(requiredPosition: String? = null): Boolean {
                if (roster.size >= MAX_SQUAD_SIZE) return false
                val candidate = freeAgents
                    .asSequence()
                    .filter { requiredPosition == null || it.position == requiredPosition }
                    .minWithOrNull(
                        compareBy<Player> { abs(it.force - team.rating) }
                            .thenBy { it.age }
                            .thenByDescending { it.potential }
                            .thenBy { it.id }
                    ) ?: return false

                val signedPlayer = candidate.copy(
                    teamId = team.id,
                    originalTeamId = null,
                    contractDurationWeeks = renewalDuration(candidate.age),
                    salary = calculateSalary(candidate.force, team.rating),
                    isStarter = false,
                    isOnLoan = false,
                    loanWeeksRemaining = 0,
                    moral = candidate.moral.coerceAtLeast(70)
                )
                pendingUpdates[candidate.id] = signedPlayer
                freeAgents.removeAll { it.id == candidate.id }
                roster.add(signedPlayer)
                signed++
                return true
            }

            fun generateEmergencyPlayer(requiredPosition: String? = null): Boolean {
                if (roster.size >= MAX_SQUAD_SIZE) return false
                val template = DefaultData.generateRosterForTeam(
                    team.id,
                    team.rating,
                    team.name,
                    team.country
                ).asSequence()
                    .filter { requiredPosition == null || it.position == requiredPosition }
                    .sortedWith(compareByDescending<Player> { it.force }.thenBy { it.name })
                    .firstOrNull()
                    ?: return false

                val generatedPlayer = template.copy(
                    id = nextCollisionSafeId(),
                    teamId = team.id,
                    originalTeamId = null,
                    contractDurationWeeks = renewalDuration(template.age),
                    salary = calculateSalary(template.force, team.rating),
                    isStarter = false,
                    isOnLoan = false,
                    loanWeeksRemaining = 0
                )
                generatedPlayers.add(generatedPlayer)
                roster.add(generatedPlayer)
                generated++
                return true
            }

            if (roster.none { it.position == "GOL" }) {
                if (!signFreeAgent("GOL")) {
                    check(generateEmergencyPlayer("GOL")) {
                        "Não foi possível gerar goleiro de emergência para o clube ${team.id}."
                    }
                }
            }

            while (roster.size < MIN_SQUAD_SIZE) {
                if (!signFreeAgent()) {
                    check(generateEmergencyPlayer()) {
                        "Não foi possível completar o elenco CPU do clube ${team.id}."
                    }
                }
            }

            minRoster = minOf(minRoster, roster.size)
            maxRoster = maxOf(maxRoster, roster.size)
            if (roster.none { it.position == "GOL" }) teamsWithoutGoalkeeper++
        }

        if (pendingUpdates.isNotEmpty()) repository.updatePlayers(pendingUpdates.values.toList())
        if (generatedPlayers.isNotEmpty()) repository.savePlayers(generatedPlayers)

        ManagementReport(
            teamsChecked = cpuTeams.size,
            freeAgentsSigned = signed,
            emergencyPlayersGenerated = generated,
            excessPlayersReleased = released,
            minimumRosterSize = minRoster,
            maximumRosterSize = maxRoster,
            teamsWithoutGoalkeeper = teamsWithoutGoalkeeper,
            invalidActiveLoans = validateActiveLoans(activeLoans, allTeams)
        )
    }

    suspend fun processWeeklyAfterContracts(): ManagementReport = ensureCpuSquadIntegrity()

    private suspend fun validateActiveLoans(
        activeLoans: List<com.example.data.PlayerLoan>,
        allTeams: List<Team>
    ): Int {
        if (activeLoans.isEmpty()) return 0
        val duplicateActiveLoans = activeLoans.size - activeLoans.map { it.playerId }.toSet().size
        val validTeamIds = allTeams.mapTo(mutableSetOf()) { it.id }
        var invalidLoanRows = 0
        for (loan in activeLoans) {
            val player = repository.getPlayer(loan.playerId)
            if (player == null ||
                !player.isOnLoan ||
                player.teamId != loan.borrowerTeamId ||
                player.originalTeamId != loan.ownerTeamId ||
                loan.ownerTeamId !in validTeamIds ||
                loan.borrowerTeamId !in validTeamIds ||
                loan.remainingWeeks <= 0
            ) {
                invalidLoanRows++
            }
        }
        return duplicateActiveLoans + invalidLoanRows
    }

    private fun retentionScore(player: WeeklyRenewalCandidate, team: Team): Int {
        val agePenalty = (player.age - 29).coerceAtLeast(0) * 3
        val goalkeeperBonus = if (player.position == "GOL") 20 else 0
        val levelFit = 20 - abs(player.force - team.rating).coerceAtMost(20)
        return player.force * 3 + player.potential + goalkeeperBonus + levelFit - agePenalty
    }

    private fun retentionScore(player: Player, team: Team): Int {
        val agePenalty = (player.age - 29).coerceAtLeast(0) * 3
        val goalkeeperBonus = if (player.position == "GOL") 20 else 0
        val levelFit = 20 - abs(player.force - team.rating).coerceAtMost(20)
        return player.force * 3 + player.potential + goalkeeperBonus + levelFit - agePenalty
    }

    private fun calculateSalary(force: Int, teamRating: Int): Long =
        ((teamRating / 100.0) * force * 1500.0).toLong().coerceAtLeast(3_000L)

    private fun renewalDuration(age: Int): Int = when {
        age <= 24 -> 156
        age <= 29 -> 104
        age <= 33 -> 78
        else -> 52
    }
}
