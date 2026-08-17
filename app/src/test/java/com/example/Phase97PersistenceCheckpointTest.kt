package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DefaultData
import com.example.data.Fixture
import com.example.data.FixtureScheduleValidator
import com.example.data.GameCalendar
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.HistoricalRecord
import com.example.data.MatchSlot
import com.example.data.Player
import com.example.data.Team
import com.example.data.TransactionRecord
import com.example.usecase.DatabaseIntegrityUseCase
import com.example.usecase.GenerateCalendarUseCase
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
class Phase97PersistenceCheckpointTest {

    private val context by lazy {
        ApplicationProvider.getApplicationContext<android.content.Context>()
    }
    private val databaseName = "phase97-career-checkpoints.db"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `career state survives weeks 10 25 41 48 and post transition reopen`() = runBlocking {
        withContext(Dispatchers.IO) {
            context.deleteDatabase(databaseName)
            seedCareer()

            val checkpoints = listOf(10, 25, 41, 48)
            checkpoints.forEach { week ->
                val db = AppDatabase.buildDatabaseWithName(context, databaseName)
                val repository = GameRepository(db)

                val current = requireNotNull(repository.getGameSave())
                repository.saveGameSave(current.copy(currentWeek = week))

                val dueFixtures = repository.getFixturesForWeek(2026, week)
                if (dueFixtures.isNotEmpty()) {
                    repository.updateFixtures(
                        dueFixtures.mapIndexed { index, fixture ->
                            fixture.copy(
                                homeScore = 2 + (index % 2),
                                awayScore = index % 2,
                                isPlayed = true
                            )
                        }
                    )
                }

                val trackedPlayer = requireNotNull(repository.getPlayer(1_001L))
                repository.updatePlayer(
                    trackedPlayer.copy(
                        contractDurationWeeks = 300 - week,
                        moral = 70 + (week % 20)
                    )
                )
                repository.saveTransaction(
                    TransactionRecord(
                        week = week,
                        season = 2026,
                        type = "QA_CHECKPOINT",
                        description = "Persistência Fase 9.7 W$week",
                        amount = week.toLong() * 1_000L,
                        isIncome = week % 2 == 0,
                        timestamp = week.toLong()
                    )
                )

                FixtureScheduleValidator.requireValid(repository.getFixturesForSeason(2026))
                val expected = snapshot(repository)
                db.close()

                val reopenedDb = AppDatabase.buildDatabaseWithName(context, databaseName)
                val reopenedRepository = GameRepository(reopenedDb)
                val actual = snapshot(reopenedRepository)

                assertEquals("Snapshot completo deve sobreviver ao reload da semana $week", expected, actual)
                assertEquals(week, requireNotNull(actual.save).currentWeek)
                reopenedDb.close()
            }

            val transitionDb = AppDatabase.buildDatabaseWithName(context, databaseName)
            val transitionRepository = GameRepository(transitionDb)
            val transition = SeasonTransitionUseCase(
                transitionRepository,
                GenerateCalendarUseCase(transitionRepository),
                DatabaseIntegrityUseCase(transitionRepository)
            )
            val finalWeek = requireNotNull(transitionRepository.getGameSave()).copy(
                currentWeek = GameCalendar.WEEKS_PER_SEASON
            )
            transitionRepository.saveGameSave(finalWeek)
            val advanced = transition.advanceToNextSeason(finalWeek)
            assertEquals(2027, advanced.currentSeason)
            assertEquals(1, advanced.currentWeek)
            transitionDb.close()

            val finalDb = AppDatabase.buildDatabaseWithName(context, databaseName)
            val finalRepository = GameRepository(finalDb)
            val reloaded = requireNotNull(finalRepository.getGameSave())
            assertEquals(2027, reloaded.currentSeason)
            assertEquals(1, reloaded.currentWeek)
            assertEquals(setOf(1L, 2L, 3L, 4L), finalRepository.getAllTeams().map { it.id }.toSet())
            assertTrue(finalRepository.getAllPlayers().all { it.teamId in setOf(0L, 1L, 2L, 3L, 4L) })
            assertTrue(finalRepository.getAllHistoricalRecords().any { it.competitionName == "QA CUP" })
            FixtureScheduleValidator.requireValid(finalRepository.getFixturesForSeason(2027))
            finalDb.close()
        }
    }

    private suspend fun seedCareer() {
        val db = AppDatabase.buildDatabaseWithName(context, databaseName)
        val repository = GameRepository(db)
        val teams = (1L..4L).map { id ->
            Team(
                id = id,
                name = "Persistência Clube $id",
                city = "Cidade $id",
                state = "BR",
                country = "Brasil",
                division = 1,
                rating = 70 + id.toInt(),
                isPlayerControlled = id == 1L
            )
        }
        repository.saveTeams(teams)
        teams.forEach { team ->
            val roster = DefaultData.generateRosterForTeam(
                team.id,
                team.rating,
                team.name,
                team.country
            ).map { player ->
                player.copy(
                    age = 21 + (player.id % 7).toInt(),
                    contractDurationWeeks = 320
                )
            }
            repository.savePlayers(roster)
        }
        repository.saveGameSave(
            GameSave(
                coachName = "Persistência 9.7",
                currentWeek = 1,
                currentSeason = 2026,
                playerTeamId = 1L,
                bankBalance = 123_456_789L,
                stadiumCapacity = 40_000,
                ticketPrice = 35.0
            )
        )
        repository.saveFixtures(
            listOf(
                Fixture(0L, 2026, 10, 1L, 2L, competitionType = "COPA", matchSlot = MatchSlot.MIDWEEK),
                Fixture(0L, 2026, 10, 1L, 3L, competitionType = "SERIE_A", matchSlot = MatchSlot.WEEKEND),
                Fixture(0L, 2026, 25, 2L, 4L, competitionType = "SERIE_A", matchSlot = MatchSlot.WEEKEND),
                Fixture(0L, 2026, 41, 3L, 4L, competitionType = "CONTINENTAL_T1", matchSlot = MatchSlot.MIDWEEK),
                Fixture(0L, 2026, 48, 1L, 4L, competitionType = "WORLD_CUP", matchSlot = MatchSlot.MIDWEEK)
            )
        )
        repository.saveRecord(
            HistoricalRecord(
                season = 2025,
                competitionName = "QA CUP",
                championTeamName = "Persistência Clube 1",
                runnerUpTeamName = "Persistência Clube 2",
                topScorerName = "QA",
                topScorerGoals = 5,
                topScorerTeam = "Persistência Clube 1"
            )
        )
        db.close()
    }

    private suspend fun snapshot(repository: GameRepository): CareerSnapshot = CareerSnapshot(
        save = repository.getGameSave(),
        teams = repository.getAllTeams().sortedBy { it.id },
        players = repository.getAllPlayers().sortedBy { it.id },
        fixtures = repository.getFixturesForSeason(2026).sortedBy { it.id },
        records = repository.getAllHistoricalRecords().sortedBy { it.id },
        transactions = repository.getAllTransactions().sortedBy { it.id }
    )

    private data class CareerSnapshot(
        val save: GameSave?,
        val teams: List<Team>,
        val players: List<Player>,
        val fixtures: List<Fixture>,
        val records: List<HistoricalRecord>,
        val transactions: List<TransactionRecord>
    )
}
