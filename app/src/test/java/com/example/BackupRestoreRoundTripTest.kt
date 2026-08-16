package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class BackupRestoreRoundTripTest {

    private lateinit var dbSource: AppDatabase
    private lateinit var repositorySource: GameRepository

    private lateinit var dbRestored: AppDatabase
    private lateinit var repositoryRestored: GameRepository

    @Before
    fun setup() {
        dbSource = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        dbRestored = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repositorySource = GameRepository(dbSource)
        repositoryRestored = GameRepository(dbRestored)
    }

    @After
    fun tearDown() {
        dbSource.close()
        dbRestored.close()
    }

    @Test
    fun executeBackupRestoreRoundTripVerification() = runBlocking {
        // 1. Criar e popular uma carreira completa com todas as entidades
        val save = GameSave(
            id = 1,
            coachName = "Técnico Backup Teste",
            coachReputation = 85,
            currentWeek = 15,
            currentSeason = 2027,
            playerTeamId = 100L,
            bankBalance = 15000000L
        )
        repositorySource.saveGameSave(save)

        val team1 = Team(id = 100L, name = "Time Principal", city = "BH", state = "MG", division = 1, country = "Brasil", rating = 88)
        val team2 = Team(id = 101L, name = "Time Rival", city = "SP", state = "SP", division = 1, country = "Brasil", rating = 82)
        repositorySource.saveTeams(listOf(team1, team2))

        val p1 = Player(id = 1001L, teamId = 100L, name = "Craque 1", age = 24, position = "ATA", force = 90)
        val p2 = Player(id = 1002L, teamId = 101L, name = "Craque 2", age = 28, position = "MEI", force = 84)
        repositorySource.savePlayers(listOf(p1, p2))

        val f1 = Fixture(id = 501L, season = 2027, week = 1, homeTeamId = 100L, awayTeamId = 101L, competitionType = "SERIE_A", isPlayed = true, homeScore = 3, awayScore = 1)
        repositorySource.saveFixtures(listOf(f1))

        val t1 = TransactionRecord(id = 1, week = 1, season = 2027, type = "PRIZE", description = "Prêmio de Vitória", amount = 100000, isIncome = true)
        repositorySource.saveTransaction(t1)

        val inst1 = TransferInstallment(id = 1, transferId = 88, playerId = 1001L, buyerTeamId = 100L, sellerTeamId = 101L, totalAmount = 10000000, downPayment = 2000000, installmentAmount = 2000000, totalInstallments = 4, remainingInstallments = 4, nextDueWeek = 16, season = 2027, status = "PENDING")
        repositorySource.saveInstallments(listOf(inst1))

        val loan1 = PlayerLoan(id = 1, playerId = 1002L, ownerTeamId = 101L, borrowerTeamId = 100L, startSeason = 2027, startWeek = 5, durationWeeks = 20, remainingWeeks = 10, weeklyFee = 5000, status = "ACTIVE")
        repositorySource.saveLoans(listOf(loan1))

        val record1 = HistoricalRecord(id = 1, season = 2026, competitionName = "Campeonato Brasileiro", championTeamName = "Time Principal", runnerUpTeamName = "Time Rival", topScorerName = "Artilheiro 1", topScorerGoals = 22, topScorerTeam = "Time Principal")
        repositorySource.saveRecord(record1)

        // 2. Leitura do Snapshot Original (Exportação)
        val sourceSave = repositorySource.getGameSave()
        val sourceTeams = repositorySource.getAllTeams()
        val sourcePlayers = repositorySource.getAllPlayers()
        val sourceFixtures = repositorySource.getFixturesForSeason(2027)
        val sourceTransactions = repositorySource.getAllTransactions()
        val sourceInstallments = repositorySource.getAllInstallments()
        val sourceLoans = repositorySource.getAllLoans()
        val sourceRecords = repositorySource.getAllHistoricalRecords()

        // 3. Simulação de Restauração em novo banco de dados
        assertNotNull(sourceSave)
        repositoryRestored.saveGameSave(sourceSave!!)
        repositoryRestored.saveTeams(sourceTeams)
        repositoryRestored.savePlayers(sourcePlayers)
        repositoryRestored.saveFixtures(sourceFixtures)
        sourceTransactions.forEach { repositoryRestored.saveTransaction(it) }
        repositoryRestored.saveInstallments(sourceInstallments)
        repositoryRestored.saveLoans(sourceLoans)
        sourceRecords.forEach { repositoryRestored.saveRecord(it) }

        // 4. Verificação de Equivalência Pós-Restauração (100% igual ao estado pré-exportação)
        val restoredSave = repositoryRestored.getGameSave()
        val restoredTeams = repositoryRestored.getAllTeams()
        val restoredPlayers = repositoryRestored.getAllPlayers()
        val restoredFixtures = repositoryRestored.getFixturesForSeason(2027)
        val restoredTransactions = repositoryRestored.getAllTransactions()
        val restoredInstallments = repositoryRestored.getAllInstallments()
        val restoredLoans = repositoryRestored.getAllLoans()
        val restoredRecords = repositoryRestored.getAllHistoricalRecords()

        assertEquals("GameSave deve ser exatamente igual", sourceSave, restoredSave)
        assertEquals("Teams devem ter o mesmo tamanho", sourceTeams.size, restoredTeams.size)
        assertEquals("Teams devem ser exatamente iguais", sourceTeams, restoredTeams)

        assertEquals("Players devem ter o mesmo tamanho", sourcePlayers.size, restoredPlayers.size)
        assertEquals("Players devem ser exatamente iguais", sourcePlayers, restoredPlayers)

        assertEquals("Fixtures devem ter o mesmo tamanho", sourceFixtures.size, restoredFixtures.size)
        assertEquals("Fixtures devem ser exatamente iguais", sourceFixtures, restoredFixtures)

        assertEquals("Transactions devem ter o mesmo tamanho", sourceTransactions.size, restoredTransactions.size)
        assertEquals("Transactions devem ser exatamente iguais", sourceTransactions, restoredTransactions)

        assertEquals("Installments devem ter o mesmo tamanho", sourceInstallments.size, restoredInstallments.size)
        assertEquals("Installments devem ser exatamente iguais", sourceInstallments, restoredInstallments)

        assertEquals("Loans devem ter o mesmo tamanho", sourceLoans.size, restoredLoans.size)
        assertEquals("Loans devem ser exatamente iguais", sourceLoans, restoredLoans)

        assertEquals("Records devem ter o mesmo tamanho", sourceRecords.size, restoredRecords.size)
        assertEquals("Records devem ser exatamente iguais", sourceRecords, restoredRecords)
    }
}
