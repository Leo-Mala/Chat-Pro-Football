package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GameCalendar
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.PlayerLoan
import com.example.data.Team
import com.example.usecase.DatabaseIntegrityUseCase
import com.example.usecase.GenerateCalendarUseCase
import com.example.usecase.SeasonTransitionUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RetirementLifecycleTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var transition: SeasonTransitionUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        transition = SeasonTransitionUseCase(
            repository,
            GenerateCalendarUseCase(repository),
            DatabaseIntegrityUseCase(repository)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `retired loaned player is replaced by clean new identity at owner club`() = runBlocking {
        val owner = Team(
            id = 10L,
            name = "Clube Proprietário",
            city = "Belo Horizonte",
            state = "MG",
            country = "Brasil",
            division = 1,
            rating = 85
        )
        val borrower = Team(
            id = 20L,
            name = "Clube Tomador",
            city = "São Paulo",
            state = "SP",
            country = "Brasil",
            division = 1,
            rating = 75
        )
        repository.saveTeams(listOf(owner, borrower))

        val ownerPlayers = (1..15).map { index ->
            Player(
                id = 100L + index,
                teamId = owner.id,
                name = "Owner $index",
                age = 24,
                position = if (index == 1) "GOL" else "MEI",
                force = 70
            )
        }
        val borrowerPlayers = (1..15).map { index ->
            Player(
                id = 200L + index,
                teamId = borrower.id,
                name = "Borrower $index",
                age = 24,
                position = if (index == 1) "GOL" else "ZAG",
                force = 65
            )
        }
        val retiringPlayer = Player(
            id = 500L,
            teamId = borrower.id,
            name = "Veterano Histórico",
            age = 37,
            nationality = "Brasil",
            position = "ATA",
            force = 88,
            salary = 250_000L,
            contractDurationWeeks = 3,
            careerApps = 411,
            careerGoals = 173,
            injuryWeeksRemaining = 2,
            suspensionWeeksRemaining = 1,
            yellowCardsAccumulated = 2,
            isOnLoan = true,
            loanWeeksRemaining = 8,
            originalTeamId = owner.id,
            careerAssists = 91,
            partidasDisputadas = 36,
            minutosJogados = 2_900
        )
        repository.savePlayers(ownerPlayers + borrowerPlayers + retiringPlayer)

        repository.saveLoan(
            PlayerLoan(
                id = 700L,
                playerId = retiringPlayer.id,
                ownerTeamId = owner.id,
                borrowerTeamId = borrower.id,
                startSeason = 2026,
                startWeek = 20,
                durationWeeks = 26,
                remainingWeeks = 8,
                status = "ACTIVE"
            )
        )

        val save = GameSave(
            coachName = "Retirement QA",
            currentSeason = 2026,
            currentWeek = GameCalendar.WEEKS_PER_SEASON,
            playerTeamId = owner.id,
            bankBalance = 10_000_000L
        )
        repository.saveGameSave(save)

        transition.advanceToNextSeason(save)

        assertNull("Retired identity must be removed", repository.getPlayer(retiringPlayer.id))

        val replacement = repository.getAllPlayers().single { player ->
            player.name == "Novo Prospecto stórico"
        }
        assertNotEquals(retiringPlayer.id, replacement.id)
        assertEquals(owner.id, replacement.teamId)
        assertEquals(18, replacement.age)
        assertEquals("ATA", replacement.position)
        assertEquals("Brasil", replacement.nationality)
        assertEquals(0, replacement.careerApps)
        assertEquals(0, replacement.careerGoals)
        assertEquals(0, replacement.careerAssists)
        assertEquals(0, replacement.partidasDisputadas)
        assertEquals(0, replacement.minutosJogados)
        assertEquals(10_000L, replacement.salary)
        assertEquals(52, replacement.contractDurationWeeks)
        assertFalse(replacement.isOnLoan)
        assertEquals(0, replacement.loanWeeksRemaining)
        assertEquals(0L, replacement.originalTeamId)
        assertEquals(0, replacement.injuryWeeksRemaining)
        assertEquals(0, replacement.suspensionWeeksRemaining)
        assertEquals(0, replacement.yellowCardsAccumulated)

        val closedLoan = repository.getAllLoans().single { it.id == 700L }
        assertEquals("COMPLETED", closedLoan.status)
        assertEquals(0, closedLoan.remainingWeeks)
        assertTrue(repository.getPlayersByTeam(owner.id).any { it.id == replacement.id })
    }
}
