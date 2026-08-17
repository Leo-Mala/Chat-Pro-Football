package com.example.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * V19 adiciona apenas o snapshot compacto das classificações globais.
 * Nenhuma tabela existente é recriada ou apagada, preservando integralmente saves V18.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `global_league_standings` (
                `season` INTEGER NOT NULL,
                `country` TEXT NOT NULL,
                `division` INTEGER NOT NULL,
                `teamId` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `points` INTEGER NOT NULL,
                `played` INTEGER NOT NULL,
                `wins` INTEGER NOT NULL,
                `draws` INTEGER NOT NULL,
                `losses` INTEGER NOT NULL,
                `goalsFor` INTEGER NOT NULL,
                `goalsAgainst` INTEGER NOT NULL,
                `goalDifference` INTEGER NOT NULL,
                PRIMARY KEY(`season`, `country`, `division`, `teamId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_global_league_standings_season_country_division_position` " +
                "ON `global_league_standings` (`season`, `country`, `division`, `position`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_global_league_standings_teamId` " +
                "ON `global_league_standings` (`teamId`)"
        )
    }
}
