package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.PlayerLoan
import com.example.data.Team
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CpuSquadManagementUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var manager: CpuSquadManagementUseCase
    private lateinit var transfers: ProcessTransfersUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        manager = CpuSquadManagementUseCase(repository)
        transfers = ProcessTransfersUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `cpu renews enough expiring contracts to stay playable through weekly tick`() = runBlocking {
        val user = team(1L, "Usuário", isPlayer = true)
        val cpu = team(2L, "CPU")
        repository.saveTeams(listOf(user, cpu))
        repository.saveGameSave(GameSave(playerTeamId = user.id, currentSeason = 2026, currentWeek = 20))

        val roster = (1..16).map { index ->
            Player(
                id = 2_000L + index,
                teamId = cpu.id,
                name = "CPU $index",
                age = if (index == 16) 36 else 24 + (index % 5),
                position = if (index == 1) "GOL" else if (index <= 6) "ZAG" else if (index <= 11) "MEI" else "ATA",
                force = if (index == 16) 45 else 74,
                potential = if (index == 16) 55 else 82,
                salary = 20_000L,
                contractDurationWeeks = 1
            )
        }
        repository.savePlayers(roster)

        val renewed = manager.renewCpuContractsBeforeWeeklyTick()
        transfers.processWeeklyContractsAndLoans()
        val report = manager.ensureCpuSquadIntegrity()
        val after = repository.getPlayersByTeam(cpu.id)

        assertTrue("A CPU deve renovar contratos suficientes antes da expiração", renewed >= 15)
        assertEquals(16, after.size)
        assertTrue(after.any { it.position == "GOL" })
        assertTrue(after.all { it.contractDurationWeeks >= 0 })
        assertEquals(16, report.minimumRosterSize)
        assertEquals(0, report.teamsWithoutGoalkeeper)
        assertEquals(after.size, after.map { it.id }.toSet().size)
    }

    @Test
    fun `cpu reuses free agents before generating emergency players`() = runBlocking {
        val user = team(1L, "Usuário", isPlayer = true)
        val cpu = team(2L, "CPU")
        repository.saveTeams(listOf(user, cpu))
        repository.saveGameSave(GameSave(playerTeamId = user.id))

        repository.savePlayers(
            (1..10).map { index ->
                Player(
                    id = 2_100L + index,
                    teamId = cpu.id,
                    name = "Titular CPU $index",
                    age = 25,
                    position = if (index <= 4) "ZAG" else if (index <= 7) "MEI" else "ATA",
                    force = 70,
                    contractDurationWeeks = 80
                )
            } +
                (1..6).map { index ->
                    Player(
                        id = 9_000L + index,
                        teamId = null,
                        name = "Livre $index",
                        age = 22 + index,
                        position = if (index == 1) "GOL" else if (index <= 3) "ZAG" else "MEI",
                        force = 68 + index,
                        salary = 0L,
                        contractDurationWeeks = 0
                    )
                }
        )

        val freeAgentIds = repository.getAllPlayers().filter { it.teamId == null }.map { it.id }.toSet()
        val report = manager.ensureCpuSquadIntegrity()
        val roster = repository.getPlayersByTeam(cpu.id)

        assertEquals(16, roster.size)
        assertTrue(roster.any { it.position == "GOL" })
        assertEquals(6, report.freeAgentsSigned)
        assertEquals(0, report.emergencyPlayersGenerated)
        assertTrue("Os mesmos IDs dos agentes livres devem ser reaproveitados", roster.count { it.id in freeAgentIds } == 6)
        assertEquals(0, repository.getAllPlayers().count { it.teamId == null })
    }

    @Test
    fun `cpu generates only missing emergency depth when free agents are insufficient`() = runBlocking {
        val user = team(1L, "Usuário", isPlayer = true)
        val cpu = team(2L, "CPU")
        repository.saveTeams(listOf(user, cpu))
        repository.saveGameSave(GameSave(playerTeamId = user.id))

        repository.savePlayers(
            (1..12).map { index ->
                Player(
                    id = 3_000L + index,
                    teamId = cpu.id,
                    name = "CPU Base $index",
                    age = 25,
                    position = if (index == 1) "GOL" else "MEI",
                    force = 70,
                    contractDurationWeeks = 70
                )
            } +
                Player(
                    id = 9_500L,
                    teamId = null,
                    name = "Livre Único",
                    age = 24,
                    position = "ATA",
                    force = 71,
                    contractDurationWeeks = 0,
                    salary = 0L
                )
        )

        val report = manager.ensureCpuSquadIntegrity()
        val roster = repository.getPlayersByTeam(cpu.id)

        assertEquals(16, roster.size)
        assertEquals(1, report.freeAgentsSigned)
        assertEquals(3, report.emergencyPlayersGenerated)
        assertEquals(16, roster.map { it.id }.toSet().size)
        assertTrue(roster.all { it.contractDurationWeeks >= 0 })
    }

    @Test
    fun `legacy oversize cpu roster is reduced to maximum without touching active loan`() = runBlocking {
        val user = team(1L, "Usuário", isPlayer = true)
        val owner = team(2L, "Proprietário")
        val borrower = team(3L, "Tomador")
        repository.saveTeams(listOf(user, owner, borrower))
        repository.saveGameSave(GameSave(playerTeamId = user.id))

        val borrowerRoster = (1..35).map { index ->
            Player(
                id = 4_000L + index,
                teamId = borrower.id,
                name = "Tomador $index",
                age = 24 + (index % 8),
                position = if (index == 1) "GOL" else "MEI",
                force = 65 + (index % 10),
                contractDurationWeeks = 90
            )
        }.toMutableList()
        val loaned = Player(
            id = 8_888L,
            teamId = borrower.id,
            originalTeamId = owner.id,
            name = "Emprestado",
            age = 23,
            position = "ATA",
            force = 80,
            contractDurationWeeks = 80,
            isOnLoan = true,
            loanWeeksRemaining = 12
        )
        borrowerRoster.add(loaned)

        repository.savePlayers(
            borrowerRoster +
                (1..16).map { index ->
                    Player(
                        id = 5_000L + index,
                        teamId = owner.id,
                        name = "Proprietário $index",
                        age = 25,
                        position = if (index == 1) "GOL" else "ZAG",
                        force = 70,
                        contractDurationWeeks = 90
                    )
                }
        )
        repository.saveLoan(
            PlayerLoan(
                playerId = loaned.id,
                ownerTeamId = owner.id,
                borrowerTeamId = borrower.id,
                startSeason = 2026,
                startWeek = 1,
                durationWeeks = 12,
                remainingWeeks = 12,
                status = "ACTIVE"
            )
        )

        val report = manager.ensureCpuSquadIntegrity()
        val after = repository.getPlayersByTeam(borrower.id)
        val persistedLoaned = requireNotNull(repository.getPlayer(loaned.id))

        assertEquals(CpuSquadManagementUseCase.MAX_SQUAD_SIZE, after.size)
        assertTrue(persistedLoaned.isOnLoan)
        assertEquals(owner.id, persistedLoaned.originalTeamId)
        assertEquals(borrower.id, persistedLoaned.teamId)
        assertEquals(0, report.invalidActiveLoans)
        assertFalse(repository.getActiveLoans().isEmpty())
    }

    @Test
    fun `same persisted state produces identical CPU squad decisions`() = runBlocking {
        val secondDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val secondRepository = GameRepository(secondDb)
            seedDeterministicScenario(repository)
            seedDeterministicScenario(secondRepository)

            val firstReport = CpuSquadManagementUseCase(repository).ensureCpuSquadIntegrity()
            val secondReport = CpuSquadManagementUseCase(secondRepository).ensureCpuSquadIntegrity()

            assertEquals(firstReport, secondReport)
            assertEquals(
                repository.getAllPlayers().sortedBy { it.id },
                secondRepository.getAllPlayers().sortedBy { it.id }
            )
        } finally {
            secondDb.close()
        }
    }

    private suspend fun seedDeterministicScenario(target: GameRepository) {
        val user = team(10L, "Usuário Determinismo", isPlayer = true)
        val cpuA = team(20L, "CPU A")
        val cpuB = team(30L, "CPU B")
        target.saveTeams(listOf(user, cpuA, cpuB))
        target.saveGameSave(GameSave(playerTeamId = user.id, currentSeason = 2029, currentWeek = 12))
        target.savePlayers(
            (1..12).map { index ->
                Player(
                    id = 20_000L + index,
                    teamId = cpuA.id,
                    name = "A $index",
                    age = 24 + index % 7,
                    position = if (index == 1) "GOL" else "MEI",
                    force = 68 + index % 5,
                    contractDurationWeeks = 40
                )
            } +
                (1..14).map { index ->
                    Player(
                        id = 30_000L + index,
                        teamId = cpuB.id,
                        name = "B $index",
                        age = 23 + index % 8,
                        position = if (index == 1) "GOL" else "ZAG",
                        force = 70 + index % 6,
                        contractDurationWeeks = 40
                    )
                } +
                (1..8).map { index ->
                    Player(
                        id = 90_000L + index,
                        teamId = null,
                        name = "Livre Determinístico $index",
                        age = 21 + index,
                        position = if (index == 1) "GOL" else if (index <= 4) "MEI" else "ATA",
                        force = 65 + index,
                        contractDurationWeeks = 0,
                        salary = 0L
                    )
                }
        )
    }

    private fun team(id: Long, name: String, isPlayer: Boolean = false): Team = Team(
        id = id,
        name = name,
        city = name,
        state = "BR",
        country = "Brasil",
        division = 1,
        isPlayerControlled = isPlayer,
        rating = 75
    )
}
