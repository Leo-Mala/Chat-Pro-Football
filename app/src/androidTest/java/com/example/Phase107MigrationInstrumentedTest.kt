package com.example

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.APP_DATABASE_SCHEMA_VERSION
import com.example.data.AppDatabase
import com.example.data.migrations.MIGRATION_21_22
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
    fun migration21To22RunsOnAndroidSQLiteAndProducesTheExportedV22Schema() {
        val databaseName = "phase107_migration_21_22.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)

        try {
            migrationHelper.createDatabase(databaseName, 21).close()

            val migrated = migrationHelper.runMigrationsAndValidate(
                databaseName,
                APP_DATABASE_SCHEMA_VERSION,
                true,
                MIGRATION_21_22
            )
            migrationHelper.closeWhenFinished(migrated)

            val userVersion = migrated.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
            assertEquals(APP_DATABASE_SCHEMA_VERSION, userVersion)

            val indexes = mutableSetOf<String>()
            migrated.query("PRAGMA index_list(`historico_evolucao`)").use { cursor ->
                while (cursor.moveToNext()) {
                    indexes += cursor.getString(1)
                }
            }
            assertTrue(
                "Migration 21->22 must create the evolution date index on Android SQLite",
                "index_historico_evolucao_data" in indexes
            )
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
