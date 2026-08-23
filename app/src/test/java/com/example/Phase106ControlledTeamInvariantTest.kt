package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GameSave
import com.example.data.Team
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.data.repository.SlotDatabaseState
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
@Config(manifest = Config.NONE)
class Phase106ControlledTeamInvariantTest {

    private lateinit var context: Context
    private lateinit var saveRepository: GameSaveRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf("2", "3", "4").forEach(::clearSlot)
        saveRepository = GameSaveRepository(context, SlotDatabaseFactory(context))
    }

    @After
    fun tearDown() {
        saveRepository.closeAllDatabases()
        listOf("2", "3", "4").forEach(::clearSlot)
    }

    @Test
    fun referencedTeamMustItselfBeMarkedControlled() = runBlocking {
        val slotId = "2"
        val team = team(id = 92_002L, name = "Referenciado mas CPU", controlled = false)
        val repo = saveRepository.getRepositoryForSlot(slotId)
        repo.saveTeams(listOf(team))
        repo.saveGameSave(GameSave(coachName = "Restore Parcial", playerTeamId = team.id))

        val inspection = saveRepository.inspectSlot(slotId)
        assertEquals(SlotDatabaseState.RECOVERY_REQUIRED, inspection.state)
        assertFalse(inspection.newGameAllowed)
        assertTrue(inspection.failureReason?.contains("PlayerTeamNotControlled") == true)
        assertEquals("O preflight não pode reparar silenciosamente o marcador", team, repo.getTeam(team.id))
    }

    @Test
    fun referencedControlledTeamMustBeTheOnlyControlledTeam() = runBlocking {
        val slotId = "3"
        val playerTeam = team(id = 93_003L, name = "Clube do Jogador", controlled = true)
        val duplicateControlled = team(id = 93_004L, name = "Segundo Controlado", controlled = true)
        val repo = saveRepository.getRepositoryForSlot(slotId)
        repo.saveTeams(listOf(playerTeam, duplicateControlled))
        repo.saveGameSave(GameSave(coachName = "Controle Ambíguo", playerTeamId = playerTeam.id))

        val inspection = saveRepository.inspectSlot(slotId)
        assertEquals(SlotDatabaseState.RECOVERY_REQUIRED, inspection.state)
        assertFalse(inspection.newGameAllowed)
        assertTrue(inspection.failureReason?.contains("ControlledTeamInvariantMismatch") == true)
        assertEquals(2, repo.getAllTeams().count { it.isPlayerControlled })
    }

    @Test
    fun exactlyOneControlledReferencedTeamIsValidCareer() = runBlocking {
        val slotId = "4"
        val playerTeam = team(id = 94_004L, name = "Controle Canônico", controlled = true)
        val cpuTeam = team(id = 94_005L, name = "CPU", controlled = false)
        val save = GameSave(coachName = "Carreira Canônica", playerTeamId = playerTeam.id)
        val repo = saveRepository.getRepositoryForSlot(slotId)
        repo.saveTeams(listOf(playerTeam, cpuTeam))
        repo.saveGameSave(save)

        val inspection = saveRepository.inspectSlot(slotId)
        assertEquals(SlotDatabaseState.VALID_CAREER, inspection.state)
        assertEquals(save, inspection.save)
        assertEquals(playerTeam.name, inspection.teamName)
        assertFalse(inspection.newGameAllowed)
        assertEquals(listOf(playerTeam.id), repo.getAllTeams().filter { it.isPlayerControlled }.map { it.id })
    }

    private fun team(id: Long, name: String, controlled: Boolean): Team = Team(
        id = id,
        name = name,
        city = "Belo Horizonte",
        state = "MG",
        country = "Brasil",
        division = 1,
        rating = 80,
        isPlayerControlled = controlled
    )

    private fun clearSlot(slotId: String) {
        val name = SlotDatabaseFactory.databaseNameForSlot(slotId)
        context.deleteDatabase(name)
    }
}
