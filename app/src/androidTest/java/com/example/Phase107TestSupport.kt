package com.example

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.GamePreferencesRepository
import com.example.data.GameSave
import com.example.data.Team
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import kotlinx.coroutines.runBlocking

data class Phase107RuntimeDependencies(
    private val saveRepository: GameSaveRepository,
    private val preferencesRepository: GamePreferencesRepository
) {
    fun gameSaveRepository(): GameSaveRepository = saveRepository
    fun gamePreferencesRepository(): GamePreferencesRepository = preferencesRepository
}

object Phase107TestSupport {
    const val TARGET_PACKAGE = "com.aistudio.brasfutretro.djuxzt"
    const val TEST_PACKAGE = "com.aistudio.brasfutretro.djuxzt.test"

    fun targetContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    private val runtimeDependencies: Phase107RuntimeDependencies by lazy {
        val context = targetContext().applicationContext
        val saveRepository = GameSaveRepository(context, SlotDatabaseFactory(context))
        Phase107RuntimeDependencies(
            saveRepository = saveRepository,
            preferencesRepository = GamePreferencesRepository(context.dataStore, context, saveRepository)
        )
    }

    /**
     * Persistence helpers use the same production classes and physical application storage without
     * replacing MainApplication or Hilt. Hilt itself is certified by launching MainActivity, whose
     * @AndroidEntryPoint composition must create the real hiltViewModel graph successfully.
     */
    fun entryPoint(): Phase107RuntimeDependencies = runtimeDependencies

    private suspend fun resetSlotInternal(slotId: String) {
        val dependencies = entryPoint()
        dependencies.gameSaveRepository().deleteSlotDatabase(slotId)
        dependencies.gamePreferencesRepository().removeSlotMetadata(slotId)
    }

    fun resetSlot(slotId: String) = runBlocking {
        resetSlotInternal(slotId)
    }

    fun seedCareer(
        slotId: String,
        coachName: String,
        teamName: String,
        teamId: Long,
        writeMetadata: Boolean = true,
        season: Int = 2026,
        week: Int = 9,
        balance: Long = 10_700_000L
    ) = runBlocking {
        resetSlotInternal(slotId)
        val dependencies = entryPoint()
        val saveRepository = dependencies.gameSaveRepository()
        val gameRepository = saveRepository.getRepositoryForSlot(slotId)
        gameRepository.saveTeams(
            listOf(
                Team(
                    id = teamId,
                    name = teamName,
                    city = "Instrumented CI",
                    state = "CI",
                    country = "Brasil",
                    division = 1,
                    isPlayerControlled = true,
                    rating = 70
                )
            )
        )
        gameRepository.saveGameSave(
            GameSave(
                coachName = coachName,
                currentSeason = season,
                currentWeek = week,
                playerTeamId = teamId,
                bankBalance = balance
            )
        )
        saveRepository.checkpointSlot(slotId)

        if (writeMetadata) {
            dependencies.gamePreferencesRepository().updateSlotMetadata(
                saveId = slotId,
                coachName = coachName,
                teamName = teamName,
                season = season,
                week = week,
                balance = balance
            )
        } else {
            dependencies.gamePreferencesRepository().removeSlotMetadata(slotId)
        }
    }

    fun closeDatabases() {
        entryPoint().gameSaveRepository().closeAllDatabases()
    }

    fun sqliteUserVersion(slotId: String): Int {
        val db = entryPoint().gameSaveRepository().getDatabaseForSlot(slotId)
        return db.openHelper.readableDatabase
            .query("PRAGMA user_version")
            .use { cursor ->
                check(cursor.moveToFirst()) { "PRAGMA user_version returned no row" }
                cursor.getInt(0)
            }
    }

    fun sqliteJournalMode(slotId: String): String {
        val db = entryPoint().gameSaveRepository().getDatabaseForSlot(slotId)
        return db.openHelper.readableDatabase
            .query("PRAGMA journal_mode")
            .use { cursor ->
                check(cursor.moveToFirst()) { "PRAGMA journal_mode returned no row" }
                cursor.getString(0).lowercase()
            }
    }

    fun sqliteRowCount(slotId: String, tableName: String): Long {
        require(tableName in setOf("game_save", "teams", "players", "fixtures")) {
            "Unsupported Phase 10.7 table count: $tableName"
        }
        val db = entryPoint().gameSaveRepository().getDatabaseForSlot(slotId)
        return db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM `$tableName`")
            .use { cursor ->
                check(cursor.moveToFirst()) { "COUNT(*) returned no row for $tableName" }
                cursor.getLong(0)
            }
    }
}
