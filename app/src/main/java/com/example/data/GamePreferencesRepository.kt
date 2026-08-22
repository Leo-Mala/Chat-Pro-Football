package com.example.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.SaveSlotMetadata
import com.example.data.repository.GameSaveRepository
import com.example.data.repository.SlotDatabaseState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "game_settings")

@Singleton
class GamePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val context: Context,
    private val saveRepository: GameSaveRepository
) {
    companion object {
        private val AUTOSAVE_KEY = booleanPreferencesKey("autosave_enabled")
        private val INFINITE_STAMINA_KEY = booleanPreferencesKey("infinite_stamina_enabled")
        private val AUTOLINEUP_KEY = booleanPreferencesKey("autolineup_enabled")
        private val WATCHLIST_KEY = stringSetPreferencesKey("watchlist_players")
        private const val TAG = "GamePreferencesRepo"
    }

    private data class StoredSlotMetadata(
        val exists: Boolean,
        val coachName: String,
        val teamName: String,
        val season: Int,
        val week: Int,
        val balance: Long
    )

    private val legacyPrefs by lazy {
        context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
    }

    val autoSaveEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTOSAVE_KEY] ?: legacyPrefs.getBoolean("autosave_enabled", true)
    }

    val infiniteStaminaEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[INFINITE_STAMINA_KEY] ?: legacyPrefs.getBoolean("infinite_stamina_enabled", false)
    }

    val autoLineupEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTOLINEUP_KEY] ?: legacyPrefs.getBoolean("autolineup_enabled", true)
    }

    val watchlistPlayers: Flow<Set<Long>> = dataStore.data.map { prefs ->
        val set = prefs[WATCHLIST_KEY] ?: legacyPrefs.getStringSet("watchlist_players", emptySet()) ?: emptySet()
        set.mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun setAutoSaveEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AUTOSAVE_KEY] = enabled
        }
        legacyPrefs.edit().putBoolean("autosave_enabled", enabled).apply()
    }

    suspend fun setInfiniteStaminaEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[INFINITE_STAMINA_KEY] = enabled
        }
        legacyPrefs.edit().putBoolean("infinite_stamina_enabled", enabled).apply()
    }

    suspend fun setAutoLineupEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AUTOLINEUP_KEY] = enabled
        }
        legacyPrefs.edit().putBoolean("autolineup_enabled", enabled).apply()
    }

    suspend fun toggleWatchlistPlayer(playerId: Long): Set<Long> {
        var currentSet = emptySet<Long>()
        dataStore.edit { prefs ->
            val existing = prefs[WATCHLIST_KEY] ?: legacyPrefs.getStringSet("watchlist_players", emptySet()) ?: emptySet()
            val mutable = existing.toMutableSet()
            val idStr = playerId.toString()
            if (mutable.contains(idStr)) {
                mutable.remove(idStr)
            } else {
                mutable.add(idStr)
            }
            prefs[WATCHLIST_KEY] = mutable
            currentSet = mutable.mapNotNull { it.toLongOrNull() }.toSet()
        }
        legacyPrefs.edit().putStringSet("watchlist_players", currentSet.map { it.toString() }.toSet()).apply()
        return currentSet
    }

    /**
     * Reconcilia metadata derivada com o conteúdo autoritativo de cada banco Room.
     *
     * A existência de carreira nunca depende apenas de DataStore/SharedPreferences nem apenas de
     * File.exists(). O registro `game_save(id=1)` lido com sucesso é a autoridade para uma carreira
     * válida. Se a leitura do banco falhar, o slot fica ocupado em RECOVERY_REQUIRED (fail-closed).
     */
    suspend fun loadSaveSlots(): List<SaveSlotMetadata> {
        val prefs = try {
            dataStore.data.first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao ler DataStore de metadata; usando fallback legado e Room", e)
            null
        }

        return (1..5).map { i ->
            val id = i.toString()
            val stored = readStoredSlotMetadata(prefs, id)
            val inspection = saveRepository.inspectSlot(id)

            when (inspection.state) {
                SlotDatabaseState.MISSING,
                SlotDatabaseState.EMPTY -> {
                    if (stored.exists) {
                        try {
                            removeSlotMetadata(id)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // O estado semântico do Room continua prevalecendo mesmo se o saneamento
                            // da projeção falhar; a falha fica registrada e será tentada novamente.
                            Log.e(TAG, "Falha ao sanear metadata fantasma do slot $id", e)
                        }
                    }
                    SaveSlotMetadata(id = id, exists = false)
                }

                SlotDatabaseState.VALID_CAREER -> {
                    val save = checkNotNull(inspection.save)
                    val authoritative = SaveSlotMetadata(
                        id = id,
                        exists = true,
                        coachName = save.coachName,
                        teamName = inspection.teamName ?: "Sem Clube",
                        season = save.currentSeason,
                        week = save.currentWeek,
                        balance = save.bankBalance
                    )

                    if (!stored.matches(authoritative)) {
                        try {
                            updateSlotMetadata(
                                saveId = id,
                                coachName = authoritative.coachName,
                                teamName = authoritative.teamName,
                                season = authoritative.season,
                                week = authoritative.week,
                                balance = authoritative.balance
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Metadata é reconstruível. Uma falha simultânea dos dois stores nunca
                            // pode rebaixar uma carreira Room válida para "slot vazio".
                            Log.e(TAG, "Carreira do slot $id recuperada, mas metadata não pôde ser reconstruída", e)
                        }
                    }
                    authoritative
                }

                SlotDatabaseState.RECOVERY_REQUIRED -> {
                    SaveSlotMetadata(
                        id = id,
                        exists = true,
                        coachName = stored.coachName.ifBlank { "Carreira preservada" },
                        teamName = stored.teamName.ifBlank { "Recuperação necessária" },
                        season = stored.season,
                        week = stored.week,
                        balance = stored.balance,
                        recoveryRequired = true,
                        recoveryMessage = "O banco deste slot não pôde ser validado. Os dados foram preservados e um novo jogo está bloqueado."
                    )
                }
            }
        }
    }

    private fun readStoredSlotMetadata(prefs: Preferences?, saveId: String): StoredSlotMetadata {
        return StoredSlotMetadata(
            exists = prefs?.get(booleanPreferencesKey("slot_${saveId}_exists"))
                ?: legacyPrefs.getBoolean("slot_${saveId}_exists", false),
            coachName = prefs?.get(stringPreferencesKey("slot_${saveId}_coach_name"))
                ?: legacyPrefs.getString("slot_${saveId}_coach_name", "").orEmpty(),
            teamName = prefs?.get(stringPreferencesKey("slot_${saveId}_team_name"))
                ?: legacyPrefs.getString("slot_${saveId}_team_name", "").orEmpty(),
            season = prefs?.get(intPreferencesKey("slot_${saveId}_season"))
                ?: legacyPrefs.getInt("slot_${saveId}_season", 2026),
            week = prefs?.get(intPreferencesKey("slot_${saveId}_week"))
                ?: legacyPrefs.getInt("slot_${saveId}_week", 1),
            balance = prefs?.get(longPreferencesKey("slot_${saveId}_balance"))
                ?: legacyPrefs.getLong("slot_${saveId}_balance", 0L)
        )
    }

    private fun StoredSlotMetadata.matches(authoritative: SaveSlotMetadata): Boolean =
        exists &&
            coachName == authoritative.coachName &&
            teamName == authoritative.teamName &&
            season == authoritative.season &&
            week == authoritative.week &&
            balance == authoritative.balance

    /**
     * Persiste a projeção de metadata em dois stores. Pelo menos um deles precisa confirmar a
     * escrita. DataStore é preferido; SharedPreferences usa commit síncrono como fallback durável.
     */
    suspend fun updateSlotMetadata(
        saveId: String,
        coachName: String,
        teamName: String,
        season: Int,
        week: Int,
        balance: Long
    ) {
        var dataStoreSucceeded = false
        var dataStoreFailure: Exception? = null
        try {
            dataStore.edit { prefs ->
                prefs[booleanPreferencesKey("slot_${saveId}_exists")] = true
                prefs[stringPreferencesKey("slot_${saveId}_coach_name")] = coachName
                prefs[stringPreferencesKey("slot_${saveId}_team_name")] = teamName
                prefs[intPreferencesKey("slot_${saveId}_season")] = season
                prefs[intPreferencesKey("slot_${saveId}_week")] = week
                prefs[longPreferencesKey("slot_${saveId}_balance")] = balance
            }
            dataStoreSucceeded = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            dataStoreFailure = e
            Log.e(TAG, "Falha ao persistir metadata do slot $saveId no DataStore", e)
        }

        val legacySucceeded = legacyPrefs.edit()
            .putBoolean("slot_${saveId}_exists", true)
            .putString("slot_${saveId}_coach_name", coachName)
            .putString("slot_${saveId}_team_name", teamName)
            .putInt("slot_${saveId}_season", season)
            .putInt("slot_${saveId}_week", week)
            .putLong("slot_${saveId}_balance", balance)
            .commit()

        if (!legacySucceeded) {
            Log.e(TAG, "Falha ao persistir metadata do slot $saveId no SharedPreferences legado")
        }

        if (!dataStoreSucceeded && !legacySucceeded) {
            throw IllegalStateException(
                "Nenhum store confirmou a persistência da metadata do slot $saveId",
                dataStoreFailure
            )
        }
    }

    suspend fun removeSlotMetadata(saveId: String) {
        var dataStoreSucceeded = false
        var dataStoreFailure: Exception? = null
        try {
            dataStore.edit { prefs ->
                prefs[booleanPreferencesKey("slot_${saveId}_exists")] = false
                prefs.remove(stringPreferencesKey("slot_${saveId}_coach_name"))
                prefs.remove(stringPreferencesKey("slot_${saveId}_team_name"))
                prefs.remove(intPreferencesKey("slot_${saveId}_season"))
                prefs.remove(intPreferencesKey("slot_${saveId}_week"))
                prefs.remove(longPreferencesKey("slot_${saveId}_balance"))
            }
            dataStoreSucceeded = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            dataStoreFailure = e
            Log.e(TAG, "Falha ao remover metadata do slot $saveId do DataStore", e)
        }

        val legacySucceeded = legacyPrefs.edit()
            .putBoolean("slot_${saveId}_exists", false)
            .remove("slot_${saveId}_coach_name")
            .remove("slot_${saveId}_team_name")
            .remove("slot_${saveId}_season")
            .remove("slot_${saveId}_week")
            .remove("slot_${saveId}_balance")
            .commit()

        if (!legacySucceeded) {
            Log.e(TAG, "Falha ao remover metadata do slot $saveId no SharedPreferences legado")
        }

        if (!dataStoreSucceeded && !legacySucceeded) {
            throw IllegalStateException(
                "Nenhum store confirmou a remoção da metadata do slot $saveId",
                dataStoreFailure
            )
        }
    }
}
