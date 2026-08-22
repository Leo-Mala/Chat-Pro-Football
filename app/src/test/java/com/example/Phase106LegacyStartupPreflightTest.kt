package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.local.SlotDatabaseFactory
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase106LegacyStartupPreflightTest {

    private lateinit var context: Context
    private lateinit var databaseFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val name = SlotDatabaseFactory.LEGACY_SLOT_1_DATABASE_NAME
        databaseFile = context.getDatabasePath(name)
        clearArtifacts()
        databaseFile.parentFile?.mkdirs()
    }

    @After
    fun tearDown() {
        clearArtifacts()
    }

    @Test
    fun zeroLengthLegacyDatabaseIsRejectedBeforeRoomCanMaterializeIt() {
        databaseFile.writeBytes(byteArrayOf())
        val before = databaseFile.readBytes()

        var blocked = false
        try {
            AppDatabase.getDatabaseWithName(context, SlotDatabaseFactory.LEGACY_SLOT_1_DATABASE_NAME)
        } catch (_: IllegalStateException) {
            blocked = true
        }

        assertTrue("Startup legado deve falhar fechado para DB 0-byte", blocked)
        assertTrue("Artefato truncado precisa ser preservado", databaseFile.exists())
        assertTrue(databaseFile.readBytes().contentEquals(before))
        assertTrue("Room não pode materializar schema sobre arquivo truncado", databaseFile.length() == 0L)
    }

    @Test
    fun orphanedLegacySidecarIsRejectedBeforeMainDatabaseCreation() {
        val wal = File(databaseFile.path + "-wal")
        val originalBytes = "legacy-partial-restore-wal".toByteArray()
        wal.writeBytes(originalBytes)

        var blocked = false
        try {
            AppDatabase.getDatabaseWithName(context, SlotDatabaseFactory.LEGACY_SLOT_1_DATABASE_NAME)
        } catch (_: IllegalStateException) {
            blocked = true
        }

        assertTrue("Startup legado deve falhar fechado para sidecar órfão", blocked)
        assertFalse("Room não pode criar o arquivo principal", databaseFile.exists())
        assertTrue("Sidecar precisa permanecer recuperável", wal.exists())
        assertTrue(wal.readBytes().contentEquals(originalBytes))
    }

    @Test
    fun validLegacyDatabaseIsAlreadyOpenWhenGuardedFactoryReturns() {
        val name = SlotDatabaseFactory.LEGACY_SLOT_1_DATABASE_NAME

        // Materializa uma base canônica sem passar pelo método que está sob teste.
        val prepared = AppDatabase.buildDatabaseWithName(context, name)
        prepared.openHelper.writableDatabase
        prepared.close()
        assertTrue(databaseFile.exists())
        assertTrue(databaseFile.length() > 0L)

        val opened = AppDatabase.getDatabaseWithName(context, name)
        try {
            assertTrue(
                "Factory legado deve forçar o primeiro open antes de devolver o Room",
                opened.isOpen
            )
            assertTrue(
                "Handle SQLite precisa estar disponível imediatamente no retorno",
                opened.openHelper.writableDatabase.isOpen
            )
        } finally {
            opened.close()
        }
    }

    private fun clearArtifacts() {
        val name = SlotDatabaseFactory.LEGACY_SLOT_1_DATABASE_NAME
        context.deleteDatabase(name)
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            File(context.getDatabasePath(name).path + suffix).delete()
        }
        context.getDatabasePath(name).delete()
    }
}
