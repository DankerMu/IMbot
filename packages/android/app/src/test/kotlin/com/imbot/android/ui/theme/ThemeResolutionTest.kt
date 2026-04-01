package com.imbot.android.ui.theme

import com.imbot.android.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeResolutionTest {
    @Test
    fun `system mode with light os resolves light theme`() {
        val resolution =
            resolveThemeResolution(
                themeMode = SettingsRepository.THEME_MODE_SYSTEM,
                systemInDarkTheme = false,
                sdkInt = 30,
            )

        assertFalse(resolution.useDarkTheme)
        assertFalse(resolution.useDynamicColor)
        assertEquals(PrimaryLight, StaticLightColorScheme.primary)
        assertEquals(BackgroundLight, StaticLightColorScheme.background)
    }

    @Test
    fun `system mode with dark os resolves dark theme`() {
        val resolution =
            resolveThemeResolution(
                themeMode = SettingsRepository.THEME_MODE_SYSTEM,
                systemInDarkTheme = true,
                sdkInt = 30,
            )

        assertTrue(resolution.useDarkTheme)
        assertFalse(resolution.useDynamicColor)
        assertEquals(PrimaryDark, StaticDarkColorScheme.primary)
        assertEquals(BackgroundDark, StaticDarkColorScheme.background)
    }

    @Test
    fun `explicit light overrides os dark setting`() {
        val resolution =
            resolveThemeResolution(
                themeMode = SettingsRepository.THEME_MODE_LIGHT,
                systemInDarkTheme = true,
                sdkInt = 31,
            )

        assertFalse(resolution.useDarkTheme)
        assertFalse(resolution.useDynamicColor)
    }

    @Test
    fun `explicit dark overrides os light setting`() {
        val resolution =
            resolveThemeResolution(
                themeMode = SettingsRepository.THEME_MODE_DARK,
                systemInDarkTheme = false,
                sdkInt = 31,
            )

        assertTrue(resolution.useDarkTheme)
        assertFalse(resolution.useDynamicColor)
    }

    @Test
    fun `api 31 system mode enables dynamic color`() {
        val resolution =
            resolveThemeResolution(
                themeMode = SettingsRepository.THEME_MODE_SYSTEM,
                systemInDarkTheme = false,
                sdkInt = 31,
            )

        assertTrue(resolution.useDynamicColor)
    }

    @Test
    fun `api 30 system mode uses static colors`() {
        val resolution =
            resolveThemeResolution(
                themeMode = SettingsRepository.THEME_MODE_SYSTEM,
                systemInDarkTheme = false,
                sdkInt = 30,
            )

        assertFalse(resolution.useDynamicColor)
    }

    @Test
    fun `provider colors match foundation spec`() {
        val providerColors = ProviderColors()

        assertEquals(ClaudeAmber, providerColors.claude)
        assertEquals(BookViolet, providerColors.book)
        assertEquals(OpenClawRed, providerColors.openclaw)
    }

    @Test
    fun `status colors match foundation spec in light and dark`() {
        assertEquals(LightStatusColors.queued, statusColorFor("queued", LightStatusColors))
        assertEquals(LightStatusColors.running, statusColorFor("running", LightStatusColors))
        assertEquals(LightStatusColors.idle, statusColorFor("idle", LightStatusColors))
        assertEquals(LightStatusColors.completed, statusColorFor("completed", LightStatusColors))
        assertEquals(LightStatusColors.failed, statusColorFor("failed", LightStatusColors))
        assertEquals(LightStatusColors.cancelled, statusColorFor("cancelled", LightStatusColors))

        assertEquals(DarkStatusColors.queued, statusColorFor("queued", DarkStatusColors))
        assertEquals(DarkStatusColors.running, statusColorFor("running", DarkStatusColors))
        assertEquals(DarkStatusColors.idle, statusColorFor("idle", DarkStatusColors))
        assertEquals(DarkStatusColors.completed, statusColorFor("completed", DarkStatusColors))
        assertEquals(DarkStatusColors.failed, statusColorFor("failed", DarkStatusColors))
        assertEquals(DarkStatusColors.cancelled, statusColorFor("cancelled", DarkStatusColors))
    }
}
