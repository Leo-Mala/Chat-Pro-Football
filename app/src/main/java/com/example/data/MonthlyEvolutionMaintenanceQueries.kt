package com.example.data

/**
 * Phase 10.1 action-set helpers for monthly evolution.
 *
 * The legacy path rewrote every Player row each month only to reset `minutosJogados` and
 * `evolucaoMensal`, even when no attribute/force changed. At ~60k players that turns a small
 * logical reset into tens of thousands of full Room entity updates. This action set preserves the
 * exact persisted semantics while allowing the use case to upsert only players whose football
 * state actually changed.
 */
internal fun GameRepository.resetMonthlyEvolutionCounters(): Int =
    db.openHelper.writableDatabase.compileStatement(
        "UPDATE players SET minutosJogados = 0, evolucaoMensal = 0.0"
    ).executeUpdateDelete()

/**
 * Returns stable fingerprints for evolution-history rows already persisted for one monthly period.
 * This makes a prepared monthly plan safe to retry: player/counter writes are idempotent, and
 * history rows that were already committed are not inserted a second time with a new auto ID.
 */
internal suspend fun GameRepository.getMonthlyEvolutionHistoryFingerprints(
    periodDate: String
): Set<String> = db.historicoEvolucaoDao()
    .getHistoricoPorData(periodDate)
    .mapTo(hashSetOf(), HistoricoEvolucao::monthlyEvolutionFingerprint)

internal fun HistoricoEvolucao.monthlyEvolutionFingerprint(): String =
    "$jogadorId|$data|$atributo|$valorAntigo|$valorNovo"
