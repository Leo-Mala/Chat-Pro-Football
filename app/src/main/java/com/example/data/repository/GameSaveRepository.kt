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
 * Impede que qualquer chamador abra Room sobre um conjunto físico que já exige recuperação.
 * A exceção carrega a inspeção que motivou o bloqueio para que a UI possa reconciliar o slot sem
 * tocar nos artefatos recuperáveis.
 */
class SlotRecoveryRequiredException(
    val inspection: SlotDatabaseInspection
) : IllegalStateException(
    "Slot exige recuperação antes da abertura: ${inspection.failureReason ?: inspection.state.name}"
)

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
        private val SQLITE_SIDECAR_SUFFIXES = listOf("-wal", "-shm", "-journal")
    }

    private val repositories = mutableMapOf<String, GameRepository>()

    private fun requirePhysicalOpenAllowed(slotId: String) {
        physicalRecoveryInspection(slotId)?.let { inspection ->
            throw SlotRecoveryRequiredException(inspection)
        }
    }

    /**
     * Abertura de Room é intencionalmente eager neste ponto. `Room.databaseBuilder().build()` é
     * lazy e deixaria uma janela na qual o arquivo principal ainda não existe depois do preflight;
     * forçar `writableDatabase` materializa o SQLite validado antes de entregar a instância.
     */
    @Synchronized
    fun getDatabaseForSlot(slotId: String): AppDatabase {
        requirePhysicalOpenAllowed(slotId)
        val database = databaseFactory.getDatabaseForSlot(slotId)
        requirePhysicalOpenAllowed(slotId)
        database.openHelper.writableDatabase
        return database
    }

    @Synchronized
    fun getRepositoryForSlot(slotId: String): GameRepository {
        requirePhysicalOpenAllowed(slotId)
        repositories[slotId]?.let { return it }

        val database = databaseFactory.getDatabaseForSlot(slotId)
        // Segunda inspeção imediatamente antes do primeiro acesso SQLite. Se restore/filesystem
        // mudou entre build e open, a instância é fechada sem tocar no conjunto recuperável.
        try {
            requirePhysicalOpenAllowed(slotId)
            database.openHelper.writableDatabase
        } catch (e: Exception) {
            databaseFactory.closeAndRemoveSlot(slotId)
            throw e
        }

        return GameRepository(database).also { repositories[slotId] = it }
    }

    fun databaseNameForSlot(slotId: String): String {
        return SlotDatabaseFactory.databaseNameForSlot(slotId)
    }

    fun databaseFileForSlot(slotId: String): File {
        return context.getDatabasePath(databaseNameForSlot(slotId))
    }

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

    /**
     * Verificação física síncrona executável antes da primeira abertura do Room.
     *
     * Ela não cria arquivos e só devolve estados que exigem bloqueio imediato. `null` significa
     * que o conjunto físico não apresenta, por si só, um motivo de recovery; a inspeção semântica
     * ainda pode classificar o conteúdo do Room como EMPTY, VALID_CAREER ou RECOVERY_REQUIRED.
     */
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

    private fun countGameSaveRows(repository: GameRepository): Int {
        return repository.db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM game_save")
            .use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
    }

    /**
     * Inspeciona o conteúdo real do slot sem criar banco para um arquivo inexistente.
     *
     * Contrato de autoridade:
     * - `game_save(id=1)` é a única prova de uma carreira válida e deve ser a única linha da tabela;
     * - tabelas de times/jogadores/fixtures podem existir antes da criação da carreira porque a UI
     *   pré-semeia o universo para a seleção de clube, portanto não transformam um slot em save;
     * - arquivo principal e sidecars ausentes -> MISSING;
     * - banco legível com ZERO linhas em `game_save` -> EMPTY / estado pré-carreira;
     * - exatamente uma linha `GameSave(id=1)` -> VALID_CAREER;
     * - qualquer linha inesperada em `game_save`, sidecar órfão, arquivo truncado, cabeçalho SQLite
     *   inválido, ou falha de abertura/migration/leitura -> RECOVERY_REQUIRED.
     *
     * Cabeçalho e conjunto físico de arquivos são validados antes de abrir Room para que restore
     * parcial/corrupção nunca sejam silenciosamente recriados pelo SQLite e confundidos com banco
     * vazio. Conteúdo não-canônico na tabela autoritativa também nunca é apagado automaticamente.
     */
    suspend fun inspectSlot(slotId: String): SlotDatabaseInspection {
        val file = databaseFileForSlot(slotId)
        physicalRecoveryInspection(slotId)?.let { return it }
        if (!file.exists()) {
            return SlotDatabaseInspection(SlotDatabaseState.MISSING)
        }

        return try {
            val repository = getRepositoryForSlot(slotId)
            val save = repository.getGameSave()
            val gameSaveRowCount = countGameSaveRows(repository)

            when {
                gameSaveRowCount == 0 && save == null -> {
                    SlotDatabaseInspection(SlotDatabaseState.EMPTY)
                }

                gameSaveRowCount == 1 && save != null && save.id == 1 -> {
                    val teamName = repository.getTeam(save.playerTeamId)?.name ?: "Sem Clube"
                    SlotDatabaseInspection(
                        state = SlotDatabaseState.VALID_CAREER,
                        save = save,
                        teamName = teamName
                    )
                }

                else -> {
                    SlotDatabaseInspection(
                        state = SlotDatabaseState.RECOVERY_REQUIRED,
                        failureReason = "UnexpectedGameSaveRows:count=$gameSaveRowCount,canonical=${save != null}"
                    )
                }
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

    /**
     * Exclusão física é sempre uma ação explícita do usuário. Além do arquivo principal, remove
     * sidecars órfãos que possam ter sobrevivido a um restore/cópia interrompida, permitindo que o
     * slot volte de RECOVERY_REQUIRED para MISSING somente após essa decisão intencional.
     */
    @Synchronized
    fun deleteSlotDatabase(slotId: String): Boolean {
        closeAndRemoveSlot(slotId)
        val databaseFile = databaseFileForSlot(slotId)
        val sidecars = databaseSidecarFiles(databaseFile)
        val hadPhysicalArtifact = databaseFile.exists() || sidecars.any { it.exists() }

        context.deleteDatabase(databaseNameForSlot(slotId))
        sidecars.forEach { sidecar ->
            if (sidecar.exists()) {
                sidecar.delete()
            }
        }

        val allArtifactsRemoved = !databaseFile.exists() && sidecars.none { it.exists() }
        return hadPhysicalArtifact && allArtifactsRemoved
    }

    @Synchronized
    fun closeAllDatabases() {
        repositories.clear()
        databaseFactory.closeAllDatabases()
    }
}
