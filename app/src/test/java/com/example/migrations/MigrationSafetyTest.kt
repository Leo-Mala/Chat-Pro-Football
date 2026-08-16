package com.example.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.migrations.MIGRATION_15_16
import com.example.data.migrations.MIGRATION_17_18
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
class MigrationSafetyTest {

    private val context by lazy {
        ApplicationProvider.getApplicationContext<android.content.Context>()
    }
    private val createdDatabases = mutableSetOf<String>()

    @After
    fun tearDown() {
        createdDatabases.forEach(context::deleteDatabase)
        createdDatabases.clear()
    }

    @Test
    fun migration15To16PreservesEmbeddedPlayerAttributesAndLegacyTransactions() {
        val helper = createLegacyDatabase("migration-v15.db", 15) { db ->
            createHistoricalV15PlayersTable(db)
            db.execSQL(
                """
                CREATE TABLE `transaction_records` (
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
        val db = helper.writableDatabase

        db.execSQL(
            """
            INSERT INTO `players`
                (`id`, `teamId`, `name`, `age`, `position`, `force`,
                 `attr_reflexos`, `attr_finalizacao`, `attr_velocidade`)
            VALUES (101, 10, 'Jogador Histórico', 24, 'ATA', 82, 91, 77, 88)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `transaction_records`
                (`week`, `season`, `type`, `description`, `amount`, `isIncome`)
            VALUES (7, 2027, 'VENDA', 'Venda histórica', 123456, 1)
            """.trimIndent()
        )

        MIGRATION_15_16.migrate(db)

        db.query("SELECT `id`, `name`, `atributos` FROM `players` WHERE `id` = 101").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(101L, cursor.getLong(0))
            assertEquals("Jogador Histórico", cursor.getString(1))
            val json = cursor.getString(2)
            assertTrue(json.contains("\"reflexos\":91"))
            assertTrue(json.contains("\"finalizacao\":77"))
            assertTrue(json.contains("\"velocidade\":88"))
        }

        val columns = tableColumns(db, "players")
        assertTrue("atributos" in columns)
        assertFalse("attr_reflexos" in columns)
        assertFalse("attr_finalizacao" in columns)

        db.query(
            "SELECT `week`, `season`, `amount`, `isIncome`, `timestamp` " +
                "FROM `transaction_history`"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7, cursor.getInt(0))
            assertEquals(2027, cursor.getInt(1))
            assertEquals(123456L, cursor.getLong(2))
            assertEquals(1, cursor.getInt(3))
            // V15 antiga não possuía timestamp nessa tabela legada.
            assertEquals(0L, cursor.getLong(4))
        }
        assertFalse(tableExists(db, "transaction_records"))

        db.close()
        helper.close()
    }

    @Test
    fun migration17To18NormalizesIndexesWithoutChangingRows() {
        val helper = createLegacyDatabase("migration-v17.db", 17) { db ->
            db.execSQL(
                "CREATE TABLE `players` (`id` INTEGER PRIMARY KEY NOT NULL, `teamId` INTEGER NOT NULL, " +
                    "`position` TEXT NOT NULL, `force` INTEGER NOT NULL, `isStarter` INTEGER NOT NULL, " +
                    "`originalTeamId` INTEGER NOT NULL, `name` TEXT NOT NULL)"
            )
            db.execSQL("CREATE INDEX `index_players_teamId` ON `players` (`teamId`)")
            db.execSQL("CREATE INDEX `index_players_position` ON `players` (`position`)")
            db.execSQL("CREATE INDEX `index_players_isStarter` ON `players` (`isStarter`)")
            db.execSQL("CREATE INDEX `index_players_originalTeamId` ON `players` (`originalTeamId`)")

            db.execSQL("CREATE TABLE `teams` (`id` INTEGER PRIMARY KEY NOT NULL, `division` INTEGER NOT NULL, `country` TEXT NOT NULL)")
            db.execSQL(
                "CREATE TABLE `fixtures` (`id` INTEGER PRIMARY KEY NOT NULL, `season` INTEGER NOT NULL, " +
                    "`week` INTEGER NOT NULL, `homeTeamId` INTEGER NOT NULL, `awayTeamId` INTEGER NOT NULL, " +
                    "`competitionType` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE `transfer_installments` (`id` INTEGER PRIMARY KEY NOT NULL, " +
                    "`buyerTeamId` INTEGER NOT NULL, `sellerTeamId` INTEGER NOT NULL, `status` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE `player_loans` (`id` INTEGER PRIMARY KEY NOT NULL, `playerId` INTEGER NOT NULL, " +
                    "`ownerTeamId` INTEGER NOT NULL, `borrowerTeamId` INTEGER NOT NULL, `status` TEXT NOT NULL)"
            )
        }
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO `players` (`id`,`teamId`,`position`,`force`,`isStarter`,`originalTeamId`,`name`) " +
                "VALUES (7, 2, 'MEI', 79, 1, 2, 'Persistente')"
        )

        MIGRATION_17_18.migrate(db)

        db.query("SELECT `name`, `force` FROM `players` WHERE `id` = 7").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Persistente", cursor.getString(0))
            assertEquals(79, cursor.getInt(1))
        }

        val indexes = indexNames(db, "players")
        assertTrue("index_players_teamId_position_force" in indexes)
        assertTrue("index_players_teamId_isStarter" in indexes)
        assertTrue("index_players_originalTeamId" in indexes)
        assertFalse("index_players_teamId" in indexes)
        assertFalse("index_players_position" in indexes)
        assertFalse("index_players_isStarter" in indexes)

        db.close()
        helper.close()
    }

    @Test
    fun unsupportedOldVersionFailsWithoutDeletingDatabase() {
        assertUnsupportedVersionPreservesSentinel("unsupported-v13.db", 13)
    }

    @Test
    fun downgradeFailsWithoutDeletingDatabase() {
        assertUnsupportedVersionPreservesSentinel("unsupported-v19.db", 19)
    }

    private fun assertUnsupportedVersionPreservesSentinel(name: String, version: Int) {
        val helper = createLegacyDatabase(name, version) { db ->
            db.execSQL("CREATE TABLE `migration_guard` (`value` TEXT NOT NULL)")
            db.execSQL("INSERT INTO `migration_guard` (`value`) VALUES ('PRESERVE_ME')")
        }
        helper.writableDatabase
        helper.close()

        var failedAsExpected = false
        val roomDb = AppDatabase.buildDatabaseWithName(context, name)
        try {
            roomDb.openHelper.writableDatabase
        } catch (_: Exception) {
            failedAsExpected = true
        } finally {
            if (roomDb.isOpen) roomDb.close()
        }
        assertTrue("Room deve recusar versão sem migration em vez de destruir o save", failedAsExpected)
        assertTrue(context.getDatabasePath(name).exists())

        val verifier = createLegacyDatabase(name, version) { }
        verifier.writableDatabase.query("SELECT `value` FROM `migration_guard`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("PRESERVE_ME", cursor.getString(0))
        }
        verifier.close()
    }

    private fun createLegacyDatabase(
        name: String,
        version: Int,
        onCreate: (SupportSQLiteDatabase) -> Unit
    ): SupportSQLiteOpenHelper {
        context.deleteDatabase(name)
        createdDatabases += name
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private fun createHistoricalV15PlayersTable(db: SupportSQLiteDatabase) {
        val attributeColumns = listOf(
            "reflexos", "pegada", "umContraUm", "saidaDeGol", "lancamento",
            "desarme", "marcacao", "cabeceio", "passeCurto", "cruzamento",
            "drible", "passe", "primeiroToque", "finalizacao", "chuteDeLonge",
            "controleBola", "posicionamento", "concentracao", "sangueFrio",
            "antecipacao", "bravura", "trabalhoEquipe", "decisao", "semBola",
            "visaoJogo", "criatividade", "agressividade", "lideranca",
            "regularidade", "agilidade", "impulsao", "forca", "velocidade",
            "aceleracao", "resistencia"
        ).joinToString(",\n") { "`attr_$it` INTEGER NOT NULL DEFAULT 50" }

        db.execSQL(
            """
            CREATE TABLE `players` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `teamId` INTEGER NOT NULL DEFAULT 0,
                `name` TEXT NOT NULL DEFAULT '',
                `age` INTEGER NOT NULL DEFAULT 18,
                `nationality` TEXT NOT NULL DEFAULT 'Brasil',
                `position` TEXT NOT NULL DEFAULT 'MEI',
                `force` INTEGER NOT NULL DEFAULT 50,
                `energy` INTEGER NOT NULL DEFAULT 100,
                `moral` INTEGER NOT NULL DEFAULT 75,
                `salary` INTEGER NOT NULL DEFAULT 10000,
                `contractDurationWeeks` INTEGER NOT NULL DEFAULT 52,
                `isFromAcademy` INTEGER NOT NULL DEFAULT 0,
                `careerApps` INTEGER NOT NULL DEFAULT 0,
                `careerGoals` INTEGER NOT NULL DEFAULT 0,
                `imageUrl` TEXT,
                `injuryWeeksRemaining` INTEGER NOT NULL DEFAULT 0,
                `suspensionWeeksRemaining` INTEGER NOT NULL DEFAULT 0,
                `yellowCardsAccumulated` INTEGER NOT NULL DEFAULT 0,
                `isStarter` INTEGER NOT NULL DEFAULT 0,
                `isOnLoan` INTEGER NOT NULL DEFAULT 0,
                `loanWeeksRemaining` INTEGER NOT NULL DEFAULT 0,
                `originalTeamId` INTEGER NOT NULL DEFAULT 0,
                `careerAssists` INTEGER NOT NULL DEFAULT 0,
                `careerTackles` INTEGER NOT NULL DEFAULT 0,
                `careerSaves` INTEGER NOT NULL DEFAULT 0,
                `ratingSum` REAL NOT NULL DEFAULT 0.0,
                `ratingCount` INTEGER NOT NULL DEFAULT 0,
                `maxHistoricalForce` INTEGER NOT NULL DEFAULT 0,
                `market_value` INTEGER NOT NULL DEFAULT 0,
                `min_price` INTEGER NOT NULL DEFAULT 0,
                `max_price` INTEGER NOT NULL DEFAULT 0,
                `demand_level` TEXT NOT NULL DEFAULT 'medium',
                `finishing` INTEGER NOT NULL DEFAULT 50,
                `passing` INTEGER NOT NULL DEFAULT 50,
                `pace` INTEGER NOT NULL DEFAULT 50,
                `strength` INTEGER NOT NULL DEFAULT 50,
                `vision` INTEGER NOT NULL DEFAULT 50,
                `defense` INTEGER NOT NULL DEFAULT 50,
                `scoutedLevel` INTEGER NOT NULL DEFAULT 0,
                `atributosJson` TEXT,
                $attributeColumns,
                `potential` INTEGER NOT NULL DEFAULT 80,
                `gols` INTEGER NOT NULL DEFAULT 0,
                `assistencias` INTEGER NOT NULL DEFAULT 0,
                `partidasDisputadas` INTEGER NOT NULL DEFAULT 0,
                `minutosJogados` INTEGER NOT NULL DEFAULT 0,
                `mediaNotas` REAL NOT NULL DEFAULT 0.0,
                `focoTreino` TEXT,
                `condicao` INTEGER NOT NULL DEFAULT 100,
                `evolucaoMensal` REAL NOT NULL DEFAULT 0.0
            )
            """.trimIndent()
        )
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='$table'").use {
            it.moveToFirst()
        }

    private fun tableColumns(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private fun indexNames(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA index_list(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
}
