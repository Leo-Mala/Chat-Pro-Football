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
 * sucesso, é a única linha da tabela e `playerTeamId` referencia um clube persistido. O marcador
 * `Team.isPlayerControlled` é uma projeção derivada e não participa da decisão de existência da
 * carreira. A mera existência do arquivo SQLite nunca é usada como prova de carreira.
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
     * pré-carreira. Uma carreira válida exige exatamente `id=1` e um clube persistido para
     * `playerTeamId`. O próprio `GameSave` é autoritativo para identificar o clube do jogador;
     * `Team.isPlayerControlled` é derivado e pode estar ausente/divergente em saves históricos ou
     * restores parciais sem que isso autorize classificar a carreira como vazia. Quando o marcador
     * diverge, ele é reconciliado de forma determinística a partir de `playerTeamId` antes de o
     * repositório ser entregue aos consumidores de gameplay.
     *
     * Linhas não canônicas de `game_save` ou ausência do clube referenciado continuam fail-closed.
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
        val playerTeamExists = sqlite
            .query(
                "SELECT 1 FROM teams WHERE id = ? LIMIT 1",
                arrayOf(playerTeamId)
            )
            .use { cursor -> cursor.moveToFirst() }
        if (!playerTeamExists) {
            return SlotDatabaseInspection(
                state = SlotDatabaseState.RECOVERY_REQUIRED,
                failureReason = "MissingReferencedTeam:playerTeamId=$playerTeamId"
            )
        }

        return reconcilePlayerControlledProjection(database, playerTeamId)
    }

    /**
     * `isPlayerControlled` é estado derivado do `GameSave`, não uma segunda autoridade de carreira.
     * Saves históricos podem chegar com marcador ausente, obsoleto ou duplicado; nesses casos a
     * abertura continua válida e a projeção é normalizada atomicamente para exatamente o clube
     * referenciado por `playerTeamId`. Se a projeção não puder ser persistida, falhamos fechado para
     * não entregar uma carreira que seria tratada como CPU por rotinas de partida.
     */
    private fun reconcilePlayerControlledProjection(
        database: AppDatabase,
        playerTeamId: Long
    ): SlotDatabaseInspection? {
        val sqlite = database.openHelper.writableDatabase

        fun controlledTeamIds(): List<Long> = sqlite
            .query("SELECT id FROM teams WHERE isPlayerControlled = 1 ORDER BY id")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.getLong(0))
                    }
                }
            }

        val before = controlledTeamIds()
        if (before == listOf(playerTeamId)) return null

        try {
            sqlite.beginTransaction()
            try {
                sqlite.execSQL(
                    "UPDATE teams SET isPlayerControlled = CASE WHEN id = ? THEN 1 ELSE 0 END",
                    arrayOf<Any?>(playerTeamId)
                )
                sqlite.setTransactionSuccessful()
            } finally {
                sqlite.endTransaction()
            }
        } catch (e: Exception) {
            Log.e(
                "GameSaveRepository",
                "Falha ao reconciliar isPlayerControlled para playerTeamId=$playerTeamId",
                e
            )
            return SlotDatabaseInspection(
                state = SlotDatabaseState.RECOVERY_REQUIRED,
                failureReason = "ControlledTeamProjectionRepairFailed:${e.javaClass.simpleName}"
            )
        }

        val after = controlledTeamIds()
        return if (after == listOf(playerTeamId)) {
            null
        } else {
            SlotDatabaseInspection(
                state = SlotDatabaseState.RECOVERY_REQUIRED,
                failureReason = "ControlledTeamProjectionRepairDidNotConverge:ids=${after.joinToString(",")}"
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
     * Fecha a segunda metade da inspeção contra mudanças de lifecycle do mesmo slot. Se o Room que
     * lemos deixou de ser o repositório publicado, ou o arquivo foi removido enquanto as queries
     * suspensivas rodavam, nunca devolvemos um snapshot jogável baseado em um handle obsoleto.
     */
    private fun finalizeInspectionAgainstLifecycle(
        slotId: String,
        repository: GameRepository,
        candidate: SlotDatabaseInspection
    ): SlotDatabaseInspection = synchronized(slotLifecycleLock(slotId)) {
        physicalRecoveryInspection(slotId)?.let { return@synchronized it }
        val file = databaseFileForSlot(slotId)
        if (!file.exists()) {
            return@synchronized SlotDatabaseInspection(SlotDatabaseState.MISSING)
        }
        if (repositories[slotId] !== repository) {
            return@synchronized SlotDatabaseInspection(
                state = SlotDatabaseState.RECOVERY_REQUIRED,
                failureReason = "SlotLifecycleChangedDuringInspection"
            )
        }
        candidate
    }

    /**
     * Inspeciona o conteúdo real do slot sem criar banco para um arquivo inexistente.
     * `game_save(id=1)` é a autoridade e só é válida quando é a única linha e `playerTeamId`
     * referencia um clube persistido. O flag `isPlayerControlled` é derivado: divergências são
     * reconciliadas a partir de `GameSave` e nunca transformam, por si só, a carreira em slot vazio.
     *
     * A decisão `arquivo existente -> abrir Room` ocorre sob o MESMO lock de lifecycle usado pela
     * exclusão física. Assim uma inspeção que viu o arquivo antes de um delete nunca pode acordar
     * depois do delete e recriar silenciosamente um banco vazio apenas para concluir a leitura.
     */
    suspend fun inspectSlot(slotId: String): SlotDatabaseInspection {
        val file = databaseFileForSlot(slotId)
        val repository = try {
            synchronized(slotLifecycleLock(slotId)) {
                physicalRecoveryInspection(slotId)?.let { return it }
                if (!file.exists()) return SlotDatabaseInspection(SlotDatabaseState.MISSING)
                // O monitor Java é reentrante; getRepositoryForSlot usa o mesmo lock e por isso
                // mantém atômica a transição entre revalidar a existência e abrir/obter Room.
                getRepositoryForSlot(slotId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SlotRecoveryRequiredException) {
            return e.inspection
        } catch (e: Exception) {
            Log.e("GameSaveRepository", "Falha ao abrir banco para inspeção do slot $slotId", e)
            return synchronized(slotLifecycleLock(slotId)) {
                physicalRecoveryInspection(slotId)
                    ?: if (!file.exists()) {
                        SlotDatabaseInspection(SlotDatabaseState.MISSING)
                    } else {
                        SlotDatabaseInspection(
                            state = SlotDatabaseState.RECOVERY_REQUIRED,
                            failureReason = e.javaClass.simpleName.ifBlank { "DatabaseOpenFailure" }
                        )
                    }
            }
        }

        return try {
            semanticRecoveryInspection(repository.db)?.let { inspection ->
                return finalizeInspectionAgainstLifecycle(slotId, repository, inspection)
            }
            val save = repository.getGameSave()
            val gameSaveRowCount = countGameSaveRows(repository)
            val candidate = when {
                gameSaveRowCount == 0 && save == null -> SlotDatabaseInspection(SlotDatabaseState.EMPTY)
                gameSaveRowCount == 1 && save != null && save.id == 1 -> {
                    val team = repository.getTeam(save.playerTeamId)
                    if (team == null) {
                        SlotDatabaseInspection(
                            state = SlotDatabaseState.RECOVERY_REQUIRED,
                            failureReason = "MissingReferencedTeam:playerTeamId=${save.playerTeamId}"
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
            finalizeInspectionAgainstLifecycle(slotId, repository, candidate)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SlotRecoveryRequiredException) {
            finalizeInspectionAgainstLifecycle(slotId, repository, e.inspection)
        } catch (e: Exception) {
            Log.e("GameSaveRepository", "Falha ao inspecionar banco do slot $slotId", e)
            finalizeInspectionAgainstLifecycle(
                slotId,
                repository,
                SlotDatabaseInspection(
                    state = SlotDatabaseState.RECOVERY_REQUIRED,
                    failureReason = e.javaClass.simpleName.ifBlank { "DatabaseReadFailure" }
                )
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
