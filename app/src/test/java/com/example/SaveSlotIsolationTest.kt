package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.GameSave
import com.example.data.Team
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaveSlotIsolationTest {

    private val context by lazy {
        ApplicationProvider.getApplicationContext<android.content.Context>()
    }

    private lateinit var factory: SlotDatabaseFactory
    private lateinit var saveRepository: GameSaveRepository

    @Before
    fun setup() {
        cleanupDatabase("1")
        cleanupDatabase("2")
        factory = SlotDatabaseFactory(context)
        saveRepository = GameSaveRepository(context, factory)
    }

    @After
    fun tearDown() {
        saveRepository.closeAllDatabases()
        cleanupDatabase("1")
        cleanupDatabase("2")
    }

    @Test
    fun databaseNamesAreCentralizedAndStable() {
        assertEquals("brasfut_retro_database", SlotDatabaseFactory.databaseNameForSlot("1"))
        assertEquals("game_save_slot_2.db", SlotDatabaseFactory.databaseNameForSlot("2"))
        assertEquals("game_save_slot_5.db", SlotDatabaseFactory.databaseNameForSlot("5"))
    }

    @Test
    fun openingAnotherSlotDoesNotCloseThePreviousDatabase() {
        val slot1 = factory.getDatabaseForSlot("1")
        // Room opens the underlying SQLite connection lazily, so force a real open.
        slot1.openHelper.writableDatabase
        assertTrue(slot1.isOpen)

        val slot2 = factory.getDatabaseForSlot("2")
        slot2.openHelper.writableDatabase

        assertNotSame(slot1, slot2)
        assertTrue(slot1.isOpen)
        assertTrue(slot2.isOpen)
    }

    @Test
    fun repositoryInstanceIsStablePerSlotAndDataRemainsIsolated() = runTest {
        val slot1 = saveRepository.getRepositoryForSlot("1")
        val slot1Again = saveRepository.getRepositoryForSlot("1")
        val slot2 = saveRepository.getRepositoryForSlot("2")

        assertSame(slot1, slot1Again)
        assertNotSame(slot1, slot2)

        slot1.saveGameSave(
            GameSave(
                coachName = "Técnico Slot 1",
                playerTeamId = 101L,
                bankBalance = 1_000_000L
            )
        )
        slot1.saveTeams(
            listOf(
                Team(
                    id = 101L,
                    name = "Time Slot 1",
                    city = "Belo Horizonte",
                    state = "MG",
                    country = "Brasil",
                    division = 1,
                    rating = 80
                )
            )
        )

        slot2.saveGameSave(
            GameSave(
                coachName = "Técnico Slot 2",
                playerTeamId = 202L,
                bankBalance = 2_000_000L
            )
        )
        slot2.saveTeams(
            listOf(
                Team(
                    id = 202L,
                    name = "Time Slot 2",
                    city = "São Paulo",
                    state = "SP",
                    country = "Brasil",
                    division = 1,
                    rating = 81
                )
            )
        )

        assertEquals("Técnico Slot 1", slot1.getGameSave()?.coachName)
        assertEquals("Técnico Slot 2", slot2.getGameSave()?.coachName)
        assertEquals(listOf("Time Slot 1"), slot1.getAllTeams().map { it.name })
        assertEquals(listOf("Time Slot 2"), slot2.getAllTeams().map { it.name })
        assertTrue(factory.getDatabaseForSlot("1").isOpen)
        assertTrue(factory.getDatabaseForSlot("2").isOpen)
    }

    @Test
    fun deletingOneSlotDoesNotDeleteOrCloseAnotherSlot() = runTest {
        val slot1 = saveRepository.getRepositoryForSlot("1")
        val slot2 = saveRepository.getRepositoryForSlot("2")

        slot1.saveGameSave(GameSave(coachName = "Mantém", playerTeamId = 1L))
        slot2.saveGameSave(GameSave(coachName = "Apaga", playerTeamId = 2L))

        assertTrue(saveRepository.databaseFileForSlot("1").exists())
        assertTrue(saveRepository.databaseFileForSlot("2").exists())

        assertTrue(saveRepository.deleteSlotDatabase("2"))

        assertFalse(saveRepository.databaseFileForSlot("2").exists())
        assertTrue(saveRepository.databaseFileForSlot("1").exists())
        assertTrue(factory.getDatabaseForSlot("1").isOpen)
        assertEquals("Mantém", slot1.getGameSave()?.coachName)
    }

    private fun cleanupDatabase(slotId: String) {
        context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(slotId))
    }
}
