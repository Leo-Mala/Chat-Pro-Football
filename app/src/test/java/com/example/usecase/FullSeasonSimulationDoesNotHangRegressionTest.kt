package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class FullSeasonSimulationDoesNotHangRegressionTest {
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
    fun `week four monthly boundary completes inside a bounded execution`() = runBlocking {
        val team = Team(id = 1L, name = "Cruzeiro", city = "BH", state = "MG", division = 1, rating = 75)
        repository.saveTeams(listOf(team))
        val save = GameSave(currentSeason = 2026, currentWeek = 4, playerTeamId = team.id)
        repository.saveGameSave(save)
        val playerCount = 8_500
        repository.savePlayers(
            List(playerCount) { index ->
                Player(
                    id = index.toLong() + 1,
                    teamId = team.id,
                    name = "Jogador %05d".format(index),
                    age = 25,
                    position = "ATA",
                    force = 60,
                    potential = 80,
                    finishing = 60,
                    passing = 60,
                    pace = 60,
                    strength = 60,
                    vision = 60,
                    defense = 60
                )
            }
        )

        val plan = withTimeout(20_000) {
            PlayerEvolutionUseCase(repository).prepareMonthlyEvolution(save, "S2026_W4")
        }
        assertEquals(playerCount, plan.expectedPlayerCount)
        assertEquals(playerCount, plan.expectedInputs.size)
    }
}
