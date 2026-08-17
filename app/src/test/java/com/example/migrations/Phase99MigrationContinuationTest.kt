package com.example.migrations

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.MatchSlot
import com.example.data.Player
import com.example.data.PlayerLoan
import com.example.data.Team
import com.example.data.TransactionRecord
import com.example.data.TransferInstallment
import com.example.support.RelationalIntegrityAssertions
import com.example.usecase.FinanceUseCase
import com.example.usecase.ProcessTransfersUseCase
import com.example.usecase.SimulateWeekUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase99MigrationContinuationTest {

    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }
    private val databaseName = "phase99-v20-v21-continuation.db"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `v20 physical career migrates to v21 and continues after reopen`() = runBlocking {
        withContext(Dispatchers.IO) {
            context.deleteDatabase(databaseName)
            createRepresentativeV21Career()
            convertRelationalTablesBackToV20Shape()

            val migratedDb = AppDatabase.buildDatabaseWithName(context, databaseName)
            val repository = GameRepository(migratedDb)

            val migratedSave = requireNotNull(repository.getGameSave())
            assertEquals("Carreira V20 Realista", migratedSave.coachName)
            assertEquals(2026, migratedSave.currentSeason)
            assertEquals(12, migratedSave.currentWeek)
            assertEquals(1L, migratedSave.playerTeamId)

            assertEquals(setOf(1L, 2L, 3L), repository.getAllTeams().map { it.id }.toSet())
            assertEquals(setOf(101L, 201L, 301L, 401L), repository.getAllPlayers().map { it.id }.toSet())
            assertNull("Free Agent V20 teamId=0 deve virar null", requireNotNull(repository.getPlayer(401L)).teamId)

            val loaned = requireNotNull(repository.getPlayer(301L))
            assertEquals(3L, loaned.teamId)
            assertEquals(2L, loaned.originalTeamId)
            assertTrue(loaned.isOnLoan)
            val loan = repository.getActiveLoans().single()
            assertEquals(301L, loan.playerId)
            assertEquals(2L, loan.ownerTeamId)
            assertEquals(3L, loan.borrowerTeamId)

            assertEquals(1, repository.getAllInstallments().size)
            assertTrue(repository.getAllTransactions().any { it.description == "Histórico financeiro V20" })
            assertEquals(2, repository.getFixturesForSeason(2026).size)

            RelationalIntegrityAssertions.assertRepositoryReferences(repository)
            RelationalIntegrityAssertions.assertDatabasePragmas(migratedDb)

            // A carreira migrada precisa continuar funcional, não apenas abrir.
            SimulateWeekUseCase(repository).simulateCpuMatchesForWeek(2026, 12)
            assertTrue(repository.getFixturesForWeek(2026, 12).all { it.isPlayed })

            val transfers = ProcessTransfersUseCase(repository)
            transfers.processWeeklyContractsAndLoans()
            val finance = FinanceUseCase(repository)
            val afterFinance = finance.processWeeklyFinances(
                save = migratedSave,
                homeMatchCount = 0,
                userPlayers = repository.getPlayersByTeam(1L)
            )
            assertNotNull(afterFinance)

            repository.saveGameSave(requireNotNull(repository.getGameSave()).copy(currentWeek = 13))
            RelationalIntegrityAssertions.assertDatabasePragmas(migratedDb)
            migratedDb.close()

            val reopenedDb = AppDatabase.buildDatabaseWithName(context, databaseName)
            val reopenedRepository = GameRepository(reopenedDb)
            assertEquals(13, requireNotNull(reopenedRepository.getGameSave()).currentWeek)
            assertNull(requireNotNull(reopenedRepository.getPlayer(401L)).teamId)
            assertEquals(2, reopenedRepository.getFixturesForWeek(2026, 12).size)
            assertTrue(reopenedRepository.getFixturesForWeek(2026, 12).all { it.isPlayed })
            assertEquals(1, reopenedRepository.getAllInstallments().size)
            assertEquals(1, reopenedRepository.getAllLoans().size)
            RelationalIntegrityAssertions.assertRepositoryReferences(reopenedRepository)
            RelationalIntegrityAssertions.assertDatabasePragmas(reopenedDb)
            reopenedDb.close()
        }
    }

    private suspend fun createRepresentativeV21Career() {
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
                Player(301L, 3L, "Jogador Emprestado", 23, position = "ZAG", force = 74, contractDurationWeeks = 90, isOnLoan = true, loanWeeksRemaining = 8, originalTeamId = 2L),
                Player(401L, null, "Agente Livre V20", 26, position = "ATA", force = 71, salary = 0L, contractDurationWeeks = 0)
            )
        )
        repository.saveGameSave(
            GameSave(
                coachName = "Carreira V20 Realista",
                currentWeek = 12,
                currentSeason = 2026,
                playerTeamId = 1L,
                bankBalance = 77_000_000L
            )
        )
        repository.saveFixtures(
            listOf(
                Fixture(season = 2026, week = 12, homeTeamId = 1L, awayTeamId = 2L, competitionType = "SERIE_A", matchSlot = MatchSlot.WEEKEND),
                Fixture(season = 2026, week = 12, homeTeamId = 1L, awayTeamId = 3L, competitionType = "COPA", matchSlot = MatchSlot.MIDWEEK)
            )
        )
        repository.saveLoan(
            PlayerLoan(
                playerId = 301L,
                ownerTeamId = 2L,
                borrowerTeamId = 3L,
                startSeason = 2026,
                startWeek = 5,
                durationWeeks = 15,
                remainingWeeks = 8,
                weeklyFee = 5_000L,
                status = "ACTIVE"
            )
        )
        repository.saveInstallment(
            TransferInstallment(
                transferId = 7001L,
                playerId = 101L,
                buyerTeamId = 1L,
                sellerTeamId = 2L,
                totalAmount = 900_000L,
                downPayment = 300_000L,
                installmentAmount = 300_000L,
                totalInstallments = 2,
                remainingInstallments = 2,
                nextDueWeek = 13,
                season = 2026
            )
        )
        repository.saveTransaction(
            TransactionRecord(
                week = 12,
                season = 2026,
                type = "HISTORICO",
                description = "Histórico financeiro V20",
                amount = 123_456L,
                isIncome = true
            )
        )
        db.close()
    }

    /** Rebaixa somente as tabelas modificadas pela V21; as demais permanecem V20-compatíveis. */
    private fun convertRelationalTablesBackToV20Shape() {
        val path = context.getDatabasePath(databaseName).absolutePath
        val sqlite = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE)
        sqlite.beginTransaction()
        try {
            sqlite.execSQL("ALTER TABLE players RENAME TO players_v21_backup")
            sqlite.execSQL(v20PlayersCreateSql())
            sqlite.execSQL(
                """
                INSERT INTO players (
                    id,teamId,name,age,nationality,position,force,energy,moral,salary,
                    contractDurationWeeks,isFromAcademy,careerApps,careerGoals,imageUrl,
                    injuryWeeksRemaining,suspensionWeeksRemaining,yellowCardsAccumulated,isStarter,
                    isOnLoan,loanWeeksRemaining,originalTeamId,careerAssists,careerTackles,careerSaves,
                    ratingSum,ratingCount,maxHistoricalForce,market_value,min_price,max_price,demand_level,
                    finishing,passing,pace,strength,vision,defense,scoutedLevel,atributosJson,atributos,
                    potential,gols,assistencias,partidasDisputadas,minutosJogados,mediaNotas,focoTreino,
                    condicao,evolucaoMensal
                )
                SELECT
                    id,COALESCE(teamId,0),name,age,nationality,position,force,energy,moral,salary,
                    contractDurationWeeks,isFromAcademy,careerApps,careerGoals,imageUrl,
                    injuryWeeksRemaining,suspensionWeeksRemaining,yellowCardsAccumulated,isStarter,
                    isOnLoan,loanWeeksRemaining,COALESCE(originalTeamId,0),careerAssists,careerTackles,careerSaves,
                    ratingSum,ratingCount,maxHistoricalForce,market_value,min_price,max_price,demand_level,
                    finishing,passing,pace,strength,vision,defense,scoutedLevel,atributosJson,atributos,
                    potential,gols,assistencias,partidasDisputadas,minutosJogados,mediaNotas,focoTreino,
                    condicao,evolucaoMensal
                FROM players_v21_backup
                """.trimIndent()
            )
            sqlite.execSQL("DROP TABLE players_v21_backup")
            sqlite.execSQL("CREATE INDEX index_players_teamId_position_force ON players (teamId,position,force)")
            sqlite.execSQL("CREATE INDEX index_players_teamId_isStarter ON players (teamId,isStarter)")
            sqlite.execSQL("CREATE INDEX index_players_originalTeamId ON players (originalTeamId)")

            sqlite.execSQL("ALTER TABLE fixtures RENAME TO fixtures_v21_backup")
            sqlite.execSQL(v20FixturesCreateSql())
            sqlite.execSQL(
                """
                INSERT INTO fixtures (
                    id,season,week,homeTeamId,awayTeamId,homeScore,awayScore,homePenalties,awayPenalties,
                    competitionType,isPlayed,matchEventsJson,matchSlot
                )
                SELECT id,season,week,homeTeamId,awayTeamId,homeScore,awayScore,homePenalties,awayPenalties,
                    competitionType,isPlayed,matchEventsJson,matchSlot
                FROM fixtures_v21_backup
                """.trimIndent()
            )
            sqlite.execSQL("DROP TABLE fixtures_v21_backup")
            sqlite.execSQL("CREATE INDEX index_fixtures_season ON fixtures (season)")
            sqlite.execSQL("CREATE INDEX index_fixtures_week ON fixtures (week)")
            sqlite.execSQL("CREATE INDEX index_fixtures_homeTeamId ON fixtures (homeTeamId)")
            sqlite.execSQL("CREATE INDEX index_fixtures_awayTeamId ON fixtures (awayTeamId)")
            sqlite.execSQL("CREATE INDEX index_fixtures_competitionType ON fixtures (competitionType)")
            sqlite.execSQL("CREATE INDEX index_fixtures_season_week ON fixtures (season,week)")
            sqlite.execSQL("CREATE INDEX index_fixtures_season_week_matchSlot ON fixtures (season,week,matchSlot)")

            sqlite.execSQL("PRAGMA user_version = 20")
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
            sqlite.close()
        }
    }

    private fun v20PlayersCreateSql(): String =
        """
        CREATE TABLE players (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, teamId INTEGER NOT NULL,
            name TEXT NOT NULL, age INTEGER NOT NULL, nationality TEXT NOT NULL,
            position TEXT NOT NULL, force INTEGER NOT NULL, energy INTEGER NOT NULL,
            moral INTEGER NOT NULL, salary INTEGER NOT NULL, contractDurationWeeks INTEGER NOT NULL,
            isFromAcademy INTEGER NOT NULL, careerApps INTEGER NOT NULL, careerGoals INTEGER NOT NULL,
            imageUrl TEXT, injuryWeeksRemaining INTEGER NOT NULL, suspensionWeeksRemaining INTEGER NOT NULL,
            yellowCardsAccumulated INTEGER NOT NULL, isStarter INTEGER NOT NULL, isOnLoan INTEGER NOT NULL,
            loanWeeksRemaining INTEGER NOT NULL, originalTeamId INTEGER NOT NULL, careerAssists INTEGER NOT NULL,
            careerTackles INTEGER NOT NULL, careerSaves INTEGER NOT NULL, ratingSum REAL NOT NULL,
            ratingCount INTEGER NOT NULL, maxHistoricalForce INTEGER NOT NULL, market_value INTEGER NOT NULL,
            min_price INTEGER NOT NULL, max_price INTEGER NOT NULL, demand_level TEXT NOT NULL,
            finishing INTEGER NOT NULL, passing INTEGER NOT NULL, pace INTEGER NOT NULL, strength INTEGER NOT NULL,
            vision INTEGER NOT NULL, defense INTEGER NOT NULL, scoutedLevel INTEGER NOT NULL, atributosJson TEXT,
            atributos TEXT NOT NULL, potential INTEGER NOT NULL, gols INTEGER NOT NULL, assistencias INTEGER NOT NULL,
            partidasDisputadas INTEGER NOT NULL, minutosJogados INTEGER NOT NULL, mediaNotas REAL NOT NULL,
            focoTreino TEXT, condicao INTEGER NOT NULL, evolucaoMensal REAL NOT NULL
        )
        """.trimIndent()

    private fun v20FixturesCreateSql(): String =
        """
        CREATE TABLE fixtures (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, season INTEGER NOT NULL, week INTEGER NOT NULL,
            homeTeamId INTEGER NOT NULL, awayTeamId INTEGER NOT NULL, homeScore INTEGER, awayScore INTEGER,
            homePenalties INTEGER, awayPenalties INTEGER, competitionType TEXT NOT NULL, isPlayed INTEGER NOT NULL,
            matchEventsJson TEXT, matchSlot TEXT NOT NULL DEFAULT 'WEEKEND'
        )
        """.trimIndent()
}
