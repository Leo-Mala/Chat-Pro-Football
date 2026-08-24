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
 * Additive payload certification for tables that are physically rebuilt by historical migrations.
 * The structural migration corpus stays frozen; this class proves create/copy/drop/rename paths do
 * not silently lose representative player or fixture career rows on real Android SQLite.
 */
@RunWith(AndroidJUnit4::class)
class Phase107MigrationRebuiltTablesPreservationInstrumentedTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migration15ToCurrentPreservesPlayerAcrossEmbeddedAttributeRebuild() {
        val databaseName = "phase107_player_rebuild_15_${APP_DATABASE_SCHEMA_VERSION}.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)
        try {
            val historical = createHistoricalV15(databaseName)
            try {
                insertCompleteRow(
                    historical,
                    "players",
                    mapOf(
                        "id" to PLAYER_15_ID,
                        "teamId" to 0,
                        "originalTeamId" to 0,
                        "name" to PLAYER_15_NAME,
                        "age" to 27,
                        "force" to 73,
                        "attr_reflexos" to 61,
                        "attr_passe" to 67,
                    )
                )
            } finally {
                historical.close()
            }

            val migrated = migrationHelper.runMigrationsAndValidate(
                databaseName,
                APP_DATABASE_SCHEMA_VERSION,
                true,
                *AppDatabase.ALL_MIGRATIONS
            )
            try {
                migrated.query(
                    "SELECT `name`,`age`,`force`,`atributos` FROM `players` WHERE `id` = ?",
                    arrayOf(PLAYER_15_ID)
                ).use { cursor ->
                    assertTrue("V15 player sentinel is missing after migration", cursor.moveToFirst())
                    assertEquals(PLAYER_15_NAME, cursor.getString(0))
                    assertEquals(27, cursor.getInt(1))
                    assertEquals(73, cursor.getInt(2))
                    val atributos = cursor.getString(3)
                    assertTrue("Embedded reflexos value was not serialized", atributos.contains("\"reflexos\":61"))
                    assertTrue("Embedded passe value was not serialized", atributos.contains("\"passe\":67"))
                    assertTrue("V15 player sentinel was duplicated", !cursor.moveToNext())
                }
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migration20ToCurrentPreservesPlayerAndFixtureAcrossForeignKeyRebuilds() {
        val databaseName = "phase107_relational_rebuild_20_${APP_DATABASE_SCHEMA_VERSION}.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)
        try {
            val historical = migrationHelper.createDatabase(databaseName, 20)
            try {
                insertCompleteRow(
                    historical,
                    "players",
                    mapOf(
                        "id" to PLAYER_20_ID,
                        "teamId" to 0,
                        "originalTeamId" to 0,
                        "name" to PLAYER_20_NAME,
                        "age" to 24,
                        "force" to 81,
                        "atributos" to "{}",
                    )
                )
                insertCompleteRow(
                    historical,
                    "fixtures",
                    mapOf(
                        "id" to FIXTURE_ID,
                        "season" to 2088,
                        "week" to 19,
                        "homeTeamId" to HOME_TEAM_ID,
                        "awayTeamId" to AWAY_TEAM_ID,
                        "homeScore" to 3,
                        "awayScore" to 2,
                        "competitionType" to "LEAGUE",
                        "isPlayed" to 1,
                        "matchSlot" to "WEEKEND",
                    )
                )
            } finally {
                historical.close()
            }

            val migrated = migrationHelper.runMigrationsAndValidate(
                databaseName,
                APP_DATABASE_SCHEMA_VERSION,
                true,
                *AppDatabase.ALL_MIGRATIONS
            )
            try {
                migrated.query(
                    "SELECT `name`,`age`,`force`,`teamId` FROM `players` WHERE `id` = ?",
                    arrayOf(PLAYER_20_ID)
                ).use { cursor ->
                    assertTrue("V20 player sentinel is missing after migration", cursor.moveToFirst())
                    assertEquals(PLAYER_20_NAME, cursor.getString(0))
                    assertEquals(24, cursor.getInt(1))
                    assertEquals(81, cursor.getInt(2))
                    assertTrue("Legacy teamId=0 must normalize to NULL", cursor.isNull(3))
                    assertTrue("V20 player sentinel was duplicated", !cursor.moveToNext())
                }

                migrated.query(
                    """
                    SELECT `season`,`week`,`homeTeamId`,`awayTeamId`,`homeScore`,`awayScore`,
                           `competitionType`,`isPlayed`,`matchSlot`
                    FROM `fixtures` WHERE `id` = ?
                    """.trimIndent(),
                    arrayOf(FIXTURE_ID)
                ).use { cursor ->
                    assertTrue("V20 fixture sentinel is missing after migration", cursor.moveToFirst())
                    assertEquals(2088, cursor.getInt(0))
                    assertEquals(19, cursor.getInt(1))
                    assertEquals(HOME_TEAM_ID, cursor.getLong(2))
                    assertEquals(AWAY_TEAM_ID, cursor.getLong(3))
                    assertEquals(3, cursor.getInt(4))
                    assertEquals(2, cursor.getInt(5))
                    assertEquals("LEAGUE", cursor.getString(6))
                    assertEquals(1, cursor.getInt(7))
                    assertEquals("WEEKEND", cursor.getString(8))
                    assertTrue("V20 fixture sentinel was duplicated", !cursor.moveToNext())
                }

                for (teamId in listOf(HOME_TEAM_ID, AWAY_TEAM_ID)) {
                    migrated.query("SELECT `id` FROM `teams` WHERE `id` = ?", arrayOf(teamId)).use { cursor ->
                        assertTrue("Fixture reference $teamId was not materialized", cursor.moveToFirst())
                        assertEquals(teamId, cursor.getLong(0))
                    }
                }
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    private fun createHistoricalV15(databaseName: String): SupportSQLiteDatabase {
        val database = migrationHelper.createDatabase(databaseName, 17)
        try {
            database.execSQL("DROP TABLE IF EXISTS `transfer_installments`")
            database.execSQL("DROP TABLE IF EXISTS `player_loans`")
            val v17CreateSql = tableCreateSql(database, "players")
            val embeddedColumns = HISTORICAL_ATTRIBUTE_NAMES.joinToString(", ") { name ->
                "`attr_$name` INTEGER NOT NULL DEFAULT 50"
            }
            val v15CreateSql = v17CreateSql.replace("`atributos` TEXT NOT NULL", embeddedColumns)
            check(v15CreateSql != v17CreateSql) { "Could not reconstruct V15 player schema" }
            database.execSQL("DROP TABLE `players`")
            database.execSQL(v15CreateSql)
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_players_teamId` ON `players` (`teamId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_players_position` ON `players` (`position`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_players_isStarter` ON `players` (`isStarter`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_players_originalTeamId` ON `players` (`originalTeamId`)")
            database.version = 15
            return database
        } catch (error: Throwable) {
            database.close()
            throw error
        }
    }

    private fun insertCompleteRow(
        database: SupportSQLiteDatabase,
        table: String,
        overrides: Map<String, Any?>
    ) {
        val columns = mutableListOf<ColumnInfo>()
        database.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val typeIndex = cursor.getColumnIndex("type")
            val notNullIndex = cursor.getColumnIndex("notnull")
            val defaultIndex = cursor.getColumnIndex("dflt_value")
            while (cursor.moveToNext()) {
                columns += ColumnInfo(
                    name = cursor.getString(nameIndex),
                    type = cursor.getString(typeIndex) ?: "",
                    notNull = cursor.getInt(notNullIndex) != 0,
                    hasDefault = !cursor.isNull(defaultIndex),
                )
            }
        }
        check(columns.isNotEmpty()) { "Missing table $table" }

        val names = mutableListOf<String>()
        val values = mutableListOf<Any?>()
        for (column in columns) {
            if (overrides.containsKey(column.name)) {
                names += column.name
                values += overrides[column.name]
                continue
            }
            if (column.hasDefault) continue
            if (!column.notNull) continue
            names += column.name
            values += genericValue(column.name, column.type)
        }
        val placeholders = names.joinToString(",") { "?" }
        val quotedNames = names.joinToString(",") { "`$it`" }
        database.execSQL(
            "INSERT INTO `$table` ($quotedNames) VALUES ($placeholders)",
            values.toTypedArray()
        )
    }

    private fun genericValue(name: String, type: String): Any = when {
        name == "name" -> "Phase107 Sentinel"
        name == "nationality" -> "BR"
        name == "position" -> "MC"
        name == "demand_level" -> "NORMAL"
        name == "competitionType" -> "LEAGUE"
        name == "matchSlot" -> "WEEKEND"
        name == "atributos" -> "{}"
        name == "country" -> "Brasil"
        name == "city" -> "Teste"
        name == "state" -> "TS"
        name == "stadiumName" -> "Arena Teste"
        type.uppercase().contains("INT") -> 1
        type.uppercase().let { it.contains("REAL") || it.contains("FLOA") || it.contains("DOUB") } -> 1.0
        else -> "phase107"
    }

    private fun tableCreateSql(database: SupportSQLiteDatabase, table: String): String =
        database.query(
            "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Missing table $table" }
            cursor.getString(0)
        }

    private data class ColumnInfo(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val hasDefault: Boolean,
    )

    private companion object {
        const val PLAYER_15_ID = 9_150_015L
        const val PLAYER_20_ID = 9_200_020L
        const val FIXTURE_ID = 8_200_020L
        const val HOME_TEAM_ID = 8_100_001L
        const val AWAY_TEAM_ID = 8_100_002L
        const val PLAYER_15_NAME = "Phase107 V15 Player Sentinel"
        const val PLAYER_20_NAME = "Phase107 V20 Player Sentinel"

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
