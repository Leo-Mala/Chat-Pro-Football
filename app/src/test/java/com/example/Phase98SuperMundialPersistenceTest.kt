package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.SuperMundialEditionPolicy
import com.example.data.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase98SuperMundialPersistenceTest {

    private val context by lazy {
        ApplicationProvider.getApplicationContext<android.content.Context>()
    }
    private val databaseName = "phase98-super-mundial-host.db"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `edition host is identical after physical database close and reopen`() = runBlocking {
        withContext(Dispatchers.IO) {
            context.deleteDatabase(databaseName)
            val db = AppDatabase.buildDatabaseWithName(context, databaseName)
            val repository = GameRepository(db)
            repository.saveTeams(hostTeams())
            repository.saveGameSave(
                GameSave(
                    coachName = "QA Sede Mundial",
                    currentSeason = 2029,
                    currentWeek = 1,
                    playerTeamId = 11L
                )
            )

            val beforeClose = requireNotNull(
                SuperMundialEditionPolicy.editionForSeason(2029, repository.getAllTeams())
            )
            db.close()

            val reopenedDb = AppDatabase.buildDatabaseWithName(context, databaseName)
            val reopenedRepository = GameRepository(reopenedDb)
            val afterReload = requireNotNull(
                SuperMundialEditionPolicy.editionForSeason(2029, reopenedRepository.getAllTeams())
            )

            assertEquals(beforeClose, afterReload)
            assertEquals(2029, requireNotNull(reopenedRepository.getGameSave()).currentSeason)
            assertEquals(beforeClose.hostCountry, afterReload.hostCountry)
            assertEquals(beforeClose.hostTeamId, afterReload.hostTeamId)
            reopenedDb.close()
        }
    }

    private fun hostTeams(): List<Team> = listOf(
        Team(11L, "Brasil Host", "Belo Horizonte", "MG", "Brasil", 1, rating = 85),
        Team(21L, "Argentina Host", "Buenos Aires", "AR", "Argentina", 1, rating = 84),
        Team(31L, "Espanha Host", "Madrid", "ES", "Espanha", 1, rating = 90),
        Team(41L, "Inglaterra Host", "London", "EN", "Inglaterra", 1, rating = 91)
    )
}
