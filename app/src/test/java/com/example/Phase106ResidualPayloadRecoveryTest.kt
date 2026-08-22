package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GameRepository
import com.example.data.Team
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.data.repository.SlotDatabaseState
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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase106ResidualPayloadRecoveryTest {

    private lateinit var context: Context
    private lateinit var factory: SlotDatabaseFactory
    private lateinit var saveRepository: GameSaveRepository
    private val slotId = "4"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))
        factory = SlotDatabaseFactory(context)
        saveRepository = GameSaveRepository(context, factory)
    }

    @After
    fun tearDown() {
        saveRepository.closeAllDatabases()
        context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))
    }

    @Test
    fun databaseWithDomainPayloadButNoGameSaveRequiresRecovery() = runBlocking {
        val repository: GameRepository = saveRepository.getRepositoryForSlot(slotId)
        val orphanedTeam = Team(
            id = 96_004L,
            name = "Payload Recuperável",
            city = "Belo Horizonte",
            state = "MG",
            country = "Brasil",
            division = 1,
            rating = 80
        )
        repository.saveTeams(listOf(orphanedTeam))
        assertEquals(null, repository.getGameSave())

        val inspection = saveRepository.inspectSlot(slotId)

        assertEquals(
            "Payload sem GameSave é ambíguo e deve falhar fechado",
            SlotDatabaseState.RECOVERY_REQUIRED,
            inspection.state
        )
        assertEquals("ResidualDataWithoutGameSave", inspection.failureReason)
        assertFalse("Novo Jogo não pode apagar payload potencialmente recuperável", inspection.newGameAllowed)
        assertFalse(saveRepository.isNewGameAllowed(slotId))
        assertTrue(saveRepository.databaseFileForSlot(slotId).exists())
        assertNotNull(
            "A inspeção não pode apagar os dados residuais",
            saveRepository.getRepositoryForSlot(slotId).getTeam(orphanedTeam.id)
        )
    }
}
