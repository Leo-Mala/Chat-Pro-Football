package com.example.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.data.APP_DATABASE_SCHEMA_VERSION
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.local.SlotDatabaseFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Estado semântico do banco físico de um slot. */
enum class SlotDatabaseState {
    MISSING,
    EMPTY,
    VALID_CAREER,
    RECOVERY_REQUIRED
}

/**
 * Resultado fail-closed da inspeção de um slot.
 *
 * [VALID_CAREER] só é emitido quando o registro autoritativo `game_save(id=1)` foi lido com
 * sucesso, é a única linha da tabela e `playerTeamId` é exatamente o único clube marcado como
 * controlado pelo jogador. A mera existência do arquivo SQLite nunca é usada como prova de carreira.
 */
data class SlotDatabaseInspection(
    val state: SlotDatabaseState,
    val save: GameSave? = null,
    val teamName: String? = null,
    val failureReason: String? = null
) {
    val newGameAllowed: Boolean
        get() = state == SlotDatabaseState.MISSING || state == SlotDatabaseState.EMPTY
}

/**
 * Impede que qualquer chamador abra/consuma Room sobre um slot que já exige recuperação.
 */
class SlotRecoveryRequiredException(
    val inspection: SlotDatabaseInspection
) : IllegalStateException(
    "Slot exige recuperação antes da abertura: ${inspection.failureReason ?: inspection.state.name}"
)

@Singleton
class GameSaveRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseFactory: SlotDatabaseFactory
) {
    companion object {
        private val SQLITE_FILE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        private val SQLITE_SIDECAR_SUFFIXES = listOf("-wal", "-shm", "-journal")
    }

    /**
     * Locks são por slot: exclusão/reopen do mesmo slot são serializados sem bloquear slots
     * independentes. Os mapas também são concorrentes porque operações de slots diferentes podem
     * progredir em paralelo.
     */
    private val repositories = ConcurrentHashMap<String, GameRepository>()
    private val slotLifecycleLocks = ConcurrentHashMap<String, Any>()

    private fun slotLifecycleLock(slotId: String): Any =
        slotLifecycleLocks.computeIfAbsent(slotId) { Any() }

    private fun requirePhysicalOpenAllowed(slotId: String) {
        physicalRecoveryInspection(slotId)?.let { inspection ->
            throw SlotRecoveryRequiredException(inspection)
        }
    }

    /**
     * Depois que Room materializa a tabela, nenhum `GameSave` representa apenas um banco
     * pré-carreira. Uma carreira válida exige exatamente `id=1`, o clube referenciado por
     * `playerTeamId` existente e marcado como controlado, e nenhum segundo clube controlado.
     * Qualquer outra combinação é corrupção/restore parcial e precisa ser preservada antes de
     * seed, repair ou qualquer mutação de UI.
     */
    private fun semanticRecoveryInspection(database: AppDatabase): SlotDatabaseInspection? {
        val sqlite = database.openHelper.readableDatabase
        val rows = sqlite
            .query("SELECT id, playerTeamId FROM game_save ORDER BY id")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.getInt(0) to cursor.getLong(1))
                    }
                }
            }

        if (rows.isEmpty()) return null
        if (rows.size != 1 || rows.single().first != 1) {
            return SlotDatabaseInspection(
                state = SlotDatabaseState.RECOVERY_REQUIRED,
                failureReason = "UnexpectedGameSaveRows:ids=${rows.joinToString(",") { it.first.toString() }}"
            )
        }

        val playerTeamId = rows.single().second
        val playerTeamControlled = sqlite
            .query(
                "SELECT isPlayerControlled FROM teams WHERE id = ? LIMIT 1",
                arrayOf(playerTeamId)
            )
            .use { cursor ->
                if (!cursor.moveToFirst()) null else cursor.getInt(0) != 0
            }
        if (playerTeamControlled == null) {
            return SlotDatabaseInspection(
                state = SlotDatabaseState.RECOVERY_REQUIRED,
                failureReason = "MissingControlledTeam:playerTeamId=$playerTeamId"
            )
        }
        if (!playerTeamControlled) {
            return SlotDatabaseInspection(
                state = SlotDatabaseState.RECOVERY_REQUIRED,
                failureReason = "PlayerTeamNotControlled:playerTeamId=$playerTeamId"
            )
        }

        val controlledTeamIds = sqlite
            .query("SELECT id FROM teams WHERE isPlayerControlled = 1 ORDER BY id")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getLong(0))
                }
            }
        return if (controlledTeamIds == listOf(playerTeamId)) {
            null
        } else {
            SlotDatabaseInspection(
                state = SlotDatabaseState.RECOVERY_REQUIRED,
                failureReason =
                    "ControlledTeamInvariantMismatch:playerTeamId=$playerTeamId,controlledIds=${controlledTeamIds.joinToString(",")}"
            )
        }
    }

    private fun requireSemanticUseAllowed(database: AppDatabase) {
        semanticRecoveryInspection(database)?.let { inspection ->
            throw SlotRecoveryRequiredException(inspection)
        }
    }

    /**
     * Abertura de Room é eager: `build()` sozinho é lazy e deixaria uma janela em que o arquivo
     * principal ainda não existe após o preflight. A instância só é entregue depois da abertura e
     * da validação da tabela autoritativa. O lock por slot impede reopen concorrente durante delete.
     */
    fun getDatabaseForSlot(slotId: String): AppDatabase = synchronized(slotLifecycleLock(slotId)) {
        requirePhysicalOpenAllowed(slotId)
        val database = databaseFactory.getDatabaseForSlot(slotId)
        try {
            requirePhysicalOpenAllowed(slotId)
            database.openHelper.writableDatabase
            requireSemanticUseAllowed(database)
        } catch (e: Exception) {
            databaseFactory.closeAndRemoveSlot(slotId)
            throw e
        }
        database
    }

    fun getRepositoryForSlot(slotId: String): GameRepository = synchronized(slotLifecycleLock(slotId)) {
        requirePhysicalOpenAllowed(slotId)
        repositories[slotId]?.let { repository ->
            requireSemanticUseAllowed(repository.db)
            return@synchronized repository
        }

        val database = databaseFactory.getDatabaseForSlot(slotId)
        try {
            requirePhysicalOpenAllowed(slotId)
            database.openHelper.writableDatabase
            requireSemanticUseAllowed(database)
        } catch (e: Exception) {
            databaseFactory.closeAndRemoveSlot(slotId)
            throw e
        }

        GameRepository(database).also { repositories[slotId] = it }
    }

    fun databaseNameForSlot(slotId: String): String = SlotDatabaseFactory.databaseNameForSlot(slotId)

    fun databaseFileForSlot(slotId: String): File = context.getDatabasePath(databaseNameForSlot(slotId))

    private fun databaseSidecarFiles(databaseFile: File): List<File> =
        SQLITE_SIDECAR_SUFFIXES.map { suffix -> File(databaseFile.path + suffix) }

    private fun existingDatabaseSidecars(databaseFile: File): List<File> =
        databaseSidecarFiles(databaseFile).filter { it.exists() }

    private fun hasCanonicalSqliteHeader(file: File): Boolean {
        if (file.length() < SQLITE_FILE_HEADER.size) return false
        return try {
            file.inputStream().use { input ->
                val actual = ByteArray(SQLITE_FILE_HEADER.size)
                var offset = 0
                while (offset < actual.size) {
                    val read = input.read(actual, offset, actual.size - offset)
                    if (read <= 0) return false
                    offset += read
                }
                actual.contentEquals(SQLITE_FILE_HEADER)
            }
        } catch (e: Exception) {
            Log.e("GameSaveRepository", "Falha ao ler cabeçalho SQLite de ${file.name}", e)
            false
        }
    }

    private fun readSqliteUserVersion(file: File): Int {
        val rawDatabase = SQLiteDatabase.openDatabase(
            file.path,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        return try {
            rawDatabase.rawQuery("PRAGMA user_version", null).use { cursor ->
                check(cursor.moveToFirst()) { "PRAGMA user_version não retornou linha" }
                cursor.getInt(0)
            }
        } finally {
            rawDatabase.close()
        }
    }

    fun physicalRecoveryInspection(slotId: String): SlotDatabaseInspection? {
        val file = databaseFileForSlot(slotId)
        if (!file.exists()) {
            val orphanedSidecars = existingDatabaseSidecars(file)
            return if (orphanedSidecars.isNotEmpty()) {
                SlotDatabaseInspection(
                    state = SlotDatabaseState.RECOVERY_REQUIRED,
                    failureReason = "OrphanedSQLiteSidecar:${orphanedSidecars.joinToString(",") { it.name }}"
                )
            } else {
                null
            }
        }
        if (file.length() == 0L) {
            return SlotDatabaseInspection(
                state = SlotDatabaseState.RECOVERY_REQUIRED,
                failureReason = "ZeroLengthDatabaseFile"
            )
        }
        if (!hasCanonicalSqliteHeader(file)) {
            return SlotDatabaseInspection(
                state = SlotDatabaseState.RECOVERY_REQUIRED,
                failureReason = "InvalidSQLiteHeader"
            )
        }

        val userVersion = try {
            readSqliteUserVersion(file)
        } catch (e: Exception) {
            Log.e("GameSaveRepository", "Falha ao ler user_version SQLite de ${file.name}", e)
            return SlotDatabaseInspection(
                state = SlotDatabaseState.RECOVERY_REQUIRED,
                failureReason = "UnreadableSQLiteContainer:${e.javaClass.simpleName}"
            )
        }
        if (userVersion !in AppDatabase.MINIMUM_AUTOMATICALLY_MIGRATABLE_VERSION..APP_DATABASE_SCHEMA_VERSION) {
            return SlotDatabaseInspection(
                state = SlotDatabaseState.RECOVERY_REQUIRED,
                failureReason = "UnsupportedOrUninitializedSchemaVersion:$userVersion"
            )
        }
        return null
    }

    private fun countGameSaveRows(repository: GameRepository): Int =
        repository.db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM game_save")
            .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    /**
     * Inspeciona o conteúdo real do slot sem criar banco para um arquivo inexistente.
     * `game_save(id=1)` é a autoridade e só é válida quando é a única linha e `playerTeamId` é
     * exatamente o único clube controlado.
     */
    suspend fun inspectSlot(slotId: String): SlotDatabaseInspection {
        val file = databaseFileForSlot(slotId)
        physicalRecoveryInspection(slotId)?.let { return it }
        if (!file.exists()) return SlotDatabaseInspection(SlotDatabaseState.MISSING)

        return try {
            val repository = getRepositoryForSlot(slotId)
            semanticRecoveryInspection(repository.db)?.let { return it }
            val save = repository.getGameSave()
            val gameSaveRowCount = countGameSaveRows(repository)
            when {
                gameSaveRowCount == 0 && save == null -> SlotDatabaseInspection(SlotDatabaseState.EMPTY)
                gameSaveRowCount == 1 && save != null && save.id == 1 -> {
                    val team = repository.getTeam(save.playerTeamId)
                    if (team == null) {
                        SlotDatabaseInspection(
                            state = SlotDatabaseState.RECOVERY_REQUIRED,
                            failureReason = "MissingControlledTeam:playerTeamId=${save.playerTeamId}"
                        )
                    } else {
                        SlotDatabaseInspection(
                            state = SlotDatabaseState.VALID_CAREER,
                            save = save,
                            teamName = team.name
                        )
                    }
                }
                else -> SlotDatabaseInspection(
                    state = SlotDatabaseState.RECOVERY_REQUIRED,
                    failureReason = "UnexpectedGameSaveRows:count=$gameSaveRowCount,canonical=${save != null}"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SlotRecoveryRequiredException) {
            e.inspection
        } catch (e: Exception) {
            Log.e("GameSaveRepository", "Falha ao inspecionar banco do slot $slotId", e)
            SlotDatabaseInspection(
                state = SlotDatabaseState.RECOVERY_REQUIRED,
                failureReason = e.javaClass.simpleName.ifBlank { "DatabaseReadFailure" }
            )
        }
    }

    suspend fun isNewGameAllowed(slotId: String): Boolean = inspectSlot(slotId).newGameAllowed

    fun checkpointSlot(slotId: String) = synchronized(slotLifecycleLock(slotId)) {
        val db = getDatabaseForSlot(slotId)
        db.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(FULL)")
            .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) }
    }

    private fun closeAndRemoveSlotLocked(slotId: String) {
        repositories.remove(slotId)
        databaseFactory.closeAndRemoveSlot(slotId)
    }

    fun closeAndRemoveSlot(slotId: String) = synchronized(slotLifecycleLock(slotId)) {
        closeAndRemoveSlotLocked(slotId)
    }

    /**
     * Exclusão física é uma fronteira destrutiva: o lock permanece retido de close até a
     * confirmação de remoção, impedindo que o mesmo slot seja reaberto entre essas etapas. O
     * contexto da coroutine é capturado antes do monitor e revalidado imediatamente antes de
     * `Context.deleteDatabase()`; cancelamento tardio preserva o arquivo para reconciliação futura.
     */
    suspend fun deleteSlotDatabase(slotId: String): Boolean {
        val coroutineContext = currentCoroutineContext()
        return synchronized(slotLifecycleLock(slotId)) {
            closeAndRemoveSlotLocked(slotId)
            val databaseFile = databaseFileForSlot(slotId)
            val sidecars = databaseSidecarFiles(databaseFile)
            val hadPhysicalArtifact = databaseFile.exists() || sidecars.any { it.exists() }

            coroutineContext.ensureActive()
            context.deleteDatabase(databaseNameForSlot(slotId))
            sidecars.forEach { sidecar -> if (sidecar.exists()) sidecar.delete() }

            val allArtifactsRemoved = !databaseFile.exists() && sidecars.none { it.exists() }
            hadPhysicalArtifact && allArtifactsRemoved
        }
    }

    @Synchronized
    fun closeAllDatabases() {
        repositories.clear()
        databaseFactory.closeAllDatabases()
    }
}
