package com.example.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.migrations.MIGRATION_14_15
import com.example.data.migrations.MIGRATION_16_17
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
class MigrationCompatibilityTest {

    private val context by lazy {
        ApplicationProvider.getApplicationContext<android.content.Context>()
    }
    private val databases = mutableSetOf<String>()

    @After
    fun tearDown() {
        databases.forEach(context::deleteDatabase)
        databases.clear()
    }

    @Test
    fun migration14To15PreservesTransactionAndAddsScoutColumn() {
        val helper = createDatabase("migration-14-15.db", 14) { db ->
            db.execSQL("CREATE TABLE `game_save` (`id` INTEGER PRIMARY KEY NOT NULL, `coachName` TEXT NOT NULL)")
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
        db.execSQL("INSERT INTO `game_save` (`id`,`coachName`) VALUES (1,'Histórico')")
        db.execSQL(
            "INSERT INTO `transaction_records` (`week`,`season`,`type`,`description`,`amount`,`isIncome`) " +
                "VALUES (4,2026,'TICKET','Ingressos',50000,1)"
        )

        MIGRATION_14_15.migrate(db)

        assertTrue("globalScoutRevealWeeksRemaining" in columns(db, "game_save"))
        db.query("SELECT `week`,`season`,`amount`,`isIncome` FROM `transaction_history`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(4, cursor.getInt(0))
            assertEquals(2026, cursor.getInt(1))
            assertEquals(50000L, cursor.getLong(2))
            assertEquals(1, cursor.getInt(3))
        }
        assertFalse(tableExists(db, "transaction_records"))
        db.close()
        helper.close()
    }

    @Test
    fun migration16To17CreatesInstallmentsLoansAndIndexes() {
        val helper = createDatabase("migration-16-17.db", 16) { }
        val db = helper.writableDatabase

        MIGRATION_16_17.migrate(db)

        assertTrue(tableExists(db, "transfer_installments"))
        assertTrue(tableExists(db, "player_loans"))

        val installmentIndexes = indexes(db, "transfer_installments")
        assertTrue("index_transfer_installments_buyerTeamId" in installmentIndexes)
        assertTrue("index_transfer_installments_sellerTeamId" in installmentIndexes)
        assertTrue("index_transfer_installments_status" in installmentIndexes)

        val loanIndexes = indexes(db, "player_loans")
        assertTrue("index_player_loans_playerId" in loanIndexes)
        assertTrue("index_player_loans_ownerTeamId" in loanIndexes)
        assertTrue("index_player_loans_borrowerTeamId" in loanIndexes)
        assertTrue("index_player_loans_status" in loanIndexes)

        db.close()
        helper.close()
    }

    private fun createDatabase(
        name: String,
        version: Int,
        onCreate: (SupportSQLiteDatabase) -> Unit
    ): SupportSQLiteOpenHelper {
        context.deleteDatabase(name)
        databases += name
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='$table'").use {
            it.moveToFirst()
        }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private fun indexes(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA index_list(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
}
