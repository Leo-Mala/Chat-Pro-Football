package com.example.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migração da versão 15 para 16.
 *
 * A V15 histórica armazenava Atributos com @Embedded(prefix = "attr_") e a V16
 * passou a armazená-los em uma única coluna TEXT através de TypeConverter. A
 * implementação antiga não migrava a tabela players, o que podia tornar um save
 * V15 impossível de abrir. Esta migração reconstrói players preservando todos os
 * dados e serializa os campos attr_* no JSON esperado pelo TypeConverter.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrateTransactionHistory(db)
        migrateEmbeddedPlayerAttributes(db)
    }
}

private fun migrateTransactionHistory(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `transaction_history` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `week` INTEGER NOT NULL,
            `season` INTEGER NOT NULL,
            `type` TEXT NOT NULL,
            `description` TEXT NOT NULL,
            `amount` INTEGER NOT NULL,
            `isIncome` INTEGER NOT NULL,
            `timestamp` INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent()
    )

    if (!tableExists(db, "transaction_records")) return

    val legacyColumns = tableColumns(db, "transaction_records")
    val timestampExpression = if ("timestamp" in legacyColumns) {
        "COALESCE(`timestamp`, 0)"
    } else {
        "0"
    }

    db.execSQL(
        """
        INSERT INTO `transaction_history`
            (`week`, `season`, `type`, `description`, `amount`, `isIncome`, `timestamp`)
        SELECT
            COALESCE(`week`, 1),
            COALESCE(`season`, 1),
            COALESCE(`type`, 'GERAL'),
            COALESCE(`description`, 'Registro Histórico'),
            COALESCE(`amount`, 0),
            COALESCE(`isIncome`, 0),
            $timestampExpression
        FROM `transaction_records`
        """.trimIndent()
    )
    db.execSQL("DROP TABLE `transaction_records`")
}

private fun migrateEmbeddedPlayerAttributes(db: SupportSQLiteDatabase) {
    if (!tableExists(db, "players")) return

    val columns = tableColumns(db, "players")
    // Databases already produced by a corrected/intermediate V16 layout need no rebuild.
    if ("atributos" in columns) return
    if ("attr_reflexos" !in columns) {
        throw IllegalStateException(
            "Schema V15 de players não reconhecido: coluna atributos e campos attr_* ausentes."
        )
    }

    val attributeNames = listOf(
        "reflexos", "pegada", "umContraUm", "saidaDeGol", "lancamento",
        "desarme", "marcacao", "cabeceio", "passeCurto", "cruzamento",
        "drible", "passe", "primeiroToque", "finalizacao", "chuteDeLonge",
        "controleBola", "posicionamento", "concentracao", "sangueFrio",
        "antecipacao", "bravura", "trabalhoEquipe", "decisao", "semBola",
        "visaoJogo", "criatividade", "agressividade", "lideranca",
        "regularidade", "agilidade", "impulsao", "forca", "velocidade",
        "aceleracao", "resistencia"
    )

    val atributosJson = buildString {
        append("'{' ")
        attributeNames.forEachIndexed { index, name ->
            if (index == 0) {
                append("|| '\"").append(name).append("\":' ")
            } else {
                append("|| ',\"").append(name).append("\":' ")
            }
            append("|| COALESCE(`attr_").append(name).append("`, 50) ")
        }
        append("|| '}'")
    }

    db.execSQL(
        """
        CREATE TABLE `players_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `teamId` INTEGER NOT NULL,
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
            `originalTeamId` INTEGER NOT NULL,
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
            `evolucaoMensal` REAL NOT NULL
        )
        """.trimIndent()
    )

    val preservedColumns = listOf(
        "id", "teamId", "name", "age", "nationality", "position", "force",
        "energy", "moral", "salary", "contractDurationWeeks", "isFromAcademy",
        "careerApps", "careerGoals", "imageUrl", "injuryWeeksRemaining",
        "suspensionWeeksRemaining", "yellowCardsAccumulated", "isStarter",
        "isOnLoan", "loanWeeksRemaining", "originalTeamId", "careerAssists",
        "careerTackles", "careerSaves", "ratingSum", "ratingCount",
        "maxHistoricalForce", "market_value", "min_price", "max_price",
        "demand_level", "finishing", "passing", "pace", "strength", "vision",
        "defense", "scoutedLevel", "atributosJson"
    )
    val trailingColumns = listOf(
        "potential", "gols", "assistencias", "partidasDisputadas", "minutosJogados",
        "mediaNotas", "focoTreino", "condicao", "evolucaoMensal"
    )

    val insertColumns = (preservedColumns + "atributos" + trailingColumns)
        .joinToString(", ") { "`$it`" }
    val selectColumns = buildList {
        addAll(preservedColumns.map { "`$it`" })
        add(atributosJson)
        addAll(trailingColumns.map { "`$it`" })
    }.joinToString(", ")

    db.execSQL(
        "INSERT INTO `players_new` ($insertColumns) SELECT $selectColumns FROM `players`"
    )
    db.execSQL("DROP TABLE `players`")
    db.execSQL("ALTER TABLE `players_new` RENAME TO `players`")

    // Índices que faziam parte da V16 histórica. A V18 normaliza para os índices atuais.
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_players_teamId` ON `players` (`teamId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_players_position` ON `players` (`position`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_players_isStarter` ON `players` (`isStarter`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_players_originalTeamId` ON `players` (`originalTeamId`)")
}

internal fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
    val cursor = db.query(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
        arrayOf(tableName)
    )
    return cursor.use { it.moveToFirst() }
}

internal fun tableColumns(db: SupportSQLiteDatabase, tableName: String): Set<String> {
    val cursor = db.query("PRAGMA table_info(`$tableName`)")
    return cursor.use {
        val nameIndex = it.getColumnIndex("name")
        buildSet {
            while (it.moveToNext()) {
                add(it.getString(nameIndex))
            }
        }
    }
}
