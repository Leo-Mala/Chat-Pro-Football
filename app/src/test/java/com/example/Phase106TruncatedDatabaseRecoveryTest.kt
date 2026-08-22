package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class Phase106TruncatedDatabaseRecoveryTest {

    private lateinit var context: Context
    private lateinit var factory: SlotDatabaseFactory
    private lateinit var saveRepository: GameSaveRepository
    private val slotId = "3"

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
    fun zeroLengthDatabaseIsNeverOpenedOrClassifiedAsEmpty() = runBlocking {
        val file = saveRepository.databaseFileForSlot(slotId)
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf())
        assertTrue(file.exists())
        assertEquals(0L, file.length())

        val inspection = saveRepository.inspectSlot(slotId)

        assertEquals(SlotDatabaseState.RECOVERY_REQUIRED, inspection.state)
        assertEquals("ZeroLengthDatabaseFile", inspection.failureReason)
        assertFalse("Arquivo truncado não pode autorizar Novo Jogo", inspection.newGameAllowed)
        assertFalse(saveRepository.isNewGameAllowed(slotId))
        assertTrue("A inspeção deve preservar o artefato truncado", file.exists())
        assertEquals("Room/SQLite não pode recriar silenciosamente o arquivo", 0L, file.length())
    }
}
