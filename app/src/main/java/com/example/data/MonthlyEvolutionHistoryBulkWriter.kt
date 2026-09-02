package com.example.data

private const val MONTHLY_HISTORY_INSERT_BATCH_SIZE = 180

/**
 * Candidate fast-path for the high-volume monthly evolution history append.
 *
 * Monthly plans always create fresh history rows (`id == 0`). Omitting the autoincrement id keeps
 * the same persisted identity semantics as Room's `@Insert` while allowing SQLite to append many
 * rows per statement. 180 rows * 5 values = 900 bind parameters, intentionally below the classic
 * Android SQLite limit of 999.
 *
 * This helper does not open its own transaction. Callers that make it authoritative must execute it
 * inside the same weekly-close transaction that validates and applies the monthly plan.
 */
internal fun GameRepository.insertMonthlyEvolutionHistoryRowsBulk(
    rows: List<HistoricoEvolucao>
): Int {
    if (rows.isEmpty()) return 0
    require(rows.all { it.id == 0L }) {
        "Monthly evolution bulk history writer only accepts fresh auto-id rows."
    }

    val database = db.openHelper.writableDatabase
    var inserted = 0

    rows.chunked(MONTHLY_HISTORY_INSERT_BATCH_SIZE).forEach { chunk ->
        val valuesSql = List(chunk.size) { "(?,?,?,?,?)" }.joinToString(",")
        val bindArgs = arrayOfNulls<Any>(chunk.size * 5)
        var index = 0
        chunk.forEach { row ->
            bindArgs[index++] = row.jogadorId
            bindArgs[index++] = row.data
            bindArgs[index++] = row.atributo
            bindArgs[index++] = row.valorAntigo.toLong()
            bindArgs[index++] = row.valorNovo.toLong()
        }

        database.execSQL(
            """
            INSERT INTO historico_evolucao
                (jogadorId, data, atributo, valorAntigo, valorNovo)
            VALUES $valuesSql
            """.trimIndent(),
            bindArgs
        )
        inserted += chunk.size
    }

    return inserted
}
