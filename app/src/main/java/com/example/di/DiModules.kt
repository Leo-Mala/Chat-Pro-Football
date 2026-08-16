package com.example.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.data.GamePreferencesRepository
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.data.PlayerMoshiAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(PlayerMoshiAdapter())
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideSlotDatabaseFactory(
        @ApplicationContext context: Context
    ): SlotDatabaseFactory {
        return SlotDatabaseFactory(context)
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun provideGamePreferencesRepository(
        dataStore: DataStore<Preferences>,
        @ApplicationContext context: Context
    ): GamePreferencesRepository {
        return GamePreferencesRepository(dataStore, context)
    }

    @Provides
    @Singleton
    fun provideGameSaveRepository(
        @ApplicationContext context: Context,
        slotDatabaseFactory: SlotDatabaseFactory
    ): GameSaveRepository {
        return GameSaveRepository(context, slotDatabaseFactory)
    }
}
