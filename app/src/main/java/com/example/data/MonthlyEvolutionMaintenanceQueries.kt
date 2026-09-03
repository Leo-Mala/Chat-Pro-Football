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

data class MonthlyEvolutionPlayerState(
    val id: Long,
    val atributosJson: String?,
    val force: Int,
    val evolucaoMensal: Double
)

internal fun Player.toMonthlyEvolutionPlayerState(): MonthlyEvolutionPlayerState =
    MonthlyEvolutionPlayerState(
        id = id,
        atributosJson = atributosJson,
        force = force,
        evolucaoMensal = evolucaoMensal
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
        """
        UPDATE players
        SET minutosJogados = 0, evolucaoMensal = 0.0
        WHERE minutosJogados != 0 OR evolucaoMensal != 0.0
        """.trimIndent()
    ).executeUpdateDelete()

internal fun GameRepository.getMonthlyEvolutionPlayerCount(): Int =
    db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM players").use { cursor ->
        check(cursor.moveToFirst()) { "Não foi possível contar jogadores para validar evolução mensal." }
        cursor.getInt(0)
    }

/**
 * Compact production read for monthly planning.
 *
 * The normal Room Player query materializes every persisted column even though the compact monthly
 * path only needs fields that influence evolution or its stale-plan snapshot. This projection keeps
 * the canonical ORDER BY used by the legacy path while avoiding unrelated contract, market, career,
 * fitness and scouting columns. It intentionally returns Player objects only because the existing
 * monthly engine already encodes the audited evolution rules; compact callers persist through
 * column-scoped writes, so unrelated default fields are never written back.
 */
internal fun GameRepository.getMonthlyEvolutionPlayersBatch(limit: Int, offset: Int): List<Player> {
    require(limit > 0) { "Monthly evolution batch limit must be positive." }
    require(offset >= 0) { "Monthly evolution batch offset cannot be negative." }

    val result = ArrayList<Player>(limit)
    db.openHelper.writableDatabase.query(
        """
        SELECT id, teamId, name, age, position, force,
               finishing, passing, pace, strength, vision, defense,
               atributosJson, atributos, potential, minutosJogados, mediaNotas, focoTreino
        FROM players
        ORDER BY force DESC, name ASC
        LIMIT ? OFFSET ?
        """.trimIndent(),
        arrayOf<Any>(limit, offset)
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
            result.add(
                Player(
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
                    atributos = AtributosConverter.jsonToAtributos(atributosStorage) ?: Atributos(),
                    potential = cursor.getInt(potentialIndex),
                    minutosJogados = cursor.getInt(minutesIndex),
                    mediaNotas = cursor.getDouble(ratingIndex),
                    focoTreino = if (cursor.isNull(focusIndex)) null else cursor.getString(focusIndex)
                )
            )
        }
    }
    return result
}

private fun hashMapCapacityForSize(size: Int): Int {
    if (size <= 0) return 16
    // HashMap grows at a 0.75 load factor. Reserving for the complete monthly universe prevents
    // repeated table rehash/copies while the weekly-close transaction scans ~60k player rows.
    return ((size / 0.75f) + 1f).toInt().coerceAtLeast(16)
}

/**
 * Keep validation pages small enough to stay bounded inside Android CursorWindow. Unlike
 * LIMIT/OFFSET, keyset pagination does not repeatedly walk the already consumed prefix as the
 * world-player table grows.
 */
private const val MONTHLY_VALIDATION_SCAN_BATCH_SIZE = 1024

/**
 * Reads only the columns that participate in monthly evolution. The query is chunked below the
 * SQLite bind limit so a prepared plan can be validated without materializing full Player rows.
 */
internal fun GameRepository.getMonthlyEvolutionInputSnapshots(
    playerIds: Collection<Long>
): Map<Long, MonthlyEvolutionInputSnapshot> {
    if (playerIds.isEmpty()) return emptyMap()
    val result = HashMap<Long, MonthlyEvolutionInputSnapshot>(hashMapCapacityForSize(playerIds.size))
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
 * have moved or inserted players after the CPU-heavy plan was prepared.
 *
 * Android's SQLiteCursor resolves getCount() by asking SQLiteQuery.fillWindow(...,
 * countAllRows=true). On a ~60k-row projection that includes two sizeable attribute strings, a
 * single unbounded cursor therefore enumerates the whole result before normal CursorWindow paging
 * begins. Read the same complete universe with primary-key keyset pages instead. Every Player row
 * is still validated exactly once; only the transport from SQLite to Kotlin is bounded.
 */
internal fun GameRepository.getAllMonthlyEvolutionInputSnapshots(): Map<Long, MonthlyEvolutionInputSnapshot> {
    val expectedCount = getMonthlyEvolutionPlayerCount()
    if (expectedCount == 0) return emptyMap()

    val result = HashMap<Long, MonthlyEvolutionInputSnapshot>(hashMapCapacityForSize(expectedCount))
    val database = db.openHelper.writableDatabase
    var lastSeenId: Long? = null

    while (result.size < expectedCount) {
        val query = if (lastSeenId == null) {
            """
            SELECT id, teamId, age, position, force, potential, minutosJogados, mediaNotas,
                   focoTreino, atributosJson, atributos
            FROM players
            ORDER BY id ASC
            LIMIT $MONTHLY_VALIDATION_SCAN_BATCH_SIZE
            """.trimIndent()
        } else {
            """
            SELECT id, teamId, age, position, force, potential, minutosJogados, mediaNotas,
                   focoTreino, atributosJson, atributos
            FROM players
            WHERE id > ?
            ORDER BY id ASC
            LIMIT $MONTHLY_VALIDATION_SCAN_BATCH_SIZE
            """.trimIndent()
        }
        val args = lastSeenId?.let { arrayOf<Any>(it) } ?: emptyArray()

        val batchLastId = database.query(query, args).use { cursor ->
            cursor.readMonthlyEvolutionSnapshotsInto(result)
        }
        check(batchLastId != null) {
            "Monthly evolution validation scan ended at ${result.size} of $expectedCount rows."
        }
        check(lastSeenId == null || batchLastId > lastSeenId!!) {
            "Monthly evolution validation keyset did not advance after player id $lastSeenId."
        }
        lastSeenId = batchLastId
    }

    check(result.size == expectedCount) {
        "Monthly evolution validation expected $expectedCount players but read ${result.size}."
    }
    return result
}

private fun Cursor.readMonthlyEvolutionSnapshotsInto(
    result: MutableMap<Long, MonthlyEvolutionInputSnapshot>
): Long? {
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
    var lastId: Long? = null

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
        lastId = snapshot.id
    }
    return lastId
}

data class MonthlyEvolutionRosterValidation(
    val valid: Boolean,
    val correctionIds: Set<Long>,
    val currentPlayerCount: Int
)

/**
 * Full-universe weekly-close validation without materializing a second current-input HashMap.
 * Every persisted evolution input is still checked. New rows are targeted for recalculation;
 * removed or mutated expected rows invalidate the plan fail-closed.
 */
internal fun GameRepository.validateMonthlyEvolutionRosterInputs(
    expectedInputs: List<MonthlyEvolutionInputSnapshot>,
    expectedTrainingCenterLevels: Map<Long, Int>,
    currentTrainingCenterLevels: Map<Long, Int>
): MonthlyEvolutionRosterValidation {
    if (expectedInputs.isEmpty()) {
        val count = getMonthlyEvolutionPlayerCount()
        return MonthlyEvolutionRosterValidation(count == 0, emptySet(), count)
    }

    val expectedById = HashMap<Long, MonthlyEvolutionInputSnapshot>(hashMapCapacityForSize(expectedInputs.size))
    for (expected in expectedInputs) expectedById[expected.id] = expected

    val corrections = linkedSetOf<Long>()
    val database = db.openHelper.writableDatabase
    var lastSeenId: Long? = null
    var currentCount = 0
    var matchedExpectedCount = 0
    var valid = true

    while (true) {
        val query = if (lastSeenId == null) {
            """
            SELECT id, teamId, age, position, force, potential, minutosJogados, mediaNotas,
                   focoTreino, atributosJson, atributos
            FROM players
            ORDER BY id ASC
            LIMIT $MONTHLY_VALIDATION_SCAN_BATCH_SIZE
            """.trimIndent()
        } else {
            """
            SELECT id, teamId, age, position, force, potential, minutosJogados, mediaNotas,
                   focoTreino, atributosJson, atributos
            FROM players
            WHERE id > ?
            ORDER BY id ASC
            LIMIT $MONTHLY_VALIDATION_SCAN_BATCH_SIZE
            """.trimIndent()
        }
        val args = lastSeenId?.let { arrayOf<Any>(it) } ?: emptyArray()
        var rowsInBatch = 0
        var batchLastId: Long? = null

        database.query(query, args).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val teamIdIndex = cursor.getColumnIndexOrThrow("teamId")
            val ageIndex = cursor.getColumnIndexOrThrow("age")
            val positionIndex = cursor.getColumnIndexOrThrow("position")
            val forceIndex = cursor.getColumnIndexOrThrow("force")
            val potentialIndex = cursor.getColumnIndexOrThrow("potential")
            val minutesIndex = cursor.getColumnIndexOrThrow("minutosJogados")
            val ratingIndex = cursor.getColumnIndexOrThrow("mediaNotas")
            val focusIndex = cursor.getColumnIndexOrThrow("focoTreino")
            val jsonIndex = cursor.getColumnIndexOrThrow("atributosJson")
            val attributesIndex = cursor.getColumnIndexOrThrow("atributos")

            while (cursor.moveToNext()) {
                val current = MonthlyEvolutionInputSnapshot(
                    id = cursor.getLong(idIndex),
                    teamId = if (cursor.isNull(teamIdIndex)) null else cursor.getLong(teamIdIndex),
                    age = cursor.getInt(ageIndex),
                    position = cursor.getString(positionIndex),
                    force = cursor.getInt(forceIndex),
                    potential = cursor.getInt(potentialIndex),
                    minutosJogados = cursor.getInt(minutesIndex),
                    mediaNotas = cursor.getDouble(ratingIndex),
                    focoTreino = if (cursor.isNull(focusIndex)) null else cursor.getString(focusIndex),
                    atributosJson = if (cursor.isNull(jsonIndex)) null else cursor.getString(jsonIndex),
                    atributosStorage = cursor.getString(attributesIndex)
                )
                currentCount++
                rowsInBatch++
                batchLastId = current.id

                val expected = expectedById[current.id]
                if (expected == null) {
                    corrections.add(current.id)
                    continue
                }
                matchedExpectedCount++
                if (!expected.sameEvolutionStateIgnoringTeam(current)) {
                    valid = false
                    continue
                }
                if (expected.teamId != current.teamId) {
                    val oldLevel = expected.teamId?.let { expectedTrainingCenterLevels[it] } ?: 1
                    val newLevel = current.teamId?.let { currentTrainingCenterLevels[it] } ?: 1
                    if (oldLevel != newLevel) corrections.add(current.id)
                }
            }
        }

        if (rowsInBatch == 0) break
        check(lastSeenId == null || requireNotNull(batchLastId) > lastSeenId!!) {
            "Monthly evolution validation keyset did not advance after player id $lastSeenId."
        }
        lastSeenId = batchLastId
        if (rowsInBatch < MONTHLY_VALIDATION_SCAN_BATCH_SIZE) break
    }

    if (matchedExpectedCount != expectedById.size) valid = false
    return MonthlyEvolutionRosterValidation(valid, corrections, currentCount)
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

internal fun GameRepository.applyMonthlyEvolutionPlayerStateDeltas(
    states: Collection<MonthlyEvolutionPlayerState>
): Int {
    if (states.isEmpty()) return 0
    val statement = db.openHelper.writableDatabase.compileStatement(
        """
        UPDATE players
        SET atributosJson = ?, force = ?, minutosJogados = 0, evolucaoMensal = ?
        WHERE id = ?
        """.trimIndent()
    )
    var updated = 0
    for (state in states) {
        statement.clearBindings()
        if (state.atributosJson == null) statement.bindNull(1) else statement.bindString(1, state.atributosJson)
        statement.bindLong(2, state.force.toLong())
        statement.bindDouble(3, state.evolucaoMensal)
        statement.bindLong(4, state.id)
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
