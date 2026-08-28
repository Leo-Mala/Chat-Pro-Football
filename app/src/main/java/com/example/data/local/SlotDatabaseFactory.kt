package com.example.data.local

import android.content.Context
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.CareerSeedTemplateContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central source of truth for save-slot database names and Room instances.
 *
 * Each slot owns an independent AppDatabase instance. Opening one slot must never
 * close another slot's database because other ViewModels/Flows may still be using it.
 */
@Singleton
class SlotDatabaseFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val LEGACY_SLOT_1_DATABASE_NAME = "brasfut_retro_database"

        fun databaseNameForSlot(slotId: String): String {
            require(slotId.isNotBlank()) { "slotId não pode ser vazio." }
            return if (slotId == "1") {
                LEGACY_SLOT_1_DATABASE_NAME
            } else {
                "game_save_slot_$slotId.db"
            }
        }
    }

    private val databases = mutableMapOf<String, AppDatabase>()

    @Synchronized
    fun getDatabaseForSlot(slotId: String): AppDatabase {
        // Room opens SQLite lazily, so isOpen=false does not mean this instance was closed.
        // Entries are removed from this map before we explicitly close them; therefore any
        // instance still registered here is the canonical database instance for that slot.
        databases[slotId]?.let { return it }

        val appContext = context.applicationContext
        val databaseName = databaseNameForSlot(slotId)
        val databaseFile = appContext.getDatabasePath(databaseName)
        val isBrandNewSlot = !databaseFile.exists()

        val builder = Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            databaseName
        )
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)

        // Somente um arquivo fisicamente inexistente recebe a cópia do baseline. Saves reais,
        // inclusive o banco legado do slot 1, nunca são substituídos nem recriados a partir do asset.
        // O baseline usa o mesmo schema Room V22 e mantém cada slot totalmente independente.
        if (isBrandNewSlot) {
            builder.createFromAsset(CareerSeedTemplateContract.ASSET_PATH)
        }

        val db = builder
            // Never destroy a save because its schema version is unknown or newer.
            .build()

        databases[slotId] = db
        return db
    }

    @Synchronized
    fun closeAndRemoveSlot(slotId: String) {
        databases.remove(slotId)?.close()
    }

    @Synchronized
    fun closeAllDatabases() {
        databases.values.forEach { db ->
            if (db.isOpen) {
                db.close()
            }
        }
        databases.clear()
    }

    /**
     * Kept for source compatibility with older callers.
     * The previous implementation tracked only one "current" DB; now all slots are tracked.
     */
    @Synchronized
    fun closeCurrentDatabase() {
        closeAllDatabases()
    }
}
