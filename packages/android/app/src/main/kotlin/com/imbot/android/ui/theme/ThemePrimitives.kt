package com.imbot.android.ui.theme

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val MaterialTheme.spacing: IMbotSpacing
    @Composable
    get() = LocalIMbotSpacing.current

val MaterialTheme.appleShadow: IMbotShadowTokens
    @Composable
    get() = LocalIMbotShadowTokens.current

fun Modifier.appleChrome(
    shape: Shape,
    isDarkTheme: Boolean,
    outlineColor: Color,
    shadowTokens: IMbotShadowTokens,
): Modifier {
    val elevated =
        if (isDarkTheme) {
            this
        } else {
            shadow(
                elevation = shadowTokens.elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = shadowTokens.ambientAlpha),
                spotColor = Color.Black.copy(alpha = shadowTokens.spotAlpha),
            )
        }

    return elevated.border(
        width =
            if (isDarkTheme) {
                shadowTokens.darkBorderWidth
            } else {
                0.75.dp
            },
        color = outlineColor,
        shape = shape,
    )
}

@Composable
fun imbotFilledTextFieldColors(): androidx.compose.material3.TextFieldColors {
    val colorScheme = MaterialTheme.colorScheme
    val defaultColors = TextFieldDefaults.colors()
    return remember(
        defaultColors,
        colorScheme.onSurface,
        colorScheme.onSurfaceVariant,
        colorScheme.surfaceVariant,
        colorScheme.surface,
        colorScheme.errorContainer,
        colorScheme.primary,
        colorScheme.error,
        colorScheme.outline,
    ) {
        defaultColors.copy(
            focusedTextColor = colorScheme.onSurface,
            unfocusedTextColor = colorScheme.onSurface,
            disabledTextColor = colorScheme.onSurfaceVariant,
            errorTextColor = colorScheme.onSurface,
            focusedContainerColor = colorScheme.surfaceVariant,
            unfocusedContainerColor = colorScheme.surfaceVariant,
            disabledContainerColor = colorScheme.surface,
            errorContainerColor = colorScheme.errorContainer,
            cursorColor = colorScheme.primary,
            errorCursorColor = colorScheme.error,
            focusedIndicatorColor = colorScheme.primary,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = colorScheme.error,
            focusedLeadingIconColor = colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = colorScheme.onSurfaceVariant,
            disabledLeadingIconColor = colorScheme.outline,
            errorLeadingIconColor = colorScheme.error,
            focusedTrailingIconColor = colorScheme.onSurfaceVariant,
            unfocusedTrailingIconColor = colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = colorScheme.outline,
            errorTrailingIconColor = colorScheme.error,
            focusedLabelColor = colorScheme.onSurfaceVariant,
            unfocusedLabelColor = colorScheme.onSurfaceVariant,
            disabledLabelColor = colorScheme.outline,
            errorLabelColor = colorScheme.error,
            focusedPlaceholderColor = colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = colorScheme.onSurfaceVariant,
            disabledPlaceholderColor = colorScheme.outline,
            errorPlaceholderColor = colorScheme.error,
            focusedSupportingTextColor = colorScheme.onSurfaceVariant,
            unfocusedSupportingTextColor = colorScheme.onSurfaceVariant,
            disabledSupportingTextColor = colorScheme.outline,
            errorSupportingTextColor = colorScheme.error,
        )
    }
}
