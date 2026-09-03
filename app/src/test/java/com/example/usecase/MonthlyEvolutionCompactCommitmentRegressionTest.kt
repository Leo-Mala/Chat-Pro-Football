package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class MonthlyEvolutionCompactCommitmentRegressionTest {
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
    fun tearDown() = db.close()

    @Test
    fun `compact commitment rejects changed football input before any monthly write`() = runTest {
        val team = Team(id = 41L, name = "Digest FC", city = "BH", state = "MG", division = 1, trainingCenterLevel = 2)
        repository.saveTeams(listOf(team))
        val save = GameSave(currentSeason = 2026, currentWeek = 8, playerTeamId = team.id)
        repository.saveGameSave(save)
        repository.savePlayers(listOf(
            Player(id = 401L, teamId = team.id, name = "Digest One", age = 22, position = "MEI", force = 70,
                potential = 90, minutosJogados = 180, mediaNotas = 7.5)
        ))

        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W8")
        val current = requireNotNull(repository.getPlayer(401L))
        repository.updatePlayers(listOf(current.copy(force = current.force + 1)))

        assertFalse(useCase.commitMonthlyEvolution(plan))
        val after = requireNotNull(repository.getPlayer(401L))
        assertEquals(180, after.minutosJogados)
        assertEquals(current.force + 1, after.force)
        assertTrue(repository.getHistoricoPorJogador(401L).isEmpty())
    }

    @Test
    fun `weekly compact commitment accepts new player as targeted correction`() = runTest {
        val team = Team(id = 51L, name = "Correction FC", city = "SP", state = "SP", division = 1, trainingCenterLevel = 2)
        repository.saveTeams(listOf(team))
        val save = GameSave(currentSeason = 2026, currentWeek = 12, playerTeamId = team.id)
        repository.saveGameSave(save)
        repository.savePlayers(listOf(
            Player(id = 501L, teamId = team.id, name = "Existing", age = 23, position = "ATA", force = 68,
                potential = 88, minutosJogados = 120, mediaNotas = 7.2)
        ))

        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W12")
        repository.savePlayers(listOf(
            Player(id = 502L, teamId = team.id, name = "Emergency", age = 18, position = "ATA", force = 55,
                potential = 85, minutosJogados = 0, mediaNotas = 0.0)
        ))

        assertTrue(useCase.commitMonthlyEvolution(plan, allowWeeklyRosterCorrections = true))
        assertEquals(0, requireNotNull(repository.getPlayer(501L)).minutosJogados)
        assertTrue(repository.getPlayer(502L) != null)
    }

    @Test
    fun `weekly same training level team move remains safe and preserves new team`() = runTest {
        val oldTeam = Team(id = 61L, name = "Old FC", city = "RJ", state = "RJ", division = 1, trainingCenterLevel = 3)
        val newTeam = Team(id = 62L, name = "New FC", city = "RJ", state = "RJ", division = 1, trainingCenterLevel = 3)
        repository.saveTeams(listOf(oldTeam, newTeam))
        val save = GameSave(currentSeason = 2026, currentWeek = 16, playerTeamId = oldTeam.id)
        repository.saveGameSave(save)
        repository.savePlayers(listOf(
            Player(id = 601L, teamId = oldTeam.id, name = "Moved", age = 24, position = "ZAG", force = 72,
                potential = 82, minutosJogados = 240, mediaNotas = 7.0)
        ))

        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W16")
        val moved = requireNotNull(repository.getPlayer(601L)).copy(teamId = newTeam.id)
        repository.updatePlayers(listOf(moved))

        assertTrue(useCase.commitMonthlyEvolution(plan, allowWeeklyRosterCorrections = true))
        assertEquals(newTeam.id, requireNotNull(repository.getPlayer(601L)).teamId)
    }
}
