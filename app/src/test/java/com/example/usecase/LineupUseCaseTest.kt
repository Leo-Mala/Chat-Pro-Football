package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.Team
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
class LineupUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var useCase: LineupUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        useCase = LineupUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun setPlayerStarter_replacesLowestFieldAndKeepsElevenStarters() = runTest {
        seedTeam(10L)
        val starters = mutableListOf(
            player(1L, 10L, "Goleiro", "GOL", 80, true)
        )
        starters += (2L..11L).map { id ->
            player(id, 10L, "Titular $id", "MEI", (40 + id).toInt(), true)
        }
        val bench = player(12L, 10L, "Reserva Forte", "ATA", 90, false)
        repository.insertPlayersIfNotExists(starters + bench)

        val result = useCase.setPlayerStarter(bench.id, true)

        assertTrue(result is LineupUseCase.Result.Success)
        val roster = repository.getPlayersByTeam(10L)
        assertEquals(11, roster.count { it.isStarter })
        assertEquals(1, roster.count { it.isStarter && it.position == "GOL" })
        assertTrue(roster.single { it.id == bench.id }.isStarter)
        assertFalse(roster.single { it.id == 2L }.isStarter)
    }

    @Test
    fun setPlayerStarter_rejectsRemovingOnlyStartingGoalkeeperWithoutMutation() = runTest {
        seedTeam(10L)
        val goalkeeper = player(1L, 10L, "Goleiro", "GOL", 80, true)
        repository.insertPlayersIfNotExists(listOf(goalkeeper))

        val result = useCase.setPlayerStarter(goalkeeper.id, false)

        assertTrue(result is LineupUseCase.Result.Rejected)
        assertTrue(repository.getPlayer(goalkeeper.id)!!.isStarter)
    }

    @Test
    fun swapPlayers_rejectsGoalkeeperForFieldWhenNoOtherGoalkeeperExists() = runTest {
        seedTeam(10L)
        val goalkeeper = player(1L, 10L, "Goleiro", "GOL", 80, true)
        val fieldBench = player(2L, 10L, "Reserva", "MEI", 70, false)
        repository.insertPlayersIfNotExists(listOf(goalkeeper, fieldBench))

        val result = useCase.swapPlayers(goalkeeper.id, fieldBench.id)

        assertTrue(result is LineupUseCase.Result.Rejected)
        assertTrue(repository.getPlayer(goalkeeper.id)!!.isStarter)
        assertFalse(repository.getPlayer(fieldBench.id)!!.isStarter)
    }

    @Test
    fun swapPlayers_goalkeeperChangePreservesLineupSizeAndSingleGoalkeeper() = runTest {
        seedTeam(10L)
        val starters = mutableListOf(
            player(1L, 10L, "Goleiro Atual", "GOL", 70, true),
            player(2L, 10L, "Linha Selecionado", "MEI", 75, true)
        )
        starters += (3L..11L).map { id ->
            player(id, 10L, "Linha $id", "MEI", 60, true)
        }
        val goalkeeperBench = player(12L, 10L, "Novo Goleiro", "GOL", 85, false)
        repository.insertPlayersIfNotExists(starters + goalkeeperBench)

        val result = useCase.swapPlayers(2L, goalkeeperBench.id)

        assertTrue(result is LineupUseCase.Result.Success)
        val roster = repository.getPlayersByTeam(10L)
        assertEquals(11, roster.count { it.isStarter })
        assertEquals(1, roster.count { it.isStarter && it.position == "GOL" })
        assertTrue(roster.single { it.id == 2L }.isStarter)
        assertFalse(roster.single { it.id == 1L }.isStarter)
        assertTrue(roster.single { it.id == goalkeeperBench.id }.isStarter)
    }

    @Test
    fun swapPlayers_rejectsCrossTeamMutation() = runTest {
        seedTeam(10L)
        seedTeam(20L)
        val starter = player(1L, 10L, "Titular", "MEI", 80, true)
        val foreignBench = player(2L, 20L, "Outro Clube", "MEI", 80, false)
        repository.insertPlayersIfNotExists(listOf(starter, foreignBench))

        val result = useCase.swapPlayers(starter.id, foreignBench.id)

        assertTrue(result is LineupUseCase.Result.Rejected)
        assertTrue(repository.getPlayer(starter.id)!!.isStarter)
        assertFalse(repository.getPlayer(foreignBench.id)!!.isStarter)
    }

    private suspend fun seedTeam(id: Long) {
        repository.saveTeams(
            listOf(
                Team(
                    id = id,
                    name = "Time $id",
                    city = "Cidade",
                    state = "MG",
                    country = "Brasil",
                    division = 1
                )
            )
        )
    }

    private fun player(
        id: Long,
        teamId: Long,
        name: String,
        position: String,
        force: Int,
        isStarter: Boolean
    ) = Player(
        id = id,
        teamId = teamId,
        name = name,
        age = 24,
        position = position,
        force = force,
        isStarter = isStarter
    )
}
