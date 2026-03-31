package com.imbot.android.di

import android.content.Context
import androidx.room.Room
import com.imbot.android.data.local.AppDatabase
import com.imbot.android.data.local.SessionDao
import com.imbot.android.data.repository.SessionRepository
import com.imbot.android.data.repository.SessionStore
import com.imbot.android.network.RelayHttpClient
import com.imbot.android.network.RelayWsClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    fun provideRelayWsClient(okHttpClient: OkHttpClient): RelayWsClient = RelayWsClient(okHttpClient)

    @Provides
    @Singleton
    fun provideRelayHttpClient(okHttpClient: OkHttpClient): RelayHttpClient = RelayHttpClient(okHttpClient)

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "imbot.db",
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideSessionDao(appDatabase: AppDatabase): SessionDao = appDatabase.sessionDao()

    @Provides
    @Singleton
    fun provideSessionRepository(
        appDatabase: AppDatabase,
        sessionDao: SessionDao,
        relayHttpClient: RelayHttpClient,
        settingsRepository: com.imbot.android.data.SettingsRepository,
    ): SessionRepository =
        SessionRepository(
            database = appDatabase,
            sessionDao = sessionDao,
            relayHttpClient = relayHttpClient,
            settingsRepository = settingsRepository,
        )

    @Provides
    @Singleton
    fun provideSessionStore(sessionRepository: SessionRepository): SessionStore = sessionRepository
}
