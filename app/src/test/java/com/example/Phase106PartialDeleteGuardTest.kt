package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ExistingCareerOverwriteBlockedException
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Team
import com.example.data.local.SlotDatabaseFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase106PartialDeleteGuardTest {

    private lateinit var context: Context
    private lateinit var factory: SlotDatabaseFactory
    private lateinit var repository: GameRepository
    private val slotId = "5"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))
        factory = SlotDatabaseFactory(context)
        repository = GameRepository(factory.getDatabaseForSlot(slotId))
    }

    @After
    fun tearDown() {
        factory.closeAllDatabases()
        context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))
    }

    @Test
    fun existingCareerCannotBePartiallyDeletedByNewGameResetChain() = runBlocking {
        val team = Team(
            id = 95_005L,
            name = "Carreira Protegida",
            city = "Belo Horizonte",
            state = "MG",
            country = "Brasil",
            division = 1,
            rating = 82,
            isPlayerControlled = true
        )
        val save = GameSave(
            coachName = "Técnico Protegido",
            currentWeek = 23,
            currentSeason = 2038,
            playerTeamId = team.id,
            bankBalance = 123_456_789L
        )
        repository.saveTeams(listOf(team))
        repository.saveGameSave(save)

        var blocked: ExistingCareerOverwriteBlockedException? = null
        try {
            repository.withTransaction {
                repository.deleteSave()
                repository.deleteTeams()
                repository.deletePlayers()
                repository.deleteFixtures()
            }
        } catch (e: ExistingCareerOverwriteBlockedException) {
            blocked = e
        }

        assertTrue("A exclusão parcial de carreira existente deve ser fail-closed", blocked != null)
        assertEquals("Guard deve explicar quantas linhas bloquearam o reset", 1, blocked?.gameSaveRowCount)
        assertEquals("GameSave original deve sobreviver integralmente", save, repository.getGameSave())
        assertEquals("Clube original não pode ser removido", team, repository.getTeam(team.id))
    }

    @Test
    fun semanticallyEmptyDatabaseStillAllowsResetNoOpForNewGame() = runBlocking {
        assertNull(repository.getGameSave())
        repository.deleteSave()
        assertNull(repository.getGameSave())
    }
}
