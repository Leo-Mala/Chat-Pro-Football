package com.example.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.migrations.MIGRATION_20_21
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration20To21RelationalIntegrityTest {

    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }
    private val databaseName = "migration-v20-v21-relational.db"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `v20 to v21 preserves rows normalizes free agents and materializes legacy fixture teams`() {
        val helper = createV20Database()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO teams (id,name,city,state,country,division,isPlayerControlled,rating,stadiumName,rivalTeamId,trainingCenterLevel) VALUES (1,'A','A','BR','Brasil',1,1,80,'Arena A',0,1)")
        db.execSQL("INSERT INTO teams (id,name,city,state,country,division,isPlayerControlled,rating,stadiumName,rivalTeamId,trainingCenterLevel) VALUES (2,'B','B','BR','Brasil',1,0,75,'Arena B',0,1)")

        insertPlayer(db, id = 11L, teamId = 1L, originalTeamId = 0L, name = "Contratado")
        insertPlayer(db, id = 12L, teamId = 0L, originalTeamId = 0L, name = "Livre")
        insertPlayer(db, id = 13L, teamId = 999L, originalTeamId = 999L, name = "Órfão Legado")

        db.execSQL("INSERT INTO fixtures (id,season,week,homeTeamId,awayTeamId,competitionType,isPlayed,matchSlot) VALUES (101,2026,1,1,2,'SERIE_A',0,'WEEKEND')")
        db.execSQL("INSERT INTO fixtures (id,season,week,homeTeamId,awayTeamId,competitionType,isPlayed,matchSlot) VALUES (102,2029,42,900123,1,'WORLD_CUP',0,'MIDWEEK')")

        assertEquals(3L, count(db, "players"))
        assertEquals(2L, count(db, "fixtures"))
        assertEquals(2L, count(db, "teams"))

        MIGRATION_20_21.migrate(db)

        assertEquals("Migration não pode perder Player", 3L, count(db, "players"))
        assertEquals("Migration não pode perder Fixture", 2L, count(db, "fixtures"))
        assertEquals("Fixture legado deve materializar somente o Team faltante", 3L, count(db, "teams"))

        db.query("SELECT teamId,originalTeamId FROM players WHERE id=11").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertTrue(cursor.isNull(1))
        }
        db.query("SELECT teamId,originalTeamId FROM players WHERE id=12").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue("teamId=0 deve migrar para NULL", cursor.isNull(0))
            assertTrue(cursor.isNull(1))
        }
        db.query("SELECT teamId,originalTeamId FROM players WHERE id=13").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue("Referência órfã legada deve virar Free Agent, sem inventar Team", cursor.isNull(0))
            assertTrue(cursor.isNull(1))
        }

        db.query("SELECT name,country FROM teams WHERE id=900123").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Clube Legado 900123", cursor.getString(0))
            assertEquals("Mundial", cursor.getString(1))
        }

        assertForeignKey(db, "players", from = "teamId", table = "teams", onDelete = "SET NULL")
        assertForeignKey(db, "fixtures", from = "homeTeamId", table = "teams", onDelete = "NO ACTION")
        assertForeignKey(db, "fixtures", from = "awayTeamId", table = "teams", onDelete = "NO ACTION")

        db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        db.query("PRAGMA integrity_check").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0).lowercase())
        }

        assertFalse("teamId deve ser nullable no schema V21", columnIsNotNull(db, "players", "teamId"))
        assertFalse("originalTeamId deve ser nullable no schema V21", columnIsNotNull(db, "players", "originalTeamId"))

        db.close()
        helper.close()
    }

    private fun createV20Database(): SupportSQLiteOpenHelper {
        context.deleteDatabase(databaseName)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(20) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE teams (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL, city TEXT NOT NULL, state TEXT NOT NULL,
                            country TEXT NOT NULL, division INTEGER NOT NULL,
                            isPlayerControlled INTEGER NOT NULL, rating INTEGER NOT NULL,
                            stadiumName TEXT NOT NULL, logoUrl TEXT, rivalTeamId INTEGER NOT NULL,
                            colorHex TEXT, trainingCenterLevel INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX index_teams_division ON teams (division)")
                    db.execSQL("CREATE INDEX index_teams_country ON teams (country)")
                    createV20Players(db)
                    createV20Fixtures(db)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private fun createV20Players(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE players (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                teamId INTEGER NOT NULL,
                name TEXT NOT NULL,
                age INTEGER NOT NULL,
                nationality TEXT NOT NULL DEFAULT 'Brasil',
                position TEXT NOT NULL,
                force INTEGER NOT NULL,
                energy INTEGER NOT NULL DEFAULT 100,
                moral INTEGER NOT NULL DEFAULT 75,
                salary INTEGER NOT NULL DEFAULT 10000,
                contractDurationWeeks INTEGER NOT NULL DEFAULT 52,
                isFromAcademy INTEGER NOT NULL DEFAULT 0,
                careerApps INTEGER NOT NULL DEFAULT 0,
                careerGoals INTEGER NOT NULL DEFAULT 0,
                imageUrl TEXT,
                injuryWeeksRemaining INTEGER NOT NULL DEFAULT 0,
                suspensionWeeksRemaining INTEGER NOT NULL DEFAULT 0,
                yellowCardsAccumulated INTEGER NOT NULL DEFAULT 0,
                isStarter INTEGER NOT NULL DEFAULT 0,
                isOnLoan INTEGER NOT NULL DEFAULT 0,
                loanWeeksRemaining INTEGER NOT NULL DEFAULT 0,
                originalTeamId INTEGER NOT NULL DEFAULT 0,
                careerAssists INTEGER NOT NULL DEFAULT 0,
                careerTackles INTEGER NOT NULL DEFAULT 0,
                careerSaves INTEGER NOT NULL DEFAULT 0,
                ratingSum REAL NOT NULL DEFAULT 0.0,
                ratingCount INTEGER NOT NULL DEFAULT 0,
                maxHistoricalForce INTEGER NOT NULL DEFAULT 0,
                market_value INTEGER NOT NULL DEFAULT 0,
                min_price INTEGER NOT NULL DEFAULT 0,
                max_price INTEGER NOT NULL DEFAULT 0,
                demand_level TEXT NOT NULL DEFAULT 'medium',
                finishing INTEGER NOT NULL DEFAULT 50,
                passing INTEGER NOT NULL DEFAULT 50,
                pace INTEGER NOT NULL DEFAULT 50,
                strength INTEGER NOT NULL DEFAULT 50,
                vision INTEGER NOT NULL DEFAULT 50,
                defense INTEGER NOT NULL DEFAULT 50,
                scoutedLevel INTEGER NOT NULL DEFAULT 0,
                atributosJson TEXT,
                atributos TEXT NOT NULL DEFAULT '{}',
                potential INTEGER NOT NULL DEFAULT 80,
                gols INTEGER NOT NULL DEFAULT 0,
                assistencias INTEGER NOT NULL DEFAULT 0,
                partidasDisputadas INTEGER NOT NULL DEFAULT 0,
                minutosJogados INTEGER NOT NULL DEFAULT 0,
                mediaNotas REAL NOT NULL DEFAULT 0.0,
                focoTreino TEXT,
                condicao INTEGER NOT NULL DEFAULT 100,
                evolucaoMensal REAL NOT NULL DEFAULT 0.0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_players_teamId_position_force ON players (teamId,position,force)")
        db.execSQL("CREATE INDEX index_players_teamId_isStarter ON players (teamId,isStarter)")
        db.execSQL("CREATE INDEX index_players_originalTeamId ON players (originalTeamId)")
    }

    private fun createV20Fixtures(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE fixtures (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                season INTEGER NOT NULL,
                week INTEGER NOT NULL,
                homeTeamId INTEGER NOT NULL,
                awayTeamId INTEGER NOT NULL,
                homeScore INTEGER,
                awayScore INTEGER,
                homePenalties INTEGER,
                awayPenalties INTEGER,
                competitionType TEXT NOT NULL,
                isPlayed INTEGER NOT NULL,
                matchEventsJson TEXT,
                matchSlot TEXT NOT NULL DEFAULT 'WEEKEND'
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_fixtures_season ON fixtures (season)")
        db.execSQL("CREATE INDEX index_fixtures_week ON fixtures (week)")
        db.execSQL("CREATE INDEX index_fixtures_homeTeamId ON fixtures (homeTeamId)")
        db.execSQL("CREATE INDEX index_fixtures_awayTeamId ON fixtures (awayTeamId)")
        db.execSQL("CREATE INDEX index_fixtures_competitionType ON fixtures (competitionType)")
        db.execSQL("CREATE INDEX index_fixtures_season_week ON fixtures (season,week)")
        db.execSQL("CREATE INDEX index_fixtures_season_week_matchSlot ON fixtures (season,week,matchSlot)")
    }

    private fun insertPlayer(db: SupportSQLiteDatabase, id: Long, teamId: Long, originalTeamId: Long, name: String) {
        db.execSQL(
            "INSERT INTO players (id,teamId,name,age,position,force,originalTeamId) VALUES (?,?,?,?,?,?,?)",
            arrayOf(id, teamId, name, 24, "MEI", 70, originalTeamId)
        )
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Long =
        db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun columnIsNotNull(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) return@use cursor.getInt(notNullIndex) == 1
            }
            error("Coluna $column não encontrada em $table")
        }

    private fun assertForeignKey(
        db: SupportSQLiteDatabase,
        tableName: String,
        from: String,
        table: String,
        onDelete: String
    ) {
        var found = false
        db.query("PRAGMA foreign_key_list($tableName)").use { cursor ->
            val tableIndex = cursor.getColumnIndexOrThrow("table")
            val fromIndex = cursor.getColumnIndexOrThrow("from")
            val deleteIndex = cursor.getColumnIndexOrThrow("on_delete")
            while (cursor.moveToNext()) {
                if (cursor.getString(fromIndex) == from && cursor.getString(tableIndex) == table) {
                    assertEquals(onDelete, cursor.getString(deleteIndex))
                    found = true
                }
            }
        }
        assertTrue("FK $tableName.$from -> $table deve existir", found)
    }
}
