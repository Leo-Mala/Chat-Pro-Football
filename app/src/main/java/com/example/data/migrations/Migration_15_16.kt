package com.example.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migração da versão 15 para 16 da base de dados Room.
 * Garante a existência da tabela transaction_history e migra todos os registros
 * antigos de transaction_records de forma idempotente antes de executar o DROP TABLE.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Cria a nova tabela transaction_history caso não exista
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `transaction_history` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `week` INTEGER NOT NULL, 
                `season` INTEGER NOT NULL, 
                `type` TEXT NOT NULL, 
                `description` TEXT NOT NULL, 
                `amount` INTEGER NOT NULL, 
                `isIncome` INTEGER NOT NULL, 
                `timestamp` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // 2. Verifica se a tabela antiga transaction_records existe antes de copiar os dados
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='transaction_records'")
        val tableExists = cursor.use { it.count > 0 }

        if (tableExists) {
            db.execSQL("""
                INSERT INTO `transaction_history` (`week`, `season`, `type`, `description`, `amount`, `isIncome`, `timestamp`)
                SELECT 
                    COALESCE(`week`, 1), 
                    COALESCE(`season`, 1), 
                    COALESCE(`type`, 'GERAL'), 
                    COALESCE(`description`, 'Registro Histórico'), 
                    COALESCE(`amount`, 0), 
                    COALESCE(`isIncome`, 0), 
                    COALESCE(`timestamp`, 0)
                FROM `transaction_records`
            """.trimIndent())
        }

        // 3. Remove a tabela antiga com segurança
        db.execSQL("DROP TABLE IF EXISTS `transaction_records` ")
    }
}
