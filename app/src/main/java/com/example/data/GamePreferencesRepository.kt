package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.SaveSlotMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "game_settings")

@Singleton
class GamePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val context: Context
) {
    companion object {
        private val AUTOSAVE_KEY = booleanPreferencesKey("autosave_enabled")
        private val INFINITE_STAMINA_KEY = booleanPreferencesKey("infinite_stamina_enabled")
        private val AUTOLINEUP_KEY = booleanPreferencesKey("autolineup_enabled")
        private val WATCHLIST_KEY = stringSetPreferencesKey("watchlist_players")
    }

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

    suspend fun loadSaveSlots(): List<SaveSlotMetadata> {
        val prefs = try { dataStore.data.first() } catch (e: Exception) { null }
        return (1..5).map { i ->
            val id = i.toString()
            val existsKey = booleanPreferencesKey("slot_${id}_exists")
            val exists = prefs?.get(existsKey) ?: legacyPrefs.getBoolean("slot_${id}_exists", false)
            if (exists) {
                SaveSlotMetadata(
                    id = id,
                    exists = true,
                    coachName = prefs?.get(stringPreferencesKey("slot_${id}_coach_name")) ?: legacyPrefs.getString("slot_${id}_coach_name", "") ?: "",
                    teamName = prefs?.get(stringPreferencesKey("slot_${id}_team_name")) ?: legacyPrefs.getString("slot_${id}_team_name", "") ?: "",
                    season = prefs?.get(intPreferencesKey("slot_${id}_season")) ?: legacyPrefs.getInt("slot_${id}_season", 2026),
                    week = prefs?.get(intPreferencesKey("slot_${id}_week")) ?: legacyPrefs.getInt("slot_${id}_week", 1),
                    balance = prefs?.get(longPreferencesKey("slot_${id}_balance")) ?: legacyPrefs.getLong("slot_${id}_balance", 0L)
                )
            } else {
                SaveSlotMetadata(id = id, exists = false)
            }
        }
    }

    suspend fun updateSlotMetadata(
        saveId: String,
        coachName: String,
        teamName: String,
        season: Int,
        week: Int,
        balance: Long
    ) {
        try {
            dataStore.edit { prefs ->
                prefs[booleanPreferencesKey("slot_${saveId}_exists")] = true
                prefs[stringPreferencesKey("slot_${saveId}_coach_name")] = coachName
                prefs[stringPreferencesKey("slot_${saveId}_team_name")] = teamName
                prefs[intPreferencesKey("slot_${saveId}_season")] = season
                prefs[intPreferencesKey("slot_${saveId}_week")] = week
                prefs[longPreferencesKey("slot_${saveId}_balance")] = balance
            }
        } catch (_: Exception) {}
        legacyPrefs.edit()
            .putBoolean("slot_${saveId}_exists", true)
            .putString("slot_${saveId}_coach_name", coachName)
            .putString("slot_${saveId}_team_name", teamName)
            .putInt("slot_${saveId}_season", season)
            .putInt("slot_${saveId}_week", week)
            .putLong("slot_${saveId}_balance", balance)
            .apply()
    }

    suspend fun removeSlotMetadata(saveId: String) {
        try {
            dataStore.edit { prefs ->
                prefs[booleanPreferencesKey("slot_${saveId}_exists")] = false
                prefs.remove(stringPreferencesKey("slot_${saveId}_coach_name"))
                prefs.remove(stringPreferencesKey("slot_${saveId}_team_name"))
                prefs.remove(intPreferencesKey("slot_${saveId}_season"))
                prefs.remove(intPreferencesKey("slot_${saveId}_week"))
                prefs.remove(longPreferencesKey("slot_${saveId}_balance"))
            }
        } catch (_: Exception) {}
        legacyPrefs.edit()
            .putBoolean("slot_${saveId}_exists", false)
            .remove("slot_${saveId}_coach_name")
            .remove("slot_${saveId}_team_name")
            .remove("slot_${saveId}_season")
            .remove("slot_${saveId}_week")
            .remove("slot_${saveId}_balance")
            .apply()
    }
}
