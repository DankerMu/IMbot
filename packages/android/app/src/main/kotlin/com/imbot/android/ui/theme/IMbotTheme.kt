@file:Suppress("FunctionName", "MatchingDeclarationName")

package com.imbot.android.ui.theme

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import com.imbot.android.data.SettingsRepository

internal data class ThemeResolution(
    val useDarkTheme: Boolean,
    val useDynamicColor: Boolean,
)

val LocalUseDarkTheme = staticCompositionLocalOf { false }

internal fun resolveThemeResolution(
    themeMode: String,
    systemInDarkTheme: Boolean,
    sdkInt: Int,
): ThemeResolution {
    val useDarkTheme =
        when (themeMode) {
            SettingsRepository.THEME_MODE_LIGHT -> false
            SettingsRepository.THEME_MODE_DARK -> true
            else -> systemInDarkTheme
        }

    return ThemeResolution(
        useDarkTheme = useDarkTheme,
        useDynamicColor = themeMode == SettingsRepository.THEME_MODE_SYSTEM && sdkInt >= Build.VERSION_CODES.S,
    )
}

internal val StaticLightColorScheme: ColorScheme =
    lightColorScheme(
        primary = BrandBlue,
        onPrimary = SurfaceLight,
        primaryContainer = BrandBlueLight,
        onPrimaryContainer = BrandBlue,
        secondary = SuccessColor,
        onSecondary = SurfaceLight,
        secondaryContainer = overlayColor(SurfaceLight, SuccessColor, 0.14f),
        onSecondaryContainer = LabelPrimary,
        tertiary = WarningColor,
        onTertiary = SurfaceLight,
        tertiaryContainer = overlayColor(SurfaceLight, WarningColor, 0.14f),
        onTertiaryContainer = LabelPrimary,
        error = DestructiveColor,
        onError = SurfaceLight,
        errorContainer = overlayColor(SurfaceLight, DestructiveColor, 0.14f),
        onErrorContainer = DestructiveColor,
        surface = SurfaceLight,
        onSurface = LabelPrimary,
        surfaceVariant = SurfaceSecondary,
        onSurfaceVariant = LabelSecondary,
        background = Background,
        onBackground = LabelPrimary,
        outline = SeparatorLight,
        outlineVariant = SeparatorLight.copy(alpha = 0.72f),
        scrim = LabelPrimary.copy(alpha = 0.2f),
        inverseSurface = SurfaceDark,
        inverseOnSurface = LabelPrimaryDark,
        inversePrimary = BrandBlueLight,
        surfaceTint = BrandBlue,
    )

internal val StaticDarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = BrandBlue,
        onPrimary = LabelPrimaryDark,
        primaryContainer = overlayColor(SurfaceSecondaryDark, BrandBlue, 0.24f),
        onPrimaryContainer = LabelPrimaryDark,
        secondary = SuccessColor,
        onSecondary = LabelPrimaryDark,
        secondaryContainer = overlayColor(SurfaceSecondaryDark, SuccessColor, 0.22f),
        onSecondaryContainer = LabelPrimaryDark,
        tertiary = WarningColor,
        onTertiary = LabelPrimaryDark,
        tertiaryContainer = overlayColor(SurfaceSecondaryDark, WarningColor, 0.24f),
        onTertiaryContainer = LabelPrimaryDark,
        error = DestructiveColor,
        onError = LabelPrimaryDark,
        errorContainer = overlayColor(SurfaceSecondaryDark, DestructiveColor, 0.24f),
        onErrorContainer = LabelPrimaryDark,
        surface = SurfaceDark,
        onSurface = LabelPrimaryDark,
        surfaceVariant = SurfaceSecondaryDark,
        onSurfaceVariant = LabelSecondaryDark,
        background = BackgroundDark,
        onBackground = LabelPrimaryDark,
        outline = SeparatorDark,
        outlineVariant = SeparatorDark.copy(alpha = 0.72f),
        scrim = Color.Black.copy(alpha = 0.5f),
        inverseSurface = SurfaceLight,
        inverseOnSurface = LabelPrimary,
        inversePrimary = BrandBlue,
        surfaceTint = BrandBlue,
    )

private fun overlayColor(
    base: Color,
    tint: Color,
    alpha: Float,
): Color = tint.copy(alpha = alpha).compositeOver(base)

@Composable
fun IMbotTheme(
    themeMode: String,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val themeResolution =
        resolveThemeResolution(
            themeMode = themeMode,
            systemInDarkTheme = isSystemInDarkTheme(),
            sdkInt = Build.VERSION.SDK_INT,
        )

    Crossfade(
        targetState = themeResolution,
        animationSpec = tween(durationMillis = IMbotAnimations.THEME_CROSSFADE_MS),
        label = "imbot-theme",
    ) { resolvedTheme ->
        @SuppressLint("NewApi") // guarded by resolvedTheme.useDynamicColor (sdkInt >= S)
        val colorScheme =
            when {
                resolvedTheme.useDynamicColor && resolvedTheme.useDarkTheme -> dynamicDarkColorScheme(context)
                resolvedTheme.useDynamicColor && !resolvedTheme.useDarkTheme -> dynamicLightColorScheme(context)
                resolvedTheme.useDarkTheme -> StaticDarkColorScheme
                else -> StaticLightColorScheme
            }

        CompositionLocalProvider(
            LocalProviderColors provides ProviderColors(),
            LocalStatusColors provides if (resolvedTheme.useDarkTheme) DarkStatusColors else LightStatusColors,
            LocalCodeTheme provides if (resolvedTheme.useDarkTheme) CodeTheme.Dark else CodeTheme.Light,
            LocalIMbotComponentShapes provides IMbotComponentShapes(),
            LocalIMbotSpacing provides IMbotSpacing(),
            LocalIMbotShadowTokens provides IMbotShadowTokens(),
            LocalUseDarkTheme provides resolvedTheme.useDarkTheme,
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = IMbotTypography,
                shapes = IMbotMaterialShapes,
                content = content,
            )
        }
    }
}
