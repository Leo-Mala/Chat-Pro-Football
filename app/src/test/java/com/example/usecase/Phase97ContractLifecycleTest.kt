package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class Phase97ContractLifecycleTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var transfers: ProcessTransfersUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        transfers = ProcessTransfersUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `weekly contract countdown ends in free agency without duplicating player`() = runTest {
        repository.saveTeams(
            listOf(
                Team(id = 1L, name = "Contrato A", city = "A", state = "BR", division = 1),
                Team(id = 2L, name = "Contrato B", city = "B", state = "BR", division = 1)
            )
        )
        repository.saveGameSave(
            GameSave(
                currentSeason = 2026,
                currentWeek = 1,
                playerTeamId = 1L
            )
        )
        repository.savePlayers(
            listOf(
                Player(
                    id = 11L,
                    teamId = 1L,
                    name = "Contrato Curto",
                    age = 25,
                    position = "MEI",
                    force = 70,
                    salary = 50_000L,
                    contractDurationWeeks = 2
                ),
                Player(
                    id = 22L,
                    teamId = 2L,
                    name = "Expira Agora",
                    age = 26,
                    position = "ATA",
                    force = 72,
                    salary = 60_000L,
                    contractDurationWeeks = 1
                )
            )
        )

        transfers.processWeeklyContractsAndLoans()

        val afterWeekOneUser = requireNotNull(repository.getPlayer(11L))
        val afterWeekOneCpu = requireNotNull(repository.getPlayer(22L))
        assertEquals(1, afterWeekOneUser.contractDurationWeeks)
        assertEquals(1L, afterWeekOneUser.teamId)
        assertEquals(0, afterWeekOneCpu.contractDurationWeeks)
        assertNull(afterWeekOneCpu.teamId)
        assertEquals(0L, afterWeekOneCpu.salary)

        repository.saveGameSave(requireNotNull(repository.getGameSave()).copy(currentWeek = 2))
        transfers.processWeeklyContractsAndLoans()

        val expiredUser = requireNotNull(repository.getPlayer(11L))
        assertEquals(0, expiredUser.contractDurationWeeks)
        assertNull(expiredUser.teamId)
        assertEquals(0L, expiredUser.salary)

        val allPlayers = repository.getAllPlayers()
        assertEquals(2, allPlayers.size)
        assertEquals(2, allPlayers.map { it.id }.toSet().size)
    }
}
