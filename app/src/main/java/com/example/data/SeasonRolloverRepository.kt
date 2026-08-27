package com.example.data

/**
 * Phase 10.8 repository operations that are deliberately scoped to the season-rollover hot path.
 *
 * They use existing V22 tables only. No index/schema change is required: the measured regression
 * came from per-row Room round-trips, not from missing lookup indexes.
 */
suspend fun GameRepository.getRolloverRetiringPlayers(retirementCurrentAge: Int): List<Player> =
    db.playerDao().getPlayersAtLeastAge(retirementCurrentAge)

suspend fun GameRepository.ageAndResetRolloverPlayers(retirementCurrentAge: Int): Int =
    db.playerDao().ageAndResetPlayersBelowRetirementAge(retirementCurrentAge)

/**
 * Limpa somente estatísticas da temporada encerrada durante a transição anual.
 *
 * Este action-set fica deliberadamente separado de [ageAndResetRolloverPlayers]: o contrato
 * histórico da Fase 10.8 desse método é alterar somente idade/energia/moral/lesão/suspensão/cartões.
 * Os acumulados de carreira (careerGoals/careerApps/careerAssists) permanecem intactos.
 */
suspend fun GameRepository.resetRolloverSeasonalStatistics() {
    db.openHelper.writableDatabase.execSQL(
        """
        UPDATE players
        SET gols = 0,
            assistencias = 0,
            partidasDisputadas = 0,
            minutosJogados = 0,
            mediaNotas = 0.0,
            evolucaoMensal = 0.0
        """.trimIndent()
    )
}

suspend fun GameRepository.completeRolloverLoansForPlayers(playerIds: Collection<Long>): Int {
    val distinctIds = playerIds.distinct()
    if (distinctIds.isEmpty()) return 0
    return distinctIds
        .chunked(ROLLOVER_SQLITE_SAFE_IN_QUERY_SIZE)
        .sumOf { ids -> db.playerLoanDao().completeActiveLoansForPlayers(ids) }
}

suspend fun GameRepository.deleteRolloverPlayers(playerIds: Collection<Long>): Int {
    val distinctIds = playerIds.distinct()
    if (distinctIds.isEmpty()) return 0
    return distinctIds
        .chunked(ROLLOVER_SQLITE_SAFE_IN_QUERY_SIZE)
        .sumOf { ids -> db.playerDao().deletePlayersByIds(ids) }
}

private const val ROLLOVER_SQLITE_SAFE_IN_QUERY_SIZE = 900
