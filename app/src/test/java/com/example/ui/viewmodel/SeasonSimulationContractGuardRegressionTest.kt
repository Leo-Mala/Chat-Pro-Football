package com.example.ui.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeasonSimulationContractGuardRegressionTest {
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
    fun `season simulation pauses before controlled roster contract expiry`() = runBlocking {
        val user = Team(id = 1L, name = "Usuário", city = "BH", state = "MG", division = 1, isPlayerControlled = true)
        val other = Team(id = 2L, name = "Outro", city = "SP", state = "SP", division = 1)
        repository.saveTeams(listOf(user, other))
        repository.savePlayers(
            listOf(
                Player(id = 101L, teamId = user.id, name = "Expira", age = 25, position = "ZAG", force = 80, contractDurationWeeks = 1),
                Player(id = 102L, teamId = user.id, name = "Seguro", age = 25, position = "MEI", force = 80, contractDurationWeeks = 2),
                Player(id = 103L, teamId = user.id, name = "Emprestado", age = 25, position = "ATA", force = 80, contractDurationWeeks = 1, isOnLoan = true, originalTeamId = other.id),
                Player(id = 201L, teamId = other.id, name = "CPU Expira", age = 25, position = "ZAG", force = 80, contractDurationWeeks = 1)
            )
        )

        val expiring = repository.getControlledRosterExpiringContractCount(user.id)
        assertEquals(1, expiring)
        assertTrue(shouldPauseSeasonSimulationForExpiringContracts(expiring))
        assertFalse(shouldPauseSeasonSimulationForExpiringContracts(0))

        val before = repository.getPlayersByTeam(user.id).map { it.id }.toSet()
        assertEquals(setOf(101L, 102L, 103L), before)
        // Detection is read-only: no player is released merely by checking the guard.
        assertEquals(before, repository.getPlayersByTeam(user.id).map { it.id }.toSet())
    }
}
