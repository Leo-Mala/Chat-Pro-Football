package com.example.data

import com.google.gson.JsonParser

private const val FC26_UNASSIGNED_SOURCE_CLUB = "UNASSIGNED_SOURCE_CLUB"
private const val FC26_UNASSIGNED_SOURCE_CLUB_JSON_MARKER =
    "\"assignmentStatus\":\"UNASSIGNED_SOURCE_CLUB\""
private const val FC26_LOAN_OWNERSHIP_UNRESOLVED = "LOAN_OWNERSHIP_UNRESOLVED"
private const val FC26_LOAN_OWNERSHIP_UNRESOLVED_JSON_MARKER =
    "\"assignmentStatus\":\"LOAN_OWNERSHIP_UNRESOLVED\""
private const val FC26_LOAN_TEMPORAL_NOT_AVAILABLE = "NOT_AVAILABLE"

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
    val sourceSalary: Long?,
    val clubLoanedFrom: String? = null,
    val loanResolutionStatus: String? = null,
    val loanOwnerTeamId: Long? = null,
    val loanBorrowerTeamId: Long? = null,
    val loanTemporalCoverage: String? = null
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
            sourceSalary = import.get("sourceSalary")?.takeUnless { it.isJsonNull }?.asLong,
            clubLoanedFrom = import.get("clubLoanedFrom")?.takeUnless { it.isJsonNull }?.asString,
            loanResolutionStatus = import.get("loanResolutionStatus")?.takeUnless { it.isJsonNull }?.asString,
            loanOwnerTeamId = import.get("loanOwnerTeamId")?.takeUnless { it.isJsonNull }?.asLong,
            loanBorrowerTeamId = import.get("loanBorrowerTeamId")?.takeUnless { it.isJsonNull }?.asLong,
            loanTemporalCoverage = import.get("loanTemporalCoverage")?.takeUnless { it.isJsonNull }?.asString
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

/**
 * Persiste a decisão de resolução do sinal de empréstimo FC26 no envelope factual existente.
 * RESOLVED materializa owner/borrower normalmente. Qualquer outro status representa um sinal factual
 * de empréstimo que não pôde ser materializado com segurança; o jogador entra em quarentena
 * operacional e deixa de ser atribuído a qualquer clube runtime (`teamId=null`, `isOnLoan=true`, sem
 * owner operacional e sem PlayerLoan). Isso inclui ausência/ambiguidade de owner/borrower, metadata
 * incompleto e referências inconsistentes. Identidades factuais resolvidas parcialmente permanecem
 * apenas no metadata; nenhuma delas é promovida a ownership de gameplay. Salário/contrato runtime são
 * zerados, preservando os valores de origem já registrados no envelope. O snapshot não contém datas
 * de empréstimo, portanto a cobertura temporal é NOT_AVAILABLE.
 */
internal fun Player.markFc26LoanResolution(resolution: Fc26LoanResolution): Player {
    require(id == resolution.playerId) { "Resolução FC26 aplicada ao jogador errado." }
    val quarantineOwnership = resolution.status != Fc26LoanResolutionStatus.RESOLVED
    val json = atributosJson?.takeIf { it.isNotBlank() } ?: return this
    val updatedJson = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        val import = root.getAsJsonObject("import") ?: return this
        if (import.get("source")?.asString != "FC26") return this
        import.addProperty("loanResolutionStatus", resolution.status.name)
        import.addProperty("loanProvenance", Fc26LoanPolicy.SOURCE)
        import.addProperty("loanTemporalCoverage", FC26_LOAN_TEMPORAL_NOT_AVAILABLE)
        if (resolution.status == Fc26LoanResolutionStatus.RESOLVED) {
            import.addProperty("loanOwnerTeamId", requireNotNull(resolution.ownerTeamId))
            import.addProperty("loanBorrowerTeamId", requireNotNull(resolution.borrowerTeamId))
        } else if (quarantineOwnership) {
            import.addProperty("assignmentStatus", FC26_LOAN_OWNERSHIP_UNRESOLVED)
            if (!import.has("sourceContractDurationWeeks") || import.get("sourceContractDurationWeeks").isJsonNull) {
                import.addProperty("sourceContractDurationWeeks", contractDurationWeeks)
            }
            if (!import.has("sourceSalary") || import.get("sourceSalary").isJsonNull) {
                import.addProperty("sourceSalary", salary)
            }
            resolution.ownerTeamId?.takeIf { it > 0L }?.let {
                import.addProperty("loanOwnerTeamId", it)
            }
            resolution.borrowerTeamId?.takeIf { it > 0L }?.let {
                import.addProperty("loanBorrowerTeamId", it)
            }
        }
        root.toString()
    }.getOrNull() ?: return this

    return when {
        resolution.status == Fc26LoanResolutionStatus.RESOLVED -> {
            val ownerTeamId = requireNotNull(resolution.ownerTeamId)
            val borrowerTeamId = requireNotNull(resolution.borrowerTeamId)
            require(ownerTeamId != borrowerTeamId)
            require(teamId == borrowerTeamId) {
                "FC26 roster/borrower divergence for player=$id: roster=$teamId borrower=$borrowerTeamId"
            }
            copy(
                atributosJson = updatedJson,
                originalTeamId = ownerTeamId,
                isOnLoan = true,
                loanWeeksRemaining = Fc26LoanPolicy.UNKNOWN_DURATION_WEEKS,
                isStarter = false
            )
        }
        quarantineOwnership -> copy(
            atributosJson = updatedJson,
            teamId = null,
            contractDurationWeeks = 0,
            salary = 0L,
            originalTeamId = null,
            isOnLoan = true,
            loanWeeksRemaining = 0,
            isStarter = false
        )
        else -> copy(atributosJson = updatedJson)
    }
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

/** Estado fail-closed de um sinal factual de empréstimo cujo ownership não pôde ser determinado. */
internal fun Player.isFc26LoanOwnershipQuarantined(): Boolean =
    teamId == null && isOnLoan && originalTeamId == null &&
        atributosJson?.contains(FC26_LOAN_OWNERSHIP_UNRESOLVED_JSON_MARKER) == true
