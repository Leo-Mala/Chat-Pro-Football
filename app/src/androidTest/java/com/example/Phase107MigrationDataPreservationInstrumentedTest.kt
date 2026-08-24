package com.example

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.APP_DATABASE_SCHEMA_VERSION
import com.example.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Additive migration-data certification.
 *
 * The frozen structural migration test proves schema/path validity from every supported version.
 * This companion test keeps that corpus immutable while adding real payload sentinels so a
 * migration that silently drops or rewrites user rows cannot pass on empty databases.
 */
@RunWith(AndroidJUnit4::class)
class Phase107MigrationDataPreservationInstrumentedTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun everySupportedRoomPathPreservesRepresentativeHistoricalRows() {
        val currentVersion = APP_DATABASE_SCHEMA_VERSION
        val minimumVersion = AppDatabase.MINIMUM_AUTOMATICALLY_MIGRATABLE_VERSION
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(minimumVersion >= EARLIEST_RECONSTRUCTABLE_SCHEMA_VERSION)
        assertTrue(minimumVersion < currentVersion)

        for (startVersion in minimumVersion until currentVersion) {
            val databaseName = "supported_android_data_migration_${startVersion}_${currentVersion}.db"
            context.deleteDatabase(databaseName)
            try {
                val historical = if (startVersion < FIRST_RETAINED_EXPORTED_SCHEMA_VERSION) {
                    createHistoricalDatabaseBeforeV17(databaseName, startVersion)
                } else {
                    migrationHelper.createDatabase(databaseName, startVersion)
                }
                val sentinel = try {
                    seedHistoricalRows(historical, startVersion)
                } finally {
                    historical.close()
                }

                val migrated = migrationHelper.runMigrationsAndValidate(
                    databaseName,
                    currentVersion,
                    true,
                    *AppDatabase.ALL_MIGRATIONS
                )
                try {
                    assertHistoricalRowsPreserved(migrated, sentinel)
                    migrated.query("PRAGMA integrity_check").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("ok", cursor.getString(0))
                    }
                } finally {
                    migrated.close()
                }
            } finally {
                context.deleteDatabase(databaseName)
            }
        }
    }

    private fun createHistoricalDatabaseBeforeV17(
        databaseName: String,
        version: Int
    ): SupportSQLiteDatabase {
        require(version in EARLIEST_RECONSTRUCTABLE_SCHEMA_VERSION until FIRST_RETAINED_EXPORTED_SCHEMA_VERSION)
        val database = migrationHelper.createDatabase(databaseName, FIRST_RETAINED_EXPORTED_SCHEMA_VERSION)
        try {
            database.execSQL("DROP TABLE IF EXISTS `transfer_installments`")
            database.execSQL("DROP TABLE IF EXISTS `player_loans`")
            if (version <= 15) restoreV15EmbeddedPlayerAttributes(database)
            if (version <= 14) restoreV14GameSaveAndTransactions(database)
            database.version = version
            return database
        } catch (error: Throwable) {
            database.close()
            throw error
        }
    }

    private fun seedHistoricalRows(
        database: SupportSQLiteDatabase,
        startVersion: Int
    ): HistoricalSentinel {
        val week = 7 + startVersion
        val season = 2040 + startVersion
        val type = "phase107-migration-$startVersion"
        val description = "sentinel-transaction-$startVersion"
        val amount = 900_000L + startVersion
        val isIncome = startVersion % 2
        val timestamp = if (startVersion == 14) 0L else 1_700_000_000_000L + startVersion

        if (startVersion == 14) {
            database.execSQL(
                """
                INSERT INTO `transaction_records`
                    (`week`, `season`, `type`, `description`, `amount`, `isIncome`)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(week, season, type, description, amount, isIncome)
            )
        } else {
            database.execSQL(
                """
                INSERT INTO `transaction_history`
                    (`week`, `season`, `type`, `description`, `amount`, `isIncome`, `timestamp`)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(week, season, type, description, amount, isIncome, timestamp)
            )
        }

        val jogadorId = 9_000_000L + startVersion
        val data = "2099-${(startVersion % 12 + 1).toString().padStart(2, '0')}"
        val atributo = "phase107-$startVersion"
        val valorAntigo = 40 + startVersion
        val valorNovo = valorAntigo + 1
        database.execSQL(
            """
            INSERT INTO `historico_evolucao`
                (`jogadorId`, `data`, `atributo`, `valorAntigo`, `valorNovo`)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(jogadorId, data, atributo, valorAntigo, valorNovo)
        )

        return HistoricalSentinel(
            week, season, type, description, amount, isIncome, timestamp,
            jogadorId, data, atributo, valorAntigo, valorNovo
        )
    }

    private fun assertHistoricalRowsPreserved(
        database: SupportSQLiteDatabase,
        sentinel: HistoricalSentinel
    ) {
        database.query(
            """
            SELECT `week`, `season`, `type`, `description`, `amount`, `isIncome`, `timestamp`
            FROM `transaction_history`
            WHERE `description` = ?
            """.trimIndent(),
            arrayOf(sentinel.description)
        ).use { cursor ->
            assertTrue("Migrated transaction sentinel is missing", cursor.moveToFirst())
            assertEquals(sentinel.week, cursor.getInt(0))
            assertEquals(sentinel.season, cursor.getInt(1))
            assertEquals(sentinel.type, cursor.getString(2))
            assertEquals(sentinel.description, cursor.getString(3))
            assertEquals(sentinel.amount, cursor.getLong(4))
            assertEquals(sentinel.isIncome, cursor.getInt(5))
            assertEquals(sentinel.timestamp, cursor.getLong(6))
            assertTrue("Migrated transaction sentinel was duplicated", !cursor.moveToNext())
        }

        database.query(
            """
            SELECT `jogadorId`, `data`, `atributo`, `valorAntigo`, `valorNovo`
            FROM `historico_evolucao`
            WHERE `jogadorId` = ? AND `atributo` = ?
            """.trimIndent(),
            arrayOf(sentinel.jogadorId, sentinel.atributo)
        ).use { cursor ->
            assertTrue("Migrated evolution sentinel is missing", cursor.moveToFirst())
            assertEquals(sentinel.jogadorId, cursor.getLong(0))
            assertEquals(sentinel.data, cursor.getString(1))
            assertEquals(sentinel.atributo, cursor.getString(2))
            assertEquals(sentinel.valorAntigo, cursor.getInt(3))
            assertEquals(sentinel.valorNovo, cursor.getInt(4))
            assertTrue("Migrated evolution sentinel was duplicated", !cursor.moveToNext())
        }
    }

    private fun restoreV15EmbeddedPlayerAttributes(database: SupportSQLiteDatabase) {
        val v17CreateSql = tableCreateSql(database, "players")
        val embeddedColumns = HISTORICAL_ATTRIBUTE_NAMES.joinToString(", ") { name ->
            "`attr_$name` INTEGER NOT NULL DEFAULT 50"
        }
        val v15CreateSql = v17CreateSql.replace("`atributos` TEXT NOT NULL", embeddedColumns)
        require(v15CreateSql != v17CreateSql) { "Could not reconstruct V15 embedded player attributes" }
        database.execSQL("DROP TABLE `players`")
        database.execSQL(v15CreateSql)
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_players_teamId` ON `players` (`teamId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_players_position` ON `players` (`position`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_players_isStarter` ON `players` (`isStarter`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_players_originalTeamId` ON `players` (`originalTeamId`)")
    }

    private fun restoreV14GameSaveAndTransactions(database: SupportSQLiteDatabase) {
        val v17CreateSql = tableCreateSql(database, "game_save")
        val v14CreateSql = v17CreateSql.replace(
            Regex(",?\\s*`globalScoutRevealWeeksRemaining` INTEGER NOT NULL(?: DEFAULT \\d+)?"),
            ""
        )
        require(v14CreateSql != v17CreateSql) { "Could not reconstruct V14 game_save" }
        database.execSQL("DROP TABLE `game_save`")
        database.execSQL(v14CreateSql)
        database.execSQL("DROP TABLE IF EXISTS `transaction_history`")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `transaction_records` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `week` INTEGER NOT NULL,
                `season` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `amount` INTEGER NOT NULL,
                `isIncome` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun tableCreateSql(database: SupportSQLiteDatabase, table: String): String =
        database.query(
            "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Missing table $table in retained V17 fixture" }
            cursor.getString(0)
        }

    private data class HistoricalSentinel(
        val week: Int,
        val season: Int,
        val type: String,
        val description: String,
        val amount: Long,
        val isIncome: Int,
        val timestamp: Long,
        val jogadorId: Long,
        val data: String,
        val atributo: String,
        val valorAntigo: Int,
        val valorNovo: Int
    )

    private companion object {
        const val EARLIEST_RECONSTRUCTABLE_SCHEMA_VERSION = 14
        const val FIRST_RETAINED_EXPORTED_SCHEMA_VERSION = 17
        val HISTORICAL_ATTRIBUTE_NAMES = listOf(
            "reflexos", "pegada", "umContraUm", "saidaDeGol", "lancamento",
            "desarme", "marcacao", "cabeceio", "passeCurto", "cruzamento",
            "drible", "passe", "primeiroToque", "finalizacao", "chuteDeLonge",
            "controleBola", "posicionamento", "concentracao", "sangueFrio",
            "antecipacao", "bravura", "trabalhoEquipe", "decisao", "semBola",
            "visaoJogo", "criatividade", "agressividade", "lideranca",
            "regularidade", "agilidade", "impulsao", "forca", "velocidade",
            "aceleracao", "resistencia"
        )
    }
}
