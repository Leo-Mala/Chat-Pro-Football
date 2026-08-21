package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import com.example.usecase.MonthlyEvolutionPlan
import com.example.usecase.PlayerEvolutionUseCase
import kotlinx.coroutines.test.runTest
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
class PlayerEvolutionPlanAtomicityTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var useCase: PlayerEvolutionUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        useCase = PlayerEvolutionUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun preparedPlanCommitsAgainstSameSaveSnapshot() = runTest {
        seedCareer()
        val save = repository.getGameSave()!!
        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W4")

        assertTrue(useCase.commitMonthlyEvolution(plan))

        val persisted = repository.getPlayer(10L)!!
        assertEquals(0, persisted.minutosJogados)
        assertTrue(repository.getHistoricoPorJogador(10L).isNotEmpty())
    }

    @Test
    fun retryingSamePreparedPlanDoesNotDuplicateEvolutionHistory() = runTest {
        seedCareer()
        val save = repository.getGameSave()!!
        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W4")

        assertTrue(useCase.commitMonthlyEvolution(plan))
        val historyAfterFirstCommit = repository.getHistoricoPorJogador(10L)
        assertTrue(historyAfterFirstCommit.isNotEmpty())

        assertTrue(useCase.commitMonthlyEvolution(plan))
        val historyAfterRetry = repository.getHistoricoPorJogador(10L)

        assertEquals(historyAfterFirstCommit.size, historyAfterRetry.size)
        assertEquals(
            historyAfterFirstCommit.map { listOf(it.jogadorId, it.data, it.atributo, it.valorAntigo, it.valorNovo) },
            historyAfterRetry.map { listOf(it.jogadorId, it.data, it.atributo, it.valorAntigo, it.valorNovo) }
        )
    }

    @Test
    fun unrelatedPlayerMutationIsPreservedByColumnScopedEvolutionCommit() = runTest {
        seedCareer()
        val save = repository.getGameSave()!!
        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W4")
        val current = repository.getPlayer(10L)!!

        repository.updatePlayer(current.copy(contractDurationWeeks = 77, salary = 999_999L, energy = 33))

        assertTrue(useCase.commitMonthlyEvolution(plan))
        val persisted = repository.getPlayer(10L)!!
        assertEquals(77, persisted.contractDurationWeeks)
        assertEquals(999_999L, persisted.salary)
        assertEquals(33, persisted.energy)
        assertEquals(0, persisted.minutosJogados)
    }

    @Test
    fun changedEvolutionInputRejectsPreparedPlanBeforeAnyWrite() = runTest {
        seedCareer()
        val save = repository.getGameSave()!!
        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W4")
        val current = repository.getPlayer(10L)!!
        repository.updatePlayer(current.copy(focoTreino = "finalizacao"))
        val changed = repository.getPlayer(10L)!!

        assertFalse(useCase.commitMonthlyEvolution(plan))
        assertEquals(changed, repository.getPlayer(10L))
        assertTrue(repository.getHistoricoPorJogador(10L).isEmpty())
    }

    @Test
    fun unchangedPlayersStillHaveMonthlyCountersResetWithoutEntityUpdate() = runTest {
        seedCareer()
        val save = repository.getGameSave()!!
        val original = repository.getPlayer(10L)!!
        repository.updatePlayer(original.copy(minutosJogados = 321, evolucaoMensal = 2.5))

        val counterOnlyPlan = MonthlyEvolutionPlan(
            expectedSeason = save.currentSeason,
            expectedWeek = save.currentWeek,
            expectedPlayerTeamId = save.playerTeamId,
            periodDate = "S2026_W4",
            results = emptyList(),
            updatedPlayers = emptyList(),
            historyLogs = emptyList()
        )

        assertTrue(useCase.commitMonthlyEvolution(counterOnlyPlan))

        val persisted = repository.getPlayer(10L)!!
        assertEquals(0, persisted.minutosJogados)
        assertEquals(0.0, persisted.evolucaoMensal, 0.0)
        assertEquals(original.force, persisted.force)
        assertEquals(original.teamId, persisted.teamId)
        assertEquals(original.contractDurationWeeks, persisted.contractDurationWeeks)
    }

    @Test
    fun stalePlanIsRejectedWithoutPlayerOrHistoryWrites() = runTest {
        seedCareer()
        val save = repository.getGameSave()!!
        val originalPlayer = repository.getPlayer(10L)!!
        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W4")

        repository.saveGameSave(save.copy(currentWeek = 5))

        assertFalse(useCase.commitMonthlyEvolution(plan))
        assertEquals(originalPlayer, repository.getPlayer(10L))
        assertTrue(repository.getHistoricoPorJogador(10L).isEmpty())
    }

    private suspend fun seedCareer() {
        repository.saveTeams(
            listOf(
                Team(
                    id = 1L,
                    name = "Performance FC",
                    city = "Belo Horizonte",
                    state = "MG",
                    country = "Brasil",
                    division = 1,
                    rating = 75,
                    trainingCenterLevel = 3
                )
            )
        )
        repository.saveGameSave(
            GameSave(
                currentSeason = 2026,
                currentWeek = 4,
                playerTeamId = 1L
            )
        )
        repository.savePlayers(
            listOf(
                Player(
                    id = 10L,
                    teamId = 1L,
                    name = "Jogador Performance",
                    age = 21,
                    position = "ATA",
                    force = 65,
                    potential = 90,
                    finishing = 60,
                    passing = 60,
                    pace = 60,
                    strength = 60,
                    vision = 60,
                    defense = 40,
                    minutosJogados = 240,
                    mediaNotas = 7.8
                )
            )
        )
    }
}
