package com.example.data

/**
 * Identidade do banco-base empacotado para novos slots.
 *
 * O template contém somente o estado imutável inicial (clubes, jogadores FC26 + fallbacks e
 * empréstimos factuais). GameSave e calendário continuam sendo criados para a escolha real do
 * usuário. Assim a otimização elimina dezenas de milhares de INSERTs sem compartilhar estado entre
 * carreiras nem alterar dados esportivos.
 */
data class CareerSeedTemplateMarker(
    val schemaVersion: Int,
    val assetSha256: String,
    val teamCount: Int,
    val playerCount: Int
)

object CareerSeedTemplateContract {
    const val ASSET_PATH = "databases/career_seed_template.db"
    const val TABLE_NAME = "career_seed_template_marker"
    const val EXPECTED_FC26_ASSET_SHA256 = "8355b09029358214ba265834f2f1889a2017693fa5c4f666821d6828ed1119a4"
    const val MINIMUM_TEAM_COUNT = 1_000
    const val MINIMUM_PLAYER_COUNT = 18_405
}

/**
 * Retorna marker somente se o arquivo ainda representar um template virgem e coerente.
 * Qualquer dúvida faz o runtime cair no seed tradicional; nunca reutilizamos uma carreira parcial.
 */
fun GameRepository.pristineCareerSeedTemplateOrNull(): CareerSeedTemplateMarker? {
    val sqlite = db.openHelper.readableDatabase
    val tableExists = sqlite.query(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '${CareerSeedTemplateContract.TABLE_NAME}' LIMIT 1"
    ).use { it.moveToFirst() }
    if (!tableExists) return null

    val marker = sqlite.query(
        "SELECT schemaVersion, assetSha256, teamCount, playerCount " +
            "FROM ${CareerSeedTemplateContract.TABLE_NAME} WHERE id = 1"
    ).use { cursor ->
        if (!cursor.moveToFirst()) return null
        CareerSeedTemplateMarker(
            schemaVersion = cursor.getInt(0),
            assetSha256 = cursor.getString(1),
            teamCount = cursor.getInt(2),
            playerCount = cursor.getInt(3)
        )
    }

    if (marker.schemaVersion != APP_DATABASE_SCHEMA_VERSION) return null
    if (!marker.assetSha256.equals(CareerSeedTemplateContract.EXPECTED_FC26_ASSET_SHA256, ignoreCase = true)) return null
    if (marker.teamCount < CareerSeedTemplateContract.MINIMUM_TEAM_COUNT) return null
    if (marker.playerCount < CareerSeedTemplateContract.MINIMUM_PLAYER_COUNT) return null

    fun count(sql: String): Int = sqlite.query(sql).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else -1
    }

    // O marker só é válido antes da primeira carreira. Uma linha GameSave invalida imediatamente o
    // fast path mesmo que algum caller externo tenha preservado a tabela auxiliar por engano.
    if (count("SELECT COUNT(*) FROM game_save") != 0) return null
    if (count("SELECT COUNT(*) FROM teams") != marker.teamCount) return null
    if (count("SELECT COUNT(*) FROM players") != marker.playerCount) return null

    return marker
}

/** Deve ser chamado dentro da mesma transação que publica o primeiro GameSave. */
fun GameRepository.consumePristineCareerSeedTemplate() {
    db.openHelper.writableDatabase.execSQL(
        "DELETE FROM ${CareerSeedTemplateContract.TABLE_NAME} WHERE id = 1"
    )
}
