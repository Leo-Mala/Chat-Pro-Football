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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class PlayerEvolutionUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var useCase: PlayerEvolutionUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = GameRepository(db)
        useCase = PlayerEvolutionUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun processPostMatchRecovery_restores_energy_and_decreases_injury() = runTest {
        seedTeam()
        val save = GameSave(id = 1, playerTeamId = 1L)
        val player = Player(
            id = 1L,
            teamId = 1L,
            name = "Atleta 1",
            age = 22,
            position = "MEI",
            force = 70,
            energy = 50,
            injuryWeeksRemaining = 2,
            suspensionWeeksRemaining = 1
        )
        repository.savePlayers(listOf(player))

        val updated = useCase.processPostMatchRecovery(save, listOf(player), trainingCenterLevel = 2)

        assertEquals(1, updated.size)
        assertEquals(71, updated[0].energy)
        assertEquals(1, updated[0].injuryWeeksRemaining)
        assertEquals(0, updated[0].suspensionWeeksRemaining)
    }

    @Test
    fun promoteYouthPlayer_fails_when_roster_is_full() = runTest {
        seedTeam()
        val save = GameSave(id = 1, playerTeamId = 1L)

        val (success, message) = useCase.promoteYouthPlayer(save, "Novo Craque", "ATA", currentRosterSize = 35)

        assertEquals(false, success)
        assertTrue(message.contains("limite"))
    }

    @Test
    fun promoteYouthPlayer_successfully_adds_youth_player() = runTest {
        seedTeam()
        val save = GameSave(id = 1, playerTeamId = 1L)

        val (success, message) = useCase.promoteYouthPlayer(save, "Jovem Promessa", "MEI", currentRosterSize = 22)

        assertEquals(true, success)
        assertTrue(message.contains("promovido com sucesso"))
    }

    private suspend fun seedTeam() {
        repository.saveTeams(
            listOf(
                Team(
                    id = 1L,
                    name = "Evolução QA",
                    city = "Belo Horizonte",
                    state = "MG",
                    country = "Brasil",
                    division = 1
                )
            )
        )
    }
}
