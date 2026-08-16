package com.example.data.repository

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.local.SlotDatabaseFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositório responsável pelo gerenciamento centralizado e thread-safe de instâncias
 * dinâmicas do AppDatabase e GameRepository por slot de salvamento.
 */
@Singleton
class GameSaveRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseFactory: SlotDatabaseFactory
) {
    fun getDatabaseForSlot(slotId: String): AppDatabase {
        return databaseFactory.getDatabaseForSlot(slotId)
    }

    fun getRepositoryForSlot(slotId: String): GameRepository {
        val db = getDatabaseForSlot(slotId)
        return GameRepository(db)
    }

    fun closeAndRemoveSlot(slotId: String) {
        databaseFactory.closeAndRemoveSlot(slotId)
    }

    fun closeAllDatabases() {
        databaseFactory.closeCurrentDatabase()
    }
}
