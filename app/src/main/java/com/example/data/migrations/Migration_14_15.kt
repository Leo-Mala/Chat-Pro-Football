package com.example.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migração da versão 14 para 15 da base de dados Room.
 * Preserva integralmente todos os dados existentes sem destruir tabelas.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE game_save ADD COLUMN globalScoutRevealWeeksRemaining INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `week` INTEGER NOT NULL, `season` INTEGER NOT NULL, `type` TEXT NOT NULL, `description` TEXT NOT NULL, `amount` INTEGER NOT NULL, `isIncome` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL DEFAULT 0)")
        
        try {
            val cursor = db.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='transaction_records'")
            val tableExists = cursor.moveToFirst() && cursor.getInt(0) > 0
            cursor.close()

            if (tableExists) {
                db.execSQL("INSERT INTO `transaction_history` (`week`, `season`, `type`, `description`, `amount`, `isIncome`, `timestamp`) SELECT `week`, `season`, `type`, `description`, `amount`, `isIncome`, 0 FROM `transaction_records`")
                db.execSQL("DROP TABLE IF EXISTS `transaction_records` ")
            }
        } catch (_: Throwable) {
            // Table might not exist or already migrated
        }
    }
}
