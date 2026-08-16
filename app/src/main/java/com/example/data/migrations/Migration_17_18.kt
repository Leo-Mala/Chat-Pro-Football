package com.example.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Normaliza bancos V17 produzidos ao longo da evolução do projeto.
 *
 * A versão 17 permaneceu ativa enquanto alguns índices mudaram. Ao avançar para 18
 * damos ao Room uma transição explícita, idempotente e sem perda de dados.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        normalizePlayerIndexes(db)
        ensureCurrentIndexes(db)
    }
}

private fun normalizePlayerIndexes(db: SupportSQLiteDatabase) {
    // Índices antigos da V15/V16 e variantes anteriores da V17.
    db.execSQL("DROP INDEX IF EXISTS `index_players_teamId`")
    db.execSQL("DROP INDEX IF EXISTS `index_players_position`")
    db.execSQL("DROP INDEX IF EXISTS `index_players_isStarter`")

    // Recria também os índices atuais para garantir exatamente uma definição conhecida.
    db.execSQL("DROP INDEX IF EXISTS `index_players_teamId_position_force`")
    db.execSQL("DROP INDEX IF EXISTS `index_players_teamId_isStarter`")
    db.execSQL("DROP INDEX IF EXISTS `index_players_originalTeamId`")

    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_players_teamId_position_force` " +
            "ON `players` (`teamId`, `position`, `force`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_players_teamId_isStarter` " +
            "ON `players` (`teamId`, `isStarter`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_players_originalTeamId` " +
            "ON `players` (`originalTeamId`)"
    )
}

private fun ensureCurrentIndexes(db: SupportSQLiteDatabase) {
    // Teams
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_teams_division` ON `teams` (`division`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_teams_country` ON `teams` (`country`)")

    // Fixtures
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_fixtures_season` ON `fixtures` (`season`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_fixtures_week` ON `fixtures` (`week`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_fixtures_homeTeamId` ON `fixtures` (`homeTeamId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_fixtures_awayTeamId` ON `fixtures` (`awayTeamId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_fixtures_competitionType` ON `fixtures` (`competitionType`)")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_fixtures_season_week` " +
            "ON `fixtures` (`season`, `week`)"
    )

    // Transfer installments
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_transfer_installments_buyerTeamId` " +
            "ON `transfer_installments` (`buyerTeamId`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_transfer_installments_sellerTeamId` " +
            "ON `transfer_installments` (`sellerTeamId`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_transfer_installments_status` " +
            "ON `transfer_installments` (`status`)"
    )

    // Player loans
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_player_loans_playerId` " +
            "ON `player_loans` (`playerId`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_player_loans_ownerTeamId` " +
            "ON `player_loans` (`ownerTeamId`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_player_loans_borrowerTeamId` " +
            "ON `player_loans` (`borrowerTeamId`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_player_loans_status` " +
            "ON `player_loans` (`status`)"
    )
}
