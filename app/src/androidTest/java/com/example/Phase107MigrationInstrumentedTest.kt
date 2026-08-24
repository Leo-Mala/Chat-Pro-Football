package com.example

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.APP_DATABASE_SCHEMA_VERSION
import com.example.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase107MigrationInstrumentedTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun everySupportedMigrationEdgeAndRetainedSchemaReachCurrentOnRealAndroidSQLite() {
        val currentVersion = APP_DATABASE_SCHEMA_VERSION
        val minimumVersion = AppDatabase.MINIMUM_AUTOMATICALLY_MIGRATABLE_VERSION
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(minimumVersion < currentVersion)
        assertTrue(FIRST_RETAINED_EXPORTED_SCHEMA_VERSION in minimumVersion until currentVersion)

        // ALL_MIGRATIONS must be exactly the supported consecutive chain. Duplicate starts,
        // shortcut edges (for example 14 -> current) and unrelated outgoing edges are forbidden.
        // This verifies the same list Room receives in production rather than an associateBy view
        // that could silently hide an earlier shortcut migration.
        val actualEdges = AppDatabase.ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        val expectedEdges = (minimumVersion until currentVersion).map { start ->
            start to (start + 1)
        }
        assertEquals("Room migration registry must contain exactly one consecutive edge per supported version", expectedEdges, actualEdges)
        assertEquals(
            "Room migration registry contains duplicate start versions",
            actualEdges.size,
            actualEdges.map { it.first }.distinct().size
        )

        // For every historical schema JSON actually retained by the repository, exercise Room's
        // real path selection on Android SQLite with the complete production migration registry.
        for (startVersion in FIRST_RETAINED_EXPORTED_SCHEMA_VERSION until currentVersion) {
            val databaseName = "supported_android_migration_${startVersion}_${currentVersion}.db"
            context.deleteDatabase(databaseName)
            try {
                migrationHelper.createDatabase(databaseName, startVersion).close()

                val migrated = migrationHelper.runMigrationsAndValidate(
                    databaseName,
                    currentVersion,
                    true,
                    *AppDatabase.ALL_MIGRATIONS
                )
                try {
                    val userVersion = migrated.query("PRAGMA user_version").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        cursor.getInt(0)
                    }
                    assertEquals(currentVersion, userVersion)
                    migrated.query("PRAGMA integrity_check").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("ok", cursor.getString(0))
                    }
                } finally {
                    migrated.close()
                }
            } finally {
                context.deleteDatabase(databaseName)
            }
        }
    }

    private companion object {
        // V17 is the oldest exported AppDatabase JSON retained in app/schemas. This is historical
        // fixture availability, not a pin of the current Room version.
        const val FIRST_RETAINED_EXPORTED_SCHEMA_VERSION = 17
    }
}
