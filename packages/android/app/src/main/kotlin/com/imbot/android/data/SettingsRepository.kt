package com.imbot.android.data

import android.content.Context
import android.content.SharedPreferences
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
open class SettingsRepository
    protected constructor(
        private val preferences: SharedPreferences,
    ) {
        @Inject
        constructor(
            @ApplicationContext context: Context,
        ) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

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

        fun loadSessionProviderFilter(): String? =
            preferences.getString(KEY_SESSION_PROVIDER_FILTER, null)
                ?.takeIf { it in SUPPORTED_PROVIDER_FILTERS }

        fun saveSessionProviderFilter(provider: String?) {
            preferences.edit().apply {
                if (provider.isNullOrBlank()) {
                    remove(KEY_SESSION_PROVIDER_FILTER)
                } else {
                    putString(KEY_SESSION_PROVIDER_FILTER, provider)
                }
            }.apply()
        }

        fun loadPendingPushToken(): String? =
            preferences.getString(KEY_PENDING_PUSH_TOKEN, null)
                ?.takeIf { it.isNotBlank() }

        fun savePendingPushToken(token: String) {
            val normalizedToken = token.trim()
            if (normalizedToken.isBlank()) {
                return
            }

            preferences.edit()
                .putString(KEY_PENDING_PUSH_TOKEN, normalizedToken)
                .apply()
        }

        fun clearPendingPushToken(token: String? = null) {
            val currentToken = loadPendingPushToken()
            if (token != null && currentToken != token) {
                return
            }

            preferences.edit()
                .remove(KEY_PENDING_PUSH_TOKEN)
                .apply()
        }

        private companion object {
            const val PREFS_NAME = "imbot_settings"
            const val KEY_RELAY_URL = "relay_url"
            const val KEY_TOKEN = "token"
            const val KEY_PENDING_PUSH_TOKEN = "pending_push_token"
            const val KEY_SESSION_PROVIDER_FILTER = "session_provider_filter"

            val SUPPORTED_PROVIDER_FILTERS = setOf("claude", "book", "openclaw")
        }
    }
