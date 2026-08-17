package com.example.migrations

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.FixtureScheduleValidator
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.MatchSlot
import com.example.data.Player
import com.example.data.Team
import com.example.usecase.SimulateWeekUseCase
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
class Phase97MigrationContinuationTest {

    private val context by lazy {
        ApplicationProvider.getApplicationContext<android.content.Context>()
    }
    private val databaseName = "phase97-v19-v20-continuation.db"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `v19 career migrates through Room v20 preserves state and keeps playing`() = runBlocking {
        withContext(Dispatchers.IO) {
            context.deleteDatabase(databaseName)
            createRepresentativeV20Career()
            convertFixtureTableBackToV19Shape()

            // Esta abertura usa o AppDatabase real V20 e a cadeia oficial de migrations.
            val migratedDb = AppDatabase.buildDatabaseWithName(context, databaseName)
            val repository = GameRepository(migratedDb)

            val migratedSave = requireNotNull(repository.getGameSave())
            assertEquals("Carreira Migrada", migratedSave.coachName)
            assertEquals(2026, migratedSave.currentSeason)
            assertEquals(12, migratedSave.currentWeek)
            assertEquals(1L, migratedSave.playerTeamId)
            assertEquals(77_000_000L, migratedSave.bankBalance)

            assertEquals(setOf(1L, 2L, 3L), repository.getAllTeams().map { it.id }.toSet())
            assertEquals(setOf(101L, 201L, 301L), repository.getAllPlayers().map { it.id }.toSet())

            val migratedFixtures = repository.getFixturesForSeason(2026)
            assertEquals(2, migratedFixtures.size)
            assertEquals(
                MatchSlot.WEEKEND,
                migratedFixtures.single { it.competitionType == "SERIE_A" }.matchSlot
            )
            assertEquals(
                MatchSlot.MIDWEEK,
                migratedFixtures.single { it.competitionType == "COPA" }.matchSlot
            )
            FixtureScheduleValidator.requireValid(migratedFixtures)

            // Continuidade funcional: a carreira migrada não é apenas legível; ela consegue
            // processar normalmente os dois slots da semana e persistir os resultados.
            SimulateWeekUseCase(repository).simulateCpuMatchesForWeek(2026, 12)
            val played = repository.getFixturesForWeek(2026, 12)
            assertEquals(2, played.size)
            assertTrue(played.all { it.isPlayed })
            assertTrue(played.all { it.homeScore != null && it.awayScore != null })

            repository.saveGameSave(migratedSave.copy(currentWeek = 13))
            migratedDb.close()

            val reopenedDb = AppDatabase.buildDatabaseWithName(context, databaseName)
            val reopenedRepository = GameRepository(reopenedDb)
            assertEquals(13, requireNotNull(reopenedRepository.getGameSave()).currentWeek)
            assertTrue(reopenedRepository.getFixturesForWeek(2026, 12).all { it.isPlayed })
            FixtureScheduleValidator.requireValid(reopenedRepository.getFixturesForSeason(2026))
            reopenedDb.close()
        }
    }

    private suspend fun createRepresentativeV20Career() {
        val db = AppDatabase.buildDatabaseWithName(context, databaseName)
        val repository = GameRepository(db)
        repository.saveTeams(
            listOf(
                Team(1L, "Migrado A", "Cidade A", "BR", "Brasil", 1, isPlayerControlled = true),
                Team(2L, "Migrado B", "Cidade B", "BR", "Brasil", 1),
                Team(3L, "Migrado C", "Cidade C", "BR", "Brasil", 1)
            )
        )
        repository.savePlayers(
            listOf(
                Player(101L, 1L, "Jogador A", 24, position = "ATA", force = 78, contractDurationWeeks = 80),
                Player(201L, 2L, "Jogador B", 25, position = "MEI", force = 75, contractDurationWeeks = 70),
                Player(301L, 3L, "Jogador C", 23, position = "ZAG", force = 74, contractDurationWeeks = 90)
            )
        )
        repository.saveGameSave(
            GameSave(
                coachName = "Carreira Migrada",
                currentWeek = 12,
                currentSeason = 2026,
                playerTeamId = 1L,
                bankBalance = 77_000_000L
            )
        )
        repository.saveFixtures(
            listOf(
                Fixture(
                    season = 2026,
                    week = 12,
                    homeTeamId = 1L,
                    awayTeamId = 2L,
                    competitionType = "SERIE_A",
                    matchSlot = MatchSlot.WEEKEND
                ),
                Fixture(
                    season = 2026,
                    week = 12,
                    homeTeamId = 1L,
                    awayTeamId = 3L,
                    competitionType = "COPA",
                    matchSlot = MatchSlot.MIDWEEK
                )
            )
        )
        db.close()
    }

    /**
     * Parte de um banco V20 válido para manter todas as demais tabelas exatamente compatíveis
     * com a V19 e rebaixa somente `fixtures`, que foi a estrutura modificada pela 19 -> 20.
     * Isso produz um arquivo de carreira V19 realista que o Room V20 precisa migrar e validar.
     */
    private fun convertFixtureTableBackToV19Shape() {
        val path = context.getDatabasePath(databaseName).absolutePath
        val sqlite = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE)
        sqlite.beginTransaction()
        try {
            sqlite.execSQL("ALTER TABLE `fixtures` RENAME TO `fixtures_v20_backup`")
            sqlite.execSQL(
                """
                CREATE TABLE `fixtures` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `season` INTEGER NOT NULL,
                    `week` INTEGER NOT NULL,
                    `homeTeamId` INTEGER NOT NULL,
                    `awayTeamId` INTEGER NOT NULL,
                    `homeScore` INTEGER,
                    `awayScore` INTEGER,
                    `homePenalties` INTEGER,
                    `awayPenalties` INTEGER,
                    `competitionType` TEXT NOT NULL,
                    `isPlayed` INTEGER NOT NULL,
                    `matchEventsJson` TEXT
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                INSERT INTO `fixtures` (
                    `id`,`season`,`week`,`homeTeamId`,`awayTeamId`,`homeScore`,`awayScore`,
                    `homePenalties`,`awayPenalties`,`competitionType`,`isPlayed`,`matchEventsJson`
                )
                SELECT
                    `id`,`season`,`week`,`homeTeamId`,`awayTeamId`,`homeScore`,`awayScore`,
                    `homePenalties`,`awayPenalties`,`competitionType`,`isPlayed`,`matchEventsJson`
                FROM `fixtures_v20_backup`
                """.trimIndent()
            )
            sqlite.execSQL("DROP TABLE `fixtures_v20_backup`")
            sqlite.execSQL("CREATE INDEX `index_fixtures_season` ON `fixtures` (`season`)")
            sqlite.execSQL("CREATE INDEX `index_fixtures_week` ON `fixtures` (`week`)")
            sqlite.execSQL("CREATE INDEX `index_fixtures_homeTeamId` ON `fixtures` (`homeTeamId`)")
            sqlite.execSQL("CREATE INDEX `index_fixtures_awayTeamId` ON `fixtures` (`awayTeamId`)")
            sqlite.execSQL("CREATE INDEX `index_fixtures_competitionType` ON `fixtures` (`competitionType`)")
            sqlite.execSQL("CREATE INDEX `index_fixtures_season_week` ON `fixtures` (`season`, `week`)")
            sqlite.execSQL("PRAGMA user_version = 19")
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
            sqlite.close()
        }
    }
}
