package com.example.data

import android.database.Cursor

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
) {
    /**
     * Weekly finance/contracts can legitimately move a player between clubs without changing the
     * football inputs themselves. Those moves can be repaired from the current row with a small
     * targeted recalculation instead of forcing a new 60k-player calculation under the DB lock.
     */
    fun sameEvolutionStateIgnoringTeam(other: MonthlyEvolutionInputSnapshot): Boolean =
        id == other.id &&
            age == other.age &&
            position == other.position &&
            force == other.force &&
            potential == other.potential &&
            minutosJogados == other.minutosJogados &&
            mediaNotas == other.mediaNotas &&
            focoTreino == other.focoTreino &&
            atributosJson == other.atributosJson &&
            atributosStorage == other.atributosStorage
}

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

internal fun GameRepository.getMonthlyEvolutionPlayerCount(): Int =
    db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM players").use { cursor ->
        check(cursor.moveToFirst()) { "Não foi possível contar jogadores para validar evolução mensal." }
        cursor.getInt(0)
    }

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
        database.query(
            """
            SELECT id, teamId, age, position, force, potential, minutosJogados, mediaNotas,
                   focoTreino, atributosJson, atributos
            FROM players
            WHERE id IN ($placeholders)
            """.trimIndent(),
            chunk.map { it as Any }.toTypedArray()
        ).use { cursor ->
            cursor.readMonthlyEvolutionSnapshotsInto(result)
        }
    }
    return result
}

/**
 * Full-universe lightweight scan used only by the atomic weekly close when roster maintenance may
 * have moved or inserted players after the CPU-heavy plan was prepared. This is intentionally a
 * projection, not `SELECT *`, so detecting the exceptional subset remains cheap under the lock.
 */
internal fun GameRepository.getAllMonthlyEvolutionInputSnapshots(): Map<Long, MonthlyEvolutionInputSnapshot> {
    val result = HashMap<Long, MonthlyEvolutionInputSnapshot>()
    db.openHelper.writableDatabase.query(
        """
        SELECT id, teamId, age, position, force, potential, minutosJogados, mediaNotas,
               focoTreino, atributosJson, atributos
        FROM players
        """.trimIndent()
    ).use { cursor ->
        cursor.readMonthlyEvolutionSnapshotsInto(result)
    }
    return result
}

private fun Cursor.readMonthlyEvolutionSnapshotsInto(
    result: MutableMap<Long, MonthlyEvolutionInputSnapshot>
) {
    val idIndex = getColumnIndexOrThrow("id")
    val teamIdIndex = getColumnIndexOrThrow("teamId")
    val ageIndex = getColumnIndexOrThrow("age")
    val positionIndex = getColumnIndexOrThrow("position")
    val forceIndex = getColumnIndexOrThrow("force")
    val potentialIndex = getColumnIndexOrThrow("potential")
    val minutesIndex = getColumnIndexOrThrow("minutosJogados")
    val ratingIndex = getColumnIndexOrThrow("mediaNotas")
    val focusIndex = getColumnIndexOrThrow("focoTreino")
    val jsonIndex = getColumnIndexOrThrow("atributosJson")
    val attributesIndex = getColumnIndexOrThrow("atributos")

    while (moveToNext()) {
        val snapshot = MonthlyEvolutionInputSnapshot(
            id = getLong(idIndex),
            teamId = if (isNull(teamIdIndex)) null else getLong(teamIdIndex),
            age = getInt(ageIndex),
            position = getString(positionIndex),
            force = getInt(forceIndex),
            potential = getInt(potentialIndex),
            minutosJogados = getInt(minutesIndex),
            mediaNotas = getDouble(ratingIndex),
            focoTreino = if (isNull(focusIndex)) null else getString(focusIndex),
            atributosJson = if (isNull(jsonIndex)) null else getString(jsonIndex),
            atributosStorage = getString(attributesIndex)
        )
        result[snapshot.id] = snapshot
    }
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
