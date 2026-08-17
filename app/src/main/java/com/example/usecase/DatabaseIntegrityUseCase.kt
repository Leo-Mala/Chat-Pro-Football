package com.example.usecase

import com.example.data.DefaultData
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.Team

/**
 * UseCase responsável pela integridade e reparo automático do banco de dados Room.
 * Separa a validação (somente leitura) do reparo (modificação auditada com gerador de ID collision-safe).
 *
 * Desde V21, constraints do próprio banco são a primeira defesa. Este UseCase permanece para
 * diagnóstico e recuperação controlada de dados legados que ainda sejam semanticamente reparáveis.
 */
class DatabaseIntegrityUseCase(private val repository: GameRepository) {

    data class IntegrityCheckReport(
        val totalTeamsChecked: Int,
        val teamsRepaired: Int,
        val playersAddedCount: Int,
        val orphanPlayersFixedCount: Int,
        val issuesFound: List<String>
    )

    private fun Team.requiresDomesticRosterIntegrity(): Boolean =
        !country.equals("Mundial", ignoreCase = true)

    suspend fun validateDatabase(): IntegrityCheckReport {
        val teams = repository.getAllTeams()
        val rosterTeams = teams.filter { it.requiresDomesticRosterIntegrity() }
        val allPlayers = repository.getAllPlayers()
        val playersByTeam = allPlayers.groupBy { it.teamId }

        val teamIds = teams.map { it.id }.toSet()
        val issues = mutableListOf<String>()
        var teamsNeedingRepair = 0
        var playersNeededCount = 0

        val orphanPlayers = allPlayers.filter { it.teamId != null && it.teamId !in teamIds }
        if (orphanPlayers.isNotEmpty()) {
            issues.add("Detectados %d jogadores órfãos com time inexistente.".format(orphanPlayers.size))
        }

        for (team in rosterTeams) {
            val roster = playersByTeam[team.id].orEmpty()
            val hasGoleiro = roster.any { it.position == "GOL" }
            var missingInTeam = 0
            if (!hasGoleiro) missingInTeam++
            val effectiveSize = roster.size + missingInTeam
            if (effectiveSize < 16) missingInTeam += (16 - effectiveSize)
            if (missingInTeam > 0) {
                teamsNeedingRepair++
                playersNeededCount += missingInTeam
                issues.add(
                    "Time %s (ID %d) necessita de +%d jogadores (Elenco atual: %d, Goleiro: %b).".format(
                        team.name,
                        team.id,
                        missingInTeam,
                        roster.size,
                        hasGoleiro
                    )
                )
            }
        }

        return IntegrityCheckReport(
            totalTeamsChecked = rosterTeams.size,
            teamsRepaired = teamsNeedingRepair,
            playersAddedCount = playersNeededCount,
            orphanPlayersFixedCount = orphanPlayers.size,
            issuesFound = issues
        )
    }

    suspend fun repairDatabase(): IntegrityCheckReport {
        val preflight = validateDatabase()
        if (preflight.issuesFound.isEmpty()) return preflight

        return repository.withTransaction {
            val teams = repository.getAllTeams()
            val rosterTeams = teams.filter { it.requiresDomesticRosterIntegrity() }
            val allPlayers = repository.getAllPlayers().toMutableList()
            val playersByTeam = allPlayers.groupBy { it.teamId }
            val existingPlayerIds = allPlayers.map { it.id }.toMutableSet()

            val teamIds = teams.map { it.id }.toSet()
            val issues = mutableListOf<String>()
            var repairedTeamsCount = 0
            var addedPlayersCount = 0
            var orphanFixedCount = 0

            // V21 impede novos órfãos por FK. Este bloco atende apenas estados legados/carregados.
            val orphanPlayers = allPlayers.filter { it.teamId != null && it.teamId !in teamIds }
            if (orphanPlayers.isNotEmpty()) {
                val fixedOrphans = orphanPlayers.map { orphan ->
                    val previousTeamId = orphan.teamId
                    val updated = orphan.copy(teamId = null, originalTeamId = null, isStarter = false)
                    issues.add(
                        "REPARO ÓRFÃO: Jogador ID %d (%s) tinha teamId %s inexistente. Convertido para Agente Livre (teamId=null).".format(
                            orphan.id,
                            orphan.name,
                            previousTeamId.toString()
                        )
                    )
                    updated
                }
                repository.updatePlayers(fixedOrphans)
                orphanFixedCount = fixedOrphans.size
            }

            fun getCollisionSafePlayerId(desiredId: Long): Long {
                var candidate = if (desiredId <= 0L) 100000L else desiredId
                while (candidate in existingPlayerIds) candidate++
                existingPlayerIds.add(candidate)
                return candidate
            }

            // Clubes Mundial existem somente para garantir referências de fixtures e não recebem elenco.
            for (team in rosterTeams) {
                val roster = playersByTeam[team.id].orEmpty()
                var needsRepair = false
                val playersToInsert = mutableListOf<Player>()

                val hasGoleiro = roster.any { it.position == "GOL" }
                if (!hasGoleiro) {
                    needsRepair = true
                    val desiredGkId = team.id * 1000L + (roster.size + 1)
                    val safeGkId = getCollisionSafePlayerId(desiredGkId)
                    val gkAge = 20 + ((team.id + safeGkId).toInt().let { if (it < 0) -it else it } % 12)
                    playersToInsert.add(
                        Player(
                            id = safeGkId,
                            teamId = team.id,
                            name = "Goleiro ${team.name.take(6)}",
                            age = gkAge,
                            position = "GOL",
                            force = team.rating.coerceIn(45, 90),
                            moral = 80,
                            energy = 100
                        )
                    )
                    issues.add(
                        "REPARO GOLEIRO: Criado goleiro de emergência para %s (ID %d) com novo ID collision-safe %d.".format(
                            team.name,
                            team.id,
                            safeGkId
                        )
                    )
                }

                val currentSize = roster.size + playersToInsert.size
                if (currentSize < 16) {
                    needsRepair = true
                    val missingCount = 16 - currentSize
                    val generatedRoster = DefaultData.generateRosterForTeam(
                        team.id,
                        team.rating,
                        team.name,
                        team.country
                    )
                    playersToInsert.addAll(
                        generatedRoster.take(missingCount).mapIndexed { idx, p ->
                            val desiredId = team.id * 1000L + (roster.size + playersToInsert.size + idx + 1)
                            val safeId = getCollisionSafePlayerId(desiredId)
                            val updatedP = p.copy(id = safeId, teamId = team.id)
                            issues.add(
                                "REPARO ELENCO: Adicionado jogador suplementar %s para %s (ID %d) com ID collision-safe %d.".format(
                                    updatedP.name,
                                    team.name,
                                    team.id,
                                    safeId
                                )
                            )
                            updatedP
                        }
                    )
                }

                if (needsRepair) {
                    repairedTeamsCount++
                    addedPlayersCount += playersToInsert.size
                    repository.savePlayers(playersToInsert)
                }
            }

            IntegrityCheckReport(
                totalTeamsChecked = rosterTeams.size,
                teamsRepaired = repairedTeamsCount,
                playersAddedCount = addedPlayersCount,
                orphanPlayersFixedCount = orphanFixedCount,
                issuesFound = issues
            )
        }
    }

    suspend fun validateAndRepairDatabase(): IntegrityCheckReport = repairDatabase()
}
