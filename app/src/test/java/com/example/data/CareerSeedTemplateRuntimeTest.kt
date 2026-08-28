package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SlotDatabaseFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CareerSeedTemplateRuntimeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val slotId = "career_seed_template_runtime_test"
    private val databaseName = SlotDatabaseFactory.databaseNameForSlot(slotId)

    @After
    fun cleanup() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun freshSlotCopiesValidatedBaselineOnlyOnce() = runBlocking {
        context.deleteDatabase(databaseName)
        val factory = SlotDatabaseFactory(context)

        try {
            val firstDb = factory.getDatabaseForSlot(slotId)
            val firstRepo = GameRepository(firstDb)
            val marker = firstRepo.pristineCareerSeedTemplateOrNull()

            assertNotNull(marker)
            marker!!
            assertEquals(APP_DATABASE_SCHEMA_VERSION, marker.schemaVersion)
            assertEquals(CareerSeedTemplateContract.EXPECTED_FC26_ASSET_SHA256, marker.assetSha256)
            assertEquals(2_524, marker.teamCount)
            assertEquals(60_885, marker.playerCount)
            assertEquals(marker.playerCount, firstDb.playerDao().getTotalPlayerCount())
            assertEquals(marker.teamCount, firstRepo.getAllTeams().size)
            assertNull(firstRepo.getGameSave())

            firstRepo.runInTransaction {
                firstRepo.consumePristineCareerSeedTemplate()
            }
            assertNull(firstRepo.pristineCareerSeedTemplateOrNull())

            factory.closeAndRemoveSlot(slotId)

            // O arquivo agora existe fisicamente. Reabrir nunca deve copiar o asset novamente nem
            // restaurar o marker consumido; isso prova que saves existentes não são substituídos.
            val reopenedDb = factory.getDatabaseForSlot(slotId)
            val reopenedRepo = GameRepository(reopenedDb)
            assertNull(reopenedRepo.pristineCareerSeedTemplateOrNull())
            assertEquals(60_885, reopenedDb.playerDao().getTotalPlayerCount())
            assertEquals(2_524, reopenedRepo.getAllTeams().size)
        } finally {
            factory.closeAllDatabases()
        }
    }
}
