package com.example.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * V20 -> V21
 *
 * - normaliza Free Agent: players.teamId 0 -> NULL;
 * - normaliza originalTeamId 0/órfão -> NULL;
 * - protege Player.teamId -> Team.id com ON DELETE SET NULL;
 * - materializa referências legadas de fixtures antes de protegê-las;
 * - protege Fixture.homeTeamId/awayTeamId -> Team.id com NO ACTION;
 * - não remove nenhuma linha válida e não usa destructive fallback.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        requireNoNonPositiveFixtureReferences(db)
        materializeLegacyFixtureTeams(db)

        val oldPlayerCount = db.rowCount("players")
        rebuildPlayers(db)
        check(db.rowCount("players") == oldPlayerCount) {
            "Migration 20->21 alterou a quantidade de jogadores."
        }

        val oldFixtureCount = db.rowCount("fixtures")
        rebuildFixtures(db)
        check(db.rowCount("fixtures") == oldFixtureCount) {
            "Migration 20->21 alterou a quantidade de fixtures."
        }

        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(!cursor.moveToFirst()) {
                "Migration 20->21 terminou com violação de Foreign Key."
            }
        }
        db.query("PRAGMA integrity_check").use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)) {
                "Migration 20->21 terminou com integrity_check inválido."
            }
        }
    }
}

private fun requireNoNonPositiveFixtureReferences(db: SupportSQLiteDatabase) {
    db.query(
        "SELECT COUNT(*) FROM fixtures WHERE homeTeamId <= 0 OR awayTeamId <= 0"
    ).use { cursor ->
        check(cursor.moveToFirst() && cursor.getLong(0) == 0L) {
            "Fixture legado contém teamId <= 0; migration recusada para preservar o save sem inventar referência."
        }
    }
}

/**
 * V20 permitia fixtures apontarem para clubes virtuais que não estavam persistidos em Team. Para
 * preservar integralmente esses fixtures, cada referência faltante recebe um registro Team legado
 * com o MESMO id antes da criação das FKs. País "Mundial" evita contaminar ligas/sedes nacionais.
 */
private fun materializeLegacyFixtureTeams(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        INSERT OR IGNORE INTO teams
            (id, name, city, state, country, division, isPlayerControlled, rating,
             stadiumName, logoUrl, rivalTeamId, colorHex, trainingCenterLevel)
        SELECT refs.teamId,
               'Clube Legado ' || refs.teamId,
               'Global',
               'GL',
               'Mundial',
               1,
               0,
               50,
               'Arena Global',
               NULL,
               0,
               NULL,
               1
        FROM (
            SELECT homeTeamId AS teamId FROM fixtures
            UNION
            SELECT awayTeamId AS teamId FROM fixtures
        ) AS refs
        LEFT JOIN teams ON teams.id = refs.teamId
        WHERE teams.id IS NULL
        """.trimIndent()
    )
}

private fun rebuildPlayers(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE `players_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `teamId` INTEGER,
            `name` TEXT NOT NULL,
            `age` INTEGER NOT NULL,
            `nationality` TEXT NOT NULL,
            `position` TEXT NOT NULL,
            `force` INTEGER NOT NULL,
            `energy` INTEGER NOT NULL,
            `moral` INTEGER NOT NULL,
            `salary` INTEGER NOT NULL,
            `contractDurationWeeks` INTEGER NOT NULL,
            `isFromAcademy` INTEGER NOT NULL,
            `careerApps` INTEGER NOT NULL,
            `careerGoals` INTEGER NOT NULL,
            `imageUrl` TEXT,
            `injuryWeeksRemaining` INTEGER NOT NULL,
            `suspensionWeeksRemaining` INTEGER NOT NULL,
            `yellowCardsAccumulated` INTEGER NOT NULL,
            `isStarter` INTEGER NOT NULL,
            `isOnLoan` INTEGER NOT NULL,
            `loanWeeksRemaining` INTEGER NOT NULL,
            `originalTeamId` INTEGER,
            `careerAssists` INTEGER NOT NULL,
            `careerTackles` INTEGER NOT NULL,
            `careerSaves` INTEGER NOT NULL,
            `ratingSum` REAL NOT NULL,
            `ratingCount` INTEGER NOT NULL,
            `maxHistoricalForce` INTEGER NOT NULL,
            `market_value` INTEGER NOT NULL,
            `min_price` INTEGER NOT NULL,
            `max_price` INTEGER NOT NULL,
            `demand_level` TEXT NOT NULL,
            `finishing` INTEGER NOT NULL,
            `passing` INTEGER NOT NULL,
            `pace` INTEGER NOT NULL,
            `strength` INTEGER NOT NULL,
            `vision` INTEGER NOT NULL,
            `defense` INTEGER NOT NULL,
            `scoutedLevel` INTEGER NOT NULL,
            `atributosJson` TEXT,
            `atributos` TEXT NOT NULL,
            `potential` INTEGER NOT NULL,
            `gols` INTEGER NOT NULL,
            `assistencias` INTEGER NOT NULL,
            `partidasDisputadas` INTEGER NOT NULL,
            `minutosJogados` INTEGER NOT NULL,
            `mediaNotas` REAL NOT NULL,
            `focoTreino` TEXT,
            `condicao` INTEGER NOT NULL,
            `evolucaoMensal` REAL NOT NULL,
            FOREIGN KEY(`teamId`) REFERENCES `teams`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
        )
        """.trimIndent()
    )

    db.execSQL(
        """
        INSERT INTO `players_new` (
            `id`,`teamId`,`name`,`age`,`nationality`,`position`,`force`,`energy`,`moral`,`salary`,
            `contractDurationWeeks`,`isFromAcademy`,`careerApps`,`careerGoals`,`imageUrl`,
            `injuryWeeksRemaining`,`suspensionWeeksRemaining`,`yellowCardsAccumulated`,`isStarter`,
            `isOnLoan`,`loanWeeksRemaining`,`originalTeamId`,`careerAssists`,`careerTackles`,
            `careerSaves`,`ratingSum`,`ratingCount`,`maxHistoricalForce`,`market_value`,`min_price`,
            `max_price`,`demand_level`,`finishing`,`passing`,`pace`,`strength`,`vision`,`defense`,
            `scoutedLevel`,`atributosJson`,`atributos`,`potential`,`gols`,`assistencias`,
            `partidasDisputadas`,`minutosJogados`,`mediaNotas`,`focoTreino`,`condicao`,`evolucaoMensal`
        )
        SELECT
            p.`id`,
            CASE
                WHEN p.`teamId` = 0 THEN NULL
                WHEN EXISTS (SELECT 1 FROM teams t WHERE t.id = p.`teamId`) THEN p.`teamId`
                ELSE NULL
            END,
            p.`name`,p.`age`,p.`nationality`,p.`position`,p.`force`,p.`energy`,p.`moral`,p.`salary`,
            p.`contractDurationWeeks`,p.`isFromAcademy`,p.`careerApps`,p.`careerGoals`,p.`imageUrl`,
            p.`injuryWeeksRemaining`,p.`suspensionWeeksRemaining`,p.`yellowCardsAccumulated`,p.`isStarter`,
            p.`isOnLoan`,p.`loanWeeksRemaining`,
            CASE
                WHEN p.`originalTeamId` = 0 THEN NULL
                WHEN EXISTS (SELECT 1 FROM teams t WHERE t.id = p.`originalTeamId`) THEN p.`originalTeamId`
                ELSE NULL
            END,
            p.`careerAssists`,p.`careerTackles`,p.`careerSaves`,p.`ratingSum`,p.`ratingCount`,
            p.`maxHistoricalForce`,p.`market_value`,p.`min_price`,p.`max_price`,p.`demand_level`,
            p.`finishing`,p.`passing`,p.`pace`,p.`strength`,p.`vision`,p.`defense`,p.`scoutedLevel`,
            p.`atributosJson`,p.`atributos`,p.`potential`,p.`gols`,p.`assistencias`,p.`partidasDisputadas`,
            p.`minutosJogados`,p.`mediaNotas`,p.`focoTreino`,p.`condicao`,p.`evolucaoMensal`
        FROM `players` p
        """.trimIndent()
    )

    db.execSQL("DROP TABLE `players`")
    db.execSQL("ALTER TABLE `players_new` RENAME TO `players`")
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

private fun rebuildFixtures(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE `fixtures_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `season` INTEGER NOT NULL,
            `week` INTEGER NOT NULL,
            `homeTeamId` INTEGER NOT NULL,
            `awayTeamId` INTEGER NOT NULL,
            `homeScore` INTEGER,
            `awayScore` INTEGER,
            `homePenalties` INTEGER,
            `awayPenalties` INTEGER,
            `competitionType` TEXT NOT NULL,
            `isPlayed` INTEGER NOT NULL,
            `matchEventsJson` TEXT,
            `matchSlot` TEXT NOT NULL DEFAULT 'WEEKEND',
            FOREIGN KEY(`homeTeamId`) REFERENCES `teams`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
            FOREIGN KEY(`awayTeamId`) REFERENCES `teams`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
        )
        """.trimIndent()
    )

    db.execSQL(
        """
        INSERT INTO `fixtures_new`
            (`id`,`season`,`week`,`homeTeamId`,`awayTeamId`,`homeScore`,`awayScore`,
             `homePenalties`,`awayPenalties`,`competitionType`,`isPlayed`,`matchEventsJson`,`matchSlot`)
        SELECT `id`,`season`,`week`,`homeTeamId`,`awayTeamId`,`homeScore`,`awayScore`,
               `homePenalties`,`awayPenalties`,`competitionType`,`isPlayed`,`matchEventsJson`,`matchSlot`
        FROM `fixtures`
        """.trimIndent()
    )

    db.execSQL("DROP TABLE `fixtures`")
    db.execSQL("ALTER TABLE `fixtures_new` RENAME TO `fixtures`")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_fixtures_season` ON `fixtures` (`season`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_fixtures_week` ON `fixtures` (`week`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_fixtures_homeTeamId` ON `fixtures` (`homeTeamId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_fixtures_awayTeamId` ON `fixtures` (`awayTeamId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_fixtures_competitionType` ON `fixtures` (`competitionType`)")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_fixtures_season_week` " +
            "ON `fixtures` (`season`, `week`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_fixtures_season_week_matchSlot` " +
            "ON `fixtures` (`season`, `week`, `matchSlot`)"
    )
}

private fun SupportSQLiteDatabase.rowCount(table: String): Long =
    query("SELECT COUNT(*) FROM `$table`").use { cursor ->
        check(cursor.moveToFirst()) { "Não foi possível contar linhas de $table." }
        cursor.getLong(0)
    }
