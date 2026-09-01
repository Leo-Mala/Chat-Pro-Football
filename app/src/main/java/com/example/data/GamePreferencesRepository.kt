package com.example.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.SaveSlotMetadata
import com.example.data.model.SaveSlotsPublicationClock
import com.example.data.model.SaveSlotsSnapshot
import com.example.data.repository.GameSaveRepository
import com.example.data.repository.SlotDatabaseInspection
import com.example.data.repository.SlotDatabaseState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        const val DEFAULT_COACH_AVATAR_ID = "coach_1"
        val SUPPORTED_COACH_AVATAR_IDS: Set<String> = setOf("coach_1", "coach_2", "coach_3", "coach_4")
        private const val TAG = "GamePreferencesRepo"
        private const val MAX_RECONCILIATION_RETRIES = 3
        private const val MAX_RECONCILIATION_FAILURE_ATTEMPTS = 4
        private const val INITIAL_RECONCILIATION_FAILURE_BACKOFF_MS = 50L
        private const val MAX_RECONCILIATION_FAILURE_BACKOFF_MS = 1000L
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

    /**
     * Cada repositório possui seu próprio domínio monotônico de publicação. Assim, uma mutação ou
     * reconciliação ainda em voo de outra instância não pode invalidar snapshots desta instância.
     * O domínio global de compatibilidade permanece restrito aos gates que exercitam o contrato
     * diretamente, enquanto o runtime usa este domínio privado.
     */
    private val saveSlotsPublicationDomain = SaveSlotsPublicationClock.newDomain()

    /**
     * Apenas uma reconciliação pode reservar/publicar gerações por vez nesta instância. Mutadores
     * desta mesma instância ainda invalidam o domínio normalmente, mas uma leitura antiga que está
     * falhando não pode reservar N+1 e tornar obsoleto o snapshot N produzido por outra leitura
     * bem-sucedida do mesmo repositório.
     */
    private val saveSlotsReconciliationMutex = Mutex()

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

    fun coachAvatarId(saveId: String): Flow<String> {
        val preferenceKey = stringPreferencesKey("slot_${saveId}_coach_avatar")
        val legacyKey = "slot_${saveId}_coach_avatar"
        return dataStore.data.map { prefs ->
            val stored = prefs[preferenceKey]
                ?: legacyPrefs.getString(legacyKey, DEFAULT_COACH_AVATAR_ID)
                ?: DEFAULT_COACH_AVATAR_ID
            stored.takeIf { it in SUPPORTED_COACH_AVATAR_IDS } ?: DEFAULT_COACH_AVATAR_ID
        }
    }

    suspend fun setCoachAvatarId(saveId: String, avatarId: String) {
        require(avatarId in SUPPORTED_COACH_AVATAR_IDS) { "Avatar interno inválido: $avatarId" }
        val preferenceKey = stringPreferencesKey("slot_${saveId}_coach_avatar")
        val legacyKey = "slot_${saveId}_coach_avatar"
        dataStore.edit { prefs ->
            prefs[preferenceKey] = avatarId
        }
        check(legacyPrefs.edit().putString(legacyKey, avatarId).commit()) {
            "Falha ao persistir avatar do técnico para o slot $saveId"
        }
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
     * A geração é reservada ANTES da primeira leitura. Ela acompanha o resultado até a fronteira
     * do StateFlow; se uma mutação desta instância começar antes da publicação, a factory
     * especializada do ViewModel rejeita este snapshot antigo consultando o domínio transportado
     * pelo próprio snapshot.
     *
     * As reconciliações são serializadas para que retries fracassados não possam superseder uma
     * publicação válida concorrente. Falhas permanentes têm orçamento finito e, ao esgotá-lo, viram
     * um snapshot explicitamente bloqueado/recoveryRequired. Isso preserva dados, impede Novo Jogo
     * destrutivo e evita lançar exceção não tratada a partir do startup fire-and-forget. Cancelamento
     * continua sendo propagado imediatamente.
     */
    suspend fun loadSaveSlots(): List<SaveSlotMetadata> = saveSlotsReconciliationMutex.withLock {
        var backoffMs = INITIAL_RECONCILIATION_FAILURE_BACKOFF_MS
        var failureAttempts = 0
        while (true) {
            val publicationGeneration = saveSlotsPublicationDomain.reserve()
            try {
                val reconciled = reconcileAllSlots()
                return@withLock SaveSlotsSnapshot(
                    publicationGeneration,
                    reconciled,
                    saveSlotsPublicationDomain
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failureAttempts += 1
                Log.e(
                    TAG,
                    "Falha ao reconciliar slots na geração $publicationGeneration; tentativa $failureAttempts/$MAX_RECONCILIATION_FAILURE_ATTEMPTS",
                    e
                )
                if (failureAttempts >= MAX_RECONCILIATION_FAILURE_ATTEMPTS) {
                    val message =
                        "Falha persistente ao reconciliar os slots após $failureAttempts tentativas. Os dados foram preservados e novos jogos permanecem bloqueados até uma leitura válida."
                    Log.e(TAG, message, e)
                    return@withLock SaveSlotsSnapshot(
                        publicationGeneration,
                        persistentFailureMetadata(message),
                        saveSlotsPublicationDomain
                    )
                }
                delay(backoffMs)
                backoffMs = (backoffMs * 2L).coerceAtMost(MAX_RECONCILIATION_FAILURE_BACKOFF_MS)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        emptyList()
    }

    private fun persistentFailureMetadata(message: String): List<SaveSlotMetadata> =
        (1..5).map { slot ->
            SaveSlotMetadata(
                id = slot.toString(),
                exists = true,
                coachName = "Carreira preservada",
                teamName = "Recuperação necessária",
                recoveryRequired = true,
                recoveryMessage = message
            )
        }

    private suspend fun reconcileAllSlots(attempt: Int = 0): List<SaveSlotMetadata> {
        val saveIds = (1..5).map(Int::toString)
        val projected = saveIds.map { saveId -> reconcileSlot(saveId) }

        val finalInspections = saveIds.map { saveId -> saveRepository.inspectSlot(saveId) }
        val stable = projected.indices.all { index ->
            projectionMatchesInspection(projected[index], finalInspections[index])
        }
        if (stable) return projected

        if (attempt >= MAX_RECONCILIATION_RETRIES) {
            return projected.indices.map { index ->
                val metadata = projected[index]
                val inspection = finalInspections[index]
                if (projectionMatchesInspection(metadata, inspection)) {
                    metadata
                } else {
                    val stored = readStoredSlotMetadata(readPreferencesSnapshot(), saveIds[index])
                    recoveryMetadata(
                        saveId = saveIds[index],
                        stored = stored,
                        message = "O estado do slot mudou repetidamente durante a reconciliação global. Os dados foram preservados e um novo jogo está bloqueado."
                    )
                }
            }
        }

        return reconcileAllSlots(attempt + 1)
    }

    private suspend fun readPreferencesSnapshot(): Preferences? = try {
        dataStore.data.first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Falha ao ler DataStore de metadata; usando fallback legado e Room", e)
        null
    }

    private suspend fun reconcileSlot(saveId: String, attempt: Int = 0): SaveSlotMetadata {
        val stored = readStoredSlotMetadata(readPreferencesSnapshot(), saveId)
        val before = saveRepository.inspectSlot(saveId)
        val projected = projectInspection(saveId, stored, before)

        val after = saveRepository.inspectSlot(saveId)
        if (sameSemanticSnapshot(before, after)) {
            return projected
        }

        if (attempt >= MAX_RECONCILIATION_RETRIES) {
            val latestStored = readStoredSlotMetadata(readPreferencesSnapshot(), saveId)
            return recoveryMetadata(
                saveId = saveId,
                stored = latestStored,
                message = "O estado do slot mudou repetidamente durante a reconciliação. Os dados foram preservados e um novo jogo está bloqueado."
            )
        }
        return reconcileSlot(saveId, attempt + 1)
    }

    private suspend fun projectInspection(
        saveId: String,
        stored: StoredSlotMetadata,
        inspection: SlotDatabaseInspection
    ): SaveSlotMetadata = when (inspection.state) {
        SlotDatabaseState.MISSING,
        SlotDatabaseState.EMPTY -> {
            if (stored.exists) {
                try {
                    removeSlotMetadataProjection(saveId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Falha ao sanear metadata fantasma do slot $saveId", e)
                }
            }
            SaveSlotMetadata(id = saveId, exists = false)
        }

        SlotDatabaseState.VALID_CAREER -> {
            val save = checkNotNull(inspection.save)
            val authoritative = SaveSlotMetadata(
                id = saveId,
                exists = true,
                coachName = save.coachName,
                teamName = inspection.teamName ?: "Sem Clube",
                season = save.currentSeason,
                week = save.currentWeek,
                balance = save.bankBalance
            )

            if (!stored.matches(authoritative)) {
                try {
                    updateSlotMetadataProjection(
                        saveId = saveId,
                        coachName = authoritative.coachName,
                        teamName = authoritative.teamName,
                        season = authoritative.season,
                        week = authoritative.week,
                        balance = authoritative.balance
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Carreira do slot $saveId recuperada, mas metadata não pôde ser reconstruída", e)
                }
            }
            authoritative
        }

        SlotDatabaseState.RECOVERY_REQUIRED -> recoveryMetadata(saveId, stored)
    }

    private fun projectionMatchesInspection(
        metadata: SaveSlotMetadata,
        inspection: SlotDatabaseInspection
    ): Boolean = when (inspection.state) {
        SlotDatabaseState.MISSING,
        SlotDatabaseState.EMPTY -> !metadata.exists && !metadata.recoveryRequired

        SlotDatabaseState.VALID_CAREER -> {
            val save = inspection.save
            save != null &&
                metadata.exists &&
                !metadata.recoveryRequired &&
                metadata.coachName == save.coachName &&
                metadata.teamName == (inspection.teamName ?: "Sem Clube") &&
                metadata.season == save.currentSeason &&
                metadata.week == save.currentWeek &&
                metadata.balance == save.bankBalance
        }

        SlotDatabaseState.RECOVERY_REQUIRED -> metadata.exists && metadata.recoveryRequired
    }

    private fun recoveryMetadata(
        saveId: String,
        stored: StoredSlotMetadata,
        message: String = "O banco deste slot não pôde ser validado. Os dados foram preservados e um novo jogo está bloqueado."
    ): SaveSlotMetadata = SaveSlotMetadata(
        id = saveId,
        exists = true,
        coachName = stored.coachName.ifBlank { "Carreira preservada" },
        teamName = stored.teamName.ifBlank { "Recuperação necessária" },
        season = stored.season,
        week = stored.week,
        balance = stored.balance,
        recoveryRequired = true,
        recoveryMessage = message
    )

    private fun sameSemanticSnapshot(
        first: SlotDatabaseInspection,
        second: SlotDatabaseInspection
    ): Boolean {
        if (first.state != second.state) return false
        return when (first.state) {
            SlotDatabaseState.VALID_CAREER ->
                first.save == second.save && first.teamName == second.teamName
            SlotDatabaseState.RECOVERY_REQUIRED ->
                first.failureReason == second.failureReason
            SlotDatabaseState.MISSING,
            SlotDatabaseState.EMPTY -> true
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

    suspend fun updateSlotMetadata(
        saveId: String,
        coachName: String,
        teamName: String,
        season: Int,
        week: Int,
        balance: Long
    ) {
        saveSlotsPublicationDomain.invalidate()
        updateSlotMetadataProjection(saveId, coachName, teamName, season, week, balance)
    }

    private suspend fun updateSlotMetadataProjection(
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
        saveSlotsPublicationDomain.invalidate()
        removeSlotMetadataProjection(saveId)
    }

    private suspend fun removeSlotMetadataProjection(saveId: String) {
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
                prefs.remove(stringPreferencesKey("slot_${saveId}_coach_avatar"))
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
            .remove("slot_${saveId}_coach_avatar")
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