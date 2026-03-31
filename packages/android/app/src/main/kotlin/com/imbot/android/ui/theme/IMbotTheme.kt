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
import androidx.compose.ui.platform.LocalContext
import com.imbot.android.data.SettingsRepository

internal data class ThemeResolution(
    val useDarkTheme: Boolean,
    val useDynamicColor: Boolean,
)

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
        primary = PrimaryLight,
        secondary = SecondaryLight,
        error = ErrorLight,
        surface = SurfaceLight,
        background = BackgroundLight,
    )

internal val StaticDarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = PrimaryDark,
        secondary = SecondaryDark,
        error = ErrorDark,
        surface = SurfaceDark,
        background = BackgroundDark,
    )

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
