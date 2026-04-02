package com.imbot.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        assertEquals(BrandBlue, StaticLightColorScheme.primary)
        assertEquals(Background, StaticLightColorScheme.background)
        assertEquals(SurfaceLight, StaticLightColorScheme.surface)
        assertEquals(DestructiveColor, StaticLightColorScheme.error)
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
        assertEquals(BrandBlue, StaticDarkColorScheme.primary)
        assertEquals(BackgroundDark, StaticDarkColorScheme.background)
        assertEquals(SurfaceDark, StaticDarkColorScheme.surface)
        assertEquals(LabelPrimaryDark, StaticDarkColorScheme.onSurface)
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

        assertEquals(ProviderClaude, providerColors.claude)
        assertEquals(ProviderBook, providerColors.book)
        assertEquals(ProviderOpenClaw, providerColors.openclaw)
    }

    @Test
    fun `shape tokens use apple radii`() {
        assertEquals(RoundedCornerShape(8.dp), IMbotMaterialShapes.small)
        assertEquals(RoundedCornerShape(12.dp), IMbotMaterialShapes.medium)
        assertEquals(RoundedCornerShape(16.dp), IMbotMaterialShapes.large)
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

    @Test
    fun `visual polish color tokens resolve for light and dark themes`() {
        assertEquals(UserBubbleLight, userBubbleBackground(useDarkTheme = false))
        assertEquals(UserBubbleLightText, userBubbleTextColor(useDarkTheme = false))
        assertEquals(UserBubbleDark, userBubbleBackground(useDarkTheme = true))
        assertEquals(UserBubbleDarkText, userBubbleTextColor(useDarkTheme = true))
        assertEquals(AssistantBubbleLight, assistantBubbleBackground(useDarkTheme = false))
        assertEquals(AssistantBubbleDark, assistantBubbleBackground(useDarkTheme = true))
        assertEquals(CodeBlockHeaderBg, codeBlockHeaderBackground(useDarkTheme = false))
        assertEquals(CodeBlockHeaderBgDark, codeBlockHeaderBackground(useDarkTheme = true))
        assertEquals(Color(0xFF0A0A0A), TerminalBg)
    }

    @Test
    fun `typography tokens match redesign spec`() {
        assertEquals(17.sp, IMbotTypography.titleLarge.fontSize)
        assertEquals(androidx.compose.ui.text.font.FontWeight.SemiBold, IMbotTypography.titleLarge.fontWeight)
        assertEquals(17.sp, IMbotTypography.bodyLarge.fontSize)
        assertEquals(12.sp, IMbotTypography.labelLarge.fontSize)
        assertEquals(11.sp, IMbotTypography.labelMedium.fontSize)
        assertTrue(IMbotTypography.headlineLarge.letterSpacing.value < 0f)
    }
}
