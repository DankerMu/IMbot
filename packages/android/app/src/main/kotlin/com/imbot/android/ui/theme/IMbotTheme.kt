@file:Suppress("FunctionName")

package com.imbot.android.ui.theme

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.imbot.android.data.SettingsRepository

@Composable
fun IMbotTheme(
    themeMode: String,
    content: @Composable () -> Unit,
) {
    val useDarkTheme =
        when (themeMode) {
            SettingsRepository.THEME_MODE_LIGHT -> false
            SettingsRepository.THEME_MODE_DARK -> true
            else -> isSystemInDarkTheme()
        }

    Crossfade(
        targetState = useDarkTheme,
        animationSpec = tween(durationMillis = 400),
        label = "imbot-theme",
    ) { isDark ->
        MaterialTheme(
            colorScheme = if (isDark) darkColorScheme() else lightColorScheme(),
            content = content,
        )
    }
}
