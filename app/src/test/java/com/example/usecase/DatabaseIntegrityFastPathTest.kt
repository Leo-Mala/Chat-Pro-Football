package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.DefaultData
import com.example.data.GameRepository
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
class DatabaseIntegrityFastPathTest {

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
    fun validGeneratedRostersNeedNoRepairAndPreserveEveryPlayer() = runTest {
        val teams = (1L..120L).map { id ->
            Team(
                id = id,
                name = "Clube $id",
                city = "Cidade $id",
                state = "BR",
                country = "Brasil",
                division = 1,
                rating = 70 + (id % 15).toInt()
            )
        }
        repository.saveTeams(teams)

        val players = teams.flatMap { team ->
            DefaultData.generateRosterForTeam(
                teamId = team.id,
                teamRating = team.rating,
                teamName = team.name,
                country = team.country
            )
        }
        repository.savePlayers(players)

        val beforeCount = repository.getAllPlayers().size
        val report = DatabaseIntegrityUseCase(repository).repairDatabase()
        val afterPlayers = repository.getAllPlayers()

        assertTrue("O fixture precisa representar um banco não trivial", beforeCount > 1_900)
        assertEquals(teams.size, report.totalTeamsChecked)
        assertEquals(0, report.teamsRepaired)
        assertEquals(0, report.playersAddedCount)
        assertEquals(0, report.orphanPlayersFixedCount)
        assertTrue(report.issuesFound.isEmpty())
        assertEquals(beforeCount, afterPlayers.size)
        assertEquals(afterPlayers.size, afterPlayers.map { it.id }.toSet().size)
    }
}
