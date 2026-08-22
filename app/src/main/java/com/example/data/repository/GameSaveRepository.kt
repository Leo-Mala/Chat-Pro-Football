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
 * sucesso. A mera existência do arquivo SQLite nunca é usada como prova de carreira.
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
 * Gerencia instâncias estáveis de GameRepository/AppDatabase por slot.
 *
 * Um slot nunca deve fechar ou substituir o banco de outro slot. Isso evita que
 * Flows e corrotinas em andamento percam o banco quando a UI troca de tela/save.
 */
@Singleton
class GameSaveRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseFactory: SlotDatabaseFactory
) {
    companion object {
        private val SQLITE_FILE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }

    private val repositories = mutableMapOf<String, GameRepository>()

    @Synchronized
    fun getDatabaseForSlot(slotId: String): AppDatabase {
        return databaseFactory.getDatabaseForSlot(slotId)
    }

    @Synchronized
    fun getRepositoryForSlot(slotId: String): GameRepository {
        return repositories.getOrPut(slotId) {
            GameRepository(getDatabaseForSlot(slotId))
        }
    }

    fun databaseNameForSlot(slotId: String): String {
        return SlotDatabaseFactory.databaseNameForSlot(slotId)
    }

    fun databaseFileForSlot(slotId: String): File {
        return context.getDatabasePath(databaseNameForSlot(slotId))
    }

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

    /**
     * Inspeciona o conteúdo real do slot sem criar banco para um arquivo inexistente.
     *
     * Contrato de autoridade:
     * - `game_save(id=1)` é a única prova de uma carreira válida;
     * - tabelas de times/jogadores/fixtures podem existir antes da criação da carreira porque a UI
     *   pré-semeia o universo para a seleção de clube, portanto não transformam um slot em save;
     * - arquivo ausente -> MISSING;
     * - banco legível sem `GameSave` -> EMPTY / estado pré-carreira;
     * - `GameSave` legível -> VALID_CAREER;
     * - arquivo truncado, com cabeçalho SQLite inválido, ou falha de abertura/migration/leitura ->
     *   RECOVERY_REQUIRED.
     *
     * O cabeçalho físico é validado antes de abrir Room para que um restore parcial/arquivo
     * corrompido nunca seja silenciosamente recriado pelo SQLite e confundido com banco vazio.
     * Assim, conteúdo derivado/reconstruível nunca vira uma segunda fonte de verdade, enquanto
     * corrupção ou incompatibilidade continuam fail-closed e nunca autorizam reseed destrutivo.
     */
    suspend fun inspectSlot(slotId: String): SlotDatabaseInspection {
        val file = databaseFileForSlot(slotId)
        if (!file.exists()) {
            return SlotDatabaseInspection(SlotDatabaseState.MISSING)
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

        return try {
            val repository = getRepositoryForSlot(slotId)
            val save = repository.getGameSave()
            if (save == null) {
                SlotDatabaseInspection(SlotDatabaseState.EMPTY)
            } else {
                val teamName = repository.getTeam(save.playerTeamId)?.name ?: "Sem Clube"
                SlotDatabaseInspection(
                    state = SlotDatabaseState.VALID_CAREER,
                    save = save,
                    teamName = teamName
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("GameSaveRepository", "Falha ao inspecionar banco do slot $slotId", e)
            SlotDatabaseInspection(
                state = SlotDatabaseState.RECOVERY_REQUIRED,
                failureReason = e.javaClass.simpleName.ifBlank { "DatabaseReadFailure" }
            )
        }
    }

    /** Fail-closed preflight usado antes de qualquer criação destrutiva de nova carreira. */
    suspend fun isNewGameAllowed(slotId: String): Boolean = inspectSlot(slotId).newGameAllowed

    /**
     * Forces committed WAL pages into the main database file before a local snapshot.
     */
    @Synchronized
    fun checkpointSlot(slotId: String) {
        val db = getDatabaseForSlot(slotId)
        db.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(FULL)")
            .use { cursor ->
                if (cursor.moveToFirst()) {
                    // Reading the result ensures the pragma completed before returning.
                    cursor.getInt(0)
                }
            }
    }

    @Synchronized
    fun closeAndRemoveSlot(slotId: String) {
        repositories.remove(slotId)
        databaseFactory.closeAndRemoveSlot(slotId)
    }

    @Synchronized
    fun deleteSlotDatabase(slotId: String): Boolean {
        closeAndRemoveSlot(slotId)
        return context.deleteDatabase(databaseNameForSlot(slotId))
    }

    @Synchronized
    fun closeAllDatabases() {
        repositories.clear()
        databaseFactory.closeAllDatabases()
    }
}
