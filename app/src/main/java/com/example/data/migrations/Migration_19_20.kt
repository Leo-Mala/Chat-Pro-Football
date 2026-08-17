package com.example.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * V20 adiciona o slot temporal do fixture sem recriar a tabela nem perder partidas.
 *
 * Saves V19 nasceram em um calendário de uma data lógica por semana, mas já podiam conter
 * liga + copa/continental na mesma semana. Para preservar essa intenção na nova arquitetura:
 * - ligas detalhadas SERIE_*/DIV_* permanecem em WEEKEND;
 * - copas, continentais e torneios mundiais legados são classificados como MIDWEEK.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `fixtures` ADD COLUMN `matchSlot` TEXT NOT NULL DEFAULT 'WEEKEND'"
        )
        db.execSQL(
            """
            UPDATE `fixtures`
            SET `matchSlot` = 'MIDWEEK'
            WHERE `competitionType` NOT LIKE 'SERIE_%'
              AND `competitionType` NOT LIKE 'DIV_%'
              AND `competitionType` != 'STATE'
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_fixtures_season_week_matchSlot` " +
                "ON `fixtures` (`season`, `week`, `matchSlot`)"
        )
    }
}
