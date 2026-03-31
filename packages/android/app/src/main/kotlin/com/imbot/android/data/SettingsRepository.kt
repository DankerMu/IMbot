package com.imbot.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.imbot.android.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

data class RelaySettings(
    val relayUrl: String,
    val token: String,
) {
    fun isConfigured(): Boolean = relayUrl.isNotBlank() && token.isNotBlank()
}

@Singleton
@Suppress("TooManyFunctions")
open class SettingsRepository
    protected constructor(
        private val preferences: SharedPreferences,
        private val securePreferences: SharedPreferences = preferences,
    ) {
        @Inject
        constructor(
            @ApplicationContext context: Context,
        ) : this(
            preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            securePreferences = createSecurePreferences(context.applicationContext),
        )

        open fun load(): RelaySettings =
            RelaySettings(
                relayUrl = preferences.getString(KEY_RELAY_URL, BuildConfig.DEFAULT_RELAY_URL).orEmpty(),
                token = securePreferences.getString(KEY_TOKEN, "").orEmpty(),
            )

        open fun save(settings: RelaySettings) {
            preferences.edit()
                .putString(KEY_RELAY_URL, settings.relayUrl)
                .apply()
            securePreferences.edit()
                .putString(KEY_TOKEN, settings.token)
                .apply()
        }

        open fun observeRelayUrl(): Flow<String> =
            observePreference(KEY_RELAY_URL) {
                preferences.getString(KEY_RELAY_URL, BuildConfig.DEFAULT_RELAY_URL).orEmpty()
            }

        open fun observeThemeMode(): Flow<String> =
            observePreference(KEY_THEME_MODE) {
                loadThemeMode()
            }

        open fun loadThemeMode(): String =
            preferences.getString(KEY_THEME_MODE, THEME_MODE_SYSTEM)
                ?.takeIf { it in SUPPORTED_THEME_MODES }
                ?: THEME_MODE_SYSTEM

        open fun saveThemeMode(mode: String) {
            val normalizedMode = mode.takeIf { it in SUPPORTED_THEME_MODES } ?: THEME_MODE_SYSTEM
            preferences.edit()
                .putString(KEY_THEME_MODE, normalizedMode)
                .apply()
        }

        open fun loadSessionProviderFilter(): String? =
            preferences.getString(KEY_SESSION_PROVIDER_FILTER, null)
                ?.takeIf { it in SUPPORTED_PROVIDER_FILTERS }

        open fun saveSessionProviderFilter(provider: String?) {
            preferences.edit().apply {
                if (provider.isNullOrBlank()) {
                    remove(KEY_SESSION_PROVIDER_FILTER)
                } else {
                    putString(KEY_SESSION_PROVIDER_FILTER, provider)
                }
            }.apply()
        }

        open fun loadPendingPushToken(): String? =
            preferences.getString(KEY_PENDING_PUSH_TOKEN, null)
                ?.takeIf { it.isNotBlank() }

        open fun savePendingPushToken(token: String) {
            val normalizedToken = token.trim()
            if (normalizedToken.isBlank()) {
                return
            }

            preferences.edit()
                .putString(KEY_PENDING_PUSH_TOKEN, normalizedToken)
                .apply()
        }

        open fun clearPendingPushToken(token: String? = null) {
            val currentToken = loadPendingPushToken()
            if (token != null && currentToken != token) {
                return
            }

            preferences.edit()
                .remove(KEY_PENDING_PUSH_TOKEN)
                .apply()
        }

        private fun observePreference(
            key: String,
            valueProvider: () -> String,
        ): Flow<String> =
            callbackFlow {
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                        if (changedKey == null || changedKey == key) {
                            trySend(valueProvider())
                        }
                    }
                preferences.registerOnSharedPreferenceChangeListener(listener)
                trySend(valueProvider())
                awaitClose {
                    preferences.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }.conflate().distinctUntilChanged()

        companion object {
            const val THEME_MODE_SYSTEM = "system"
            const val THEME_MODE_LIGHT = "light"
            const val THEME_MODE_DARK = "dark"

            private const val PREFS_NAME = "imbot_settings"
            private const val KEY_RELAY_URL = "relay_url"
            private const val KEY_TOKEN = "token"
            private const val KEY_THEME_MODE = "theme_mode"
            private const val KEY_PENDING_PUSH_TOKEN = "pending_push_token"
            private const val KEY_SESSION_PROVIDER_FILTER = "session_provider_filter"

            private val SUPPORTED_PROVIDER_FILTERS = setOf("claude", "book", "openclaw")
            private val SUPPORTED_THEME_MODES = setOf(THEME_MODE_SYSTEM, THEME_MODE_LIGHT, THEME_MODE_DARK)
        }
    }

private fun createSecurePreferences(context: Context): SharedPreferences {
    val masterKey =
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    return EncryptedSharedPreferences.create(
        context,
        "imbot_secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}
