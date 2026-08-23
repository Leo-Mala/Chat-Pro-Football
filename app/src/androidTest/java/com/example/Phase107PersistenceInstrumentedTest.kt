package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.APP_DATABASE_SCHEMA_VERSION
import com.example.data.Team
import com.example.data.repository.SlotDatabaseState
import com.example.usecase.DatabaseIntegrityUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase107PersistenceInstrumentedTest {

    @Test
    fun fileBackedRoomPersistsReopensAndUsesCurrentSchema() {
        val slotId = "5"
        try {
            Phase107TestSupport.seedCareer(
                slotId = slotId,
                coachName = "Phase 10.7 Room",
                teamName = "Instrumented Room Club",
                teamId = 10_705L
            )

            val saveRepository = Phase107TestSupport.entryPoint().gameSaveRepository()
            val databaseFile = saveRepository.databaseFileForSlot(slotId)
            assertTrue("Room must create a physical SQLite file", databaseFile.isFile)
            assertTrue("SQLite file must be non-empty", databaseFile.length() > 0L)
            assertEquals(APP_DATABASE_SCHEMA_VERSION, Phase107TestSupport.sqliteUserVersion(slotId))
            assertTrue(
                "Unexpected SQLite journal mode",
                Phase107TestSupport.sqliteJournalMode(slotId) in setOf("wal", "delete", "truncate", "persist")
            )

            Phase107TestSupport.closeDatabases()

            val inspection = runBlocking { saveRepository.inspectSlot(slotId) }
            assertEquals(SlotDatabaseState.VALID_CAREER, inspection.state)
            assertEquals("Phase 10.7 Room", inspection.save?.coachName)
            assertEquals(10_705L, inspection.save?.playerTeamId)
            assertEquals("Instrumented Room Club", inspection.teamName)
        } finally {
            Phase107TestSupport.resetSlot(slotId)
        }
    }

    @Test
    fun preCareerSlotDoesNotSelfHealRostersBeforeGameSaveExists() {
        val slotId = "1"
        try {
            Phase107TestSupport.resetSlot(slotId)
            val saveRepository = Phase107TestSupport.entryPoint().gameSaveRepository()
            val repository = saveRepository.getRepositoryForSlot(slotId)

            val report = runBlocking {
                repository.saveTeams(
                    listOf(
                        Team(
                            id = 10_701L,
                            name = "Pre Career Club",
                            city = "Instrumented CI",
                            state = "CI",
                            country = "Brasil",
                            division = 1,
                            rating = 70
                        )
                    )
                )
                DatabaseIntegrityUseCase(repository).repairDatabase()
            }

            assertEquals(0, report.totalTeamsChecked)
            assertEquals(0, report.teamsRepaired)
            assertEquals(0, report.playersAddedCount)
            assertEquals(0L, Phase107TestSupport.sqliteRowCount(slotId, "game_save"))
            assertEquals(0L, Phase107TestSupport.sqliteRowCount(slotId, "players"))

            Phase107TestSupport.closeDatabases()
            val inspection = runBlocking { saveRepository.inspectSlot(slotId) }
            assertEquals(SlotDatabaseState.EMPTY, inspection.state)
        } finally {
            Phase107TestSupport.resetSlot(slotId)
        }
    }

    @Test
    fun validCareerStillSelfHealsAMissingRoster() {
        val slotId = "1"
        try {
            Phase107TestSupport.seedCareer(
                slotId = slotId,
                coachName = "Repair Coach",
                teamName = "Repair Club",
                teamId = 10_711L
            )
            val saveRepository = Phase107TestSupport.entryPoint().gameSaveRepository()
            val repository = saveRepository.getRepositoryForSlot(slotId)
            assertEquals(0L, Phase107TestSupport.sqliteRowCount(slotId, "players"))

            val report = runBlocking { DatabaseIntegrityUseCase(repository).repairDatabase() }

            assertTrue("A valid career with an empty roster must still be repaired", report.teamsRepaired >= 1)
            assertTrue("Roster repair must add players for the controlled club", report.playersAddedCount >= 16)
            assertTrue(Phase107TestSupport.sqliteRowCount(slotId, "players") >= 16L)

            Phase107TestSupport.closeDatabases()
            val inspection = runBlocking { saveRepository.inspectSlot(slotId) }
            assertEquals(SlotDatabaseState.VALID_CAREER, inspection.state)
        } finally {
            Phase107TestSupport.resetSlot(slotId)
        }
    }

    @Test
    fun validDatabaseWithoutMetadataIsRecoveredAndNeverExposedAsEmpty() {
        val slotId = "4"
        try {
            Phase107TestSupport.seedCareer(
                slotId = slotId,
                coachName = "Recovered Coach",
                teamName = "Recovered Club",
                teamId = 10_704L,
                writeMetadata = false,
                week = 17,
                balance = 17_000_704L
            )
            Phase107TestSupport.closeDatabases()

            val entryPoint = Phase107TestSupport.entryPoint()
            val before = runBlocking { entryPoint.gameSaveRepository().inspectSlot(slotId) }
            assertEquals(SlotDatabaseState.VALID_CAREER, before.state)

            val slots = runBlocking { entryPoint.gamePreferencesRepository().loadSaveSlots() }
            val recovered = slots.single { it.id == slotId }
            assertTrue(recovered.exists)
            assertFalse(recovered.recoveryRequired)
            assertEquals("Recovered Coach", recovered.coachName)
            assertEquals("Recovered Club", recovered.teamName)
            assertEquals(17, recovered.week)
            assertEquals(17_000_704L, recovered.balance)

            Phase107TestSupport.closeDatabases()
            val reopened = runBlocking { entryPoint.gameSaveRepository().inspectSlot(slotId) }
            assertEquals(SlotDatabaseState.VALID_CAREER, reopened.state)
            assertEquals("Recovered Coach", reopened.save?.coachName)
        } finally {
            Phase107TestSupport.resetSlot(slotId)
        }
    }

    @Test
    fun metadataWithoutDatabaseIsSanitizedAsGhostSave() {
        val slotId = "3"
        try {
            Phase107TestSupport.resetSlot(slotId)
            val entryPoint = Phase107TestSupport.entryPoint()
            val saveRepository = entryPoint.gameSaveRepository()
            runBlocking {
                entryPoint.gamePreferencesRepository().updateSlotMetadata(
                    saveId = slotId,
                    coachName = "Ghost Coach",
                    teamName = "Ghost Club",
                    season = 2026,
                    week = 4,
                    balance = 400L
                )
            }
            assertFalse(saveRepository.databaseFileForSlot(slotId).exists())

            val first = runBlocking { entryPoint.gamePreferencesRepository().loadSaveSlots() }
                .single { it.id == slotId }
            assertFalse(first.exists)
            assertFalse(first.recoveryRequired)

            val second = runBlocking { entryPoint.gamePreferencesRepository().loadSaveSlots() }
                .single { it.id == slotId }
            assertFalse(second.exists)
            assertFalse(second.recoveryRequired)
            assertFalse(saveRepository.databaseFileForSlot(slotId).exists())
        } finally {
            Phase107TestSupport.resetSlot(slotId)
        }
    }

    @Test
    fun independentSlotsRemainIsolatedAcrossPhysicalReopen() {
        val slotA = "2"
        val slotB = "3"
        try {
            Phase107TestSupport.seedCareer(slotA, "Coach A", "Club A", 10_702L)
            Phase107TestSupport.seedCareer(slotB, "Coach B", "Club B", 10_703L)
            Phase107TestSupport.closeDatabases()

            val repository = Phase107TestSupport.entryPoint().gameSaveRepository()
            val a = runBlocking { repository.inspectSlot(slotA) }
            val b = runBlocking { repository.inspectSlot(slotB) }

            assertEquals(SlotDatabaseState.VALID_CAREER, a.state)
            assertEquals(SlotDatabaseState.VALID_CAREER, b.state)
            assertEquals("Coach A", a.save?.coachName)
            assertEquals("Coach B", b.save?.coachName)
            assertEquals(10_702L, a.save?.playerTeamId)
            assertEquals(10_703L, b.save?.playerTeamId)
            assertTrue(repository.databaseFileForSlot(slotA).canonicalPath != repository.databaseFileForSlot(slotB).canonicalPath)
        } finally {
            Phase107TestSupport.resetSlot(slotA)
            Phase107TestSupport.resetSlot(slotB)
        }
    }
}
