package com.imbot.android.data

import android.content.SharedPreferences
import com.imbot.android.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun `load migrates legacy token into encrypted preferences`() {
        val legacyPreferences =
            FakeSharedPreferences().apply {
                edit()
                    .putString("relay_url", "https://relay.example.com")
                    .putString("token", "legacy-token")
                    .apply()
            }
        val securePreferences = FakeSharedPreferences()
        val repository = TestSettingsRepository(legacyPreferences, securePreferences)

        val settings = repository.load()

        assertEquals("https://relay.example.com", settings.relayUrl)
        assertEquals("legacy-token", settings.token)
        assertEquals("legacy-token", securePreferences.getString("token", null))
        assertFalse(legacyPreferences.contains("token"))
        assertNull(legacyPreferences.getString("token", null))
    }

    @Test
    fun `load returns empty token when both prefs are empty`() {
        val legacyPreferences = FakeSharedPreferences()
        val securePreferences = FakeSharedPreferences()
        val repository = TestSettingsRepository(legacyPreferences, securePreferences)

        val settings = repository.load()

        assertEquals("", settings.token)
        assertNull(securePreferences.getString("token", null))
    }

    @Test
    fun `load uses secure token when already present and ignores legacy`() {
        val legacyPreferences =
            FakeSharedPreferences().apply {
                edit()
                    .putString("relay_url", "https://relay.example.com")
                    .putString("token", "stale-legacy")
                    .apply()
            }
        val securePreferences =
            FakeSharedPreferences().apply {
                edit()
                    .putString("token", "secure-token")
                    .apply()
            }
        val repository = TestSettingsRepository(legacyPreferences, securePreferences)

        val settings = repository.load()

        assertEquals("secure-token", settings.token)
    }

    private class TestSettingsRepository(
        preferences: SharedPreferences,
        securePreferences: SharedPreferences,
    ) : SettingsRepository(preferences, securePreferences)
}
