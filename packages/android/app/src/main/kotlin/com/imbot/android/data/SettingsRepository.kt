package com.imbot.android.data

import android.content.Context
import com.imbot.android.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class RelaySettings(
    val relayUrl: String,
    val token: String,
) {
    fun isConfigured(): Boolean = relayUrl.isNotBlank() && token.isNotBlank()
}

@Singleton
class SettingsRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun load(): RelaySettings =
            RelaySettings(
                relayUrl = preferences.getString(KEY_RELAY_URL, BuildConfig.DEFAULT_RELAY_URL).orEmpty(),
                token = preferences.getString(KEY_TOKEN, "").orEmpty(),
            )

        fun save(settings: RelaySettings) {
            preferences.edit()
                .putString(KEY_RELAY_URL, settings.relayUrl)
                .putString(KEY_TOKEN, settings.token)
                .apply()
        }

        private companion object {
            const val PREFS_NAME = "imbot_settings"
            const val KEY_RELAY_URL = "relay_url"
            const val KEY_TOKEN = "token"
        }
    }
