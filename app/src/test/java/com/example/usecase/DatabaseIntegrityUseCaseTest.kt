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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class DatabaseIntegrityUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var useCase: DatabaseIntegrityUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = GameRepository(db)
        useCase = DatabaseIntegrityUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun validateAndRepairDatabase_fixes_teams_with_incomplete_rosters() = runTest {
        val team = Team(id = 1L, name = "Time Incompleto", city = "Sp", state = "SP", division = 1)
        repository.saveTeams(listOf(team))

        // Create only 1 player
        val p1 = Player(id = 1L, teamId = 1L, name = "P1", age = 20, position = "GOL", force = 60)
        repository.savePlayers(listOf(p1))

        val result = useCase.validateAndRepairDatabase()

        val playersAfter = repository.getPlayersByTeam(1L)
        assertTrue(playersAfter.size >= 16)
    }
}
