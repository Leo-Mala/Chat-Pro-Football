package com.example.data

import android.content.Context
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
    version = 22,
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

        fun getDatabaseWithName(context: Context, name: String): AppDatabase {
            return buildDatabaseWithName(context, name)
        }

        fun getDatabase(context: Context): AppDatabase {
            return buildDatabaseWithName(context, SlotDatabaseFactory.LEGACY_SLOT_1_DATABASE_NAME)
        }
    }
}
