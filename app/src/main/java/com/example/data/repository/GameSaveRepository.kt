package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.local.SlotDatabaseFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

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
 * sucesso e é a única linha da tabela. A mera existência do arquivo SQLite nunca é usada como
 * prova de carreira.
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

    private val repositories = mutableMapOf<String, GameRepository>()

    private fun requirePhysicalOpenAllowed(slotId: String) {
        physicalRecoveryInspection(slotId)?.let { inspection ->
            throw SlotRecoveryRequiredException(inspection)
        }
    }

    /**
     * Depois que Room materializa a tabela, zero linhas ou exatamente `id=1` são as únicas formas
     * que podem prosseguir para uso normal. Qualquer outra combinação é corrupção/restore parcial
     * e precisa ser preservada antes de seed, repair ou qualquer mutação de UI.
     */
    private fun semanticRecoveryInspection(database: AppDatabase): SlotDatabaseInspection? {
        val ids = database.openHelper.readableDatabase
            .query("SELECT id FROM game_save ORDER BY id")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getInt(0))
                }
            }
        return if (ids.isEmpty() || ids == listOf(1)) {
            null
        } else {
            SlotDatabaseInspection(
                state = SlotDatabaseState.RECOVERY_REQUIRED,
                failureReason = "UnexpectedGameSaveRows:ids=${ids.joinToString(",")}"
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
     * da validação da tabela autoritativa.
     */
    @Synchronized
    fun getDatabaseForSlot(slotId: String): AppDatabase {
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
        return database
    }

    @Synchronized
    fun getRepositoryForSlot(slotId: String): GameRepository {
        requirePhysicalOpenAllowed(slotId)
        repositories[slotId]?.let { return it }

        val database = databaseFactory.getDatabaseForSlot(slotId)
        try {
            requirePhysicalOpenAllowed(slotId)
            database.openHelper.writableDatabase
            requireSemanticUseAllowed(database)
        } catch (e: Exception) {
            databaseFactory.closeAndRemoveSlot(slotId)
            throw e
        }

        return GameRepository(database).also { repositories[slotId] = it }
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
        return null
    }

    private fun countGameSaveRows(repository: GameRepository): Int =
        repository.db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM game_save")
            .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    /**
     * Inspeciona o conteúdo real do slot sem criar banco para um arquivo inexistente.
     * `game_save(id=1)` é a autoridade e só é válida quando é a única linha da tabela.
     */
    suspend fun inspectSlot(slotId: String): SlotDatabaseInspection {
        val file = databaseFileForSlot(slotId)
        physicalRecoveryInspection(slotId)?.let { return it }
        if (!file.exists()) return SlotDatabaseInspection(SlotDatabaseState.MISSING)

        return try {
            val repository = getRepositoryForSlot(slotId)
            val save = repository.getGameSave()
            val gameSaveRowCount = countGameSaveRows(repository)
            when {
                gameSaveRowCount == 0 && save == null -> SlotDatabaseInspection(SlotDatabaseState.EMPTY)
                gameSaveRowCount == 1 && save != null && save.id == 1 -> {
                    val teamName = repository.getTeam(save.playerTeamId)?.name ?: "Sem Clube"
                    SlotDatabaseInspection(
                        state = SlotDatabaseState.VALID_CAREER,
                        save = save,
                        teamName = teamName
                    )
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

    @Synchronized
    fun checkpointSlot(slotId: String) {
        val db = getDatabaseForSlot(slotId)
        db.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(FULL)")
            .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) }
    }

    @Synchronized
    fun closeAndRemoveSlot(slotId: String) {
        repositories.remove(slotId)
        databaseFactory.closeAndRemoveSlot(slotId)
    }

    @Synchronized
    fun deleteSlotDatabase(slotId: String): Boolean {
        closeAndRemoveSlot(slotId)
        val databaseFile = databaseFileForSlot(slotId)
        val sidecars = databaseSidecarFiles(databaseFile)
        val hadPhysicalArtifact = databaseFile.exists() || sidecars.any { it.exists() }

        context.deleteDatabase(databaseNameForSlot(slotId))
        sidecars.forEach { sidecar -> if (sidecar.exists()) sidecar.delete() }

        val allArtifactsRemoved = !databaseFile.exists() && sidecars.none { it.exists() }
        return hadPhysicalArtifact && allArtifactsRemoved
    }

    @Synchronized
    fun closeAllDatabases() {
        repositories.clear()
        databaseFactory.closeAllDatabases()
    }
}
