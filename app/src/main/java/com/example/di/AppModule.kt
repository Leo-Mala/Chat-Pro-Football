package com.example.di

import com.example.usecase.TacticsUseCase
import com.example.usecase.YouthAcademyUseCase
import com.example.util.DefaultDispatcherProvider
import com.example.util.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider {
        return DefaultDispatcherProvider()
    }

    @Provides
    @Singleton
    fun provideYouthAcademyUseCase(): YouthAcademyUseCase {
        return YouthAcademyUseCase()
    }

    @Provides
    @Singleton
    fun provideTacticsUseCase(): TacticsUseCase {
        return TacticsUseCase()
    }
}

