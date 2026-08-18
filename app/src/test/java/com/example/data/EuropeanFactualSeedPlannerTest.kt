package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EuropeanFactualSeedPlannerTest {

    @Test
    fun `gameplay ready squad replaces procedural roster and factual loan joins borrower`() {
        val united = team(
            id = 5L,
            name = "Manchester United",
            country = "Inglaterra",
            rating = 84
        )
        val trabzonspor = team(
            id = requireNotNull(StableTeamIdentityRegistry.idFor("Turquia", "Trabzonspor")),
            name = "Trabzonspor",
            country = "Turquia",
            rating = 78
        )

        val fallbackCalls = mutableListOf<Long>()
        val plan = EuropeanFactualSeedPlanner.build(
            teams = listOf(united, trabzonspor),
            proceduralRosterFactory = { team ->
                fallbackCalls += team.id
                listOf(proceduralPlayer(team, 900_000_000L + fallbackCalls.size))
            }
        )

        assertEquals(setOf(united.id), plan.factualSquadTeamIds)
        assertEquals(setOf(trabzonspor.id), plan.proceduralFallbackTeamIds)
        assertEquals(listOf(trabzonspor.id), fallbackCalls)
        assertEquals(1, plan.loans.size)
        assertTrue(plan.blockedLoans.isEmpty())

        val unitedPlayers = plan.players.filter { it.teamId == united.id }
        assertEquals(32, unitedPlayers.size)
        assertTrue(unitedPlayers.all { StableRealPlayerIdentity.isRealPlayerId(it.id) })

        val onana = plan.players.single { it.name == "Andre Onana" }
        assertEquals(trabzonspor.id, onana.teamId)
        assertTrue(onana.isOnLoan)
        assertEquals(united.id, onana.originalTeamId)
        assertEquals(onana.id, plan.loans.single().playerId)
    }

    @Test
    fun `missing borrower blocks factual loan instead of inventing a team`() {
        val united = team(5L, "Manchester United", "Inglaterra", 84)

        val plan = EuropeanFactualSeedPlanner.build(
            teams = listOf(united),
            proceduralRosterFactory = { error("United factual squad must not use fallback") }
        )

        assertEquals(32, plan.players.size)
        assertTrue(plan.loans.isEmpty())
        assertEquals(1, plan.blockedLoans.size)
        assertEquals("Andre Onana", plan.blockedLoans.single().playerName)
        assertTrue(plan.blockedLoans.single().reason.contains("Trabzonspor"))
        assertFalse(plan.players.any { it.name == "Andre Onana" })
    }

    @Test
    fun `missing owner also blocks factual loan`() {
        val trabzonspor = team(
            id = requireNotNull(StableTeamIdentityRegistry.idFor("Turquia", "Trabzonspor")),
            name = "Trabzonspor",
            country = "Turquia",
            rating = 78
        )

        val plan = EuropeanFactualSeedPlanner.build(
            teams = listOf(trabzonspor),
            proceduralRosterFactory = { team -> listOf(proceduralPlayer(team, 905_000_001L)) }
        )

        assertTrue(plan.loans.isEmpty())
        assertEquals(1, plan.blockedLoans.size)
        assertTrue(plan.blockedLoans.single().reason.contains("Manchester United"))
        assertFalse(plan.players.any { it.name == "Andre Onana" })
    }

    @Test
    fun `club without factual snapshot remains explicit procedural fallback`() {
        val realMadrid = team(201L, "Real Madrid", "Espanha", 90)

        val plan = EuropeanFactualSeedPlanner.build(
            teams = listOf(realMadrid),
            squadCatalog = EuropeanRealSquadCatalog(emptyList()),
            loanCatalog = EuropeanRealLoanCatalog(emptyList()),
            proceduralRosterFactory = { team -> listOf(proceduralPlayer(team, 910_000_001L)) }
        )

        assertTrue(plan.factualSquadTeamIds.isEmpty())
        assertEquals(setOf(realMadrid.id), plan.proceduralFallbackTeamIds)
        assertEquals(1, plan.players.size)
        assertEquals(realMadrid.id, plan.players.single().teamId)
    }

    @Test
    fun `partial factual snapshot still uses procedural fallback`() {
        val realMadrid = team(201L, "Real Madrid", "Espanha", 90)
        val partial = EuropeanRealSquadSnapshot(
            country = "Espanha",
            clubName = "Real Madrid",
            domesticSeasonLabel = "2026/27",
            verifiedAsOfIso = "2026-08-18",
            sourceRefs = listOf("official:test-partial"),
            players = listOf(
                EuropeanRealPlayerTemplate(
                    fullName = "Partial Keeper",
                    birthDateIso = "2000-01-01",
                    nationality = "Spain",
                    position = "GOL"
                )
            )
        )

        val plan = EuropeanFactualSeedPlanner.build(
            teams = listOf(realMadrid),
            squadCatalog = EuropeanRealSquadCatalog(listOf(partial)),
            loanCatalog = EuropeanRealLoanCatalog(emptyList()),
            proceduralRosterFactory = { team -> listOf(proceduralPlayer(team, 915_000_001L)) }
        )

        assertTrue(plan.factualSquadTeamIds.isEmpty())
        assertEquals(setOf(realMadrid.id), plan.proceduralFallbackTeamIds)
        assertEquals(listOf("Procedural Real Madrid"), plan.players.map { it.name })
    }

    @Test
    fun `planner rejects duplicate player ids across fallback rosters`() {
        val first = team(201L, "Real Madrid", "Espanha", 90)
        val second = team(202L, "FC Barcelona", "Espanha", 89)

        var failed = false
        try {
            EuropeanFactualSeedPlanner.build(
                teams = listOf(first, second),
                squadCatalog = EuropeanRealSquadCatalog(emptyList()),
                loanCatalog = EuropeanRealLoanCatalog(emptyList()),
                proceduralRosterFactory = { team -> proceduralPlayer(team, 920_000_001L).let(::listOf) }
            )
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    private fun team(id: Long, name: String, country: String, rating: Int) = Team(
        id = id,
        name = name,
        city = name,
        state = "EU",
        country = country,
        division = 1,
        rating = rating,
        stadiumName = "Stadium"
    )

    private fun proceduralPlayer(team: Team, id: Long) = Player(
        id = id,
        teamId = team.id,
        name = "Procedural ${team.name}",
        age = 22,
        nationality = team.country,
        position = "ATA",
        force = team.rating
    )
}
