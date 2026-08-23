package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.data.repository.SlotDatabaseState
import com.example.data.repository.SlotRecoveryRequiredException
import java.io.File
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
class Phase106UninitializedSqliteRecoveryTest {

    private lateinit var context: Context
    private lateinit var factory: SlotDatabaseFactory
    private lateinit var saveRepository: GameSaveRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearSlot("1")
        clearSlot("3")
        factory = SlotDatabaseFactory(context)
        saveRepository = GameSaveRepository(context, factory)
    }

    @After
    fun tearDown() {
        saveRepository.closeAllDatabases()
        clearSlot("1")
        clearSlot("3")
    }

    @Test
    fun nonEmptySqliteWithUserVersionZeroIsRecoveryAndNeverMaterializedByRoom() = runBlocking {
        val slotId = "3"
        val file = saveRepository.databaseFileForSlot(slotId)
        createUninitializedSqlite(file)

        assertTrue(file.exists())
        assertTrue(file.length() > 0L)
        assertEquals(0, readUserVersion(file))
        assertTrue(tableExists(file, "restore_sentinel"))
        assertFalse(tableExists(file, "game_save"))

        val inspection = saveRepository.inspectSlot(slotId)
        assertEquals(SlotDatabaseState.RECOVERY_REQUIRED, inspection.state)
        assertFalse("SQLite não inicializado nunca pode liberar Novo Jogo", inspection.newGameAllowed)
        assertTrue(
            inspection.failureReason?.contains("UnsupportedOrUninitializedSchemaVersion:0") == true
        )

        var blocked = false
        try {
            saveRepository.getRepositoryForSlot(slotId)
        } catch (_: SlotRecoveryRequiredException) {
            blocked = true
        }

        assertTrue("Primeira abertura de Room precisa ser bloqueada antes do onCreate", blocked)
        assertEquals("Preflight não pode mudar user_version", 0, readUserVersion(file))
        assertTrue("Sentinela do restore parcial precisa ser preservada", tableExists(file, "restore_sentinel"))
        assertFalse("Room não pode materializar schema sobre restore parcial", tableExists(file, "game_save"))
        assertTrue(file.exists())

        assertTrue("Somente delete explícito pode liberar o slot", saveRepository.deleteSlotDatabase(slotId))
        assertEquals(SlotDatabaseState.MISSING, saveRepository.inspectSlot(slotId).state)
    }

    @Test
    fun legacyStartupRejectsUserVersionZeroBeforeRoomOnCreate() {
        val name = SlotDatabaseFactory.LEGACY_SLOT_1_DATABASE_NAME
        val file = context.getDatabasePath(name)
        createUninitializedSqlite(file)

        assertEquals(0, readUserVersion(file))
        assertFalse(tableExists(file, "game_save"))

        var blocked = false
        try {
            AppDatabase.getDatabaseWithName(context, name)
        } catch (_: IllegalStateException) {
            blocked = true
        }

        assertTrue("Startup legado deve falhar fechado para SQLite user_version=0", blocked)
        assertTrue(file.exists())
        assertEquals("Startup legado não pode promover o arquivo para V22", 0, readUserVersion(file))
        assertTrue(tableExists(file, "restore_sentinel"))
        assertFalse("Startup legado não pode executar Room onCreate", tableExists(file, "game_save"))
    }

    private fun createUninitializedSqlite(file: File) {
        file.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            database.execSQL("CREATE TABLE restore_sentinel(id INTEGER PRIMARY KEY, marker TEXT NOT NULL)")
            database.execSQL("INSERT INTO restore_sentinel(marker) VALUES ('phase-10.6-partial-restore')")
            assertEquals(0, database.version)
        } finally {
            database.close()
        }
    }

    private fun readUserVersion(file: File): Int {
        val database = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            database.rawQuery("PRAGMA user_version", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
        } finally {
            database.close()
        }
    }

    private fun tableExists(file: File, tableName: String): Boolean {
        val database = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            database.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
                arrayOf(tableName)
            ).use { cursor -> cursor.moveToFirst() }
        } finally {
            database.close()
        }
    }

    private fun clearSlot(slotId: String) {
        val name = SlotDatabaseFactory.databaseNameForSlot(slotId)
        val file = context.getDatabasePath(name)
        context.deleteDatabase(name)
        listOf("-wal", "-shm", "-journal").forEach { suffix -> File(file.path + suffix).delete() }
        file.delete()
    }
}
