package com.example.data

/**
 * Snapshot only of pre-career state that differs from the deterministic editor bootstrap.
 *
 * The factual FC26 dataset remains immutable. User edits are applied as a runtime/save overlay when
 * a new career is materialized, so opening the editor and doing nothing never rewrites factual data.
 */
internal data class PreCareerEditorOverrides(
    val teamsById: Map<Long, Team>,
    val rostersByTeamId: Map<Long, List<Player>>
) {
    val overriddenTeamIds: Set<Long> = rostersByTeamId.keys + teamsById.keys
    val isEmpty: Boolean get() = overriddenTeamIds.isEmpty()
}

private fun Team.sameEditableStateAs(other: Team): Boolean =
    copy(isPlayerControlled = false) == other.copy(isPlayerControlled = false)

internal fun detectPreCareerEditorOverrides(
    requestedTeams: List<Team>,
    persistedTeams: List<Team>,
    persistedPlayers: List<Player>
): PreCareerEditorOverrides? {
    if (persistedTeams.isEmpty()) return null

    val requestedById = requestedTeams.associateBy { it.id }
    val persistedById = persistedTeams.associateBy { it.id }
    val persistedRosterByTeam = persistedPlayers
        .filter { it.teamId != null }
        .groupBy { requireNotNull(it.teamId) }

    val editedTeams = mutableMapOf<Long, Team>()
    val editedRosters = mutableMapOf<Long, List<Player>>()

    requestedTeams.forEach { requested ->
        val persisted = persistedById[requested.id] ?: return@forEach
        if (!persisted.sameEditableStateAs(requested)) {
            editedTeams[requested.id] = persisted
        }

        val persistedRoster = persistedRosterByTeam[requested.id].orEmpty().sortedBy { it.id }
        if (persistedRoster.isEmpty()) return@forEach

        val bootstrapRoster = DefaultData.generateRosterForTeam(
            teamId = requested.id,
            teamRating = requested.rating,
            teamName = requested.name,
            country = requested.country
        ).sortedBy { it.id }

        if (persistedRoster != bootstrapRoster) {
            editedRosters[requested.id] = persistedRoster
        }
    }

    // A custom/renamed team still has the same stable id, so its roster is preserved if the team
    // entity itself was edited even when every generated Player field happens to equal the baseline.
    editedTeams.keys.forEach { teamId ->
        if (teamId !in editedRosters) {
            persistedRosterByTeam[teamId]?.takeIf { it.isNotEmpty() }?.let { roster ->
                editedRosters[teamId] = roster.sortedBy { it.id }
            }
        }
    }

    // Persisted teams that do not belong to the deterministic requested universe are genuine editor
    // additions. Keep them and their players instead of silently deleting them at career creation.
    persistedById.forEach { (teamId, team) ->
        if (teamId !in requestedById) {
            editedTeams[teamId] = team
            persistedRosterByTeam[teamId]?.takeIf { it.isNotEmpty() }?.let { roster ->
                editedRosters[teamId] = roster.sortedBy { it.id }
            }
        }
    }

    val result = PreCareerEditorOverrides(editedTeams, editedRosters)
    return result.takeUnless { it.isEmpty }
}

internal fun applyPreCareerEditorOverrides(
    seedTeams: List<Team>,
    seedPlayers: List<Player>,
    seedLoans: List<PlayerLoan>,
    overrides: PreCareerEditorOverrides?
): Triple<List<Team>, List<Player>, List<PlayerLoan>> {
    if (overrides == null || overrides.isEmpty) {
        return Triple(seedTeams, seedPlayers, seedLoans)
    }

    val requestedSelectionById = seedTeams.associate { it.id to it.isPlayerControlled }
    val mergedTeamsById = LinkedHashMap<Long, Team>()
    seedTeams.forEach { team -> mergedTeamsById[team.id] = team }
    overrides.teamsById.forEach { (teamId, edited) ->
        mergedTeamsById[teamId] = edited.copy(
            isPlayerControlled = requestedSelectionById[teamId] ?: edited.isPlayerControlled
        )
    }

    val replacedTeamIds = overrides.rostersByTeamId.keys
    val mergedPlayers = buildList {
        addAll(seedPlayers.filterNot { it.teamId in replacedTeamIds })
        replacedTeamIds.sorted().forEach { teamId ->
            addAll(overrides.rostersByTeamId[teamId].orEmpty())
        }
    }.distinctBy { it.id }

    val validPlayerIds = mergedPlayers.mapTo(hashSetOf()) { it.id }
    val validTeamIds = mergedTeamsById.keys
    val mergedLoans = seedLoans.filter { loan ->
        loan.playerId in validPlayerIds &&
            loan.ownerTeamId in validTeamIds &&
            loan.borrowerTeamId in validTeamIds
    }

    return Triple(mergedTeamsById.values.toList(), mergedPlayers, mergedLoans)
}
