package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fc26SeedPlannerTest {
    @Test fun `optimized FC26 fallback exactly matches canonical selection for production universe`() {
        val teams = ProductionCareerSeedPrewarm.buildProductionTeamUniverse()
        assertTrue(teams.size >= 1_000)
        teams.forEach { team ->
            val expected = Fc26FallbackRosterPolicy.select(
                DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
            )
            val optimized = DefaultData.generateFc26FallbackRosterForTeam(
                team.id, team.rating, team.name, team.country
            )
            assertEquals("${team.country}/${team.name}", expected, optimized)
        }
    }

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
        assertFalse(importedFreeAgent.isFc26UnassignedSourceClub())
        assertNull(importedFreeAgent.sourceMetadataOrNull()?.assignmentStatus)
        assertEquals(clubPlayer.overall, importedClubPlayer.force)
        assertEquals(clubPlayer.potential, importedClubPlayer.potential)
        assertTrue(plan.players.any { it.name == "Procedural" && it.teamId == fallback.id })
        assertEquals(2, plan.report.importedFc26Players)
        assertEquals(0, plan.report.skippedDatasetPlayers)
        assertEquals(2, plan.report.bulkImportedFc26Players)
        assertEquals(0, plan.report.importedUnassignedClubPlayers)
        assertEquals(1, plan.report.matchedClubs)
        assertEquals(1, plan.report.fallbackRostersRequired)
        assertTrue(plan.loans.isEmpty())
    }

    @Test fun `unmatched club player is imported unassigned while source club metadata is preserved`() {
        val target = team(88L, "Unrelated Target")
        val source = sourcePlayer(9999L, "Unresolved FC26 Club", sourceId = 301L, fullName = "Unassigned Real Player")
        val dataset = Fc26Dataset(
            manifest(playerCount = 1, clubCount = 1),
            listOf(source)
        )

        val plan = Fc26SeedPlanner.build(listOf(target), dataset) { emptyList() }
        val imported = plan.players.single()

        assertNull(imported.teamId)
        assertEquals(source.stableId, imported.id)
        assertEquals(source.overall, imported.force)
        assertEquals(source.potential, imported.potential)
        assertEquals(source.atributos, imported.atributos)
        assertTrue(imported.isFc26UnassignedSourceClub())
        assertEquals(0, imported.contractDurationWeeks)
        assertEquals(0L, imported.salary)
        val metadata = imported.sourceMetadataOrNull()
        assertNotNull(metadata)
        assertEquals(9999L, metadata?.sourceClubTeamId)
        assertEquals("Unresolved FC26 Club", metadata?.sourceClubName)
        assertEquals("UNASSIGNED_SOURCE_CLUB", metadata?.assignmentStatus)
        assertTrue((metadata?.sourceContractDurationWeeks ?: 0) > 0)
        assertTrue((metadata?.sourceSalary ?: 0L) > 0L)
        assertEquals(0, plan.report.importedFc26Players)
        assertEquals(1, plan.report.skippedDatasetPlayers)
        assertEquals(1, plan.report.bulkImportedFc26Players)
        assertEquals(1, plan.report.importedUnassignedClubPlayers)
        assertEquals(1, plan.report.importedUnmatchedClubPlayers)
        assertEquals(0, plan.report.importedAmbiguousClubPlayers)
        assertEquals(1, plan.report.unmatchedClubs)
    }

    @Test fun `loan marker with unknown owner is quarantined instead of becoming borrower ownership`() {
        val arsenal = team(2L, "Arsenal FC")
        val loaned = sourcePlayer(1L, "Arsenal", sourceId = 201L, fullName = "Loaned Player", loanedFrom = "Other Club")
        val dataset = Fc26Dataset(manifest(playerCount = 1, clubCount = 1, loanedPlayerCount = 1), listOf(loaned))
        val plan = Fc26SeedPlanner.build(listOf(arsenal), dataset) { emptyList() }

        assertEquals(1, plan.report.unresolvedLoans)
        assertEquals(1, plan.report.ownerNotFound)
        assertEquals(0, plan.report.successfullyMappedLoans)
        assertTrue(plan.loans.isEmpty())
        val mapped = plan.players.single()
        assertNull(mapped.teamId)
        assertTrue(mapped.isOnLoan)
        assertTrue(mapped.isFc26LoanOwnershipQuarantined())
        assertNull(mapped.originalTeamId)
        assertEquals(0, mapped.contractDurationWeeks)
        assertEquals(0L, mapped.salary)
        assertEquals(loaned.overall, mapped.force)
        assertEquals(loaned.potential, mapped.potential)
        assertEquals(loaned.atributos, mapped.atributos)
        assertTrue(mapped.atributosJson.orEmpty().contains("Other Club"))
        val metadata = mapped.sourceMetadataOrNull()
        assertEquals(Fc26LoanResolutionStatus.OWNER_NOT_FOUND.name, metadata?.loanResolutionStatus)
        assertEquals("LOAN_OWNERSHIP_UNRESOLVED", metadata?.assignmentStatus)
        assertEquals(2L, metadata?.loanBorrowerTeamId)
        assertTrue((metadata?.sourceContractDurationWeeks ?: 0) > 0)
        assertTrue((metadata?.sourceSalary ?: 0L) > 0L)
        assertEquals(1, plan.players.size)
        assertEquals(1, plan.players.map { it.id }.distinct().size)
    }

    @Test fun `valid FC26 loan keeps one player in borrower roster and owner identity`() {
        val arsenal = team(2L, "Arsenal FC")
        val chelsea = team(3L, "Chelsea FC")
        val loaned = sourcePlayer(1L, "Arsenal", sourceId = 401L, fullName = "Safe Loan Player", loanedFrom = "Chelsea")
        val ownerRosterPlayer = sourcePlayer(2L, "Chelsea", sourceId = 402L, fullName = "Owner Roster Player")
        val dataset = Fc26Dataset(
            manifest(playerCount = 2, clubCount = 2, loanedPlayerCount = 1),
            listOf(loaned, ownerRosterPlayer)
        )

        val plan = Fc26SeedPlanner.build(listOf(arsenal, chelsea), dataset) { emptyList() }
        val persistedLoan = plan.loans.single()
        val mapped = plan.players.single { it.id == loaned.stableId }

        assertEquals(1, plan.report.resolvedLoans)
        assertEquals(0, plan.report.rejectedLoans)
        assertEquals(loaned.stableId, persistedLoan.playerId)
        assertEquals(3L, persistedLoan.ownerTeamId)
        assertEquals(2L, persistedLoan.borrowerTeamId)
        assertEquals(2L, mapped.teamId)
        assertEquals(3L, mapped.originalTeamId)
        assertTrue(mapped.isOnLoan)
        assertEquals(0, mapped.loanWeeksRemaining)
        assertTrue(Fc26LoanPolicy.isUnknownEndSnapshotLoan(persistedLoan))
        assertEquals("NOT_AVAILABLE", mapped.sourceMetadataOrNull()?.loanTemporalCoverage)
        assertEquals(2, plan.players.size)
        assertEquals(2, plan.players.map { it.id }.distinct().size)
    }

    @Test fun `owner equal borrower is rejected and quarantined as self loan`() {
        val arsenal = team(2L, "Arsenal FC")
        val loaned = sourcePlayer(1L, "Arsenal", sourceId = 501L, fullName = "Self Loan Player", loanedFrom = "Arsenal")
        val dataset = Fc26Dataset(manifest(playerCount = 1, clubCount = 1, loanedPlayerCount = 1), listOf(loaned))

        val plan = Fc26SeedPlanner.build(listOf(arsenal), dataset) { emptyList() }

        assertTrue(plan.loans.isEmpty())
        assertEquals(1, plan.report.selfLoansRejected)
        assertEquals(1, plan.report.rejectedLoans)
        assertEquals(Fc26LoanResolutionStatus.SELF_LOAN, plan.report.loanResolutions.single().status)
        val mapped = plan.players.single()
        assertNull(mapped.teamId)
        assertNull(mapped.originalTeamId)
        assertTrue(mapped.isOnLoan)
        assertTrue(mapped.isFc26LoanOwnershipQuarantined())
        assertEquals(0, mapped.contractDurationWeeks)
        assertEquals(0L, mapped.salary)
        assertEquals("LOAN_OWNERSHIP_UNRESOLVED", mapped.sourceMetadataOrNull()?.assignmentStatus)
        assertEquals(Fc26LoanResolutionStatus.SELF_LOAN.name, mapped.sourceMetadataOrNull()?.loanResolutionStatus)
    }

    @Test fun `audited alias resolves owner to canonical identity`() {
        val arsenal = team(2L, "Arsenal FC")
        val inter = team(3L, "Internazionale")
        val loaned = sourcePlayer(1L, "Arsenal", sourceId = 601L, fullName = "Alias Loan Player", loanedFrom = "Inter Milan")
        val ownerRosterPlayer = sourcePlayer(2L, "Internazionale", sourceId = 602L, fullName = "Inter Owner Player")
        val dataset = Fc26Dataset(
            manifest(playerCount = 2, clubCount = 2, loanedPlayerCount = 1),
            listOf(loaned, ownerRosterPlayer)
        )

        val plan = Fc26SeedPlanner.build(listOf(arsenal, inter), dataset) { emptyList() }

        assertEquals(1, plan.loans.size)
        assertEquals(3L, plan.loans.single().ownerTeamId)
        assertEquals(Fc26LoanResolutionStatus.RESOLVED, plan.report.loanResolutions.single().status)
    }

    @Test fun `owner alias collision fails closed as ambiguous`() {
        val loaned = sourcePlayer(1L, "Borrower", sourceId = 701L, fullName = "Ambiguous Loan Player", loanedFrom = "Twin Club")
        val ownerA = sourcePlayer(2L, "Twin Club", sourceId = 702L, fullName = "Twin A")
        val ownerB = sourcePlayer(3L, "Twin Club", sourceId = 703L, fullName = "Twin B")
        val dataset = Fc26Dataset(
            manifest(playerCount = 3, clubCount = 3, loanedPlayerCount = 1),
            listOf(loaned, ownerA, ownerB)
        )
        val matches = listOf(
            match(1L, "Borrower", targetId = 10L),
            match(2L, "Twin Club", targetId = 20L),
            match(3L, "Twin Club", targetId = 30L)
        )

        val result = Fc26LoanResolver.resolve(dataset, matches)

        assertTrue(result.loans.isEmpty())
        assertEquals(1, result.audit.ambiguousLoans)
        assertEquals(Fc26LoanResolutionStatus.AMBIGUOUS_OWNER, result.audit.resolutions.single().status)
    }

    @Test fun `ambiguous owner target is classified before null target`() {
        val loaned = sourcePlayer(1L, "Borrower", sourceId = 711L, fullName = "Ambiguous Target Player", loanedFrom = "Owner")
        val owner = sourcePlayer(2L, "Owner", sourceId = 712L, fullName = "Owner Player")
        val dataset = Fc26Dataset(
            manifest(playerCount = 2, clubCount = 2, loanedPlayerCount = 1),
            listOf(loaned, owner)
        )
        val matches = listOf(
            match(1L, "Borrower", targetId = 10L),
            Fc26ClubMatch(
                sourceClubTeamId = 2L,
                sourceClubName = "Owner",
                leagueId = 1L,
                leagueName = "Test League",
                playerCount = 1,
                status = Fc26ClubMatchStatus.AMBIGUOUS,
                targetTeamId = null,
                targetTeamName = null,
                reason = "two canonical targets"
            )
        )

        val result = Fc26LoanResolver.resolve(dataset, matches)

        assertTrue(result.loans.isEmpty())
        assertEquals(1, result.audit.ambiguousLoans)
        assertEquals(0, result.audit.ownerNotFound)
        assertEquals(Fc26LoanResolutionStatus.AMBIGUOUS_OWNER, result.audit.resolutions.single().status)
    }

    @Test fun `invalid player references never throw from snapshot sentinel detection`() {
        fun invalidLoan(playerId: Long, id: Long) = PlayerLoan(
            id = id,
            playerId = playerId,
            ownerTeamId = 10L,
            borrowerTeamId = 20L,
            startSeason = Fc26LoanPolicy.UNKNOWN_SEASON,
            startWeek = Fc26LoanPolicy.UNKNOWN_WEEK,
            durationWeeks = Fc26LoanPolicy.UNKNOWN_DURATION_WEEKS,
            remainingWeeks = Fc26LoanPolicy.UNKNOWN_DURATION_WEEKS,
            weeklyFee = 0L,
            status = "ACTIVE"
        )

        assertFalse(Fc26LoanPolicy.isUnknownEndSnapshotLoan(invalidLoan(playerId = 0L, id = 0L)))
        assertFalse(Fc26LoanPolicy.isUnknownEndSnapshotLoan(invalidLoan(playerId = -7L, id = 7L)))
    }

    @Test fun `unresolved borrower fails closed even when owner exists`() {
        val loaned = sourcePlayer(1L, "Borrower", sourceId = 801L, fullName = "Borrower Missing Player", loanedFrom = "Owner")
        val owner = sourcePlayer(2L, "Owner", sourceId = 802L, fullName = "Owner Player")
        val dataset = Fc26Dataset(
            manifest(playerCount = 2, clubCount = 2, loanedPlayerCount = 1),
            listOf(loaned, owner)
        )
        val matches = listOf(
            Fc26ClubMatch(1L, "Borrower", 1L, "Test League", 1, Fc26ClubMatchStatus.UNMATCHED, reason = "no canonical target"),
            match(2L, "Owner", targetId = 20L)
        )

        val result = Fc26LoanResolver.resolve(dataset, matches)

        assertTrue(result.loans.isEmpty())
        assertEquals(1, result.audit.borrowerNotFound)
        assertEquals(Fc26LoanResolutionStatus.BORROWER_NOT_FOUND, result.audit.resolutions.single().status)
    }

    @Test fun `same snapshot resolution is deterministic and idempotent`() {
        val arsenal = team(2L, "Arsenal FC")
        val chelsea = team(3L, "Chelsea FC")
        val loaned = sourcePlayer(1L, "Arsenal", sourceId = 901L, fullName = "Idempotent Loan Player", loanedFrom = "Chelsea")
        val ownerRosterPlayer = sourcePlayer(2L, "Chelsea", sourceId = 902L, fullName = "Idempotent Owner Player")
        val dataset = Fc26Dataset(
            manifest(playerCount = 2, clubCount = 2, loanedPlayerCount = 1),
            listOf(loaned, ownerRosterPlayer)
        )

        val first = Fc26SeedPlanner.build(listOf(arsenal, chelsea), dataset) { emptyList() }
        val second = Fc26SeedPlanner.build(listOf(arsenal, chelsea), dataset) { emptyList() }

        assertEquals(first.loans, second.loans)
        assertEquals(first.players, second.players)
        assertEquals(first.report.loanResolutions, second.report.loanResolutions)
        assertEquals(-loaned.stableId, first.loans.single().id)
    }

    private fun match(sourceId: Long, sourceName: String, targetId: Long) = Fc26ClubMatch(
        sourceClubTeamId = sourceId,
        sourceClubName = sourceName,
        leagueId = 1L,
        leagueName = "Test League",
        playerCount = 1,
        status = Fc26ClubMatchStatus.MATCHED,
        targetTeamId = targetId,
        targetTeamName = "Target $targetId",
        reason = "test canonical match"
    )

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
