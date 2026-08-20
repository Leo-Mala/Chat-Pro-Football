package com.example.data

/** Borrower/current-club resolution for an FC26 loan-marked player. */
internal enum class Fc26LoanBorrowerStatus {
    RESOLVED,
    SOURCE_CLUB_MISSING,
    TARGET_UNMATCHED,
    TARGET_AMBIGUOUS
}

/** Owner-club resolution using exact factual identity only. */
internal enum class Fc26LoanOwnerStatus {
    RESOLVED,
    OWNER_NAME_MISSING,
    SOURCE_NAME_AMBIGUOUS,
    SOURCE_TARGET_UNMATCHED,
    SOURCE_TARGET_AMBIGUOUS,
    STABLE_TARGET_NAME_AMBIGUOUS,
    NOT_FOUND
}

internal enum class Fc26LoanOwnerEvidence {
    EXACT_SOURCE_CLUB_MATCH,
    UNIQUE_STABLE_MATERIALIZED_TARGET_NAME,
    NONE
}

internal enum class Fc26LoanIdentityStatus {
    RESOLVED_IDENTITY_UNDATED,
    BORROWER_UNRESOLVED,
    OWNER_UNRESOLVED,
    BOTH_UNRESOLVED,
    SAME_OWNER_AND_BORROWER
}

internal data class Fc26LoanIdentityResolution(
    val sourcePlayerId: Long,
    val stablePlayerId: Long,
    val playerName: String,
    val borrowerSourceClubTeamId: Long?,
    val borrowerSourceClubName: String?,
    val borrowerStatus: Fc26LoanBorrowerStatus,
    val borrowerTargetTeamId: Long?,
    val borrowerTargetTeamName: String?,
    val ownerSourceName: String?,
    val ownerStatus: Fc26LoanOwnerStatus,
    val ownerEvidence: Fc26LoanOwnerEvidence,
    val ownerSourceClubTeamId: Long?,
    val ownerTargetTeamId: Long?,
    val ownerTargetTeamName: String?,
    val identityStatus: Fc26LoanIdentityStatus
) {
    val borrowerResolved: Boolean
        get() = borrowerStatus == Fc26LoanBorrowerStatus.RESOLVED && borrowerTargetTeamId != null

    val ownerResolved: Boolean
        get() = ownerStatus == Fc26LoanOwnerStatus.RESOLVED && ownerTargetTeamId != null

    val bothSidesResolved: Boolean
        get() = borrowerResolved && ownerResolved

    val validDistinctIdentity: Boolean
        get() = bothSidesResolved && borrowerTargetTeamId != ownerTargetTeamId
}

internal data class Fc26LoanIdentityAudit(
    val datasetLoanPlayers: Int,
    val resolutions: List<Fc26LoanIdentityResolution>
) {
    init {
        require(resolutions.size == datasetLoanPlayers)
        require(resolutions.map { it.sourcePlayerId }.distinct().size == resolutions.size)
        require(resolutions.map { it.stablePlayerId }.distinct().size == resolutions.size)
    }

    val borrowerResolved: Int
        get() = resolutions.count { it.borrowerResolved }

    val ownerResolved: Int
        get() = resolutions.count { it.ownerResolved }

    val bothSidesResolved: Int
        get() = resolutions.count { it.bothSidesResolved }

    val identityResolvedUndated: Int
        get() = resolutions.count { it.identityStatus == Fc26LoanIdentityStatus.RESOLVED_IDENTITY_UNDATED }

    val unresolvedIdentity: Int
        get() = datasetLoanPlayers - identityResolvedUndated

    /** FC26 has no loan-end/duration column, so audit-only identity matches are never ACTIVE loans. */
    val durationResolved: Int
        get() = 0

    val materializableActiveLoans: Int
        get() = 0
}

/**
 * Phase 9.14B deterministic audit for FC26 loan-marked players.
 *
 * The supplied snapshot exposes the current club by `club_team_id` and the contractual owner only
 * as `club_loaned_from` text. It does not expose owner source id or loan end/duration. Consequently
 * this resolver deliberately separates identity resolution from gameplay loan materialization.
 *
 * Accepted owner evidence is restricted to:
 *  1. exact normalized name of exactly one FC26 source club whose target is already MATCHED by the
 *     conservative [Fc26ClubMatcher]; or
 *  2. when no FC26 source club has that exact name, an exact canonical/alias name of exactly one
 *     materialized [StableTeamIdentity].
 *
 * No core-name, edit-distance or fuzzy candidate can resolve an owner here. A source club that is
 * present but unresolved is never bypassed by a target-name lookalike.
 */
internal object Fc26LoanIdentityResolver {
    fun audit(dataset: Fc26Dataset, teams: List<Team>): Fc26LoanIdentityAudit {
        val loanPlayers = dataset.players
            .asSequence()
            .filter { !it.clubLoanedFrom.isNullOrBlank() }
            .sortedBy { it.sourcePlayerId }
            .toList()

        require(loanPlayers.size == dataset.manifest.loanedPlayerCount) {
            "FC26 loan marker count divergiu do manifest: ${loanPlayers.size} != ${dataset.manifest.loanedPlayerCount}"
        }

        val matches = Fc26ClubMatcher.match(dataset, teams)
        val matchBySourceId = matches.associateBy { it.sourceClubTeamId }
        val sourceById = dataset.sourceClubs.associateBy { it.sourceClubTeamId }
        val sourcesByExactName = dataset.sourceClubs.groupBy { Fc26ClubMatcher.normalize(it.clubName) }
        val teamById = teams.associateBy { it.id }

        val stableNamesByMaterializedTeamId = teams.mapNotNull { team ->
            val identity = StableTeamIdentityRegistry.identityForId(team.id) ?: return@mapNotNull null
            val names = (identity.aliases + identity.canonicalName + team.name)
                .mapTo(linkedSetOf()) { Fc26ClubMatcher.normalize(it) }
            team.id to names
        }.toMap()

        val resolutions = loanPlayers.map { player ->
            val borrower = resolveBorrower(player, sourceById, matchBySourceId)
            val owner = resolveOwner(
                ownerName = player.clubLoanedFrom,
                sourcesByExactName = sourcesByExactName,
                matchBySourceId = matchBySourceId,
                teamById = teamById,
                stableNamesByMaterializedTeamId = stableNamesByMaterializedTeamId
            )

            val identityStatus = when {
                borrower.targetTeamId != null && owner.targetTeamId != null &&
                    borrower.targetTeamId == owner.targetTeamId -> Fc26LoanIdentityStatus.SAME_OWNER_AND_BORROWER
                borrower.status == Fc26LoanBorrowerStatus.RESOLVED &&
                    owner.status == Fc26LoanOwnerStatus.RESOLVED -> Fc26LoanIdentityStatus.RESOLVED_IDENTITY_UNDATED
                borrower.status != Fc26LoanBorrowerStatus.RESOLVED &&
                    owner.status != Fc26LoanOwnerStatus.RESOLVED -> Fc26LoanIdentityStatus.BOTH_UNRESOLVED
                borrower.status != Fc26LoanBorrowerStatus.RESOLVED -> Fc26LoanIdentityStatus.BORROWER_UNRESOLVED
                else -> Fc26LoanIdentityStatus.OWNER_UNRESOLVED
            }

            Fc26LoanIdentityResolution(
                sourcePlayerId = player.sourcePlayerId,
                stablePlayerId = player.stableId,
                playerName = player.fullName,
                borrowerSourceClubTeamId = player.clubTeamId,
                borrowerSourceClubName = player.clubName,
                borrowerStatus = borrower.status,
                borrowerTargetTeamId = borrower.targetTeamId,
                borrowerTargetTeamName = borrower.targetTeamName,
                ownerSourceName = player.clubLoanedFrom,
                ownerStatus = owner.status,
                ownerEvidence = owner.evidence,
                ownerSourceClubTeamId = owner.sourceClubTeamId,
                ownerTargetTeamId = owner.targetTeamId,
                ownerTargetTeamName = owner.targetTeamName,
                identityStatus = identityStatus
            )
        }

        return Fc26LoanIdentityAudit(
            datasetLoanPlayers = dataset.manifest.loanedPlayerCount,
            resolutions = resolutions
        )
    }

    private data class BorrowerResolution(
        val status: Fc26LoanBorrowerStatus,
        val targetTeamId: Long? = null,
        val targetTeamName: String? = null
    )

    private fun resolveBorrower(
        player: Fc26NormalizedPlayer,
        sourceById: Map<Long, Fc26SourceClub>,
        matchBySourceId: Map<Long, Fc26ClubMatch>
    ): BorrowerResolution {
        val sourceId = player.clubTeamId
            ?: return BorrowerResolution(Fc26LoanBorrowerStatus.SOURCE_CLUB_MISSING)
        if (sourceById[sourceId] == null) {
            return BorrowerResolution(Fc26LoanBorrowerStatus.SOURCE_CLUB_MISSING)
        }
        val match = matchBySourceId[sourceId]
            ?: return BorrowerResolution(Fc26LoanBorrowerStatus.SOURCE_CLUB_MISSING)
        return when (match.status) {
            Fc26ClubMatchStatus.MATCHED -> BorrowerResolution(
                status = Fc26LoanBorrowerStatus.RESOLVED,
                targetTeamId = requireNotNull(match.targetTeamId),
                targetTeamName = requireNotNull(match.targetTeamName)
            )
            Fc26ClubMatchStatus.UNMATCHED -> BorrowerResolution(Fc26LoanBorrowerStatus.TARGET_UNMATCHED)
            Fc26ClubMatchStatus.AMBIGUOUS -> BorrowerResolution(Fc26LoanBorrowerStatus.TARGET_AMBIGUOUS)
        }
    }

    private data class OwnerResolution(
        val status: Fc26LoanOwnerStatus,
        val evidence: Fc26LoanOwnerEvidence = Fc26LoanOwnerEvidence.NONE,
        val sourceClubTeamId: Long? = null,
        val targetTeamId: Long? = null,
        val targetTeamName: String? = null
    )

    private fun resolveOwner(
        ownerName: String?,
        sourcesByExactName: Map<String, List<Fc26SourceClub>>,
        matchBySourceId: Map<Long, Fc26ClubMatch>,
        teamById: Map<Long, Team>,
        stableNamesByMaterializedTeamId: Map<Long, Set<String>>
    ): OwnerResolution {
        val rawOwner = ownerName?.trim().orEmpty()
        if (rawOwner.isBlank()) return OwnerResolution(Fc26LoanOwnerStatus.OWNER_NAME_MISSING)
        val normalizedOwner = Fc26ClubMatcher.normalize(rawOwner)
        val exactSources = sourcesByExactName[normalizedOwner].orEmpty()

        if (exactSources.size > 1) {
            return OwnerResolution(Fc26LoanOwnerStatus.SOURCE_NAME_AMBIGUOUS)
        }
        if (exactSources.size == 1) {
            val source = exactSources.single()
            val match = requireNotNull(matchBySourceId[source.sourceClubTeamId])
            return when (match.status) {
                Fc26ClubMatchStatus.MATCHED -> OwnerResolution(
                    status = Fc26LoanOwnerStatus.RESOLVED,
                    evidence = Fc26LoanOwnerEvidence.EXACT_SOURCE_CLUB_MATCH,
                    sourceClubTeamId = source.sourceClubTeamId,
                    targetTeamId = requireNotNull(match.targetTeamId),
                    targetTeamName = requireNotNull(match.targetTeamName)
                )
                Fc26ClubMatchStatus.UNMATCHED -> OwnerResolution(
                    status = Fc26LoanOwnerStatus.SOURCE_TARGET_UNMATCHED,
                    sourceClubTeamId = source.sourceClubTeamId
                )
                Fc26ClubMatchStatus.AMBIGUOUS -> OwnerResolution(
                    status = Fc26LoanOwnerStatus.SOURCE_TARGET_AMBIGUOUS,
                    sourceClubTeamId = source.sourceClubTeamId
                )
            }
        }

        // No exact FC26 source club exists. Allow only an exact, unique, factual stable identity that
        // is already materialized in the current Team universe. Never fall through from an unresolved
        // FC26 source club to this path.
        val stableTargets = stableNamesByMaterializedTeamId
            .filterValues { normalizedOwner in it }
            .keys
            .sorted()
        return when (stableTargets.size) {
            0 -> OwnerResolution(Fc26LoanOwnerStatus.NOT_FOUND)
            1 -> {
                val targetId = stableTargets.single()
                val team = requireNotNull(teamById[targetId])
                OwnerResolution(
                    status = Fc26LoanOwnerStatus.RESOLVED,
                    evidence = Fc26LoanOwnerEvidence.UNIQUE_STABLE_MATERIALIZED_TARGET_NAME,
                    targetTeamId = targetId,
                    targetTeamName = team.name
                )
            }
            else -> OwnerResolution(Fc26LoanOwnerStatus.STABLE_TARGET_NAME_AMBIGUOUS)
        }
    }
}
