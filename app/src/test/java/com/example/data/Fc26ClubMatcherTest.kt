package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fc26ClubMatcherTest {
    @Test fun `matcher resolves stable team aliases without fuzzy guessing`() {
        val dataset = datasetFor("Arsenal", 1L, leagueId = 13L)
        val teams = listOf(team(2L, "Arsenal FC", "Inglaterra"))
        val match = Fc26ClubMatcher.match(dataset, teams).single()
        assertEquals(Fc26ClubMatchStatus.MATCHED, match.status)
        assertEquals(2L, match.targetTeamId)
    }

    @Test fun `explicit FC26 source id resolves Liverpool stable target instead of legacy duplicate`() {
        val dataset = datasetFor("Liverpool", 9L, leagueId = 13L)
        val teams = listOf(
            team(3L, "Liverpool FC", "Inglaterra"),
            team(57L, "Liverpool", "Inglaterra")
        )

        val match = Fc26ClubMatcher.match(dataset, teams).single()

        assertEquals(Fc26ClubMatchStatus.MATCHED, match.status)
        assertEquals(3L, match.targetTeamId)
        assertTrue(match.reason.startsWith("explicit source club id"))
    }

    @Test fun `audited canonical FC26 variants resolve only to stable materialized targets`() {
        val dataset = datasetFor("Real Betis Balompié", 449L, leagueId = 53L)
        val teams = listOf(team(207L, "Real Betis", "Espanha"))

        val match = Fc26ClubMatcher.match(dataset, teams).single()

        assertEquals(Fc26ClubMatchStatus.MATCHED, match.status)
        assertEquals(207L, match.targetTeamId)
    }

    @Test fun `source id override is rejected when source name does not match audited identity`() {
        val dataset = datasetFor("Not Liverpool", 9L, leagueId = 13L)
        val teams = listOf(team(3L, "Liverpool FC", "Inglaterra"))

        val match = Fc26ClubMatcher.match(dataset, teams).single()

        assertNotEquals(Fc26ClubMatchStatus.MATCHED, match.status)
        assertNull(match.targetTeamId)
    }

    @Test fun `matcher leaves unknown club unmatched`() {
        val dataset = datasetFor("Completely Unknown Club", 999L)
        val match = Fc26ClubMatcher.match(dataset, listOf(team(2L, "Arsenal FC", "Teste"))).single()
        assertEquals(Fc26ClubMatchStatus.UNMATCHED, match.status)
    }

    @Test fun `matcher never chooses between duplicate target candidates`() {
        val dataset = datasetFor("City AFC", 999L)
        val teams = listOf(team(50_001L, "City FC", "Teste"), team(50_002L, "City SC", "Teste"))
        val match = Fc26ClubMatcher.match(dataset, teams).single()
        assertEquals(Fc26ClubMatchStatus.AMBIGUOUS, match.status)
    }

    @Test fun `candidate scoring never promotes a fuzzy suggestion into a match`() {
        val dataset = datasetFor("Juventus", 45L, leagueId = 31L)
        val teams = listOf(team(77_001L, "Juventude FC", "Itália"))

        val match = Fc26ClubMatcher.match(dataset, teams).single()
        val audit = Fc26ClubMatcher.auditCandidates(dataset, teams).single()

        assertEquals(Fc26ClubMatchStatus.UNMATCHED, match.status)
        assertEquals(Fc26ClubMatchStatus.UNMATCHED, audit.currentStatus)
        assertTrue(audit.candidates.isNotEmpty())
        assertNull(match.targetTeamId)
    }

    @Test fun `candidate audit distinguishes stable target missing from arbitrary unmatched club`() {
        val dataset = datasetFor("Juventus", 45L, leagueId = 31L)
        val teams = listOf(team(77_001L, "Torino City", "Itália"))

        val audit = Fc26ClubMatcher.auditCandidates(dataset, teams).single()

        assertEquals(Fc26TargetMaterializationStatus.STABLE_TARGET_MISSING, audit.materializationStatus)
        assertTrue(audit.expectedStableTeamId != null)
        assertEquals("Juventus", audit.expectedStableTeamName)
    }

    private fun team(id: Long, name: String, country: String) = Team(
        id = id, name = name, city = "X", state = "X", country = country, division = 1, rating = 70
    )

    private fun datasetFor(clubName: String, clubId: Long, leagueId: Long? = null): Fc26Dataset {
        val p = samplePlayer(clubName, clubId, leagueId)
        return Fc26Dataset(manifest(playerCount = 1, clubCount = 1), listOf(p))
    }

    private fun samplePlayer(clubName: String, clubId: Long, leagueId: Long?) = Fc26NormalizedPlayer(
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
        leagueId = leagueId,
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
