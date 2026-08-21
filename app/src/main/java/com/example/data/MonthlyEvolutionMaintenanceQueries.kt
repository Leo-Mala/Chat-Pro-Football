package com.example.data

/**
 * Lightweight evolution inputs used to fail closed before applying a prepared monthly plan.
 *
 * Only fields that can change the evolution result are captured. Contract/salary/roster flags are
 * deliberately excluded because Phase 10.1 applies evolution through column-scoped writes and must
 * not reject safe weekly contract mutations that do not affect the calculation.
 */
data class MonthlyEvolutionInputSnapshot(
    val id: Long,
    val teamId: Long?,
    val age: Int,
    val position: String,
    val force: Int,
    val potential: Int,
    val minutosJogados: Int,
    val mediaNotas: Double,
    val focoTreino: String?,
    val atributosJson: String?,
    val atributosStorage: String
)

internal fun Player.toMonthlyEvolutionInputSnapshot(): MonthlyEvolutionInputSnapshot =
    MonthlyEvolutionInputSnapshot(
        id = id,
        teamId = teamId,
        age = age,
        position = position,
        force = force,
        potential = potential,
        minutosJogados = minutosJogados,
        mediaNotas = mediaNotas,
        focoTreino = focoTreino,
        atributosJson = atributosJson,
        atributosStorage = requireNotNull(AtributosConverter.atributosToJson(atributos))
    )

/**
 * Phase 10.1 action-set helpers for monthly evolution.
 *
 * The legacy path rewrote every Player row each month only to reset `minutosJogados` and
 * `evolucaoMensal`, even when no attribute/force changed. At ~60k players that turns a small
 * logical reset into tens of thousands of full Room entity updates.
 */
internal fun GameRepository.resetMonthlyEvolutionCounters(): Int =
    db.openHelper.writableDatabase.compileStatement(
        "UPDATE players SET minutosJogados = 0, evolucaoMensal = 0.0"
    ).executeUpdateDelete()

/**
 * Reads only the columns that participate in monthly evolution. The query is chunked below the
 * SQLite bind limit so a prepared plan can be validated without materializing full Player rows.
 */
internal fun GameRepository.getMonthlyEvolutionInputSnapshots(
    playerIds: Collection<Long>
): Map<Long, MonthlyEvolutionInputSnapshot> {
    if (playerIds.isEmpty()) return emptyMap()
    val result = HashMap<Long, MonthlyEvolutionInputSnapshot>(playerIds.size)
    val database = db.openHelper.writableDatabase

    playerIds.distinct().chunked(800).forEach { chunk ->
        val placeholders = List(chunk.size) { "?" }.joinToString(",")
        val cursor = database.query(
            """
            SELECT id, teamId, age, position, force, potential, minutosJogados, mediaNotas,
                   focoTreino, atributosJson, atributos
            FROM players
            WHERE id IN ($placeholders)
            """.trimIndent(),
            chunk.map { it as Any }.toTypedArray()
        )
        cursor.use {
            val idIndex = it.getColumnIndexOrThrow("id")
            val teamIdIndex = it.getColumnIndexOrThrow("teamId")
            val ageIndex = it.getColumnIndexOrThrow("age")
            val positionIndex = it.getColumnIndexOrThrow("position")
            val forceIndex = it.getColumnIndexOrThrow("force")
            val potentialIndex = it.getColumnIndexOrThrow("potential")
            val minutesIndex = it.getColumnIndexOrThrow("minutosJogados")
            val ratingIndex = it.getColumnIndexOrThrow("mediaNotas")
            val focusIndex = it.getColumnIndexOrThrow("focoTreino")
            val jsonIndex = it.getColumnIndexOrThrow("atributosJson")
            val attributesIndex = it.getColumnIndexOrThrow("atributos")
            while (it.moveToNext()) {
                val snapshot = MonthlyEvolutionInputSnapshot(
                    id = it.getLong(idIndex),
                    teamId = if (it.isNull(teamIdIndex)) null else it.getLong(teamIdIndex),
                    age = it.getInt(ageIndex),
                    position = it.getString(positionIndex),
                    force = it.getInt(forceIndex),
                    potential = it.getInt(potentialIndex),
                    minutosJogados = it.getInt(minutesIndex),
                    mediaNotas = it.getDouble(ratingIndex),
                    focoTreino = if (it.isNull(focusIndex)) null else it.getString(focusIndex),
                    atributosJson = if (it.isNull(jsonIndex)) null else it.getString(jsonIndex),
                    atributosStorage = it.getString(attributesIndex)
                )
                result[snapshot.id] = snapshot
            }
        }
    }
    return result
}

/**
 * Applies only the four columns owned by monthly evolution. This prevents a prepared plan from
 * restoring an old contract, team, salary, fitness or transfer state through a full-entity update.
 * Callers must validate the input snapshots first while holding the same Room transaction.
 */
internal fun GameRepository.applyMonthlyEvolutionPlayerStates(players: Collection<Player>): Int {
    if (players.isEmpty()) return 0
    val statement = db.openHelper.writableDatabase.compileStatement(
        """
        UPDATE players
        SET atributosJson = ?, force = ?, minutosJogados = 0, evolucaoMensal = ?
        WHERE id = ?
        """.trimIndent()
    )
    var updated = 0
    for (player in players) {
        statement.clearBindings()
        if (player.atributosJson == null) statement.bindNull(1) else statement.bindString(1, player.atributosJson)
        statement.bindLong(2, player.force.toLong())
        statement.bindDouble(3, player.evolucaoMensal)
        statement.bindLong(4, player.id)
        updated += statement.executeUpdateDelete()
    }
    return updated
}

/**
 * Returns stable fingerprints for evolution-history rows already persisted for one monthly period.
 * The V22 index on `historico_evolucao.data` keeps this lookup bounded to the requested period.
 */
internal suspend fun GameRepository.getMonthlyEvolutionHistoryFingerprints(
    periodDate: String
): Set<String> = db.historicoEvolucaoDao()
    .getHistoricoPorData(periodDate)
    .mapTo(hashSetOf()) { it.monthlyEvolutionFingerprint() }

internal fun HistoricoEvolucao.monthlyEvolutionFingerprint(): String =
    "$jogadorId|$data|$atributo|$valorAntigo|$valorNovo"
