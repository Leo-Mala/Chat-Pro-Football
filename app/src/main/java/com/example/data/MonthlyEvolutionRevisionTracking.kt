package com.example.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Persisted monotonic epochs used to decide whether the expensive monthly universe proof must be
 * revalidated. The proof itself remains the fail-closed authority whenever these epochs changed
 * or the tracking infrastructure cannot be proven intact.
 *
 * The auxiliary state table and triggers intentionally live outside Room's entity schema. They are
 * installed idempotently before the first player read of monthly preparation, without changing
 * user_version, rebuilding a save or backfilling Player rows.
 */
data class MonthlyEvolutionRevisionSnapshot(
    val footballRevision: Long,
    val rosterRevision: Long
)

private const val REVISION_STATE_TABLE = "monthly_player_revision_state"
private const val INSERT_TRIGGER = "monthly_player_revision_after_insert"
private const val DELETE_TRIGGER = "monthly_player_revision_after_delete"
private const val ROSTER_TRIGGER = "monthly_player_revision_after_roster_change"
private const val FOOTBALL_TRIGGER = "monthly_player_revision_after_football_change"
private const val ROSTER_VALIDATION_BATCH_SIZE = 4096

/**
 * Row creation changes both roster identity and the football-input universe. Advancing both epochs
 * also makes INSERT OR REPLACE safe even when SQLite resolves a same-primary-key replacement
 * without an UPDATE trigger firing.
 */
private val INSERT_TRIGGER_SQL = """
    CREATE TRIGGER $INSERT_TRIGGER
    AFTER INSERT ON players
    BEGIN
        UPDATE $REVISION_STATE_TABLE
        SET footballRevision = footballRevision + 1,
            rosterRevision = rosterRevision + 1
        WHERE id = 1;
    END
""".trimIndent()

/** Row removal changes both roster identity and the football-input universe. */
private val DELETE_TRIGGER_SQL = """
    CREATE TRIGGER $DELETE_TRIGGER
    AFTER DELETE ON players
    BEGIN
        UPDATE $REVISION_STATE_TABLE
        SET footballRevision = footballRevision + 1,
            rosterRevision = rosterRevision + 1
        WHERE id = 1;
    END
""".trimIndent()

private val ROSTER_TRIGGER_SQL = """
    CREATE TRIGGER $ROSTER_TRIGGER
    AFTER UPDATE OF id, teamId ON players
    WHEN OLD.id IS NOT NEW.id OR OLD.teamId IS NOT NEW.teamId
    BEGIN
        UPDATE $REVISION_STATE_TABLE
        SET rosterRevision = rosterRevision + 1
        WHERE id = 1;
    END
""".trimIndent()

/**
 * Keep this list in lockstep with updateMonthlyEvolutionStateDigest().
 *
 * `atributos` is the Room-persisted Atributos representation (`atributosStorage` in the digest).
 * teamId is deliberately excluded: roster movement is handled by a separate epoch and can use the
 * id/teamId-only validator when football inputs did not change.
 */
private val FOOTBALL_TRIGGER_SQL = """
    CREATE TRIGGER $FOOTBALL_TRIGGER
    AFTER UPDATE OF age, position, force, potential, minutosJogados, mediaNotas, focoTreino, atributosJson, atributos
    ON players
    WHEN
        OLD.age IS NOT NEW.age OR
        OLD.position IS NOT NEW.position OR
        OLD.force IS NOT NEW.force OR
        OLD.potential IS NOT NEW.potential OR
        OLD.minutosJogados IS NOT NEW.minutosJogados OR
        OLD.mediaNotas IS NOT NEW.mediaNotas OR
        OLD.focoTreino IS NOT NEW.focoTreino OR
        OLD.atributosJson IS NOT NEW.atributosJson OR
        OLD.atributos IS NOT NEW.atributos
    BEGIN
        UPDATE $REVISION_STATE_TABLE
        SET footballRevision = footballRevision + 1
        WHERE id = 1;
    END
""".trimIndent()

private val REQUIRED_TRIGGER_SQL = linkedMapOf(
    INSERT_TRIGGER to INSERT_TRIGGER_SQL,
    DELETE_TRIGGER to DELETE_TRIGGER_SQL,
    ROSTER_TRIGGER to ROSTER_TRIGGER_SQL,
    FOOTBALL_TRIGGER to FOOTBALL_TRIGGER_SQL
)

private fun normalizeSql(sql: String): String =
    sql.trim()
        .replace(Regex("\\s+"), " ")
        .lowercase()

private fun SupportSQLiteDatabase.installMonthlyEvolutionRevisionTracking() {
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS $REVISION_STATE_TABLE (
            id INTEGER NOT NULL PRIMARY KEY,
            footballRevision INTEGER NOT NULL DEFAULT 0,
            rosterRevision INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent()
    )
    execSQL(
        "INSERT OR IGNORE INTO $REVISION_STATE_TABLE(id, footballRevision, rosterRevision) VALUES(1, 0, 0)"
    )

    // Recreate the definitions before opening a new observation window. If an unexpected trigger
    // using our owned prefix exists, it is deliberately left in place so the exact-set integrity
    // check below fails closed instead of silently normalizing unknown infrastructure.
    REQUIRED_TRIGGER_SQL.keys.forEach { trigger ->
        execSQL("DROP TRIGGER IF EXISTS $trigger")
    }
    REQUIRED_TRIGGER_SQL.values.forEach(::execSQL)
}

private fun SupportSQLiteDatabase.hasIntactMonthlyEvolutionRevisionTracking(): Boolean {
    return try {
        val columns = linkedMapOf<String, Pair<String, Int>>()
        query("PRAGMA table_info($REVISION_STATE_TABLE)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            val pkIndex = cursor.getColumnIndexOrThrow("pk")
            while (cursor.moveToNext()) {
                columns[cursor.getString(nameIndex)] =
                    cursor.getString(typeIndex).uppercase() to cursor.getInt(pkIndex)
            }
        }
        if (columns.keys != setOf("id", "footballRevision", "rosterRevision")) return false
        if (columns.values.any { (type, _) -> type != "INTEGER" }) return false
        if (columns["id"]?.second != 1) return false

        val stateRowCount = query("SELECT COUNT(*) FROM $REVISION_STATE_TABLE").use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) == 1
        }
        if (!stateRowCount) return false

        val actualTriggers = linkedMapOf<String, String>()
        query(
            """
            SELECT name, sql
            FROM sqlite_master
            WHERE type = 'trigger'
              AND tbl_name = 'players'
              AND name GLOB 'monthly_player_revision_*'
            ORDER BY name ASC
            """.trimIndent()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                actualTriggers[cursor.getString(0)] =
                    if (cursor.isNull(1)) "" else cursor.getString(1)
            }
        }

        if (actualTriggers.keys != REQUIRED_TRIGGER_SQL.keys.toSet()) return false
        for ((name, expectedSql) in REQUIRED_TRIGGER_SQL) {
            val actualSql = actualTriggers[name] ?: return false
            if (normalizeSql(actualSql) != normalizeSql(expectedSql)) return false
        }

        query(
            "SELECT footballRevision, rosterRevision FROM $REVISION_STATE_TABLE WHERE id = 1"
        ).use { cursor -> cursor.moveToFirst() }
    } catch (_: Exception) {
        false
    }
}

private fun SupportSQLiteDatabase.readMonthlyEvolutionRevisionSnapshotOrNull():
    MonthlyEvolutionRevisionSnapshot? =
    query(
        "SELECT footballRevision, rosterRevision FROM $REVISION_STATE_TABLE WHERE id = 1"
    ).use { cursor ->
        if (!cursor.moveToFirst()) null
        else MonthlyEvolutionRevisionSnapshot(
            footballRevision = cursor.getLong(0),
            rosterRevision = cursor.getLong(1)
        )
    }

/**
 * Starts a new observation window. The single SELECT reads both epochs atomically and is executed
 * before Player count/streaming begins in prepareMonthlyEvolution().
 */
internal fun GameRepository.prepareMonthlyEvolutionRevisionSnapshot():
    MonthlyEvolutionRevisionSnapshot? = runCatching {
    val database = db.openHelper.writableDatabase
    database.installMonthlyEvolutionRevisionTracking()
    if (!database.hasIntactMonthlyEvolutionRevisionTracking()) null
    else database.readMonthlyEvolutionRevisionSnapshotOrNull()
}.getOrNull()

/**
 * Commit-side observation never repairs tracking infrastructure. Missing, extra or modified
 * triggers return null so the caller falls back to the existing SHA-256 universe validation.
 */
internal fun GameRepository.currentMonthlyEvolutionRevisionSnapshotOrNull():
    MonthlyEvolutionRevisionSnapshot? = runCatching {
    val database = db.openHelper.writableDatabase
    if (!database.hasIntactMonthlyEvolutionRevisionTracking()) null
    else database.readMonthlyEvolutionRevisionSnapshotOrNull()
}.getOrNull()

/**
 * When footballRevision is unchanged but rosterRevision moved, football inputs are already proven
 * unchanged by the trigger epoch. Scan only id/teamId to preserve the existing roster-correction
 * semantics without hashing every player again.
 */
internal fun GameRepository.validateMonthlyEvolutionRosterRevisionOnly(
    expected: MonthlyEvolutionUniverseCommitment,
    expectedTrainingCenterLevels: Map<Long, Int>,
    currentTrainingCenterLevels: Map<Long, Int>,
    allowRosterCorrections: Boolean
): MonthlyEvolutionRosterValidation {
    if (expected.size == 0) {
        val count = getMonthlyEvolutionPlayerCount()
        return MonthlyEvolutionRosterValidation(
            valid = count == 0,
            correctionIds = emptySet(),
            currentPlayerCount = count
        )
    }

    val corrections = linkedSetOf<Long>()
    val database = db.openHelper.writableDatabase
    var expectedIndex = 0
    var lastSeenId: Long? = null
    var currentCount = 0
    var valid = true

    while (true) {
        val query = if (lastSeenId == null) {
            """
            SELECT id, teamId
            FROM players
            ORDER BY id ASC
            LIMIT $ROSTER_VALIDATION_BATCH_SIZE
            """.trimIndent()
        } else {
            """
            SELECT id, teamId
            FROM players
            WHERE id > ?
            ORDER BY id ASC
            LIMIT $ROSTER_VALIDATION_BATCH_SIZE
            """.trimIndent()
        }
        val args = lastSeenId?.let { arrayOf<Any>(it) } ?: emptyArray()
        var rowsInBatch = 0
        var batchLastId: Long? = null

        database.query(query, args).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val teamIdIndex = cursor.getColumnIndexOrThrow("teamId")
            while (cursor.moveToNext()) {
                val currentId = cursor.getLong(idIndex)
                val currentTeamId =
                    if (cursor.isNull(teamIdIndex)) null else cursor.getLong(teamIdIndex)
                currentCount++
                rowsInBatch++
                batchLastId = currentId

                while (expectedIndex < expected.size &&
                    expected.playerIds[expectedIndex] < currentId
                ) {
                    // A prepared player disappeared. There is no row left to recalculate.
                    valid = false
                    expectedIndex++
                }

                if (expectedIndex >= expected.size ||
                    currentId < expected.playerIds[expectedIndex]
                ) {
                    // New player: weekly close may recalculate it from the current row.
                    if (allowRosterCorrections) corrections.add(currentId) else valid = false
                    continue
                }

                val expectedTeamId = expected.teamIdAt(expectedIndex)
                if (expectedTeamId != currentTeamId) {
                    if (!allowRosterCorrections) {
                        valid = false
                    } else {
                        val oldLevel =
                            expectedTeamId?.let { expectedTrainingCenterLevels[it] } ?: 1
                        val newLevel =
                            currentTeamId?.let { currentTrainingCenterLevels[it] } ?: 1
                        if (oldLevel != newLevel) corrections.add(currentId)
                    }
                }
                expectedIndex++
            }
        }

        if (rowsInBatch == 0) break
        check(lastSeenId == null || requireNotNull(batchLastId) > lastSeenId!!) {
            "Monthly evolution roster-only keyset did not advance after player id $lastSeenId."
        }
        lastSeenId = batchLastId
        if (rowsInBatch < ROSTER_VALIDATION_BATCH_SIZE) break
    }

    if (expectedIndex != expected.size) valid = false
    return MonthlyEvolutionRosterValidation(
        valid = valid,
        correctionIds = corrections,
        currentPlayerCount = currentCount
    )
}
