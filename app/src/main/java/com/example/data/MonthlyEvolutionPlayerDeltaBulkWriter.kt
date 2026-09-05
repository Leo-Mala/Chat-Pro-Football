package com.example.data

private const val MONTHLY_PLAYER_DELTA_STAGE_BATCH_SIZE = 200
private const val MONTHLY_PLAYER_DELTA_STAGE_TABLE = "temp_monthly_evolution_player_delta"

/**
 * Set-based production writer for the compact monthly player-state plan.
 *
 * The legacy Collection overload executes one UPDATE statement per changed player. A canonical
 * month changes roughly 55k players, which turns the Android SQLite boundary into the dominant
 * commit cost. Production plans expose a List, so this more-specific overload stages those deltas
 * in bounded multi-row INSERTs and applies the same four owned columns with one set-based UPDATE.
 *
 * 200 rows * 4 values = 800 bind parameters, intentionally below Android SQLite's classic 999
 * parameter limit. The TEMP table is connection-local and is only scratch state; it does not
 * change Room's schema version or require a migration.
 *
 * The final UPDATE still targets `players`, so the existing row-level monthly revision trigger sees
 * the same OLD/NEW column values as the per-row writer. Callers remain responsible for executing
 * this helper inside the already-authoritative monthly transaction and for fail-closing when the
 * returned row count differs from the prepared state count.
 */
internal fun GameRepository.applyMonthlyEvolutionPlayerStateDeltas(
    states: List<MonthlyEvolutionPlayerState>
): Int {
    if (states.isEmpty()) return 0

    val database = db.openHelper.writableDatabase
    database.execSQL(
        """
        CREATE TEMP TABLE IF NOT EXISTS $MONTHLY_PLAYER_DELTA_STAGE_TABLE (
            id INTEGER NOT NULL PRIMARY KEY,
            atributosJson TEXT,
            force INTEGER NOT NULL,
            evolucaoMensal REAL NOT NULL
        )
        """.trimIndent()
    )
    // A previous transaction may have rolled back after creating the TEMP table. Always establish
    // an empty staging set before accepting a new prepared plan.
    database.execSQL("DELETE FROM $MONTHLY_PLAYER_DELTA_STAGE_TABLE")

    try {
        states.chunked(MONTHLY_PLAYER_DELTA_STAGE_BATCH_SIZE).forEach { chunk ->
            val valuesSql = List(chunk.size) { "(?,?,?,?)" }.joinToString(",")
            val bindArgs = arrayOfNulls<Any>(chunk.size * 4)
            var index = 0
            chunk.forEach { state ->
                bindArgs[index++] = state.id
                bindArgs[index++] = state.atributosJson
                bindArgs[index++] = state.force.toLong()
                bindArgs[index++] = state.evolucaoMensal
            }
            database.execSQL(
                """
                INSERT INTO $MONTHLY_PLAYER_DELTA_STAGE_TABLE
                    (id, atributosJson, force, evolucaoMensal)
                VALUES $valuesSql
                """.trimIndent(),
                bindArgs
            )
        }

        return database.compileStatement(
            """
            UPDATE players
            SET atributosJson = (
                    SELECT staged.atributosJson
                    FROM $MONTHLY_PLAYER_DELTA_STAGE_TABLE AS staged
                    WHERE staged.id = players.id
                ),
                force = (
                    SELECT staged.force
                    FROM $MONTHLY_PLAYER_DELTA_STAGE_TABLE AS staged
                    WHERE staged.id = players.id
                ),
                minutosJogados = 0,
                evolucaoMensal = (
                    SELECT staged.evolucaoMensal
                    FROM $MONTHLY_PLAYER_DELTA_STAGE_TABLE AS staged
                    WHERE staged.id = players.id
                )
            WHERE id IN (SELECT id FROM $MONTHLY_PLAYER_DELTA_STAGE_TABLE)
            """.trimIndent()
        ).executeUpdateDelete()
    } finally {
        database.execSQL("DELETE FROM $MONTHLY_PLAYER_DELTA_STAGE_TABLE")
    }
}
