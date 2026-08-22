package com.example

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.example.data.GamePreferencesRepository
import com.example.data.GameSave
import com.example.data.Team
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.data.repository.SlotDatabaseState
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase106SaveRecoverySafetyTest {

    private lateinit var context: Context
    private lateinit var databaseFactory: SlotDatabaseFactory
    private lateinit var saveRepository: GameSaveRepository
    private lateinit var preferencesRepository: GamePreferencesRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        clearMetadata()
        (1..5).forEach { context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(it.toString())) }
        reopenRepositories()
    }

    @After
    fun tearDown() = runBlocking {
        saveRepository.closeAllDatabases()
        clearMetadata()
        (1..5).forEach { context.deleteDatabase(SlotDatabaseFactory.databaseNameForSlot(it.toString())) }
    }

    @Test
    fun metadataAndValidDatabaseRemainNormalSave() = runBlocking {
        val expected = createCareer(
            slotId = "1",
            coachName = "Carreira Normal",
            teamId = 9_001L,
            teamName = "Clube Normal",
            season = 2030,
            week = 8,
            balance = 81_000_000L
        )
        preferencesRepository.updateSlotMetadata(
            saveId = "1",
            coachName = expected.coachName,
            teamName = "Clube Normal",
            season = expected.currentSeason,
            week = expected.currentWeek,
            balance = expected.bankBalance
        )

        val slot = preferencesRepository.loadSaveSlots().single { it.id == "1" }
        assertTrue(slot.exists)
        assertFalse(slot.recoveryRequired)
        assertEquals(expected.coachName, slot.coachName)
        assertEquals("Clube Normal", slot.teamName)
        assertFalse(saveRepository.isNewGameAllowed("1"))
    }

    @Test
    fun noMetadataAndNoDatabaseIsTrulyEmpty() = runBlocking {
        val inspection = saveRepository.inspectSlot("5")
        assertEquals(SlotDatabaseState.MISSING, inspection.state)
        assertTrue(inspection.newGameAllowed)

        val slot = preferencesRepository.loadSaveSlots().single { it.id == "5" }
        assertFalse(slot.exists)
        assertFalse(slot.recoveryRequired)
        assertTrue(saveRepository.isNewGameAllowed("5"))
    }

    @Test
    fun validDatabaseWithoutMetadataIsRecoveredAndProjectionIsRebuiltAfterReopen() = runBlocking {
        val expected = createCareer(
            slotId = "2",
            coachName = "Carreira Recuperável",
            teamId = 9_002L,
            teamName = "Clube Recuperável",
            season = 2031,
            week = 17,
            balance = 91_234_567L
        )

        // Simula process death / restore D2D parcial depois do commit Room e antes da metadata.
        clearMetadata()
        reopenRepositories()

        val recovered = preferencesRepository.loadSaveSlots().single { it.id == "2" }
        assertTrue("Room válido deve prevalecer sobre metadata ausente", recovered.exists)
        assertFalse("Carreira válida não deve cair em recoveryRequired", recovered.recoveryRequired)
        assertEquals(expected.coachName, recovered.coachName)
        assertEquals("Clube Recuperável", recovered.teamName)
        assertEquals(expected.currentSeason, recovered.season)
        assertEquals(expected.currentWeek, recovered.week)
        assertEquals(expected.bankBalance, recovered.balance)
        assertFalse("Novo Jogo deve ser recusado em DB com carreira válida", saveRepository.isNewGameAllowed("2"))

        val persisted = saveRepository.getRepositoryForSlot("2").getGameSave()
        assertEquals("A recuperação não pode mutar a carreira", expected, persisted)

        val dataStoreSnapshot = context.dataStore.data.first()
        assertTrue(dataStoreSnapshot[booleanPreferencesKey("slot_2_exists")] == true)
        assertEquals("Carreira Recuperável", dataStoreSnapshot[stringPreferencesKey("slot_2_coach_name")])
        assertTrue(context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .getBoolean("slot_2_exists", false))

        // Recovery idempotente: perder a projeção novamente continua recuperando o mesmo Room.
        clearMetadata()
        val secondRecovery = preferencesRepository.loadSaveSlots().single { it.id == "2" }
        assertEquals(recovered.copy(updatedAt = secondRecovery.updatedAt), secondRecovery)
        assertEquals(expected, saveRepository.getRepositoryForSlot("2").getGameSave())
    }

    @Test
    fun recoverySurvivesRepeatedReopensAndSubsequentCareerWrite() = runBlocking {
        val original = createCareer(
            slotId = "2",
            coachName = "Recovery Persistente",
            teamId = 9_052L,
            teamName = "Clube Recovery",
            season = 2032,
            week = 11,
            balance = 52_000_000L
        )
        clearMetadata()
        reopenRepositories()

        val firstRecovered = preferencesRepository.loadSaveSlots().single { it.id == "2" }
        assertEquals(original.coachName, firstRecovered.coachName)

        // Depois do recovery, uma gravação normal de carreira deve continuar autoritativa e
        // reconstruir a projeção metadata sem depender do estado anterior.
        val updated = original.copy(currentWeek = 12, bankBalance = 53_500_000L)
        saveRepository.getRepositoryForSlot("2").saveGameSave(updated)
        val afterWrite = preferencesRepository.loadSaveSlots().single { it.id == "2" }
        assertEquals(12, afterWrite.week)
        assertEquals(53_500_000L, afterWrite.balance)

        repeat(3) {
            reopenRepositories()
            val reopened = preferencesRepository.loadSaveSlots().single { it.id == "2" }
            assertTrue(reopened.exists)
            assertFalse(reopened.recoveryRequired)
            assertEquals(updated.coachName, reopened.coachName)
            assertEquals(updated.currentWeek, reopened.week)
            assertEquals(updated.bankBalance, reopened.balance)
            assertEquals(updated, saveRepository.getRepositoryForSlot("2").getGameSave())
        }
    }

    @Test
    fun inconsistentMetadataIsReconciledFromRoomWithoutChangingCareer() = runBlocking {
        val expected = createCareer(
            slotId = "2",
            coachName = "Autoridade Room",
            teamId = 9_102L,
            teamName = "Time Autoritativo",
            season = 2033,
            week = 28,
            balance = 77_000_123L
        )
        preferencesRepository.updateSlotMetadata(
            saveId = "2",
            coachName = "Metadata Errada",
            teamName = "Outro Clube",
            season = 1999,
            week = 52,
            balance = 1L
        )

        val reconciled = preferencesRepository.loadSaveSlots().single { it.id == "2" }
        assertTrue(reconciled.exists)
        assertFalse(reconciled.recoveryRequired)
        assertEquals(expected.coachName, reconciled.coachName)
        assertEquals("Time Autoritativo", reconciled.teamName)
        assertEquals(expected.currentSeason, reconciled.season)
        assertEquals(expected.currentWeek, reconciled.week)
        assertEquals(expected.bankBalance, reconciled.balance)
        assertEquals(expected, saveRepository.getRepositoryForSlot("2").getGameSave())

        val rebuilt = context.dataStore.data.first()
        assertEquals("Autoridade Room", rebuilt[stringPreferencesKey("slot_2_coach_name")])
    }

    @Test
    fun emptyDatabaseWithMetadataIsSanitizedAndRemainsEligibleForNewGame() = runBlocking {
        assertNull(saveRepository.getRepositoryForSlot("3").getGameSave())
        assertTrue(saveRepository.databaseFileForSlot("3").exists())
        preferencesRepository.updateSlotMetadata(
            saveId = "3",
            coachName = "Fantasma",
            teamName = "Sem Carreira",
            season = 2040,
            week = 9,
            balance = 10L
        )

        val slot = preferencesRepository.loadSaveSlots().single { it.id == "3" }
        assertFalse("DB estrutural sem GameSave não é carreira", slot.exists)
        assertFalse(slot.recoveryRequired)
        assertTrue("Somente DB semanticamente vazio pode iniciar Novo Jogo", saveRepository.isNewGameAllowed("3"))
        assertFalse(context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .getBoolean("slot_3_exists", true))
    }

    @Test
    fun unreadableDatabaseIsPreservedAsRecoveryRequiredAndBlocksNewGame() = runBlocking {
        val slotId = "4"
        val file = saveRepository.databaseFileForSlot(slotId)
        file.parentFile?.mkdirs()
        val originalBytes = "phase-10.6-corrupted-sqlite-sentinel".toByteArray()
        file.writeBytes(originalBytes)

        val slots = preferencesRepository.loadSaveSlots()
        val slot = slots.single { it.id == slotId }
        assertTrue("Banco não validável deve permanecer ocupado", slot.exists)
        assertTrue("Falha de abertura deve exigir recuperação", slot.recoveryRequired)
        assertNotNull(slot.recoveryMessage)
        assertFalse("Erro/corrupção nunca autoriza Novo Jogo", saveRepository.isNewGameAllowed(slotId))
        assertTrue("A inspeção não pode apagar o arquivo problemático", file.exists())
        assertTrue("A inspeção não pode truncar silenciosamente o arquivo", file.length() > 0L)
    }

    @Test
    fun recoveryAcrossMultipleSlotsIsIsolatedAndIdempotent() = runBlocking {
        val save2 = createCareer("2", "Técnico Dois", 9_202L, "Clube Dois", 2032, 7, 22_000_000L)
        val save3 = createCareer("3", "Técnico Três", 9_303L, "Clube Três", 2034, 19, 33_000_000L)
        clearMetadata()
        reopenRepositories()

        repeat(2) {
            val slots = preferencesRepository.loadSaveSlots().associateBy { it.id }
            assertEquals("Técnico Dois", slots.getValue("2").coachName)
            assertEquals("Clube Dois", slots.getValue("2").teamName)
            assertEquals("Técnico Três", slots.getValue("3").coachName)
            assertEquals("Clube Três", slots.getValue("3").teamName)
            assertFalse(slots.getValue("2").recoveryRequired)
            assertFalse(slots.getValue("3").recoveryRequired)
            assertEquals(save2, saveRepository.getRepositoryForSlot("2").getGameSave())
            assertEquals(save3, saveRepository.getRepositoryForSlot("3").getGameSave())
        }
    }

    @Test
    fun rapidAlternatingSlotInspectionNeverCrossesCareerData() = runBlocking {
        val expected = mapOf(
            "1" to createCareer("1", "Rápido Um", 9_601L, "Clube Um", 2031, 3, 11_000_000L),
            "2" to createCareer("2", "Rápido Dois", 9_602L, "Clube Dois", 2032, 6, 22_000_000L),
            "3" to createCareer("3", "Rápido Três", 9_603L, "Clube Três", 2033, 9, 33_000_000L)
        )
        clearMetadata()
        reopenRepositories()

        val order = listOf("1", "2", "3", "2", "1", "3")
        repeat(20) { iteration ->
            val slotId = order[iteration % order.size]
            val inspection = saveRepository.inspectSlot(slotId)
            assertEquals(SlotDatabaseState.VALID_CAREER, inspection.state)
            assertEquals(expected.getValue(slotId), inspection.save)
            assertFalse(inspection.newGameAllowed)

            val listed = preferencesRepository.loadSaveSlots().associateBy { it.id }
            expected.forEach { (id, save) ->
                assertEquals(save.coachName, listed.getValue(id).coachName)
                assertFalse(listed.getValue(id).recoveryRequired)
                assertEquals(save, saveRepository.getRepositoryForSlot(id).getGameSave())
            }
        }
    }

    @Test
    fun dataStoreFailureFallsBackToLegacyAndRoomStillRecoversCareer() = runBlocking {
        val expected = createCareer("2", "Fallback Durável", 9_402L, "Clube Fallback", 2035, 21, 44_000_000L)
        val failingRepository = GamePreferencesRepository(
            dataStore = FailingDataStore(),
            context = context,
            saveRepository = saveRepository
        )

        // Simula metadata indisponível/corrompida: DataStore falha, mas SharedPreferences commit()
        // confirma a projeção durável e Room continua sendo a autoridade da carreira.
        failingRepository.updateSlotMetadata(
            saveId = "2",
            coachName = expected.coachName,
            teamName = "Clube Fallback",
            season = expected.currentSeason,
            week = expected.currentWeek,
            balance = expected.bankBalance
        )
        assertTrue(context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .getBoolean("slot_2_exists", false))

        val recovered = failingRepository.loadSaveSlots().single { it.id == "2" }
        assertTrue(recovered.exists)
        assertFalse(recovered.recoveryRequired)
        assertEquals(expected.coachName, recovered.coachName)
        assertEquals(expected, saveRepository.getRepositoryForSlot("2").getGameSave())
    }

    @Test
    fun explicitDeletionIsRequiredBeforeIntentionalReplacement() = runBlocking {
        createCareer("5", "Excluir Explicitamente", 9_505L, "Clube Cinco", 2036, 4, 55_000_000L)
        assertEquals(SlotDatabaseState.VALID_CAREER, saveRepository.inspectSlot("5").state)
        assertFalse(saveRepository.isNewGameAllowed("5"))

        preferencesRepository.removeSlotMetadata("5")
        assertTrue(saveRepository.deleteSlotDatabase("5"))

        val inspection = saveRepository.inspectSlot("5")
        assertEquals(SlotDatabaseState.MISSING, inspection.state)
        assertTrue(inspection.newGameAllowed)
        val slot = preferencesRepository.loadSaveSlots().single { it.id == "5" }
        assertFalse(slot.exists)

        val replacement = createCareer(
            "5",
            "Substituição Intencional",
            9_555L,
            "Novo Clube Cinco",
            2040,
            1,
            65_000_000L
        )
        val replacementInspection = saveRepository.inspectSlot("5")
        assertEquals(SlotDatabaseState.VALID_CAREER, replacementInspection.state)
        assertEquals(replacement, replacementInspection.save)
        assertFalse(replacementInspection.newGameAllowed)
    }

    private suspend fun createCareer(
        slotId: String,
        coachName: String,
        teamId: Long,
        teamName: String,
        season: Int,
        week: Int,
        balance: Long
    ): GameSave {
        val repo = saveRepository.getRepositoryForSlot(slotId)
        repo.saveTeams(
            listOf(
                Team(
                    id = teamId,
                    name = teamName,
                    city = "Cidade $slotId",
                    state = "MG",
                    country = "Brasil",
                    division = 1,
                    rating = 75
                )
            )
        )
        val save = GameSave(
            coachName = coachName,
            currentWeek = week,
            currentSeason = season,
            playerTeamId = teamId,
            bankBalance = balance
        )
        repo.saveGameSave(save)
        return save
    }

    private suspend fun clearMetadata() {
        context.dataStore.edit { it.clear() }
        context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun reopenRepositories() {
        if (::saveRepository.isInitialized) {
            saveRepository.closeAllDatabases()
        }
        databaseFactory = SlotDatabaseFactory(context)
        saveRepository = GameSaveRepository(context, databaseFactory)
        preferencesRepository = GamePreferencesRepository(context.dataStore, context, saveRepository)
    }

    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
            throw IOException("forced DataStore read failure")
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            throw IOException("forced DataStore write failure")
        }
    }
}
