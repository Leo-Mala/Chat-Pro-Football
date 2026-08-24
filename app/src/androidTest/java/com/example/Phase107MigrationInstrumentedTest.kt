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

@RunWith(AndroidJUnit4::class)
class Phase107MigrationInstrumentedTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun everySupportedMigrationEdgeAndRetainedSchemaReachCurrentOnRealAndroidSQLite() {
        val currentVersion = APP_DATABASE_SCHEMA_VERSION
        val minimumVersion = AppDatabase.MINIMUM_AUTOMATICALLY_MIGRATABLE_VERSION
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(minimumVersion < currentVersion)
        assertTrue(minimumVersion >= EARLIEST_RECONSTRUCTABLE_SCHEMA_VERSION)
        assertTrue(FIRST_RETAINED_EXPORTED_SCHEMA_VERSION in minimumVersion until currentVersion)

        // ALL_MIGRATIONS must be exactly the supported consecutive chain. Duplicate starts,
        // shortcut edges (for example 14 -> current) and unrelated outgoing edges are forbidden.
        val actualEdges = AppDatabase.ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        val expectedEdges = (minimumVersion until currentVersion).map { start ->
            start to (start + 1)
        }
        assertEquals(
            "Room migration registry must contain exactly one consecutive edge per supported version",
            expectedEdges,
            actualEdges
        )
        assertEquals(
            "Room migration registry contains duplicate start versions",
            actualEdges.size,
            actualEdges.map { it.first }.distinct().size
        )

        // V17 is the oldest exported JSON still retained. V14-V16 are reconstructed as real
        // Android SQLite databases by reversing only the schema changes introduced by 14->17.
        // This lets MigrationTestHelper exercise the production Room-selected path from the
        // declared support floor instead of silently skipping the oldest supported saves.
        // Every starting database also receives rows in both an historically renamed table and
        // the long-lived evolution table. Structural validation alone is insufficient: these
        // sentinels prove that each complete Room-selected path preserves real payload data.
        for (startVersion in minimumVersion until currentVersion) {
            val databaseName = "supported_android_migration_${startVersion}_${currentVersion}.db"
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
                    val userVersion = migrated.query("PRAGMA user_version").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        cursor.getInt(0)
                    }
                    assertEquals(currentVersion, userVersion)
                    migrated.query("PRAGMA integrity_check").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("ok", cursor.getString(0))
                    }
                    assertHistoricalRowsPreserved(migrated, sentinel)
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
            // V16 did not yet contain the two V17 transfer/loan tables.
            database.execSQL("DROP TABLE IF EXISTS `transfer_installments`")
            database.execSQL("DROP TABLE IF EXISTS `player_loans`")

            if (version <= 15) {
                restoreV15EmbeddedPlayerAttributes(database)
            }
            if (version <= 14) {
                restoreV14GameSaveAndTransactions(database)
            }
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
            week = week,
            season = season,
            type = type,
            description = description,
            amount = amount,
            isIncome = isIncome,
            timestamp = timestamp,
            jogadorId = jogadorId,
            data = data,
            atributo = atributo,
            valorAntigo = valorAntigo,
            valorNovo = valorNovo
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
        val v15CreateSql = v17CreateSql.replace(
            "`atributos` TEXT NOT NULL",
            embeddedColumns
        )
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

    private fun tableCreateSql(database: SupportSQLiteDatabase, table: String): String {
        return database.query(
            "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Missing table $table in retained V17 fixture" }
            cursor.getString(0)
        }
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
