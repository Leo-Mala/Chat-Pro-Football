package com.example.data

/**
 * Scalar integrity probes used during normal save load. Room V21 foreign keys prevent new orphan
 * player rows, but migrated/legacy files can still be checked without materializing the Player table.
 */
internal fun GameRepository.getOrphanPlayerCount(): Int {
    val cursor = db.openHelper.writableDatabase.query(
        """
        SELECT COUNT(*) AS orphanCount
        FROM players p
        LEFT JOIN teams t ON t.id = p.teamId
        WHERE p.teamId IS NOT NULL AND t.id IS NULL
        """.trimIndent()
    )
    return cursor.use {
        check(it.moveToFirst())
        it.getInt(it.getColumnIndexOrThrow("orphanCount"))
    }
}
