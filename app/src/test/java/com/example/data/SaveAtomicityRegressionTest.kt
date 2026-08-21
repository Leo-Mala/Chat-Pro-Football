package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.usecase.ScoutingUseCase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaveAtomicityRegressionTest {

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
    fun globalStandingsReplacementRollsBackAcrossChunkFailure() = runTest {
        val original = listOf(
            standing(teamId = 9001L, position = 1, points = 90),
            standing(teamId = 9002L, position = 2, points = 80)
        )
        repository.saveGlobalStandingsForSeason(2026, original)

        db.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_global_standing_insert
            BEFORE INSERT ON global_league_standings
            WHEN NEW.teamId = 101
            BEGIN
                SELECT RAISE(ABORT, 'forced global standing failure');
            END
            """.trimIndent()
        )

        val replacement = (1L..101L).mapIndexed { index, teamId ->
            standing(teamId = teamId, position = index + 1, points = 200 - index)
        }

        try {
            repository.saveGlobalStandingsForSeason(2026, replacement)
            fail("Falha forçada no segundo chunk deveria abortar a substituição.")
        } catch (_: Exception) {
            // esperado: a transação inteira deve voltar ao snapshot anterior
        }

        assertEquals(original, repository.getGlobalStandingsForSeason(2026))
    }

    @Test
    fun seasonRestartRollsBackSaveFixturesPlayersAndOffersTogether() = runTest {
        seedTeams(1L, 2L, 3L)
        val originalSave = GameSave(
            currentWeek = 22,
            currentSeason = 2026,
            playerTeamId = 1L,
            bankBalance = 12_345_678L,
            isGameOver = true
        )
        repository.saveGameSave(originalSave)

        val originalPlayer = Player(
            id = 10L,
            teamId = 1L,
            name = "Jogador Teste",
            age = 27,
            position = "ATA",
            force = 78,
            energy = 41,
            moral = 33,
            injuryWeeksRemaining = 2,
            suspensionWeeksRemaining = 1,
            yellowCardsAccumulated = 4,
            careerGoals = 17
        )
        repository.savePlayers(listOf(originalPlayer))

        val originalFixture = Fixture(
            id = 20L,
            season = 2026,
            week = 22,
            homeTeamId = 1L,
            awayTeamId = 2L,
            homeScore = 1,
            awayScore = 0,
            competitionType = "SERIE_A",
            isPlayed = true
        )
        repository.saveFixtures(listOf(originalFixture))

        val originalOffer = CoachOffer(
            id = 30L,
            teamId = 3L,
            teamName = "Time 3",
            rating = 70,
            weeklySalary = 100_000L,
            description = "Oferta teste"
        )
        repository.saveOffers(listOf(originalOffer))

        db.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_restart_fixture_insert
            BEFORE INSERT ON fixtures
            WHEN NEW.id = 99
            BEGIN
                SELECT RAISE(ABORT, 'forced restart fixture failure');
            END
            """.trimIndent()
        )

        val replacementFixture = Fixture(
            id = 99L,
            season = 2026,
            week = 1,
            homeTeamId = 1L,
            awayTeamId = 3L,
            competitionType = "SERIE_A"
        )

        try {
            repository.restartSeasonStateAtomically(
                expectedSeason = 2026,
                expectedPlayerTeamId = 1L,
                replacementFixtures = listOf(replacementFixture)
            )
            fail("Falha forçada no fixture deveria abortar todo o reinício.")
        } catch (_: Exception) {
            // esperado
        }

        assertEquals(originalSave, repository.getGameSave())
        assertEquals(listOf(originalFixture), repository.getAllFixtures())
        assertEquals(originalPlayer, repository.getPlayer(originalPlayer.id))
        assertEquals(listOf(originalOffer), repository.getAllOffers())
    }

    @Test
    fun seasonRestartCommitsCompleteResetWithoutChangingFinancialState() = runTest {
        seedTeams(1L, 2L, 3L)
        val originalSave = GameSave(
            currentWeek = 22,
            currentSeason = 2026,
            playerTeamId = 1L,
            bankBalance = 12_345_678L,
            loanAmount = 2_500_000L,
            sponsorName = "Patrocinador Teste",
            isGameOver = true
        )
        repository.saveGameSave(originalSave)

        val originalPlayer = Player(
            id = 10L,
            teamId = 1L,
            name = "Jogador Teste",
            age = 27,
            position = "ATA",
            force = 78,
            energy = 41,
            moral = 33,
            injuryWeeksRemaining = 2,
            suspensionWeeksRemaining = 1,
            yellowCardsAccumulated = 4,
            careerGoals = 17,
            salary = 75_000L,
            contractDurationWeeks = 80
        )
        repository.savePlayers(listOf(originalPlayer))

        repository.saveFixtures(
            listOf(
                Fixture(
                    id = 20L,
                    season = 2026,
                    week = 22,
                    homeTeamId = 1L,
                    awayTeamId = 2L,
                    homeScore = 1,
                    awayScore = 0,
                    competitionType = "SERIE_A",
                    isPlayed = true
                )
            )
        )
        repository.saveOffers(
            listOf(
                CoachOffer(
                    id = 30L,
                    teamId = 3L,
                    teamName = "Time 3",
                    rating = 70,
                    weeklySalary = 100_000L,
                    description = "Oferta teste"
                )
            )
        )

        val replacementFixture = Fixture(
            id = 99L,
            season = 2026,
            week = 1,
            homeTeamId = 1L,
            awayTeamId = 3L,
            competitionType = "SERIE_A"
        )

        val restarted = repository.restartSeasonStateAtomically(
            expectedSeason = 2026,
            expectedPlayerTeamId = 1L,
            replacementFixtures = listOf(replacementFixture)
        )

        assertTrue(restarted)
        assertEquals(
            originalSave.copy(currentWeek = 1, isGameOver = false),
            repository.getGameSave()
        )
        assertEquals(listOf(replacementFixture), repository.getAllFixtures())
        assertTrue(repository.getAllOffers().isEmpty())
        assertEquals(
            originalPlayer.copy(
                energy = 100,
                moral = 75,
                injuryWeeksRemaining = 0,
                suspensionWeeksRemaining = 0,
                yellowCardsAccumulated = 0,
                careerGoals = 0
            ),
            repository.getPlayer(originalPlayer.id)
        )
    }

    @Test
    fun seasonRestartRejectsStaleSeasonPlanWithoutMutation() = runTest {
        seedTeams(1L, 2L)
        val currentSave = GameSave(
            currentWeek = 5,
            currentSeason = 2027,
            playerTeamId = 1L,
            bankBalance = 99_000L
        )
        repository.saveGameSave(currentSave)

        val accepted = repository.restartSeasonStateAtomically(
            expectedSeason = 2026,
            expectedPlayerTeamId = 1L,
            replacementFixtures = emptyList()
        )

        assertFalse(accepted)
        assertEquals(currentSave, repository.getGameSave())
    }

    @Test
    fun scoutingPurchaseRollsBackSaveWhenFinancialHistoryInsertFails() = runTest {
        val originalSave = GameSave(
            currentWeek = 7,
            currentSeason = 2026,
            playerTeamId = 1L,
            bankBalance = 1_000_000L,
            globalScoutRevealWeeksRemaining = 0
        )
        repository.saveGameSave(originalSave)

        db.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_scouting_history_insert
            BEFORE INSERT ON transaction_history
            WHEN NEW.type = 'MELHORIA_OLHEIROS'
            BEGIN
                SELECT RAISE(ABORT, 'forced scouting history failure');
            END
            """.trimIndent()
        )

        try {
            ScoutingUseCase(repository).buyGlobalScoutReveal(originalSave, 4)
            fail("Falha forçada no histórico deveria abortar compra de olheiros.")
        } catch (_: Exception) {
            // esperado
        }

        assertEquals(originalSave, repository.getGameSave())
        assertTrue(repository.getAllTransactions().isEmpty())
    }

    @Test
    fun scoutingRejectsNonPositiveDurationWithoutChangingBalance() = runTest {
        val originalSave = GameSave(
            currentWeek = 7,
            currentSeason = 2026,
            playerTeamId = 1L,
            bankBalance = 1_000_000L,
            globalScoutRevealWeeksRemaining = 3
        )
        repository.saveGameSave(originalSave)

        val result = ScoutingUseCase(repository).buyGlobalScoutReveal(originalSave, -1)

        assertTrue(result is ScoutingUseCase.ScoutingResult.Error)
        assertEquals(originalSave, repository.getGameSave())
        assertTrue(repository.getAllTransactions().isEmpty())
    }

    private suspend fun seedTeams(vararg ids: Long) {
        repository.saveTeams(
            ids.map { id ->
                Team(
                    id = id,
                    name = "Time $id",
                    city = "Cidade $id",
                    state = "BR",
                    country = "Brasil",
                    division = 1
                )
            }
        )
    }

    private fun standing(teamId: Long, position: Int, points: Int): GlobalLeagueStanding =
        GlobalLeagueStanding(
            season = 2026,
            country = "Brasil",
            division = 1,
            teamId = teamId,
            position = position,
            points = points,
            played = 38,
            wins = points / 3,
            draws = points % 3,
            losses = 0,
            goalsFor = 50,
            goalsAgainst = 20,
            goalDifference = 30
        )
}
