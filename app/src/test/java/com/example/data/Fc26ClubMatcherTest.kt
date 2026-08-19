package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class Fc26ClubMatcherTest {
    @Test fun `matcher resolves stable team aliases without fuzzy guessing`() {
        val dataset = datasetFor("Arsenal", 1L)
        val teams = listOf(team(2L, "Arsenal FC"))
        val match = Fc26ClubMatcher.match(dataset, teams).single()
        assertEquals(Fc26ClubMatchStatus.MATCHED, match.status)
        assertEquals(2L, match.targetTeamId)
    }

    @Test fun `matcher leaves unknown club unmatched`() {
        val dataset = datasetFor("Completely Unknown Club", 999L)
        val match = Fc26ClubMatcher.match(dataset, listOf(team(2L, "Arsenal FC"))).single()
        assertEquals(Fc26ClubMatchStatus.UNMATCHED, match.status)
    }

    @Test fun `matcher never chooses between duplicate target candidates`() {
        val dataset = datasetFor("City AFC", 999L)
        val teams = listOf(team(50_001L, "City FC"), team(50_002L, "City SC"))
        val match = Fc26ClubMatcher.match(dataset, teams).single()
        assertEquals(Fc26ClubMatchStatus.AMBIGUOUS, match.status)
    }

    private fun team(id: Long, name: String) = Team(
        id = id, name = name, city = "X", state = "X", country = "Teste", division = 1, rating = 70
    )

    private fun datasetFor(clubName: String, clubId: Long): Fc26Dataset {
        val p = samplePlayer(clubName, clubId)
        return Fc26Dataset(manifest(playerCount = 1, clubCount = 1), listOf(p))
    }

    private fun samplePlayer(clubName: String, clubId: Long) = Fc26NormalizedPlayer(
        sourcePlayerId = clubId + 1000,
        shortName = "T",
        fullName = "Test Player $clubId",
        sourceAge = 25,
        birthDateIso = "2000-01-01",
        heightCm = 180,
        weightKg = 75,
        nationality = "Test",
        positions = listOf("CM"),
        overall = 70,
        potential = 75,
        valueEur = 1_000_000,
        wageEur = 10_000,
        leagueId = 1,
        leagueName = "Test League",
        clubTeamId = clubId,
        clubName = clubName,
        clubPosition = "CM",
        clubLoanedFrom = null,
        contractUntilYear = 2028,
        preferredFoot = "Right",
        weakFoot = 3,
        skillMoves = 3,
        internationalReputation = 1,
        workRate = "Medium/Medium",
        releaseClauseEur = 0,
        summaryPace = 70,
        summaryShooting = 70,
        summaryPassing = 70,
        summaryDribbling = 70,
        summaryDefending = 70,
        summaryPhysic = 70,
        atributos = Atributos()
    )

    private fun manifest(playerCount: Int, clubCount: Int) = Fc26DatasetManifest(
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
        freeAgentCount = 0,
        loanedPlayerCount = 0,
        validationStatus = "VALIDATED",
        money = Fc26MoneyManifest("EUR", "BRL", 6.2567, "2025-09-19", "test")
    )
}
