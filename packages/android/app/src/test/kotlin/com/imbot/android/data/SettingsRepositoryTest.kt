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

    private class TestSettingsRepository(
        preferences: SharedPreferences,
        securePreferences: SharedPreferences,
    ) : SettingsRepository(preferences, securePreferences)
}
