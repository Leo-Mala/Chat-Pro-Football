package com.example.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migração da versão 16 para 17 da base de dados Room.
 * Adiciona tabelas transfer_installments e player_loans.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `transfer_installments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `transferId` INTEGER NOT NULL,
                `playerId` INTEGER NOT NULL,
                `buyerTeamId` INTEGER NOT NULL,
                `sellerTeamId` INTEGER NOT NULL,
                `totalAmount` INTEGER NOT NULL,
                `downPayment` INTEGER NOT NULL,
                `installmentAmount` INTEGER NOT NULL,
                `totalInstallments` INTEGER NOT NULL,
                `remainingInstallments` INTEGER NOT NULL,
                `nextDueWeek` INTEGER NOT NULL,
                `season` INTEGER NOT NULL,
                `status` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfer_installments_buyerTeamId` ON `transfer_installments` (`buyerTeamId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfer_installments_sellerTeamId` ON `transfer_installments` (`sellerTeamId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfer_installments_status` ON `transfer_installments` (`status`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `player_loans` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `playerId` INTEGER NOT NULL,
                `ownerTeamId` INTEGER NOT NULL,
                `borrowerTeamId` INTEGER NOT NULL,
                `startSeason` INTEGER NOT NULL,
                `startWeek` INTEGER NOT NULL,
                `durationWeeks` INTEGER NOT NULL,
                `remainingWeeks` INTEGER NOT NULL,
                `weeklyFee` INTEGER NOT NULL,
                `buyoutOptionPrice` INTEGER,
                `status` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_player_loans_playerId` ON `player_loans` (`playerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_player_loans_ownerTeamId` ON `player_loans` (`ownerTeamId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_player_loans_borrowerTeamId` ON `player_loans` (`borrowerTeamId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_player_loans_status` ON `player_loans` (`status`)")
    }
}
