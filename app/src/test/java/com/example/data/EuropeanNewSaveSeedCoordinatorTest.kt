package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EuropeanNewSaveSeedCoordinatorTest {
    @Test
    fun `factual seed is one-shot and cannot leak into a later repository operation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataset = EuropeanCanonicalDatasetLoader.loadForTesting(context.assets)
        val repositoryKey = Any()

        val proceduralTeams = dataset.clubFacts.map { fact ->
            Team(
                id = fact.teamId,
                name = fact.name,
                city = "Procedural City",
                state = "EU",
                country = fact.country,
                division = 1,
                rating = 75,
                stadiumName = "Procedural Stadium",
                logoUrl = null
            )
        }

        EuropeanNewSaveSeedCoordinator.prepareForDataset(repositoryKey, proceduralTeams, dataset)

        val factualTeams = EuropeanNewSaveSeedCoordinator.teamsForTesting(repositoryKey, proceduralTeams)
        val arsenal = factualTeams.single { it.name == "Arsenal FC" }
        assertEquals("London", arsenal.city)
        assertEquals("Fixture Stadium 01", arsenal.stadiumName)

        val firstConsume = EuropeanNewSaveSeedCoordinator.consumePlayersForKey(repositoryKey, emptyList())
        assertTrue(firstConsume.overridden)
        assertEquals(91, firstConsume.players.size)
        assertEquals(1, firstConsume.loans.size)

        val secondConsume = EuropeanNewSaveSeedCoordinator.consumePlayersForKey(repositoryKey, emptyList())
        assertFalse(secondConsume.overridden)
        assertTrue(secondConsume.players.isEmpty())
        assertTrue(secondConsume.loans.isEmpty())

        assertEquals(
            proceduralTeams,
            EuropeanNewSaveSeedCoordinator.teamsForTesting(repositoryKey, proceduralTeams)
        )
    }

    @Test
    fun `FC26 new save seed materializes resolved loan exactly once and preserves owner roster split`() {
        val repositoryKey = Any()
        val borrower = Team(2L, "Arsenal FC", "London", "ENG", "Inglaterra", 1, rating = 80)
        val owner = Team(3L, "Chelsea FC", "London", "ENG", "Inglaterra", 1, rating = 80)
        val loaned = sourcePlayer(
            clubId = 1L,
            clubName = "Arsenal",
            sourceId = 9001L,
            fullName = "FC26 New Save Loan",
            loanedFrom = "Chelsea"
        )
        val ownerPlayer = sourcePlayer(
            clubId = 2L,
            clubName = "Chelsea",
            sourceId = 9002L,
            fullName = "FC26 Owner Control"
        )
        val dataset = Fc26Dataset(
            manifest = manifest(playerCount = 2, clubCount = 2, loanedPlayerCount = 1),
            players = listOf(loaned, ownerPlayer)
        )

        val report = EuropeanNewSaveSeedCoordinator.prepareForFc26(
            repositoryKey = repositoryKey,
            teams = listOf(borrower, owner),
            dataset = dataset
        )
        assertEquals(1, report.resolvedLoans)
        assertEquals(0, report.rejectedLoans)

        val seededTeams = EuropeanNewSaveSeedCoordinator.teamsForTesting(
            repositoryKey,
            listOf(borrower, owner)
        )
        assertEquals(listOf(borrower, owner), seededTeams)

        val first = EuropeanNewSaveSeedCoordinator.consumePlayersForKey(repositoryKey, emptyList())
        assertTrue(first.overridden)
        assertEquals(2, first.players.size)
        assertEquals(1, first.loans.size)
        val loan = first.loans.single()
        val player = first.players.single { it.id == loaned.stableId }
        assertEquals(loaned.stableId, loan.playerId)
        assertEquals(owner.id, loan.ownerTeamId)
        assertEquals(borrower.id, loan.borrowerTeamId)
        assertEquals(borrower.id, player.teamId)
        assertEquals(owner.id, player.originalTeamId)
        assertTrue(player.isOnLoan)
        assertTrue(Fc26LoanPolicy.isUnknownEndSnapshotLoan(loan))

        val second = EuropeanNewSaveSeedCoordinator.consumePlayersForKey(repositoryKey, emptyList())
        assertFalse(second.overridden)
        assertTrue(second.players.isEmpty())
        assertTrue(second.loans.isEmpty())
        assertNull(EuropeanNewSaveSeedCoordinator.consumePlayersForKey(repositoryKey, emptyList()).players.firstOrNull())
    }

    private fun sourcePlayer(
        clubId: Long?,
        clubName: String?,
        sourceId: Long,
        fullName: String,
        loanedFrom: String? = null
    ) = Fc26NormalizedPlayer(
        sourcePlayerId = sourceId,
        shortName = fullName,
        fullName = fullName,
        sourceAge = 25,
        birthDateIso = "2000-01-01",
        heightCm = 180,
        weightKg = 75,
        nationality = "England",
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
