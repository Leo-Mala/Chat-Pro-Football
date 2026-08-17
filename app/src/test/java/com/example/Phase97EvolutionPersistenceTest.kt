package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GameCalendar
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import com.example.usecase.DatabaseIntegrityUseCase
import com.example.usecase.GenerateCalendarUseCase
import com.example.usecase.PlayerEvolutionUseCase
import com.example.usecase.SeasonTransitionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase97EvolutionPersistenceTest {

    private val context by lazy {
        ApplicationProvider.getApplicationContext<android.content.Context>()
    }
    private val databaseName = "phase97-evolution-persistence.db"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `real evolution history survives reload and season transition`() = runBlocking {
        withContext(Dispatchers.IO) {
            context.deleteDatabase(databaseName)
            val db = AppDatabase.buildDatabaseWithName(context, databaseName)
            val repository = GameRepository(db)
            val teams = (1L..4L).map { id ->
                Team(
                    id = id,
                    name = "Evolução Clube $id",
                    city = "Cidade $id",
                    state = "BR",
                    country = "Brasil",
                    division = 1,
                    rating = 72,
                    isPlayerControlled = id == 1L,
                    trainingCenterLevel = 5
                )
            }
            repository.saveTeams(teams)
            repository.savePlayers(
                teams.map { team ->
                    Player(
                        id = team.id * 1_000L + 1L,
                        teamId = team.id,
                        name = "Evolução ${team.id}",
                        age = 19,
                        position = "ATA",
                        force = 65,
                        potential = 92,
                        minutosJogados = 600,
                        mediaNotas = 8.5,
                        contractDurationWeeks = 300
                    )
                }
            )
            val save = GameSave(
                coachName = "Evolução QA",
                currentSeason = 2026,
                currentWeek = 4,
                playerTeamId = 1L,
                bankBalance = 50_000_000L
            )
            repository.saveGameSave(save)

            PlayerEvolutionUseCase(repository).executeMonthlyEvolution(save, "PHASE97_2026_W4")
            val generatedHistory = repository.getAllHistorico().sortedBy { it.id }
            assertTrue("A evolução real deve gerar histórico persistível", generatedHistory.isNotEmpty())
            assertTrue(generatedHistory.any { it.jogadorId == 1_001L })
            db.close()

            val reopenedDb = AppDatabase.buildDatabaseWithName(context, databaseName)
            val reopenedRepository = GameRepository(reopenedDb)
            assertEquals(generatedHistory, reopenedRepository.getAllHistorico().sortedBy { it.id })

            val finalWeek = requireNotNull(reopenedRepository.getGameSave()).copy(
                currentWeek = GameCalendar.WEEKS_PER_SEASON
            )
            reopenedRepository.saveGameSave(finalWeek)
            val transition = SeasonTransitionUseCase(
                reopenedRepository,
                GenerateCalendarUseCase(reopenedRepository),
                DatabaseIntegrityUseCase(reopenedRepository)
            )
            val advanced = transition.advanceToNextSeason(finalWeek)
            assertEquals(2027, advanced.currentSeason)
            assertEquals(1, advanced.currentWeek)
            assertEquals(20, requireNotNull(reopenedRepository.getPlayer(1_001L)).age)
            assertEquals(generatedHistory, reopenedRepository.getAllHistorico().sortedBy { it.id })
            reopenedDb.close()

            val finalDb = AppDatabase.buildDatabaseWithName(context, databaseName)
            val finalRepository = GameRepository(finalDb)
            assertEquals(generatedHistory, finalRepository.getAllHistorico().sortedBy { it.id })
            assertEquals(2027, requireNotNull(finalRepository.getGameSave()).currentSeason)
            finalDb.close()
        }
    }
}
