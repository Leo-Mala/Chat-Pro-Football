package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fc26SeedPlannerTest {
    @Test fun `planner imports matched squad and factual free agent while keeping procedural fallback`() {
        val arsenal = team(2L, "Arsenal FC")
        val fallback = team(88L, "Fallback Town")
        val clubPlayer = sourcePlayer(1L, "Arsenal", sourceId = 101L, fullName = "Real Club Player")
        val freeAgent = sourcePlayer(null, null, sourceId = 102L, fullName = "Real Free Agent")
        val dataset = Fc26Dataset(
            manifest(playerCount = 2, clubCount = 1, freeAgentCount = 1),
            listOf(clubPlayer, freeAgent)
        )

        val plan = Fc26SeedPlanner.build(listOf(arsenal, fallback), dataset) { team ->
            listOf(Player(id = team.id * 1000 + 1, teamId = team.id, name = "Procedural", age = 25, position = "MEI", force = 55))
        }

        val importedClubPlayer = plan.players.single { it.name == "Real Club Player" }
        val importedFreeAgent = plan.players.single { it.name == "Real Free Agent" }
        assertEquals(2L, importedClubPlayer.teamId)
        assertNull(importedFreeAgent.teamId)
        assertEquals(clubPlayer.overall, importedClubPlayer.force)
        assertEquals(clubPlayer.potential, importedClubPlayer.potential)
        assertTrue(plan.players.any { it.name == "Procedural" && it.teamId == fallback.id })
        assertEquals(2, plan.report.importedFc26Players)
        assertEquals(1, plan.report.matchedClubs)
        assertEquals(1, plan.report.fallbackRostersRequired)
        assertTrue(plan.loans.isEmpty())
    }

    @Test fun `loan marker is preserved as unresolved metadata instead of invented PlayerLoan`() {
        val arsenal = team(2L, "Arsenal FC")
        val loaned = sourcePlayer(1L, "Arsenal", sourceId = 201L, fullName = "Loaned Player", loanedFrom = "Other Club")
        val dataset = Fc26Dataset(manifest(playerCount = 1, clubCount = 1, loanedPlayerCount = 1), listOf(loaned))
        val plan = Fc26SeedPlanner.build(listOf(arsenal), dataset) { emptyList() }

        assertEquals(1, plan.report.unresolvedLoans)
        assertEquals(0, plan.report.successfullyMappedLoans)
        assertTrue(plan.loans.isEmpty())
        val mapped = plan.players.single()
        assertFalse(mapped.isOnLoan)
        assertNull(mapped.originalTeamId)
        assertTrue(mapped.atributosJson.orEmpty().contains("Other Club"))
    }

    private fun team(id: Long, name: String) = Team(
        id = id, name = name, city = "X", state = "X", country = "Teste", division = 1, rating = 70
    )

    private fun sourcePlayer(
        clubId: Long?, clubName: String?, sourceId: Long, fullName: String, loanedFrom: String? = null
    ) = Fc26NormalizedPlayer(
        sourcePlayerId = sourceId,
        shortName = fullName,
        fullName = fullName,
        sourceAge = 25,
        birthDateIso = "2000-01-${(sourceId % 20 + 1).toString().padStart(2, '0')}",
        heightCm = 180,
        weightKg = 75,
        nationality = "Test",
        positions = listOf("CM"),
        overall = 79,
        potential = 84,
        valueEur = 5_000_000,
        wageEur = 25_000,
        leagueId = clubId?.let { 1L },
        leagueName = clubId?.let { "Test League" },
        clubTeamId = clubId,
        clubName = clubName,
        clubPosition = clubId?.let { "CM" },
        clubLoanedFrom = loanedFrom,
        contractUntilYear = 2028,
        preferredFoot = "Right",
        weakFoot = 3,
        skillMoves = 3,
        internationalReputation = 2,
        workRate = "Medium/Medium",
        releaseClauseEur = 0,
        summaryPace = 75,
        summaryShooting = 75,
        summaryPassing = 80,
        summaryDribbling = 78,
        summaryDefending = 70,
        summaryPhysic = 75,
        atributos = Atributos()
    )

    private fun manifest(
        playerCount: Int,
        clubCount: Int,
        freeAgentCount: Int = 0,
        loanedPlayerCount: Int = 0
    ) = Fc26DatasetManifest(
        schemaVersion = 1,
        datasetSource = "FC26",
        datasetVersion = "2025-09-19",
        sourceFile = "test.csv",
        sourceSha256 = "source",
        assetFile = "test.tsv.gz",
        assetSha256 = "asset",
        playerCount = playerCount,
        clubCount = clubCount,
        leagueCount = 1,
        nationalityCount = 1,
        freeAgentCount = freeAgentCount,
        loanedPlayerCount = loanedPlayerCount,
        validationStatus = "VALIDATED",
        money = Fc26MoneyManifest("EUR", "BRL", 6.2567, "2025-09-19", "test")
    )
}
