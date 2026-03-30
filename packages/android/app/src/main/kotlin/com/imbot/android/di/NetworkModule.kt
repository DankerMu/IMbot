package com.imbot.android.di

import com.imbot.android.network.RelayHttpClient
import com.imbot.android.network.RelayWsClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
}
