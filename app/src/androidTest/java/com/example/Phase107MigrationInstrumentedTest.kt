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
    fun previousSchemaMigratesToCurrentSchemaOnRealAndroidSQLite() {
        val currentVersion = APP_DATABASE_SCHEMA_VERSION
        val previousVersion = currentVersion - 1
        val databaseName = "current_android_migration_${previousVersion}_${currentVersion}.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)

        try {
            migrationHelper.createDatabase(databaseName, previousVersion).close()

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
