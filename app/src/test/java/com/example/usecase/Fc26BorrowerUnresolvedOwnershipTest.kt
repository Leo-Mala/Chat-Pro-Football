package com.example.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Atributos
import com.example.data.Fc26LoanResolution
import com.example.data.Fc26LoanResolutionStatus
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import com.example.data.isFc26LoanOwnershipQuarantined
import com.example.data.isTransferMarketCandidateFor
import com.example.data.markFc26LoanResolution
import com.example.data.markFc26UnassignedSourceClub
import com.example.data.sourceMetadataOrNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Fc26BorrowerUnresolvedOwnershipTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `rejected borrower signals cannot become free agents or market ownership`() = runTest {
        repository.saveTeams(
            listOf(
                Team(USER_TEAM_ID, "User FC", "A", "SP", "Brasil", 1, rating = 75),
                Team(OTHER_TEAM_ID, "Other FC", "B", "RJ", "Brasil", 1, rating = 75)
            )
        )
        val save = GameSave(playerTeamId = USER_TEAM_ID, bankBalance = 50_000_000L)
        repository.saveGameSave(save)
        val transfers = ProcessTransfersUseCase(repository)
        val statuses = listOf(
            Fc26LoanResolutionStatus.BORROWER_NOT_FOUND,
            Fc26LoanResolutionStatus.AMBIGUOUS_BORROWER,
            Fc26LoanResolutionStatus.UNSUPPORTED_METADATA
        )

        statuses.forEachIndexed { index, status ->
            val id = 700L + index
            val source = sourcePlayer(
                id = id,
                includeBorrowerMetadata = status != Fc26LoanResolutionStatus.UNSUPPORTED_METADATA
            ).markFc26UnassignedSourceClub()
            val sourceMetadata = requireNotNull(source.sourceMetadataOrNull())
            val quarantined = quarantine(source, status)
            repository.savePlayers(listOf(quarantined))

            assertNull(quarantined.teamId)
            assertNull(quarantined.originalTeamId)
            assertTrue(quarantined.isOnLoan)
            assertTrue(quarantined.isFc26LoanOwnershipQuarantined())
            assertFalse(quarantined.isTransferMarketCandidateFor(USER_TEAM_ID))
            assertFalse(quarantined.isTransferMarketCandidateFor(OTHER_TEAM_ID))
            assertNull(repository.getActiveLoanForPlayer(id))

            val metadata = requireNotNull(quarantined.sourceMetadataOrNull())
            assertEquals("LOAN_OWNERSHIP_UNRESOLVED", metadata.assignmentStatus)
            assertEquals(status.name, metadata.loanResolutionStatus)
            assertEquals("NOT_AVAILABLE", metadata.loanTemporalCoverage)
            assertEquals(sourceMetadata.sourceContractDurationWeeks, metadata.sourceContractDurationWeeks)
            assertEquals(sourceMetadata.sourceSalary, metadata.sourceSalary)
            assertTrue((metadata.sourceContractDurationWeeks ?: 0) > 0)
            assertTrue((metadata.sourceSalary ?: 0L) > 0L)

            val purchase = transfers.executePurchase(
                save = save,
                player = quarantined,
                price = 1_000_000L,
                currentRoster = emptyList()
            )
            assertTrue("$status must not be purchasable as a free agent", purchase is ProcessTransfersUseCase.TransferResult.Error)
            assertEquals(quarantined, repository.getPlayer(id))
            assertEquals(50_000_000L, repository.getGameSave()?.bankBalance)
        }
    }

    @Test
    fun `borrower unresolved quarantine survives Room close and reopen`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "phase_10_4_borrower_quarantine_reopen.db"
        context.deleteDatabase(databaseName)
        var fileDb: AppDatabase? = null
        try {
            fileDb = AppDatabase.buildDatabaseWithName(context, databaseName)
            var fileRepository = GameRepository(fileDb)
            val quarantined = quarantine(
                sourcePlayer(900L).markFc26UnassignedSourceClub(),
                Fc26LoanResolutionStatus.BORROWER_NOT_FOUND
            )
            fileRepository.savePlayers(listOf(quarantined))
            fileDb.close()
            fileDb = null

            fileDb = AppDatabase.buildDatabaseWithName(context, databaseName)
            fileRepository = GameRepository(fileDb)
            val reopened = requireNotNull(fileRepository.getPlayer(quarantined.id))

            assertEquals(quarantined.id, reopened.id)
            assertEquals(quarantined.force, reopened.force)
            assertEquals(quarantined.potential, reopened.potential)
            assertEquals(quarantined.atributos, reopened.atributos)
            assertNull(reopened.teamId)
            assertNull(reopened.originalTeamId)
            assertTrue(reopened.isOnLoan)
            assertTrue(reopened.isFc26LoanOwnershipQuarantined())
            assertFalse(reopened.isTransferMarketCandidateFor(USER_TEAM_ID))
            assertNull(fileRepository.getActiveLoanForPlayer(reopened.id))
            assertEquals("BORROWER_NOT_FOUND", reopened.sourceMetadataOrNull()?.loanResolutionStatus)
        } finally {
            fileDb?.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun quarantine(source: Player, status: Fc26LoanResolutionStatus): Player =
        source.markFc26LoanResolution(
            Fc26LoanResolution(
                sourcePlayerId = source.id,
                playerId = source.id,
                playerName = source.name,
                ownerSourceName = "Factual Owner",
                borrowerSourceTeamId = if (status == Fc26LoanResolutionStatus.UNSUPPORTED_METADATA) null else 999L,
                borrowerSourceName = if (status == Fc26LoanResolutionStatus.UNSUPPORTED_METADATA) null else "Unresolved Borrower",
                ownerTeamId = null,
                borrowerTeamId = null,
                status = status,
                reason = "borrower cannot be materialized safely"
            )
        )

    private fun sourcePlayer(id: Long, includeBorrowerMetadata: Boolean = true) = Player(
        id = id,
        teamId = null,
        name = "Borrower unresolved $id",
        age = 23,
        position = "ATA",
        force = 81,
        potential = 88,
        salary = 90_000L,
        contractDurationWeeks = 52,
        atributos = Atributos(finalizacao = 85, velocidade = 86),
        atributosJson = if (includeBorrowerMetadata) {
            """{"import":{"source":"FC26","sourcePlayerId":$id,"datasetVersion":"test","birthDateIso":"2003-01-01","primaryPosition":"ST","alternativePositions":[],"sourceClubTeamId":999,"sourceClubName":"Unresolved Borrower","leagueId":1,"leagueName":"Test League","clubLoanedFrom":"Factual Owner"}}"""
        } else {
            """{"import":{"source":"FC26","sourcePlayerId":$id,"datasetVersion":"test","birthDateIso":"2003-01-01","primaryPosition":"ST","alternativePositions":[],"sourceClubTeamId":null,"sourceClubName":null,"leagueId":1,"leagueName":"Test League","clubLoanedFrom":"Factual Owner"}}"""
        }
    )

    companion object {
        private const val USER_TEAM_ID = 10L
        private const val OTHER_TEAM_ID = 20L
    }
}
