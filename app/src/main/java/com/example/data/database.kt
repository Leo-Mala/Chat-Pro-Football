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
    version = 19,
    exportSchema = true
)
@TypeConverters(AtributosConverter::class)
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
        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19
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
