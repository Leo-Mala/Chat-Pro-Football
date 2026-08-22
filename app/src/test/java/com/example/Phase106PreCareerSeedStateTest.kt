package com.example

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.data.GamePreferencesRepository
import com.example.data.Player
import com.example.data.Team
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.data.repository.SlotDatabaseState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase106PreCareerSeedStateTest {

    private lateinit var context: Context
    private lateinit var factory: SlotDatabaseFactory
    private lateinit var saveRepository: GameSaveRepository
    private lateinit var preferencesRepository: GamePreferencesRepository
    private val slotId = "4"

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.dataStore.edit { it.clear() }
        context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))
        factory = SlotDatabaseFactory(context)
        saveRepository = GameSaveRepository(context, factory)
        preferencesRepository = GamePreferencesRepository(context.dataStore, context, saveRepository)
    }

    @After
    fun tearDown() = runBlocking {
        saveRepository.closeAllDatabases()
        context.dataStore.edit { it.clear() }
        context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))
    }

    @Test
    fun seededDatabaseWithoutGameSaveIsPreCareerEmptyAndGhostMetadataIsSanitized() = runBlocking {
        val repository = saveRepository.getRepositoryForSlot(slotId)
        val team = Team(
            id = 96_104L,
            name = "Pré-Carreira",
            city = "Belo Horizonte",
            state = "MG",
            country = "Brasil",
            division = 1,
            rating = 80
        )
        val player = Player(
            id = 96_104_001L,
            teamId = team.id,
            name = "Jogador Pré-Carreira",
            age = 23,
            position = "MEI",
            force = 75
        )
        repository.saveTeams(listOf(team))
        repository.savePlayers(listOf(player))
        assertNull(repository.getGameSave())

        // Simula metadata restaurada/inconsistente sobre um banco apenas inicializado para a UI.
        preferencesRepository.updateSlotMetadata(
            saveId = slotId,
            coachName = "Fantasma",
            teamName = team.name,
            season = 2030,
            week = 7,
            balance = 99_000_000L
        )

        val inspection = saveRepository.inspectSlot(slotId)
        assertEquals(SlotDatabaseState.EMPTY, inspection.state)
        assertTrue("Sem GameSave não existe carreira autoritativa", inspection.newGameAllowed)
        assertTrue(saveRepository.isNewGameAllowed(slotId))

        val slot = preferencesRepository.loadSaveSlots().single { it.id == slotId }
        assertFalse("Metadata sem GameSave não pode criar carreira fantasma", slot.exists)
        assertFalse(slot.recoveryRequired)

        // A reconciliação só remove a projeção fantasma; não destrói o seed pré-carreira.
        assertTrue(saveRepository.databaseFileForSlot(slotId).exists())
        assertNotNull(repository.getTeam(team.id))
        assertNotNull(repository.getPlayer(player.id))
        assertNull(repository.getGameSave())
    }
}
