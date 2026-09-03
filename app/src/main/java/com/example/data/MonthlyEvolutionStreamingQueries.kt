package com.example.data

/**
 * Streams the compact monthly-evolution projection through one ordered SQLite cursor.
 *
 * The previous compact path issued one ORDER BY + LIMIT/OFFSET query per 4k-player batch. With the
 * world database above 75k players SQLite repeatedly sorted/walked the same prefix. This helper
 * preserves the exact canonical ORDER BY and batch size while executing that SQL ordering only
 * once. The callback is invoked outside any Room transaction; the later monthly commit still
 * performs the existing fail-closed snapshot validation.
 *
 * [onBatchReadNanos] is an optional test/benchmark sink. Production callers use the no-op default,
 * so no performance logging is emitted by the app.
 */
internal fun GameRepository.forEachMonthlyEvolutionPlayerBatch(
    batchSize: Int,
    onBatchReadNanos: (Long) -> Unit = {},
    onPlayerRead: (Player, String) -> Unit = { _, _ -> },
    consume: (List<Player>) -> Unit
): Int {
    require(batchSize > 0) { "Monthly evolution batch size must be positive." }

    val database = db.openHelper.writableDatabase
    var processed = 0
    var batch = ArrayList<Player>(batchSize)
    var readStartedAtNs = System.nanoTime()

    database.query(
        """
        SELECT id, teamId, name, age, position, force,
               finishing, passing, pace, strength, vision, defense,
               atributosJson, atributos, potential, minutosJogados, mediaNotas, focoTreino
        FROM players
        ORDER BY force DESC, name ASC
        """.trimIndent()
    ).use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow("id")
        val teamIdIndex = cursor.getColumnIndexOrThrow("teamId")
        val nameIndex = cursor.getColumnIndexOrThrow("name")
        val ageIndex = cursor.getColumnIndexOrThrow("age")
        val positionIndex = cursor.getColumnIndexOrThrow("position")
        val forceIndex = cursor.getColumnIndexOrThrow("force")
        val finishingIndex = cursor.getColumnIndexOrThrow("finishing")
        val passingIndex = cursor.getColumnIndexOrThrow("passing")
        val paceIndex = cursor.getColumnIndexOrThrow("pace")
        val strengthIndex = cursor.getColumnIndexOrThrow("strength")
        val visionIndex = cursor.getColumnIndexOrThrow("vision")
        val defenseIndex = cursor.getColumnIndexOrThrow("defense")
        val atributosJsonIndex = cursor.getColumnIndexOrThrow("atributosJson")
        val atributosIndex = cursor.getColumnIndexOrThrow("atributos")
        val potentialIndex = cursor.getColumnIndexOrThrow("potential")
        val minutesIndex = cursor.getColumnIndexOrThrow("minutosJogados")
        val ratingIndex = cursor.getColumnIndexOrThrow("mediaNotas")
        val focusIndex = cursor.getColumnIndexOrThrow("focoTreino")

        while (cursor.moveToNext()) {
            val atributosStorage = cursor.getString(atributosIndex)
            val player = Player(
                    id = cursor.getLong(idIndex),
                    teamId = if (cursor.isNull(teamIdIndex)) null else cursor.getLong(teamIdIndex),
                    name = cursor.getString(nameIndex),
                    age = cursor.getInt(ageIndex),
                    position = cursor.getString(positionIndex),
                    force = cursor.getInt(forceIndex),
                    finishing = cursor.getInt(finishingIndex),
                    passing = cursor.getInt(passingIndex),
                    pace = cursor.getInt(paceIndex),
                    strength = cursor.getInt(strengthIndex),
                    vision = cursor.getInt(visionIndex),
                    defense = cursor.getInt(defenseIndex),
                    atributosJson = if (cursor.isNull(atributosJsonIndex)) null else cursor.getString(atributosJsonIndex),
                    atributos = MonthlyEvolutionAtributosDecoder.decode(atributosStorage) ?: Atributos(),
                    potential = cursor.getInt(potentialIndex),
                    minutosJogados = cursor.getInt(minutesIndex),
                    mediaNotas = cursor.getDouble(ratingIndex),
                    focoTreino = if (cursor.isNull(focusIndex)) null else cursor.getString(focusIndex)
                )
            onPlayerRead(player, atributosStorage)
            batch.add(player)

            if (batch.size == batchSize) {
                onBatchReadNanos(System.nanoTime() - readStartedAtNs)
                consume(batch)
                processed += batch.size
                batch = ArrayList(batchSize)
                readStartedAtNs = System.nanoTime()
            }
        }

        if (batch.isNotEmpty()) {
            onBatchReadNanos(System.nanoTime() - readStartedAtNs)
            consume(batch)
            processed += batch.size
        }
    }

    return processed
}
