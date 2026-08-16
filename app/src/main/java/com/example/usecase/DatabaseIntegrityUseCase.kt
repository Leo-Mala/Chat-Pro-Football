package com.example.usecase

import com.example.data.DefaultData
import com.example.data.GameRepository
import com.example.data.Player
import kotlin.random.Random

/**
 * UseCase responsável pela integridade e reparo automático do banco de dados Room.
 * Separa a validação (somente leitura) do reparo (modificação auditada com gerador de ID collision-safe).
 */
class DatabaseIntegrityUseCase(private val repository: GameRepository) {

    data class IntegrityCheckReport(
        val totalTeamsChecked: Int,
        val teamsRepaired: Int,
        val playersAddedCount: Int,
        val orphanPlayersFixedCount: Int,
        val issuesFound: List<String>
    )

    /**
     * Valida o banco de dados sem realizar alterações (apenas detecta problemas).
     */
    suspend fun validateDatabase(): IntegrityCheckReport {
        val teams = repository.getAllTeams()
        val allPlayers = repository.getAllPlayers()

        val teamIds = teams.map { it.id }.toSet()
        val issues = mutableListOf<String>()
        var teamsNeedingRepair = 0
        var playersNeededCount = 0

        // 1. Detectar jogadores órfãos (com teamId inexistente e != 0L)
        val orphanPlayers = allPlayers.filter { it.teamId != 0L && it.teamId !in teamIds }
        if (orphanPlayers.isNotEmpty()) {
            issues.add("Detectados %d jogadores órfãos com time inexistente.".format(orphanPlayers.size))
        }

        // 2. Detectar elencos incompletos (<16 jogadores) ou sem goleiro
        for (team in teams) {
            val roster = allPlayers.filter { it.teamId == team.id }
            val hasGoleiro = roster.any { it.position == "GOL" }
            var missingInTeam = 0
            if (!hasGoleiro) missingInTeam++
            val effectiveSize = roster.size + missingInTeam
            if (effectiveSize < 16) {
                missingInTeam += (16 - effectiveSize)
            }
            if (missingInTeam > 0) {
                teamsNeedingRepair++
                playersNeededCount += missingInTeam
                issues.add("Time %s (ID %d) necessita de +%d jogadores (Elenco atual: %d, Goleiro: %b).".format(
                    team.name, team.id, missingInTeam, roster.size, hasGoleiro
                ))
            }
        }

        return IntegrityCheckReport(
            totalTeamsChecked = teams.size,
            teamsRepaired = teamsNeedingRepair,
            playersAddedCount = playersNeededCount,
            orphanPlayersFixedCount = orphanPlayers.size,
            issuesFound = issues
        )
    }

    /**
     * Executa varredura e reparo preventivo no banco de dados ativo com relatórios detalhados e IDs collision-safe.
     */
    suspend fun repairDatabase(): IntegrityCheckReport = repository.withTransaction {
        val teams = repository.getAllTeams()
        val allPlayers = repository.getAllPlayers().toMutableList()
        val existingPlayerIds = allPlayers.map { it.id }.toMutableSet()

        val teamIds = teams.map { it.id }.toSet()
        val issues = mutableListOf<String>()
        var repairedTeamsCount = 0
        var addedPlayersCount = 0
        var orphanFixedCount = 0

        // 1. Corrigir jogadores órfãos (com teamId inexistente e != 0L) -> Converter em Agentes Livres (teamId = 0L)
        val orphanPlayers = allPlayers.filter { it.teamId != 0L && it.teamId !in teamIds }
        if (orphanPlayers.isNotEmpty()) {
            val fixedOrphans = orphanPlayers.map { orphan ->
                val updated = orphan.copy(teamId = 0L, isStarter = false)
                issues.add("REPARO ÓRFÃO: Jogador ID %d (%s) tinha teamId %d inexistente. Convertido para Agente Livre (teamId=0L).".format(
                    orphan.id, orphan.name, orphan.teamId
                ))
                updated
            }
            repository.updatePlayers(fixedOrphans)
            orphanFixedCount = fixedOrphans.size
        }

        // Helper para gerar ID de jogador collision-safe
        fun getCollisionSafePlayerId(desiredId: Long): Long {
            var candidate = if (desiredId <= 0L) 100000L else desiredId
            while (candidate in existingPlayerIds) {
                candidate++
            }
            existingPlayerIds.add(candidate)
            return candidate
        }

        // 2. Garantir que todo time tenha no mínimo 16 jogadores e 1 goleiro
        for (team in teams) {
            val roster = repository.getPlayersByTeam(team.id)
            var needsRepair = false
            val playersToInsert = mutableListOf<Player>()

            // Verificar se falta goleiro
            val hasGoleiro = roster.any { it.position == "GOL" }
            if (!hasGoleiro) {
                needsRepair = true
                val desiredGkId = team.id * 1000L + (roster.size + 1)
                val safeGkId = getCollisionSafePlayerId(desiredGkId)
                val gkAge = 20 + ((team.id + safeGkId).toInt().let { if (it < 0) -it else it } % 12)
                val gk = Player(
                    id = safeGkId,
                    teamId = team.id,
                    name = "Goleiro ${team.name.take(6)}",
                    age = gkAge,
                    position = "GOL",
                    force = team.rating.coerceIn(45, 90),
                    moral = 80,
                    energy = 100
                )
                playersToInsert.add(gk)
                issues.add("REPARO GOLEIRO: Criado goleiro de emergência para %s (ID %d) com novo ID collision-safe %d.".format(
                    team.name, team.id, safeGkId
                ))
            }

            // Completar elenco se < 16 atletas
            val currentSize = roster.size + playersToInsert.size
            if (currentSize < 16) {
                needsRepair = true
                val missingCount = 16 - currentSize
                val generatedRoster = DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
                val supplementary = generatedRoster.take(missingCount).mapIndexed { idx, p ->
                    val desiredId = team.id * 1000L + (roster.size + playersToInsert.size + idx + 1)
                    val safeId = getCollisionSafePlayerId(desiredId)
                    val updatedP = p.copy(id = safeId, teamId = team.id)
                    issues.add("REPARO ELENCO: Adicionado jogador suplementar %s para %s (ID %d) com ID collision-safe %d.".format(
                        updatedP.name, team.name, team.id, safeId
                    ))
                    updatedP
                }
                playersToInsert.addAll(supplementary)
            }

            if (needsRepair) {
                repairedTeamsCount++
                addedPlayersCount += playersToInsert.size
                repository.savePlayers(playersToInsert)
            }
        }

        IntegrityCheckReport(
            totalTeamsChecked = teams.size,
            teamsRepaired = repairedTeamsCount,
            playersAddedCount = addedPlayersCount,
            orphanPlayersFixedCount = orphanFixedCount,
            issuesFound = issues
        )
    }

    /**
     * Atalho para manter compatibilidade retroativa com código existente.
     */
    suspend fun validateAndRepairDatabase(): IntegrityCheckReport = repairDatabase()
}

