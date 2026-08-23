package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase108RolloverSetBasedSemanticsTest {

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
    fun `set based age reset changes only legacy rollover fields`() = runBlocking {
        val team = Team(
            id = 10L,
            name = "Semantics FC",
            city = "Belo Horizonte",
            state = "MG",
            country = "Brasil",
            division = 1,
            rating = 80
        )
        repository.saveTeams(listOf(team))

        val survivor = Player(
            id = 100L,
            teamId = team.id,
            name = "Survivor",
            age = 36,
            position = "MEI",
            force = 83,
            energy = 41,
            moral = 72,
            salary = 123_456L,
            contractDurationWeeks = 77,
            careerApps = 234,
            careerGoals = 51,
            careerAssists = 63,
            injuryWeeksRemaining = 4,
            suspensionWeeksRemaining = 2,
            yellowCardsAccumulated = 3,
            potential = 91,
            partidasDisputadas = 29,
            minutosJogados = 2_340
        )
        val retirementCandidate = Player(
            id = 101L,
            teamId = team.id,
            name = "Retirement Candidate",
            age = 37,
            position = "ATA",
            force = 79,
            energy = 55,
            moral = 67,
            salary = 222_222L,
            contractDurationWeeks = 12,
            injuryWeeksRemaining = 1,
            suspensionWeeksRemaining = 1,
            yellowCardsAccumulated = 4,
            potential = 82
        )
        repository.savePlayers(listOf(survivor, retirementCandidate))

        val changed = repository.ageAndResetRolloverPlayers(retirementCurrentAge = 37)
        assertEquals(1, changed)

        val updated = requireNotNull(repository.getPlayer(survivor.id))
        assertEquals(37, updated.age)
        assertEquals(100, updated.energy)
        assertEquals(80, updated.moral)
        assertEquals(0, updated.injuryWeeksRemaining)
        assertEquals(0, updated.suspensionWeeksRemaining)
        assertEquals(0, updated.yellowCardsAccumulated)

        assertEquals(survivor.teamId, updated.teamId)
        assertEquals(survivor.name, updated.name)
        assertEquals(survivor.position, updated.position)
        assertEquals(survivor.force, updated.force)
        assertEquals(survivor.potential, updated.potential)
        assertEquals(survivor.salary, updated.salary)
        assertEquals(survivor.contractDurationWeeks, updated.contractDurationWeeks)
        assertEquals(survivor.careerApps, updated.careerApps)
        assertEquals(survivor.careerGoals, updated.careerGoals)
        assertEquals(survivor.careerAssists, updated.careerAssists)
        assertEquals(survivor.partidasDisputadas, updated.partidasDisputadas)
        assertEquals(survivor.minutosJogados, updated.minutosJogados)

        val untouchedRetiree = requireNotNull(repository.getPlayer(retirementCandidate.id))
        assertEquals(retirementCandidate, untouchedRetiree)
    }

    @Test
    fun `bulk loan completion and retirement deletion preserve exact target set`() = runBlocking {
        val owner = Team(
            id = 20L,
            name = "Owner FC",
            city = "São Paulo",
            state = "SP",
            country = "Brasil",
            division = 1,
            rating = 82
        )
        val borrower = owner.copy(id = 21L, name = "Borrower FC", rating = 74)
        repository.saveTeams(listOf(owner, borrower))

        val retiring = Player(
            id = 200L,
            teamId = borrower.id,
            name = "Retiring Loan",
            age = 37,
            position = "ZAG",
            force = 76,
            isOnLoan = true,
            originalTeamId = owner.id
        )
        val survivor = Player(
            id = 201L,
            teamId = borrower.id,
            name = "Surviving Loan",
            age = 25,
            position = "ATA",
            force = 73,
            isOnLoan = true,
            originalTeamId = owner.id
        )
        repository.savePlayers(listOf(retiring, survivor))
        repository.saveLoans(
            listOf(
                PlayerLoan(
                    id = 300L,
                    playerId = retiring.id,
                    ownerTeamId = owner.id,
                    borrowerTeamId = borrower.id,
                    startSeason = 2026,
                    startWeek = 1,
                    durationWeeks = 20,
                    remainingWeeks = 7,
                    status = "ACTIVE"
                ),
                PlayerLoan(
                    id = 301L,
                    playerId = survivor.id,
                    ownerTeamId = owner.id,
                    borrowerTeamId = borrower.id,
                    startSeason = 2026,
                    startWeek = 1,
                    durationWeeks = 20,
                    remainingWeeks = 5,
                    status = "ACTIVE"
                )
            )
        )

        assertEquals(1, repository.completeRolloverLoansForPlayers(listOf(retiring.id)))
        assertEquals(1, repository.deleteRolloverPlayers(listOf(retiring.id)))

        assertNull(repository.getPlayer(retiring.id))
        assertEquals(survivor, repository.getPlayer(survivor.id))

        val loans = repository.getAllLoans().associateBy { it.id }
        assertEquals("COMPLETED", loans.getValue(300L).status)
        assertEquals(0, loans.getValue(300L).remainingWeeks)
        assertEquals("ACTIVE", loans.getValue(301L).status)
        assertEquals(5, loans.getValue(301L).remainingWeeks)
    }
}
