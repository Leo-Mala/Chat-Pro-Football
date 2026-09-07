package com.example.ui.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import com.example.usecase.PlayerEvolutionUseCase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class RosterCollapseAndAvailabilityRegressionTest {
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
    fun tearDown() = db.close()

    @Test
    fun `season simulation contract guard is read only and ignores loaned expiry`() = runTest {
        val user = Team(
            id = 1L,
            name = "Usuário",
            city = "BH",
            state = "MG",
            division = 1,
            isPlayerControlled = true
        )
        val owner = Team(id = 2L, name = "Proprietário", city = "SP", state = "SP", division = 1)
        repository.saveTeams(listOf(user, owner))
        repository.savePlayers(
            listOf(
                Player(id = 101L, teamId = user.id, name = "Expira", age = 25, position = "ZAG", force = 80, contractDurationWeeks = 1),
                Player(id = 102L, teamId = user.id, name = "Seguro", age = 25, position = "MEI", force = 80, contractDurationWeeks = 2),
                Player(id = 103L, teamId = user.id, name = "Emprestado", age = 25, position = "ATA", force = 80, contractDurationWeeks = 1, isOnLoan = true, originalTeamId = owner.id)
            )
        )

        val before = repository.getPlayersByTeam(user.id).associateBy { it.id }
        val expiring = repository.getControlledRosterExpiringContractCount(user.id)

        assertEquals(1, expiring)
        assertTrue(shouldPauseSeasonSimulationForExpiringContracts(expiring))
        assertFalse(shouldPauseSeasonSimulationForExpiringContracts(0))
        assertEquals(before, repository.getPlayersByTeam(user.id).associateBy { it.id })
    }

    @Test
    fun `season simulation pauses instead of playing with five eligible athletes`() {
        val roster = (1L..22L).map { id ->
            Player(
                id = id,
                teamId = 1L,
                name = "P$id",
                age = 24,
                position = if (id == 1L) "GOL" else "MEI",
                force = 70,
                suspensionWeeksRemaining = if (id <= 5L) 0 else 1
            )
        }

        val eligible = controlledRosterEligibleCount(roster)
        assertEquals(5, eligible)
        assertTrue(shouldPauseSeasonSimulationForIncompleteLineup(eligible))
        assertFalse(shouldPauseSeasonSimulationForIncompleteLineup(11))
        assertTrue(seasonSimulationLineupPauseMessage(roster.size, eligible).contains("5 aptos"))
    }

    @Test
    fun `new suspension survives current week tick and clears after served week`() = runTest {
        val team = Team(id = 1L, name = "Time", city = "BH", state = "MG", division = 1)
        repository.saveTeams(listOf(team))
        val save = GameSave(id = 1, playerTeamId = team.id)
        repository.saveGameSave(save)
        val player = Player(
            id = 1L,
            teamId = team.id,
            name = "Suspenso",
            age = 25,
            position = "MEI",
            force = 75,
            suspensionWeeksRemaining = persistedAbsenceCounterForNewMatchStatus(1)
        )
        repository.savePlayers(listOf(player))
        val useCase = PlayerEvolutionUseCase(repository)

        val afterCurrentWeek = useCase.processPostMatchRecovery(save, listOf(player)).single()
        assertEquals(1, afterCurrentWeek.suspensionWeeksRemaining)

        val afterServedWeek = useCase.processPostMatchRecovery(
            save,
            listOf(requireNotNull(repository.getPlayer(player.id)))
        ).single()
        assertEquals(0, afterServedWeek.suspensionWeeksRemaining)
    }

    @Test
    fun `new four week injury remains four weeks after same week recovery tick`() = runTest {
        val team = Team(id = 1L, name = "Time", city = "BH", state = "MG", division = 1)
        repository.saveTeams(listOf(team))
        val save = GameSave(id = 1, playerTeamId = team.id)
        repository.saveGameSave(save)
        val player = Player(
            id = 2L,
            teamId = team.id,
            name = "Lesionado",
            age = 25,
            position = "ZAG",
            force = 75,
            injuryWeeksRemaining = persistedAbsenceCounterForNewMatchStatus(4)
        )
        repository.savePlayers(listOf(player))

        val recovered = PlayerEvolutionUseCase(repository)
            .processPostMatchRecovery(save, listOf(player))
            .single()

        assertEquals(4, recovered.injuryWeeksRemaining)
    }
}
