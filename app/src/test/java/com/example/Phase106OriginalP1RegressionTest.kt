package com.example

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.data.GamePreferencesRepository
import com.example.data.GameSave
import com.example.data.Team
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.data.repository.SlotDatabaseState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Gate principal da Fase 10.6: reproduz e fecha de ponta a ponta o P1 original. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase106OriginalP1RegressionTest {

    private lateinit var context: Context
    private lateinit var factory: SlotDatabaseFactory
    private lateinit var saveRepository: GameSaveRepository
    private lateinit var preferencesRepository: GamePreferencesRepository
    private val slotId = "2"

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        clearMetadata()
        context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))
        reopenRepositories()
    }

    @After
    fun tearDown() {
        runBlocking {
            saveRepository.closeAllDatabases()
            clearMetadata()
            context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))
        }
    }

    @Test
    fun validRoomCareerWithoutMetadataNeverBecomesDestructivelyEmpty() = runBlocking {
        val team = Team(
            id = 97_002L,
            name = "Clube P1 Original",
            city = "Belo Horizonte",
            state = "MG",
            country = "Brasil",
            division = 1,
            rating = 84
        )
        val originalSave = GameSave(
            coachName = "Técnico P1 Original",
            currentWeek = 31,
            currentSeason = 2039,
            playerTeamId = team.id,
            bankBalance = 187_654_321L
        )

        val originalRepository = saveRepository.getRepositoryForSlot(slotId)
        originalRepository.saveTeams(listOf(team))
        originalRepository.saveGameSave(originalSave)
        preferencesRepository.updateSlotMetadata(
            saveId = slotId,
            coachName = originalSave.coachName,
            teamName = team.name,
            season = originalSave.currentSeason,
            week = originalSave.currentWeek,
            balance = originalSave.bankBalance
        )

        // P1 original: Room já está commitado, mas metadata some antes do próximo processo.
        clearMetadata()
        reopenRepositories()

        // 1) A listagem precisa recuperar pelo Room e nunca apresentar slot vazio.
        val recoveredSlot = preferencesRepository.loadSaveSlots().single { it.id == slotId }
        assertTrue("DB válido sem metadata não pode virar slot vazio", recoveredSlot.exists)
        assertFalse(recoveredSlot.recoveryRequired)
        assertEquals(originalSave.coachName, recoveredSlot.coachName)
        assertEquals(team.name, recoveredSlot.teamName)

        val inspection = saveRepository.inspectSlot(slotId)
        assertEquals(SlotDatabaseState.VALID_CAREER, inspection.state)
        assertFalse("Novo Jogo deve ser bloqueado antes de qualquer limpeza", inspection.newGameAllowed)
        assertFalse(saveRepository.isNewGameAllowed(slotId))

        // 2) Defesa em profundidade: mesmo que uma chamada contorne a UI/preflight e tente a
        // primeira cadeia destrutiva do Novo Jogo, deleteSave() aborta a transação inteira.
        val recoveredRepository = saveRepository.getRepositoryForSlot(slotId)
        var destructiveResetBlocked = false
        try {
            recoveredRepository.withTransaction {
                recoveredRepository.deleteSave()
                recoveredRepository.deleteTeams()
                recoveredRepository.deletePlayers()
                recoveredRepository.deleteFixtures()
            }
        } catch (_: CancellationException) {
            destructiveResetBlocked = true
        }
        assertTrue("A cadeia destrutiva de Novo Jogo deve abortar", destructiveResetBlocked)

        // 3) A metadata deve ter sido reconstruída e a carreira deve permanecer byte-semanticamente
        // equivalente nos campos autoritativos após nova reabertura.
        assertTrue(
            context.dataStore.data.first()[booleanPreferencesKey("slot_${slotId}_exists")] == true
        )
        assertEquals(originalSave, recoveredRepository.getGameSave())
        assertEquals(team, recoveredRepository.getTeam(team.id))

        reopenRepositories()
        val reopenedSlot = preferencesRepository.loadSaveSlots().single { it.id == slotId }
        val reopenedRepository = saveRepository.getRepositoryForSlot(slotId)

        assertTrue(reopenedSlot.exists)
        assertFalse(reopenedSlot.recoveryRequired)
        assertEquals(originalSave.coachName, reopenedSlot.coachName)
        assertEquals(originalSave.currentSeason, reopenedSlot.season)
        assertEquals(originalSave.currentWeek, reopenedSlot.week)
        assertEquals(originalSave.bankBalance, reopenedSlot.balance)
        assertEquals(originalSave, reopenedRepository.getGameSave())
        assertNotNull(reopenedRepository.getTeam(team.id))
        assertEquals(team, reopenedRepository.getTeam(team.id))
    }

    private suspend fun clearMetadata() {
        context.dataStore.edit { it.clear() }
        context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun reopenRepositories() {
        if (::saveRepository.isInitialized) {
            saveRepository.closeAllDatabases()
        }
        factory = SlotDatabaseFactory(context)
        saveRepository = GameSaveRepository(context, factory)
        preferencesRepository = GamePreferencesRepository(context.dataStore, context, saveRepository)
    }
}
