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
     * Executar após o tick semanal de contratos. A leitura global é feita uma única vez; mudanças
     * de vínculo são calculadas em memória e gravadas em lote, evitando uma consulta Room por clube.
     */
    suspend fun ensureCpuSquadIntegrity(): ManagementReport = repository.withTransaction {
        val save = repository.getGameSave()
        val cpuTeams = repository.getAllTeams()
            .filter { it.id != save?.playerTeamId && !it.isPlayerControlled }
            .sortedBy { it.id }

        val allPlayers = repository.getAllPlayers()
        val activeLoans = repository.getActiveLoans()
        val rosters = allPlayers
            .filter { it.teamId != null }
            .groupBy { it.teamId }
            .mapValues { (_, players) -> players.toMutableList() }
            .toMutableMap()
        val freeAgents = allPlayers
            .filter { it.teamId == null && !it.isOnLoan }
            .sortedBy { it.id }
            .toMutableList()
        val pendingUpdates = linkedMapOf<Long, Player>()
        val generatedPlayers = mutableListOf<Player>()

        var nextPlayerId = ((allPlayers.maxOfOrNull { it.id } ?: 99_999L) + 1L)
            .coerceAtLeast(100_000L)
        val occupiedIds = allPlayers.map { it.id }.toMutableSet()

        fun nextCollisionSafeId(): Long {
            while (nextPlayerId in occupiedIds || nextPlayerId <= 0L) nextPlayerId++
            return nextPlayerId.also {
                occupiedIds.add(it)
                nextPlayerId++
            }
        }

        var signed = 0
        var generated = 0
        var released = 0
        var minRoster = Int.MAX_VALUE
        var maxRoster = 0

        for (team in cpuTeams) {
            val roster = rosters.getOrPut(team.id) { mutableListOf() }

            // Empréstimos ativos normais já respeitam o limite na contratação. Em estados legados
            // acima de 35, liberamos primeiro atletas próprios; empréstimos ativos são preservados.
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
                    contractDurationWeeks = renewalDuration(candidate),
                    salary = candidate.calculateSalary(team.rating.toDouble()).coerceAtLeast(3_000L),
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
                    contractDurationWeeks = renewalDuration(template),
                    salary = template.calculateSalary(team.rating.toDouble()).coerceAtLeast(3_000L),
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
        }

        if (pendingUpdates.isNotEmpty()) {
            repository.updatePlayers(pendingUpdates.values.toList())
        }
        if (generatedPlayers.isNotEmpty()) {
            repository.savePlayers(generatedPlayers)
        }

        val refreshedPlayers = repository.getAllPlayers()
        val refreshedByTeam = refreshedPlayers.groupBy { it.teamId }
        val duplicateActiveLoans = activeLoans.size - activeLoans.map { it.playerId }.toSet().size
        val invalidLoanRows = activeLoans.count { loan ->
            val player = refreshedPlayers.firstOrNull { it.id == loan.playerId }
            player == null ||
                !player.isOnLoan ||
                player.teamId != loan.borrowerTeamId ||
                player.originalTeamId != loan.ownerTeamId ||
                loan.ownerTeamId !in rosters.keys ||
                loan.borrowerTeamId !in rosters.keys ||
                loan.remainingWeeks <= 0
        }
        val teamsWithoutGoalkeeper = cpuTeams.count { team ->
            refreshedByTeam[team.id].orEmpty().none { it.position == "GOL" }
        }

        ManagementReport(
            teamsChecked = cpuTeams.size,
            freeAgentsSigned = signed,
            emergencyPlayersGenerated = generated,
            excessPlayersReleased = released,
            minimumRosterSize = if (cpuTeams.isEmpty()) 0 else minRoster,
            maximumRosterSize = if (cpuTeams.isEmpty()) 0 else maxRoster,
            teamsWithoutGoalkeeper = teamsWithoutGoalkeeper,
            invalidActiveLoans = duplicateActiveLoans + invalidLoanRows
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
