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
    fun referencedTeamMarkerIsDerivedAndCannotHideValidCareer() = runBlocking {
        val slotId = "2"
        val team = team(id = 92_002L, name = "Referenciado com marcador antigo", controlled = false)
        val save = GameSave(coachName = "Save Histórico", playerTeamId = team.id)
        val repo = saveRepository.getRepositoryForSlot(slotId)
        repo.saveTeams(listOf(team))
        repo.saveGameSave(save)

        saveRepository.closeAllDatabases()
        val reopened = GameSaveRepository(context, SlotDatabaseFactory(context))
        try {
            // O primeiro open protegido já precisa reconciliar a projeção antes de qualquer
            // consumidor de gameplay poder observar o clube como CPU.
            val reopenedRepository = reopened.getRepositoryForSlot(slotId)
            val repairedTeam = reopenedRepository.getTeam(team.id)
            assertTrue("O clube referenciado pelo GameSave deve ser projetado como controlado", repairedTeam?.isPlayerControlled == true)
            assertEquals(
                listOf(team.id),
                reopenedRepository.getAllTeams().filter { it.isPlayerControlled }.map { it.id }
            )

            val inspection = reopened.inspectSlot(slotId)
            assertEquals(SlotDatabaseState.VALID_CAREER, inspection.state)
            assertFalse(inspection.newGameAllowed)
            assertEquals(save, inspection.save)
            assertEquals(team.name, inspection.teamName)
            assertEquals("A reconciliação da projeção não pode alterar o GameSave", save, reopenedRepository.getGameSave())
        } finally {
            reopened.closeAllDatabases()
        }
    }

    @Test
    fun extraControlledMarkerIsReconciledToAuthoritativeGameSave() = runBlocking {
        val slotId = "3"
        val playerTeam = team(id = 93_003L, name = "Clube do Jogador", controlled = true)
        val staleControlled = team(id = 93_004L, name = "Marcador derivado obsoleto", controlled = true)
        val save = GameSave(coachName = "Controle pelo GameSave", playerTeamId = playerTeam.id)
        val repo = saveRepository.getRepositoryForSlot(slotId)
        repo.saveTeams(listOf(playerTeam, staleControlled))
        repo.saveGameSave(save)

        val inspection = saveRepository.inspectSlot(slotId)
        assertEquals(SlotDatabaseState.VALID_CAREER, inspection.state)
        assertFalse(inspection.newGameAllowed)
        assertEquals(save, inspection.save)
        assertEquals(playerTeam.name, inspection.teamName)
        assertEquals(
            "A projeção deve convergir para exatamente o clube referenciado",
            listOf(playerTeam.id),
            repo.getAllTeams().filter { it.isPlayerControlled }.map { it.id }
        )
        assertFalse(
            "Marcador derivado duplicado deve ser removido sem invalidar a carreira",
            repo.getTeam(staleControlled.id)?.isPlayerControlled == true
        )
        assertEquals("A reconciliação não pode mutar a autoridade da carreira", save, repo.getGameSave())
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
