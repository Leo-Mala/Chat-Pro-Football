package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fc26LoanIdentityMetadataTest {

    @Test
    fun `resolved borrower and owner persist as undated identity without activating gameplay loan`() {
        val borrower = team(2L, "Arsenal")
        val owner = team(77L, "Owner FC")
        val loaned = sourcePlayer(
            clubId = 1L,
            clubName = "Arsenal",
            sourceId = 501L,
            fullName = "Loaned One",
            loanedFrom = "Owner FC"
        )
        val ownerPlayer = sourcePlayer(
            clubId = 77_001L,
            clubName = "Owner FC",
            sourceId = 502L,
            fullName = "Owner Squad Player"
        )
        val dataset = Fc26Dataset(
            manifest(playerCount = 2, clubCount = 2, loanedPlayerCount = 1),
            listOf(loaned, ownerPlayer)
        )

        val plan = Fc26SeedPlanner.build(listOf(borrower, owner), dataset) { emptyList() }
        val player = plan.players.single { it.id == loaned.stableId }
        val loanMetadata = requireNotNull(player.fc26LoanIdentityMetadataOrNull())

        assertEquals(Fc26LoanIdentityStatus.RESOLVED_IDENTITY_UNDATED.name, loanMetadata.identityStatus)
        assertEquals(borrower.id, loanMetadata.borrowerTargetTeamId)
        assertEquals(owner.id, loanMetadata.ownerTargetTeamId)
        assertEquals("Owner FC", loanMetadata.sourceOwnerClubName)
        assertEquals("UNKNOWN_FROM_SOURCE_SNAPSHOT", loanMetadata.durationStatus)
        assertFalse(loanMetadata.gameplayLoanMaterialized)
        assertFalse(player.isOnLoan)
        assertEquals(0, player.loanWeeksRemaining)
        assertNull(player.originalTeamId)
        assertTrue(plan.loans.isEmpty())
        assertEquals(1, plan.report.datasetLoanPlayers)
        assertEquals(1, plan.report.resolvedLoanIdentitiesUndated)
        assertEquals(0, plan.report.unresolvedLoanIdentities)
        assertEquals(0, plan.report.successfullyMappedLoans)
        assertEquals(1, plan.report.unresolvedLoans)
        assertEquals(0, plan.report.materializedActiveLoans)
        assertEquals(loaned.overall, player.force)
        assertEquals(loaned.potential, player.potential)
        assertEquals(loaned.atributos, player.atributos)
    }

    @Test
    fun `unresolved owner remains explicit metadata and never invents original team`() {
        val borrower = team(2L, "Arsenal")
        val loaned = sourcePlayer(
            clubId = 1L,
            clubName = "Arsenal",
            sourceId = 601L,
            fullName = "Loaned Two",
            loanedFrom = "Missing Owner"
        )
        val missingOwnerPlayer = sourcePlayer(
            clubId = 88_001L,
            clubName = "Missing Owner",
            sourceId = 602L,
            fullName = "Missing Owner Squad Player"
        )
        val dataset = Fc26Dataset(
            manifest(playerCount = 2, clubCount = 2, loanedPlayerCount = 1),
            listOf(loaned, missingOwnerPlayer)
        )

        val plan = Fc26SeedPlanner.build(listOf(borrower), dataset) { emptyList() }
        val player = plan.players.single { it.id == loaned.stableId }
        val loanMetadata = requireNotNull(player.fc26LoanIdentityMetadataOrNull())

        assertEquals(Fc26LoanIdentityStatus.OWNER_UNRESOLVED.name, loanMetadata.identityStatus)
        assertEquals(Fc26LoanOwnerStatus.SOURCE_TARGET_UNMATCHED.name, loanMetadata.ownerStatus)
        assertNull(loanMetadata.ownerTargetTeamId)
        assertFalse(player.isOnLoan)
        assertEquals(0, player.loanWeeksRemaining)
        assertNull(player.originalTeamId)
        assertTrue(plan.loans.isEmpty())
        assertEquals(0, plan.report.resolvedLoanIdentitiesUndated)
        assertEquals(1, plan.report.unresolvedLoanIdentities)
        assertEquals(0, plan.report.materializedActiveLoans)
    }

    private fun team(id: Long, name: String) = Team(
        id = id,
        name = name,
        city = "X",
        state = "X",
        country = "Teste",
        division = 1,
        rating = 70
    )

    private fun sourcePlayer(
        clubId: Long,
        clubName: String,
        sourceId: Long,
        fullName: String,
        loanedFrom: String? = null
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
        leagueId = 999L,
        leagueName = "Test League",
        clubTeamId = clubId,
        clubName = clubName,
        clubPosition = "CM",
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
        loanedPlayerCount: Int
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
        freeAgentCount = 0,
        loanedPlayerCount = loanedPlayerCount,
        validationStatus = "VALIDATED",
        money = Fc26MoneyManifest("EUR", "BRL", 6.2567, "2025-09-19", "test")
    )
}
