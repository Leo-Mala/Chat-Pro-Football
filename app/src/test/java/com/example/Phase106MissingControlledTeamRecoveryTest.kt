package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.APP_DATABASE_SCHEMA_VERSION
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.data.repository.SlotDatabaseState
import com.example.data.repository.SlotRecoveryRequiredException
import java.io.File
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
class Phase106MissingControlledTeamRecoveryTest {

    private lateinit var context: Context
    private lateinit var saveRepository: GameSaveRepository
    private val slotId = "2"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearSlot()
        saveRepository = GameSaveRepository(context, SlotDatabaseFactory(context))
    }

    @After
    fun tearDown() {
        saveRepository.closeAllDatabases()
        clearSlot()
    }

    @Test
    fun canonicalGameSaveWhoseReferencedTeamIsMissingRequiresRecoveryBeforeSeed() = runBlocking {
        val name = SlotDatabaseFactory.databaseNameForSlot(slotId)
        val missingTeamId = 987_654_321L

        // Constrói intencionalmente um Room no schema atual, parcial, por fora do repositório protegido.
        val partialDatabase = AppDatabase.buildDatabaseWithName(context, name)
        try {
            partialDatabase.openHelper.writableDatabase
            GameRepository(partialDatabase).saveGameSave(
                GameSave(
                    coachName = "Carreira Parcial",
                    currentSeason = 2034,
                    currentWeek = 9,
                    playerTeamId = missingTeamId,
                    bankBalance = 77_000_000L
                )
            )
        } finally {
            partialDatabase.close()
        }

        val file = saveRepository.databaseFileForSlot(slotId)
        assertTrue(file.exists())
        assertEquals(APP_DATABASE_SCHEMA_VERSION, readUserVersion(file))
        assertEquals(1, countRows(file, "game_save"))
        assertEquals(0, countRows(file, "teams"))

        val inspection = saveRepository.inspectSlot(slotId)
        assertEquals(SlotDatabaseState.RECOVERY_REQUIRED, inspection.state)
        assertFalse("GameSave sem o clube referenciado não é uma carreira jogável", inspection.newGameAllowed)
        assertTrue(inspection.failureReason?.contains("MissingReferencedTeam:playerTeamId=$missingTeamId") == true)

        var blocked = false
        try {
            saveRepository.getRepositoryForSlot(slotId)
        } catch (_: SlotRecoveryRequiredException) {
            blocked = true
        }
        assertTrue("Abertura precisa ser bloqueada antes de qualquer seed/reparo", blocked)

        // Nenhuma tentativa de abertura pode reconstruir silenciosamente o clube ausente.
        assertEquals(1, countRows(file, "game_save"))
        assertEquals(0, countRows(file, "teams"))
        assertNotNull(file)
        assertTrue(file.exists())
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

    private fun countRows(file: File, table: String): Int {
        require(table == "game_save" || table == "teams")
        val database = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            database.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
        } finally {
            database.close()
        }
    }

    private fun clearSlot() {
        val name = SlotDatabaseFactory.databaseNameForSlot(slotId)
        val file = context.getDatabasePath(name)
        context.deleteDatabase(name)
        listOf("-wal", "-shm", "-journal").forEach { suffix -> File(file.path + suffix).delete() }
        file.delete()
    }
}
