package com.example.data

/**
 * Phase 9.13 query/action-set layer for the weekly CPU roster lifecycle.
 *
 * These queries intentionally use lightweight scalar projections instead of materializing every
 * Player entity. The database schema remains V22: no entity, index or migration change is required.
 */
internal data class WeeklyRosterAggregate(
    val teamId: Long,
    val rosterSize: Int,
    val goalkeeperCount: Int
)

internal data class WeeklyRenewalCandidate(
    val id: Long,
    val teamId: Long,
    val age: Int,
    val position: String,
    val force: Int,
    val potential: Int,
    /** Loaned-out players are owned by [teamId] but are not part of that owner's sporting roster. */
    val countsInRoster: Boolean = true
)

internal data class WeeklyLoanRenewalCandidate(
    val id: Long,
    val ownerTeamId: Long,
    val borrowerTeamId: Long,
    val age: Int,
    val position: String,
    val force: Int,
    val potential: Int
)

internal data class WeeklyRenewalDecision(
    val playerId: Long,
    val contractDurationWeeks: Int,
    val salary: Long
)

internal fun GameRepository.getWeeklyRosterAggregates(): Map<Long, WeeklyRosterAggregate> {
    val cursor = db.openHelper.writableDatabase.query(
        """
        SELECT teamId,
               COUNT(*) AS rosterSize,
               SUM(CASE WHEN position = 'GOL' THEN 1 ELSE 0 END) AS goalkeeperCount
        FROM players
        WHERE teamId IS NOT NULL
        GROUP BY teamId
        """.trimIndent()
    )
    return cursor.use {
        val teamIdIndex = it.getColumnIndexOrThrow("teamId")
        val rosterSizeIndex = it.getColumnIndexOrThrow("rosterSize")
        val goalkeeperCountIndex = it.getColumnIndexOrThrow("goalkeeperCount")
        buildMap {
            while (it.moveToNext()) {
                val aggregate = WeeklyRosterAggregate(
                    teamId = it.getLong(teamIdIndex),
                    rosterSize = it.getInt(rosterSizeIndex),
                    goalkeeperCount = it.getInt(goalkeeperCountIndex)
                )
                put(aggregate.teamId, aggregate)
            }
        }
    }
}

internal fun GameRepository.getWeeklyRenewalCandidates(windowWeeks: Int): List<WeeklyRenewalCandidate> {
    require(windowWeeks > 0)
    val cursor = db.openHelper.writableDatabase.query(
        """
        SELECT id, teamId, age, position, force, potential
        FROM players
        WHERE teamId IS NOT NULL
          AND isOnLoan = 0
          AND contractDurationWeeks BETWEEN 1 AND $windowWeeks
        ORDER BY teamId ASC, id ASC
        """.trimIndent()
    )
    return cursor.use {
        val idIndex = it.getColumnIndexOrThrow("id")
        val teamIdIndex = it.getColumnIndexOrThrow("teamId")
        val ageIndex = it.getColumnIndexOrThrow("age")
        val positionIndex = it.getColumnIndexOrThrow("position")
        val forceIndex = it.getColumnIndexOrThrow("force")
        val potentialIndex = it.getColumnIndexOrThrow("potential")
        buildList {
            while (it.moveToNext()) {
                add(
                    WeeklyRenewalCandidate(
                        id = it.getLong(idIndex),
                        teamId = it.getLong(teamIdIndex),
                        age = it.getInt(ageIndex),
                        position = it.getString(positionIndex),
                        force = it.getInt(forceIndex),
                        potential = it.getInt(potentialIndex),
                        countsInRoster = true
                    )
                )
            }
        }
    }
}

/**
 * Lightweight projection of expiring players who are currently playing for a borrower while their
 * main contract remains owned by [Player.originalTeamId]. The active PlayerLoan is deliberately
 * validated by the caller before this projection is used as ownership authority.
 */
internal fun GameRepository.getWeeklyLoanRenewalCandidates(windowWeeks: Int): List<WeeklyLoanRenewalCandidate> {
    require(windowWeeks > 0)
    val cursor = db.openHelper.writableDatabase.query(
        """
        SELECT id, originalTeamId AS ownerTeamId, teamId AS borrowerTeamId,
               age, position, force, potential
        FROM players
        WHERE teamId IS NOT NULL
          AND originalTeamId IS NOT NULL
          AND isOnLoan = 1
          AND contractDurationWeeks BETWEEN 1 AND $windowWeeks
        ORDER BY originalTeamId ASC, id ASC
        """.trimIndent()
    )
    return cursor.use {
        val idIndex = it.getColumnIndexOrThrow("id")
        val ownerTeamIdIndex = it.getColumnIndexOrThrow("ownerTeamId")
        val borrowerTeamIdIndex = it.getColumnIndexOrThrow("borrowerTeamId")
        val ageIndex = it.getColumnIndexOrThrow("age")
        val positionIndex = it.getColumnIndexOrThrow("position")
        val forceIndex = it.getColumnIndexOrThrow("force")
        val potentialIndex = it.getColumnIndexOrThrow("potential")
        buildList {
            while (it.moveToNext()) {
                add(
                    WeeklyLoanRenewalCandidate(
                        id = it.getLong(idIndex),
                        ownerTeamId = it.getLong(ownerTeamIdIndex),
                        borrowerTeamId = it.getLong(borrowerTeamIdIndex),
                        age = it.getInt(ageIndex),
                        position = it.getString(positionIndex),
                        force = it.getInt(forceIndex),
                        potential = it.getInt(potentialIndex)
                    )
                )
            }
        }
    }
}

internal fun GameRepository.applyWeeklyRenewals(decisions: Collection<WeeklyRenewalDecision>): Int {
    if (decisions.isEmpty()) return 0
    val statement = db.openHelper.writableDatabase.compileStatement(
        "UPDATE players SET contractDurationWeeks = ?, salary = ? WHERE id = ?"
    )
    var updated = 0
    decisions.forEach { decision ->
        statement.clearBindings()
        statement.bindLong(1, decision.contractDurationWeeks.toLong())
        statement.bindLong(2, decision.salary)
        statement.bindLong(3, decision.playerId)
        updated += statement.executeUpdateDelete()
    }
    return updated
}

internal fun GameRepository.getMaxPersistedPlayerId(): Long {
    val cursor = db.openHelper.writableDatabase.query("SELECT COALESCE(MAX(id), 0) AS maxId FROM players")
    return cursor.use {
        check(it.moveToFirst())
        it.getLong(it.getColumnIndexOrThrow("maxId"))
    }
}
