package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.local.SlotDatabaseFactory

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
        PlayerLoan::class
    ],
    version = 17,
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

    companion object {
        fun buildDatabaseWithName(context: Context, name: String): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                name
            )
            .addMigrations(
                com.example.data.migrations.MIGRATION_14_15,
                com.example.data.migrations.MIGRATION_15_16,
                com.example.data.migrations.MIGRATION_16_17
            )
            .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
            .fallbackToDestructiveMigrationOnDowngrade()
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
