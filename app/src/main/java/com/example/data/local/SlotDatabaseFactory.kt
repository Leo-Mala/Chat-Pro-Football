package com.example.data.local

import android.content.Context
import androidx.room.Room
import com.example.data.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SlotDatabaseFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var currentDatabase: AppDatabase? = null
    private var currentSlotId: String? = null

    @Synchronized
    fun getDatabaseForSlot(slotId: String): AppDatabase {
        if (currentSlotId == slotId && currentDatabase != null && currentDatabase!!.isOpen) {
            return currentDatabase!!
        }

        currentDatabase?.close()

        val dbName = if (slotId == "1") "brasfut_retro_database" else "game_save_slot_$slotId.db"

        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        )
            .addMigrations(
                com.example.data.migrations.MIGRATION_14_15,
                com.example.data.migrations.MIGRATION_15_16,
                com.example.data.migrations.MIGRATION_16_17
            )
            .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

        currentDatabase = db
        currentSlotId = slotId
        return db
    }

    @Synchronized
    fun closeCurrentDatabase() {
        currentDatabase?.close()
        currentDatabase = null
        currentSlotId = null
    }

    @Synchronized
    fun closeAndRemoveSlot(slotId: String) {
        if (currentSlotId == slotId) {
            closeCurrentDatabase()
        }
    }
}
