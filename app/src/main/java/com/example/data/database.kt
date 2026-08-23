package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.local.SlotDatabaseFactory
import com.example.data.migrations.MIGRATION_14_15
import com.example.data.migrations.MIGRATION_15_16
import com.example.data.migrations.MIGRATION_16_17
import com.example.data.migrations.MIGRATION_17_18
import com.example.data.migrations.MIGRATION_18_19
import com.example.data.migrations.MIGRATION_19_20
import com.example.data.migrations.MIGRATION_20_21
import com.example.data.migrations.MIGRATION_21_22
import java.io.File

const val APP_DATABASE_SCHEMA_VERSION = 22

@Database(
    entities = [
        GameSave::class,
        Team::class,
        Player::class,
        Fixture::class,
        ClubLegend::class,
        HistoricalRecord::class,
        CoachOffer::class,
        TransactionRecord::class,
        TransferOrder::class,
        HistoricoEvolucao::class,
        TransferInstallment::class,
        PlayerLoan::class,
        GlobalLeagueStanding::class
    ],
    version = APP_DATABASE_SCHEMA_VERSION,
    exportSchema = true
)
@TypeConverters(AtributosConverter::class, MatchSlotConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameSaveDao(): GameSaveDao
    abstract fun teamDao(): TeamDao
    abstract fun playerDao(): PlayerDao
    abstract fun fixtureDao(): FixtureDao
    abstract fun clubLegendDao(): ClubLegendDao
    abstract fun historicalRecordDao(): HistoricalRecordDao
    abstract fun coachOfferDao(): CoachOfferDao
    abstract fun transactionRecordDao(): TransactionRecordDao
    abstract fun transferOrderDao(): TransferOrderDao
    abstract fun historicoEvolucaoDao(): HistoricoEvolucaoDao
    abstract fun transferInstallmentDao(): TransferInstallmentDao
    abstract fun playerLoanDao(): PlayerLoanDao
    abstract fun globalLeagueStandingDao(): GlobalLeagueStandingDao

    companion object {
        /**
         * Não há definição histórica confiável de schema anterior à V14 no repositório atual.
         * Versões abaixo disso falham de modo seguro e o arquivo físico é preservado.
         */
        const val MINIMUM_AUTOMATICALLY_MIGRATABLE_VERSION = 14

        private val SQLITE_FILE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        private val SQLITE_SIDECAR_SUFFIXES = listOf("-wal", "-shm", "-journal")

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
            MIGRATION_20_21,
            MIGRATION_21_22
        )

        /**
         * Proteção fail-closed para callers legados que ainda abrem um banco pelo nome físico.
         * Um restore parcial não pode deixar Room materializar um arquivo novo sobre um artefato
         * truncado, não inicializado ou sobre sidecars órfãos antes que a reconciliação consiga
         * classificá-lo.
         */
        private fun requireLegacyPhysicalOpenAllowed(context: Context, name: String) {
            val databaseFile = context.applicationContext.getDatabasePath(name)
            val sidecars = SQLITE_SIDECAR_SUFFIXES.map { suffix -> File(databaseFile.path + suffix) }

            if (!databaseFile.exists()) {
                val orphaned = sidecars.filter { it.exists() }
                check(orphaned.isEmpty()) {
                    "Database recovery required: orphaned SQLite sidecar(s) ${orphaned.joinToString { it.name }}"
                }
                return
            }

            check(databaseFile.length() > 0L) {
                "Database recovery required: zero-length SQLite file ${databaseFile.name}"
            }

            val canonicalHeader = try {
                databaseFile.inputStream().use { input ->
                    val actual = ByteArray(SQLITE_FILE_HEADER.size)
                    var offset = 0
                    while (offset < actual.size) {
                        val read = input.read(actual, offset, actual.size - offset)
                        if (read <= 0) return@use false
                        offset += read
                    }
                    actual.contentEquals(SQLITE_FILE_HEADER)
                }
            } catch (_: Exception) {
                false
            }

            check(canonicalHeader) {
                "Database recovery required: invalid SQLite header ${databaseFile.name}"
            }

            val userVersion = try {
                val rawDatabase = SQLiteDatabase.openDatabase(
                    databaseFile.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )
                try {
                    rawDatabase.rawQuery("PRAGMA user_version", null).use { cursor ->
                        check(cursor.moveToFirst()) {
                            "Database recovery required: unreadable user_version ${databaseFile.name}"
                        }
                        cursor.getInt(0)
                    }
                } finally {
                    rawDatabase.close()
                }
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Database recovery required: unreadable SQLite container ${databaseFile.name}",
                    e
                )
            }

            check(userVersion in MINIMUM_AUTOMATICALLY_MIGRATABLE_VERSION..APP_DATABASE_SCHEMA_VERSION) {
                "Database recovery required: unsupported/uninitialized schema version $userVersion in ${databaseFile.name}"
            }
        }

        fun buildDatabaseWithName(context: Context, name: String): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                name
            )
                .addMigrations(*ALL_MIGRATIONS)
                // Intencionalmente sem fallback destrutivo: uma versão desconhecida deve
                // falhar ao abrir em vez de apagar silenciosamente uma carreira.
                .build()
        }

        /**
         * Caller legado também recebe uma instância eager. O preflight é repetido imediatamente
         * antes do primeiro open e novamente antes de entregar o handle, fechando a janela em que
         * `build()` (lazy) poderia devolver um Room ainda não validado.
         */
        fun getDatabaseWithName(context: Context, name: String): AppDatabase {
            requireLegacyPhysicalOpenAllowed(context, name)
            val database = buildDatabaseWithName(context, name)
            try {
                requireLegacyPhysicalOpenAllowed(context, name)
                database.openHelper.writableDatabase
                requireLegacyPhysicalOpenAllowed(context, name)
                return database
            } catch (e: Exception) {
                database.close()
                throw e
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return getDatabaseWithName(context, SlotDatabaseFactory.LEGACY_SLOT_1_DATABASE_NAME)
        }
    }
}
