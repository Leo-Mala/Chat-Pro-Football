package com.example.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser

private const val FC26_UNASSIGNED_SOURCE_CLUB = "UNASSIGNED_SOURCE_CLUB"
private const val FC26_UNASSIGNED_SOURCE_CLUB_JSON_MARKER =
    "\"assignmentStatus\":\"UNASSIGNED_SOURCE_CLUB\""
private const val FC26_LOAN_DURATION_UNKNOWN = "UNKNOWN_FROM_SOURCE_SNAPSHOT"

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

data class Fc26PersistedLoanIdentityMetadata(
    val sourceOwnerClubName: String?,
    val identityStatus: String,
    val borrowerStatus: String,
    val ownerStatus: String,
    val ownerEvidence: String,
    val borrowerTargetTeamId: Long?,
    val borrowerTargetTeamName: String?,
    val ownerSourceClubTeamId: Long?,
    val ownerTargetTeamId: Long?,
    val ownerTargetTeamName: String?,
    val durationStatus: String,
    val gameplayLoanMaterialized: Boolean
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

/** Reads the Phase 9.14B identity-only loan envelope without implying an ACTIVE gameplay loan. */
internal fun Player.fc26LoanIdentityMetadataOrNull(): Fc26PersistedLoanIdentityMetadata? {
    val json = atributosJson?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val import = JsonParser.parseString(json).asJsonObject.getAsJsonObject("import") ?: return null
        if (import.get("source")?.asString != "FC26") return null
        val loan = import.getAsJsonObject("loanIdentity") ?: return null
        Fc26PersistedLoanIdentityMetadata(
            sourceOwnerClubName = loan.get("sourceOwnerClubName")?.takeUnless { it.isJsonNull }?.asString,
            identityStatus = loan.get("identityStatus")?.asString ?: return null,
            borrowerStatus = loan.get("borrowerStatus")?.asString ?: return null,
            ownerStatus = loan.get("ownerStatus")?.asString ?: return null,
            ownerEvidence = loan.get("ownerEvidence")?.asString ?: return null,
            borrowerTargetTeamId = loan.get("borrowerTargetTeamId")?.takeUnless { it.isJsonNull }?.asLong,
            borrowerTargetTeamName = loan.get("borrowerTargetTeamName")?.takeUnless { it.isJsonNull }?.asString,
            ownerSourceClubTeamId = loan.get("ownerSourceClubTeamId")?.takeUnless { it.isJsonNull }?.asLong,
            ownerTargetTeamId = loan.get("ownerTargetTeamId")?.takeUnless { it.isJsonNull }?.asLong,
            ownerTargetTeamName = loan.get("ownerTargetTeamName")?.takeUnless { it.isJsonNull }?.asString,
            durationStatus = loan.get("durationStatus")?.asString ?: return null,
            gameplayLoanMaterialized = loan.get("gameplayLoanMaterialized")?.asBoolean ?: return null
        )
    }.getOrNull()
}

/**
 * Persists only factual identity evidence for a source-marked FC26 loan.
 *
 * The FC26 snapshot has no loan-end/duration field. Therefore this helper MUST NOT set `isOnLoan`,
 * `originalTeamId`, `loanWeeksRemaining`, or create a `PlayerLoan`. The current gameplay club remains
 * whatever the conservative club seed already established. Owner identity and unresolved reasons
 * live only in the existing JSON metadata envelope until a duration can be verified separately.
 */
internal fun Player.markFc26LoanIdentity(resolution: Fc26LoanIdentityResolution): Player {
    val json = atributosJson?.takeIf { it.isNotBlank() } ?: return this
    val updatedJson = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        val import = root.getAsJsonObject("import") ?: return this
        if (import.get("source")?.asString != "FC26") return this
        val loan = JsonObject().apply {
            resolution.ownerSourceName?.let { addProperty("sourceOwnerClubName", it) }
            addProperty("identityStatus", resolution.identityStatus.name)
            addProperty("borrowerStatus", resolution.borrowerStatus.name)
            addProperty("ownerStatus", resolution.ownerStatus.name)
            addProperty("ownerEvidence", resolution.ownerEvidence.name)
            resolution.borrowerTargetTeamId?.let { addProperty("borrowerTargetTeamId", it) }
            resolution.borrowerTargetTeamName?.let { addProperty("borrowerTargetTeamName", it) }
            resolution.ownerSourceClubTeamId?.let { addProperty("ownerSourceClubTeamId", it) }
            resolution.ownerTargetTeamId?.let { addProperty("ownerTargetTeamId", it) }
            resolution.ownerTargetTeamName?.let { addProperty("ownerTargetTeamName", it) }
            addProperty("durationStatus", FC26_LOAN_DURATION_UNKNOWN)
            addProperty("gameplayLoanMaterialized", false)
        }
        import.add("loanIdentity", loan)
        root.toString()
    }.getOrNull() ?: return this
    return copy(atributosJson = updatedJson)
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

/**
 * True apenas para o snapshot FC26 ainda sem associação de clube; não inclui free agents reais.
 *
 * Este predicado fica em hot paths semanais. O marcador é escrito por [markFc26UnassignedSourceClub]
 * usando JsonObject.toString(), então uma busca textual exata evita milhares de parses Gson sem mudar
 * o envelope persistido nem a semântica de [sourceMetadataOrNull].
 */
internal fun Player.isFc26UnassignedSourceClub(): Boolean =
    teamId == null && atributosJson?.contains(FC26_UNASSIGNED_SOURCE_CLUB_JSON_MARKER) == true
