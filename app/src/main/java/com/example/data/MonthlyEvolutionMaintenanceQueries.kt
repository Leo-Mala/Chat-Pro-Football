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
