package com.example.usecase

import com.example.data.DefaultData
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.Team
import kotlin.math.abs

/**
 * Mantém os clubes controlados pela CPU estruturalmente jogáveis durante todas as 48 semanas.
 *
 * A decisão de renovar/repor atletas é determinística para um mesmo estado persistido. Free Agents
 * são sempre reaproveitados antes da geração de um atleta de emergência.
 */
class CpuSquadManagementUseCase(private val repository: GameRepository) {

    companion object {
        const val MIN_SQUAD_SIZE = 16
        const val MAX_SQUAD_SIZE = 35
        private const val RENEWAL_WINDOW_WEEKS = 1
    }

    data class ManagementReport(
        val teamsChecked: Int,
        val contractsRenewed: Int,
        val freeAgentsSigned: Int,
        val emergencyPlayersGenerated: Int,
        val excessPlayersReleased: Int,
        val minimumRosterSize: Int,
        val maximumRosterSize: Int,
        val teamsWithoutGoalkeeper: Int,
        val invalidActiveLoans: Int
    )

    /**
     * Executar imediatamente antes do decremento semanal de contratos.
     * Renova apenas atletas cujo contrato expiraria neste tick e que a CPU tem motivo esportivo
     * objetivo para manter.
     */
    suspend fun renewCpuContractsBeforeWeeklyTick(): Int = repository.withTransaction {
        val save = repository.getGameSave()
        val cpuTeams = repository.getAllTeams()
            .filter { it.id != save?.playerTeamId && !it.isPlayerControlled }
            .sortedBy { it.id }
        val playersByTeam = repository.getAllPlayers().groupBy { it.teamId }
        val updates = mutableListOf<Player>()

        for (team in cpuTeams) {
            val roster = playersByTeam[team.id].orEmpty()
            if (roster.isEmpty()) continue

            val expiring = roster
                .filter {
                    !it.isOnLoan &&
                        it.contractDurationWeeks in 1..RENEWAL_WINDOW_WEEKS
                }
                .sortedWith(
                    compareByDescending<Player> { retentionScore(it, team) }
                        .thenBy { it.id }
                )
            if (expiring.isEmpty()) continue

            val survivorsWithoutRenewal = roster.size - expiring.size
            val mandatoryForSize = (MIN_SQUAD_SIZE - survivorsWithoutRenewal).coerceAtLeast(0)
            val onlyGoalkeeper = roster.filter { it.position == "GOL" }
                .singleOrNull()
                ?.takeIf { it in expiring }

            val mustRenewIds = expiring.take(mandatoryForSize).map { it.id }.toMutableSet()
            onlyGoalkeeper?.let { mustRenewIds.add(it.id) }

            for (player in expiring) {
                val sportingKeep = player.age <= 32 &&
                    (player.force >= team.rating - 5 || player.potential >= 85)
                if (player.id in mustRenewIds || sportingKeep) {
                    updates.add(
                        player.copy(
                            contractDurationWeeks = renewalDuration(player),
                            salary = player.calculateSalary(team.rating.toDouble()).coerceAtLeast(3_000L)
                        )
                    )
                }
            }
        }

        if (updates.isNotEmpty()) repository.updatePlayers(updates)
        updates.size
    }

    /**
     * Executar após o tick semanal de contratos. Reaproveita jogadores livres, garante goleiro,
     * repõe saídas e impede que um elenco CPU permaneça fora do intervalo 16..35.
     */
    suspend fun ensureCpuSquadIntegrity(): ManagementReport = repository.withTransaction {
        val save = repository.getGameSave()
        val cpuTeams = repository.getAllTeams()
            .filter { it.id != save?.playerTeamId && !it.isPlayerControlled }
            .sortedBy { it.id }

        val allPlayers = repository.getAllPlayers().toMutableList()
        val activeLoans = repository.getActiveLoans()
        val activeLoanByPlayer = activeLoans.associateBy { it.playerId }
        var freeAgents = allPlayers
            .filter { it.teamId == 0L && !it.isOnLoan }
            .sortedBy { it.id }
            .toMutableList()

        var nextPlayerId = ((allPlayers.maxOfOrNull { it.id } ?: 99_999L) + 1L)
            .coerceAtLeast(100_000L)
        val occupiedIds = allPlayers.map { it.id }.toMutableSet()

        fun nextCollisionSafeId(): Long {
            while (nextPlayerId in occupiedIds || nextPlayerId <= 0L) nextPlayerId++
            val result = nextPlayerId
            occupiedIds.add(result)
            nextPlayerId++
            return result
        }

        var signed = 0
        var generated = 0
        var released = 0
        var minRoster = Int.MAX_VALUE
        var maxRoster = 0

        for (team in cpuTeams) {
            var roster = repository.getPlayersByTeam(team.id).toMutableList()

            // Empréstimos ativos já respeitam o limite na contratação; excedentes próprios são os
            // primeiros a serem liberados se algum estado legado ainda exceder 35.
            if (roster.size > MAX_SQUAD_SIZE) {
                val releasable = roster
                    .filter { !it.isOnLoan }
                    .sortedWith(
                        compareBy<Player> { retentionScore(it, team) }
                            .thenByDescending { it.age }
                            .thenBy { it.id }
                    )
                val releaseCount = (roster.size - MAX_SQUAD_SIZE).coerceAtMost(releasable.size)
                val toRelease = releasable.take(releaseCount)
                if (toRelease.isNotEmpty()) {
                    val releasedPlayers = toRelease.map {
                        it.copy(
                            teamId = 0L,
                            originalTeamId = 0L,
                            contractDurationWeeks = 0,
                            salary = 0L,
                            isStarter = false,
                            isOnLoan = false,
                            loanWeeksRemaining = 0
                        )
                    }
                    repository.updatePlayers(releasedPlayers)
                    freeAgents.addAll(releasedPlayers)
                    released += releasedPlayers.size
                    roster = repository.getPlayersByTeam(team.id).toMutableList()
                }
            }

            fun signFreeAgent(requiredPosition: String? = null): Boolean {
                if (roster.size >= MAX_SQUAD_SIZE) return false
                val candidate = freeAgents
                    .asSequence()
                    .filter { requiredPosition == null || it.position == requiredPosition }
                    .sortedWith(
                        compareBy<Player> { abs(it.force - team.rating) }
                            .thenBy { it.age }
                            .thenByDescending { it.potential }
                            .thenBy { it.id }
                    )
                    .firstOrNull()
                    ?: return false

                val signedPlayer = candidate.copy(
                    teamId = team.id,
                    originalTeamId = 0L,
                    contractDurationWeeks = renewalDuration(candidate),
                    salary = candidate.calculateSalary(team.rating.toDouble()).coerceAtLeast(3_000L),
                    isStarter = false,
                    isOnLoan = false,
                    loanWeeksRemaining = 0,
                    moral = candidate.moral.coerceAtLeast(70)
                )
                repository.updatePlayer(signedPlayer)
                freeAgents.removeAll { it.id == candidate.id }
                roster.add(signedPlayer)
                signed++
                return true
            }

            fun generateEmergencyPlayer(requiredPosition: String? = null) {
                if (roster.size >= MAX_SQUAD_SIZE) return
                val template = DefaultData.generateRosterForTeam(
                    team.id,
                    team.rating,
                    team.name,
                    team.country
                ).asSequence()
                    .filter { requiredPosition == null || it.position == requiredPosition }
                    .sortedWith(compareByDescending<Player> { it.force }.thenBy { it.name })
                    .firstOrNull()
                    ?: return

                val generatedPlayer = template.copy(
                    id = nextCollisionSafeId(),
                    teamId = team.id,
                    originalTeamId = 0L,
                    contractDurationWeeks = renewalDuration(template),
                    salary = template.calculateSalary(team.rating.toDouble()).coerceAtLeast(3_000L),
                    isStarter = false,
                    isOnLoan = false,
                    loanWeeksRemaining = 0
                )
                repository.savePlayers(listOf(generatedPlayer))
                roster.add(generatedPlayer)
                generated++
            }

            if (roster.none { it.position == "GOL" }) {
                if (!signFreeAgent("GOL")) generateEmergencyPlayer("GOL")
            }

            while (roster.size < MIN_SQUAD_SIZE) {
                if (!signFreeAgent()) {
                    generateEmergencyPlayer()
                }
                if (roster.size < MIN_SQUAD_SIZE && freeAgents.isEmpty() && generated > allPlayers.size + cpuTeams.size * MIN_SQUAD_SIZE) {
                    break
                }
            }

            minRoster = minOf(minRoster, roster.size)
            maxRoster = maxOf(maxRoster, roster.size)
        }

        val refreshedPlayers = repository.getAllPlayers()
        val refreshedByTeam = refreshedPlayers.groupBy { it.teamId }
        val teamsWithoutGoalkeeper = cpuTeams.count { team ->
            refreshedByTeam[team.id].orEmpty().none { it.position == "GOL" }
        }
        val invalidLoans = activeLoanByPlayer.values.count { loan ->
            val player = refreshedPlayers.firstOrNull { it.id == loan.playerId }
            player == null ||
                !player.isOnLoan ||
                player.teamId != loan.borrowerTeamId ||
                player.originalTeamId != loan.ownerTeamId ||
                loan.remainingWeeks <= 0
        }

        ManagementReport(
            teamsChecked = cpuTeams.size,
            contractsRenewed = 0,
            freeAgentsSigned = signed,
            emergencyPlayersGenerated = generated,
            excessPlayersReleased = released,
            minimumRosterSize = if (cpuTeams.isEmpty()) 0 else minRoster,
            maximumRosterSize = if (cpuTeams.isEmpty()) 0 else maxRoster,
            teamsWithoutGoalkeeper = teamsWithoutGoalkeeper,
            invalidActiveLoans = invalidLoans
        )
    }

    suspend fun processWeeklyAfterContracts(): ManagementReport = ensureCpuSquadIntegrity()

    private fun retentionScore(player: Player, team: Team): Int {
        val agePenalty = (player.age - 29).coerceAtLeast(0) * 3
        val goalkeeperBonus = if (player.position == "GOL") 20 else 0
        val levelFit = 20 - abs(player.force - team.rating).coerceAtMost(20)
        return player.force * 3 + player.potential + goalkeeperBonus + levelFit - agePenalty
    }

    private fun renewalDuration(player: Player): Int = when {
        player.age <= 24 -> 156
        player.age <= 29 -> 104
        player.age <= 33 -> 78
        else -> 52
    }
}
