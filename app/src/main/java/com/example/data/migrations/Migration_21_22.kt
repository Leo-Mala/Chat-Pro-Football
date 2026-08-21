package com.example.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * V21 -> V22
 *
 * Adds an index on historico_evolucao.data so monthly retry protection can query only the
 * requested period without scanning the full, ever-growing evolution audit table.
 *
 * This migration is intentionally non-destructive: no rows are rewritten or removed.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val beforeCount = db.query("SELECT COUNT(*) FROM historico_evolucao").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_historico_evolucao_data` " +
                "ON `historico_evolucao` (`data`)"
        )

        val afterCount = db.query("SELECT COUNT(*) FROM historico_evolucao").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
        check(afterCount == beforeCount) {
            "Migration 21->22 alterou a quantidade de registros de evolução."
        }

        db.query("PRAGMA integrity_check").use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)) {
                "Migration 21->22 terminou com integrity_check inválido."
            }
        }
    }
}
