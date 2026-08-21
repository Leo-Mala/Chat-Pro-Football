package com.example.data

/**
 * Phase 10.4 policy for FC26 snapshot loans.
 *
 * The normalized snapshot exposes the current club (`clubTeamId`/`clubName`) and an optional
 * owner name (`clubLoanedFrom`), but it does not expose a trustworthy loan start/end date.
 * Resolution is therefore deliberately fail-closed: only a unique source-club identity that also
 * resolves to a canonical target Team is materialized. No fuzzy candidate is ever promoted.
 */
enum class Fc26LoanResolutionStatus {
    RESOLVED,
    AMBIGUOUS_OWNER,
    AMBIGUOUS_BORROWER,
    OWNER_NOT_FOUND,
    BORROWER_NOT_FOUND,
    SELF_LOAN,
    DUPLICATE_ACTIVE_LOAN,
    INVALID_REFERENCE,
    UNSUPPORTED_METADATA
}

data class Fc26LoanResolution(
    val sourcePlayerId: Long,
    val playerId: Long,
    val playerName: String,
    val ownerSourceName: String?,
    val borrowerSourceTeamId: Long?,
    val borrowerSourceName: String?,
    val ownerTeamId: Long? = null,
    val borrowerTeamId: Long? = null,
    val status: Fc26LoanResolutionStatus,
    val reason: String
)

data class Fc26LoanAudit(
    val datasetLoanPlayers: Int,
    val resolutions: List<Fc26LoanResolution>
) {
    val resolvedLoans: Int get() = resolutions.count { it.status == Fc26LoanResolutionStatus.RESOLVED }
    val rejectedLoans: Int get() = resolutions.size - resolvedLoans
    val ambiguousLoans: Int get() = resolutions.count {
        it.status == Fc26LoanResolutionStatus.AMBIGUOUS_OWNER ||
            it.status == Fc26LoanResolutionStatus.AMBIGUOUS_BORROWER
    }
    val ownerNotFound: Int get() = resolutions.count { it.status == Fc26LoanResolutionStatus.OWNER_NOT_FOUND }
    val borrowerNotFound: Int get() = resolutions.count { it.status == Fc26LoanResolutionStatus.BORROWER_NOT_FOUND }
    val selfLoansRejected: Int get() = resolutions.count { it.status == Fc26LoanResolutionStatus.SELF_LOAN }
    val duplicateLoans: Int get() = resolutions.count { it.status == Fc26LoanResolutionStatus.DUPLICATE_ACTIVE_LOAN }

    init {
        require(resolutions.size == datasetLoanPlayers) {
            "FC26 loan audit incompleto: resolutions=${resolutions.size} datasetLoanPlayers=$datasetLoanPlayers"
        }
    }
}

object Fc26LoanPolicy {
    const val UNKNOWN_SEASON = 0
    const val UNKNOWN_WEEK = 0
    const val UNKNOWN_DURATION_WEEKS = 0
    const val SOURCE = "FC26"

    /**
     * Negative IDs reserve a deterministic namespace for snapshot loans and cannot collide with
     * Room auto-generated gameplay loan IDs (positive rowids).
     */
    fun deterministicLoanId(playerId: Long): Long {
        require(playerId > 0L) { "playerId de empréstimo FC26 deve ser positivo." }
        return -playerId
    }

    fun isUnknownEndSnapshotLoan(loan: PlayerLoan): Boolean =
        loan.id == deterministicLoanId(loan.playerId) &&
            loan.startSeason == UNKNOWN_SEASON &&
            loan.startWeek == UNKNOWN_WEEK &&
            loan.durationWeeks == UNKNOWN_DURATION_WEEKS &&
            loan.remainingWeeks == UNKNOWN_DURATION_WEEKS

    fun toPlayerLoan(resolution: Fc26LoanResolution): PlayerLoan {
        require(resolution.status == Fc26LoanResolutionStatus.RESOLVED)
        val ownerTeamId = requireNotNull(resolution.ownerTeamId)
        val borrowerTeamId = requireNotNull(resolution.borrowerTeamId)
        require(resolution.playerId > 0L)
        require(ownerTeamId > 0L)
        require(borrowerTeamId > 0L)
        require(ownerTeamId != borrowerTeamId)

        return PlayerLoan(
            id = deterministicLoanId(resolution.playerId),
            playerId = resolution.playerId,
            ownerTeamId = ownerTeamId,
            borrowerTeamId = borrowerTeamId,
            startSeason = UNKNOWN_SEASON,
            startWeek = UNKNOWN_WEEK,
            durationWeeks = UNKNOWN_DURATION_WEEKS,
            remainingWeeks = UNKNOWN_DURATION_WEEKS,
            weeklyFee = 0L,
            buyoutOptionPrice = null,
            status = "ACTIVE"
        )
    }
}

object Fc26LoanResolver {
    // Same small audited spelling bridge used by the conservative club matcher. It is intentionally
    // finite and deterministic; edit-distance/fuzzy matching is forbidden for ownership.
    private val explicitAliases = mapOf(
        "atletico madrid" to "atletico de madrid",
        "paris saint germain" to "paris saint germain",
        "inter" to "internazionale",
        "inter milan" to "internazionale",
        "bayern munich" to "bayern munchen",
        "borussia monchengladbach" to "borussia monchengladbach"
    ).mapKeys { Fc26ClubMatcher.normalize(it.key) }
        .mapValues { Fc26ClubMatcher.normalize(it.value) }

    data class Result(
        val loans: List<PlayerLoan>,
        val audit: Fc26LoanAudit
    ) {
        init {
            require(loans.map { it.playerId }.distinct().size == loans.size) {
                "FC26 materializou dois empréstimos ativos para o mesmo jogador."
            }
            require(loans.map { it.id }.distinct().size == loans.size) {
                "FC26 materializou PlayerLoan.id duplicado."
            }
        }
    }

    fun resolve(
        dataset: Fc26Dataset,
        clubMatches: List<Fc26ClubMatch>
    ): Result {
        val loanPlayers = dataset.players.filter { !it.clubLoanedFrom.isNullOrBlank() }
            .sortedBy { it.stableId }
        require(loanPlayers.size == dataset.manifest.loanedPlayerCount) {
            "FC26 manifest loanedPlayerCount divergente: manifest=${dataset.manifest.loanedPlayerCount} actual=${loanPlayers.size}"
        }

        val matchesBySourceId = clubMatches.associateBy { it.sourceClubTeamId }
        require(matchesBySourceId.size == dataset.sourceClubs.size) {
            "FC26 loan resolver exige exatamente um club match por source club."
        }

        val sourceClubs = dataset.sourceClubs
        val exactOwnerIndex = sourceClubs.groupBy { canonicalAliasName(it.clubName) }
        val coreOwnerIndex = sourceClubs.groupBy { Fc26ClubMatcher.core(canonicalAliasName(it.clubName)) }
        val resolvedPlayerIds = mutableSetOf<Long>()
        val materialized = mutableListOf<PlayerLoan>()
        val resolutions = mutableListOf<Fc26LoanResolution>()

        for (source in loanPlayers) {
            val ownerRaw = source.clubLoanedFrom?.trim()?.takeIf { it.isNotEmpty() }
            val borrowerSourceId = source.clubTeamId
            val borrowerSourceName = source.clubName

            if (ownerRaw == null || borrowerSourceId == null || borrowerSourceName.isNullOrBlank()) {
                resolutions += source.resolution(
                    status = Fc26LoanResolutionStatus.UNSUPPORTED_METADATA,
                    reason = "loan signal lacks owner/current-club metadata required for safe resolution"
                )
                continue
            }
            if (source.stableId <= 0L || borrowerSourceId <= 0L) {
                resolutions += source.resolution(
                    status = Fc26LoanResolutionStatus.INVALID_REFERENCE,
                    reason = "player/source-club reference is not positive"
                )
                continue
            }

            val borrowerMatch = matchesBySourceId[borrowerSourceId]
            if (borrowerMatch == null) {
                resolutions += source.resolution(
                    status = Fc26LoanResolutionStatus.BORROWER_NOT_FOUND,
                    reason = "current source club has no canonical matching result"
                )
                continue
            }
            if (borrowerMatch.status == Fc26ClubMatchStatus.AMBIGUOUS) {
                resolutions += source.resolution(
                    status = Fc26LoanResolutionStatus.AMBIGUOUS_BORROWER,
                    reason = borrowerMatch.reason
                )
                continue
            }
            if (borrowerMatch.status != Fc26ClubMatchStatus.MATCHED || borrowerMatch.targetTeamId == null) {
                resolutions += source.resolution(
                    status = Fc26LoanResolutionStatus.BORROWER_NOT_FOUND,
                    reason = borrowerMatch.reason
                )
                continue
            }

            val ownerCandidates = findOwnerCandidates(ownerRaw, exactOwnerIndex, coreOwnerIndex)
            if (ownerCandidates.isEmpty()) {
                resolutions += source.resolution(
                    borrowerTeamId = borrowerMatch.targetTeamId,
                    status = Fc26LoanResolutionStatus.OWNER_NOT_FOUND,
                    reason = "owner name does not resolve to a unique FC26 source club"
                )
                continue
            }
            if (ownerCandidates.size > 1) {
                resolutions += source.resolution(
                    borrowerTeamId = borrowerMatch.targetTeamId,
                    status = Fc26LoanResolutionStatus.AMBIGUOUS_OWNER,
                    reason = "owner name resolves to multiple FC26 source clubs: ${ownerCandidates.map { it.sourceClubTeamId }.sorted()}"
                )
                continue
            }

            val ownerSource = ownerCandidates.single()
            val ownerMatch = matchesBySourceId[ownerSource.sourceClubTeamId]
            if (ownerMatch == null || ownerMatch.status == Fc26ClubMatchStatus.UNMATCHED || ownerMatch.targetTeamId == null) {
                resolutions += source.resolution(
                    borrowerTeamId = borrowerMatch.targetTeamId,
                    status = Fc26LoanResolutionStatus.OWNER_NOT_FOUND,
                    reason = ownerMatch?.reason ?: "owner source club has no canonical matching result"
                )
                continue
            }
            if (ownerMatch.status == Fc26ClubMatchStatus.AMBIGUOUS) {
                resolutions += source.resolution(
                    borrowerTeamId = borrowerMatch.targetTeamId,
                    status = Fc26LoanResolutionStatus.AMBIGUOUS_OWNER,
                    reason = ownerMatch.reason
                )
                continue
            }

            val ownerTeamId = ownerMatch.targetTeamId
            val borrowerTeamId = borrowerMatch.targetTeamId
            if (ownerTeamId <= 0L || borrowerTeamId <= 0L) {
                resolutions += source.resolution(
                    ownerTeamId = ownerTeamId,
                    borrowerTeamId = borrowerTeamId,
                    status = Fc26LoanResolutionStatus.INVALID_REFERENCE,
                    reason = "resolved owner/borrower Team.id must be positive"
                )
                continue
            }
            if (ownerTeamId == borrowerTeamId) {
                resolutions += source.resolution(
                    ownerTeamId = ownerTeamId,
                    borrowerTeamId = borrowerTeamId,
                    status = Fc26LoanResolutionStatus.SELF_LOAN,
                    reason = "owner and borrower resolve to the same canonical Team.id"
                )
                continue
            }
            if (!resolvedPlayerIds.add(source.stableId)) {
                resolutions += source.resolution(
                    ownerTeamId = ownerTeamId,
                    borrowerTeamId = borrowerTeamId,
                    status = Fc26LoanResolutionStatus.DUPLICATE_ACTIVE_LOAN,
                    reason = "more than one active FC26 loan candidate resolved for the same player"
                )
                continue
            }

            val resolved = source.resolution(
                ownerTeamId = ownerTeamId,
                borrowerTeamId = borrowerTeamId,
                status = Fc26LoanResolutionStatus.RESOLVED,
                reason = "owner and borrower resolved through deterministic FC26 club identities"
            )
            resolutions += resolved
            materialized += Fc26LoanPolicy.toPlayerLoan(resolved)
        }

        return Result(
            loans = materialized.sortedBy { it.playerId },
            audit = Fc26LoanAudit(
                datasetLoanPlayers = dataset.manifest.loanedPlayerCount,
                resolutions = resolutions.sortedBy { it.playerId }
            )
        )
    }

    private fun findOwnerCandidates(
        ownerRaw: String,
        exactIndex: Map<String, List<Fc26SourceClub>>,
        coreIndex: Map<String, List<Fc26SourceClub>>
    ): List<Fc26SourceClub> {
        val exactKey = canonicalAliasName(ownerRaw)
        exactIndex[exactKey]?.let { return it }

        val coreKey = Fc26ClubMatcher.core(exactKey)
        return coreIndex[coreKey].orEmpty()
    }

    private fun canonicalAliasName(value: String): String {
        val normalized = Fc26ClubMatcher.normalize(value)
        return explicitAliases[normalized] ?: normalized
    }

    private fun Fc26NormalizedPlayer.resolution(
        ownerTeamId: Long? = null,
        borrowerTeamId: Long? = null,
        status: Fc26LoanResolutionStatus,
        reason: String
    ) = Fc26LoanResolution(
        sourcePlayerId = sourcePlayerId,
        playerId = stableId,
        playerName = fullName,
        ownerSourceName = clubLoanedFrom,
        borrowerSourceTeamId = clubTeamId,
        borrowerSourceName = clubName,
        ownerTeamId = ownerTeamId,
        borrowerTeamId = borrowerTeamId,
        status = status,
        reason = reason
    )
}
