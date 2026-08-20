package com.example.usecase

import com.example.data.DefaultData
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.Team
import com.example.data.isFc26UnassignedSourceClub
import kotlin.math.abs

/**
 * Mantém os clubes controlados pela CPU estruturalmente jogáveis durante todas as 48 semanas.
 *
 * A decisão de renovar/repor atletas é determinística para um mesmo estado persistido. Free Agents
 * são sempre reaproveitados antes da geração de um atleta de emergência.
 *
 * Registros Team com country="Mundial" são participantes virtuais/legados persistidos apenas para
 * integridade referencial de fixtures. Eles não representam clubes domésticos ativos e, portanto,
 * não recebem renovação nem geração automática de elenco.
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
     * O fluxo semanal chama renovação -> tick de contratos -> integridade no mesmo objeto.
     * Com dezenas de milhares de jogadores, reler toda a tabela em cada uma dessas etapas domina o
     * tempo da rodada. Guardamos somente um snapshot efêmero em memória para a terceira etapa.
     * Nada é persistido fora do Room e o snapshot nunca atravessa uma instância/use case semanal.
     */
    private var predictedPlayersAfterContractTick: List<Player>? = null
    private var weeklyTeamsSnapshot: List<Team>? = null

    private fun Team.isManagedCpuClub(playerTeamId: Long?): Boolean =
        id != playerTeamId &&
            !isPlayerControlled &&
            !country.equals("Mundial", ignoreCase = true)

    suspend fun renewCpuContractsBeforeWeeklyTick(): Int = repository.withTransaction {
        val save = repository.getGameSave()
        val allTeams = repository.getAllTeams()
        val cpuTeams = allTeams
            .filter { it.isManagedCpuClub(save?.playerTeamId) }
            .sortedBy { it.id }
        val allPlayers = repository.getAllPlayers()
        val updates = buildRenewalUpdates(allPlayers, cpuTeams)

        if (updates.isNotEmpty()) repository.updatePlayers(updates.values.toList())

        // ProcessTransfersUseCase aplica exatamente o tick logo em seguida. Antecipar somente o
        // estado resultante em memória permite que a integridade CPU reutilize o mesmo universo sem
        // uma nova leitura integral do Room depois do tick.
        val renewedPlayers = if (updates.isEmpty()) {
            allPlayers
        } else {
            allPlayers.map { updates[it.id] ?: it }
        }
        predictedPlayersAfterContractTick = renewedPlayers.map(::predictWeeklyContractTick)
        weeklyTeamsSnapshot = allTeams
        updates.size
    }

    suspend fun ensureCpuSquadIntegrity(): ManagementReport = repository.withTransaction {
        predictedPlayersAfterContractTick = null
        weeklyTeamsSnapshot = null
        val save = repository.getGameSave()
        val allTeams = repository.getAllTeams()
        val allPlayers = repository.getAllPlayers()
        val activeLoans = repository.getActiveLoans()
        ensureCpuSquadIntegrityFromSnapshot(
            playerTeamId = save?.playerTeamId,
            allTeams = allTeams,
            allPlayers = allPlayers,
            activeLoans = activeLoans
        )
    }

    /**
     * Caminho canônico usado após o tick semanal de contratos. Reaproveita o snapshot calculado na
     * renovação imediatamente anterior e evita duas leituras integrais adicionais de players/teams.
     */
    suspend fun processWeeklyAfterContracts(): ManagementReport {
        val cachedPlayers = predictedPlayersAfterContractTick
        val cachedTeams = weeklyTeamsSnapshot
        return try {
            if (cachedPlayers != null && cachedTeams != null) {
                repository.withTransaction {
                    val save = repository.getGameSave()
                    val activeLoans = repository.getActiveLoans()
                    ensureCpuSquadIntegrityFromSnapshot(
                        playerTeamId = save?.playerTeamId,
                        allTeams = cachedTeams,
                        allPlayers = cachedPlayers,
                        activeLoans = activeLoans
                    )
                }
            } else {
                ensureCpuSquadIntegrity()
            }
        } finally {
            predictedPlayersAfterContractTick = null
            weeklyTeamsSnapshot = null
        }
    }

    private fun buildRenewalUpdates(
        allPlayers: List<Player>,
        cpuTeams: List<Team>
    ): LinkedHashMap<Long, Player> {
        val playersByTeam = allPlayers
            .asSequence()
            .filter { it.teamId != null }
            .groupBy { it.teamId }
        val updates = linkedMapOf<Long, Player>()

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
                    updates[player.id] = player.copy(
                        contractDurationWeeks = renewalDuration(player),
                        salary = player.calculateSalary(team.rating.toDouble()).coerceAtLeast(3_000L)
                    )
                }
            }
        }
        return updates
    }

    /** Mantém em memória a mesma transformação persistida pelo tick semanal de contratos. */
    private fun predictWeeklyContractTick(player: Player): Player {
        if (player.contractDurationWeeks <= 0) return player
        val newWeeks = player.contractDurationWeeks - 1
        if (newWeeks > 0) return player.copy(contractDurationWeeks = newWeeks)

        return if (player.isOnLoan) {
            player.copy(
                contractDurationWeeks = 0,
                isStarter = false,
                salary = 0L
            )
        } else {
            player.copy(
                contractDurationWeeks = 0,
                teamId = null,
                originalTeamId = null,
                isStarter = false,
                salary = 0L
            )
        }
    }

    private suspend fun ensureCpuSquadIntegrityFromSnapshot(
        playerTeamId: Long?,
        allTeams: List<Team>,
        allPlayers: List<Player>,
        activeLoans: List<com.example.data.PlayerLoan>
    ): ManagementReport {
        val cpuTeams = allTeams
            .filter { it.isManagedCpuClub(playerTeamId) }
            .sortedBy { it.id }

        val rosters = allPlayers
            .asSequence()
            .filter { it.teamId != null }
            .groupBy { it.teamId }
            .mapValues { (_, players) -> players.toMutableList() }
            .toMutableMap()
        val freeAgents = allPlayers
            .asSequence()
            .filter {
                it.teamId == null &&
                    !it.isOnLoan &&
                    !it.isFc26UnassignedSourceClub()
            }
            .sortedBy { it.id }
            .toMutableList()
        val pendingUpdates = linkedMapOf<Long, Player>()
        val generatedPlayers = mutableListOf<Player>()
        val finalPlayersById = allPlayers.associateBy { it.id }.toMutableMap()

        var nextPlayerId = ((allPlayers.maxOfOrNull { it.id } ?: 99_999L) + 1L)
            .coerceAtLeast(100_000L)
        val occupiedIds = allPlayers.mapTo(mutableSetOf()) { it.id }

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
                        finalPlayersById[player.id] = freeAgent
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
                finalPlayersById[candidate.id] = signedPlayer
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
                finalPlayersById[generatedPlayer.id] = generatedPlayer
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

        val duplicateActiveLoans = activeLoans.size - activeLoans.map { it.playerId }.toSet().size
        val validTeamIds = allTeams.mapTo(mutableSetOf()) { it.id }
        val invalidLoanRows = activeLoans.count { loan ->
            val player = finalPlayersById[loan.playerId]
            player == null ||
                !player.isOnLoan ||
                player.teamId != loan.borrowerTeamId ||
                player.originalTeamId != loan.ownerTeamId ||
                loan.ownerTeamId !in validTeamIds ||
                loan.borrowerTeamId !in validTeamIds ||
                loan.remainingWeeks <= 0
        }
        val teamsWithoutGoalkeeper = cpuTeams.count { team ->
            rosters[team.id].orEmpty().none { it.position == "GOL" }
        }

        return ManagementReport(
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
