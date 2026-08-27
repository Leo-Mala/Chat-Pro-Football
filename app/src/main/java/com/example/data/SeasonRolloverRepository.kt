package com.example.data

/**
 * Phase 10.8 repository operations that are deliberately scoped to the season-rollover hot path.
 *
 * They use existing V22 tables only. No index/schema change is required: the measured regression
 * came from per-row Room round-trips, not from missing lookup indexes.
 */
suspend fun GameRepository.getRolloverRetiringPlayers(retirementCurrentAge: Int): List<Player> =
    db.playerDao().getPlayersAtLeastAge(retirementCurrentAge)

suspend fun GameRepository.ageAndResetRolloverPlayers(retirementCurrentAge: Int): Int {
    val updated = db.playerDao().ageAndResetPlayersBelowRetirementAge(retirementCurrentAge)
    // O rollover anual precisa limpar apenas estatísticas sazonais dos sobreviventes, preservando
    // careerGoals/careerApps e os demais acumulados históricos. A exclusão dos aposentados ocorre
    // logo em seguida no mesmo fluxo transacional, então limpar também suas colunas sazonais aqui é
    // inofensivo e mantém o hot path em um único action-set SQL.
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
    return updated
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
