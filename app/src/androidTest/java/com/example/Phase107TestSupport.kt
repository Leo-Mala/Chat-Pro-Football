package com.example

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.GamePreferencesRepository
import com.example.data.GameSave
import com.example.data.Team
import com.example.data.repository.GameSaveRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking

@EntryPoint
@InstallIn(SingletonComponent::class)
interface Phase107AppEntryPoint {
    fun gameSaveRepository(): GameSaveRepository
    fun gamePreferencesRepository(): GamePreferencesRepository
}

object Phase107TestSupport {
    const val TARGET_PACKAGE = "com.aistudio.brasfutretro.djuxzt"
    const val TEST_PACKAGE = "com.aistudio.brasfutretro.djuxzt.test"

    fun targetContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    fun entryPoint(): Phase107AppEntryPoint = EntryPointAccessors.fromApplication(
        targetContext().applicationContext,
        Phase107AppEntryPoint::class.java
    )

    fun resetSlot(slotId: String) = runBlocking {
        val entryPoint = entryPoint()
        entryPoint.gameSaveRepository().deleteSlotDatabase(slotId)
        entryPoint.gamePreferencesRepository().removeSlotMetadata(slotId)
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
        resetSlot(slotId)
        val entryPoint = entryPoint()
        val saveRepository = entryPoint.gameSaveRepository()
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
            entryPoint.gamePreferencesRepository().updateSlotMetadata(
                saveId = slotId,
                coachName = coachName,
                teamName = teamName,
                season = season,
                week = week,
                balance = balance
            )
        } else {
            entryPoint.gamePreferencesRepository().removeSlotMetadata(slotId)
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
}
