package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.CoachOffer
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class Phase102ArchitectureBoundaryTest {
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
    fun contractLifecycleReadsFreshStateInsideItsTransaction() = runTest {
        repository.saveGameSave(GameSave(id = 1, playerTeamId = 1L, bankBalance = 5_000_000L))
        repository.saveTeams(listOf(Team(id = 1L, name = "Owner", city = "A", state = "AA", division = 1)))
        repository.savePlayers(
            listOf(
                Player(
                    id = 101L,
                    teamId = 1L,
                    name = "Renew Me",
                    age = 24,
                    position = "MEI",
                    force = 72,
                    salary = 10_000L,
                    contractDurationWeeks = 40,
                    moral = 88
                )
            )
        )

        val result = ContractLifecycleUseCase(repository).renewPlayerContract(101L, 52)

        assertTrue(result is ContractLifecycleUseCase.RenewalResult.Success)
        val persisted = requireNotNull(repository.getPlayer(101L))
        assertEquals(92, persisted.contractDurationWeeks)
        assertEquals(11_000L, persisted.salary)
        assertEquals(88, persisted.moral)
        assertEquals(1L, persisted.teamId)
    }

    @Test
    fun contractLifecycleRejectsPlayerThatDoesNotBelongToCurrentClub() = runTest {
        repository.saveGameSave(GameSave(id = 1, playerTeamId = 1L, bankBalance = 5_000_000L))
        repository.saveTeams(
            listOf(
                Team(id = 1L, name = "Owner", city = "A", state = "AA", division = 1),
                Team(id = 2L, name = "Other", city = "B", state = "BB", division = 1)
            )
        )
        repository.savePlayers(
            listOf(
                Player(
                    id = 102L,
                    teamId = 2L,
                    name = "Moved",
                    age = 25,
                    position = "ATA",
                    force = 70,
                    salary = 9_000L,
                    contractDurationWeeks = 30
                )
            )
        )

        val result = ContractLifecycleUseCase(repository).renewPlayerContract(102L, 52)

        assertTrue(result is ContractLifecycleUseCase.RenewalResult.Rejected)
        val persisted = requireNotNull(repository.getPlayer(102L))
        assertEquals(30, persisted.contractDurationWeeks)
        assertEquals(9_000L, persisted.salary)
        assertEquals(2L, persisted.teamId)
    }

    @Test
    fun negotiationDoesNotReportAcceptedWhenTransferWasNotPersisted() = runTest {
        val save = GameSave(id = 1, playerTeamId = 1L, bankBalance = 100_000_000L)
        repository.saveGameSave(save)
        repository.saveTeams(listOf(Team(id = 1L, name = "Buyer", city = "A", state = "AA", division = 1)))
        val missingPlayer = Player(
            id = 999L,
            teamId = 2L,
            name = "Missing",
            age = 25,
            position = "ATA",
            force = 75,
            potential = 80
        )
        val offeredPrice = TransferNegotiationUseCase.calculateDynamicPlayerPrice(missingPlayer)

        val result = TransferNegotiationUseCase(ProcessTransfersUseCase(repository)).submitPurchaseOffer(
            save = save,
            player = missingPlayer,
            offeredPrice = offeredPrice
        )

        assertTrue(result is TransferNegotiationUseCase.NegotiationResult.Declined)
        assertTrue(
            (result as TransferNegotiationUseCase.NegotiationResult.Declined)
                .reason.contains("não encontrado", ignoreCase = true)
        )
    }

    @Test
    fun coachOfferPublishesNewClubOnlyAfterDatabaseCommit() = runTest {
        repository.saveGameSave(GameSave(id = 1, playerTeamId = 1L))
        repository.saveTeams(
            listOf(
                Team(id = 1L, name = "Old", city = "A", state = "AA", division = 1),
                Team(id = 2L, name = "New", city = "B", state = "BB", division = 1)
            )
        )
        val offer = CoachOffer(
            teamId = 2L,
            teamName = "New",
            rating = 70,
            weeklySalary = 50_000L,
            description = "Oferta de trabalho"
        )

        val result = CoachCareerUseCase(repository).acceptOffer(offer)

        assertTrue(result is CoachCareerUseCase.AcceptOfferResult.Success)
        assertEquals(2L, requireNotNull(repository.getGameSave()).playerTeamId)
    }

    @Test
    fun academyManagementOwnsPersistenceWithoutViewModelDependency() = runTest {
        val codec = YouthAcademyUseCase()
        val prospects = listOf(AcademyProspect("Base One", 16, "MEI", 45, 78))
        repository.saveGameSave(
            GameSave(
                id = 1,
                playerTeamId = 1L,
                bankBalance = 2_000_000L,
                academyProspects = codec.serializeProspects(prospects)
            )
        )
        repository.saveTeams(listOf(Team(id = 1L, name = "Academy", city = "A", state = "AA", division = 1)))

        val management = YouthAcademyManagementUseCase(repository, codec)
        val upgrade = management.upgradeAcademyLevel()
        val promote = management.promoteProspect(prospects.single(), "Brasil")

        assertTrue(upgrade is YouthAcademyManagementUseCase.AcademyResult.Success)
        assertTrue(promote is YouthAcademyManagementUseCase.AcademyResult.Success)
        val save = requireNotNull(repository.getGameSave())
        assertEquals(2, save.academyLevel)
        assertEquals(1_000_000L, save.bankBalance)
        assertTrue(codec.parseProspects(save.academyProspects).isEmpty())
        assertTrue(repository.getPlayersByTeam(1L).any { it.name == "Base One" && it.isFromAcademy })
    }
}
