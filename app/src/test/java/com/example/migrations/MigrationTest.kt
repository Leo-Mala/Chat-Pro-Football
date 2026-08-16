package com.example.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.example.data.migrations.MIGRATION_14_15
import com.example.data.migrations.MIGRATION_15_16
import com.example.data.migrations.MIGRATION_16_17
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @Test
    fun testMigrationChainFrom14To17() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test-migration.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `game_save` (`id` INTEGER PRIMARY KEY NOT NULL, `coachName` TEXT NOT NULL, `coachReputation` INTEGER NOT NULL, `currentWeek` INTEGER NOT NULL, `currentSeason` INTEGER NOT NULL, `playerTeamId` INTEGER NOT NULL, `bankBalance` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `teams` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `city` TEXT NOT NULL, `state` TEXT NOT NULL, `country` TEXT NOT NULL, `division` INTEGER NOT NULL, `rating` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `players` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `teamId` INTEGER NOT NULL, `name` TEXT NOT NULL, `age` INTEGER NOT NULL, `position` TEXT NOT NULL, `force` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `week` INTEGER NOT NULL, `season` INTEGER NOT NULL, `type` TEXT NOT NULL, `description` TEXT NOT NULL, `amount` INTEGER NOT NULL, `isIncome` INTEGER NOT NULL)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val openHelper = factory.create(config)
        val db = openHelper.writableDatabase

        // 1. Inserir dados pré-migração na V14
        db.execSQL("INSERT INTO game_save (id, coachName, coachReputation, currentWeek, currentSeason, playerTeamId, bankBalance) VALUES (1, 'Técnico Migração', 50, 1, 2026, 100, 5000000)")
        db.execSQL("INSERT INTO teams (id, name, city, state, country, division, rating) VALUES (100, 'Time Migração', 'Cidade', 'MG', 'Brasil', 1, 75)")
        db.execSQL("INSERT INTO players (id, teamId, name, age, position, force) VALUES (1001, 100, 'Jogador Migração', 25, 'MEI', 80)")
        db.execSQL("INSERT INTO transaction_records (week, season, type, description, amount, isIncome) VALUES (1, 2026, 'TICKET', 'Venda de Ingressos', 50000, 1)")

        // 2. Executar Migração 14 -> 15
        MIGRATION_14_15.migrate(db)

        val cursorHistory = db.query("SELECT COUNT(*) FROM transaction_history")
        assertTrue(cursorHistory.moveToFirst())
        assertEquals("Deve migrar 1 registro para transaction_history", 1, cursorHistory.getInt(0))
        cursorHistory.close()

        // 3. Executar Migração 15 -> 16
        MIGRATION_15_16.migrate(db)

        // 4. Executar Migração 16 -> 17
        MIGRATION_16_17.migrate(db)

        val cursorInstallments = db.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='transfer_installments'")
        assertTrue(cursorInstallments.moveToFirst())
        assertEquals(1, cursorInstallments.getInt(0))
        cursorInstallments.close()

        val cursorLoans = db.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='player_loans'")
        assertTrue(cursorLoans.moveToFirst())
        assertEquals(1, cursorLoans.getInt(0))
        cursorLoans.close()

        db.close()
    }
}
