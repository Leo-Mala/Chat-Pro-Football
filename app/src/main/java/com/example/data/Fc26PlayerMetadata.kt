package com.example.data

import com.google.gson.JsonParser

private const val FC26_UNASSIGNED_SOURCE_CLUB = "UNASSIGNED_SOURCE_CLUB"

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
    val leagueName: String?,
    val assignmentStatus: String?,
    val sourceContractDurationWeeks: Int?,
    val sourceSalary: Long?
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
            leagueName = import.get("leagueName")?.takeUnless { it.isJsonNull }?.asString,
            assignmentStatus = import.get("assignmentStatus")?.takeUnless { it.isJsonNull }?.asString,
            sourceContractDurationWeeks = import.get("sourceContractDurationWeeks")?.takeUnless { it.isJsonNull }?.asInt,
            sourceSalary = import.get("sourceSalary")?.takeUnless { it.isJsonNull }?.asLong
        )
    }.getOrNull()
}

/**
 * Marca somente jogadores FC26 cujo clube de origem ainda não possui target seguro no universo do
 * jogo. O marcador vive no envelope de metadados já persistido, portanto não exige mudança Room e
 * não toca em overall, potential nem nos atributos de gameplay.
 *
 * Enquanto não houver target, salário/contrato de clube não são runtime-applicáveis. Os valores já
 * derivados da fonte são preservados no envelope para futura associação e zerados apenas nos campos
 * operacionais, evitando que rotinas semanais tratem o snapshot como vínculo com um clube inexistente.
 */
internal fun Player.markFc26UnassignedSourceClub(): Player {
    val json = atributosJson?.takeIf { it.isNotBlank() } ?: return this
    val updatedJson = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        val import = root.getAsJsonObject("import") ?: return this
        if (import.get("source")?.asString != "FC26") return this
        import.addProperty("assignmentStatus", FC26_UNASSIGNED_SOURCE_CLUB)
        import.addProperty("sourceContractDurationWeeks", contractDurationWeeks)
        import.addProperty("sourceSalary", salary)
        root.toString()
    }.getOrNull() ?: return this
    return copy(
        atributosJson = updatedJson,
        contractDurationWeeks = 0,
        salary = 0L,
        isStarter = false,
        isOnLoan = false,
        loanWeeksRemaining = 0,
        originalTeamId = null
    )
}

/** True apenas para o snapshot FC26 ainda sem associação de clube; não inclui free agents reais. */
internal fun Player.isFc26UnassignedSourceClub(): Boolean =
    teamId == null && sourceMetadataOrNull()?.assignmentStatus == FC26_UNASSIGNED_SOURCE_CLUB
