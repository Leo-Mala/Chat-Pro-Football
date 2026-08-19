package com.example.data

import com.google.gson.JsonParser

data class Fc26PersistedImportMetadata(
    val source: String,
    val sourcePlayerId: Long,
    val datasetVersion: String,
    val birthDateIso: String,
    val primaryPosition: String,
    val alternativePositions: List<String>,
    val sourceClubTeamId: Long?,
    val sourceClubName: String?,
    val leagueId: Long?,
    val leagueName: String?
)

/**
 * Lê somente o envelope de identidade externa persistido em `atributosJson`.
 * Falhas de parsing retornam null para preservar compatibilidade com jogadores legados/procedurais.
 */
internal fun Player.sourceMetadataOrNull(): Fc26PersistedImportMetadata? {
    val json = atributosJson?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val import = JsonParser.parseString(json).asJsonObject.getAsJsonObject("import") ?: return null
        val source = import.get("source")?.asString ?: return null
        if (source != "FC26") return null
        Fc26PersistedImportMetadata(
            source = source,
            sourcePlayerId = import.get("sourcePlayerId")?.asLong ?: return null,
            datasetVersion = import.get("datasetVersion")?.asString ?: return null,
            birthDateIso = import.get("birthDateIso")?.asString ?: return null,
            primaryPosition = import.get("primaryPosition")?.asString ?: return null,
            alternativePositions = import.getAsJsonArray("alternativePositions")
                ?.map { it.asString }
                .orEmpty(),
            sourceClubTeamId = import.get("sourceClubTeamId")?.takeUnless { it.isJsonNull }?.asLong,
            sourceClubName = import.get("sourceClubName")?.takeUnless { it.isJsonNull }?.asString,
            leagueId = import.get("leagueId")?.takeUnless { it.isJsonNull }?.asLong,
            leagueName = import.get("leagueName")?.takeUnless { it.isJsonNull }?.asString
        )
    }.getOrNull()
}
